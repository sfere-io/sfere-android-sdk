package io.sfere.analytics

import android.content.Intent
import android.net.Uri
import io.sfere.analytics.internal.FakeContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** What an app sees when setup fails or never ran: a no-op instance that never throws. */
class SfereSetupTest {
    @Test
    fun anInvalidConfigReturnsTheNoOpInstance() {
        val blankKey = Sfere.setup(FakeContext(), SfereConfig(writeKey = " ", host = "https://ingest.example.com"))
        assertSame(Sfere.shared, blankKey)
        // http:// needs a debuggable app or debug = true; the fake app is neither.
        val insecureHost = Sfere.setup(FakeContext(), SfereConfig(writeKey = "key:secret", host = "http://10.0.2.2:4320"))
        assertSame(Sfere.shared, insecureHost)
    }

    @Test
    fun aSetupThatThrowsReturnsTheNoOpInstance() {
        // No ApplicationInfo: reading its flags throws inside setup, which must not reach the app.
        val context = FakeContext(appInfo = null)
        assertSame(Sfere.shared, Sfere.setup(context, SfereConfig(writeKey = "key:secret", host = "https://ingest.example.com")))
    }

    @Test
    fun everyCallOnTheNoOpInstanceIsIgnored() {
        val sfere = Sfere(null, null, null)
        sfere.track("add_to_cart")
        sfere.purchase(SferePurchase("order_1", "SAR", 1.0, emptyList()))
        sfere.identify("user_1")
        sfere.screen("Home")
        sfere.group("club_1")
        sfere.alias("user_2")
        sfere.openUrl(null as Intent?)
        sfere.openUrl(null as Uri?)
        sfere.openUrl("app://p?gclid=G")
        sfere.setConsent(mapOf("analytics" to true))
        sfere.setEnabled(false)
        sfere.reset()
        sfere.flush()
        assertFalse(sfere.isEnabled)
        assertNull(sfere.anonymousId())
        assertNull(sfere.userId())
    }
}
