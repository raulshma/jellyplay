package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository

/**
 * Backs the Continue Watching widget's `ListView` with a
 * [RemoteViewsFactory] that pulls the latest snapshot from
 * [UserPreferencesStore.continueWatching].
 *
 * RemoteViewsFactory operations are synchronous, so the Hilt entry point
 * is used to grab a [UserPreferencesStore] handle and the latest list is
 * loaded with [runBlocking] on each [onDataSetChanged] call. The
 * [ContinueWatchingWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class ContinueWatchingWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun preferencesStore(): UserPreferencesStore
        fun playbackRepository(): PlaybackRepository
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        val store = entryPoint.preferencesStore()
        val playbackRepo = entryPoint.playbackRepository()
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return ContinueWatchingFactory(applicationContext, store, playbackRepo, appWidgetId)
    }

    private class ContinueWatchingFactory(
        private val context: Context,
        private val store: UserPreferencesStore,
        private val playbackRepository: PlaybackRepository,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        private var items: List<MediaItem> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            items = runBlocking { store.continueWatching.first() }
        }

        override fun onDestroy() {
            items = emptyList()
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

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                if (options != null) {
                    val config = context.resources.configuration
                    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
                    val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    var width = if (isLandscape) maxWidth else minWidth
                    var height = if (isLandscape) minHeight else maxHeight

                    if (width <= 0) width = 280
                    if (height <= 0) height = 220

                    if (width < 240) {
                        hideProgress = true
                    }
                    if (width < 180) {
                        hidePoster = true
                    }
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

            val imageId = item.seriesId ?: item.id
            val posterUrl = playbackRepository.getImageUrl(imageId, maxWidth = 300)
            val posterBitmap = if (!posterUrl.isNullOrBlank() && !hidePoster) {
                runBlocking { WidgetImageLoader.loadPoster(context, posterUrl) }
            } else null

            if (!hidePoster) {
                if (posterBitmap != null) {
                    view.setImageViewBitmap(R.id.cw_item_poster, posterBitmap)
                } else {
                    view.setImageViewResource(R.id.cw_item_poster, R.drawable.ic_banner)
                }
            }

            val deepLinkUri = Uri.parse("${DeepLinkHandler.SCHEME_CUSTOM}://media/${item.id}")
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
