package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.runBlocking

/**
 * Backs the Seerr Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [WidgetDataStore.seerrWidgetItems].
 *
 * `onDataSetChanged` is posted to the main-thread handler (only `getViewAt`
 * runs on a background thread); items are read from the store's eagerly
 * warmed [kotlinx.coroutines.flow.StateFlow] snapshots, so no DataStore
 * disk IO blocks them once warmed — on a cold process the first read pays
 * one bounded (≤1 s) warm-up (see [WidgetDataStore]'s *Snapshot() docs).
 */
class SeerrRecommendationsWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Koin accessor (wave 8B — Hilt removal): resolved straight from the
        // application container, same shape the EntryPoint call used.
        val store: WidgetDataStore = KoinPlatform.getKoin()!!.get()
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return SeerrRecommendationsFactory(applicationContext, store, appWidgetId)
    }

    private class SeerrRecommendationsFactory(
        private val context: Context,
        private val store: WidgetDataStore,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        private var items: List<SeerrWidgetItem> = emptyList()
        private var loadedVersion: Long = -1L
        // Poster cache populated in [onDataSetChanged] so [getViewAt] never
        // performs network I/O on the binder thread.
        private var posterCache: Map<String, Bitmap?> = emptyMap()
        private var widgetDims: WidgetDimensions? = null

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            // Always re-read the latest snapshot. See
            // [LibraryRecommendationsWidgetService.onDataSetChanged] for the
            // rationale: skipping loads on unchanged versions left the widget
            // blank when the factory was recreated after a worker run that
            // short-circuited the version bump.
            // Memory reads from the store's eagerly-warmed snapshots — no
            // DataStore disk IO on the main thread once warmed (cold-process
            // behavior: see WidgetDataStore's *Snapshot() docs).
            val (version, fresh) = store.seerrWidgetVersion.value to store.seerrWidgetItemsSnapshot()
            items = fresh
            loadedVersion = version
            // Pre-fetch posters concurrently so each `getViewAt` is a map lookup.
            // `posterUrl` is nullable; blank/null entries fall through to the placeholder.
            posterCache = if (items.isEmpty()) {
                emptyMap()
            } else {
                val nonNullUrls = items.map { it.posterUrl }.filterNotNull()
                runBlocking { WidgetImageLoader.fetchPosters(context, nonNullUrls) }
            }
            widgetDims = refreshWidgetDimensions(context, appWidgetId, 250)
        }

        override fun onDestroy() {
            items = emptyList()
            posterCache = emptyMap()
            widgetDims = null
        }

        override fun getCount(): Int = items.size

        override fun getViewAt(position: Int): RemoteViews {
            val item = items.getOrNull(position) ?: return loadingView()
            val view = RemoteViews(context.packageName, R.layout.seerr_recommendations_item)
            view.setTextViewText(R.id.sr_item_title, item.title)
            view.setViewVisibility(R.id.sr_item_title, View.VISIBLE)
            view.setTextViewText(R.id.sr_item_subtitle, buildSubtitle(item))
            view.setViewVisibility(R.id.sr_item_subtitle, View.VISIBLE)
            val rating = item.voteAverage
            if (rating != null && rating > 0f) {
                view.setTextViewText(R.id.sr_item_rating, "★ %.1f".format(rating))
                view.setViewVisibility(R.id.sr_item_rating, View.VISIBLE)
            } else {
                view.setViewVisibility(R.id.sr_item_rating, View.GONE)
            }

            // Apply responsive rules based on widget options
            val hideText = widgetDims?.isTooSmallForText() == true

            if (hideText) {
                view.setViewVisibility(R.id.sr_item_text_container, View.GONE)
            } else {
                view.setViewVisibility(R.id.sr_item_text_container, View.VISIBLE)
            }

            val bitmap = posterCache[item.posterUrl]
            if (bitmap != null) {
                view.setImageViewBitmap(R.id.sr_item_poster, bitmap)
            } else {
                view.setImageViewResource(R.id.sr_item_poster, R.drawable.widget_backdrop_placeholder)
            }

            val fillIn = Intent().apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(
                    DeepLinkGrammar.seerrLink(item.tmdbId, item.mediaType),
                )
                putExtra(EXTRA_TMDB_ID, item.tmdbId)
                putExtra(EXTRA_MEDIA_TYPE, item.mediaType)
            }
            view.setOnClickFillInIntent(R.id.sr_item_root, fillIn)
            return view
        }

        override fun getLoadingView(): RemoteViews = loadingView()

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long =
            items.getOrNull(position)?.let { (it.tmdbId.toLong() shl 8) or it.mediaType.hashCode().toLong() }
                ?: position.toLong()

        override fun hasStableIds(): Boolean = true

        private fun loadingView(): RemoteViews {
            val view = RemoteViews(context.packageName, R.layout.seerr_recommendations_item)
            view.setTextViewText(R.id.sr_item_title, "")
            view.setTextViewText(R.id.sr_item_subtitle, "")
            view.setViewVisibility(R.id.sr_item_title, View.INVISIBLE)
            view.setViewVisibility(R.id.sr_item_subtitle, View.INVISIBLE)

            val hideText = widgetDims?.isTooSmallForText() == true
            if (hideText) {
                view.setViewVisibility(R.id.sr_item_text_container, View.GONE)
            } else {
                view.setViewVisibility(R.id.sr_item_text_container, View.VISIBLE)
            }
            return view
        }

        private fun buildSubtitle(item: SeerrWidgetItem): String {
            val parts = mutableListOf<String>()
            parts += item.subtitle.orEmpty()
            item.year?.let { parts.add(it.toString()) }
            return parts.filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { " " }
        }
    }

    companion object {
        const val EXTRA_TMDB_ID = "extra_sr_tmdb_id"
        const val EXTRA_MEDIA_TYPE = "extra_sr_media_type"
    }
}
