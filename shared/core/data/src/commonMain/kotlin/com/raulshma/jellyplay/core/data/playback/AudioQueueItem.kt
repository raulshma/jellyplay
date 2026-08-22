package com.raulshma.jellyplay.core.data.playback

import androidx.compose.runtime.Immutable

/**
 * A single row of the audio play queue. Extracted verbatim from the legacy
 * `AudioPlaybackManager.kt` (C4 part 2) so the queue interface
 * [AudioQueueManager] and its persistence helpers can live in commonMain
 * while the media3-coupled manager implementation stays Android-side.
 */
@Immutable
data class AudioQueueItem(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val imageUrl: String?,
    val mediaSourceId: String?,
    val durationMs: Long = 0L,
    val normalizationGain: Float? = null,
)
