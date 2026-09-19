package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode

/**
 * Every user intent the "Manage Series" screen can express (the home
 * feature's `HomeUiEvent`/`onEvent` precedent).
 * [ManageSeriesViewModel.onEvent] is the single entry point — the VM exposes
 * no per-action command methods, so new intents are added here (and routed
 * once) rather than as new public members on the VM.
 */
sealed interface ManageSeriesUiEvent {
    /** Loads the series detail (tvdb id) then the Sonarr episodes. */
    data class Load(val seriesId: String) : ManageSeriesUiEvent

    /** Re-fetches episodes from Sonarr and recomputes the season grouping. */
    data object Refresh : ManageSeriesUiEvent

    /** Toggles one episode's monitored flag (optimistic, reverted on failure). */
    data class ToggleEpisodeMonitored(val episode: ArrSeriesEpisode) : ManageSeriesUiEvent

    /** Triggers a Sonarr search for one episode. */
    data class SearchEpisode(val episode: ArrSeriesEpisode) : ManageSeriesUiEvent

    /** Stages the episode in the `PendingConfirmation` hold write. */
    data class RequestDeleteEpisode(val episode: ArrSeriesEpisode) : ManageSeriesUiEvent

    /** The dismiss write — refused while a delete is in flight. */
    data object CancelDeleteEpisode : ManageSeriesUiEvent

    /** Runs the staged episode-file delete, then refreshes. */
    data object ConfirmDeleteEpisode : ManageSeriesUiEvent

    /** Triggers a Sonarr search of a season's monitored episodes. */
    data class SearchSeason(val seasonNumber: Int) : ManageSeriesUiEvent

    /** Monitors/unmonitors every episode of a season (optimistic, reverted on failure). */
    data class ToggleSeasonMonitor(val seasonNumber: Int) : ManageSeriesUiEvent

    /** Series-level Sonarr refresh (metadata). */
    data object RefreshSeries : ManageSeriesUiEvent

    /** Series-level Sonarr refresh + disk scan. */
    data object RefreshAndScan : ManageSeriesUiEvent

    /** Series-level Sonarr search for all monitored missing episodes. */
    data object SearchSeries : ManageSeriesUiEvent

    /** Expands/collapses one season's episode list. */
    data class ToggleSeasonExpanded(val seasonNumber: Int) : ManageSeriesUiEvent

    /** Consumes the one-shot snackbar message. */
    data object ClearUserMessage : ManageSeriesUiEvent

    /** Clears the error banner. */
    data object ClearError : ManageSeriesUiEvent
}
