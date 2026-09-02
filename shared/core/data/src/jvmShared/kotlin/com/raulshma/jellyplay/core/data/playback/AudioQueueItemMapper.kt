package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaylistItem

/**
 * Queue-item mapping is facade-internal (plan 04 step 7): after the
 * AudioQueueFacade migration no code outside core/data builds queue items,
 * and the `internal` visibility keeps it that way.
 */
internal fun MediaItem.toAudioQueueItem(
    imageUrl: String?,
    albumFallback: String? = null,
): AudioQueueItem = AudioQueueItem(
    id = id,
    name = name,
    artist = albumArtist ?: artistItems.firstOrNull()?.name ?: "",
    album = album ?: albumFallback,
    imageUrl = imageUrl,
    mediaSourceId = null,
    durationMs = runTimeTicks?.let { it / 10_000 } ?: 0L,
    normalizationGain = normalizationGain,
)

internal fun PlaylistItem.toAudioQueueItem(): AudioQueueItem = AudioQueueItem(
    id = id,
    name = name,
    artist = artist ?: "",
    album = album,
    imageUrl = null,
    mediaSourceId = null,
    durationMs = runTimeTicks?.let { it / 10_000 } ?: 0L,
)
