package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.EventPlugin
import com.segment.analytics.kotlin.core.platform.Plugin
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal data class LibraryStamp(val name: String, val version: String, val base: String)

/**
 * Everything Sfere adds to an event, in one place so the order is explicit:
 * identity, rename, session, link click IDs, then the context fields and `sfere_dedup_id`.
 */
internal class SfereEnrichmentPlugin(
    private val session: SessionTracker,
    private val clicks: ClickIdStore,
    private val consent: ConsentStore,
    private val clock: Clock,
    private val library: LibraryStamp,
    private val identities: IdentityTracker = IdentityTracker(),
) : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    // The latest reset whose session clearing has run, and the session it ended; see [Identity.resets].
    private var resetsApplied = 0
    private var sessionBeforeReset: Long? = null

    override fun execute(event: BaseEvent): BaseEvent? = try {
        enrich(event)
    } catch (e: Exception) {
        SfereLog.debug("enrichment failed: $e")
        event
    }

    private fun enrich(event: BaseEvent): BaseEvent {
        val stamp = event.enrichment as? IdentityStamp
        val identity = stamp?.identity ?: identities.current
        identity?.let { applyIdentity(event, it, stamped = stamp != null) }

        if (event is TrackEvent) {
            event.event = EventNames.rename(event.event)
            if (event.event == EventNames.DEEP_LINK_OPENED) {
                event.properties = EventNames.deepLinkProperties(event.properties)
            }
        }

        val tick = sessionTick(identity)
        if (tick.isNew) {
            clicks.clear()
            val isSessionStart = event is TrackEvent && event.event == EventNames.SESSION_STARTED
            if (!isSessionStart) emitSessionStarted(event, identity)
        }

        if (event is TrackEvent && event.event == EventNames.DEEP_LINK_OPENED) {
            val url = (event.properties["url"] as? JsonPrimitive)?.contentOrNull
            clicks.merge(ClickIdParser.parse(url, clock.nowMillis()))
        }

        event.context = buildContext(event, tick.id, identity)

        if (event is TrackEvent) {
            val dedup = event.properties["sfere_dedup_id"]
            if (dedup == null || dedup is kotlinx.serialization.json.JsonNull) {
                event.properties = JsonObject(event.properties + ("sfere_dedup_id" to JsonPrimitive(event.messageId)))
            }
        }
        return event
    }

    private fun buildContext(event: BaseEvent, sessionId: Long, identity: Identity?): JsonObject {
        val context = LinkedHashMap<String, JsonElement>(event.context)
        val snapshot = clicks.snapshot()

        val clientIds = LinkedHashMap<String, JsonElement>()
        snapshot.clientIds.forEach { (k, v) -> clientIds[k] = JsonPrimitive(v) }
        (context["clientIds"] as? JsonObject)?.let { clientIds.putAll(it) } // the caller's values win
        if (clientIds.isNotEmpty()) context["clientIds"] = JsonObject(clientIds)

        if (snapshot.campaign.isNotEmpty()) {
            val campaign = LinkedHashMap<String, JsonElement>()
            (context["campaign"] as? JsonObject)?.let { campaign.putAll(it) }
            snapshot.campaign.forEach { (k, v) -> campaign[k] = JsonPrimitive(v) }
            context["campaign"] = JsonObject(campaign)
        }

        if (event !is IdentifyEvent) {
            val traits = if (identity != null) {
                identity.traits
            } else {
                try {
                    analytics.traits()
                } catch (e: Exception) {
                    null
                }
            }
            if (!traits.isNullOrEmpty()) {
                val merged = LinkedHashMap<String, JsonElement>(traits)
                (context["traits"] as? JsonObject)?.let { merged.putAll(it) }
                context["traits"] = JsonObject(merged)
            }
        }

        context["session"] = buildJsonObject { put("id", sessionId) }
        context["library"] = buildJsonObject {
            put("name", library.name)
            put("version", library.version)
            put("base", library.base)
        }
        consent.get()?.let { prefs ->
            context["consent"] = buildJsonObject {
                put("categoryPreferences", JsonObject(prefs.mapValues { JsonPrimitive(it.value) }))
            }
        }
        return JsonObject(context)
    }

    /** `session_started` gets the timestamp and the identity of the event that started the session. */
    private fun emitSessionStarted(trigger: BaseEvent, identity: Identity?) {
        val timestamp = trigger.timestamp
        if (identity != null) {
            analytics.track(EventNames.SESSION_STARTED, enrichment = IdentityStamp(identity, timestamp))
        } else {
            analytics.track(EventNames.SESSION_STARTED) { event ->
                event?.also { it.timestamp = timestamp }
            }
        }
    }

    // The first event called after reset() ends the session and starts a new one. The base library processes
    // events on a thread pool, so an event called before the reset may come later: it keeps the old session.
    @Synchronized
    private fun sessionTick(identity: Identity?): SessionTracker.Tick {
        if (identity != null && identity.resets > resetsApplied) {
            resetsApplied = identity.resets
            sessionBeforeReset = session.currentId()
            session.reset()
            clicks.clear()
        } else if (identity != null && identity.resets < resetsApplied) {
            sessionBeforeReset?.let { return SessionTracker.Tick(it, isNew = false) }
        }
        return session.tick()
    }

    // The base library calls this on its own threads, possibly before events called earlier are processed.
    // With an identity tracker the reset happens in sessionTick instead.
    override fun reset() {
        if (identities.current != null) return
        session.reset()
        clicks.clear()
    }
}
