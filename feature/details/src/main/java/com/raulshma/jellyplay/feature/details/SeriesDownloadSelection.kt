package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.MultiEpisodeSelection

/**
 * Mutable selection state for the series-download sheet, hoisted out of the
 * `SeriesDownloadSheet` composable.
 *
 * Thin wrapper over [MultiEpisodeSelection] that owns the download-specific
 * selectability rule (every not-yet-downloaded episode) and pushes it into
 * the base class's [MultiEpisodeSelection.selectableIdsBySeason] map. The
 * base class owns the selected-id sets + toggle/tri-state/derived state.
 */
@Stable
internal class SeriesDownloadSelection(
    private val seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>> = emptyMap(),
    downloadedEpisodeIds: Set<String> = emptySet(),
) {
    // Live episode / downloaded data. These change as the sheet fetches
    // seasons and resolves downloaded ids (both happen asynchronously right
    // after the sheet opens), but they must NOT reset the selection. They are
    // pushed in via [rememberSeriesDownloadSelection] each recomposition and
    // kept out of the `remember` keys so a fetch never wipes selections the
    // user already made — that re-creation was the root cause of Select-all
    // racing and selecting only a subset of seasons.
    var episodes: Map<String, List<MediaItem>> by mutableStateOf(episodes)
        internal set
    var downloadedEpisodeIds: Set<String> by mutableStateOf(downloadedEpisodeIds)
        internal set

    private val selection = MultiEpisodeSelection(seasons.map { it.id })

    // Recompute selectability from the live inputs every read; push it into
    // the base class so its derived state (triState, allSelected, counts)
    // reflects the current not-yet-downloaded set. Done as a read-time view
    // rather than a write on every mutation so the base class never needs to
    // know about episodes/downloaded ids.
    private fun syncSelectability() {
        selection.selectableIdsBySeason = seasons.associate { season ->
            season.id to (episodes[season.id].orEmpty()
                .map { it.id }
                .filter { it !in downloadedEpisodeIds }
                .toSet())
        }
    }

    val selectedEpisodeIds: Map<String, Set<String>>
        get() { syncSelectability(); return selection.selectedEpisodeIds }

    val allSelectableIds: Set<String>
        get() { syncSelectability(); return selection.allSelectableIds }

    val totalSelectedCount: Int
        get() = selection.totalSelectedCount

    val allSelected: Boolean
        get() { syncSelectability(); return selection.allSelected }

    fun selectableEpisodeIdsForSeason(seasonId: String): Set<String> {
        syncSelectability()
        return selection.selectableEpisodeIdsForSeason(seasonId)
    }

    fun selectedForSeason(seasonId: String): Set<String> = selection.selectedForSeason(seasonId)

    fun triStateForSeason(seasonId: String): androidx.compose.ui.state.ToggleableState {
        syncSelectability()
        return selection.triStateForSeason(seasonId)
    }

    fun toggleSelectAll() { syncSelectability(); selection.toggleSelectAll() }
    fun toggleSeason(seasonId: String) { syncSelectability(); selection.toggleSeason(seasonId) }
    fun toggleEpisode(seasonId: String, episodeId: String) { selection.toggleEpisode(seasonId, episodeId) }

    fun selectAll() { syncSelectability(); selection.selectAll() }
    fun deselectAll() { selection.deselectAll() }

    /** Snapshot of the selection as season → list of episode ids, for the download callback. */
    fun toDownloadMap(): Map<String, List<String>> =
        selection.selectedEpisodeIds.mapValues { (_, ids) -> ids.toList() }
}

@Composable
internal fun rememberSeriesDownloadSelection(
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    downloadedEpisodeIds: Set<String>,
): SeriesDownloadSelection {
    // Key only on `seasons`: it's loaded once before the sheet opens and is
    // the only input whose change legitimately warrants a fresh selection.
    // `episodes` and `downloadedEpisodeIds` arrive asynchronously after open
    // (and again on every season fetch); keying on them recreated the whole
    // selection — discarding `selectedEpisodeIds` — which caused Select-all to
    // sometimes select only the last-loaded season.
    val selection = remember(seasons) { SeriesDownloadSelection(seasons) }
    selection.episodes = episodes
    selection.downloadedEpisodeIds = downloadedEpisodeIds
    return selection
}
