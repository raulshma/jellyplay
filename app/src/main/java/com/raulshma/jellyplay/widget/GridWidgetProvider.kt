package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.raulshma.jellyplay.widget.skeleton.WidgetProviderSkeleton
import com.raulshma.jellyplay.widget.skeleton.notifyProviderDataChanged
import kotlinx.coroutines.cancel

/**
 * The provider-lifecycle fold shared by the two recommendation-grid providers
 * (`LibraryRecommendationsWidget` / `SeerrRecommendationsWidget`), which used
 * to hand-copy the same five overrides. Each concrete provider supplies the
 * small spec values that actually differ plus two seams:
 *
 *  - [gridViewId] / [refreshAction] — the collection view the launcher notify
 *    targets and the refresh broadcast this provider owns (both must stay
 *    matched with the manifest receiver and the views the grid renders);
 *  - [updateWidget] — renders one bound instance; the per-provider
 *    `updateAppWidget` template stays in the subclass companion because its
 *    layout/view/service spec and PendingIntent request-code namespace are
 *    provider-private (the request codes are a WorkManager-external match key
 *    — existing launchers can still resolve/cancel the refresh PendingIntents,
 *    so their values may not drift);
 *  - [refreshNow] — the immediate-refresh entry into the
 *    [WidgetWorkScheduler], run inside the broadcast's `goAsync()` window by
 *    [triggerInitialRefresh] and the refresh broadcast.
 *
 * The Seerr grid's batch-level server-configured read stays a per-batch
 * concern: [onUpdateWidgets] is the open render loop [onUpdate] walks, and the
 * Seerr provider overrides it to hoist the read out of the per-id renders.
 */
abstract class GridWidgetProvider : WidgetProviderSkeleton() {

    /** This grid's collection view — the launcher-notify target. */
    protected abstract val gridViewId: Int

    /** The refresh broadcast action this provider owns. */
    protected abstract val refreshAction: String

    /**
     * Renders one bound instance of this grid — the subclass companion's
     * `updateAppWidget` template.
     */
    protected abstract fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    )

    /**
     * The grid's immediate-refresh entry into the [WidgetWorkScheduler]
     * (`refreshLibraryNow` / `refreshSeerrNow`); suspension happens inside the
     * caller's `goAsync()` window.
     */
    protected abstract suspend fun refreshNow(context: Context)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        onUpdateWidgets(context, appWidgetManager, appWidgetIds)
        triggerInitialRefresh(context)
    }

    /**
     * The per-batch render loop behind [onUpdate]. Open so a provider can
     * hoist a batch-level read out of the per-id renders (the Seerr grid's
     * server-configured check).
     */
    protected open fun onUpdateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    /**
     * The first-refresh trigger shared by [onUpdate] and [onEnabled]: the
     * grid's cache is cold on widget creation, so ask the scheduler for an
     * immediate refresh inside the broadcast's `goAsync()` window.
     */
    protected fun triggerInitialRefresh(context: Context) = launchWithPendingResult {
        refreshNow(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, gridViewId)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        triggerInitialRefresh(context)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        refreshScope.cancel()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == refreshAction) {
            notifyProviderDataChanged(context, this::class.java, gridViewId)
            launchWithPendingResult {
                refreshNow(context)
            }
        }
    }
}
