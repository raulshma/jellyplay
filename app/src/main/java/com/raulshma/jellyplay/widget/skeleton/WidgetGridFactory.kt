package com.raulshma.jellyplay.widget.skeleton

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.widget.WidgetDimensions
import com.raulshma.jellyplay.widget.WidgetImageLoader
import com.raulshma.jellyplay.widget.refreshWidgetDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lifecycle choreography the three widget factories (Library
 * recommendations, Seerr recommendations, Continue Watching) used to carry
 * as hand copies, now owned once:
 *
 *  - `onDataSetChanged` re-reads the latest snapshot ([snapshotProvider]),
 *    reads the poster cache MEMORY-ONLY ([cachedPosters]) and refreshes the
 *    cached widget dimensions for this bind (see
 *    [onDataSetChanged]); a cold snapshot or uncached posters schedule the
 *    async repaint tail ([scheduleWarmupRepaint]) which warms both off the
 *    bind path and repaints via `notifyAppWidgetViewDataChanged`. The
 *    provider also calls that notify on resize, which re-runs the refresh.
 *  - `getViewAt` inflates the row layout, delegates the per-widget
 *    text/threshold/progress decisions to [bind] (pure cores in
 *    [WidgetGridPolicy]), then applies the deep-link fill-in intent on the
 *    row root.
 *  - `onDestroy` drops the snapshot, poster cache, and dimensions; row ids,
 *    `hasStableIds`, and the single view type are fixed here.
 *
 * Adapter seams — the recorded "Widget grid skeleton" design's
 * `(snapshotProvider, posterUrlOf, bind, stableIdOf)` hooks, plus one
 * documented adaptation:
 *  - [snapshotProvider] — the latest rows for this widget.
 *  - [posterUrlOf] — the poster url to preload for a row (null/blank falls
 *    through to the placeholder). Open only so Continue Watching can ignore
 *    it: its cache is keyed by the series image id, not the url, so it
 *    overrides the whole poster pipeline ([preloadPosters]/[posterFor])
 *    instead.
 *  - [bind] — the per-widget row decisions, supplied with the row view ids.
 *  - [stableIdOf] — the row's stable id; the `position` fallback for
 *    out-of-range reads lives here.
 *  - [fillInIntent] — the tap deep link + extras for a row. The recorded
 *    design folded this into `bind`, but all three real factories build the
 *    same ACTION_VIEW intent and apply it to the row root, so the base owns
 *    the `setOnClickFillInIntent` wiring and the seam only supplies the
 *    intent.
 *
 * @param context          application context, for row inflation and string
 *                         resources.
 * @param appWidgetId      the widget this factory instance backs; drives the
 *                         dimensions refresh.
 * @param itemLayoutRes    the row layout (one per widget).
 * @param itemRootViewId   the row root that receives the fill-in intent.
 * @param titleViewId      row title — used by [clearRowTexts].
 * @param subtitleViewId   row subtitle — used by [clearRowTexts].
 * @param defaultHeightDp  fallback height when the widget options report
 *                         `<= 0` (per-widget constant from
 *                         `WidgetLayoutThresholds`).
 * @param remoteAdapterViewId the widget's collection view (grid/list) — the
 *                         [scheduleWarmupRepaint] tail's
 *                         `notifyAppWidgetViewDataChanged` target, i.e. the
 *                         same view id the providers notify on data pushes.
 */
abstract class WidgetGridFactory<T>(
    protected val context: Context,
    protected val appWidgetId: Int,
    private val itemLayoutRes: Int,
    private val itemRootViewId: Int,
    protected val titleViewId: Int,
    protected val subtitleViewId: Int,
    private val defaultHeightDp: Int,
    private val remoteAdapterViewId: Int,
) : RemoteViewsFactory {

    private var items: List<T> = emptyList()

    // Poster cache read MEMORY-ONLY in [onDataSetChanged] so [getViewAt] is a
    // map lookup (never network I/O on the binder thread). Keyed by the
    // adapter's poster seam: [posterUrlOf] by default, the Continue Watching
    // image id for its override. Null entries are the placeholder render
    // path — the same render a failed bounded fetch produced.
    protected var posterCache: Map<String, Bitmap?> = emptyMap()
        private set

    // Dimensions resolved once per refresh (not per bind — the options read
    // is an IPC); null when the widget id is invalid or options are absent.
    protected var widgetDims: WidgetDimensions? = null
        private set

    // Fire-and-forget scope for the async repaint tail; cancelled in
    // [onDestroy] (same per-instance scope pattern as the provider
    // skeleton's refreshScope). SupervisorJob so a failed warmup pass never
    // cancels a sibling bind's pass.
    private val warmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Stable-id set the last scheduled warmup pass ran for — one
    // pass per data generation, so the notify→rebind→tail cycle can never
    // loop on posters that fail to fetch (a re-bind with the same ids finds
    // the pass already attempted and skips).
    private var warmupAttemptedIds: List<Long>? = null

    /** The latest rows for this widget (memory-only store read). */
    protected abstract fun snapshotProvider(): List<T>

    /** The poster url to preload for [item]; null/blank → placeholder. */
    protected open fun posterUrlOf(item: T): String? = null

    /**
     * The bind's poster read — memory cache ONLY, keyed exactly like
     * [posterFor] ([posterUrlOf] by default). Misses map to null (the
     * placeholder render the bounded fetch's failures always produced); the
     * async repaint tail does the actual fetching off the bind path.
     */
    protected open fun cachedPosters(items: List<T>): Map<String, Bitmap?> =
        items.mapNotNull { posterUrlOf(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .associateWith { WidgetImageLoader.cachedPoster(it) }

    /**
     * Suspends until the store's eager snapshot has materialized,
     * then returns the warmed rows this factory would render (null when the
     * bounded wait expired — a cold store that never emitted, treated as
     * "nothing to repaint"; the empty view the cold bind rendered stays).
     * Default null: only the store-backed factories override it.
     */
    protected open suspend fun awaitWarmedSnapshot(): List<T>? = null

    /**
     * Suspends until [flow] — the store's eagerly-warmed snapshot —
     * emits non-empty, under the bounded [SNAPSHOT_WARMUP_REPAINT_TIMEOUT_MS];
     * null when the wait expired (a genuinely-empty snapshot never emits, so
     * the bounded wait expires and the empty view the cold bind rendered
     * stays). Shared body of the store-backed [awaitWarmedSnapshot]
     * overrides.
     */
    protected suspend fun <E> awaitWarmed(flow: Flow<List<E>>): List<E>? =
        withTimeoutOrNull(SNAPSHOT_WARMUP_REPAINT_TIMEOUT_MS) { flow.first { it.isNotEmpty() } }

    /**
     * The per-widget row decisions: texts, visibilities, poster, progress.
     * Everything here is RemoteViews plumbing around the pure rules in
     * [WidgetGridPolicy].
     */
    protected abstract fun bind(view: RemoteViews, item: T)

    /** The row's stable id (backing `hasStableIds`). */
    protected abstract fun stableIdOf(item: T): Long

    /** The tap deep link + extras for [item], applied to the row root. */
    protected abstract fun fillInIntent(item: T): Intent

    /**
     * Batch poster fetch used by the ASYNC repaint tail so a later
     * bind resolves from memory. Default fetches every non-blank
     * [posterUrlOf] (the url-keyed grids); the bounded batch is
     * [WidgetImageLoader]'s — slow urls simply map to null and fall through
     * to the placeholder.
     */
    protected open suspend fun preloadPosters(items: List<T>): Map<String, Bitmap?> =
        WidgetImageLoader.fetchPosters(context, items.mapNotNull { posterUrlOf(it) })

    /** The bitmap for [item]'s cell, or null for the placeholder. */
    protected open fun posterFor(item: T): Bitmap? = posterCache[posterUrlOf(item)]

    /**
     * The empty row shown while the grid loads. Default clears the title and
     * subtitle (and marks them INVISIBLE) via [clearRowTexts]; the Continue
     * Watching row does not dim its texts, so it overrides with a plain
     * clear.
     */
    protected abstract fun loadingView(): RemoteViews

    /**
     * Clears the row's texts for loading — empty strings plus INVISIBLE, so
     * a blank cell renders instead of stale text.
     */
    protected fun RemoteViews.clearRowTexts(): RemoteViews = apply {
        setTextViewText(titleViewId, "")
        setTextViewText(subtitleViewId, "")
        setViewVisibility(titleViewId, View.INVISIBLE)
        setViewVisibility(subtitleViewId, View.INVISIBLE)
    }

    /**
     * The bind tail the two recommendation grids (Library, Seerr) used to
     * hand-copy: the responsive text-container rule
     * (`gridCellTextVisible(widgetDims)`) followed by the poster-or-
     * placeholder image (bitmap from the poster pipeline, else the shared
     * `widget_backdrop_placeholder`). The Continue Watching factory is a
     * declared diverged variant — its poster is gated on the row's own
     * visibility ladder and falls back to `ic_banner` — so it never calls
     * this; its `bind` override keeps its own tail.
     *
     * @param textContainerViewId the cell's text container (the responsive
     *   rule's target).
     * @param posterViewId        the cell's poster image view.
     */
    protected fun bindGridCellTail(
        view: RemoteViews,
        item: T,
        textContainerViewId: Int,
        posterViewId: Int,
    ) {
        // Apply responsive rules based on widget options
        view.setViewVisibility(
            textContainerViewId,
            gridCellTextVisible(widgetDims).toViewVisibility(),
        )

        val bitmap = posterFor(item)
        if (bitmap != null) {
            view.setImageViewBitmap(posterViewId, bitmap)
        } else {
            view.setImageViewResource(posterViewId, R.drawable.widget_backdrop_placeholder)
        }
    }

    /**
     * The loading row the two recommendation grids share — the inflated cell
     * with cleared texts plus the same responsive text-container rule
     * [bindGridCellTail] applies, so a loading cell never flashes its text
     * container against the ladder. The Continue Watching factory overrides
     * with a plain clear (declared divergence — no INVISIBLE pass, no
     * container rule).
     */
    protected fun gridCellLoadingView(layoutRes: Int, textContainerViewId: Int): RemoteViews =
        RemoteViews(context.packageName, layoutRes)
            .clearRowTexts()
            .apply {
                setViewVisibility(
                    textContainerViewId,
                    gridCellTextVisible(widgetDims).toViewVisibility(),
                )
            }

    final override fun onCreate() = Unit

    final override fun onDataSetChanged() {
        // Memory-first bind. This used to read
        // the snapshot through the store's *Snapshot() accessors (ONE bounded
        // ≤1 s BLOCKING disk read on a cold process — WidgetDataStore's
        // SNAPSHOT_WARMUP_TIMEOUT_MS) and then runBlocking the batch poster
        // preload (≤2 s more — WidgetImageLoader's deadline), all on the
        // thread onDataSetChanged is posted to: worst case ~3 s stalling the
        // main thread while the launcher's binder waits on this factory. The
        // bind now reads ONLY memory — the store's eagerly-warmed StateFlow
        // value and WidgetImageLoader's poster cache — and returns
        // immediately; a cold snapshot renders the existing empty path
        // (getCount 0 → the widget's setEmptyView view), a poster miss the
        // existing placeholder, both exactly the render shapes a slow
        // bounded fetch produced before. The async repaint tail below then
        // warms both off the bind path and repaints via
        // notifyAppWidgetViewDataChanged (the documented platform pattern:
        // empty list + notify after the async load), so the launcher never
        // sees a stall and the RemoteViewsFactory contract holds — `items`
        // is non-null and count-stable until that notify re-binds.
        items = snapshotProvider()
        posterCache = if (items.isEmpty()) {
            emptyMap()
        } else {
            cachedPosters(items)
        }
        widgetDims = refreshWidgetDimensions(context, appWidgetId, defaultHeightDp)
        maybeScheduleWarmupRepaint()
    }

    /**
     * Decision half: schedule the async repaint tail when this bind
     * came back cold (empty memory snapshot) or with uncached posters — at
     * most ONE tail per data generation (stable-id set), so a
     * notify→rebind→tail cycle can never loop on posters that fail to
     * fetch; a genuinely-empty warm snapshot skips re-scheduling on the
     * rebind because the id set is unchanged.
     */
    private fun maybeScheduleWarmupRepaint() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val coldSnapshot = items.isEmpty()
        val posterMisses = posterCache.values.any { it == null }
        if (!coldSnapshot && !posterMisses) return
        val ids = items.map { stableIdOf(it) }
        if (warmupAttemptedIds == ids) return
        warmupAttemptedIds = ids
        scheduleWarmupRepaint(coldSnapshot)
    }

    /**
     * Tail half: off the bind path, await the store's eager snapshot
     * when the bind was cold ([awaitWarmedSnapshot]), batch-fetch the
     * missing posters ([preloadPosters] — bounded by WidgetImageLoader's
     * own batch deadline, filling the same memory cache the next bind
     * reads), then repaint via notifyAppWidgetViewDataChanged — ONLY when
     * the pass actually changed what the next bind would render (warmed
     * rows, or at least one fetched poster), so a fully-failed pass never
     * churns the launcher. This tail also subsumes the "notify after
     * prewarm" concern for the worker pushes: WidgetPersistHelper's
     * fire-and-forget prewarm races its own notify, and whichever wins, a
     * re-bind with misses finds this tail to finish the job. Open for the
     * Robolectric suite, which records the call instead of launching.
     */
    protected open fun scheduleWarmupRepaint(coldSnapshot: Boolean) {
        val bound = items
        warmupScope.launch {
            runCatchingRethrowingCancellation {
                val warmed = if (coldSnapshot) awaitWarmedSnapshot() else bound
                if (warmed.isNullOrEmpty()) return@runCatchingRethrowingCancellation
                val posters = preloadPosters(warmed)
                if (coldSnapshot || posters.values.any { it != null }) {
                    AppWidgetManager.getInstance(context)
                        .notifyAppWidgetViewDataChanged(appWidgetId, remoteAdapterViewId)
                }
            }
        }
    }

    final override fun onDestroy() {
        items = emptyList()
        posterCache = emptyMap()
        widgetDims = null
        // The factory is going away — drop any still-running warmup
        // pass with it (a completed pass's repaint targeted this widget id
        // and is already inert).
        warmupScope.cancel()
    }

    final override fun getCount(): Int = items.size

    final override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position) ?: return loadingView()
        val view = RemoteViews(context.packageName, itemLayoutRes)
        bind(view, item)
        view.setOnClickFillInIntent(itemRootViewId, fillInIntent(item))
        return view
    }

    final override fun getLoadingView(): RemoteViews = loadingView()

    final override fun getViewTypeCount(): Int = 1

    final override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.let { stableIdOf(it) } ?: position.toLong()

    final override fun hasStableIds(): Boolean = true

    companion object {
        /**
         * Bound on the async tail's wait for the store's eager
         * snapshot to materialize ([awaitWarmedSnapshot]) — deliberately
         * generous (a local file read) because nobody blocks on it: the tail
         * is fire-and-forget off the bind path.
         */
        const val SNAPSHOT_WARMUP_REPAINT_TIMEOUT_MS = 5_000L
    }
}
