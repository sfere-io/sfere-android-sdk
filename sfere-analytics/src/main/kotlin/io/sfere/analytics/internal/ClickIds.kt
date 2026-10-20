package io.sfere.analytics.internal

import java.net.URLDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The identifier rules every Sfere SDK applies the same way (WIRE-CONTRACT §4, `packages/shared`
 * in the JS repo). Tested against `fixtures/deeplinks.json` at the repo root.
 */
internal object ClickIdRules {
    /** Landing URL parameter -> `context.clientIds` key. `fbclid` is separate: it becomes `fbc`. */
    val urlClickIds: Map<String, String> = linkedMapOf(
        "gclid" to "gclid",
        "wbraid" to "wbraid",
        "gbraid" to "gbraid",
        "ttclid" to "ttclid",
        "ScCid" to "scclid",
        "twclid" to "twclid",
        "epik" to "epik",
        "msclkid" to "msclkid",
        "li_fat_id" to "li_fat_id",
        "oppref" to "oppref",
        "rdt_cid" to "rdt_cid",
    )

    /** URL parameter -> `context.campaign` key. */
    val campaignParams: Map<String, String> = linkedMapOf(
        "utm_source" to "source",
        "utm_medium" to "medium",
        "utm_campaign" to "name",
        "utm_term" to "term",
        "utm_content" to "content",
        "gad_source" to "gad_source",
        "gad_campaignid" to "gad_campaignid",
    )

    /** The only keys the destinations read from `context.clientIds`. */
    val allowedClientIdKeys: Set<String> = setOf(
        "fbp", "fbc", "ttp", "ttclid", "scid", "scclid", "epik", "li_fat_id", "msclkid", "oppref",
        "obref", "gclid", "wbraid", "gbraid", "twclid", "rdt_cid", "clarity_uid", "clarity_sid",
    )

    /** Meta's documented click-ID format: `fb.<subdomainIndex>.<creationTimeMs>.<fbclid>`. */
    fun fbcFromFbclid(fbclid: String, nowMs: Long): String = "fb.1.$nowMs.$fbclid"
}

internal data class ParsedClicks(
    val clientIds: Map<String, String>,
    val campaign: Map<String, String>,
    val fbclid: String?,
) {
    val isEmpty: Boolean get() = clientIds.isEmpty() && campaign.isEmpty()
}

internal object ClickIdParser {
    /** Accepts a full URL (any scheme) or a bare query string such as a Play install referrer. */
    fun parse(urlOrQuery: String?, nowMs: Long): ParsedClicks {
        val params = queryParameters(urlOrQuery ?: "")
        val clientIds = LinkedHashMap<String, String>()
        for ((param, key) in ClickIdRules.urlClickIds) {
            params[param]?.takeIf { it.isNotEmpty() }?.let { clientIds[key] = it }
        }
        val fbclid = params["fbclid"]?.takeIf { it.isNotEmpty() }
        if (fbclid != null) clientIds["fbc"] = ClickIdRules.fbcFromFbclid(fbclid, nowMs)
        val campaign = LinkedHashMap<String, String>()
        for ((param, key) in ClickIdRules.campaignParams) {
            params[param]?.takeIf { it.isNotEmpty() }?.let { campaign[key] = it }
        }
        return ParsedClicks(clientIds, campaign, fbclid)
    }

    /** First value per name, percent-decoded, like `URLSearchParams.get`. The fragment is ignored. */
    internal fun queryParameters(urlOrQuery: String): Map<String, String> {
        val withoutFragment = urlOrQuery.trim().substringBefore('#')
        val query = when {
            withoutFragment.contains('?') -> withoutFragment.substringAfter('?')
            withoutFragment.contains("://") -> ""
            withoutFragment.contains('=') -> withoutFragment
            else -> ""
        }
        val result = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val name = decode(pair.substringBefore('=')) ?: continue
            val value = decode(if (pair.contains('=')) pair.substringAfter('=') else "") ?: continue
            if (name !in result) result[name] = value
        }
        return result
    }

    private fun decode(value: String): String? = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Click IDs and campaign values seen in this session's links. A newer value replaces an older one,
 * and the whole set is cleared when the session rotates.
 */
internal class ClickIdStore(private val store: KeyValueStore) {
    internal data class Snapshot(
        val clientIds: Map<String, String> = emptyMap(),
        val campaign: Map<String, String> = emptyMap(),
        val fbclid: String? = null,
    )

    private var current: Snapshot = load()

    @Synchronized
    fun snapshot(): Snapshot = current

    @Synchronized
    fun merge(parsed: ParsedClicks) {
        if (parsed.isEmpty) return
        val ids = current.clientIds.toMutableMap()
        for ((key, value) in parsed.clientIds) {
            // The same fbclid seen again keeps its original fbc creation time.
            if (key == "fbc" && parsed.fbclid != null && parsed.fbclid == current.fbclid) continue
            ids[key] = value
        }
        current = Snapshot(
            clientIds = ids,
            campaign = current.campaign + parsed.campaign,
            fbclid = parsed.fbclid ?: current.fbclid,
        )
        save()
    }

    @Synchronized
    fun clear() {
        current = Snapshot()
        store.putString(KEY, null)
    }

    private fun save() {
        val json = buildJsonObject {
            put("ids", JsonObject(current.clientIds.mapValues { JsonPrimitive(it.value) }))
            put("campaign", JsonObject(current.campaign.mapValues { JsonPrimitive(it.value) }))
            current.fbclid?.let { put("fbclid", it) }
        }
        store.putString(KEY, json.toString())
    }

    private fun load(): Snapshot = try {
        val raw = store.getString(KEY)
        if (raw == null) {
            Snapshot()
        } else {
            val json = Json.parseToJsonElement(raw).jsonObject
            Snapshot(
                clientIds = json["ids"]?.jsonObject?.stringMap().orEmpty(),
                campaign = json["campaign"]?.jsonObject?.stringMap().orEmpty(),
                fbclid = json["fbclid"]?.jsonPrimitive?.contentOrNull,
            )
        }
    } catch (e: Exception) {
        Snapshot()
    }

    private fun JsonObject.stringMap(): Map<String, String> =
        mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap()

    private companion object {
        const val KEY = "sfere_click_ids"
    }
}
