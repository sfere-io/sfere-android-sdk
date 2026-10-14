package io.sfere.analytics

/**
 * Settings for [Sfere.setup].
 *
 * @property writeKey The public browser key (`keyId:secret`). Never put a server key in an app.
 * @property host The ingestion origin, scheme and host only, e.g. `https://ingest.example.com`.
 *   `http://` is accepted only in debuggable builds or when [debug] is true, for local mocks.
 * @property flushAt Events per upload.
 * @property flushInterval Seconds an event may wait before its batch is uploaded.
 * @property sessionTimeoutMinutes Inactivity after which a new session starts.
 * @property trackLifecycleEvents Send `app_installed`, `app_updated`, `app_opened`, `app_backgrounded`.
 * @property trackDeepLinks Send `deep_link_opened` for links that open an Activity.
 * @property consent Consent categories (e.g. `analytics`, `advertising`) sent on every event.
 *   When set, it replaces any value stored by an earlier [Sfere.setConsent] call.
 * @property debug Log every payload and every rejected call.
 */
public class SfereConfig @JvmOverloads constructor(
    public val writeKey: String,
    public val host: String,
    public val flushAt: Int = 20,
    public val flushInterval: Int = 30,
    public val sessionTimeoutMinutes: Int = 30,
    public val trackLifecycleEvents: Boolean = true,
    public val trackDeepLinks: Boolean = true,
    public val consent: Map<String, Boolean>? = null,
    public val debug: Boolean = false,
)
