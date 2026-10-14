package io.sfere.analytics.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Reads the shared fixtures at the repo root (path set by Gradle as `sfere.fixtures.dir`). */
internal object Fixtures {
    private val dir: File
        get() = File(checkNotNull(System.getProperty("sfere.fixtures.dir")) { "sfere.fixtures.dir is not set" })

    fun load(name: String): JsonObject = Json.parseToJsonElement(File(dir, name).readText()).jsonObject
}

internal class FakeClock(var now: Long = 1_760_349_600_000L) : Clock {
    override fun nowMillis(): Long = now
}
