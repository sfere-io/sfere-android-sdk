package io.sfere.analytics

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.Telemetry
import com.segment.analytics.kotlin.core.utilities.InMemoryStorageProvider
import com.segment.analytics.kotlin.core.utilities.encodeToBase64
import io.sfere.analytics.internal.FakeClock
import io.sfere.analytics.internal.HostValidation
import io.sfere.analytics.internal.InMemoryKeyValueStore
import io.sfere.analytics.internal.LIBRARY_NAME
import io.sfere.analytics.internal.applySfere
import io.sfere.analytics.internal.installSfere
import io.sfere.analytics.internal.validateHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * End to end on the JVM: the real base library (core), the Sfere plugins and destination, and a
 * local HTTP server standing in for the ingestion endpoint.
 */
class TransportTest {
    private lateinit var server: MockWebServer
    private val instances = mutableListOf<Analytics>()

    @Before
    fun setUp() {
        Telemetry.enable = false
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        instances.forEach { runCatching { it.shutdown() } }
        server.shutdown()
    }

    private class Harness(val sfere: Sfere, val analytics: Analytics, val store: InMemoryKeyValueStore, val writeKey: String)

    private fun harness(flushAt: Int, flushInterval: Int, clock: FakeClock = FakeClock()): Harness {
        val hostString = server.url("/").toString().trimEnd('/')
        val host = (validateHost(hostString, allowInsecure = true) as HostValidation.Valid).host
        val writeKey = "key-${UUID.randomUUID()}:secret"
        val config = SfereConfig(
            writeKey = writeKey,
            host = hostString,
            flushAt = flushAt,
            flushInterval = flushInterval,
            trackLifecycleEvents = false,
            trackDeepLinks = false,
            consent = mapOf("analytics" to true, "advertising" to false),
        )
        val configuration = Configuration(
            writeKey = writeKey,
            application = "jvm-test",
            storageProvider = InMemoryStorageProvider(),
        ).apply { applySfere(host, config) }
        val analytics = Analytics(configuration).also { instances += it }
        val store = InMemoryKeyValueStore()
        val consent = installSfere(analytics, host, config, store, clock)
        return Harness(Sfere(analytics, consent, store), analytics, store, writeKey)
    }

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(20)
        }
    }

    private fun body(request: RecordedRequest): JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject

    private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.content

    @Test
    fun oneBatchWithTheSfereWireShape() {
        val clock = FakeClock()
        val h = harness(flushAt = 6, flushInterval = 3600, clock = clock)
        server.enqueue(MockResponse().setBody("""{"ok":true,"receivedEvents":6,"okEvents":6}"""))

        h.sfere.identify("user_1", mapOf("email" to "fan@example.com"))
        waitUntil { h.analytics.traits()?.get("email") != null }
        // What the base library sends for an auto-tracked deep link: every query parameter as a property.
        h.analytics.track(
            "Deep Link Opened",
            JsonObject(
                mapOf(
                    "url" to Json.parseToJsonElement("\"sfereexample://m/42?gclid=G1&fbclid=F1&utm_source=ig&notAnId=x\""),
                    "referrer" to Json.parseToJsonElement("\"com.instagram.android\""),
                    "gclid" to Json.parseToJsonElement("\"G1\""),
                ),
            ),
        )
        waitUntil { h.store.getString("sfere_click_ids") != null }
        h.analytics.track("Application Opened", JsonObject(mapOf("from_background" to Json.parseToJsonElement("false"))))
        h.sfere.track("add_to_cart", mapOf("currency" to "SAR", "value" to 89.99, "items" to listOf(mapOf("item_id" to "sku_1"))))
        h.sfere.purchase(SferePurchase("order_1", "SAR", 89.99, listOf(SfereItem(itemId = "sku_1")), dedupId = "order_1"))
        // + session_started, emitted by the first event = 6

        val request = assertNotNull(server.takeRequest(15, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/v1/batch/b", request.path)
        assertEquals("Basic " + encodeToBase64("${h.writeKey}:"), request.getHeader("Authorization"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertNull("no gzip", request.getHeader("Content-Encoding"))
        assertTrue(request.getHeader("User-Agent")!!.startsWith("sfere-android/"))

        val raw = body(request)
        assertEquals(h.writeKey, raw.str("writeKey"))
        val batch = raw["batch"]!!.jsonArray.map { it.jsonObject }
        val byName = batch.associateBy { it.str("event") ?: it.str("type")!! }
        assertEquals(
            setOf("identify", "deep_link_opened", "app_opened", "add_to_cart", "purchase", "session_started"),
            byName.keys,
        )

        val sessionIds = mutableSetOf<Long>()
        for (event in batch) {
            assertFalse("_metadata on ${event.str("type")}", "_metadata" in event)
            assertFalse("integrations on ${event.str("type")}", "integrations" in event)
            val context = event["context"]!!.jsonObject
            assertFalse("instanceId", "instanceId" in context)
            assertEquals(LIBRARY_NAME, context.obj("library")!!.str("name"))
            assertEquals("analytics-kotlin@1.26.0", context.obj("library")!!.str("base"))
            sessionIds += context.obj("session")!!["id"]!!.jsonPrimitive.long
            assertEquals("true", context.obj("consent")!!.obj("categoryPreferences")!!.str("analytics"))
            if (event.str("type") == "track") {
                assertNotNull("sfere_dedup_id", event.obj("properties")!!.str("sfere_dedup_id"))
            }
        }
        assertEquals("one session for the whole batch", setOf(clock.now), sessionIds)

        val addToCart = byName.getValue("add_to_cart")
        assertEquals(addToCart.str("messageId"), addToCart.obj("properties")!!.str("sfere_dedup_id"))
        assertEquals("order_1", byName.getValue("purchase").obj("properties")!!.str("sfere_dedup_id"))

        val cartContext = addToCart.obj("context")!!
        assertEquals("fan@example.com", cartContext.obj("traits")!!.str("email"))
        assertEquals(mapOf("gclid" to "G1", "fbc" to "fb.1.${clock.now}.F1"), cartContext.obj("clientIds")!!.mapValues { it.value.jsonPrimitive.content })
        assertEquals("ig", cartContext.obj("campaign")!!.str("source"))
        assertNull("identify keeps traits at the top level only", byName.getValue("identify").obj("context")!!["traits"])

        val deepLink = byName.getValue("deep_link_opened").obj("properties")!!
        assertEquals(setOf("url", "referring_application", "sfere_dedup_id"), deepLink.keys)

        Thread.sleep(500)
        assertEquals("only the upload, never a settings request", 1, server.requestCount)
    }

    @Test
    fun retriesA503ThenSucceeds() {
        val h = harness(flushAt = 2, flushInterval = 1)
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        h.sfere.track("view_item") // + session_started = 2

        val first = assertNotNull(server.takeRequest(15, TimeUnit.SECONDS))
        val second = assertNotNull("retried after 503", server.takeRequest(20, TimeUnit.SECONDS))
        assertEquals(body(first)["batch"], body(second)["batch"])
        Thread.sleep(2_500)
        assertEquals("nothing after the success", 2, server.requestCount)
    }

    @Test
    fun dropsA400WithoutRetrying() {
        val h = harness(flushAt = 2, flushInterval = 1)
        server.enqueue(MockResponse().setResponseCode(400))
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        h.sfere.track("view_item")

        assertNotNull(server.takeRequest(15, TimeUnit.SECONDS))
        Thread.sleep(4_000)
        h.sfere.flush()
        Thread.sleep(1_000)
        assertEquals("the rejected batch is not sent again", 1, server.requestCount)
    }

    @Test
    fun settingsAreAnsweredLocally() {
        val h = harness(flushAt = 100, flushInterval = 3600)
        val connection = h.analytics.configuration.requestFactory.settings("unused", h.writeKey)
        val settings = Json { ignoreUnknownKeys = true }.decodeFromString(Settings.serializer(), connection.inputStream.bufferedReader().readText())
        assertEquals("${server.hostName}:${server.port}/v1/batch", settings.integrations["Sfere"]!!.jsonObject.str("apiHost"))
        assertEquals("0", settings.metrics.str("sampleRate"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun optOutStopsUploadsAndIsRemembered() {
        val h = harness(flushAt = 1, flushInterval = 1)
        h.sfere.setEnabled(false)
        h.sfere.track("view_item")
        Thread.sleep(2_500)
        assertEquals(0, server.requestCount)
        assertEquals("false", h.store.getString("sfere_enabled"))
    }

    private fun <T> assertNotNull(value: T?): T = assertNotNull(null, value)

    /**
     * The cross-platform parity scenario. The iOS WireTests (sfere-ios-sdk) run the same calls; with
     * SFERE_PARITY_DIR set, each platform writes its upload body there for a key-by-key comparison.
     */
    @Test
    fun parityScenarioAndDeepLinkProperties() {
        val h = harness(flushAt = 6, flushInterval = 3600)
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))

        h.sfere.setConsent(mapOf("analytics" to true, "advertising" to false))
        h.sfere.identify("user_123", mapOf("email" to "fan@example.com"))
        waitUntil { h.analytics.traits()?.get("email") != null }
        h.sfere.openUrl("sfereexample://match/42?utm_source=instagram&gclid=G1&fbclid=F1", referrer = "com.example.source")
        waitUntil { h.store.getString("sfere_click_ids") != null }
        h.sfere.track(
            "add_to_cart",
            mapOf(
                "currency" to "SAR", "value" to 89.99,
                "items" to listOf(mapOf("item_id" to "sku_12345", "item_name" to "Away Jersey", "price" to 89.99, "quantity" to 1)),
            ),
        )
        h.sfere.purchase(SferePurchase("order_1", "SAR", 89.99, listOf(SfereItem(itemId = "sku_12345", price = 89.99, quantity = 1)), dedupId = "order_1"))
        h.sfere.screen("Match Center")
        // + session_started = 6 events

        val request = assertNotNull(server.takeRequest(15, TimeUnit.SECONDS))
        val raw = request.body.readUtf8()
        val batch = Json.parseToJsonElement(raw).jsonObject["batch"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, batch.size)
        val deepLink = batch.single { it.str("event") == "deep_link_opened" }
        assertEquals(setOf("url", "referring_application", "sfere_dedup_id"), deepLink.obj("properties")!!.keys)

        System.getenv("SFERE_PARITY_DIR")?.let { java.io.File(it, "android.json").writeText(raw) }
    }

    private fun <T> assertNotNull(message: String?, value: T?): T {
        org.junit.Assert.assertNotNull(message, value)
        return value!!
    }
}
