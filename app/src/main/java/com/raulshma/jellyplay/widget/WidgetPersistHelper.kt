package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
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
            notify = { notifyLibraryWidgets(context) },
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
            notify = { notifySeerrWidgets(context) },
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

    private fun notifyLibraryWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, LibraryRecommendationsWidget::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        for (id in ids) {
            LibraryRecommendationsWidget.updateAppWidget(context, manager, id)
        }
        manager.notifyAppWidgetViewDataChanged(ids, com.raulshma.jellyplay.R.id.lr_widget_grid)
    }

    private fun notifySeerrWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, SeerrRecommendationsWidget::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        for (id in ids) {
            SeerrRecommendationsWidget.updateAppWidget(context, manager, id)
        }
        manager.notifyAppWidgetViewDataChanged(ids, com.raulshma.jellyplay.R.id.sr_widget_grid)
    }
}
