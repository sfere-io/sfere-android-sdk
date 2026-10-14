package io.sfere.analytics.internal

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The last step before an event is written to the upload queue (WIRE-CONTRACT §3): no `_metadata`,
 * no empty `integrations`, no `metrics`, no `context.instanceId` / `referrer` / `__eventOrigin` /
 * `device.id`, and only the destinations' `clientIds` keys.
 * Every unknown field becomes a warehouse column.
 */
internal object WireSanitizer {
    fun sanitize(event: JsonObject): JsonObject {
        val result = LinkedHashMap<String, JsonElement>()
        for ((key, value) in event) {
            when {
                key == "_metadata" || key == "metrics" -> Unit
                key == "integrations" && (value as? JsonObject)?.isEmpty() != false -> Unit
                key == "userId" && (value as? JsonPrimitive)?.content.isNullOrBlank() -> Unit
                key == "traits" && (value as? JsonObject)?.isEmpty() == true -> Unit
                // Empty values the iOS SDK leaves out (screen category, empty properties).
                key == "category" && (value as? JsonPrimitive)?.content.isNullOrBlank() -> Unit
                key == "properties" && (value as? JsonObject)?.isEmpty() == true -> Unit
                key == "context" && value is JsonObject -> result[key] = sanitizeContext(value)
                else -> result[key] = value
            }
        }
        return JsonObject(result)
    }

    private fun sanitizeContext(context: JsonObject): JsonObject {
        val result = LinkedHashMap<String, JsonElement>()
        for ((key, value) in context) {
            when (key) {
                // Base-library extras the contract does not carry (the iOS SDK drops the same keys).
                "instanceId", "referrer", "__eventOrigin" -> Unit
                // No device id on either platform until the contract adds one.
                "device" -> if (value is JsonObject) result[key] = JsonObject(value - "id") else result[key] = value
                "clientIds" -> {
                    val ids = (value as? JsonObject)?.filterKeys { it in ClickIdRules.allowedClientIdKeys }.orEmpty()
                    if (ids.isNotEmpty()) result[key] = JsonObject(ids)
                }
                else -> result[key] = value
            }
        }
        return JsonObject(result)
    }
}
