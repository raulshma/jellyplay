package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_manage_series_load_error
import com.raulshma.jellyplay.feature.details.generated.resources.detail_manage_series_no_tvdb
import com.raulshma.jellyplay.feature.details.generated.resources.detail_manage_series_not_tracked

/**
 * ViewModel for the "Manage Series" screen — a Sonarr-style episode management
 * surface reached from the series detail overflow menu (gated behind
 * `DIRECT_ARR_INTEGRATION`).
 *
 * Loads the series' tvdb id from Jellyfin, resolves the owning Sonarr server,
 * then fetches every episode grouped by season. All mutations (monitor toggle,
 * delete file, search, refresh/scan) resolve the owning server internally via
 * [ArrRepository]; the screen only ever deals with tvdb ids.
 *
 * State is a single [MutableStateFlow]<[ManageSeriesUiState]> for atomic
 * snapshots (mirrors the DetailUiState single-state model). One-shot feedback
 * flows through [ManageSeriesUiState.userMessage]; the screen shows it then
 * sends [ManageSeriesUiEvent.ClearUserMessage]. Every user intent arrives as
 * a [ManageSeriesUiEvent] through the single [onEvent] funnel (the home
 * feature's `HomeViewModel` precedent) — the per-action handlers are private,
 * so there is no per-screen command method to keep in sync.
 *
 * The screen's aggregate state — the season-map folds, the delete confirm
 * machine, the stats — lives on [ManageSeriesUiState] (ManageSeriesUiState.kt);
 * this class is the thin async caller that owns the uiState writes.
 */
class ManageSeriesViewModel internal constructor(
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
    private val arrRepository: ArrRepository,
) : JellyPlayViewModel() {

    private val _uiState = MutableStateFlow(ManageSeriesUiState())
    val uiState: StateFlow<ManageSeriesUiState> = _uiState.asStateFlow()

    private var tvdbId: Int? = null
    private var loadedSeriesId: String? = null
    private var loadJob: kotlinx.coroutines.Job? = null

    /** The single command funnel — routes each intent to its private handler. */
    fun onEvent(event: ManageSeriesUiEvent) {
        when (event) {
            is ManageSeriesUiEvent.Load -> load(event.seriesId)
            is ManageSeriesUiEvent.Refresh -> refresh()
            is ManageSeriesUiEvent.ToggleEpisodeMonitored -> toggleEpisodeMonitored(event.episode)
            is ManageSeriesUiEvent.SearchEpisode -> searchEpisode(event.episode)
            is ManageSeriesUiEvent.RequestDeleteEpisode -> requestDeleteEpisode(event.episode)
            is ManageSeriesUiEvent.CancelDeleteEpisode -> cancelDeleteEpisode()
            is ManageSeriesUiEvent.ConfirmDeleteEpisode -> confirmDeleteEpisode()
            is ManageSeriesUiEvent.SearchSeason -> searchSeason(event.seasonNumber)
            is ManageSeriesUiEvent.ToggleSeasonMonitor -> toggleSeasonMonitor(event.seasonNumber)
            is ManageSeriesUiEvent.RefreshSeries -> refreshSeries()
            is ManageSeriesUiEvent.RefreshAndScan -> refreshAndScan()
            is ManageSeriesUiEvent.SearchSeries -> searchSeries()
            is ManageSeriesUiEvent.ToggleSeasonExpanded -> toggleSeasonExpanded(event.seasonNumber)
            is ManageSeriesUiEvent.ClearUserMessage -> clearUserMessage()
            is ManageSeriesUiEvent.ClearError -> clearError()
        }
    }

    /** Loads the series detail (for the tvdb id) then the Sonarr episodes. */
    private fun load(seriesId: String) {
        // Dedupe a reload for the same series while one is already in flight.
        if (loadedSeriesId == seriesId && loadJob?.isActive == true) return
        loadedSeriesId = seriesId
        _uiState.update { ManageSeriesUiState(isLoading = true) }
        loadJob = launch {
            // 1. Resolve the series tvdb id from Jellyfin.
            val detailResult = mediaRepository.getMediaDetail(seriesId)
            val detail = detailResult.getOrNull()
            if (detailResult.isFailure || detail == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = strings.get(Res.string.detail_manage_series_load_error))
                }
                return@launch
            }
            val resolvedTvdb = detail.providerIds["tvdb"]?.toIntOrNull()
            if (resolvedTvdb == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = strings.get(Res.string.detail_manage_series_no_tvdb))
                }
                return@launch
            }
            tvdbId = resolvedTvdb

            // 2. Resolve the owning Sonarr server.
            val resolutionResult = arrRepository.resolveSonarrSeries(resolvedTvdb)
            val resolution = resolutionResult.getOrNull()
            if (resolutionResult.isFailure || resolution == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = strings.get(Res.string.detail_manage_series_not_tracked),
                    )
                }
                return@launch
            }

            // 3. Fetch episodes.
            _uiState.update { it.copy(series = resolution) }
            loadEpisodesInternal()
        }
    }

    /** Re-fetches episodes from Sonarr and recomputes the season grouping. */
    private fun refresh() {
        val tvdb = tvdbId ?: return
        launch {
            val result = arrRepository.getSonarrEpisodes(tvdb)
            result.onSuccess { episodes ->
                _uiState.update {
                    it.copy(
                        episodesBySeason = groupBySeason(episodes),
                        error = null,
                        isLoading = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = UserErrorMessages.resolve(e, "Couldn't load episodes from Sonarr.")) }
            }
        }
    }

    private fun loadEpisodesInternal() {
        val tvdb = tvdbId ?: return
        launch {
            val result = arrRepository.getSonarrEpisodes(tvdb)
            result.onSuccess { episodes ->
                _uiState.update {
                    val bySeason = groupBySeason(episodes)
                    it.copy(
                        episodesBySeason = bySeason,
                        // Auto-expand the season with the fewest missing downloads, falling back
                        // to the first non-specials season, so the most actionable content is visible.
                        expandedSeasons = it.expandedSeasons.ifEmpty { setOf(defaultExpandedSeason(bySeason)) },
                        isLoading = false,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UserErrorMessages.resolve(e, "Couldn't load episodes from Sonarr."),
                    )
                }
            }
        }
    }

    private fun toggleEpisodeMonitored(episode: ArrSeriesEpisode) {
        val tvdb = tvdbId ?: return
        val newMonitored = !episode.monitored
        // Optimistic update.
        _uiState.update { it.updateEpisode(episode.copy(monitored = newMonitored)) }
        launch {
            arrRepository.monitorSonarrEpisodes(tvdb, listOf(episode.id), newMonitored)
                .onSuccess { refresh() }
                .onFailure { e ->
                    // Revert on failure.
                    _uiState.update { it.updateEpisode(episode) }
                    _uiState.update { it.copy(userMessage = UserErrorMessages.resolve(e, "Couldn't update monitoring.")) }
                }
        }
    }

    private fun searchEpisode(episode: ArrSeriesEpisode) {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Episode(episode.id)) }
        launch {
            arrRepository.searchSonarrEpisodes(tvdb, listOf(episode.id))
                .onSuccess {
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = "Searching for ${episode.title}…")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Search failed."))
                    }
                }
        }
    }

    /** Stages the episode in the [PendingConfirmation] hold write. */
    private fun requestDeleteEpisode(episode: ArrSeriesEpisode) {
        _uiState.update { it.copy(pendingDelete = it.pendingDelete.hold(episode)) }
    }

    /** The dismiss write — refused while a delete is in flight ([ManageSeriesUiState.isDeleting]). */
    private fun cancelDeleteEpisode() {
        _uiState.update { it.copy(pendingDelete = it.pendingDelete.dismiss(it.isDeleting)) }
    }

    private fun confirmDeleteEpisode() {
        val tvdb = tvdbId ?: return
        val state = _uiState.value
        val pending = state.pendingDelete.confirm(inFlight = state.isDeleting) ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Episode(pending.id), isDeleting = true) }
        launch {
            arrRepository.deleteSonarrEpisodeFile(tvdb, pending.episodeFileId)
                .onSuccess {
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = "Deleted ${pending.title}.")
                    }
                    refresh()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Couldn't delete the file."))
                    }
                }
            // Settle arm: the flag drops on BOTH outcomes and [PendingConfirmation.clear]
            // runs on BOTH outcomes — pre-fold this was a clear-before-action write, so
            // failure left the pending episode cleared too.
            _uiState.update { it.copy(isDeleting = false, pendingDelete = it.pendingDelete.clear()) }
        }
    }

    private fun searchSeason(seasonNumber: Int) {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Season(seasonNumber)) }
        launch {
            arrRepository.searchMonitoredSonarrSeason(tvdb, seasonNumber)
                .onSuccess {
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = "Searching monitored episodes in season $seasonNumber…")
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Search failed."))
                    }
                }
        }
    }

    private fun toggleSeasonMonitor(seasonNumber: Int) {
        val tvdb = tvdbId ?: return
        val seasonEps = _uiState.value.episodesBySeason[seasonNumber].orEmpty()
        if (seasonEps.isEmpty()) return
        // Monitor all if any unmonitored; unmonitor all if all monitored.
        val targetMonitored = seasonEps.any { !it.monitored }
        _uiState.update {
            it.updateSeason(seasonNumber) { ep -> ep.copy(monitored = targetMonitored) }
        }
        launch {
            arrRepository.monitorSonarrEpisodes(tvdb, seasonEps.map { it.id }, targetMonitored)
                .onSuccess { refresh() }
                .onFailure { e ->
                    // Revert.
                    _uiState.update {
                        it.updateSeason(seasonNumber) { ep -> ep.copy(monitored = !targetMonitored) }
                    }
                    _uiState.update { it.copy(userMessage = UserErrorMessages.resolve(e, "Couldn't update monitoring.")) }
                }
        }
    }

    private fun refreshSeries() {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Series(SeriesAction.REFRESH)) }
        launch {
            arrRepository.refreshSonarrSeries(tvdb)
                .onSuccess {
                    _uiState.update { it.copy(actionTarget = null, userMessage = "Refreshing series metadata…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Refresh failed.")) }
                }
        }
    }

    private fun refreshAndScan() {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Series(SeriesAction.REFRESH_AND_SCAN)) }
        launch {
            // Sonarr: refresh + rescan are separate commands; fire both.
            arrRepository.refreshSonarrSeries(tvdb)
            arrRepository.rescanSonarrSeries(tvdb)
                .onSuccess {
                    _uiState.update { it.copy(actionTarget = null, userMessage = "Refreshing & scanning series…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Scan failed.")) }
                }
        }
    }

    private fun searchSeries() {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Series(SeriesAction.SEARCH)) }
        launch {
            arrRepository.searchSonarrSeries(tvdb)
                .onSuccess {
                    _uiState.update { it.copy(actionTarget = null, userMessage = "Searching all monitored missing episodes…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(actionTarget = null, userMessage = UserErrorMessages.resolve(e, "Search failed.")) }
                }
        }
    }

    private fun toggleSeasonExpanded(seasonNumber: Int) {
        _uiState.update { state ->
            val expanded = if (seasonNumber in state.expandedSeasons) {
                state.expandedSeasons - seasonNumber
            } else {
                state.expandedSeasons + seasonNumber
            }
            state.copy(expandedSeasons = expanded)
        }
    }

    private fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun groupBySeason(episodes: List<ArrSeriesEpisode>): Map<Int, List<ArrSeriesEpisode>> {
        //  purification: `toSortedMap` is JVM-stdlib. A sorted-entries
        // LinkedHashMap iterates in exactly the seasonComparator() order the
        // SortedMap had, so consumers see an identical sequence.
        return episodes.groupBy { it.seasonNumber }
            .mapValues { (_, eps) -> eps.sortedWith(compareBy({ it.episodeNumber }, { it.absoluteEpisodeNumber })) }
            .entries.sortedWith(compareBy(seasonComparator()) { it.key })
            .associate { it.key to it.value }
    }

    /**
     * Seasons sorted ascending, but with specials (season 0) pushed to the end —
     * mirroring the Sonarr web UI.
     */
    private fun seasonComparator(): Comparator<Int> = Comparator { a, b ->
        when {
            a == 0 && b != 0 -> 1
            b == 0 && a != 0 -> -1
            else -> a.compareTo(b)
        }
    }

    /**
     * Picks the season to auto-expand on first load: the lowest-numbered
     * non-specials season with at least one missing monitored episode, else the
     * lowest non-specials season, else the first season available.
     */
    private fun defaultExpandedSeason(bySeason: Map<Int, List<ArrSeriesEpisode>>): Int {
        val nonSpecials = bySeason.filterKeys { it > 0 }
        val withMissing = nonSpecials.entries.firstOrNull { (_, eps) ->
            eps.any { it.monitored && !it.hasFile }
        }
        return withMissing?.key
            ?: nonSpecials.keys.minOrNull()
            ?: bySeason.keys.minOrNull()
            ?: 1
    }
}
