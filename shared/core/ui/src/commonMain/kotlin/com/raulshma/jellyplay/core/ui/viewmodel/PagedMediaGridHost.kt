package com.raulshma.jellyplay.core.ui.viewmodel

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.ui.components.DeferredRefreshHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The paged-media-grid choreography the grid view models kept re-typing:
 * a refresh-trigger generation counter spliced into the paged query's dedup
 * key, the [DeferredUserDataRefresher] pair driving it, the `cachedIn`
 * sharing, and the quick-action surface (silent mark-played, download fold
 * forwarding, remove-download, the downloaded-id/pipeline-gate re-exposure)
 * that every card grid forwards identically. The host owns that choreography;
 * the owning ViewModel keeps only its specifics — the query key and the
 * paged source (per-VM shapes like the library's offline/static fork stay
 * caller-side, spliced into [source]).
 *
 * Collapses the hand-copied `_refreshTrigger` + `combine(...).flatMapLatest
 * { paged query }.cachedIn(scope)` + `DeferredUserDataRefresher(...)` +
 * `downloadedIds`/`downloadSupported` + `markItemPlayed`/`downloadItem`/
 * `removeItemDownload` blocks (library, favorites, studio detail, search,
 * photo album). Hosts that only page ([com.raulshma.jellyplay.feature.photos.PhotoAlbumViewModel])
 * omit everything but [source]; the refresher, id set, gate and action seams
 * are all optional and their methods no-op when absent — a host must not grow
 * a refresh trigger it does not route.
 *
 * Core/ui owns no core:data types: the download/mark seams arrive as plain
 * lambdas (the VM closes them over its injected [com.raulshma.jellyplay.core.data.download.QuickDownloadActions]
 * / mutator), so this module's dependency set is unchanged.
 *
 * [items] is lazy up to `cachedIn` — nothing subscribes until a screen
 * collects, exactly like the per-VM `flatMapLatest` pagers it replaces.
 *
 * Is a [DeferredRefreshHost] so hosts that always wire [userDataChanges]
 * expose the grid itself to their screens' `DeferredRefreshEffect` — the
 * nullable-[deferredRefresher] question never leaves this class. The
 * pager-only shape inherits the interface's no-op, matching the host's
 * optional-seam doctrine.
 */
class PagedMediaGridHost(
    /** The host view model's scope: the pager cache and action jobs die with it. */
    private val scope: CoroutineScope,
    /**
     * The paged source, parameterized by the host's refresh-trigger flow so
     * the caller splices the generation into its own dedup key
     * (`PagedQueryKey`-style `combine(...).distinctUntilChanged()`) before the
     * `flatMapLatest`. A [refresh] bump re-emits the unchanged key flows with
     * a new generation, restarting the pager fresh; ignoring the flow in a
     * source that has no generation key is what makes a host trigger-less.
     */
    private val source: (refreshTrigger: Flow<Int>) -> Flow<PagingData<MediaItem>>,
    /**
     * The user-data change feed. Non-null ⇒ the host owns a
     * [DeferredUserDataRefresher] wired to [refresh] (the off-screen changes
     * only mark the grid stale; re-entry regenerates). Null ⇒ no refresher —
     * the photo-album shape.
     */
    userDataChanges: Flow<UserDataChange>? = null,
    /** The shared quick-action id set, re-exposed for the host's screens. */
    val downloadedIds: StateFlow<Set<String>>? = null,
    /** Whether this platform has a download pipeline; screens gate the download CTA on it. */
    val downloadSupported: Boolean = false,
    /**
     * The silent mark-played seam: launched once per [markPlayed] on [scope],
     * the paged grid is left untouched so the user keeps their scroll
     * position (the mutator's silent default the VMs shared).
     */
    private val setPlayedSilently: (suspend (itemId: String, played: Boolean) -> Unit)? = null,
    /**
     * The download seam — normally the widened
     * [com.raulshma.jellyplay.core.data.download.QuickDownloadActions.downloadAndReport]
     * fold with the host's message sink and `seriesOpensSheet` routing.
     * Launched once per [download] on [scope].
     */
    private val requestDownload: (suspend (
        item: MediaItem,
        onOpenDetail: (itemId: String, prePresentDownloadSheet: Boolean) -> Unit,
        seriesOpensSheet: Boolean,
    ) -> Unit)? = null,
    /** The remove-download seam (fire-and-forget delete routing). */
    private val removeDownloadItem: ((item: MediaItem) -> Unit)? = null,
) : DeferredRefreshHost {

    private val _refreshTrigger = StateFlowHandle(MutableStateFlow(0))

    /** The paged grid — one `cachedIn` generation stream over [source]. */
    val items: Flow<PagingData<MediaItem>> = source(_refreshTrigger.flow).cachedIn(scope)

    /**
     * The deferred-refresh host for the screen's `DeferredRefreshEffect`, or
     * null when the host was built without [userDataChanges].
     */
    val deferredRefresher: DeferredUserDataRefresher? =
        userDataChanges?.let { DeferredUserDataRefresher(it, scope, _refreshTrigger) }

    /**
     * Bumps the pager generation: the paged query's key changes, the
     * `flatMapLatest` restarts as a fresh generation, and no other data is
     * touched. Manual pull-to-refresh callers keep their extra cache-bypassing
     * refetches beside this call.
     */
    fun refresh() {
        _refreshTrigger.update { it + 1 }
    }

    /** Silent mark-played — see [setPlayedSilently]. No-op when the seam is absent. */
    fun markPlayed(item: MediaItem, played: Boolean) {
        val setPlayed = setPlayedSilently ?: return
        scope.launch { setPlayed(item.id, played) }
    }

    /**
     * Long-press Download — forwards to [requestDownload] exactly once, on
     * [scope]. No-op when the seam is absent. [seriesOpensSheet] defaults to
     * false: only the library grid's navigation can pre-present the series
     * selection sheet.
     */
    fun download(
        item: MediaItem,
        onOpenDetail: (itemId: String, prePresentDownloadSheet: Boolean) -> Unit,
        seriesOpensSheet: Boolean = false,
    ) {
        val request = requestDownload ?: return
        scope.launch { request(item, onOpenDetail, seriesOpensSheet) }
    }

    /** Long-press Remove download — see [removeDownloadItem]. No-op when the seam is absent. */
    fun removeDownload(item: MediaItem) {
        removeDownloadItem?.invoke(item)
    }

    /** Screen-activity front for [deferredRefresher]; no-op in the pager-only shape. */
    override fun onScreenActiveChanged(active: Boolean) {
        deferredRefresher?.onScreenActiveChanged(active)
    }
}
