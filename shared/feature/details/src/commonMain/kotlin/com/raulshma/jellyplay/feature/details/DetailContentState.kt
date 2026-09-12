package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DetailAssets
import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.DetailContext
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.LocalSubtitleOption
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
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
    /**
     * Season id the user last pinned for the current series (resolved by
     * `MediaDetailScreen` from `DetailPreferences.lastViewedSeasonBySeries`).
     * Fed into `SeasonsSection` as `persistedSeasonId`; an active resume still
     * takes precedence. Null when nothing is pinned → prior behaviour.
     */
    val persistedSeasonId: String? = null,
    val selectedSubtitleIndex: Int?,
    val selectedAudioIndex: Int?,
    val isDownloading: Boolean,
    val isDownloadingSeries: Boolean,
    val activeDownload: DownloadItem?,
    val loadState: DetailUiLoadState,
    val albumTracks: List<MediaItem>,
    val collectionItems: List<MediaItem>,
    val relatedItems: List<MediaItem>,
    val specialFeatures: List<MediaItem> = emptyList(),
    val localRelatedItems: List<MediaItem> = emptyList(),
    /** Intro skip available (INTRO segment resolved for the item). Drives the chip. */
    val hasIntroSegment: Boolean = false,
    /** Credits skip available (OUTRO segment resolved for the item). Drives the chip. */
    val hasCreditSegment: Boolean = false,
    val relatedVideos: List<SeerrRelatedVideo>,
    /** TMDB-sourced reviews shown in their own section (no Seerr connection needed). */
    val tmdbReviews: List<TmdbReview> = emptyList(),
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
    // Pre-download picker (quality + external-subtitle selection).
    val downloadPicker: DownloadPickerState = DownloadPickerState(),
)

/**
 * Artwork / image-URL resolution for the media-detail content tree.
 *
 * Grouping rule: every lambda that turns an id (or chapter coordinates) into a
 * loadable image URL. These are read across nearly every section (poster,
 * cast, rows, chapter tiles), so they live in their own bundle rather than
 * inside any single section's action group. URL getters default to an empty
 * string (MediaImage renders its placeholder) so partially-wired consumers
 * keep compiling.
 */
@Immutable
internal data class ArtworkCallbacks(
    val getImageUrl: (String) -> String = { "" },
    val getBackdropUrl: (String) -> String = { "" },
    /** Resolves a chapter thumbnail URL (imageType = Chapter) by list index + tag. */
    val getChapterImageUrl: (itemId: String, imageIndex: Int, tag: String?) -> String = { _, _, _ -> "" },
)

/**
 * Everything that configures or starts playback for the current item.
 *
 * Grouping rule: the play dispatch family (primary play, chapter resume,
 * extras, album track, audio handoff) plus the stream-selection writes the
 * media-info section persists (remote subtitle/audio indexes, local subtitle
 * index) — the play dispatch resolves those selections at invocation time, so
 * selection and dispatch are one concern. Instant mix and watch party live
 * here too: both are fire-and-forget VM actions that start a playback session
 * for the current item.
 */
@Immutable
internal data class PlaybackCallbacks(
    val onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit = { _, _, _ -> },
    /** Resume-from-chapter: hand off to the player at a chapter's start position. */
    val onPlayChapter: (startTicks: Long) -> Unit = {},
    /** Play a special feature / extra (featurette, deleted scene, etc.) in the player. */
    val onPlayExtra: (MediaItem) -> Unit = {},
    val onAudioClick: () -> Unit = {},
    /** Open the book reader for the current item (BOOK media type, readable format). */
    val onReadClick: (itemId: String) -> Unit = {},
    val onPlayAlbumTrack: (Int) -> Unit = {},
    val onSubtitleSelect: (Int?) -> Unit = {},
    val onAudioSelect: (Int?) -> Unit = {},
    /** Persist a local-subtitle selection for the current item. */
    val onSelectLocalSubtitle: (index: Int?) -> Unit = {},
    /** Start an instant mix for the current audio item (fire-and-forget VM action). */
    val onStartInstantMix: () -> Unit = {},
    /**
     * Bootstrap a SyncPlay watch party for the current item and open the player
     * (fire-and-forget VM action; success navigates via DetailMessage).
     */
    val onStartWatchParty: () -> Unit = {},
)

/**
 * Downloads and the attached-download lifecycle (offline/resync surface).
 *
 * Grouping rule: the pre-download picker (open/dismiss/pending quality/pending
 * subtitles), single + series download starts, every delete path (attached
 * download, single episode, batch sheet), and the sheet openers for resync and
 * the full download-details view. The resync sheet itself reads the VM
 * directly at screen level, so only its opener travels through the tree.
 */
@Immutable
internal data class DownloadCallbacks(
    val onDownloadClick: () -> Unit = {},
    /** Open the pre-download picker (quality + external-subtitle selection). */
    val onOpenDownloadPicker: () -> Unit = {},
    /** Dismiss the pre-download picker without starting a download. */
    val onDismissDownloadPicker: () -> Unit = {},
    /** Replace the pending download quality in the picker. */
    val onPendingQualityChange: (DownloadQuality) -> Unit = {},
    /** Replace the pending external-subtitle selection. */
    val onPendingSubtitleSelectionChange: (SubtitleSelection) -> Unit = {},
    val onDownloadSeriesClick: () -> Unit = {},
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
    /** Open the full download-details bottom sheet (DownloadInfoCard tap). */
    val onOpenDownloadDetails: () -> Unit = {},
)

/**
 * The seasons/episodes section's own interactions.
 *
 * Grouping rule: everything the `SeasonsSection` header + episode list needs —
 * season tab selection/pinning, the two episode-list preferences, and the
 * season-level mark-played pair (they join this bundle because the seasons
 * section consumes them, even though the item-level marks live in
 * [UserDataCallbacks]).
 */
@Immutable
internal data class SeasonsCallbacks(
    val onSeasonSelected: (String) -> Unit = {},
    /**
     * Persists the user's season-tab choice for the current series. Fires only
     * on a user-initiated tab select (never from the init `LaunchedEffect`).
     */
    val onSeasonPinned: (String) -> Unit = {},
    val onEpisodesDescendingChange: (Boolean) -> Unit = {},
    val onCompactEpisodeListChange: (Boolean) -> Unit = {},
    val onMarkSeasonPlayed: (seasonId: String) -> Unit = {},
    val onMarkSeasonUnplayed: (seasonId: String) -> Unit = {},
)

/**
 * Watch-state + feed-visibility user-data mutations for the current item.
 *
 * Grouping rule: the Jellyfin user-data writes behind the action row (favorite
 * toggle, item-level mark played/unplayed) and the display-preference toggles
 * surfaced in the ⋮ menu (next-up / continue-watching inclusion and the
 * detail screen's own Up Next section).
 */
@Immutable
internal data class UserDataCallbacks(
    val onToggleFavorite: () -> Unit = {},
    val onMarkPlayed: () -> Unit = {},
    val onMarkUnplayed: () -> Unit = {},
    val onHideFromNextUp: () -> Unit = {},
    val onShowFromNextUp: () -> Unit = {},
    val onHideFromContinueWatching: () -> Unit = {},
    val onShowFromContinueWatching: () -> Unit = {},
    val onShowDetailUpNext: () -> Unit = {},
    val onHideDetailUpNext: () -> Unit = {},
)

/**
 * Seerr-sourced sections: request-a-media cards and related videos.
 *
 * Grouping rule: the lambdas consumed by the Seerr recommendation/similar
 * rows and the related-videos (trailer) row — the two section families whose
 * content comes from Seerr/TMDB instead of the Jellyfin server.
 */
@Immutable
internal data class SeerrCallbacks(
    val onSeerrRequest: (SeerrSearchItem) -> Unit = {},
    /** Click on a related video (trailer) card from the Seerr videos row. */
    val onVideoClick: (SeerrRelatedVideo) -> Unit = {},
)

/**
 * Add-to-container actions (playlists + collections) for the current item.
 *
 * Grouping rule: the two picker openers that ride the `AddToTargetActions`
 * module (`viewModel.playlists` / `viewModel.collections`). The sheets and
 * create dialogs themselves compose at screen level; only the opener needs to
 * reach the ⋮ menu.
 */
@Immutable
internal data class AddToCallbacks(
    val onAddToPlaylist: () -> Unit = {},
    /** Open the Add-to-Collection picker for the current item (fire-and-forget VM action). */
    val onAddToCollection: () -> Unit = {},
)

/**
 * Navigation off the detail screen.
 *
 * Grouping rule: every lambda whose job is to land the user on another screen
 * — back, generic route navigation, item/person drill-ins, see-all cast,
 * series drill-in, manage-series, and the metadata editor.
 */
@Immutable
internal data class NavigationCallbacks(
    val onBack: () -> Unit = {},
    val onNavigate: (Route) -> Unit = {},
    val onItemClick: (String) -> Unit = {},
    val onPersonClick: (String) -> Unit = {},
    /** Open the full Cast & Crew screen (reached from "See all" on the cast row). */
    val onSeeAllCast: () -> Unit = {},
    val onNavigateToSeries: (String) -> Unit = {},
    val onManageSeries: () -> Unit = {},
    val onEditClick: () -> Unit = {},
)

/**
 * Screen-surface actions that belong to no content section.
 *
 * Grouping rule: load-lifecycle recovery (retry/refresh) and the row-card
 * quick-action intake bridging (open the sheet, track the TV-focused card) —
 * concerns of the screen chrome rather than of any one section.
 */
@Immutable
internal data class ScreenCallbacks(
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    /** Open the quick-action sheet for a row item */
    val onMediaQuickActions: (MediaItem) -> Unit = {},
    /** Track the TV-focused row item so the Menu key can open its quick actions. */
    val onFocusedMediaItem: (MediaItem) -> Unit = {},
)

/**
 * Callback bundle for the media-detail content tree: a container of the nine
 * per-concern bundles above instead of one flat lambda list.
 *
 * Grouping the tree's ~55 action lambdas by the section/subsystem they serve
 * keeps composable signatures readable (one stable parameter) and makes a new
 * capability a two-file edit — its bundle + its section — instead of touching
 * five files. `MediaDetailScreen` remembers each bundle on exactly the inputs
 * its lambdas capture, then remembers this container on the bundles. Although
 * declared `data class`, lambda fields use reference equality, so each bundle
 * is effectively identity-compared — same model as
 * [com.raulshma.jellyplay.feature.home.HomeContentCallbacks]. Every bundle
 * defaults to a no-op instance so partially-wired consumers keep compiling.
 */
@Immutable
internal data class DetailContentCallbacks(
    val artwork: ArtworkCallbacks = ArtworkCallbacks(),
    val playback: PlaybackCallbacks = PlaybackCallbacks(),
    val download: DownloadCallbacks = DownloadCallbacks(),
    val seasons: SeasonsCallbacks = SeasonsCallbacks(),
    val userData: UserDataCallbacks = UserDataCallbacks(),
    val seerr: SeerrCallbacks = SeerrCallbacks(),
    val addTo: AddToCallbacks = AddToCallbacks(),
    val navigation: NavigationCallbacks = NavigationCallbacks(),
    val screen: ScreenCallbacks = ScreenCallbacks(),
)
