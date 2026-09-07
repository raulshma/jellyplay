package com.raulshma.jellyplay.widget.skeleton

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Resolves the widget store straight from the application container (wave 8B
 * — Hilt removal), or null during the process-start race where Koin is not
 * up yet — the widget cleanup must degrade to a no-op there, never crash the
 * broadcast.
 */
internal fun resolveWidgetDataStore(): WidgetDataStore? = try {
    KoinPlatform.getKoin()!!.get()
} catch (_: Exception) {
    null
}

/**
 * The choreography the four `AppWidgetProvider`s used to carry as hand
 * copies, now owned once. Each provider instance keeps its OWN scope
 * instance (the framework constructs a fresh receiver per broadcast; the
 * scope outlives the broadcast only through its in-flight jobs):
 *
 *  - [refreshScope] — the `SupervisorJob() + Dispatchers.IO` graph behind
 *    every off-main-thread widget job. `onDisabled` implementations cancel
 *    it when the last widget instance is removed (the Continue Watching
 *    provider never cancels — it has no `onDisabled` of its own, preserved
 *    as-is).
 *  - [launchWithPendingResult] — the `goAsync()` + `launch { try { … }
 *    finally { finish() } }` wrapper every refresh trigger shared. The
 *    `finally` (no catch) is deliberate: a failed job still closes the
 *    `goAsync()` window, while the failure surfaces exactly as it did before
 *    the extraction.
 *  - [launchWidgetConfigCleanup] — the `onDeleted` loop that drops each
 *    removed widget's persisted config off the main thread.
 *
 * Pure decisions for the providers' responsive ladders live in
 * [WidgetGridPolicy]; the two recommendation grids additionally share the
 * `updateAppWidget` template in [GridWidgetUpdater]. The provider id-
 * resolution triple (`getInstance` → `ComponentName` → `getAppWidgetIds`)
 * and its notify/update folds live below as [widgetIdsFor],
 * [notifyProviderDataChanged] and [updateAllProviderWidgets].
 */
abstract class WidgetProviderSkeleton : AppWidgetProvider() {

    protected val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Runs [block] off the main thread inside the `goAsync()` window the
     * current broadcast opened, always closing the window when the block
     * returns.
     */
    protected fun launchWithPendingResult(block: suspend () -> Unit) {
        val pending = goAsync()
        refreshScope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * `onDeleted` choreography shared by the Now Playing and Continue
     * Watching providers: drop each removed widget's config, off the main
     * thread. Null context/ids and an unresolvable store (process-start
     * race) degrade to no-ops — the broadcast must never crash there.
     */
    protected fun launchWidgetConfigCleanup(context: Context?, appWidgetIds: IntArray?) {
        if (context == null || appWidgetIds == null) return
        val store = resolveWidgetDataStore() ?: return
        launchWithPendingResult {
            for (id in appWidgetIds) {
                store.removeWidgetConfigForId(id)
            }
        }
    }
}

/**
 * Resolves the ids of every bound instance of [providerClass] — the
 * `AppWidgetManager.getInstance` → `ComponentName` → `getAppWidgetIds`
 * triple the widget package used to hand-copy per provider. Empty when no
 * instance is bound.
 */
internal fun widgetIdsFor(
    context: Context,
    providerClass: Class<out AppWidgetProvider>,
): IntArray = AppWidgetManager.getInstance(context)
    .getAppWidgetIds(ComponentName(context, providerClass))

/**
 * Tells the launcher that [providerClass]'s collection view went stale so its
 * remote adapter re-reads — the refresh-broadcast tail of the Continue
 * Watching and recommendation-grid providers (`onReceive` refresh actions and
 * the Continue Watching `triggerUpdate` entry). A no-op when no instance is
 * bound (`notifyAppWidgetViewDataChanged` over an empty id set).
 */
internal fun notifyProviderDataChanged(
    context: Context,
    providerClass: Class<out AppWidgetProvider>,
    viewId: Int,
) {
    AppWidgetManager.getInstance(context)
        .notifyAppWidgetViewDataChanged(widgetIdsFor(context, providerClass), viewId)
}

/**
 * Updates every bound instance of [providerClass] via [updateAppWidget]:
 * resolve the provider's own ids, skip entirely when none are bound, run the
 * update per id, then — when [notifyGridViewId] names the provider's
 * collection view — tell the launcher that view went stale so its remote
 * adapter re-reads. This is the shape behind the persist helper's
 * Library/Seerr notify twins and the Now Playing updater's start push.
 *
 * @param updateAppWidget invoked as `(manager, appWidgetId)` per bound id;
 *   the caller's context closes over the lambda.
 * @param notifyGridViewId the provider's grid/list view id, or null when the
 *   updates alone are the whole push.
 * @return the ids that were updated — empty when none are bound, for callers
 *   that gate follow-up work on presence (the Now Playing updater's dormant
 *   start).
 */
internal fun updateAllProviderWidgets(
    context: Context,
    providerClass: Class<out AppWidgetProvider>,
    updateAppWidget: (manager: AppWidgetManager, appWidgetId: Int) -> Unit,
    notifyGridViewId: Int? = null,
): IntArray {
    // `getInstance` is a singleton accessor, so re-fetching the manager the
    // ids came from is the same instance [widgetIdsFor] resolved through —
    // the resolution triple stays owned by that one helper.
    val manager = AppWidgetManager.getInstance(context)
    val ids = widgetIdsFor(context, providerClass)
    if (ids.isEmpty()) return ids
    for (id in ids) {
        updateAppWidget(manager, id)
    }
    if (notifyGridViewId != null) {
        manager.notifyAppWidgetViewDataChanged(ids, notifyGridViewId)
    }
    return ids
}
