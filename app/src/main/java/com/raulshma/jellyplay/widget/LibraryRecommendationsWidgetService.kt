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
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.widget.skeleton.WidgetGridFactory
import com.raulshma.jellyplay.widget.skeleton.gridCellTextVisible
import com.raulshma.jellyplay.widget.skeleton.libraryRowSubtitle
import com.raulshma.jellyplay.widget.skeleton.libraryRowTitle
import com.raulshma.jellyplay.widget.skeleton.toViewVisibility
import org.koin.mp.KoinPlatform

/**
 * Backs the Library Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [WidgetDataStore.libraryWidgetItems].
 *
 * The factory is an adapter over [WidgetGridFactory], which owns the
 * lifecycle choreography (snapshot read → poster preload → dims refresh →
 * deep-link `getViewAt`); this class supplies only the Library seams: the
 * store accessor, the row's view ids, its title/subtitle decisions, and the
 * `jellyfin://media/{id}` fill-in link.
 *
 * `onDataSetChanged` runs on the main thread; items are read from the
 * store's eagerly-warmed [kotlinx.coroutines.flow.StateFlow] snapshots, so
 * no DataStore disk IO blocks them once warmed. On a cold process the
 * first read pays one bounded (≤1 s) warm-up — see [WidgetDataStore]'s
 * *Snapshot() docs. The
 * [LibraryRecommendationsWidget] calls
 * [AppWidgetManager.notifyAppWidgetViewDataChanged] whenever the data
 * changes, which re-binds the factory.
 */
class LibraryRecommendationsWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Koin accessor (wave 8B — Hilt removal): resolved straight from the
        // application container, same shape the EntryPoint call used.
        val store: WidgetDataStore = KoinPlatform.getKoin()!!.get()
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return LibraryRecommendationsFactory(applicationContext, store, appWidgetId)
    }

    private class LibraryRecommendationsFactory(
        context: Context,
        private val store: WidgetDataStore,
        appWidgetId: Int,
    ) : WidgetGridFactory<LibraryWidgetItem>(
        context = context,
        appWidgetId = appWidgetId,
        itemLayoutRes = R.layout.library_recommendations_item,
        itemRootViewId = R.id.lr_item_root,
        titleViewId = R.id.lr_item_title,
        subtitleViewId = R.id.lr_item_subtitle,
        defaultHeightDp = WidgetLayoutThresholds.RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP,
    ) {

        override fun snapshotProvider(): List<LibraryWidgetItem> = store.libraryWidgetItemsSnapshot()

        override fun posterUrlOf(item: LibraryWidgetItem): String? = item.posterUrl

        override fun stableIdOf(item: LibraryWidgetItem): Long = item.itemId.hashCode().toLong()

        override fun fillInIntent(item: LibraryWidgetItem): Intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DeepLinkGrammar.mediaLink(item.itemId))
            putExtra(EXTRA_ITEM_ID, item.itemId)
        }

        override fun bind(view: RemoteViews, item: LibraryWidgetItem) {
            view.setTextViewText(R.id.lr_item_title, libraryRowTitle(item.name))
            view.setViewVisibility(R.id.lr_item_title, View.VISIBLE)
            view.setTextViewText(R.id.lr_item_subtitle, libraryRowSubtitle(item.year, item.communityRating))
            view.setViewVisibility(R.id.lr_item_subtitle, View.VISIBLE)

            // Apply responsive rules based on widget options
            view.setViewVisibility(
                R.id.lr_item_text_container,
                gridCellTextVisible(widgetDims).toViewVisibility(),
            )

            val bitmap = posterFor(item)
            if (bitmap != null) {
                view.setImageViewBitmap(R.id.lr_item_poster, bitmap)
            } else {
                view.setImageViewResource(R.id.lr_item_poster, R.drawable.widget_backdrop_placeholder)
            }
        }

        override fun loadingView(): RemoteViews =
            RemoteViews(context.packageName, R.layout.library_recommendations_item)
                .clearRowTexts()
                .apply {
                    setViewVisibility(
                        R.id.lr_item_text_container,
                        gridCellTextVisible(widgetDims).toViewVisibility(),
                    )
                }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_lr_item_id"
    }
}
