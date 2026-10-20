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
 * rename, session, link click IDs, then the context fields and `sfere_dedup_id`.
 */
internal class SfereEnrichmentPlugin(
    private val session: SessionTracker,
    private val clicks: ClickIdStore,
    private val consent: ConsentStore,
    private val clock: Clock,
    private val library: LibraryStamp,
) : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var analytics: Analytics

    override fun execute(event: BaseEvent): BaseEvent? = try {
        enrich(event)
    } catch (e: Exception) {
        SfereLog.debug("enrichment failed: $e")
        event
    }

    private fun enrich(event: BaseEvent): BaseEvent {
        if (event is TrackEvent) {
            event.event = EventNames.rename(event.event)
            if (event.event == EventNames.DEEP_LINK_OPENED) {
                event.properties = EventNames.deepLinkProperties(event.properties)
            }
        }

        val tick = session.tick()
        if (tick.isNew) {
            clicks.clear()
            val isSessionStart = event is TrackEvent && event.event == EventNames.SESSION_STARTED
            if (!isSessionStart) emitSessionStarted(event)
        }

        if (event is TrackEvent && event.event == EventNames.DEEP_LINK_OPENED) {
            val url = (event.properties["url"] as? JsonPrimitive)?.contentOrNull
            clicks.merge(ClickIdParser.parse(url, clock.nowMillis()))
        }

        event.context = buildContext(event, tick.id)

        if (event is TrackEvent) {
            val dedup = event.properties["sfere_dedup_id"]
            if (dedup == null || dedup is kotlinx.serialization.json.JsonNull) {
                event.properties = JsonObject(event.properties + ("sfere_dedup_id" to JsonPrimitive(event.messageId)))
            }
        }
        return event
    }

    private fun buildContext(event: BaseEvent, sessionId: Long): JsonObject {
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
            val traits = try {
                analytics.traits()
            } catch (e: Exception) {
                null
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

    private fun emitSessionStarted(trigger: BaseEvent) {
        val timestamp = trigger.timestamp
        analytics.track(EventNames.SESSION_STARTED) { event ->
            event?.also { it.timestamp = timestamp }
        }
    }

    override fun reset() {
        session.reset()
        clicks.clear()
    }
}
