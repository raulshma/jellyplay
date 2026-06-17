package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Backs the Seerr Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [UserPreferencesStore.seerrWidgetItems].
 *
 * The factory runs on a worker thread provided by the platform, so the
 * synchronous [runBlocking] call is safe.
 */
class SeerrRecommendationsWidgetService : RemoteViewsService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun userPreferencesStore(): UserPreferencesStore
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetEntryPoint::class.java,
        )
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return SeerrRecommendationsFactory(applicationContext, entryPoint.userPreferencesStore(), appWidgetId)
    }

    private class SeerrRecommendationsFactory(
        private val context: Context,
        private val store: UserPreferencesStore,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {

        private var items: List<SeerrWidgetItem> = emptyList()
        private var loadedVersion: Long = -1L

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val version = runBlocking { store.seerrWidgetVersion.first() }
            if (version == loadedVersion) return
            items = runBlocking { store.seerrWidgetItems.first() }
            loadedVersion = version
        }

        override fun onDestroy() {
            items = emptyList()
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
            var hideText = false

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
                    if (height <= 0) height = 250

                    if (height < 200 || width < 180) {
                        hideText = true
                    }
                }
            }

            if (hideText) {
                view.setViewVisibility(R.id.sr_item_text_container, View.GONE)
            } else {
                view.setViewVisibility(R.id.sr_item_text_container, View.VISIBLE)
            }

            val bitmap = runBlocking {
                WidgetImageLoader.loadPoster(context, item.posterUrl)
            }
            if (bitmap != null) {
                view.setImageViewBitmap(R.id.sr_item_poster, bitmap)
            } else {
                view.setImageViewResource(R.id.sr_item_poster, R.drawable.widget_backdrop_placeholder)
            }

            val fillIn = Intent().apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(
                    WidgetDeepLinks.buildSeerrDeepLink(item.tmdbId, item.mediaType),
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

            var hideText = false
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
                    if (height <= 0) height = 250

                    if (height < 200 || width < 180) {
                        hideText = true
                    }
                }
            }

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
