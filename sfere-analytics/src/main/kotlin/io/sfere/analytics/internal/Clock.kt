package io.sfere.analytics.internal

internal fun interface Clock {
    fun nowMillis(): Long
}

internal object SystemClock : Clock {
    override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
}
