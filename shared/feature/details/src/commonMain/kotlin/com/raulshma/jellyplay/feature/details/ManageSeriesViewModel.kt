package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution
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
 * calls [clearUserMessage].
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

    /** Loads the series detail (for the tvdb id) then the Sonarr episodes. */
    fun load(seriesId: String) {
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
    fun refresh() {
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
                _uiState.update { it.copy(error = e.message ?: "Couldn't load episodes from Sonarr.") }
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
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load episodes from Sonarr.")
                }
            }
        }
    }

    fun toggleEpisodeMonitored(episode: ArrSeriesEpisode) {
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
                    _uiState.update { it.copy(userMessage = e.message ?: "Couldn't update monitoring.") }
                }
        }
    }

    fun searchEpisode(episode: ArrSeriesEpisode) {
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
                        it.copy(actionTarget = null, userMessage = e.message ?: "Search failed.")
                    }
                }
        }
    }

    fun requestDeleteEpisode(episode: ArrSeriesEpisode) {
        _uiState.update { it.copy(pendingDeleteEpisode = episode) }
    }

    fun cancelDeleteEpisode() {
        _uiState.update { it.copy(pendingDeleteEpisode = null) }
    }

    fun confirmDeleteEpisode() {
        val tvdb = tvdbId ?: return
        val pending = _uiState.value.pendingDeleteEpisode ?: return
        _uiState.update { it.copy(pendingDeleteEpisode = null, actionTarget = ActionTarget.Episode(pending.id)) }
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
                        it.copy(actionTarget = null, userMessage = e.message ?: "Couldn't delete the file.")
                    }
                }
        }
    }

    fun searchSeason(seasonNumber: Int) {
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
                        it.copy(actionTarget = null, userMessage = e.message ?: "Search failed.")
                    }
                }
        }
    }

    fun toggleSeasonMonitor(seasonNumber: Int) {
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
                    _uiState.update { it.copy(userMessage = e.message ?: "Couldn't update monitoring.") }
                }
        }
    }

    fun refreshSeries() {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Series(SeriesAction.REFRESH)) }
        launch {
            arrRepository.refreshSonarrSeries(tvdb)
                .onSuccess {
                    _uiState.update { it.copy(actionTarget = null, userMessage = "Refreshing series metadata…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(actionTarget = null, userMessage = e.message ?: "Refresh failed.") }
                }
        }
    }

    fun refreshAndScan() {
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
                    _uiState.update { it.copy(actionTarget = null, userMessage = e.message ?: "Scan failed.") }
                }
        }
    }

    fun searchSeries() {
        val tvdb = tvdbId ?: return
        _uiState.update { it.copy(actionTarget = ActionTarget.Series(SeriesAction.SEARCH)) }
        launch {
            arrRepository.searchSonarrSeries(tvdb)
                .onSuccess {
                    _uiState.update { it.copy(actionTarget = null, userMessage = "Searching all monitored missing episodes…") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(actionTarget = null, userMessage = e.message ?: "Search failed.") }
                }
        }
    }

    fun toggleSeasonExpanded(seasonNumber: Int) {
        _uiState.update { state ->
            val expanded = if (seasonNumber in state.expandedSeasons) {
                state.expandedSeasons - seasonNumber
            } else {
                state.expandedSeasons + seasonNumber
            }
            state.copy(expandedSeasons = expanded)
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun groupBySeason(episodes: List<ArrSeriesEpisode>): Map<Int, List<ArrSeriesEpisode>> {
        // Wave 16C purification: `toSortedMap` is JVM-stdlib. A sorted-entries
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

/**
 * Immutable UI state for the "Manage Series" screen.
 */
@Immutable
data class ManageSeriesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val series: ArrSeriesResolution? = null,
    /** Season number → episodes (sorted; specials at end). */
    val episodesBySeason: Map<Int, List<ArrSeriesEpisode>> = emptyMap(),
    val expandedSeasons: Set<Int> = emptySet(),
    /** One-shot snackbar message for action feedback. */
    val userMessage: String? = null,
    /** Episode awaiting delete confirmation. */
    val pendingDeleteEpisode: ArrSeriesEpisode? = null,
    /** Which target (episode/season/series) has an in-flight action, for spinners. */
    val actionTarget: ActionTarget? = null,
) {
    /** Updates a single episode in-place across the season map. */
    fun updateEpisode(updated: ArrSeriesEpisode): ManageSeriesUiState {
        val newMap = episodesBySeason.mapValues { (season, eps) ->
            eps.map { if (it.id == updated.id && season == updated.seasonNumber) updated else it }
        }
        return copy(episodesBySeason = newMap)
    }

    /** Updates all episodes in a season via [transform]. */
    fun updateSeason(seasonNumber: Int, transform: (ArrSeriesEpisode) -> ArrSeriesEpisode): ManageSeriesUiState {
        val newMap = episodesBySeason.mapValues { (season, eps) ->
            if (season == seasonNumber) eps.map(transform) else eps
        }
        return copy(episodesBySeason = newMap)
    }

    /** Per-season downloaded/total counts for the season header. */
    fun seasonStats(seasonNumber: Int): SeasonStats {
        val eps = episodesBySeason[seasonNumber].orEmpty()
        val downloaded = eps.count { it.hasFile }
        return SeasonStats(total = eps.size, downloaded = downloaded, monitored = eps.count { it.monitored })
    }

    /** Total on-disk storage used by downloaded episodes across all seasons. */
    val totalStorageBytes: Long
        get() = episodesBySeason.values.flatten().sumOf { it.fileSizeBytes ?: 0L }

    @Immutable
    data class SeasonStats(val total: Int, val downloaded: Int, val monitored: Int)
}

/** Identifies which entity has an in-flight action, for showing a spinner. */
@Immutable
sealed class ActionTarget {
    @Immutable data class Episode(val episodeId: Int) : ActionTarget()
    @Immutable data class Season(val seasonNumber: Int) : ActionTarget()
    /** A series-level command (refresh / refresh & scan / search). [action] keys it to one button. */
    @Immutable data class Series(val action: SeriesAction) : ActionTarget()
}

/** Which series-level button is in flight, so only that one shows a spinner. */
@Immutable
enum class SeriesAction { REFRESH, REFRESH_AND_SCAN, SEARCH }
