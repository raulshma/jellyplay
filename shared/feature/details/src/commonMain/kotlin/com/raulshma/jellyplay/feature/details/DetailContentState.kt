package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * Live in-flight download fraction per episode id (D16, 0..1), provided by
 * [MediaDetailScreen] from the download-lifecycle slice and read directly by
 * the seasons section's episode rows. Rides a composition local — NOT
 * [DetailContentState] — on purpose: the map moves on every ~2 s transfer
 * tick, and the seasons section memoizes its [SeasonsPresentation] on inputs
 * that deliberately exclude per-tick data, so threading the map through the
 * state bag would freeze the rows' bars at their first value. The local is
 * `compositionLocalOf` (not static) so reads are tracked: a ~2 s tick
 * recomposes only the rows whose entry actually changed — a static local
 * would invalidate the entire provider subtree per tick.
 */
internal val LocalEpisodeDownloadProgress = compositionLocalOf<Map<String, Float>> { emptyMap() }

/**
 * The current item's live download row (`DownloadLifecycleActions.downloadFlow`),
 * provided by [MediaDetailScreen] as the FLOW — not the value — so the ~2 s
 * transfer tick invalidates only the leaf that collects it (the ⋮ options-menu
 * builder, the value's one reader) instead of re-executing the
 * [DetailContentState] construction and recomposing the content tree per
 * tick. Companion of [LocalEpisodeDownloadProgress], which is the same
 * placement rule for the per-episode progress map. Null (the default) when no
 * provider is wired — reads as "no active download".
 */
internal val LocalActiveDownloadFlow = staticCompositionLocalOf<kotlinx.coroutines.flow.Flow<DownloadItem?>?> { null }

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
 *
 * ## Shape: core + screen-resolved extras
 *
 * [core] is the ViewModel's [DetailUiState] content snapshot, embedded
 * verbatim. The former shape restated the 43 content fields one by one at the
 * [MediaDetailScreen] construction site — a fold that grew by two lines (bag
 * field + copy) for every new uiState leaf and drifted whenever one was
 * missed. The embed deletes that fold; readers qualify through `state.core.X`.
 *
 * The extras beside [core] are exactly the values the ViewModel does NOT own —
 * per-composition resolutions off the route id, the projected preference
 * store, and the extracted action helpers:
 *
 *  - [itemId]: the route id (the load key — [DetailUiState] carries no item
 *    id),
 *  - [persistedSeasonId]: resolved off the projected preferences for the
 *    current series (an active resume still wins, decided in
 *    SeasonStartResolver),
 *  - [selectedVersionId]: the screen-local version-picker pick (keyed reset on
 *    item change — screen-local state, never a VM field),
 *  - [canManageSeries]: the VM's gate flow, collected at the screen,
 *  - [preferences]: the projected preference flow's current value (a separate
 *    StateFlow from the content core),
 *  - [resyncState]: the resync helper slice (the freshness banner and the
 *    resync sheet read the same value),
 *  - [downloads]: the download-lifecycle slice ([DownloadsExtras], one owner:
 *    [DownloadLifecycleActions] — the screen collects the helper's slim
 *    root-state projection and folds it here verbatim).
 *
 * ## The never-cross list
 *
 * Three [DetailUiState] fields must never be re-stated as extras (or as
 * delegating passthrough properties) on this bag. They ride [core] only
 * because core IS the live uiState; their consumers read the VM/screen
 * directly:
 *
 *  - `seerrRequest`: the Seerr request dialog's frozen-at-open snapshot — the
 *    screen reads it once off the collected uiState for the dialog gate;
 *    folding a copy into the outer bag would re-freeze it at construction
 *    time instead of reading the holder's live value.
 *  - `sortedEpisodes`: the ViewModel's smart-play/playlist resolution input
 *    (the canonical episode order) — no section reads it.
 *  - `sonarrServersResolved`: the canManageSeries combine's input — consumed
 *    inside the VM's gate derivation, never rendered.
 */
@Immutable
internal data class DetailContentState(
    /** The ViewModel's live content snapshot — the single content authority. */
    val core: DetailUiState,
    val itemId: String,
    /**
     * Season id the user last pinned for the current series (resolved by
     * `MediaDetailScreen` from `DetailPreferences.lastViewedSeasonBySeries`).
     * Fed into `SeasonsSection` as `persistedSeasonId`; an active resume still
     * takes precedence. Null when nothing is pinned → prior behaviour.
     */
    val persistedSeasonId: String? = null,
    /**
     * The version (media-source id) the user picked in the detail screen's
     * version picker for this item; null = the server's default (first)
     * source. Pure UI selection — the Play dispatch carries it through
     * `onPlayClick`'s existing id argument, nothing is persisted here.
     */
    val selectedVersionId: String? = null,
    val canManageSeries: Boolean = false,
    val preferences: DetailPreferences = DetailPreferences(),
    /** Resync / re-download action status surfaced on the freshness banner + sheet. */
    val resyncState: ResyncUiState = ResyncUiState.Idle,
    /** The download-lifecycle slice the tree reads (see [DownloadsExtras]). */
    val downloads: DownloadsExtras = DownloadsExtras(),
) {
    /**
     * The download-lifecycle fields the content tree reads — one owner:
     * [DownloadLifecycleActions] (the screen collects the helper's slim
     * root-state projection and folds it here verbatim). The sheet-scoped
     * caches, the cellular-size warning, and the per-tick progress map stay
     * OUT — the sheets and the progress provider read those in their own
     * narrow scopes, so a ~2 s transfer tick never re-copies this bag.
     */
    @Immutable
    data class DownloadsExtras(
        val isDownloading: Boolean = false,
        val isDownloadingSeries: Boolean = false,
        /**
         * Downloaded episode ids for the current series (loaded at screen-open —
         * D16 — and re-read when the download sheet opens). Drives the per-episode
         * delete badge in the seasons section for a REMOTE series with downloads.
         * Empty for a plain remote series with no downloads (the badge is hidden);
         * a LOCAL origin ignores this and treats every episode as downloaded.
         */
        val downloadedEpisodeIds: Set<String> = emptySet(),
        /** Pre-download picker (quality + external-subtitle selection). */
        val downloadPicker: DownloadPickerState = DownloadPickerState(),
    )
}

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
    /** Resolves the clear-logo URL (imageType = Logo) for the "prefer logos" title. */
    val getLogoUrl: (String) -> String = { "" },
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
    /**
     * Open the book reader for the current item (BOOK media type, readable
     * format). The jump pair is the one-shot "Contents" destination (EPUB
     * href / 0-based PDF page) — nulls for plain Read/Continue.
     */
    val onReadClick: (itemId: String, jumpHref: String?, jumpPage: Int?) -> Unit = { _, _, _ -> },
    val onPlayAlbumTrack: (Int) -> Unit = {},
    val onSubtitleSelect: (Int?) -> Unit = {},
    val onAudioSelect: (Int?) -> Unit = {},
    /** Persist a local-subtitle selection for the current item. */
    val onSelectLocalSubtitle: (index: Int?) -> Unit = {},
    /** Start an instant mix for the current audio item (fire-and-forget VM action). */
    val onStartInstantMix: () -> Unit = {},
    /** Start an endless radio for the current audio item (fire-and-forget VM action). */
    val onStartRadio: () -> Unit = {},
    /**
     * Bootstrap a SyncPlay watch party for the current item and open the player
     * (fire-and-forget VM action; success navigates via DetailMessage).
     */
    val onStartWatchParty: () -> Unit = {},
    /**
     * D14 "Play (with pre-rolls)": plays the current item with server
     * intros FORCED for this single play (the ⋮ action bypassing the global
     * cinema-mode pref). The screen arms the one-shot
     * [com.raulshma.jellyplay.core.data.playback.CinemaModeOverride] and
     * dispatches through the normal play path.
     */
    val onPlayWithPrerolls: () -> Unit = {},
    /** Open the version picker sheet (multi-version items only). */
    val onOpenVersionPicker: () -> Unit = {},
    /**
     * D6 "Play collection in order": queues the franchise row's members
     * after the first-unwatched start entry (the manual up-next queue,
     * queue-first semantics; suppressed under SyncPlay by the player's
     * advance routing) and starts playback on the start entry. Composition
     * seam — the queue controller is process-wide and consumed screen-side
     * (DetailContent's enqueueInQueue precedent), so no VM round-trip.
     */
    val onPlayCollectionInOrder: (DetailUiState.FranchiseCollection) -> Unit = {},
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
    /** D6 inline eye-hide: hides one franchise row for the current item (session memory). */
    val onHideFranchiseRow: (collectionId: String) -> Unit = {},
    /**
     * D12: toggles the current series' new-episode notification opt-in (the
     * ⋮ entry and the D7 rows' bell both land here; the screen arms the
     * POST_NOTIFICATIONS prompt beside the write).
     */
    val onToggleSeriesNotifications: (enabled: Boolean) -> Unit = {},
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
 * Admin metadata-maintenance actions for the current item (the ⋮ menu's
 * server-side metadata entries).
 *
 * Grouping rule: the metadata-maintenance openers that open sheets at screen
 * level; the underlying writes run through the `MetadataAdminActions` helper
 * the screen reaches directly.
 */
@Immutable
internal data class MetadataCallbacks(
    /** Open the "Refresh metadata" mode sheet for the current item. */
    val onRefreshMetadata: () -> Unit = {},
    /** Open the "Identify" provider re-match sheet for the current item. */
    val onIdentify: () -> Unit = {},
    /** Open the "Merge versions…" multi-select sheet (admin, movies). */
    val onOpenMergeVersions: () -> Unit = {},
    /** Split the current item's merged versions apart (admin, movies). */
    val onSplitVersions: () -> Unit = {},
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
    /** D8: the top-bar mute chip — mute/unmute the playing theme + write the per-series memory. */
    val onToggleThemeMusicMuted: (Boolean) -> Unit = {},
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
    val metadata: MetadataCallbacks = MetadataCallbacks(),
)
