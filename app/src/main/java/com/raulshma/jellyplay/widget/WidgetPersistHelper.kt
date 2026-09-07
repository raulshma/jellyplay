package com.raulshma.jellyplay.widget

import android.content.Context
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.widget.skeleton.updateAllProviderWidgets
import kotlinx.coroutines.flow.first

internal object WidgetPersistHelper {

    suspend fun persistLibraryItems(
        context: Context,
        store: WidgetDataStore,
        items: List<LibraryWidgetItem>,
    ) {
        persistItems(
            context = context,
            items = items,
            posterUrls = items.map { it.posterUrl },
            previous = store.libraryWidgetItems.first(),
            idExtractor = { it.itemId },
            write = { store.setLibraryWidgetItems(items) },
            notify = {
                // Re-render every bound widget, then mark its grid stale so
                // the remote adapter re-reads the fresh rows.
                updateAllProviderWidgets(
                    context = context,
                    providerClass = LibraryRecommendationsWidget::class.java,
                    updateAppWidget = { manager, id ->
                        LibraryRecommendationsWidget.updateAppWidget(context, manager, id)
                    },
                    notifyGridViewId = R.id.lr_widget_grid,
                )
            },
        )
    }

    suspend fun persistSeerrItems(
        context: Context,
        store: WidgetDataStore,
        items: List<SeerrWidgetItem>,
    ) {
        persistItems(
            context = context,
            items = items,
            posterUrls = items.map { it.posterUrl },
            previous = store.seerrWidgetItems.first(),
            idExtractor = { it.tmdbId },
            write = { store.setSeerrWidgetItems(items) },
            notify = {
                // Same fan-out as the library flavour, over the Seerr grid.
                updateAllProviderWidgets(
                    context = context,
                    providerClass = SeerrRecommendationsWidget::class.java,
                    updateAppWidget = { manager, id ->
                        SeerrRecommendationsWidget.updateAppWidget(context, manager, id)
                    },
                    notifyGridViewId = R.id.sr_widget_grid,
                )
            },
        )
    }

    /**
     * The persist choreography both widget flavours share: prewarm the poster
     * cache, always write the rows, and re-render the bound widgets only when
     * the item id set actually changed — an unchanged id set cannot render
     * differently, so the launcher round-trip is skipped. The previous rows
     * are read by the caller so each store's accessors stay out of this core.
     */
    private suspend fun <T> persistItems(
        context: Context,
        items: List<T>,
        posterUrls: List<String?>,
        previous: List<T>,
        idExtractor: (T) -> Any,
        write: suspend () -> Unit,
        notify: () -> Unit,
    ) {
        WidgetImageLoader.prewarmPosters(context, posterUrls)
        write()
        if (!sameContentById(previous, items, idExtractor)) {
            notify()
        }
    }

    private fun <T> sameContentById(
        previous: List<T>,
        next: List<T>,
        idExtractor: (T) -> Any,
    ): Boolean {
        if (previous.size != next.size) return false
        val prevIds = previous.map(idExtractor).toSet()
        return next.all { idExtractor(it) in prevIds }
    }
}
