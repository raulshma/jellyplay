package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.OfflineMediaItem

/**
 * Mutable selection state for the offline series **delete** sheet, hoisted out
 * of the [DeleteSeriesSheet] composable. The delete counterpart of the online
 * [com.raulshma.jellyplay.feature.details.SeriesDownloadSelection].
 *
 * Unlike the download selection, every episode in the map is already
 * downloaded, so the "selectable" set is simply the full episode list for each
 * season — there is no `downloadedEpisodeIds` exclusion and no lazy-load
 * machinery (the [OfflineSeriesViewModel] pre-loads every season's episodes).
 *
 * Owns the per-season selected-episode-id sets plus the toggle/selection
 * operations (select-all, deselect-all, toggle-season, toggle-episode). The
 * sheet reads [selectedEpisodeIds], [totalSelectedCount], [totalSelectedBytes],
 * [allSelected], and [triStateForSeason] and delegates mutations here.
 *
 * @param episodes keyed by season id; used to resolve sizes for the byte totals
 *   and to drive the select-all/toggle-season logic.
 */
@Stable
internal class SeriesDeleteSelection(
    private val episodes: Map<String, List<OfflineMediaItem>>,
) {
    var selectedEpisodeIds: Map<String, Set<String>> by mutableStateOf(
        episodes.keys.associateWith { emptySet() },
    )
        private set

    /** Every downloadable episode id across all seasons (all are deletable). */
    val allSelectableIds: Set<String> by derivedStateOf {
        episodes.values.flatten().map { it.id }.toSet()
    }

    /** Flat id → episode lookup so byte totals can be summed without re-searching. */
    private val episodeById: Map<String, OfflineMediaItem> by derivedStateOf {
        episodes.values.flatten().associateBy { it.id }
    }

    val totalSelectedCount: Int by derivedStateOf { selectedEpisodeIds.values.sumOf { it.size } }

    /** Total bytes that will be freed by the current selection. */
    val totalSelectedBytes: Long by derivedStateOf {
        selectedEpisodeIds.values.flatten().sumOf { id -> episodeById[id]?.totalSizeBytes ?: 0L }
    }

    val allSelected: Boolean by derivedStateOf {
        val selectedFlat = selectedEpisodeIds.values.flatten().toHashSet()
        allSelectableIds.isNotEmpty() && allSelectableIds.all { it in selectedFlat }
    }

    fun selectableEpisodeIdsForSeason(seasonId: String): Set<String> =
        episodes[seasonId].orEmpty().map { it.id }.toSet()

    fun selectedForSeason(seasonId: String): Set<String> = selectedEpisodeIds[seasonId].orEmpty()

    fun triStateForSeason(seasonId: String): androidx.compose.ui.state.ToggleableState {
        val selectable = selectableEpisodeIdsForSeason(seasonId)
        if (selectable.isEmpty()) return androidx.compose.ui.state.ToggleableState.Off
        val selected = selectedForSeason(seasonId)
        return when {
            selectable.all { it in selected } -> androidx.compose.ui.state.ToggleableState.On
            selectable.none { it in selected } -> androidx.compose.ui.state.ToggleableState.Off
            else -> androidx.compose.ui.state.ToggleableState.Indeterminate
        }
    }

    fun toggleSelectAll() {
        selectedEpisodeIds = if (allSelected) {
            episodes.keys.associateWith { emptySet() }
        } else {
            episodes.mapValues { (_, list) -> list.map { it.id }.toSet() }
        }
    }

    fun toggleSeason(seasonId: String) {
        val selectable = selectableEpisodeIdsForSeason(seasonId)
        val current = selectedForSeason(seasonId)
        val newSet = if (selectable.all { it in current }) current - selectable else current + selectable
        selectedEpisodeIds = selectedEpisodeIds + (seasonId to newSet)
    }

    fun toggleEpisode(seasonId: String, episodeId: String) {
        val current = selectedForSeason(seasonId)
        val newSet = if (episodeId in current) current - episodeId else current + episodeId
        selectedEpisodeIds = selectedEpisodeIds + (seasonId to newSet)
    }

    /** Flat snapshot of every selected episode id across all seasons. */
    fun toDeleteIds(): Set<String> = selectedEpisodeIds.values.flatten().toSet()
}

@Composable
internal fun rememberSeriesDeleteSelection(
    episodes: Map<String, List<OfflineMediaItem>>,
): SeriesDeleteSelection = remember(episodes) {
    SeriesDeleteSelection(episodes)
}
