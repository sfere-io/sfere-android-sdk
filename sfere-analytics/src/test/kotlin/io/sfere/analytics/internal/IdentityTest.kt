package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.TrackEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The identity rules on their own: the end-to-end cases are in TransportTest. */
class IdentityTest {
    private val traits = JsonObject(mapOf("email" to JsonPrimitive("fan@example.com")))

    @Test
    fun beforeStartNothingIsStamped() {
        val tracker = IdentityTracker()
        assertNull(tracker.stamp())
        assertNull(tracker.identify("user_1", traits))
        assertNull(tracker.alias("user_2"))
        assertNull(tracker.current)
    }

    @Test
    fun theRulesFollowTheBaseLibrary() {
        val tracker = IdentityTracker()
        tracker.reset("anon-1")

        assertEquals(Identity("user_1", "anon-1", traits, resets = 1), tracker.identify("user_1", traits)!!.identity)
        // Traits only: the user id stays and the traits are replaced, as in the base library.
        val empty = JsonObject(emptyMap())
        assertEquals(Identity("user_1", "anon-1", empty, resets = 1), tracker.identify(null, empty)!!.identity)
        assertEquals("the stamp names the identity before the alias", "user_1", tracker.alias("user_2")!!.identity.userId)
        assertEquals("user_2", tracker.current!!.userId)

        tracker.reset("anon-2")
        assertEquals(Identity(null, "anon-2", null, resets = 2), tracker.stamp()!!.identity)
    }

    @Test
    fun applyingAnIdentity() {
        val track = TrackEvent(JsonObject(emptyMap()), "logout").apply { userId = "stale" }
        applyIdentity(track, Identity(null, "anon-2", null), stamped = true)
        assertEquals("", track.userId)
        assertEquals("anon-2", track.anonymousId)

        val alias = AliasEvent("user_2", "stale")
        applyIdentity(alias, Identity("user_1", "anon-1", null), stamped = true)
        assertEquals("user_2", alias.userId)
        assertEquals("user_1", alias.previousId)

        val unstamped = AliasEvent("user_2", "kept")
        applyIdentity(unstamped, Identity(null, "anon-1", null), stamped = false)
        assertEquals("kept", unstamped.previousId)
    }

    @Test
    fun theStampSetsTheTimestampOnly() {
        val event = TrackEvent(JsonObject(emptyMap()), "session_started").apply { timestamp = "now" }
        val identity = Identity("user_1", "anon-1", null)
        assertSame(event, IdentityStamp(identity, "then")(event))
        assertEquals("then", event.timestamp)
        IdentityStamp(identity)(event)
        assertEquals("then", event.timestamp)
        assertNull(IdentityStamp(identity)(null))
    }
}
