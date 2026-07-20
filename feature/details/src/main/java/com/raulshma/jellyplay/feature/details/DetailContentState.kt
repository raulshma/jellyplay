package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.UserPreferences
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
    val preferences: UserPreferences,
    val canManageSeries: Boolean,
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
    val onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    val onAudioClick: () -> Unit,
    val onDownloadClick: () -> Unit,
    val onDownloadSeriesClick: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onMarkPlayed: () -> Unit,
    val onMarkUnplayed: () -> Unit,
    val onSubtitleSelect: (Int?) -> Unit,
    val onAudioSelect: (Int?) -> Unit,
    val onItemClick: (String) -> Unit,
    val onPersonClick: (String) -> Unit,
    val onNavigateToSeries: (String) -> Unit,
    val onSeasonSelected: (String) -> Unit,
    val onEpisodesDescendingChange: (Boolean) -> Unit,
    val onBack: () -> Unit,
    val onSeerrRequest: (SeerrSearchItem) -> Unit,
    val onNavigate: (Route) -> Unit,
    val onEditClick: () -> Unit,
    val onPlayAlbumTrack: (Int) -> Unit,
    val onVideoClick: (SeerrRelatedVideo) -> Unit,
    val onHideFromNextUp: () -> Unit,
    val onHideFromContinueWatching: () -> Unit,
    val onManageSeries: () -> Unit,
)
