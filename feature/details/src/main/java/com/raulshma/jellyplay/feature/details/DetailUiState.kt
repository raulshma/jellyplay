package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo

/**
 * Single-source-of-truth UI state for the media-detail screen.
 *
 * Every observable screen state lives behind this data class so that:
 *  - Compose sees **atomic snapshots** — a content-load no longer triggers
 *    25+ independent recompositions as separate holders flip in sequence.
 *  - State restoration works against a single serializable object.
 *  - Tests assert against one object instead of mocking dozens of fields.
 *
 * Backward-compatible property accessors on [DetailViewModel] still project
 * these fields for existing call sites; new code should prefer
 * `viewModel.uiState.collectAsStateWithLifecycle()`.
 */
@Immutable
data class DetailUiState(
    // Core load state
    val detail: MediaDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Transient, user-facing feedback (snackbar) for one-shot actions such as
    // favorite / watched toggles that fail after an optimistic UI update. The
    // screen is responsible for showing and then clearing it.
    val userMessage: String? = null,
    // Series content
    val seasons: List<MediaItem> = emptyList(),
    val episodes: Map<String, List<MediaItem>> = emptyMap(),
    val fetchedSeasonIds: Set<String> = emptySet(),
    // Music content
    val albumTracks: List<MediaItem> = emptyList(),
    // Collection content
    val collectionItems: List<MediaItem> = emptyList(),
    // Smart play (continue-watching / next-up computed target)
    val smartPlayTarget: SmartPlayTarget? = null,
    // Stream selection (audio/subtitle indices persisted across sessions)
    val selectedSubtitleIndex: Int? = null,
    val selectedAudioIndex: Int? = null,
    // Seerr discovery (recommendations / similar / videos)
    val seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    val seerrSimilar: List<SeerrSearchItem> = emptyList(),
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val isSeerrConnected: Boolean = false,
    val isSeerrRecommendationsEnabled: Boolean = false,
    // Seerr request flow (radarr/sonarr picker + result banner)
    val seerrRequestResult: SeerrRequestResult? = null,
    val seerrRadarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val seerrSonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingSeerrServices: Boolean = false,
    val seerrTvSeasons: List<SeerrSeason> = emptyList(),
    // Downloads
    val isDownloading: Boolean = false,
    val downloadError: String? = null,
    val cellularDownloadWarningMb: Int? = null,
    val isDownloadingSeries: Boolean = false,
    val seriesDownloadResult: SeriesDownloadResult? = null,
    val downloadSheetEpisodes: Map<String, List<MediaItem>> = emptyMap(),
    val downloadSheetLoadingSeasons: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    // Delete & re-download (DIRECT_ARR_INTEGRATION). Shown only when a relevant
    // *arr server is resolved; the dialog confirms before the destructive
    // delete + monitor + search flow.
    val canRedownload: Boolean = false,
    val showRedownloadDialog: Boolean = false,
    val isRedownloading: Boolean = false,
    val redownloadResult: RedownloadOutcome? = null,
    // For episodes: the parent series' provider IDs (e.g. "tvdb" → series tvdb id).
    // Sonarr's findSeriesByTvdb lookup requires the *series* tvdb id, not the
    // episode-level tvdb id stored in [detail.providerIds].
    val seriesProviderIds: Map<String, String> = emptyMap(),
) {
    @Immutable
    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
    )

    /**
     * Outcome of a delete & re-download flow. Drives the result dialog:
     * success → navigate back (item deleted); partial → the file is gone but a
     * *arr step failed (still navigate back); delete failed → stay and report.
     */
    @Immutable
    sealed class RedownloadOutcome {
        data object InProgress : RedownloadOutcome()
        data class Success(val message: String) : RedownloadOutcome()
        data class PartialFailure(val message: String) : RedownloadOutcome()
        data class DeleteFailed(val message: String) : RedownloadOutcome()
    }
}
