package io.sfere.analytics.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class EventNamesTest {
    private val fixture = Fixtures.load("event-names.json")

    @Test
    fun renamesMatchTheSharedFixture() {
        val expected = fixture["renames"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }
        assertEquals(expected, EventNames.renames)
        val expectedDeepLink = fixture["propertyRenames"]!!.jsonObject["deep_link_opened"]!!.jsonObject
            .mapValues { it.value.jsonPrimitive.content }
        assertEquals(expectedDeepLink, EventNames.deepLinkPropertyRenames)
    }

    @Test
    fun unknownNamesPassThrough() {
        assertEquals("add_to_cart", EventNames.rename("add_to_cart"))
        assertEquals("Custom Thing", EventNames.rename("Custom Thing"))
    }

    @Test
    fun deepLinkPropertiesKeepOnlyUrlAndReferrer() {
        val input = JsonObject(
            mapOf(
                "url" to JsonPrimitive("app://x?gclid=1&utm_source=ig"),
                "referrer" to JsonPrimitive("com.instagram.android"),
                "gclid" to JsonPrimitive("1"),
                "utm_source" to JsonPrimitive("ig"),
            ),
        )
        assertEquals(
            JsonObject(
                mapOf(
                    "url" to JsonPrimitive("app://x?gclid=1&utm_source=ig"),
                    "referring_application" to JsonPrimitive("com.instagram.android"),
                ),
            ),
            EventNames.deepLinkProperties(input),
        )
    }

    @Test
    fun deepLinkReferrerComesFromSourceApplicationAndEmptyValuesAreDropped() {
        val fromSource = JsonObject(mapOf("sourceApplication" to JsonPrimitive("com.example.app")))
        assertEquals(
            JsonObject(mapOf("referring_application" to JsonPrimitive("com.example.app"))),
            EventNames.deepLinkProperties(fromSource),
        )
        val empty = JsonObject(mapOf("url" to JsonPrimitive("app://x"), "referrer" to JsonPrimitive("")))
        assertEquals(JsonObject(mapOf("url" to JsonPrimitive("app://x"))), EventNames.deepLinkProperties(empty))
    }
}
