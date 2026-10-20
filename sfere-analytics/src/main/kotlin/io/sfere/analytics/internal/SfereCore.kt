package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Configuration
import com.segment.analytics.kotlin.core.Settings
import io.sfere.analytics.BuildConfig
import io.sfere.analytics.SfereConfig
import kotlinx.serialization.json.Json

internal const val LIBRARY_NAME = "@fanfinity/sfere-android"
internal const val ENABLED_KEY = "sfere_enabled"

// The ingestion endpoint accepts at most 100 events per batch.
internal const val MAX_FLUSH_AT = 100
private const val MILLIS_PER_MINUTE = 60_000L

internal val sfereLibrary = LibraryStamp(LIBRARY_NAME, BuildConfig.SDK_VERSION, BuildConfig.BASE_LIBRARY)

internal val sfereUserAgent: String =
    "sfere-android/${BuildConfig.SDK_VERSION} (${BuildConfig.BASE_LIBRARY})"

internal sealed class ConfigValidation {
    data class Valid(val host: SfereHost) : ConfigValidation()
    data class Invalid(val reason: String) : ConfigValidation()
}

internal fun validateConfig(config: SfereConfig, allowInsecureHost: Boolean): ConfigValidation {
    if (config.writeKey.isBlank()) return ConfigValidation.Invalid("\"writeKey\" is required.")
    return when (val host = validateHost(config.host, allowInsecureHost)) {
        is HostValidation.Valid -> ConfigValidation.Valid(host.host)
        is HostValidation.Invalid -> ConfigValidation.Invalid(host.reason)
    }
}

/** Points the base library at the Sfere ingestion endpoint, with settings built in. */
internal fun Configuration.applySfere(host: SfereHost, config: SfereConfig) {
    val settings = inlineSettings(writeKey, host)
    autoAddSegmentDestination = false
    defaultSettings = settings
    apiHost = host.apiHost
    cdnHost = host.authority
    requestFactory = SfereRequestFactory(
        host = host,
        writeKey = writeKey,
        settingsJson = Json.encodeToString(Settings.serializer(), settings),
        userAgent = sfereUserAgent,
    )
    httpConfig = sfereHttpConfig()
    flushAt = config.flushAt.coerceIn(1, MAX_FLUSH_AT)
    flushInterval = config.flushInterval.coerceAtLeast(1)
    trackApplicationLifecycleEvents = config.trackLifecycleEvents
    useLifecycleObserver = true
    trackDeepLinks = config.trackDeepLinks
    collectDeviceId = false
}

/** Adds the Sfere plugins and destination to an Analytics instance built with [applySfere]. */
internal fun installSfere(
    analytics: Analytics,
    host: SfereHost,
    config: SfereConfig,
    store: KeyValueStore,
    clock: Clock = SystemClock,
    library: LibraryStamp = sfereLibrary,
): ConsentStore {
    val consent = ConsentStore(store)
    // The config value is the initial consent only: a choice already stored by setConsent wins.
    if (consent.get() == null) config.consent?.let { consent.set(it) }
    val timeoutMs = config.sessionTimeoutMinutes.coerceAtLeast(1) * MILLIS_PER_MINUTE
    analytics.add(SfereEnrichmentPlugin(SessionTracker(store, clock, timeoutMs), ClickIdStore(store), consent, clock, library))
    analytics.add(SfereDestination(config.writeKey, host.apiHost, sfereHttpConfig(), library.version))
    if (store.getString(ENABLED_KEY) == "false") analytics.enabled = false
    return consent
}
