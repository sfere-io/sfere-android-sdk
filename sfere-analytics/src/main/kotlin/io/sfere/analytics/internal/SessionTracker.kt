package io.sfere.analytics.internal

/**
 * Inactivity-based session (WIRE-CONTRACT §4): `context.session.id` is the start time in epoch ms,
 * and a new one starts after [timeoutMs] without events. App foreground sends `app_opened`, so it
 * counts as activity.
 */
internal class SessionTracker(private val store: KeyValueStore, private val clock: Clock, private val timeoutMs: Long) {
    internal data class Tick(val id: Long, val isNew: Boolean)

    private var id: Long? = null
    private var lastActivity: Long = 0

    init {
        store.getString(KEY)?.split(':')?.takeIf { it.size == 2 }?.let { parts ->
            val storedId = parts[0].toLongOrNull()
            val storedLast = parts[1].toLongOrNull()
            if (storedId != null && storedLast != null) {
                id = storedId
                lastActivity = storedLast
            }
        }
    }

    @Synchronized
    fun tick(): Tick {
        val now = clock.nowMillis()
        val current = id
        val isNew = current == null || now - lastActivity > timeoutMs
        val sessionId = if (isNew) now else current
        id = sessionId
        lastActivity = now
        store.putString(KEY, "$sessionId:$now")
        return Tick(sessionId, isNew)
    }

    @Synchronized
    fun reset() {
        id = null
        lastActivity = 0
        store.putString(KEY, null)
    }

    private companion object {
        const val KEY = "sfere_session"
    }
}
