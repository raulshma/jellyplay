package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState

/**
 * Mutable multi-season, multi-episode selection state shared by the
 * download and delete sheets.
 *
 * **Why this exists.** `SeriesDownloadSelection` (online, downloads) and
 * `SeriesDeleteSelection` (offline, deletes) were near-duplicate siblings
 * — same per-season selected-id sets, same toggle/tri-state/derived-state
 * shape, copy-pasted between `feature:details` and `feature:downloads`. The
 * only real divergence was *which episodes are selectable*:
 *
 *  - downloads: every not-yet-downloaded episode (computed from the live
 *    `downloadedEpisodeIds` set, which arrives asynchronously);
 *  - deletes: every downloaded episode (the full season list, pre-loaded).
 *
 * That selectability decision is a caller concern — it depends on data the
 * base class has no business knowing about. So this class is parameterised
 * by a [selectableIdsBySeason] map that the caller pushes in (and refreshes
 * whenever its inputs change). Everything else — the selected-id sets, the
 * toggle operations, the tri-state rendering, the counts — lives here once.
 *
 * Collapsing the two siblings also removes the `triStateForSeason(seasonId,
 * downloadedInSeason)` leak the download sheet used to have: the selectability
 * asymmetry that `downloadedInSeason` carried is now inside the
 * `selectableIdsBySeason` map, so [triStateForSeason] is a pure function of
 * the season id.
 *
 * Not a `@Composable` itself; callers wrap construction in `remember`.
 */
@Stable
class MultiEpisodeSelection(
    initialSeasonIds: Collection<String>,
) {
    /**
     * Per-season selectable episode ids. Pushed in by the caller and
     * refreshed as the caller's data (episodes list, downloaded-ids set)
     * changes. The selection never resets when this map changes — only
     * `toggle*` calls mutate [selectedEpisodeIds] — so an async fetch
     * cannot wipe selections the user already made.
     */
    var selectableIdsBySeason: Map<String, Set<String>> by mutableStateOf(emptyMap())

    var selectedEpisodeIds: Map<String, Set<String>> by mutableStateOf(
        initialSeasonIds.associateWith { emptySet() },
    )
        private set

    /** Every selectable episode id across all seasons. */
    val allSelectableIds: Set<String>
        get() = selectableIdsBySeason.values.flatten().toSet()

    val totalSelectedCount: Int
        get() = selectedEpisodeIds.values.sumOf { it.size }

    val allSelected: Boolean
        get() {
            val selectable = allSelectableIds
            val selectedFlat = selectedEpisodeIds.values.flatten().toHashSet()
            return selectable.isNotEmpty() && selectable.all { it in selectedFlat }
        }

    fun selectableEpisodeIdsForSeason(seasonId: String): Set<String> =
        selectableIdsBySeason[seasonId].orEmpty()

    fun selectedForSeason(seasonId: String): Set<String> = selectedEpisodeIds[seasonId].orEmpty()

    /**
     * Tri-state for a season. Pure function of [seasonId] — the caller no
     * longer needs to supply an extra `downloadedInSeason` hint, because
     * the selectability that hint encoded now lives in [selectableIdsBySeason].
     */
    fun triStateForSeason(seasonId: String): ToggleableState {
        val selectable = selectableEpisodeIdsForSeason(seasonId)
        val selected = selectedForSeason(seasonId)
        return when {
            selectable.isEmpty() -> ToggleableState.Off
            selectable.all { it in selected } -> ToggleableState.On
            selectable.none { it in selected } -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }
    }

    fun deselectAll() {
        selectedEpisodeIds = selectedEpisodeIds.keys.associateWith { emptySet() }
    }

    fun selectAll() {
        selectedEpisodeIds = selectableIdsBySeason.mapValues { (_, ids) -> ids }
    }

    fun toggleSelectAll() {
        if (allSelected) deselectAll() else selectAll()
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
}
