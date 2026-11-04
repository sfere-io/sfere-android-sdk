package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import kotlinx.serialization.json.JsonObject

/** [resets] counts `reset()` calls in this process: an event called after one starts the new session. */
internal data class Identity(val userId: String?, val anonymousId: String, val traits: JsonObject?, val resets: Int = 0)

/**
 * The user's identity as of the latest call, updated on the caller's thread.
 *
 * The base library takes an event's ids when it processes the event, from two places: the stored identity,
 * which `identify` and `reset` update later on another thread, and the in-memory one, which `reset` replaces at
 * once. A `track` followed straight away by `reset()` therefore got the old user id with the new anonymous id.
 * Each call made through [io.sfere.analytics.Sfere] carries an [IdentityStamp] taken from here instead, and
 * [SfereEnrichmentPlugin] writes it onto the event. Events the base library sends itself get the identity of
 * the moment they are processed. The update rules are the base library's own (`UserInfo` actions).
 * The session and click IDs that `reset()` clears follow the same rule, through [Identity.resets].
 */
internal class IdentityTracker {
    @Volatile
    var current: Identity? = null
        private set

    /** Starts from the identity the base library loaded from storage. */
    @Synchronized
    fun start(analytics: Analytics) {
        current = Identity(analytics.userId(), analytics.anonymousId(), analytics.traits())
    }

    @Synchronized
    fun identify(userId: String?, traits: JsonObject): IdentityStamp? {
        val identity = current ?: return null
        val updated = identity.copy(userId = if (userId.isNullOrBlank()) identity.userId else userId, traits = traits)
        current = updated
        return IdentityStamp(updated)
    }

    /** The stamp names the identity before the alias: it gives the alias event its `previousId`. */
    @Synchronized
    fun alias(newId: String): IdentityStamp? {
        val identity = current ?: return null
        current = identity.copy(userId = newId)
        return IdentityStamp(identity)
    }

    @Synchronized
    fun reset(newAnonymousId: String) {
        current = Identity(userId = null, anonymousId = newAnonymousId, traits = null, resets = (current?.resets ?: 0) + 1)
    }

    fun stamp(): IdentityStamp? = current?.let { IdentityStamp(it) }
}

/**
 * The identity an event was called with. It rides on the event as the base library's enrichment closure, which
 * the base library keeps on the event and runs after the enrichment plugins; [timestamp] replaces the event's.
 */
internal class IdentityStamp(val identity: Identity, private val timestamp: String? = null) : (BaseEvent?) -> BaseEvent? {
    override fun invoke(event: BaseEvent?): BaseEvent? = event?.also { e -> timestamp?.let { e.timestamp = it } }
}

/** Writes [identity] onto [event]: an alias keeps its new user id and names the replaced id as `previousId`. */
internal fun applyIdentity(event: BaseEvent, identity: Identity, stamped: Boolean) {
    event.anonymousId = identity.anonymousId
    if (event is AliasEvent) {
        if (stamped) event.previousId = identity.userId ?: identity.anonymousId
    } else {
        event.userId = identity.userId ?: ""
    }
}
