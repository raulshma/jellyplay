package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState


/**
 * Mutable multi-season, multi-episode selection state shared by the
 * download and delete sheets.
 *
 * **Why this is deep.** `SeriesDownloadSelection` (online, downloads) and
 * `SeriesDeleteSelection` (offline, deletes) used to be near-duplicate
 * `@Stable` wrappers — each ~100 LOC of forwarding to this base class, with
 * the only real divergence being *which episodes are selectable*:
 *
 *  - downloads: every not-yet-downloaded episode (computed from a live
 *    `downloadedEpisodeIds` set, which arrives asynchronously);
 *  - deletes: every downloaded episode (the full season list, pre-loaded).
 *
 * That selectability decision is a caller concern — it depends on data the
 * selection state has no business owning. So this class is parameterised
 * by a [selectableProvider] lambda the caller supplies. Everything else —
 * the selected-id sets, the toggle operations, the tri-state rendering,
 * the counts — lives here once. Each sheet now constructs the base with
 * its rule and adds only the sheet-specific derived values it needs
 * (`totalSelectedBytes`, `toDownloadMap`) as local extensions.
 *
 * The provider is read on every derived-state access (inside a Compose
 * snapshot), so it picks up caller-side state changes without an explicit
 * push, and the selection never resets when it changes — only `toggle*`
 * calls mutate [selectedEpisodeIds]. An async fetch cannot wipe selections
 * the user already made.
 *
 * Not a `@Composable` itself; callers wrap construction in `remember`.
 */
@Stable
class MultiEpisodeSelection(
    initialSeasonIds: Collection<String>,
    selectableProvider: () -> Map<String, Set<String>> = { emptyMap() },
) {
    /**
     * Supplies per-season selectable episode ids from caller-owned state
     * (an episodes map, a downloaded-ids set, …). Read on every derived-state
     * access so the selection tracks caller data changes without a manual
     * push and without resetting [selectedEpisodeIds]. Reassignable via
     * [updateSelectableProvider] so the download sheet can refresh the
     * closure around live Compose state each recomposition.
     */
    private var selectableProvider: () -> Map<String, Set<String>> = selectableProvider

    fun updateSelectableProvider(provider: () -> Map<String, Set<String>>) {
        selectableProvider = provider
    }
    var selectedEpisodeIds: Map<String, Set<String>> by mutableStateOf(
        initialSeasonIds.associateWith { emptySet() },
    )
        private set

    /** Per-season selectable ids, re-derived from [selectableProvider] on each read. */
    val selectableIdsBySeason: Map<String, Set<String>>
        get() = selectableProvider()

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
     * longer supplies an extra `downloadedInSeason` hint, because the
     * selectability that hint encoded now lives in [selectableProvider].
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

    /** Snapshot of the selection as season → list of episode ids. */
    fun toSelectedMap(): Map<String, List<String>> =
        selectedEpisodeIds.mapValues { (_, ids) -> ids.toList() }

    /** Flat snapshot of every selected episode id across all seasons. */
    fun toSelectedIds(): Set<String> = selectedEpisodeIds.values.flatten().toSet()
}

/**
 * Download-sheet construction: selectability = every not-yet-downloaded
 * episode in [episodes]. [episodes] and [downloadedEpisodeIds] arrive
 * asynchronously after the sheet opens; keying `remember` on `seasons`
 * alone (not these) prevents a late fetch from wiping selections. The two
 * inputs are mirrored into Compose state each recomposition so the
 * selection's derived state tracks them without resetting
 * [MultiEpisodeSelection.selectedEpisodeIds].
 */
@Composable
fun rememberMultiEpisodeSelectionForDownload(
    seasons: List<com.raulshma.jellyplay.core.model.MediaItem>,
    episodes: Map<String, List<com.raulshma.jellyplay.core.model.MediaItem>>,
    downloadedEpisodeIds: Set<String>,
): MultiEpisodeSelection {
    val selection = remember(seasons) {
        MultiEpisodeSelection(
            initialSeasonIds = seasons.map { it.id },
            selectableProvider = { emptyMap() },
        )
    }
    // Push live inputs into the provider's closure each recomposition. The
    // provider reads these refs, so derived state refreshes; selectedEpisodeIds
    // is untouched (only toggle* mutates it).
    val episodesRef = remember { mutableStateOf(episodes) }
    val downloadedRef = remember { mutableStateOf(downloadedEpisodeIds) }
    episodesRef.value = episodes
    downloadedRef.value = downloadedEpisodeIds
    return remember(selection, episodesRef, downloadedRef) {
        selection.also {
            it.updateSelectableProvider {
                seasons.associate { season ->
                    season.id to (episodesRef.value[season.id].orEmpty()
                        .map { episode -> episode.id }
                        .filter { id -> id !in downloadedRef.value }
                        .toSet())
                }
            }
        }
    }
}

/**
 * Delete-sheet construction: selectability = every episode in [episodes]
 * (every episode is already downloaded offline, so there is no exclusion).
 * The episode map arrives pre-loaded once, so keying on it is safe.
 *
 * Operates on the unified [MediaItem] — the unified detail screen projects
 * offline episodes through `OfflineMediaItem.toMediaItem()`, so the delete
 * sheet reads the same episode shape as the seasons section.
 */
@Composable
fun rememberMultiEpisodeSelectionForDelete(
    episodes: Map<String, List<com.raulshma.jellyplay.core.model.MediaItem>>,
): MultiEpisodeSelection = remember(episodes) {
    MultiEpisodeSelection(
        initialSeasonIds = episodes.keys,
        selectableProvider = {
            episodes.mapValues { (_, list) -> list.map { it.id }.toSet() }
        },
    )
}
