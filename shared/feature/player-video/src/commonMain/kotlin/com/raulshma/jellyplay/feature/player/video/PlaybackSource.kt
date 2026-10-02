package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.OfflinePlaybackPreference
import java.io.File

/**
 * Classifies the origin of media handed to [PlayerSessionManager.loadMedia].
 *
 * - [Auto] preserves the historical auto-detection behaviour: the session
 *   manager probes the downloads DB and prefers a completed local file,
 *   falling back to the server when no usable download exists. This is what
 *   every call-site uses today.
 * - [Offline] forces local-file playback and short-circuits all server
 *   calls. Callers should have already verified the download is usable.
 * - [Online] forces server playback even when a local download happens to
 *   exist (e.g. a future "stream instead of play offline" action).
 *
 * The type is sealed so the compiler guarantees exhaustive dispatch in
 * [PlayerSessionManager.loadMedia]; new variants are a one-line addition.
 */
sealed interface PlaybackSource {
    val itemId: String

    /** Auto-detect: resolve to [Offline] when a completed download exists, else [Online]. */
    data class Auto(
        override val itemId: String,
        val mediaSourceId: String?,
    ) : PlaybackSource

    /**
     * Play the local file at [downloadPath]. The session manager still looks
     * up the matching [DownloadItem] by [itemId] for metadata (title, type)
     * and sidecar subtitles, but trusts [downloadPath] as the media source.
     */
    data class Offline(
        override val itemId: String,
        val downloadPath: String,
    ) : PlaybackSource

    /** Stream from the server, ignoring any local download. */
    data class Online(
        override val itemId: String,
        val mediaSourceId: String?,
    ) : PlaybackSource
}

/**
 * Resolves [PlaybackSource.Auto] into a concrete online/offline source by
 * consulting the download store. Returns [PlaybackSource.Offline] only when
 * the download is [DownloadStatus.COMPLETED] and the underlying file still
 * exists on disk; otherwise returns [PlaybackSource.Online].
 *
 * [offlinePlaybackPreference] inverts the default for a usable download:
 * `PREFER_STREAMING` plays the server copy instead — but only while
 * [online]; with no reachable server the stored file is the only thing that
 * can play, so the offline resolution is unchanged there.
 *
 * Extracted as a top-level function so it is trivially unit-testable without
 * instantiating the full session manager.
 */
internal fun PlaybackSource.Auto.resolve(
    download: DownloadItem?,
    online: Boolean = true,
    offlinePlaybackPreference: OfflinePlaybackPreference = OfflinePlaybackPreference.PREFER_DOWNLOADED,
): PlaybackSource {
    val file = download?.let { File(it.downloadPath).takeIf { f -> f.exists() } }
    return if (download != null && file != null && download.status == DownloadStatus.COMPLETED) {
        if (online && offlinePlaybackPreference == OfflinePlaybackPreference.PREFER_STREAMING) {
            PlaybackSource.Online(itemId, mediaSourceId)
        } else {
            PlaybackSource.Offline(itemId, download.downloadPath)
        }
    } else {
        PlaybackSource.Online(itemId, mediaSourceId)
    }
}
