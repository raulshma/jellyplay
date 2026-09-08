package com.raulshma.jellyplay.widget.skeleton

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.widget.isPermanentWidgetFailure

/**
 * The refresh chassis the two recommendation workers (Library, Seerr) used to
 * carry as hand copies, now owned once — the recorded deferred "widget refresh
 * chassis" design, whose landing condition ("fold opportunistically", "fold
 * both together") is met by folding the pair together. The chassis is one
 * ordered body:
 *
 *  1. **guard** — [skipFetch] decides whether there is enough session/server
 *     state to fetch at all. A skipped worker SUCCEEDS without touching the
 *     persisted snapshot, so the widget keeps rendering the last good rows
 *     during the window before the app restores state (the blank-widget bug
 *     class the guard exists to prevent). Side effects are part of the seam:
 *     the Library flavour's best-effort `restoreSession()` rides its override,
 *     and the Seerr flavour caches its region there for [fetchItems].
 *  2. **fetch** — [fetchItems] resolves the currently configured source into
 *     raw rows.
 *  3. **empty-keep** — an empty fetch is NOT an error: succeed without
 *     persisting so the existing snapshot survives.
 *  4. **cap + map + persist** — rows are capped at [maxItems], mapped one-to-
 *     one by [mapItem] and written by the flavour's [persist] call (the
 *     WidgetPersistHelper notify twins).
 *  5. **retry-fold** — every failure folds through [logFailure] (the per-site
 *     logging arm) then [isPermanentWidgetFailure] into failure-vs-retry, all
 *     inside [runCatchingRethrowingCancellation] so WorkManager cancellation
 *     passes through.
 *
 * Adapters keep only their seams: both concrete workers are thin overrides
 * plus their source-routing fetch helpers, and their constructor signatures
 * are unchanged (AppWidgetWorkerFactory constructs them positionally). The
 * behaviour contract is pinned by the existing worker suites
 * (`LibraryRecommendationsWidgetWorkerTest`,
 * `SeerrRecommendationsWidgetWorkerTest`), which this fold leaves
 * semantically identical.
 *
 * @param maxItems the per-flavour row cap applied between the empty-keep
 *   check and the mapper (both flavours' historical `MAX_ITEMS`).
 */
abstract class RecommendationWorkerSkeleton<Raw, Item>(
    appContext: Context,
    params: WorkerParameters,
    private val maxItems: Int,
) : CoroutineWorker(appContext, params) {

    /**
     * True when the worker must skip fetching entirely (no usable
     * session/server). Called exactly once, before [fetchItems]; see the
     * class KDoc for the skip semantics.
     */
    protected abstract suspend fun skipFetch(): Boolean

    /** Fetches the raw rows for the currently configured source. */
    protected abstract suspend fun fetchItems(): List<Raw>

    /** Maps one fetched row to its persisted widget-item shape. */
    protected abstract fun mapItem(raw: Raw): Item

    /**
     * Persists the mapped rows — the flavour's `WidgetPersistHelper`
     * persist+notify call, including its `applicationContext`.
     */
    protected abstract suspend fun persist(items: List<Item>)

    /**
     * The per-flavour failure logging arm, invoked for EVERY failure before
     * the permanent/retry fold — the one place the two historical bodies
     * genuinely differed (declared divergence, preserved): the Library worker
     * logs every failure, the Seerr worker only permanent ones. Default is
     * silent.
     */
    protected open fun logFailure(error: Throwable) {}

    final override suspend fun doWork(): Result = runCatchingRethrowingCancellation {
        if (skipFetch()) {
            return@runCatchingRethrowingCancellation
        }
        val items = fetchItems()
        if (items.isEmpty()) {
            // Keep existing data instead of clearing the widget.
            return@runCatchingRethrowingCancellation
        }
        persist(items.take(maxItems).map { mapItem(it) })
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { e ->
            logFailure(e)
            if (isPermanentWidgetFailure(e)) {
                Result.failure()
            } else {
                Result.retry()
            }
        },
    )
}
