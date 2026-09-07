package com.raulshma.jellyplay.widget.skeleton

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.raulshma.jellyplay.widget.WidgetDimensions
import com.raulshma.jellyplay.widget.WidgetImageLoader
import com.raulshma.jellyplay.widget.refreshWidgetDimensions
import kotlinx.coroutines.runBlocking

/**
 * The lifecycle choreography the three widget factories (Library
 * recommendations, Seerr recommendations, Continue Watching) used to carry
 * as hand copies, now owned once:
 *
 *  - `onDataSetChanged` re-reads the latest snapshot ([snapshotProvider]),
 *    batch pre-fetches posters so every `getViewAt` is a cache lookup (never
 *    network I/O on the binder thread), and refreshes the cached widget
 *    dimensions for this bind; the provider calls
 *    `notifyAppWidgetViewDataChanged` on resize, which re-runs the refresh.
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
 */
abstract class WidgetGridFactory<T>(
    protected val context: Context,
    protected val appWidgetId: Int,
    private val itemLayoutRes: Int,
    private val itemRootViewId: Int,
    protected val titleViewId: Int,
    protected val subtitleViewId: Int,
    private val defaultHeightDp: Int,
) : RemoteViewsFactory {

    private var items: List<T> = emptyList()

    // Poster cache populated in [onDataSetChanged] so [getViewAt] never
    // performs network I/O on the binder thread. Keyed by the adapter's
    // poster seam: [posterUrlOf] by default, the Continue Watching image id
    // for its override.
    protected var posterCache: Map<String, Bitmap?> = emptyMap()
        private set

    // Dimensions resolved once per refresh (not per bind — the options read
    // is an IPC); null when the widget id is invalid or options are absent.
    protected var widgetDims: WidgetDimensions? = null
        private set

    /** The latest rows for this widget (store snapshot read). */
    protected abstract fun snapshotProvider(): List<T>

    /** The poster url to preload for [item]; null/blank → placeholder. */
    protected open fun posterUrlOf(item: T): String? = null

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
     * Batch poster preload so each `getViewAt` is a map lookup. Default
     * fetches every non-blank [posterUrlOf] (the url-keyed grids); the
     * bounded batch is `WidgetImageLoader`'s — slow urls simply map to null
     * and fall through to the placeholder.
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

    final override fun onCreate() = Unit

    final override fun onDataSetChanged() {
        // Always re-read the latest snapshot. The early-return that once
        // skipped loads whenever the persisted version matched the factory's
        // cached value left the widget blank if the factory was recreated
        // (new binder, process restart) and the worker happened to
        // short-circuit the version bump in WidgetPersistHelper because the
        // content was unchanged. Always reading is cheap (single DataStore
        // read) and makes the widget resilient to those edge cases. Memory
        // reads from the store's eagerly-warmed snapshots — no DataStore disk
        // IO on the main thread once warmed (cold-process behavior: see
        // WidgetDataStore's *Snapshot() docs).
        items = snapshotProvider()
        posterCache = if (items.isEmpty()) {
            emptyMap()
        } else {
            runBlocking { preloadPosters(items) }
        }
        widgetDims = refreshWidgetDimensions(context, appWidgetId, defaultHeightDp)
    }

    final override fun onDestroy() {
        items = emptyList()
        posterCache = emptyMap()
        widgetDims = null
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
}
