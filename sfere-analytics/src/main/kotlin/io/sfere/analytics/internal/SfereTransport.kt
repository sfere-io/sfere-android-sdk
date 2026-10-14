package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.RequestFactory
import com.segment.analytics.kotlin.core.Settings
import com.segment.analytics.kotlin.core.platform.EventPipeline
import com.segment.analytics.kotlin.core.platform.policies.FlushPolicy
import com.segment.analytics.kotlin.core.retry.BackoffConfig
import com.segment.analytics.kotlin.core.retry.HttpConfig
import com.segment.analytics.kotlin.core.retry.RateLimitConfig
import com.segment.analytics.kotlin.core.utilities.EncodeDefaultsJson
import com.segment.analytics.kotlin.core.utilities.encodeToBase64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Key of the Sfere destination in settings and in an event's `integrations`. */
internal const val SFERE_DESTINATION_KEY = "Sfere"

/**
 * Settings built into the SDK. The ingestion endpoint has no settings route, so nothing is ever
 * fetched. Metrics are off (`sampleRate: 0`).
 */
internal fun inlineSettings(writeKey: String, host: SfereHost): Settings = Settings(
    integrations = buildJsonObject {
        putJsonObject(SFERE_DESTINATION_KEY) {
            put("apiKey", writeKey)
            put("apiHost", host.apiHost)
        }
    },
    plan = buildJsonObject {
        for (type in listOf("track", "identify", "group")) {
            putJsonObject(type) { putJsonObject("__default") { put("enabled", true) } }
        }
    },
    metrics = buildJsonObject { put("sampleRate", 0) },
)

/** Retries on network errors, 5xx and 429 with backoff; other 4xx are dropped (WIRE-CONTRACT §5). */
internal fun sfereHttpConfig(): HttpConfig = HttpConfig(
    rateLimitConfig = RateLimitConfig(enabled = true),
    backoffConfig = BackoffConfig(enabled = true),
)

/**
 * Sends uploads to `{scheme}://{host}/v1/batch/b` with the write key as Basic auth, as plain JSON.
 * The settings request never touches the network: it is answered from [settingsJson].
 */
internal class SfereRequestFactory(
    private val host: SfereHost,
    writeKey: String,
    private val settingsJson: String,
    private val userAgent: String,
) : RequestFactory() {
    private val authorization = "Basic " + encodeToBase64("$writeKey:")

    override fun settings(cdnHost: String, writeKey: String): HttpURLConnection =
        LocalResponseConnection(URL("${host.scheme}://${host.authority}/"), settingsJson)

    override fun upload(apiHost: String): HttpURLConnection {
        val connection = openConnection("${host.scheme}://$apiHost/b")
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", authorization)
        connection.doOutput = true
        connection.setChunkedStreamingMode(0)
        return connection
    }
}

/** An HttpURLConnection that never connects and answers 200 with a fixed body. */
internal class LocalResponseConnection(url: URL, body: String) : HttpURLConnection(url) {
    private val bytes = body.toByteArray(Charsets.UTF_8)
    override fun connect() { connected = true }
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = false
    override fun getResponseCode(): Int = HTTP_OK
    override fun getResponseMessage(): String = "OK"
    override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
    override fun getErrorStream(): InputStream? = null
}

/** The base library's upload pipeline, with every event cleaned by [WireSanitizer] before it is queued. */
internal class SferePipeline(
    analytics: Analytics,
    writeKey: String,
    flushPolicies: List<FlushPolicy>,
    apiHost: String,
    httpConfig: HttpConfig?,
) : EventPipeline(analytics, SFERE_DESTINATION_KEY, writeKey, flushPolicies, apiHost, httpConfig) {
    override fun stringifyBaseEvent(payload: BaseEvent): String {
        val json = EncodeDefaultsJson.encodeToJsonElement(payload).jsonObject
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), WireSanitizer.sanitize(json))
    }
}
