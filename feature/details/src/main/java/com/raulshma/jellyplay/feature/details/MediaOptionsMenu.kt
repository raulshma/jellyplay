package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Share
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.UserPreferences

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
    preferences: UserPreferences,
    activeDownload: DownloadItem?,
    isDownloading: Boolean,
    isDownloadingSeries: Boolean,
    onClose: () -> Unit,
    onEditClick: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDownloadSeries: () -> Unit,
    onHideFromNextUp: () -> Unit,
    onHideFromContinueWatching: () -> Unit,
    onTechnicalInfo: () -> Unit,
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
        (item.mediaType == MediaType.AUDIO || item.mediaType == MediaType.MUSIC || (!isAudio && !isSeries))

    return remember(item, detail, itemId, isAudio, isSeries, seasons, preferences.showShareMediaOption,
        activeDownload, isDownloading, isDownloadingSeries, isDownloadActive, isDownloadCompleted,
        downloadStatus, downloadProgress) {
        buildList {
            add(MediaOption("Edit", Tabler.Outline.Pencil) {
                onClose(); onEditClick()
            })
            if (preferences.showShareMediaOption) {
                add(MediaOption("Share", Tabler.Outline.Share) {
                    onClose(); onShare()
                })
            }
            if (canDownload) {
                val label = when {
                    isDownloadCompleted -> "Downloaded"
                    isDownloading || isDownloadActive -> {
                        if (downloadProgress > 0f && downloadStatus == DownloadStatus.DOWNLOADING) {
                            "Downloading (${(downloadProgress * 100).toInt()}%)"
                        } else {
                            "Downloading..."
                        }
                    }
                    else -> "Download"
                }
                add(
                    MediaOption(
                        label = label,
                        icon = if (isDownloadCompleted) Tabler.Outline.Check else Tabler.Outline.Download,
                        enabled = !isDownloading && !isDownloadActive && !isDownloadCompleted,
                    ) {
                        onClose(); onDownload()
                    }
                )
            } else if (!isAudio && item != null && isSeries && seasons.isNotEmpty()) {
                add(
                    MediaOption(
                        label = if (isDownloadingSeries) "Downloading Series..." else "Download Series",
                        icon = Tabler.Outline.Download,
                        enabled = !isDownloadingSeries,
                    ) {
                        onClose(); onDownloadSeries()
                    }
                )
            }
            if (isSeries || item?.seriesId != null) {
                add(MediaOption("Hide from Next Up", Tabler.Outline.EyeOff) {
                    onClose(); onHideFromNextUp()
                })
            }
            add(MediaOption("Hide from Continue Watching", Tabler.Outline.EyeOff) {
                onClose(); onHideFromContinueWatching()
            })
            add(MediaOption("Technical Info", Tabler.Outline.InfoCircle) {
                onClose(); onTechnicalInfo()
            })
        }
    }
}
