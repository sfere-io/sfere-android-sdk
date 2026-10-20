package io.sfere.analytics.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonConversionTest {
    @Test
    fun everySupportedTypeBecomesItsJsonValue() {
        val input = mapOf(
            "null" to null,
            "json" to JsonPrimitive("already json"),
            "string" to "s",
            "int" to 1,
            "double" to 2.5,
            "boolean" to true,
            "char" to 'c',
            "map" to mapOf("a" to 1, null to "dropped"),
            "list" to listOf("x", null),
            "array" to arrayOf<Any>("y", 2),
            "ints" to intArrayOf(1, 2),
            "longs" to longArrayOf(3L),
            "doubles" to doubleArrayOf(4.5),
            "booleans" to booleanArrayOf(false),
            "other" to StringBuilder("to string"),
        )
        val expected = Json.parseToJsonElement(
            """
            {"null":null,"json":"already json","string":"s","int":1,"double":2.5,"boolean":true,"char":"c",
             "map":{"a":1},"list":["x",null],"array":["y",2],"ints":[1,2],"longs":[3],"doubles":[4.5],
             "booleans":[false],"other":"to string"}
            """,
        )
        assertEquals(expected, input.toJsonObject())
    }

    @Test
    fun nullPropertiesBecomeAnEmptyObject() {
        assertEquals(JsonObject(emptyMap()), (null as Map<String, Any?>?).toJsonObject())
        assertEquals(JsonNull, null.toJsonElement())
    }
}
