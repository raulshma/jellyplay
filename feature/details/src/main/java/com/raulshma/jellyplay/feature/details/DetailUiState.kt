package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.TmdbReview

/**
 * Single-source-of-truth CONTENT state for the media-detail screen.
 *
 * Every observable screen state lives behind this data class so that:
 *  - Compose sees **atomic snapshots** — a content-load no longer triggers
 *    25+ independent recompositions as separate holders flip in sequence.
 *  - State restoration works against a single serializable object.
 *  - Tests assert against one object instead of mocking dozens of fields.
 *
 * This is deliberately the **content core** only — atomic snapshots of the
 * detail/seasons/episodes tree plus remote-discovery ephemera written solely
 * by the ViewModel body. Per-sheet action state does NOT live here: downloads,
 * playlists, collections, and resync are owned by their action helpers
 * ([DownloadLifecycleActions], [PlaylistActions], [CollectionActions],
 * [ResyncActions]), which publish their own `StateFlow`s and are collected
 * directly by the sheet that needs them (see the `viewModel.downloads` /
 * `viewModel.playlists` / `viewModel.collections` / `viewModel.resync`
 * seams). Re-flattening helper state back into this bag was removed — it
 * re-copied the entire state on every helper tick and froze the bag at 55
 * fields.
 */
@Immutable
data class DetailUiState(
    // Core load state — `detail` drives content visibility; [loadState] drives
    // the loading / refreshing / error overlays (only painted when there is no
    // content yet, per [com.raulshma.jellyplay.feature.details.DetailContent]).
    val detail: MediaDetail? = null,
    // Sealed load state for the core fetch. Default [DetailUiLoadState.Loaded]
    // matches the former all-false default (no loading, no refreshing, no error).
    val loadState: DetailUiLoadState = DetailUiLoadState.Loaded,
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
    /** Special features / extras (featurettes, deleted scenes, interviews, etc.)
     *  sourced from Jellyfin's `/Items/{id}/SpecialFeatures` endpoint. Tapping an
     *  extra plays it in the video player. Remote-only — empty for a local origin. */
    val specialFeatures: List<MediaItem> = emptyList(),
    /** LOCAL-origin "More like this": on-device titles sharing a genre/studio,
     *  surfaced so a downloaded item isn't a discovery island offline. Empty for
     *  a remote origin (which uses [relatedItems]). */
    val localRelatedItems: List<MediaItem> = emptyList(),
    /** True when the resolved item has an INTRO media segment (intro skip
     *  available). Fetched alongside [relatedItems] so the player's segment cache
     *  is pre-warmed and the detail chip can render before playback. Remote-only. */
    val hasIntroSegment: Boolean = false,
    /** True when the resolved item has an OUTRO (credits) media segment. Paired
     *  with [hasIntroSegment] to drive the detail-side skip chip. Remote-only. */
    val hasCreditSegment: Boolean = false,
    // Smart play (continue-watching / next-up computed target)
    val smartPlayTarget: SmartPlayTarget? = null,
    // Stream selection (audio/subtitle indices persisted across sessions)
    val selectedSubtitleIndex: Int? = null,
    val selectedAudioIndex: Int? = null,
    // Seerr discovery (recommendations / similar / videos / TMDB reviews)
    val seerrRecommendations: List<SeerrSearchItem> = emptyList(),
    val seerrSimilar: List<SeerrSearchItem> = emptyList(),
    val relatedVideos: List<SeerrRelatedVideo> = emptyList(),
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val isSeerrConnected: Boolean = false,
    val isSeerrRecommendationsEnabled: Boolean = false,
    // Seerr request flow (radarr/sonarr picker + result banner)
    val seerrRequestResult: SeerrRequestResult? = null,
    val seerrRadarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val seerrSonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingSeerrServices: Boolean = false,
    val seerrTvSeasons: List<SeerrSeason> = emptyList(),
    // "Manage Series" (DIRECT_ARR_INTEGRATION). Shown for a series with a tvdb
    // id when the experimental flag is on; server resolution is deferred to the
    // ManageSeriesScreen itself (cheap gate here — no network on the detail screen).
    val canManageSeries: Boolean = false,
    // Resolved once per series load (in loadItem) so the canManageSeries combine
    // stays a pure derivation over snapshot state instead of issuing network I/O
    // on every identity tick.
    val sonarrServersResolved: Boolean = false,
) {
    @Immutable
    data class SmartPlayTarget(
        val episode: MediaItem,
        val label: String,
        val startPositionTicks: Long,
        // Primary image URL for the targeted episode, precomputed in the VM so the
        // Up Next card can render a thumbnail without an image-url dependency.
        val primaryImageUrl: String? = null,
        val labelKind: LabelKind? = null,
    ) {
        val isNextUpOrResume: Boolean
            get() = labelKind == LabelKind.RESUME_EPISODE ||
                labelKind == LabelKind.NEXT_UP_EPISODE ||
                (startPositionTicks > 0L && !episode.isPlayed)
    }

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
            tagNavigation = false,
            chapters = false,
        )
    }
}

/**
 * Sealed load state for [DetailUiState.loadState]. Collapses the former
 * `isLoading` / `isRefreshing` / `error` / `isAccessDenied` field soup into a
 * single mutually-exclusive state so the screen renders exactly one overlay
 * treatment. [DetailUiState.detail] stays a separate field and remains the
 * single authority for content visibility — a load state is only surfaced when
 * there is no content yet.
 */
sealed interface DetailUiLoadState {
    /** Full-screen loading — no content yet. */
    data object Loading : DetailUiLoadState
    /** Pull-to-refresh; content stays visible underneath. */
    data object Refreshing : DetailUiLoadState
    data class Error(
        val message: String,
        val accessDenied: Boolean,
        val unavailableOffline: Boolean = false,
    ) : DetailUiLoadState
    /** Load complete (incl. LOCAL_REMOTE_FAILURE origin — rendered silently, by decision). */
    data object Loaded : DetailUiLoadState
}
