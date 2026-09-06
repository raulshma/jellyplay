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
        val previous = store.libraryWidgetItems.first()
        val previousVersion = store.libraryWidgetVersion.first()
        prewarmSnapshotPosters(context, items) { it.posterUrl }
        val now = System.currentTimeMillis()
        val version = if (versionBumpOnly) previousVersion + 1L else now
        if (!versionBumpOnly && sameContentById(previous, items) { it.itemId }) {
            store.setLibraryWidgetItems(items, previousVersion, now)
            return
        }
        store.setLibraryWidgetItems(items, version, now)
        notifyLibraryWidgets(context)
    }

    suspend fun persistSeerrItems(
        context: Context,
        store: WidgetDataStore,
        items: List<SeerrWidgetItem>,
        versionBumpOnly: Boolean,
    ) {
        val previous = store.seerrWidgetItems.first()
        val previousVersion = store.seerrWidgetVersion.first()
        prewarmSnapshotPosters(context, items) { it.posterUrl }
        val now = System.currentTimeMillis()
        val version = if (versionBumpOnly) previousVersion + 1L else now
        if (!versionBumpOnly && sameContentById(previous, items) { it.tmdbId }) {
            store.setSeerrWidgetItems(items, previousVersion, now)
            return
        }
        store.setSeerrWidgetItems(items, version, now)
        notifySeerrWidgets(context)
    }

    /**
     * CONC-6: fire-and-forget poster-cache pre-warm for a freshly pushed
     * snapshot (off the caller's dispatcher) so the next factory bind
     * resolves from memory instead of the ≤2 s blocking preload in
     * onDataSetChanged. Both recommendation-widget persist paths share it —
     * each factory binds against the exact URLs prewarmed here, so the two
     * paths cannot drift. Runs on the unchanged-content path too; cache hits
     * are free.
     */
    private fun <T> prewarmSnapshotPosters(context: Context, items: List<T>, urlOf: (T) -> String?) {
        WidgetImageLoader.prewarmPosters(context, items.mapNotNull(urlOf))
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
