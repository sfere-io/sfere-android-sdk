package io.sfere.sample

import android.content.Context

/**
 * Host, key and session timeout for the sample app. `Sfere.setup` runs once per process, so a
 * change takes effect on the next launch.
 */
class SampleSettings(context: Context) {
    private val preferences = context.getSharedPreferences("sfere-sample-settings", Context.MODE_PRIVATE)

    var host: String
        get() = preferences.getString(HOST_KEY, null) ?: DEFAULT_HOST
        set(value) = preferences.edit().putString(HOST_KEY, value.trim()).apply()

    var writeKey: String
        get() = preferences.getString(WRITE_KEY_KEY, null) ?: DEFAULT_WRITE_KEY
        set(value) = preferences.edit().putString(WRITE_KEY_KEY, value.trim()).apply()

    var sessionTimeoutMinutes: Int
        get() = preferences.getInt(SESSION_TIMEOUT_KEY, DEFAULT_SESSION_TIMEOUT_MINUTES)
        set(value) = preferences.edit().putInt(SESSION_TIMEOUT_KEY, value).apply()

    companion object {
        /** The Mac's tools/mock_ingest.mjs, as the emulator sees it. */
        const val DEFAULT_HOST = "http://10.0.2.2:4320"
        const val DEFAULT_WRITE_KEY = "sample-key:local"
        const val DEFAULT_SESSION_TIMEOUT_MINUTES = 30

        private const val HOST_KEY = "host"
        private const val WRITE_KEY_KEY = "write_key"
        private const val SESSION_TIMEOUT_KEY = "session_timeout_minutes"
    }
}
