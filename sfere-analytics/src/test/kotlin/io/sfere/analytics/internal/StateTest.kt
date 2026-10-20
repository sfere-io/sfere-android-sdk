package io.sfere.analytics.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {
    private val clock = FakeClock()
    private val store = InMemoryKeyValueStore()
    private val timeoutMs = 30 * 60_000L

    @Test
    fun firstTickStartsASessionAtNow() {
        val tick = SessionTracker(store, clock, timeoutMs).tick()
        assertEquals(SessionTracker.Tick(clock.now, isNew = true), tick)
    }

    @Test
    fun activityWithinTheTimeoutKeepsTheSession() {
        val tracker = SessionTracker(store, clock, timeoutMs)
        val first = tracker.tick()
        clock.now += timeoutMs - 1
        val second = tracker.tick()
        clock.now += timeoutMs - 1 // idle is measured from the last event, not the session start
        val third = tracker.tick()
        assertEquals(first.id, second.id)
        assertEquals(first.id, third.id)
        assertFalse(second.isNew || third.isNew)
    }

    @Test
    fun inactivityPastTheTimeoutRotates() {
        val tracker = SessionTracker(store, clock, timeoutMs)
        val first = tracker.tick()
        clock.now += timeoutMs + 1
        val second = tracker.tick()
        assertTrue(second.isNew)
        assertEquals(clock.now, second.id)
        assertTrue(second.id > first.id)
    }

    @Test
    fun sessionSurvivesARestart() {
        val first = SessionTracker(store, clock, timeoutMs).tick()
        clock.now += 1_000
        val afterRestart = SessionTracker(store, clock, timeoutMs).tick()
        assertEquals(SessionTracker.Tick(first.id, isNew = false), afterRestart)
    }

    @Test
    fun aCorruptStoredSessionStartsANewOne() {
        for (stored in listOf("garbage", "x:y", "5:x", "1:2:3")) {
            store.putString("sfere_session", stored)
            assertTrue("stored \"$stored\"", SessionTracker(store, clock, timeoutMs).tick().isNew)
        }
    }

    @Test
    fun resetStartsANewSessionOnTheNextEvent() {
        val tracker = SessionTracker(store, clock, timeoutMs)
        tracker.tick()
        clock.now += 5
        tracker.reset()
        assertTrue(tracker.tick().isNew)
    }
}

class ClickIdStoreTest {
    private val store = InMemoryKeyValueStore()

    @Test
    fun newerClickReplacesOlderAndOthersAreKept() {
        val clicks = ClickIdStore(store)
        clicks.merge(ClickIdParser.parse("app://p?gclid=OLD&ttclid=T&utm_source=a", 1))
        clicks.merge(ClickIdParser.parse("app://p?gclid=NEW&utm_medium=b", 2))
        val snapshot = clicks.snapshot()
        assertEquals(mapOf("gclid" to "NEW", "ttclid" to "T"), snapshot.clientIds)
        assertEquals(mapOf("source" to "a", "medium" to "b"), snapshot.campaign)
    }

    @Test
    fun sameFbclidKeepsItsOriginalTimeButANewOneReplacesIt() {
        val clicks = ClickIdStore(store)
        clicks.merge(ClickIdParser.parse("app://p?fbclid=A", 100))
        clicks.merge(ClickIdParser.parse("app://p?fbclid=A", 200))
        assertEquals("fb.1.100.A", clicks.snapshot().clientIds["fbc"])
        clicks.merge(ClickIdParser.parse("app://p?fbclid=B", 300))
        assertEquals("fb.1.300.B", clicks.snapshot().clientIds["fbc"])
    }

    @Test
    fun persistsAndClears() {
        ClickIdStore(store).merge(ClickIdParser.parse("app://p?gclid=G&utm_source=s", 1))
        val reloaded = ClickIdStore(store)
        assertEquals(mapOf("gclid" to "G"), reloaded.snapshot().clientIds)
        reloaded.clear()
        assertEquals(ClickIdStore.Snapshot(), ClickIdStore(store).snapshot())
    }

    @Test
    fun aCorruptOrPartialStoredValueIsReadSafely() {
        store.putString("sfere_click_ids", "not json")
        assertEquals(ClickIdStore.Snapshot(), ClickIdStore(store).snapshot())
        store.putString("sfere_click_ids", """{"ids":{"gclid":"G","bad":{}},"fbclid":null}""")
        assertEquals(ClickIdStore.Snapshot(clientIds = mapOf("gclid" to "G")), ClickIdStore(store).snapshot())
    }

    @Test
    fun aLinkWithoutClicksChangesNothing() {
        val clicks = ClickIdStore(store)
        clicks.merge(ClickIdParser.parse("app://p?unrelated=1", 1))
        assertEquals(ClickIdStore.Snapshot(), clicks.snapshot())
        assertNull(store.getString("sfere_click_ids"))
    }
}

class ConsentStoreTest {
    @Test
    fun unsetByDefaultThenPersisted() {
        val store = InMemoryKeyValueStore()
        assertNull(ConsentStore(store).get())
        ConsentStore(store).set(mapOf("analytics" to true, "advertising" to false))
        assertEquals(mapOf("analytics" to true, "advertising" to false), ConsentStore(store).get())
        ConsentStore(store).set(null)
        assertNull(ConsentStore(store).get())
    }

    @Test
    fun aCorruptStoredValueIsUnknownAndNonBooleansAreSkipped() {
        val store = InMemoryKeyValueStore()
        store.putString("sfere_consent", "not json")
        assertNull(ConsentStore(store).get())
        store.putString("sfere_consent", """{"analytics":true,"advertising":"yes","other":{}}""")
        assertEquals(mapOf("analytics" to true), ConsentStore(store).get())
    }
}

class SharedPreferencesKeyValueStoreTest {
    @Test
    fun readsWritesAndRemovesThroughSharedPreferences() {
        val prefs = FakeSharedPreferences()
        val store = SharedPreferencesKeyValueStore(FakeContext(prefs), "key:secret")
        assertNull(store.getString("a"))
        store.putString("a", "1")
        assertEquals("1", store.getString("a"))
        store.putString("a", null)
        assertNull(store.getString("a"))
        assertTrue(prefs.name!!.startsWith("io.sfere.analytics."))
    }

    @Test
    fun storageFailuresNeverReachTheApp() {
        val store = SharedPreferencesKeyValueStore(FakeContext(FakeSharedPreferences(failing = true)), "key:secret")
        store.putString("a", "1")
        assertNull(store.getString("a"))
    }
}
