package io.sfere.sample

import android.app.Application
import io.sfere.analytics.Sfere
import io.sfere.analytics.SfereConfig

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = SampleSettings(this)
        Sfere.setup(
            this,
            SfereConfig(
                writeKey = settings.writeKey,
                host = settings.host,
                flushAt = 1,
                sessionTimeoutMinutes = settings.sessionTimeoutMinutes,
                debug = true,
            ),
        )
    }
}
