package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.offline.OfflineDeleteActions
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the offline home's advanced "delete downloaded episodes" sheet for a
 * series card: the sheet's [HomeSeriesDeleteState] (seasons, downloaded
 * episodes, sizes, loading flag) plus the four actions that drive it. The VM
 * folds [state] into [HomeUiState.seriesDelete] with one collector (same fold
 * pattern as the refresher/Seerr collectors) and forwards the action methods,
 * so the sheet's UI call sites are unchanged.
 *
 * Why a holder: the sheet state and its actions are one concern with a
 * non-obvious invariant (the snapshot-before-dismiss capture below) that had
 * zero test coverage while living on the VM — constructing the VM in a test
 * required Robolectric + the whole lifecycle stack. Here it is testable with
 * a mocked [OfflineRepository] alone.
 *
 * Deletion itself is delegated to the shared core/data [OfflineDeleteActions]
 * (whole-season collapse + per-episode fallback + unknown-id defense), the
 * same module the detail screen and downloads library use. Home's reactive
 * `offlineLibrary` Room flow refreshes on its own once rows are deleted, so
 * no content-mutated callback is needed.
 */
internal class SeriesDeleteStateHolder(
    /** The VM's scope: sheet loads and deletes must die with the VM. */
    private val scope: CoroutineScope,
    private val offlineRepository: OfflineRepository,
) {

    /**
     * Shared delete module for every series-scoped path in this holder — the
     * episode path passes its snapshot per call (see [deleteOfflineEpisodes]).
     */
    private val deleteActions = OfflineDeleteActions(
        scope = scope,
        offlineRepository = offlineRepository,
    )

    private val _state = MutableStateFlow<HomeSeriesDeleteState?>(null)

    /** Non-null while the delete-episodes sheet is open; null once dismissed. */
    val state: StateFlow<HomeSeriesDeleteState?> = _state.asStateFlow()

    /** The in-flight sheet load, if any — see [requestSeriesDelete]. */
    private var loadJob: Job? = null

    /**
     * Opens the sheet for [series]: raises the loading flag, loads the
     * series' seasons and downloaded episodes (the same [OfflineRepository]
     * calls the detail provider uses) and publishes the rendered state.
     * `getEpisodesForSeries` reads the offline store, so the resulting episode
     * map is already pre-filtered to downloaded episodes — exactly what the
     * sheet expects. Cancels any in-flight load for a previous series first:
     * without that, opening series A, dismissing and quickly opening B could
     * let A's slower load publish last and render B's sheet with A's data
     * (and a load racing [dismiss] would reopen a dismissed sheet).
     */
    fun requestSeriesDelete(series: MediaItem) {
        loadJob?.cancel()
        _state.value = HomeSeriesDeleteState(series.id, emptyList(), emptyMap(), 0L, isLoading = true)
        loadJob = scope.launch {
            try {
                val seasonsOff = offlineRepository.getSeasonsForSeries(series.id).first()
                val episodesBySeasonOff = offlineRepository.getEpisodesForSeries(series.id).groupBy { it.seasonId }
                val episodesOffBySeason = seasonsOff.associate { season ->
                    season.id to (episodesBySeasonOff[season.id] ?: emptyList())
                }
                val downloadedBySeason = episodesOffBySeason.filterValues { it.isNotEmpty() }
                val seasons = seasonsOff.filter { it.id in downloadedBySeason }.map { it.toMediaItem() }
                val episodesBySeason = downloadedBySeason.mapValues { (_, eps) -> eps.map { it.toMediaItem() } }
                // Per-episode on-disk sizes from the offline store, so the delete
                // sheet's freed-space figure is exact for partial selections too.
                val episodeSizeBytes = downloadedBySeason.values
                    .flatten()
                    .associate { it.id to it.totalSizeBytes }
                val totalSizeBytes = episodesOffBySeason.values.flatten().sumOf { it.totalSizeBytes }
                _state.value = HomeSeriesDeleteState(
                    seriesId = series.id,
                    seasons = seasons,
                    episodesBySeason = episodesBySeason,
                    totalSizeBytes = totalSizeBytes,
                    episodeSizeBytes = episodeSizeBytes,
                    isLoading = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed load closes the sheet: the state has no error field,
                // and leaving isLoading = true would wedge the spinner forever.
                // The user can reopen the sheet to retry.
                _state.value = null
            }
        }
    }

    /** Closes the sheet. A load still in flight is cancelled so it can't republish state after dismissal. */
    fun dismiss() {
        loadJob?.cancel()
        _state.value = null
    }

    /**
     * Deletes the selected downloaded episodes for the open sheet via the
     * shared [OfflineDeleteActions] (whole-season collapse + per-episode
     * fallback + unknown-id defense). The sheet snapshot is passed per call
     * and read synchronously BEFORE [dismiss] clears the state — after
     * dismissal the live state is gone and the collapse would silently
     * degrade to per-episode deletes. Clearing the sheet immediately lets it
     * dismiss while the deletes run in the background.
     */
    fun deleteOfflineEpisodes(episodeIds: Set<String>) {
        if (episodeIds.isEmpty()) return
        val state = _state.value ?: return
        deleteActions.deleteOfflineEpisodes(
            episodeIds = episodeIds,
            episodes = state.episodesBySeason,
            seasons = state.seasons,
        )
        dismiss()
    }

    /** Deletes the entire downloaded series and closes the sheet. */
    fun deleteOfflineSeries(seriesId: String) {
        dismiss()
        deleteActions.deleteOfflineSeries(seriesId)
    }
}
