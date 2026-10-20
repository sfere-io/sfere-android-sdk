package io.sfere.analytics.internal

import com.segment.analytics.kotlin.core.AliasEvent
import com.segment.analytics.kotlin.core.Analytics
import com.segment.analytics.kotlin.core.BaseEvent
import com.segment.analytics.kotlin.core.GroupEvent
import com.segment.analytics.kotlin.core.IdentifyEvent
import com.segment.analytics.kotlin.core.ScreenEvent
import com.segment.analytics.kotlin.core.System as SystemState
import com.segment.analytics.kotlin.core.TrackEvent
import com.segment.analytics.kotlin.core.platform.DestinationPlugin
import com.segment.analytics.kotlin.core.platform.VersionedPlugin
import com.segment.analytics.kotlin.core.platform.policies.CountBasedFlushPolicy
import com.segment.analytics.kotlin.core.platform.policies.FrequencyFlushPolicy
import com.segment.analytics.kotlin.core.retry.HttpConfig
import kotlinx.coroutines.launch
import sovran.kotlin.Subscriber

private const val MILLIS_PER_SECOND = 1000L

/**
 * Replaces the base library's own destination (which is not added): same queue, flush policies and
 * enable toggle, but the upload body goes through [SferePipeline], and settings never change the host.
 */
internal class SfereDestination(
    private val writeKey: String,
    private val apiHost: String,
    private val httpConfig: HttpConfig?,
    private val sdkVersion: String,
) : DestinationPlugin(),
    VersionedPlugin,
    Subscriber {
    override val key: String = SFERE_DESTINATION_KEY

    private var pipeline: SferePipeline? = null

    override fun track(payload: TrackEvent): BaseEvent = payload.also { enqueue(it) }
    override fun identify(payload: IdentifyEvent): BaseEvent = payload.also { enqueue(it) }
    override fun screen(payload: ScreenEvent): BaseEvent = payload.also { enqueue(it) }
    override fun group(payload: GroupEvent): BaseEvent = payload.also { enqueue(it) }
    override fun alias(payload: AliasEvent): BaseEvent = payload.also { enqueue(it) }

    private fun enqueue(payload: BaseEvent) {
        pipeline?.put(payload)
    }

    override fun setup(analytics: Analytics) {
        super.setup(analytics)
        val configuration = analytics.configuration
        val flushPolicies = configuration.flushPolicies.ifEmpty {
            listOf(
                CountBasedFlushPolicy(configuration.flushAt),
                FrequencyFlushPolicy(configuration.flushInterval * MILLIS_PER_SECOND),
            )
        }
        pipeline = SferePipeline(analytics, writeKey, flushPolicies, apiHost, httpConfig)
        analytics.analyticsScope.launch(analytics.analyticsDispatcher) {
            analytics.store.subscribe(
                subscriber = this@SfereDestination,
                stateClazz = SystemState::class,
                initialState = true,
                handler = this@SfereDestination::onEnableToggled,
            )
        }
    }

    override fun flush() {
        pipeline?.flush()
    }

    override fun shutdown() {
        pipeline?.stop()
    }

    override fun version(): String = sdkVersion

    private fun onEnableToggled(state: SystemState) {
        if (state.enabled) pipeline?.start() else pipeline?.stop()
    }
}
