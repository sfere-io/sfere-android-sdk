package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.TrackEvent
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The enrichment plugin on its own, without an Analytics instance: the paths the end-to-end tests cannot reach. */
class SfereEnrichmentPluginTest {
    private val clock = FakeClock()
    private val store = InMemoryKeyValueStore()
    private val session = SessionTracker(store, clock, timeoutMs = 30 * 60_000L)
    private val clicks = ClickIdStore(store)
    private val consent = ConsentStore(store)
    private val plugin = SfereEnrichmentPlugin(session, clicks, consent, clock, LibraryStamp("lib", "1.0.0", "base@1"))

    private fun trackEvent(name: String, properties: Map<String, Any?> = emptyMap(), context: String = "{}") =
        TrackEvent(properties.toJsonObject(), name).apply {
            messageId = "message-1"
            timestamp = "2026-10-20T00:00:00.000Z"
            this.context = Json.parseToJsonElement(context).jsonObject
        }

    @Test
    fun aFailureInsideEnrichmentReturnsTheEventInsteadOfThrowing() {
        // A new session emits session_started through `analytics`, which this plugin was never given.
        val event = trackEvent("Application Opened")
        assertSame(event, plugin.execute(event))
    }

    @Test
    fun contextMergesStoredClicksCallerValuesConsentAndTheSession() {
        session.tick() // an ongoing session: no session_started
        clicks.merge(ClickIdParser.parse("app://p?gclid=STORED&ttclid=T&utm_source=stored&utm_medium=m", clock.now))
        consent.set(mapOf("analytics" to true))
        val event = trackEvent(
            "add_to_cart",
            properties = mapOf("sfere_dedup_id" to "given"),
            context = """{"clientIds":{"gclid":"CALLER"},"campaign":{"source":"caller","term":"t"}}""",
        )

        val context = (plugin.execute(event) as TrackEvent).context

        assertEquals(
            Json.parseToJsonElement("""{"gclid":"CALLER","ttclid":"T"}"""),
            context["clientIds"],
        )
        assertEquals(Json.parseToJsonElement("""{"source":"stored","term":"t","medium":"m"}"""), context["campaign"])
        assertEquals(clock.now, context["session"]!!.jsonObject["id"]!!.jsonPrimitive.long)
        assertEquals(Json.parseToJsonElement("""{"name":"lib","version":"1.0.0","base":"base@1"}"""), context["library"])
        assertEquals(Json.parseToJsonElement("""{"categoryPreferences":{"analytics":true}}"""), context["consent"])
        assertNull("no traits without an Analytics instance", context["traits"])
        assertEquals(JsonPrimitive("given"), event.properties["sfere_dedup_id"])
    }

    @Test
    fun aNullDedupIdIsReplacedByTheMessageId() {
        session.tick()
        val event = trackEvent("view_item", properties = mapOf("sfere_dedup_id" to JsonNull))
        plugin.execute(event)
        assertEquals(JsonPrimitive("message-1"), event.properties["sfere_dedup_id"])
    }

    @Test
    fun resetClearsTheSessionAndTheStoredClicks() {
        session.tick()
        clicks.merge(ClickIdParser.parse("app://p?gclid=G", clock.now))
        plugin.reset()
        assertNull(store.getString("sfere_session"))
        assertEquals(ClickIdStore.Snapshot(), clicks.snapshot())
        assertTrue(session.tick().isNew)
    }
}

class LocalResponseConnectionTest {
    @Test
    fun answersTheSettingsRequestWithoutANetwork() {
        val connection = LocalResponseConnection(URL("https://ingest.example.com/"), """{"integrations":{}}""")
        connection.connect()
        assertEquals(HttpURLConnection.HTTP_OK, connection.responseCode)
        assertEquals("OK", connection.responseMessage)
        assertFalse(connection.usingProxy())
        assertNull(connection.errorStream)
        assertEquals("""{"integrations":{}}""", connection.inputStream.bufferedReader().readText())
        connection.disconnect()
    }

    @Test
    fun theDestinationReportsTheSdkVersion() {
        assertEquals("1.2.3", SfereDestination("key", "ingest.example.com/v1/batch", null, "1.2.3").version())
    }
}

class SfereLogTest {
    @Test
    fun loggingNeverThrowsOnTheJvm() {
        val before = SfereLog.debug
        try {
            SfereLog.debug = false
            SfereLog.debug("not logged")
            SfereLog.debug = true
            SfereLog.debug("logged")
            SfereLog.error("always logged")
            assertTrue(SfereLog.debug)
        } finally {
            SfereLog.debug = before
        }
    }
}
