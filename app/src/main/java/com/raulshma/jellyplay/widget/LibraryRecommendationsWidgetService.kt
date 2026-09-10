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
import com.raulshma.jellyplay.widget.skeleton.libraryRowSubtitle
import com.raulshma.jellyplay.widget.skeleton.libraryRowTitle
import org.koin.mp.KoinPlatform

/**
 * Backs the Library Recommendations widget's `GridView` with a
 * [RemoteViewsFactory] that loads cached items from
 * [WidgetDataStore.libraryWidgetItems].
 *
 * The factory is an adapter over [WidgetGridFactory], which owns the
 * lifecycle choreography (memory-first snapshot + poster read → dims refresh
 * → async warmup repaint — STA-11; deep-link `getViewAt`); this class
 * supplies only the Library seams: the store accessor, the row's view ids,
 * its title/subtitle decisions, and the `jellyfin://media/{id}` fill-in
 * link.
 *
 * `onDataSetChanged` runs on the main thread; items are read from the
 * store's eagerly-warmed [kotlinx.coroutines.flow.StateFlow] snapshot —
 * memory-only, no DataStore disk IO (STA-11: the former bounded ≤1 s
 * blocking warm-up read is gone from the bind; a cold snapshot renders the
 * empty view and the skeleton's async tail repaints once the eager flow
 * lands). The [LibraryRecommendationsWidget] calls
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
        remoteAdapterViewId = R.id.lr_widget_grid,
    ) {

        // STA-11: memory-only — the StateFlow's current value; the store's
        // *Snapshot() accessor (bounded BLOCKING disk read when cold) is
        // deliberately NOT taken on the bind path anymore.
        override fun snapshotProvider(): List<LibraryWidgetItem> = store.libraryWidgetItems.value

        /**
         * STA-11: the async tail's cold-snapshot wait — the skeleton's
         * [awaitWarmed] on this store's flow.
         */
        override suspend fun awaitWarmedSnapshot(): List<LibraryWidgetItem>? =
            awaitWarmed(store.libraryWidgetItems)

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

            // Responsive text-container rule + poster-or-placeholder tail —
            // the skeleton's bindGridCellTail.
            bindGridCellTail(
                view = view,
                item = item,
                textContainerViewId = R.id.lr_item_text_container,
                posterViewId = R.id.lr_item_poster,
            )
        }

        override fun loadingView(): RemoteViews =
            gridCellLoadingView(
                layoutRes = R.layout.library_recommendations_item,
                textContainerViewId = R.id.lr_item_text_container,
            )
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_lr_item_id"
    }
}
