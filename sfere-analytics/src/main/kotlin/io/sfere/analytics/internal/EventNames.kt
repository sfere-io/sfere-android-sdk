package io.sfere.analytics.internal

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Base-library event names -> Sfere names (`fixtures/event-names.json`). */
internal object EventNames {
    const val SESSION_STARTED = "session_started"
    const val DEEP_LINK_OPENED = "deep_link_opened"

    val renames: Map<String, String> = mapOf(
        "Application Installed" to "app_installed",
        "Application Opened" to "app_opened",
        "Application Updated" to "app_updated",
        "Application Backgrounded" to "app_backgrounded",
        "Application Foregrounded" to "app_foregrounded",
        "Deep Link Opened" to DEEP_LINK_OPENED,
    )

    val deepLinkPropertyRenames: Map<String, String> = mapOf(
        "referrer" to "referring_application",
        "sourceApplication" to "referring_application",
    )

    fun rename(name: String): String = renames[name] ?: name

    /**
     * The base library copies every query parameter of a deep link into the event's properties.
     * Each would become a warehouse column, and the click IDs and UTMs already go to
     * `context.clientIds` / `context.campaign`, so keep only `url` and `referring_application`.
     */
    fun deepLinkProperties(properties: JsonObject): JsonObject {
        val kept = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        properties["url"]?.let { kept["url"] = it }
        for ((from, to) in deepLinkPropertyRenames) {
            val value = properties[from] ?: properties[to]
            if (value != null && (value as? JsonPrimitive)?.contentOrNull?.isNotEmpty() == true) kept[to] = value
        }
        return JsonObject(kept)
    }
}
