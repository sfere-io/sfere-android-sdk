package io.sfere.analytics.internal

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/** The small persisted state the Sfere plugins keep: session, click IDs, consent, opt-out. */
internal interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

internal class InMemoryKeyValueStore : KeyValueStore {
    private val values = ConcurrentHashMap<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

/** Thin adapter over SharedPreferences, one file per write key. */
internal class SharedPreferencesKeyValueStore(context: Context, writeKey: String) : KeyValueStore {
    private val prefs = context.getSharedPreferences(
        "io.sfere.analytics." + Integer.toHexString(writeKey.hashCode()),
        Context.MODE_PRIVATE,
    )

    override fun getString(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        null
    }

    override fun putString(key: String, value: String?) {
        try {
            prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        } catch (e: Exception) {
            // Storage failures must never reach the host app.
        }
    }
}
