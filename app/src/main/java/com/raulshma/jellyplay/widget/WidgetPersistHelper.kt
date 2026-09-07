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
        versionBumpOnly: Boolean,
    ) {
        persistItems(
            context = context,
            items = items,
            posterUrls = items.map { it.posterUrl },
            versionBumpOnly = versionBumpOnly,
            previous = store.libraryWidgetItems.first(),
            previousVersion = store.libraryWidgetVersion.first(),
            idExtractor = { it.itemId },
            write = { version, now -> store.setLibraryWidgetItems(items, version, now) },
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
        versionBumpOnly: Boolean,
    ) {
        persistItems(
            context = context,
            items = items,
            posterUrls = items.map { it.posterUrl },
            versionBumpOnly = versionBumpOnly,
            previous = store.seerrWidgetItems.first(),
            previousVersion = store.seerrWidgetVersion.first(),
            idExtractor = { it.tmdbId },
            write = { version, now -> store.setSeerrWidgetItems(items, version, now) },
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
     * cache, then either re-write with the previous version when the content
     * is unchanged (`versionBumpOnly` always re-stamps a new version) or stamp
     * a fresh version and update the widgets. The previous rows/version are
     * read by the caller so each store's accessors stay out of this core.
     */
    private suspend fun <T> persistItems(
        context: Context,
        items: List<T>,
        posterUrls: List<String?>,
        versionBumpOnly: Boolean,
        previous: List<T>,
        previousVersion: Long,
        idExtractor: (T) -> Any,
        write: suspend (version: Long, now: Long) -> Unit,
        notify: () -> Unit,
    ) {
        WidgetImageLoader.prewarmPosters(context, posterUrls)
        val now = System.currentTimeMillis()
        val version = if (versionBumpOnly) previousVersion + 1L else now
        if (!versionBumpOnly && sameContentById(previous, items, idExtractor)) {
            write(previousVersion, now)
            return
        }
        write(version, now)
        notify()
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
