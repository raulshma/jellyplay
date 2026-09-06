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
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.runBlocking
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository

/**
 * Backs the Continue Watching widget's `ListView` with a
 * [RemoteViewsFactory] that pulls the latest snapshot from
 * [WidgetDataStore.continueWatching].
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
        private val context: Context,
        private val store: WidgetDataStore,
        private val playbackRepository: PlaybackRepository,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        private var items: List<MediaItem> = emptyList()
        // Poster cache populated in [onDataSetChanged] so [getViewAt] never
        // performs network I/O on the binder thread. Keyed by the image id
        // from [WidgetImageLoader.continueWatchingPosterImageId] (the series
        // id when the row is an episode — the same key getViewAt looks up).
        private var posterCache: Map<String, Bitmap?> = emptyMap()
        private var widgetDims: WidgetDimensions? = null

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val maxCount = store.getWidgetConfigForIdSync(appWidgetId).continueWatchingItemCount
            // Memory read from the store's eagerly-warmed snapshot — no
            // DataStore disk IO on the main thread once warmed (cold-process
            // behavior: see WidgetDataStore's *Snapshot() docs).
            items = store.continueWatchingSnapshot().take(maxCount)
            // Pre-fetch posters concurrently so each `getViewAt` is a map lookup.
            // A slow URL is bounded by `WidgetImageLoader`'s internal timeout.
            val entries = items.map { item ->
                WidgetImageLoader.continueWatchingPosterEntry(item, playbackRepository)
            }
            posterCache = if (entries.isEmpty()) {
                emptyMap()
            } else {
                runBlocking {
                    WidgetImageLoader.preloadPosters(context, entries.map { it.url })
                }.let { urlToBitmap ->
                    entries.associate { it.imageId to urlToBitmap[it.url] }
                }
            }
            widgetDims = refreshWidgetDimensions(context, appWidgetId, 220)
        }

        override fun onDestroy() {
            items = emptyList()
            posterCache = emptyMap()
            widgetDims = null
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position) ?: return loadingView()
            val view = RemoteViews(context.packageName, R.layout.continue_watching_item)
            view.setTextViewText(R.id.cw_item_title, item.name)
            view.setTextViewText(R.id.cw_item_subtitle, buildSubtitle(item))

            // Apply responsive rules based on widget options
            var hideProgress = false
            var hidePoster = false

            widgetDims?.let { dims ->
                val width = dims.width

                if (width < 240) {
                    hideProgress = true
                }
                if (width < 180) {
                    hidePoster = true
                }
            }

            if (hidePoster) {
                view.setViewVisibility(R.id.cw_item_poster, View.GONE)
            } else {
                view.setViewVisibility(R.id.cw_item_poster, View.VISIBLE)
            }

            val progress = computeProgress(item)
            if (progress != null && !hideProgress) {
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

            val imageId = WidgetImageLoader.continueWatchingPosterImageId(item)
            val posterBitmap = if (!hidePoster) {
                posterCache[imageId]
            } else null

            if (!hidePoster) {
                if (posterBitmap != null) {
                    view.setImageViewBitmap(R.id.cw_item_poster, posterBitmap)
                } else {
                    view.setImageViewResource(R.id.cw_item_poster, R.drawable.ic_banner)
                }
            }

            val deepLinkUri = Uri.parse(DeepLinkGrammar.mediaLink(item.id))
            val fillIn = Intent().apply {
                action = Intent.ACTION_VIEW
                data = deepLinkUri
                putExtra(EXTRA_ITEM_ID, item.id)
            }
            view.setOnClickFillInIntent(R.id.cw_item_root, fillIn)
            return view
        }

        override fun getLoadingView(): RemoteViews = loadingView()

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        private fun loadingView(): RemoteViews {
            val view = RemoteViews(context.packageName, R.layout.continue_watching_item)
            view.setTextViewText(R.id.cw_item_title, "")
            view.setTextViewText(R.id.cw_item_subtitle, "")
            return view
        }

        private fun buildSubtitle(item: MediaItem): String {
            val parts = mutableListOf<String>()
            item.seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            item.seasonNumber?.let { season ->
                item.episodeNumber?.let { ep ->
                    parts.add("S${season}E${ep.toString().padStart(2, '0')}")
                } ?: parts.add("S$season")
            }
            return parts.joinToString(" · ")
        }

        private fun buildRemainingText(item: MediaItem, progress: Int): String {
            val totalTicks = item.runTimeTicks ?: 0L
            val posTicks = item.playbackPositionTicks ?: 0L
            val leftTicks = totalTicks - posTicks
            if (leftTicks > 0L) {
                val minsLeft = (leftTicks / 10_000_000L / 60L).toInt()
                if (minsLeft > 0) {
                    return context.getString(R.string.widget_minutes_left, minsLeft)
                }
            }
            return context.getString(R.string.widget_progress_percent, progress)
        }

        private fun computeProgress(item: MediaItem): Int? {
            val total = item.runTimeTicks ?: return null
            val pos = item.playbackPositionTicks ?: return null
            if (total <= 0L) return null
            val pct = (pos.toDouble() / total.toDouble() * 100.0).toInt()
            return pct.coerceIn(0, 100)
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_cw_item_id"
    }
}
