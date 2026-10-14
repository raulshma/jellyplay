package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowsJoin
import com.composables.icons.tabler.outline.ArrowsSplit
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.BellOff
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye

import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.ListDetails
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.PlayerTrackPrev
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Stack2
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Radio
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.isVideoType
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_delete_download_title
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_add_to_collection
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_add_to_playlist
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_delete_downloads
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_download
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_download_series
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_downloading
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_downloading_percent
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_downloading_series
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_edit
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_hide_detail_up_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_identify
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_play_last
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_play_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_play_with_prerolls
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_notify_new_episodes
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_stop_notify_new_episodes
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_hide_from_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_hide_from_next_up
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_instant_mix
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_manage_series
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_merge_versions
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_refresh_metadata
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_split_versions
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_start_radio
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_share
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_show_detail_up_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_show_in_continue_watching
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_show_in_next_up
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_technical_info
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_version
import com.raulshma.jellyplay.feature.details.generated.resources.detail_option_watch_party
import org.jetbrains.compose.resources.stringResource

/**
 * A single resolved options-menu entry, shared by the touch [DropdownMenu]
 * and the TV [TvSafeSheet] so the two menus can never drift apart.
 *
 * [id] is a stable snake_case action key ("download", "watch_party", …) the
 * D23 promoted-rail filter keys on: the landscape rail lifts a fixed id set
 * out of this list and the ⋮ menu suppresses exactly those ids while the rail
 * shows them, so an id must identify the menu SLOT (never the label — the
 * download slot's label legitimately varies download → downloading → delete
 * by transfer state).
 */
internal data class MediaOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The fixed option-id set the landscape/TV detail rail promotes out of the ⋮
 * menu (D23, v1 — per-item customization is explicitly out of scope). Ids the
 * current menu does not carry are ignored: "mark_watched" and "trailer" act
 * from the action row / videos row today, so the rail renders only the
 * entries that actually resolve.
 */
internal val PromotedMediaOptionIds: List<String> = listOf(
    "download",
    "mark_watched",
    "trailer",
    "watch_party",
)

/**
 * This list minus every option whose [MediaOption.id] is in [promotedIds] —
 * the ⋮-menu half of the D23 no-dupes rule (applied only while the rail
 * actually shows the promoted set; an empty [promotedIds] is a pass-through).
 * Pure and order-preserving.
 */
internal fun List<MediaOption>.excludingIds(promotedIds: Set<String>): List<MediaOption> =
    if (promotedIds.isEmpty()) {
        this
    } else {
        filter { it.id !in promotedIds }
    }

/**
 * This list's options whose [MediaOption.id] is in [promotedIds], resolved in
 * the fixed [PromotedMediaOptionIds] order (the rail's row order is stable
 * regardless of where each entry sits in the menu) and skipping ids the menu
 * does not carry. Pure.
 */
internal fun List<MediaOption>.promotedOptions(
    promotedIds: Collection<String> = PromotedMediaOptionIds,
): List<MediaOption> = promotedIds.mapNotNull { id -> firstOrNull { it.id == id } }

/**
 * D14: the "Play (with pre-rolls)" entry's gate — server intros are
 * meaningful only for MOVIE/EPISODE plays (a SERIES has nothing of its own
 * to pre-roll; intros are episode-scoped), need the server (never offline),
 * and the caller resolves the host-level SyncPlay veto into [canPlay].
 * Pure — pinned by the menu tests.
 */
internal fun offersPlayWithPrerolls(
    mediaType: MediaType?,
    isOffline: Boolean,
    canPlay: Boolean,
): Boolean = canPlay && !isOffline &&
    (mediaType == MediaType.MOVIE || mediaType == MediaType.EPISODE)

/**
 * D12: the "Notify me about new episodes" entry's gate — a per-SERIES opt-in
 * (an episode's notifications key off its parent series; nothing else has a
 * "new episode"), needs the server (the worker reads Jellyfin + optionally
 * *arr), and only where a notification stack exists (desktop: none — the
 * entry hides). Pure — pinned by the menu tests.
 */
internal fun offersSeriesNewEpisodeNotifications(
    mediaType: MediaType?,
    isOffline: Boolean,
    platformSupported: Boolean,
): Boolean = platformSupported && !isOffline && mediaType == MediaType.SERIES

/**
 * Builds the single ordered list of options-menu entries for the media
 * detail screen. Both the DropdownMenu (touch) and TvSafeSheet (TV)
 * renderers iterate this list, eliminating the previous duplication where
 * each menu reimplemented the same item/condition/download-progress logic.
 *
 * Each entry's [onClick] closes whichever menu is open (via [onClose])
 * before performing its action.
 */
@Composable
internal fun rememberMediaOptions(
    item: MediaItem?,
    detail: MediaDetail?,
    itemId: String,
    isAudio: Boolean,
    isSeries: Boolean,
    seasons: List<MediaItem>,
    preferences: DetailPreferences,
    activeDownload: DownloadItem?,
    isDownloading: Boolean,
    isDownloadingSeries: Boolean,
    canManageSeries: Boolean,
    canDeleteDownloadedSeries: Boolean,
    canEditMetadata: Boolean,
    canRefreshMetadata: Boolean,
    canIdentify: Boolean,
    canAddToPlaylist: Boolean,
    canAddToCollection: Boolean,
    canInstantMix: Boolean,
    canStartRadio: Boolean,
    isOffline: Boolean,
    onClose: () -> Unit,
    onEditClick: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDownloadSeries: () -> Unit,
    onDeleteDownload: () -> Unit,
    onDeleteDownloadedSeries: () -> Unit,
    onHideFromNextUp: () -> Unit,
    onShowFromNextUp: () -> Unit,
    onHideFromContinueWatching: () -> Unit,
    onShowFromContinueWatching: () -> Unit,
    onHideDetailUpNext: () -> Unit = {},
    onShowDetailUpNext: () -> Unit = {},
    onManageSeries: () -> Unit,
    onRefreshMetadata: () -> Unit = {},
    onIdentify: () -> Unit = {},
    onTechnicalInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToCollection: () -> Unit = {},
    onStartInstantMix: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onStartWatchParty: () -> Unit = {},
    canStartWatchParty: Boolean = false,
    /** The item carries more than one version — offers the version picker. */
    canPickVersion: Boolean = false,
    onVersionPicker: () -> Unit = {},
    /** Admin gate for the version group/split entries (RequiresElevation server-side). */
    canMergeVersions: Boolean = false,
    onMergeVersions: () -> Unit = {},
    /** The item is version-merged (more than one media source) — offers Split. */
    canSplitVersions: Boolean = false,
    onSplitVersions: () -> Unit = {},
    /** Enqueues the item onto the player's manual up-next queue (head / tail). */
    onPlayNextInQueue: () -> Unit = {},
    onPlayLastInQueue: () -> Unit = {},
    /**
     * D14: the host-level half of the "Play (with pre-rolls)" gate (SyncPlay
     * + remote-origin resolution; the media-type/offline terms live in
     * [offersPlayWithPrerolls]).
     */
    canPlayWithPrerolls: Boolean = false,
    /** Plays the current item with server intros forced for this single play. */
    onPlayWithPrerolls: () -> Unit = {},
    /**
     * D12: the current series' new-episode notification opt-in (the menu
     * entry's checkmark state) and the write. Only rendered where
     * [platformSupportsNotifications] holds — desktop hides the entry.
     */
    seriesNotificationsEnabled: Boolean = false,
    platformSupportsNotifications: Boolean = false,
    onToggleSeriesNotifications: (enabled: Boolean) -> Unit = {},
): List<MediaOption> {
    // Download status / progress, resolved once for both the label and the
    // enabled flag so the two menus stay in lock-step.
    val downloadStatus = activeDownload?.status
    val isDownloadActive = downloadStatus == DownloadStatus.PENDING ||
        downloadStatus == DownloadStatus.DOWNLOADING ||
        downloadStatus == DownloadStatus.PAUSED
    val isDownloadCompleted = downloadStatus == DownloadStatus.COMPLETED
    val downloadProgress = if (activeDownload != null && activeDownload.totalSizeBytes > 0) {
        activeDownload.downloadedBytes.toFloat() / activeDownload.totalSizeBytes
    } else 0f

    val canDownload = item != null && detail != null && detail.mediaSources.isNotEmpty() &&
        (item.mediaType.isAudioType || (!isAudio && !isSeries))

    // Resolve localized labels in the @Composable body — the remember {} calculation lambda
    // below is not a composable scope, so stringResource cannot be called inside it.
    val labelEdit = stringResource(Res.string.detail_option_edit)
    val labelShare = stringResource(Res.string.detail_option_share)
    val labelDeleteDownload = stringResource(Res.string.detail_delete_download_title)
    val labelDownloadingPercent = stringResource(Res.string.detail_option_downloading_percent, (downloadProgress * 100).toInt())
    val labelDownloading = stringResource(Res.string.detail_option_downloading)
    val labelDownload = stringResource(Res.string.detail_option_download)
    val labelDownloadingSeries = stringResource(Res.string.detail_option_downloading_series)
    val labelDownloadSeries = stringResource(Res.string.detail_option_download_series)
    val labelHideFromNextUp = stringResource(Res.string.detail_option_hide_from_next_up)
    val labelHideFromContinueWatching = stringResource(Res.string.detail_option_hide_from_continue_watching)
    val labelShowInNextUp = stringResource(Res.string.detail_option_show_in_next_up)
    val labelShowInContinueWatching = stringResource(Res.string.detail_option_show_in_continue_watching)
    val labelHideDetailUpNext = stringResource(Res.string.detail_option_hide_detail_up_next)
    val labelShowDetailUpNext = stringResource(Res.string.detail_option_show_detail_up_next)
    val labelTechnicalInfo = stringResource(Res.string.detail_option_technical_info)
    val labelManageSeries = stringResource(Res.string.detail_option_manage_series)
    val labelRefreshMetadata = stringResource(Res.string.detail_option_refresh_metadata)
    val labelIdentify = stringResource(Res.string.detail_option_identify)
    val labelAddToPlaylist = stringResource(Res.string.detail_option_add_to_playlist)
    val labelAddToCollection = stringResource(Res.string.detail_option_add_to_collection)
    val labelDeleteDownloads = stringResource(Res.string.detail_option_delete_downloads)
    val labelInstantMix = stringResource(Res.string.detail_option_instant_mix)
    val labelStartRadio = stringResource(Res.string.detail_option_start_radio)
    val labelWatchParty = stringResource(Res.string.detail_option_watch_party)
    val labelVersion = stringResource(Res.string.detail_option_version)
    val labelMergeVersions = stringResource(Res.string.detail_option_merge_versions)
    val labelSplitVersions = stringResource(Res.string.detail_option_split_versions)
    val labelPlayNextInQueue = stringResource(Res.string.detail_option_play_next)
    val labelPlayLastInQueue = stringResource(Res.string.detail_option_play_last)
    val labelPlayWithPrerolls = stringResource(Res.string.detail_option_play_with_prerolls)
    val labelNotifyNewEpisodes = stringResource(Res.string.detail_option_notify_new_episodes)
    val labelStopNotifyingNewEpisodes = stringResource(Res.string.detail_option_stop_notify_new_episodes)

    return remember(item, detail, itemId, isAudio, isSeries, seasons, preferences.showShareMediaOption,
        preferences.nextUpExcludedSeriesIds, preferences.hiddenCwItemIds, preferences.showDetailUpNext,
        activeDownload, isDownloading, isDownloadingSeries, isDownloadActive, isDownloadCompleted,
        downloadStatus, downloadProgress, canManageSeries, canDeleteDownloadedSeries, canEditMetadata,
        canRefreshMetadata, canIdentify, canAddToPlaylist, canAddToCollection, canInstantMix, canStartRadio, canStartWatchParty, isOffline,
        canPickVersion, canMergeVersions, canSplitVersions, labelManageSeries,
        labelRefreshMetadata, labelIdentify, labelStartRadio, labelVersion, labelMergeVersions, labelSplitVersions,
        labelAddToPlaylist, labelDeleteDownloads, labelInstantMix, labelAddToCollection,
        labelHideDetailUpNext, labelShowDetailUpNext, labelWatchParty,
        labelPlayNextInQueue, labelPlayLastInQueue, labelPlayWithPrerolls,
        seriesNotificationsEnabled, platformSupportsNotifications,
        labelNotifyNewEpisodes, labelStopNotifyingNewEpisodes) {
        buildList {
            // Metadata editor is a remote-only action (feature matrix: local = No);
            // gated on remoteDiscovery so a local origin never offers a server
            // edit it cannot fulfill.
            if (canEditMetadata) {
                add(MediaOption("edit_metadata", labelEdit, Tabler.Outline.Pencil) {
                    onClose(); onEditClick()
                })
            }
            // Server-side metadata refresh (the jellyfin-web "Refresh metadata"
            // dialog): admin-only (the server 403s non-admins) and remote-only,
            // same origin rule as the editor. Sits directly under Edit so the
            // two metadata-maintenance entries stay grouped.
            if (canRefreshMetadata) {
                add(MediaOption("refresh_metadata", labelRefreshMetadata, Tabler.Outline.Refresh) {
                    onClose(); onRefreshMetadata()
                })
            }
            // Identify (jellyfin-web parity): search providers by name/year/id
            // and apply the real match. Same admin + remote gates as refresh.
            if (canIdentify) {
                add(MediaOption("identify", labelIdentify, Tabler.Outline.Search) {
                    onClose(); onIdentify()
                })
            }
            // Version group/split (jellyfin-web "Merge versions" / "Split
            // versions"): admin-gated server-side (RequiresElevation — the
            // caller resolves the gate off the same isAdmin seam the refresh/
            // identify entries use), remote-only. The pair stays grouped with
            // the other metadata-maintenance entries.
            if (canMergeVersions) {
                add(MediaOption("merge_versions", labelMergeVersions, Tabler.Outline.ArrowsJoin) {
                    onClose(); onMergeVersions()
                })
            }
            if (canSplitVersions) {
                add(MediaOption("split_versions", labelSplitVersions, Tabler.Outline.ArrowsSplit) {
                    onClose(); onSplitVersions()
                })
            }
            // Version picker: multi-version items only (the size gate lives
            // with the caller, which also knows the origin/capability mix).
            if (canPickVersion) {
                add(MediaOption("version_picker", labelVersion, Tabler.Outline.Stack2) {
                    onClose(); onVersionPicker()
                })
            }
            if (preferences.showShareMediaOption) {
                add(MediaOption("share", labelShare, Tabler.Outline.Share) {
                    onClose(); onShare()
                })
            }
            if (canDownload) {
                // A completed, on-disk download is deletable in place of the old
                // dead "Downloaded" indicator — restores the delete affordance the
                // standalone offline detail screen had. An in-progress download
                // stays a read-only status row (cancel via the download manager).
                when {
                    isDownloadCompleted -> add(
                        MediaOption("delete_download", labelDeleteDownload, Tabler.Outline.Trash) {
                            onClose(); onDeleteDownload()
                        }
                    )
                    isDownloading || isDownloadActive -> add(
                        MediaOption(
                            id = "download",
                            label = if (downloadProgress > 0f && downloadStatus == DownloadStatus.DOWNLOADING) {
                                labelDownloadingPercent
                            } else {
                                labelDownloading
                            },
                            icon = Tabler.Outline.Download,
                            enabled = false,
                        ) { onClose(); onDownload() }
                    )
                    else -> add(
                        MediaOption("download", labelDownload, Tabler.Outline.Download) {
                            onClose(); onDownload()
                        }
                    )
                }
            // Series download pulls media from the server, so it is offered only on a
            // remote origin (offline mode cannot start a new transfer).
            } else if (!isOffline && !isAudio && item != null && isSeries && seasons.isNotEmpty()) {
                add(
                    MediaOption(
                        id = "download_series",
                        label = if (isDownloadingSeries) labelDownloadingSeries else labelDownloadSeries,
                        icon = Tabler.Outline.Download,
                        enabled = !isDownloadingSeries,
                    ) {
                        onClose(); onDownloadSeries()
                    }
                )
            }
            // Series batch-delete: a local-origin series that actually has
            // downloaded episodes. Opens the multi-select sheet that drives
            // DetailViewModel.deleteOfflineEpisodes / deleteOfflineSeries.
            if (canDeleteDownloadedSeries) {
                add(MediaOption("delete_downloads", labelDeleteDownloads, Tabler.Outline.Trash) {
                    onClose(); onDeleteDownloadedSeries()
                })
            }
            // Add to Playlist: only for playable video items and series (a
            // series expands to its episodes in the VM). Audio/album detail
            // already has its own playlist flow in feature/music, so it is
            // excluded here to avoid a duplicate entry path. Remote-only — a
            // local origin has no server playlist target.
            if (canAddToPlaylist && item != null && (item.mediaType.isVideoType || item.mediaType == MediaType.SERIES)) {
                add(MediaOption("add_to_playlist", labelAddToPlaylist, Tabler.Outline.Playlist) {
                    onClose(); onAddToPlaylist()
                })
            }
            // Add to Collection: mirrors the Add-to-Playlist gate (playable
            // video items + series, remote-only) but assembles a Jellyfin
            // collection (BoxSet) instead. A series expands to its episode ids
            // in the VM. Sits directly under Add to Playlist so the two
            // assemble-into actions stay grouped.
            if (canAddToCollection && item != null && (item.mediaType.isVideoType || item.mediaType == MediaType.SERIES)) {
                add(MediaOption("add_to_collection", labelAddToCollection, Tabler.Outline.Stack2) {
                    onClose(); onAddToCollection()
                })
            }
            // Play next / Play last: enqueue onto the player's manual up-next
            // queue (session-scoped; the player drains it queue-first). Video
            // items only — a series has nothing playable of its own, audio has
            // the audio queue.
            if (item != null && item.mediaType.isVideoType) {
                add(MediaOption("play_next_in_queue", labelPlayNextInQueue, Tabler.Outline.PlayerTrackNext) {
                    onClose(); onPlayNextInQueue()
                })
                add(MediaOption("play_last_in_queue", labelPlayLastInQueue, Tabler.Outline.PlayerTrackPrev) {
                    onClose(); onPlayLastInQueue()
                })
            }
            // Play (with pre-rolls) — D14: the item plays with server intros
            // FORCED for this single play (bypasses the global cinema-mode
            // pref; the pipeline's remaining vetoes still apply). Suppressed
            // for SERIES (intros are episode-scoped), offline (intros are a
            // server read) and during SyncPlay (the host-level half rides
            // [canPlayWithPrerolls]).
            if (offersPlayWithPrerolls(
                    mediaType = item?.mediaType,
                    isOffline = isOffline,
                    canPlay = canPlayWithPrerolls,
                )
            ) {
                add(MediaOption("play_with_prerolls", labelPlayWithPrerolls, Tabler.Outline.Movie) {
                    onClose(); onPlayWithPrerolls()
                })
            }
            // Instant Mix: audio-only. Builds a shuffled track queue seeded from
            // the current item via Jellyfin's instant-mix endpoint. The gate
            // (audio type + remoteDiscovery) is resolved in DetailContent so the
            // menu entry only renders for a remote audio item.
            if (canInstantMix) {
                add(MediaOption("instant_mix", labelInstantMix, Tabler.Outline.WaveSine) {
                    onClose(); onStartInstantMix()
                })
            }
            // Start Radio: audio-only, next to Instant Mix — an instant-mix
            // seed that keeps refilling itself as the queue drains until the
            // user stops it from the player's queue sheet.
            if (canStartRadio) {
                add(MediaOption("start_radio", labelStartRadio, Tabler.Outline.Radio) {
                    onClose(); onStartRadio()
                })
            }
            // Watch Party: bootstraps a SyncPlay group for the current item and
            // opens the player. Requires a working server + connectivity, gated
            // on capabilities.remoteWorkAllowed (resolved in DetailContent). No
            // invite link / share / deep-link — the player auto-detects the
            // active session once the group is joined + the queue is seeded.
            if (canStartWatchParty) {
                add(MediaOption("watch_party", labelWatchParty, Tabler.Outline.Users) {
                    onClose(); onStartWatchParty()
                })
            }
            if (isSeries || item?.seriesId != null) {
                // Next Up exclusion is keyed by series id; an episode resolves
                // to its parent series, a series to itself.
                val seriesId = item?.seriesId ?: item?.id
                val isHiddenFromNextUp =
                    seriesId != null && seriesId in preferences.nextUpExcludedSeriesIds
                add(
                    MediaOption(
                        id = "next_up_visibility",
                        label = if (isHiddenFromNextUp) labelShowInNextUp else labelHideFromNextUp,
                        icon = if (isHiddenFromNextUp) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                    ) {
                        onClose()
                        if (isHiddenFromNextUp) onShowFromNextUp() else onHideFromNextUp()
                    }
                )
            }
            // D12: "Notify me about new episodes" — the checkmark mirrors the
            // per-series opt-in (same label/icon state flip as the visibility
            // entries above; the menu carries no checkbox renderer). The gate
            // (SERIES-only, server-only, notification-stack platforms only)
            // lives in offersSeriesNewEpisodeNotifications.
            if (offersSeriesNewEpisodeNotifications(
                    mediaType = item?.mediaType,
                    isOffline = isOffline,
                    platformSupported = platformSupportsNotifications,
                )
            ) {
                add(
                    MediaOption(
                        id = "notify_new_episodes",
                        label = if (seriesNotificationsEnabled) {
                            labelStopNotifyingNewEpisodes
                        } else {
                            labelNotifyNewEpisodes
                        },
                        icon = if (seriesNotificationsEnabled) Tabler.Outline.BellOff else Tabler.Outline.Bell,
                    ) {
                        onClose()
                        onToggleSeriesNotifications(!seriesNotificationsEnabled)
                    }
                )
            }
            if (isSeries) {
                val isDetailUpNextHidden = !preferences.showDetailUpNext
                add(
                    MediaOption(
                        id = "detail_up_next_visibility",
                        label = if (isDetailUpNextHidden) labelShowDetailUpNext else labelHideDetailUpNext,
                        icon = if (isDetailUpNextHidden) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                    ) {
                        onClose()
                        if (isDetailUpNextHidden) onShowDetailUpNext() else onHideDetailUpNext()
                    }
                )
            }
            val isHiddenFromCw = item?.id in preferences.hiddenCwItemIds
            add(
                MediaOption(
                    id = "continue_watching_visibility",
                    label = if (isHiddenFromCw) labelShowInContinueWatching else labelHideFromContinueWatching,
                    icon = if (isHiddenFromCw) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                ) {
                    onClose()
                    if (isHiddenFromCw) onShowFromContinueWatching() else onHideFromContinueWatching()
                }
            )
            if (canManageSeries) {
                add(MediaOption("manage_series", labelManageSeries, Tabler.Outline.ListDetails) {
                    onClose(); onManageSeries()
                })
            }
            add(MediaOption("technical_info", labelTechnicalInfo, Tabler.Outline.InfoCircle) {
                onClose(); onTechnicalInfo()
            })
        }
    }
}
