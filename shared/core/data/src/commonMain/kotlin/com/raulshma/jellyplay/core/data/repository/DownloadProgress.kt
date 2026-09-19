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
)
