package io.sfere.analytics.internal

import io.sfere.analytics.SfereConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostValidationTest {
    private fun valid(host: String, insecure: Boolean = false) =
        (validateHost(host, insecure) as HostValidation.Valid).host

    @Test
    fun acceptsHttpsOrigins() {
        assertEquals(SfereHost("https", "ingest.example.com"), valid("https://ingest.example.com"))
        assertEquals(SfereHost("https", "ingest.example.com"), valid("https://ingest.example.com/"))
        assertEquals(SfereHost("https", "ingest.example.com:8443"), valid("https://ingest.example.com:8443"))
        assertEquals("ingest.example.com/v1/batch", valid("https://ingest.example.com").apiHost)
    }

    @Test
    fun rejectsPathsQueriesOtherSchemesAndBlank() {
        for (bad in listOf("", "  ", "ingest.example.com", "ftp://x.com", "https://x.com/v1", "https://x.com?a=1", "https://x.com#f", "https://user@x.com", "https://")) {
            assertTrue("expected \"$bad\" to be rejected", validateHost(bad, allowInsecure = true) is HostValidation.Invalid)
        }
    }

    @Test
    fun httpOnlyWhenInsecureIsAllowed() {
        assertTrue(validateHost("http://10.0.2.2:4311", allowInsecure = false) is HostValidation.Invalid)
        assertEquals(SfereHost("http", "10.0.2.2:4311"), valid("http://10.0.2.2:4311", insecure = true))
    }

    @Test
    fun blankWriteKeyIsRejected() {
        assertTrue(validateConfig(SfereConfig(" ", "https://x.com"), false) is ConfigValidation.Invalid)
    }
}

class WireSanitizerTest {
    @Test
    fun removesMetadataEmptyIntegrationsInstanceIdAndUnknownClientIds() {
        val input = Json.parseToJsonElement(
            """
            {"type":"track","event":"x","userId":"","integrations":{},"_metadata":{"bundled":[]},
             "context":{"instanceId":"abc","clientIds":{"gclid":"G","notAllowed":"N"},"library":{"name":"n"}},
             "properties":{"a":1}}
            """,
        ).jsonObject
        val expected = Json.parseToJsonElement(
            """{"type":"track","event":"x","context":{"clientIds":{"gclid":"G"},"library":{"name":"n"}},"properties":{"a":1}}""",
        ).jsonObject
        assertEquals(expected, WireSanitizer.sanitize(input))
    }

    @Test
    fun keepsIntegrationsTheCallerSet() {
        val input = Json.parseToJsonElement("""{"integrations":{"Sfere":false},"context":{"clientIds":{"x":"y"}}}""").jsonObject
        assertEquals(Json.parseToJsonElement("""{"integrations":{"Sfere":false},"context":{}}""").jsonObject, WireSanitizer.sanitize(input))
    }
}
