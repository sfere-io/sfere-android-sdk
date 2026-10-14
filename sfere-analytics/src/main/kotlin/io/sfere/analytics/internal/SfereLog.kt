package io.sfere.analytics.internal

import android.util.Log

internal object SfereLog {
    private const val TAG = "Sfere"

    @Volatile
    var debug: Boolean = false

    /** Always logged: misconfiguration the developer must see. */
    fun error(message: String) {
        try {
            Log.e(TAG, message)
        } catch (e: Throwable) {
            java.lang.System.err.println("$TAG: $message")
        }
    }

    /** Logged only when `debug` is on. */
    fun debug(message: String) {
        if (!debug) return
        try {
            Log.d(TAG, message)
        } catch (e: Throwable) {
            println("$TAG: $message")
        }
    }
}
