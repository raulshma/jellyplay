package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.Playlist
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
    // True while a pull-to-refresh is in flight. Unlike [isLoading], the
    // current content stays visible while this is set — only the
    // pull-to-refresh indicator spins.
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // True when the item load failed because the user lacks permission (HTTP
    // 401/403). Lets the screen render a dedicated "no access" treatment
    // instead of a generic network-error string.
    val isAccessDenied: Boolean = false,
    // One-shot snackbar feedback for favorite / watched / download actions is
    // surfaced via [DetailViewModel.messages] (SharedFlow<DetailMessage>), not
    // here — see [DetailMessage].
    // ── Unified-provider fields ───────────────────────────────────────────
    // The source origin of the current snapshot (REMOTE / LOCAL_OFFLINE_MODE /
    // LOCAL_REMOTE_FAILURE). Drives source-aware rendering and gates remote-only
    // side effects. Null until the first snapshot lands.
    val origin: DetailOrigin? = null,
    // Carries the attached download, sync state, series aggregate and connectivity.
    // Source of the download-info card and local-management affordances.
    val detailContext: DetailContext? = null,
    // Capability set for the current snapshot (what the UI may offer). Defaults
    // to all-false until a snapshot resolves; the Compose default lives in
    // [Companion.DefaultCapabilities].
    val capabilities: DetailCapabilities = DefaultCapabilities,
    // Local presentation artwork (on-disk poster/backdrop paths + cast portraits)
    // resolved ahead of the server-image fallback for local origins.
    val assets: DetailAssets = DetailAssets(),
    // Manifest-backed external subtitles selectable for local playback.
    val localSubtitles: List<LocalSubtitleOption> = emptyList(),
    // The currently-selected local subtitle stream index (null = none/disabled).
    // Independent from [selectedSubtitleIndex], which is the REMOTE stream index.
    val selectedLocalSubtitleIndex: Int? = null,
    // Resync / re-download action status. Owned by the ViewModel; reset to Idle
    // via DetailViewModel.clearResyncState(). Distinct from the snapshot load state.
    val resyncState: ResyncUiState = ResyncUiState.Idle,
    // Monotonic per-item content generation of the last fully-applied snapshot.
    // Used by the screen only for diagnostics; the VM gates side effects off it.
    val contentGeneration: Long = 0L,
    // Series content
    val seasons: List<MediaItem> = emptyList(),
    val episodes: Map<String, List<MediaItem>> = emptyMap(),
    val fetchedSeasonIds: Set<String> = emptySet(),
    // Every episode across [seasons] in canonical playback order. Mirrors the
    // provider snapshot's sortedEpisodes so smart-play resolution and playlist
    // expansion read from the UI state instead of a local catalogue snapshot.
    val sortedEpisodes: List<MediaItem> = emptyList(),
    // Music content
    val albumTracks: List<MediaItem> = emptyList(),
    // Collection content
    val collectionItems: List<MediaItem> = emptyList(),
    /** Similar/related items — fetched separately from the core detail so the
     *  screen can render incrementally (title/poster/cast first, similar after). */
    val relatedItems: List<MediaItem> = emptyList(),
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
    val cellularDownloadWarningMb: Int? = null,
    val isDownloadingSeries: Boolean = false,
    val downloadSheetEpisodes: Map<String, List<MediaItem>> = emptyMap(),
    val downloadSheetLoadingSeasons: Set<String> = emptySet(),
    val downloadedEpisodeIds: Set<String> = emptySet(),
    // "Manage Series" (DIRECT_ARR_INTEGRATION). Shown for a series with a tvdb
    // id when the experimental flag is on; server resolution is deferred to the
    // ManageSeriesScreen itself (cheap gate here — no network on the detail screen).
    val canManageSeries: Boolean = false,
    // Resolved once per series load (in loadItem) so the canManageSeries combine
    // stays a pure derivation over snapshot state instead of issuing network I/O
    // on every identity tick.
    val sonarrServersResolved: Boolean = false,
    // Add-to-playlist picker (movie/episode/series/music-video detail). The
    // playlist list + flags are fetched on-demand when the picker opens, not
    // on every detail load.
    val playlists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isAddingToPlaylist: Boolean = false,
    val showPlaylistPicker: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
) {
    @Immutable
    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
        // Primary image URL for the targeted episode, precomputed in the VM so the
        // Up Next card can render a thumbnail without an image-url dependency.
        val primaryImageUrl: String? = null,
    )

    companion object {
        /**
         * Capability set used before any snapshot resolves and as the Compose
         * default. All-false: nothing remote-only is offered until a snapshot
         * confirms the source. Local-management flags flip on only when a
         * completed download is attached.
         */
        val DefaultCapabilities: DetailCapabilities = DetailCapabilities(
            remoteDiscovery = false,
            remoteStreamSelection = false,
            localSubtitleSelection = false,
            localStreamInfo = false,
            personNavigation = false,
            studioNavigation = false,
            smartPlay = false,
            remoteWorkAllowed = false,
            localDownloadManagement = false,
        )
    }
}
