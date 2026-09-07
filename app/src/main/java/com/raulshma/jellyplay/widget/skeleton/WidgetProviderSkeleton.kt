package com.raulshma.jellyplay.widget.skeleton

import android.appwidget.AppWidgetProvider
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
 * `updateAppWidget` template in `GridWidgetUpdater`.
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
