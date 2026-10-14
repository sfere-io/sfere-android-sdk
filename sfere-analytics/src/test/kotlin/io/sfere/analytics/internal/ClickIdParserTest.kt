package io.sfere.analytics.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

class ClickIdParserTest {
    private val fixture = Fixtures.load("deeplinks.json")

    private fun JsonObject.strings(): Map<String, String> = mapValues { it.value.jsonPrimitive.content }

    @Test
    fun everyFixtureCaseMatches() {
        val nowMs = fixture["nowMs"]!!.jsonPrimitive.long
        val cases = fixture["cases"]!!.jsonArray
        check(cases.isNotEmpty())
        for (element in cases) {
            val case = element.jsonObject
            val name = case["name"]!!.jsonPrimitive.content
            val parsed = ClickIdParser.parse(case["url"]!!.jsonPrimitive.content, nowMs)
            assertEquals("clientIds for \"$name\"", case["clientIds"]!!.jsonObject.strings(), parsed.clientIds)
            assertEquals("campaign for \"$name\"", case["campaign"]!!.jsonObject.strings(), parsed.campaign)
        }
    }

    @Test
    fun rulesMatchTheSharedFixture() {
        assertEquals(fixture["urlClickIds"]!!.jsonObject.strings(), ClickIdRules.urlClickIds)
        assertEquals(fixture["campaignParams"]!!.jsonObject.strings(), ClickIdRules.campaignParams)
        assertEquals(
            fixture["allowedClientIdKeys"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(),
            ClickIdRules.allowedClientIdKeys,
        )
    }

    @Test
    fun malformedPercentEncodingIsSkippedNotThrown() {
        val parsed = ClickIdParser.parse("sfereexample://p?gclid=%E0%A4%A&ttclid=ok", 1L)
        assertEquals(mapOf("ttclid" to "ok"), parsed.clientIds)
    }
}
