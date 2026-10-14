package com.raulshma.jellyplay.core.data.repository

/**
 * Live per-row transfer progress for an in-flight download — the feature-
 * facing shape of the DAO's `DownloadProgressRow` projection (features never
 * import DAO types; the impl maps at the repository boundary). Rows here are
 * by definition in flight (PENDING/QUEUED/DOWNLOADING); their structural
 * status is read from [getAllDownloads][com.raulshma.jellyplay.core.data.repository.DownloadRepository.getAllDownloads],
 * which re-emits on every status transition.
 *
 * promotion from jvmShared: the type is pure data (no platform surface), so
 * it crosses verbatim — the promoted
 * [DownloadQueue][com.raulshma.jellyplay.core.data.download.DownloadQueue]
 * seam names it, and the field-identical feature-local mirror
 * (`DownloadRowProgress`) died with that promotion.
 */
data class DownloadProgress(
    val id: String,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    /**
     * The downloaded item id (the `downloads.mediaItemId` join column, D16) —
     * lets item-keyed surfaces (the detail screen's episode rows) map live
     * progress onto their rows without a second query. Null only from tests
     * constructing the bare byte/speed triple.
     */
    val mediaItemId: String? = null,
    /**
     * The row's persisted total size — the denominator of the 0..1 fraction
     * the download-progress UI derives (`downloadedBytes / totalSizeBytes`,
     * the same math as the ⋮ menu's "Downloading N%" label). 0 = unknown
     * (a PENDING row before its size probe) → no fraction yet.
     */
    val totalSizeBytes: Long = 0,
) {
    /**
     * The 0..1 transfer fraction, or null while the total size is unknown.
     * Shared by every live-percent surface so the rounding/clamping cannot
     * drift between the ⋮ menu and the episode-row bar.
     */
    val progressFraction: Float?
        get() = if (totalSizeBytes > 0) {
            (downloadedBytes.toFloat() / totalSizeBytes).coerceIn(0f, 1f)
        } else {
            null
        }
}
