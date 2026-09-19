package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.PendingConfirmation
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrSeriesResolution

/**
 * The pure Arr state holder for the "Manage Series" screen, extracted from
 * ManageSeriesViewModel.kt so the ViewModel file stays a thin caller (the
 * ownership ratchet counts declarations per file). All Arr-aggregate behavior
 * lives HERE as folds over immutable snapshots — the season map rewrites
 * ([updateEpisode]/[updateSeason]), the per-season stats fold ([seasonStats]),
 * the storage sum, and the episode-delete confirm machine ([PendingConfirmation])
 * — while the ViewModel only wires async I/O around these writes and owns the
 * uiState emits.
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
    /**
     * Episode-delete confirm machine ([PendingConfirmation]). The guard RULE
     * lives in the machine, fed the [ManageSeriesUiState.isDeleting] flag;
     * the settle arm is an explicit [PendingConfirmation.clear] on BOTH
     * delete outcomes.
     */
    val pendingDelete: PendingConfirmation<ArrSeriesEpisode> = PendingConfirmation(),
    /** True while the staged episode-file delete is in flight — the machine's guard fact. */
    val isDeleting: Boolean = false,
    /** Which target (episode/season/series) has an in-flight action, for spinners. */
    val actionTarget: ActionTarget? = null,
) {
    /** The staged delete target — the pre-fold `pendingDeleteEpisode` field, now derived from [pendingDelete]. */
    val pendingDeleteEpisode: ArrSeriesEpisode? get() = pendingDelete.item

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
