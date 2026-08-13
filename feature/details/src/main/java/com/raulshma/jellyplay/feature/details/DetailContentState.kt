package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Pure-data bundle for the media-detail content tree.
 *
 * Groups every value the `DetailContent` → `DetailContentBody` → section
 * composables read off the screen state into a single [Immutable] holder so the
 * Compose compiler treats the receiving composable as skippable (one stable
 * parameter instead of ~25 unstable ones), eliminating cascading recompositions
 * when an unrelated callback lambda reallocates.
 *
 * Mirrors the `HomeContentState` pattern introduced in the home-screen
 * decomposition (commit 216e7a7b).
 */
@Immutable
internal data class DetailContentState(
    val itemId: String,
    val detail: MediaDetail?,
    val seasons: List<MediaItem>,
    val episodes: Map<String, List<MediaItem>>,
    val fetchedSeasonIds: Set<String>,
    val smartPlayTarget: DetailUiState.SmartPlayTarget?,
    val selectedSubtitleIndex: Int?,
    val selectedAudioIndex: Int?,
    val isDownloading: Boolean,
    val isDownloadingSeries: Boolean,
    val activeDownload: DownloadItem?,
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val error: String?,
    val isAccessDenied: Boolean,
    val albumTracks: List<MediaItem>,
    val collectionItems: List<MediaItem>,
    val relatedItems: List<MediaItem>,
    val relatedVideos: List<SeerrRelatedVideo>,
    val seerrRecommendations: List<SeerrSearchItem>,
    val seerrSimilar: List<SeerrSearchItem>,
    val isSeerrConnected: Boolean,
    val isSeerrRecommendationsEnabled: Boolean,
    val preferences: DetailPreferences,
    val canManageSeries: Boolean,
    // ── Unified-provider fields. Drives source-aware rendering of the
    // download/sync UI, local subtitle selector, asset-aware image resolution,
    // and capability-gated navigation. Empty/default for a plain REMOTE item so
    // the remote-only rendering path is unchanged. ──
    /** Source origin of the snapshot. Null until the first snapshot lands. */
    val origin: DetailOrigin? = null,
    /** Attached download lifecycle + sync state + series aggregate. */
    val detailContext: DetailContext? = null,
    /** Capability set for the snapshot — single authority for source-aware gating. */
    val capabilities: DetailCapabilities = DetailUiState.DefaultCapabilities,
    /** On-disk artwork (poster/backdrop/cast portraits) preferred for local origins. */
    val assets: DetailAssets = DetailAssets(),
    /** Manifest-backed external subtitles selectable for local playback. */
    val localSubtitles: List<LocalSubtitleOption> = emptyList(),
    /** Currently-selected local subtitle stream index (null = none/disabled). */
    val selectedLocalSubtitleIndex: Int? = null,
    /** Resync / re-download action status surfaced on the freshness banner + sheet. */
    val resyncState: ResyncUiState = ResyncUiState.Idle,
    /**
     * Downloaded episode ids for the current series (populated lazily when the
     * download sheet opens). Drives the per-episode delete badge in the seasons
     * section for a REMOTE series with downloads. Empty for a plain remote series
     * with no downloads (the badge is hidden); a LOCAL origin ignores this and
     * treats every episode as downloaded.
     */
    val downloadedEpisodeIds: Set<String> = emptySet(),
)

/**
 * Callback bundle for the media-detail content tree.
 *
 * Grouping the ~27 navigation/action lambdas into one [Immutable] holder keeps
 * composable signatures readable and lets callers `remember` the bundle once at
 * the screen entry point so child composables receive a stable reference across
 * recompositions. Although declared `data class`, lambda fields use reference
 * equality, so the bundle is effectively identity-compared — same model as
 * [com.raulshma.jellyplay.feature.home.HomeContentCallbacks].
 */
@Immutable
internal data class DetailContentCallbacks(
    val getImageUrl: (String) -> String,
    val getBackdropUrl: (String) -> String,
    val getSeerrPosterUrl: (String?) -> String?,
    val onRetry: () -> Unit,
    val onRefresh: () -> Unit,
    val onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    val onAudioClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onDownloadSeriesClick: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onMarkPlayed: () -> Unit,
    val onMarkUnplayed: () -> Unit,
    val onMarkSeasonPlayed: (seasonId: String) -> Unit,
    val onMarkSeasonUnplayed: (seasonId: String) -> Unit,
    val onSubtitleSelect: (Int?) -> Unit,
    val onAudioSelect: (Int?) -> Unit,
    val onItemClick: (String) -> Unit,
    val onPersonClick: (String) -> Unit,
    val onNavigateToSeries: (String) -> Unit,
    val onSeasonSelected: (String) -> Unit,
    val onEpisodesDescendingChange: (Boolean) -> Unit,
    val onCompactEpisodeListChange: (Boolean) -> Unit,
    val onBack: () -> Unit,
    val onSeerrRequest: (SeerrSearchItem) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onEditClick: () -> Unit,
    val onPlayAlbumTrack: (Int) -> Unit,
    val onVideoClick: (SeerrRelatedVideo) -> Unit,
    val onHideFromNextUp: () -> Unit,
    val onShowFromNextUp: () -> Unit,
    val onHideFromContinueWatching: () -> Unit,
    val onShowFromContinueWatching: () -> Unit,
    val onShowDetailUpNext: () -> Unit = {},
    val onHideDetailUpNext: () -> Unit = {},
    val onManageSeries: () -> Unit,
    val onAddToPlaylist: () -> Unit,
    /** Open the quick-action sheet for a row item */
    val onMediaQuickActions: (MediaItem) -> Unit = {},
    /** Track the TV-focused row item so the Menu key can open its quick actions. */
    val onFocusedMediaItem: (MediaItem) -> Unit = {},
    // ── Unified-provider action callbacks. Wired by MediaDetailScreen
    // to the merged DetailViewModel methods. Default to no-op so existing
    // call sites and previews that don't supply them keep compiling. ──
    /** Delete the current item's attached download (single item or episode). */
    val onDeleteDownload: () -> Unit = {},
    /** Delete a single downloaded episode by id (from the seasons section). */
    val onDeleteEpisode: (episodeId: String) -> Unit = {},
    /**
     * Open the series batch-delete sheet (multi-select downloaded episodes +
     * whole-series delete). Shown only for a non-remote series with downloaded
     * episodes — the gating lives in [rememberMediaOptions].
     */
    val onDeleteDownloadedEpisodes: () -> Unit = {},
    /** Open the resync bottom sheet (banner tap). */
    val onOpenResync: () -> Unit = {},
    /** Resync metadata/images from the server. */
    val onResync: () -> Unit = {},
    /** Re-download the media file (media-source change). */
    val onRedownloadMedia: () -> Unit = {},
    /** Clear the resync action status (sheet dismiss). */
    val onClearResync: () -> Unit = {},
    /** Open the full download-details bottom sheet (DownloadInfoCard tap). */
    val onOpenDownloadDetails: () -> Unit = {},
    /** Persist a local-subtitle selection for the current item. */
    val onSelectLocalSubtitle: (index: Int?) -> Unit = {},
)
