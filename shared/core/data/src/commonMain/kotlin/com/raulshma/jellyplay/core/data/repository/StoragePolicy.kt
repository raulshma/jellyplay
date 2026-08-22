package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import kotlinx.coroutines.flow.first

/**
 * Owns the download storage-cap policy: the user-facing MB cap
 * ([UserPreferencesStore.maxCacheSizeMb]) and the GB storage ceiling
 * ([UserPreferencesStore.maxDownloadStorageGb]). `0` means "unlimited" for each.
 *
 * Extracted out of `DownloadRepositoryImpl`, where the rule was written twice
 * (once in `startDownload`, once in `downloadSeries`) and could drift between
 * them. Both call sites now route through [enforce], so the cap is evaluated
 * from a single source of truth.
 *
 * The check is pre-flight: it compares the *current* downloaded bytes against
 * the cap before a download is enqueued. No bytes are transferred here.
 */
class StoragePolicy(
    private val networkOfflineStore: NetworkOfflineStore,
    private val downloadsStore: DownloadsStore,
    private val currentBytesProvider: suspend () -> Long,
) {

    /**
     * Reads the cap from preferences, fetches the current downloaded bytes once,
     * and throws [IllegalStateException] if either ceiling is reached.
     *
     * Returns the current-bytes value it checked, so a batch caller can hand it
     * to each per-episode start as a `precomputedCurrentBytes` hint and avoid
     * re-running the SUM aggregate N times.
     *
     * @param precomputedCurrentBytes if non-null, skips the [currentBytesProvider]
     *   call (used by the single-item path which already has the value).
     */
    suspend fun enforce(precomputedCurrentBytes: Long? = null): Long {
        val net = networkOfflineStore.networkOffline.first()
        val dl = downloadsStore.downloads.first()
        val maxBytesMb = net.maxCacheSizeMb.toLong() * 1024L * 1024L
        val maxBytesGb = dl.maxDownloadStorageGb.toLong() * 1024L * 1024L * 1024L
        if (maxBytesMb <= 0L && maxBytesGb <= 0L) return -1L

        val currentBytes = precomputedCurrentBytes ?: currentBytesProvider()
        if (maxBytesMb > 0L && currentBytes >= maxBytesMb) {
            throw IllegalStateException(
                "Download limit reached (${net.maxCacheSizeMb} MB). Free up space in Settings › Storage or increase the limit.",
            )
        }
        if (maxBytesGb > 0L && currentBytes >= maxBytesGb) {
            throw IllegalStateException(
                "Download storage limit reached (${dl.maxDownloadStorageGb} GB). Free up space in Settings › Storage or increase the limit.",
            )
        }
        return currentBytes
    }
}
