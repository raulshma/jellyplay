package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.widget.skeleton.WidgetGridFactory
import com.raulshma.jellyplay.widget.skeleton.gridCellTextVisible
import com.raulshma.jellyplay.widget.skeleton.seerrRatingText
import com.raulshma.jellyplay.widget.skeleton.seerrRowSubtitle
import com.raulshma.jellyplay.widget.skeleton.toViewVisibility
import org.koin.mp.KoinPlatform

/**
 * Backs the Seerr Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [WidgetDataStore.seerrWidgetItems].
 *
 * The factory is an adapter over [WidgetGridFactory], which owns the
 * lifecycle choreography (snapshot read → poster preload → dims refresh →
 * deep-link `getViewAt`); this class supplies only the Seerr seams: the
 * store accessor, the row's view ids (title/subtitle/rating), its subtitle
 * and star-rating decisions, and the `jellyplay://seerr/{tmdbId}/{mediaType}`
 * fill-in link.
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
        context: Context,
        private val store: WidgetDataStore,
        appWidgetId: Int,
    ) : WidgetGridFactory<SeerrWidgetItem>(
        context = context,
        appWidgetId = appWidgetId,
        itemLayoutRes = R.layout.seerr_recommendations_item,
        itemRootViewId = R.id.sr_item_root,
        titleViewId = R.id.sr_item_title,
        subtitleViewId = R.id.sr_item_subtitle,
        defaultHeightDp = WidgetLayoutThresholds.RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP,
    ) {

        override fun snapshotProvider(): List<SeerrWidgetItem> = store.seerrWidgetItemsSnapshot()

        override fun posterUrlOf(item: SeerrWidgetItem): String? = item.posterUrl

        override fun stableIdOf(item: SeerrWidgetItem): Long =
            (item.tmdbId.toLong() shl 8) or item.mediaType.hashCode().toLong()

        override fun fillInIntent(item: SeerrWidgetItem): Intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DeepLinkGrammar.seerrLink(item.tmdbId, item.mediaType))
            putExtra(EXTRA_TMDB_ID, item.tmdbId)
            putExtra(EXTRA_MEDIA_TYPE, item.mediaType)
        }

        override fun bind(view: RemoteViews, item: SeerrWidgetItem) {
            view.setTextViewText(R.id.sr_item_title, item.title)
            view.setViewVisibility(R.id.sr_item_title, View.VISIBLE)
            view.setTextViewText(R.id.sr_item_subtitle, seerrRowSubtitle(item.subtitle, item.year))
            view.setViewVisibility(R.id.sr_item_subtitle, View.VISIBLE)
            val rating = seerrRatingText(item.voteAverage)
            if (rating != null) {
                view.setTextViewText(R.id.sr_item_rating, rating)
                view.setViewVisibility(R.id.sr_item_rating, View.VISIBLE)
            } else {
                view.setViewVisibility(R.id.sr_item_rating, View.GONE)
            }

            // Apply responsive rules based on widget options
            view.setViewVisibility(
                R.id.sr_item_text_container,
                gridCellTextVisible(widgetDims).toViewVisibility(),
            )

            val bitmap = posterFor(item)
            if (bitmap != null) {
                view.setImageViewBitmap(R.id.sr_item_poster, bitmap)
            } else {
                view.setImageViewResource(R.id.sr_item_poster, R.drawable.widget_backdrop_placeholder)
            }
        }

        override fun loadingView(): RemoteViews =
            RemoteViews(context.packageName, R.layout.seerr_recommendations_item)
                .clearRowTexts()
                .apply {
                    setViewVisibility(
                        R.id.sr_item_text_container,
                        gridCellTextVisible(widgetDims).toViewVisibility(),
                    )
                }
    }

    companion object {
        const val EXTRA_TMDB_ID = "extra_sr_tmdb_id"
        const val EXTRA_MEDIA_TYPE = "extra_sr_media_type"
    }
}
