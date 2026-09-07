package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.widget.skeleton.WidgetGridFactory
import com.raulshma.jellyplay.widget.skeleton.continueWatchingMinutesLeft
import com.raulshma.jellyplay.widget.skeleton.continueWatchingProgressPercent
import com.raulshma.jellyplay.widget.skeleton.continueWatchingRowSubtitle
import com.raulshma.jellyplay.widget.skeleton.continueWatchingRowVisibility
import com.raulshma.jellyplay.widget.skeleton.toViewVisibility
import org.koin.mp.KoinPlatform

/**
 * Backs the Continue Watching widget's `ListView` with a
 * [RemoteViewsFactory] that pulls the latest snapshot from
 * [WidgetDataStore.continueWatching].
 *
 * The factory is an adapter over [WidgetGridFactory], which owns the
 * lifecycle choreography (snapshot read → poster preload → dims refresh →
 * deep-link `getViewAt`); this class supplies the Continue Watching seams:
 * the snapshot read (capped by the widget's per-widget item count), the
 * progress-bar/remaining-text row decisions, and the
 * `jellyfin://media/{id}` fill-in link. It is also the one factory whose
 * poster cache is keyed by the image id from
 * [WidgetImageLoader.continueWatchingPosterImageId] (the series id when the
 * row is an episode) rather than by url, so it overrides the skeleton's
 * poster pipeline instead of [WidgetGridFactory.posterUrlOf].
 *
 * `onDataSetChanged` runs on the main thread; the list is read from the
 * store's eagerly-warmed [kotlinx.coroutines.flow.StateFlow] snapshot, so
 * no DataStore disk IO blocks it once warmed. On a cold process the first
 * read pays one bounded (≤1 s) warm-up — see [WidgetDataStore]'s
 * *Snapshot() docs. The
 * [ContinueWatchingWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class ContinueWatchingWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Koin accessors (wave 8B — Hilt removal): resolved straight from the
        // application container, same shape the EntryPoint call used.
        val koin = KoinPlatform.getKoin()!!
        val store: WidgetDataStore = koin.get()
        val playbackRepo: PlaybackRepository = koin.get()
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return ContinueWatchingFactory(applicationContext, store, playbackRepo, appWidgetId)
    }

    private class ContinueWatchingFactory(
        context: Context,
        private val store: WidgetDataStore,
        private val playbackRepository: PlaybackRepository,
        appWidgetId: Int,
    ) : WidgetGridFactory<MediaItem>(
        context = context,
        appWidgetId = appWidgetId,
        itemLayoutRes = R.layout.continue_watching_item,
        itemRootViewId = R.id.cw_item_root,
        titleViewId = R.id.cw_item_title,
        subtitleViewId = R.id.cw_item_subtitle,
        defaultHeightDp = WidgetLayoutThresholds.CONTINUE_WATCHING_DEFAULT_HEIGHT_DP,
    ) {

        override fun snapshotProvider(): List<MediaItem> =
            store.continueWatchingSnapshot()
                .take(store.getWidgetConfigForIdSync(appWidgetId).continueWatchingItemCount)

        // The cache is keyed by the continue-watching image id (the series id
        // when the row is an episode — the same key [posterFor] looks up), so
        // the preload maps url → bitmap back into (imageId → bitmap).
        override suspend fun preloadPosters(items: List<MediaItem>): Map<String, Bitmap?> {
            val entries = items.map { item ->
                WidgetImageLoader.continueWatchingPosterEntry(item, playbackRepository)
            }
            val urlToBitmap = WidgetImageLoader.fetchPosters(context, entries.map { it.url })
            return entries.associate { it.imageId to urlToBitmap[it.url] }
        }

        override fun posterFor(item: MediaItem): Bitmap? =
            posterCache[WidgetImageLoader.continueWatchingPosterImageId(item)]

        override fun stableIdOf(item: MediaItem): Long = item.id.hashCode().toLong()

        override fun fillInIntent(item: MediaItem): Intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DeepLinkGrammar.mediaLink(item.id))
            putExtra(EXTRA_ITEM_ID, item.id)
        }

        override fun bind(view: RemoteViews, item: MediaItem) {
            view.setTextViewText(R.id.cw_item_title, item.name)
            view.setTextViewText(
                R.id.cw_item_subtitle,
                continueWatchingRowSubtitle(item.seriesName, item.seasonNumber, item.episodeNumber),
            )

            // Apply responsive rules based on widget options
            val visibility = continueWatchingRowVisibility(widgetDims?.width)
            view.setViewVisibility(
                R.id.cw_item_poster,
                visibility.showPoster.toViewVisibility(),
            )

            val progress = continueWatchingProgressPercent(item.runTimeTicks, item.playbackPositionTicks)
            if (progress != null && visibility.showProgress) {
                view.setProgressBar(R.id.cw_item_progress, 100, progress, false)
                view.setViewVisibility(R.id.cw_item_progress, View.VISIBLE)
                view.setViewVisibility(R.id.cw_item_remaining, View.VISIBLE)
                view.setTextViewText(
                    R.id.cw_item_remaining,
                    buildRemainingText(item, progress),
                )
            } else {
                view.setProgressBar(R.id.cw_item_progress, 100, 0, false)
                view.setViewVisibility(R.id.cw_item_progress, View.GONE)
                view.setViewVisibility(R.id.cw_item_remaining, View.GONE)
            }

            if (visibility.showPoster) {
                val posterBitmap = posterFor(item)
                if (posterBitmap != null) {
                    view.setImageViewBitmap(R.id.cw_item_poster, posterBitmap)
                } else {
                    view.setImageViewResource(R.id.cw_item_poster, R.drawable.ic_banner)
                }
            }
        }

        // The Continue Watching loading row does not dim its texts (no
        // INVISIBLE pass and no text-container rule) — plain clears only.
        override fun loadingView(): RemoteViews =
            RemoteViews(context.packageName, R.layout.continue_watching_item).apply {
                setTextViewText(R.id.cw_item_title, "")
                setTextViewText(R.id.cw_item_subtitle, "")
            }

        private fun buildRemainingText(item: MediaItem, progress: Int): String {
            val minsLeft = continueWatchingMinutesLeft(item.runTimeTicks, item.playbackPositionTicks)
            if (minsLeft != null) {
                return context.getString(R.string.widget_minutes_left, minsLeft)
            }
            return context.getString(R.string.widget_progress_percent, progress)
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_cw_item_id"
    }
}
