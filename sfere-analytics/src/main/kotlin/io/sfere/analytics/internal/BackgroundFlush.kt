package io.sfere.analytics.internal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.segment.analytics.kotlin.core.Analytics

/**
 * Uploads when the app goes to the background, for apps that turn lifecycle events off. With them on,
 * [SfereDestination] flushes on `app_backgrounded` instead, so that the flush includes that event.
 */
internal class BackgroundFlush(private val analytics: Analytics) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        try {
            analytics.flush()
        } catch (e: Exception) {
            SfereLog.debug("flush on background failed: $e")
        }
    }
}
