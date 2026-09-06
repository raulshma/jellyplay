package com.raulshma.jellyplay.widget

import android.content.Context
import android.content.Intent
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore

/**
 * Sends an explicit-component broadcast to [ContinueWatchingWidget] so it
 * re-reads its RemoteViewsService data.
 *
 * Explicit-component (rather than implicit) because implicit broadcasts to
 * manifest-registered receivers are blocked on Android O+, and the widget's
 * intent-filter only carries APPWIDGET_UPDATE, so we target the receiver class
 * directly to guarantee delivery in-process.
 *
 * Action string + receiver class name are owned here, not by the home ViewModel.
 */
class ContinueWatchingBroadcasterImpl(
    private val context: Context,
    private val widgetDataStore: WidgetDataStore,
    private val playbackRepository: PlaybackRepository,
) : ContinueWatchingBroadcaster {

    override fun refreshContinueWatching() {
        prewarmContinueWatchingPosters()
        val intent = Intent(ContinueWatchingWidget.ACTION_REFRESH).apply {
            setClassName(context.packageName, ContinueWatchingWidget::class.java.name)
        }
        context.sendBroadcast(intent)
    }

    /**
     * CONC-6: fire-and-forget poster prewarm for the snapshot the CW factory
     * is about to re-bind against. The data push itself (`setContinueWatching`)
     * lives in the shared home refresher; this broadcast is the app-side
     * signal that the snapshot changed, so it doubles as the prewarm hook.
     * Derives each row's url via the shared
     * [WidgetImageLoader.continueWatchingPosterEntry] rule, so the prewarm
     * and the factory cannot drift. It serves every widget instance at once
     * (per-widget item counts are unknown here), so it maps the whole
     * snapshot and lets [WidgetImageLoader.prewarmPosters] apply its batch
     * cap. The prewarm never triggers a widget update itself; the broadcast
     * in [refreshContinueWatching] remains the only rebind trigger.
     */
    private fun prewarmContinueWatchingPosters() {
        runCatching {
            val urls = widgetDataStore.continueWatchingSnapshot().mapNotNull { item ->
                WidgetImageLoader.continueWatchingPosterEntry(item, playbackRepository)
                    .url
                    .takeIf { it.isNotBlank() }
            }
            WidgetImageLoader.prewarmPosters(context, urls)
        }
    }
}
