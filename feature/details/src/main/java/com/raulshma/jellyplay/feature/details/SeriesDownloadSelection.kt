package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.MediaItem

/**
 * Mutable selection state for the series-download sheet, hoisted out of the
 * `SeriesDownloadSheet` composable.
 *
 * Owns the per-season selected-episode-id sets plus the toggle/selection
 * operations (select-all, deselect-all, toggle-season, toggle-episode). The
 * sheet reads [selectedEpisodeIds], [totalSelectedCount], [allSelected], and
 * [selectableEpisodeIdsForSeason] / [triStateForSeason] and delegates mutations
 * here so its body shrinks to pure rendering.
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

    var selectedEpisodeIds: Map<String, Set<String>> by mutableStateOf(
        seasons.associate { it.id to emptySet() },
    )
        private set

    /** All not-yet-downloaded episode ids across every loaded season. */
    val allSelectableIds: Set<String>
        get() = episodes.values
            .flatten()
            .map { it.id }
            .filter { it !in downloadedEpisodeIds }
            .toSet()

    val totalSelectedCount: Int
        get() = selectedEpisodeIds.values.sumOf { it.size }

    val allSelected: Boolean
        get() {
            val selectableIds = allSelectableIds
            val selectedFlat = selectedEpisodeIds.values.flatten().toHashSet()
            return selectableIds.isNotEmpty() && selectableIds.all { it in selectedFlat }
        }

    fun selectableEpisodeIdsForSeason(seasonId: String): Set<String> {
        val seasonEpisodes = episodes[seasonId].orEmpty()
        return seasonEpisodes.map { it.id }.filter { it !in downloadedEpisodeIds }.toSet()
    }

    fun selectedForSeason(seasonId: String): Set<String> = selectedEpisodeIds[seasonId].orEmpty()

    fun triStateForSeason(seasonId: String, downloadedInSeason: Int): androidx.compose.ui.state.ToggleableState {
        val selectable = selectableEpisodeIdsForSeason(seasonId)
        val selected = selectedForSeason(seasonId)
        return when {
            selectable.isEmpty() && downloadedInSeason > 0 -> androidx.compose.ui.state.ToggleableState.On
            selectable.isEmpty() -> androidx.compose.ui.state.ToggleableState.Off
            selectable.all { it in selected } -> androidx.compose.ui.state.ToggleableState.On
            selectable.none { it in selected } -> androidx.compose.ui.state.ToggleableState.Off
            else -> androidx.compose.ui.state.ToggleableState.Indeterminate
        }
    }

    fun deselectAll() {
        selectedEpisodeIds = seasons.associate { it.id to emptySet() }
    }

    fun selectAll() {
        selectedEpisodeIds = seasons.associate { season ->
            season.id to selectableEpisodeIdsForSeason(season.id)
        }
    }

    fun toggleSelectAll() {
        if (allSelected) {
            deselectAll()
        } else {
            selectAll()
        }
    }

    fun toggleSeason(seasonId: String) {
        val selectable = selectableEpisodeIdsForSeason(seasonId)
        val current = selectedForSeason(seasonId)
        val newSet = if (selectable.all { it in current }) {
            current - selectable
        } else {
            current + selectable
        }
        selectedEpisodeIds = selectedEpisodeIds + (seasonId to newSet)
    }

    fun toggleEpisode(seasonId: String, episodeId: String) {
        val current = selectedForSeason(seasonId)
        val newSet = if (episodeId in current) current - episodeId else current + episodeId
        selectedEpisodeIds = selectedEpisodeIds + (seasonId to newSet)
    }

    /** Snapshot of the selection as season → list of episode ids, for the download callback. */
    fun toDownloadMap(): Map<String, List<String>> =
        selectedEpisodeIds.mapValues { (_, ids) -> ids.toList() }
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
