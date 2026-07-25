package com.raulshma.jellyplay.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.components.MultiEpisodeSelection

/**
 * Mutable selection state for the offline series **delete** sheet, hoisted out
 * of the [DeleteSeriesSheet] composable.
 *
 * Thin wrapper over [MultiEpisodeSelection]. Every episode in the map is
 * already downloaded, so the selectability rule is simply the full episode
 * list per season — there is no `downloadedEpisodeIds` exclusion and no
 * lazy-load machinery (the [OfflineSeriesViewModel] pre-loads every season's
 * episodes). The byte-total ([totalSelectedBytes]) is the one delete-specific
 * derived value the base class does not own.
 */
@Stable
internal class SeriesDeleteSelection(
    episodes: Map<String, List<OfflineMediaItem>>,
) {
    /**
     * Caller-owned episode map; refreshes selectability when it changes.
     * Write-private: [rememberSeriesDeleteSelection] keys `remember` on this
     * value (episodes arrive pre-loaded once), so the field is never assigned
     * after construction — kept as `mutableStateOf` only so Compose observes
     * reads inside [syncSelectability].
     */
    var episodes: Map<String, List<OfflineMediaItem>> by mutableStateOf(episodes)
        private set

    private val selection = MultiEpisodeSelection(episodes.keys)

    private val episodeById: Map<String, OfflineMediaItem>
        get() = episodes.values.flatten().associateBy { it.id }

    private fun syncSelectability() {
        selection.selectableIdsBySeason = episodes.mapValues { (_, list) -> list.map { it.id }.toSet() }
    }

    val selectedEpisodeIds: Map<String, Set<String>>
        get() { syncSelectability(); return selection.selectedEpisodeIds }

    val allSelectableIds: Set<String>
        get() { syncSelectability(); return selection.allSelectableIds }

    val totalSelectedCount: Int
        get() = selection.totalSelectedCount

    /** Total bytes that will be freed by the current selection. */
    val totalSelectedBytes: Long
        get() {
            val byId = episodeById
            return selection.selectedEpisodeIds.values.flatten().sumOf { id -> byId[id]?.totalSizeBytes ?: 0L }
        }

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

    /** Flat snapshot of every selected episode id across all seasons. */
    fun toDeleteIds(): Set<String> = selection.selectedEpisodeIds.values.flatten().toSet()
}

@Composable
internal fun rememberSeriesDeleteSelection(
    episodes: Map<String, List<OfflineMediaItem>>,
): SeriesDeleteSelection = remember(episodes) {
    SeriesDeleteSelection(episodes)
}
