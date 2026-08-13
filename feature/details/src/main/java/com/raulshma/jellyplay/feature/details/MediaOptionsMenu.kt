package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.ListDetails
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.Stack2
import com.composables.icons.tabler.outline.Trash
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
import com.raulshma.jellyplay.feature.details.R

/**
 * A single resolved options-menu entry, shared by the touch [DropdownMenu]
 * and the TV [TvSafeSheet] so the two menus can never drift apart.
 */
internal data class MediaOption(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

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
    canAddToPlaylist: Boolean,
    canAddToCollection: Boolean,
    canInstantMix: Boolean,
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
    onTechnicalInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToCollection: () -> Unit = {},
    onStartInstantMix: () -> Unit = {},
    onStartWatchParty: () -> Unit = {},
    canStartWatchParty: Boolean = false,
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
    val labelEdit = stringResource(R.string.detail_option_edit)
    val labelShare = stringResource(R.string.detail_option_share)
    val labelDeleteDownload = stringResource(R.string.detail_delete_download_title)
    val labelDownloadingPercent = stringResource(R.string.detail_option_downloading_percent, (downloadProgress * 100).toInt())
    val labelDownloading = stringResource(R.string.detail_option_downloading)
    val labelDownload = stringResource(R.string.detail_option_download)
    val labelDownloadingSeries = stringResource(R.string.detail_option_downloading_series)
    val labelDownloadSeries = stringResource(R.string.detail_option_download_series)
    val labelHideFromNextUp = stringResource(R.string.detail_option_hide_from_next_up)
    val labelHideFromContinueWatching = stringResource(R.string.detail_option_hide_from_continue_watching)
    val labelShowInNextUp = stringResource(R.string.detail_option_show_in_next_up)
    val labelShowInContinueWatching = stringResource(R.string.detail_option_show_in_continue_watching)
    val labelHideDetailUpNext = stringResource(R.string.detail_option_hide_detail_up_next)
    val labelShowDetailUpNext = stringResource(R.string.detail_option_show_detail_up_next)
    val labelTechnicalInfo = stringResource(R.string.detail_option_technical_info)
    val labelManageSeries = stringResource(R.string.detail_option_manage_series)
    val labelAddToPlaylist = stringResource(R.string.detail_option_add_to_playlist)
    val labelAddToCollection = stringResource(R.string.detail_option_add_to_collection)
    val labelDeleteDownloads = stringResource(R.string.detail_option_delete_downloads)
    val labelInstantMix = stringResource(R.string.detail_option_instant_mix)
    val labelWatchParty = stringResource(R.string.detail_option_watch_party)

    return remember(item, detail, itemId, isAudio, isSeries, seasons, preferences.showShareMediaOption,
        preferences.nextUpExcludedSeriesIds, preferences.hiddenCwItemIds, preferences.showDetailUpNext,
        activeDownload, isDownloading, isDownloadingSeries, isDownloadActive, isDownloadCompleted,
        downloadStatus, downloadProgress, canManageSeries, canDeleteDownloadedSeries, canEditMetadata,
        canAddToPlaylist, canAddToCollection, canInstantMix, canStartWatchParty, isOffline, labelManageSeries,
        labelAddToPlaylist, labelDeleteDownloads, labelInstantMix, labelAddToCollection,
        labelHideDetailUpNext, labelShowDetailUpNext, labelWatchParty) {
        buildList {
            // Metadata editor is a remote-only action (feature matrix: local = No);
            // gated on remoteDiscovery so a local origin never offers a server
            // edit it cannot fulfill.
            if (canEditMetadata) {
                add(MediaOption(labelEdit, Tabler.Outline.Pencil) {
                    onClose(); onEditClick()
                })
            }
            if (preferences.showShareMediaOption) {
                add(MediaOption(labelShare, Tabler.Outline.Share) {
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
                        MediaOption(labelDeleteDownload, Tabler.Outline.Trash) {
                            onClose(); onDeleteDownload()
                        }
                    )
                    isDownloading || isDownloadActive -> add(
                        MediaOption(
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
                        MediaOption(labelDownload, Tabler.Outline.Download) {
                            onClose(); onDownload()
                        }
                    )
                }
            // Series download pulls media from the server, so it is offered only on a
            // remote origin (offline mode cannot start a new transfer).
            } else if (!isOffline && !isAudio && item != null && isSeries && seasons.isNotEmpty()) {
                add(
                    MediaOption(
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
                add(MediaOption(labelDeleteDownloads, Tabler.Outline.Trash) {
                    onClose(); onDeleteDownloadedSeries()
                })
            }
            // Add to Playlist: only for playable video items and series (a
            // series expands to its episodes in the VM). Audio/album detail
            // already has its own playlist flow in feature/music, so it is
            // excluded here to avoid a duplicate entry path. Remote-only — a
            // local origin has no server playlist target.
            if (canAddToPlaylist && item != null && (item.mediaType.isVideoType || item.mediaType == MediaType.SERIES)) {
                add(MediaOption(labelAddToPlaylist, Tabler.Outline.Playlist) {
                    onClose(); onAddToPlaylist()
                })
            }
            // Add to Collection: mirrors the Add-to-Playlist gate (playable
            // video items + series, remote-only) but assembles a Jellyfin
            // collection (BoxSet) instead. A series expands to its episode ids
            // in the VM. Sits directly under Add to Playlist so the two
            // assemble-into actions stay grouped.
            if (canAddToCollection && item != null && (item.mediaType.isVideoType || item.mediaType == MediaType.SERIES)) {
                add(MediaOption(labelAddToCollection, Tabler.Outline.Stack2) {
                    onClose(); onAddToCollection()
                })
            }
            // Instant Mix: audio-only. Builds a shuffled track queue seeded from
            // the current item via Jellyfin's instant-mix endpoint. The gate
            // (audio type + remoteDiscovery) is resolved in DetailContent so the
            // menu entry only renders for a remote audio item.
            if (canInstantMix) {
                add(MediaOption(labelInstantMix, Tabler.Outline.WaveSine) {
                    onClose(); onStartInstantMix()
                })
            }
            // Watch Party: bootstraps a SyncPlay group for the current item and
            // opens the player. Requires a working server + connectivity, gated
            // on capabilities.remoteWorkAllowed (resolved in DetailContent). No
            // invite link / share / deep-link — the player auto-detects the
            // active session once the group is joined + the queue is seeded.
            if (canStartWatchParty) {
                add(MediaOption(labelWatchParty, Tabler.Outline.Users) {
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
                        label = if (isHiddenFromNextUp) labelShowInNextUp else labelHideFromNextUp,
                        icon = if (isHiddenFromNextUp) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                    ) {
                        onClose()
                        if (isHiddenFromNextUp) onShowFromNextUp() else onHideFromNextUp()
                    }
                )
            }
            if (isSeries) {
                val isDetailUpNextHidden = !preferences.showDetailUpNext
                add(
                    MediaOption(
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
                    label = if (isHiddenFromCw) labelShowInContinueWatching else labelHideFromContinueWatching,
                    icon = if (isHiddenFromCw) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                ) {
                    onClose()
                    if (isHiddenFromCw) onShowFromContinueWatching() else onHideFromContinueWatching()
                }
            )
            if (canManageSeries) {
                add(MediaOption(labelManageSeries, Tabler.Outline.ListDetails) {
                    onClose(); onManageSeries()
                })
            }
            add(MediaOption(labelTechnicalInfo, Tabler.Outline.InfoCircle) {
                onClose(); onTechnicalInfo()
            })
        }
    }
}
