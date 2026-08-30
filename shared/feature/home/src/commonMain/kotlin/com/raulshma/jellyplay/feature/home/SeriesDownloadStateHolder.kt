package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.message.UiText
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_download_started
import com.raulshma.jellyplay.feature.home.generated.resources.home_download_start_failed
import com.raulshma.jellyplay.feature.home.generated.resources.home_series_download_queued
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the series download sheet opened from a home series card's quick-action
 * Download: the sheet's [HomeSeriesDownloadState] (seasons, per-season
 * episodes, already-downloaded ids) plus the actions that drive it. The VM
 * folds [state] into [HomeUiState.seriesDownload] with one collector (same
 * fold pattern as [SeriesDeleteStateHolder]) and forwards the action methods,
 * so the sheet's UI call sites stay thin.
 *
 * The sheet is the shared `SeriesDownloadSheet` the media-detail screen hosts;
 * where the detail screen reads its session's seasons and expands episodes
 * through [com.raulshma.jellyplay.core.data.repository.MediaDetailProvider],
 * home has no session — so this holder assembles the same shape directly from
 * the [EpisodeCatalogue] seam (`loadSeriesEpisodes` fetches every season's
 * episodes in one batched round trip, which is also what the detail sheet's
 * open-time `prepareDownloadSheetEpisodes` eager-load ends up doing).
 *
 * Why a holder: mirrors [SeriesDeleteStateHolder] — the sheet state and its
 * actions are one concern, testable without the VM's full dependency set.
 */
internal class SeriesDownloadStateHolder(
    /** The VM's scope: sheet loads and queueing must die with the VM. */
    private val scope: CoroutineScope,
    private val episodeCatalogue: EpisodeCatalogue,
    private val downloadRepository: DownloadRepository,
    private val downloadIntake: DownloadIntake,
    private val userMessageBus: UserMessageBus,
) {
    private val _state = MutableStateFlow<HomeSeriesDownloadState?>(null)

    /** Non-null while the series download sheet is open; null once dismissed. */
    val state: StateFlow<HomeSeriesDownloadState?> = _state.asStateFlow()

    /** The in-flight sheet load, if any — see [requestSeriesDownload]. */
    private var loadJob: Job? = null

    /**
     * Opens the sheet for [series]: raises a loading sentinel, assembles the
     * catalogue snapshot (all seasons + episodes in one load) and the series'
     * already-downloaded episode ids, then publishes the rendered state. The
     * series id rides [HomeSeriesDownloadState.loadingSeasons] until the
     * snapshot lands so the sheet opens immediately with its spinner (a series
     * id is never a season id, so it can't collide). Cancels any in-flight
     * load for a previous series first: without that, opening series A,
     * dismissing and quickly opening B could let A's slower load publish last
     * and render B's sheet with A's data.
     */
    fun requestSeriesDownload(series: MediaItem) {
        loadJob?.cancel()
        _state.value = HomeSeriesDownloadState(
            seriesId = series.id,
            seasons = emptyList(),
            episodesBySeason = emptyMap(),
            loadingSeasons = setOf(series.id),
            downloadedEpisodeIds = emptySet(),
        )
        loadJob = scope.launch {
            try {
                val snapshot = episodeCatalogue.loadSeriesEpisodes(series.id).getOrThrow()
                val downloadedIds = downloadRepository.getDownloadedEpisodeIdsForSeries(series.id)
                if (_state.value?.seriesId != series.id) return@launch
                _state.value = HomeSeriesDownloadState(
                    seriesId = series.id,
                    seasons = snapshot.seasons,
                    episodesBySeason = snapshot.episodesBySeason,
                    loadingSeasons = emptySet(),
                    downloadedEpisodeIds = downloadedIds,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A failed load closes the sheet: the state has no error field,
                // and leaving the loading sentinel would wedge the spinner
                // forever. The user can reopen the sheet to retry.
                userMessageBus.error(
                    UiText.Resource(Res.string.home_download_start_failed)
                )
                _state.value = null
            }
        }
    }

    /**
     * Expands one season's episodes on demand. The open-time snapshot already
     * carries every fetched season, so this only runs for seasons the snapshot
     * didn't fill (the sheet calls it when a season is missing from the map —
     * same contract as the detail screen's lazy expansion).
     */
    fun loadSeasonEpisodes(seasonId: String) {
        val current = _state.value ?: return
        if (seasonId in current.episodesBySeason.keys) return
        _state.value = current.copy(loadingSeasons = current.loadingSeasons + seasonId)
        scope.launch {
            try {
                val episodes = episodeCatalogue.loadSeasonEpisodes(current.seriesId, seasonId).getOrThrow()
                _state.value = _state.value?.let { st ->
                    // This job isn't tracked by loadJob, so it can outlive a
                    // dismiss and overlap a freshly opened sheet for a
                    // different series. Leave that sheet untouched — only the
                    // matching series' state is amended.
                    if (st.seriesId != current.seriesId) st else st.copy(
                        episodesBySeason = st.episodesBySeason + (seasonId to episodes),
                        loadingSeasons = st.loadingSeasons - seasonId,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Clear just this season's spinner; the sheet keeps working
                // with the seasons it already has.
                _state.value = _state.value?.let { st ->
                    if (st.seriesId != current.seriesId) st else st.copy(loadingSeasons = st.loadingSeasons - seasonId)
                }
            }
        }
    }

    /**
     * Queues the selected episodes and closes the sheet. Mirrors the detail
     * screen's flow: the sheet dismisses immediately and the batch runs in the
     * background, surfacing a success/failure message once the intake resolves.
     */
    fun downloadSeries(selectedEpisodes: Map<String, List<String>>) {
        val current = _state.value ?: return
        val nonEmpty = selectedEpisodes.filterValues { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return
        _state.value = null
        scope.launch {
            val result = downloadIntake.startSeries(current.seriesId, nonEmpty)
            // Any success is a success: the intake legitimately enqueues
            // nothing when its internal catalogue fallback comes back empty
            // (nothing matched), and the detail screen's flow treats that the
            // same way instead of raising a failure.
            if (result.isSuccess) {
                userMessageBus.info(
                    UiText.Resource(Res.string.home_series_download_queued)
                )
            } else {
                userMessageBus.error(
                    UiText.Resource(Res.string.home_download_start_failed)
                )
            }
        }
    }

    /** Closes the sheet. A load still in flight is cancelled so it can't republish state after dismissal. */
    fun dismiss() {
        loadJob?.cancel()
        _state.value = null
    }
}
