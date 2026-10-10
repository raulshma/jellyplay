package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.viewmodel.IdEnricher
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One "Hidden from Next Up" list row: the excluded series' id plus the
 * hydrated metadata. [item] stays null while the detail fetch has not
 * succeeded (in flight, offline, or the series vanished from the server) —
 * the row still renders and still restores by id, so a stale exclusion is
 * never un-restorable just because its metadata is.
 */
@Immutable
data class NextUpExcludedSeries(
    val id: String,
    val item: MediaItem? = null,
)

/** The management screen's snapshot: the rows plus the hydration-in-flight flag. */
@Immutable
data class NextUpExcludedUiState(
    val series: List<NextUpExcludedSeries> = emptyList(),
    val loading: Boolean = false,
)

/**
 * ViewModel for the "Hidden from Next Up" management screen (Route
 * .NextUpExcluded): collects the excluded-series id set from the
 * home-discovery store, hydrates each id's series metadata through
 * [MediaRepository.getMediaDetail] (the cached detail read — repeated opens
 * of this screen are cache hits), and restores singly through the store's
 * read-modify-write command or in bulk through `clearNextUpExclusions`.
 *
 * Hydration is incremental and never re-fetches a failed id inside one
 * screen lifetime (a flaky fetch would otherwise retry-storm on every
 * recomposition-driven reconcile); the row falls back to a placeholder
 * rendering instead. Restores route through [PreferencesEditor] so they land
 * on the same store commands every other surface (detail menu, home
 * settings) writes through.
 */
class NextUpExcludedViewModel(
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val editor: PreferencesEditor,
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    /** The row poster's URL (primary image at the provider's default width). */
    fun posterUrl(itemId: String): String = imageUrlProvider.getImageUrl(itemId)

    private val _state = MutableStateFlow(NextUpExcludedUiState())
    val state: StateFlow<NextUpExcludedUiState> = _state.asStateFlow()

    /** Metadata resolved so far, id → item (excluded ids prune it). */
    private val resolvedItems = mutableMapOf<String, MediaItem>()

    /**
     * The id set of the latest store emission — the rows [publish] renders.
     * A hydration merge landing after a reconcile reads THIS set, so a late
     * write can only update [resolvedItems], never the published rows.
     */
    private var publishedIds: Set<String> = emptySet()

    private var hydrateJob: Job? = null

    /**
     * The hydration pass (the hand-copied sequential for-loop, now the
     * [IdEnricher] core): one detail fetch at a time (concurrency 1), the
     * merge folds the resolved item into [resolvedItems] and re-publishes,
     * and [Retry.NEVER] carries the failed-id latch — a flaky fetch is never
     * re-fetched inside one screen lifetime (no retry storm on every
     * recomposition-driven reconcile); the row falls back to its placeholder.
     */
    private val hydrater = IdEnricher<String, MediaItem>(
        scope = scope,
        concurrency = 1,
        retry = IdEnricher.Retry.NEVER,
        fetch = { id -> mediaRepository.getMediaDetail(id).getOrNull()?.item },
        merge = { id, item ->
            resolvedItems[id] = item
            publish(publishedIds, loading = false)
        },
    )

    init {
        scope.launch {
            homeDiscoveryStore.homeDiscovery
                .map { it.nextUpExcludedSeriesIds }
                .distinctUntilChanged()
                .collect { ids -> reconcile(ids) }
        }
    }

    /**
     * Re-publishes the rows for the current excluded-id set (persisted order —
     * the store's JSON array order, i.e. exclusion order) and fetches metadata
     * for every id not yet resolved or known-failed (the enricher's
     * [IdEnricher.Retry.NEVER] latch). Cancels the previous fetch pass so a
     * rapid exclude→restore pair cannot leave a stray fetch writing into state
     * after its id left the list (a late write would only update the resolved
     * map — never the published rows).
     */
    private fun reconcile(ids: Set<String>) {
        prune(ids)
        publishedIds = ids
        publish(ids, loading = false)
        val missing = ids.filter { it !in resolvedItems && !hydrater.hasFailed(it) }
        if (missing.isEmpty()) return
        publish(ids, loading = true)
        hydrateJob?.cancel()
        hydrateJob = hydrater.enrich(
            ids = missing,
            onSettled = { publish(publishedIds, loading = false) },
        )
    }

    private fun publish(ids: Set<String>, loading: Boolean) {
        _state.value = NextUpExcludedUiState(
            series = ids.map { NextUpExcludedSeries(id = it, item = resolvedItems[it]) },
            loading = loading,
        )
    }

    /** Drops resolved bookkeeping for ids no longer excluded (the enricher owns the failed latch's prune). */
    private fun prune(ids: Set<String>) {
        resolvedItems.keys.retainAll(ids)
        hydrater.pruneFailures(ids)
    }

    /** Restores one series (the row's action) — the store's RMW command. */
    fun restore(seriesId: String) {
        editor.edit { homeDiscovery.includeSeriesInNextUp(seriesId) }
    }

    /** Restores every excluded series (the top-bar "Restore all" action). */
    fun restoreAll() {
        editor.edit { homeDiscovery.clearNextUpExclusions() }
    }
}
