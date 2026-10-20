package io.sfere.analytics

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.segment.analytics.kotlin.android.Analytics as AndroidAnalytics
import com.segment.analytics.kotlin.android.openUrl
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.Telemetry
import io.sfere.analytics.internal.ConfigValidation
import io.sfere.analytics.internal.ConsentStore
import io.sfere.analytics.internal.ENABLED_KEY
import io.sfere.analytics.internal.EventNames
import io.sfere.analytics.internal.KeyValueStore
import io.sfere.analytics.internal.SfereLog
import io.sfere.analytics.internal.SharedPreferencesKeyValueStore
import io.sfere.analytics.internal.applySfere
import io.sfere.analytics.internal.installSfere
import io.sfere.analytics.internal.toJsonObject
import io.sfere.analytics.internal.validateConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Longer names are accepted but may be truncated by the ingestion endpoint (GA4 limit).
private const val MAX_EVENT_NAME_LENGTH = 128

/**
 * The Sfere SDK. Call [setup] once in `Application.onCreate`, then use [shared] anywhere.
 *
 * Every method is safe to call at any time: before [setup], or after a failed setup, calls are
 * ignored (and logged when `debug` is on). Nothing here throws into the host app.
 */
public class Sfere internal constructor(
    private val analytics: Analytics?,
    private val consentStore: ConsentStore?,
    private val store: KeyValueStore?,
) {
    public companion object {
        @Volatile
        private var instance: Sfere? = null

        private val disabled = Sfere(null, null, null)

        /** The instance created by [setup], or a no-op instance before that. */
        @JvmStatic
        public val shared: Sfere
            get() = instance ?: disabled

        /**
         * Creates the SDK. Later calls return the first instance and ignore their config.
         * An invalid config (blank key, bad host) logs an error and returns a no-op instance.
         */
        @JvmStatic
        @Synchronized
        public fun setup(context: Context, config: SfereConfig): Sfere {
            instance?.let {
                SfereLog.error("Sfere.setup was called more than once; the first configuration is kept.")
                return it
            }
            SfereLog.debug = config.debug
            return try {
                val appContext = context.applicationContext ?: context
                val debuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                when (val validation = validateConfig(config, allowInsecureHost = debuggable || config.debug)) {
                    is ConfigValidation.Invalid -> {
                        SfereLog.error("Sfere is disabled: ${validation.reason}")
                        disabled
                    }
                    is ConfigValidation.Valid -> {
                        // The base library reports usage metrics to its vendor's servers unless this is off.
                        Telemetry.enable = false
                        Analytics.debugLogsEnabled = config.debug
                        val analytics = AndroidAnalytics(config.writeKey, appContext) {
                            applySfere(validation.host, config)
                        }
                        val store = SharedPreferencesKeyValueStore(appContext, config.writeKey)
                        val consent = installSfere(analytics, validation.host, config, store)
                        Sfere(analytics, consent, store).also { instance = it }
                    }
                }
            } catch (e: Exception) {
                SfereLog.error("Sfere is disabled: setup failed with $e")
                disabled
            }
        }
    }

    /** Records an action. Use GA4 snake_case names (`add_to_cart`, `purchase`, …). */
    @JvmOverloads
    public fun track(name: String, properties: Map<String, Any?>? = null) {
        if (!validEventName(name)) return
        run("track") { it.track(name, properties.toJsonObject()) }
    }

    /** Sends a GA4 `purchase` event. */
    public fun purchase(purchase: SferePurchase) {
        run("purchase") { it.track("purchase", purchase.toJson()) }
    }

    /** Associates the device with a user. Traits (email, phone, …) are sent as plain values. */
    @JvmOverloads
    public fun identify(userId: String?, traits: Map<String, Any?>? = null) {
        run("identify") {
            if (userId.isNullOrBlank()) it.identify(traits.toJsonObject()) else it.identify(userId, traits.toJsonObject())
        }
    }

    /** Records a screen view. */
    @JvmOverloads
    public fun screen(name: String, properties: Map<String, Any?>? = null, category: String = "") {
        run("screen") { it.screen(name, properties.toJsonObject(), category) }
    }

    @JvmOverloads
    public fun group(groupId: String, traits: Map<String, Any?>? = null) {
        run("group") { it.group(groupId, traits.toJsonObject()) }
    }

    public fun alias(newId: String) {
        run("alias") { it.alias(newId) }
    }

    /**
     * Records a deep link that arrived in `onNewIntent`. Links that start an Activity are already
     * recorded when `trackDeepLinks` is on, so do not call this from `onCreate` as well.
     */
    @JvmOverloads
    public fun openUrl(intent: Intent?, referrer: String? = null) {
        run("openUrl") { it.openUrl(referrer, intent) }
    }

    /** Records a deep link given as a [Uri], e.g. from a link SDK's callback. */
    @JvmOverloads
    public fun openUrl(uri: Uri?, referrer: String? = null) {
        if (uri == null) return
        openUrl(uri.toString(), referrer)
    }

    /** Records a deep link given as a URL string. Click IDs and UTMs in it apply to the rest of the session. */
    @JvmOverloads
    public fun openUrl(url: String, referrer: String? = null) {
        run("openUrl") {
            val properties = LinkedHashMap<String, JsonPrimitive>()
            properties["url"] = JsonPrimitive(url)
            if (!referrer.isNullOrEmpty()) properties["referring_application"] = JsonPrimitive(referrer)
            it.track(EventNames.DEEP_LINK_OPENED, JsonObject(properties))
        }
    }

    /** Replaces the consent categories sent on every later event. `null` means unknown (omitted). */
    public fun setConsent(consent: Map<String, Boolean>?) {
        run("setConsent") { consentStore?.set(consent) }
    }

    /**
     * Opt-out: `false` stops collection and uploads, deletes events not yet uploaded, and is
     * remembered across launches.
     */
    public fun setEnabled(enabled: Boolean) {
        run("setEnabled") {
            it.enabled = enabled
            store?.putString(ENABLED_KEY, if (enabled) null else "false")
            if (!enabled) it.purgeStorage()
        }
    }

    public val isEnabled: Boolean
        get() = analytics?.enabled ?: false

    /** Logout: new anonymous id, no user id or traits, a new session, and no stored click IDs. */
    public fun reset() {
        run("reset") { it.reset() }
    }

    /** Uploads queued events now. */
    public fun flush() {
        run("flush") { it.flush() }
    }

    public fun anonymousId(): String? = analytics?.let { safely { it.anonymousId() } }

    public fun userId(): String? = analytics?.let { safely { it.userId() } }

    private inline fun run(action: String, block: (Analytics) -> Unit) {
        val analytics = this.analytics
        if (analytics == null) {
            SfereLog.debug("$action ignored: Sfere.setup has not completed successfully.")
            return
        }
        try {
            block(analytics)
        } catch (e: Exception) {
            SfereLog.debug("$action failed: $e")
        }
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        null
    }

    private fun validEventName(name: String): Boolean {
        if (name.isBlank()) {
            SfereLog.debug("track ignored: the event name is empty.")
            return false
        }
        if (name.length > MAX_EVENT_NAME_LENGTH) {
            SfereLog.debug("Event name \"$name\" is over $MAX_EVENT_NAME_LENGTH characters; the endpoint may truncate it.")
        }
        if (!SNAKE_CASE.matches(name)) SfereLog.debug("Event name \"$name\" is not snake_case. Use GA4 names, e.g. add_to_cart.")
        return true
    }
}

private val SNAKE_CASE = Regex("^[a-z][a-z0-9_]*$")
