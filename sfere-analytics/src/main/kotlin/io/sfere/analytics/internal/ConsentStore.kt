package io.sfere.analytics.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/** Consent categories sent as `context.consent.categoryPreferences`; null means unknown (omitted). */
internal class ConsentStore(private val store: KeyValueStore) {
    private var current: Map<String, Boolean>? = load()

    @Synchronized
    fun get(): Map<String, Boolean>? = current

    @Synchronized
    fun set(consent: Map<String, Boolean>?) {
        current = consent?.toMap()
        store.putString(KEY, current?.let { map -> JsonObject(map.mapValues { JsonPrimitive(it.value) }).toString() })
    }

    private fun load(): Map<String, Boolean>? = try {
        store.getString(KEY)?.let { raw ->
            Json.parseToJsonElement(raw).jsonObject
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.booleanOrNull?.let { k to it } }
                .toMap()
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val KEY = "sfere_consent"
    }
}
