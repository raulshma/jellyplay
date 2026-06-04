package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val durationMs: Long = 0L,
    val words: List<LyricsWord> = emptyList(),
)

@Immutable
@Serializable
data class LyricsWord(
    val timeMs: Long,
    val text: String,
    val durationMs: Long = 0L,
)

@Immutable
@Serializable
data class LyricsResult(
    val lines: List<LyricsLine>,
    val source: LyricsSource = LyricsSource.UNKNOWN,
)

@Immutable
@Serializable
enum class LyricsSource {
    EMBEDDED,
    LRC_FILE,
    EXTERNAL,
    LRCLIB,
    UNKNOWN,
}

@Immutable
@Serializable
data class LrcLibTrack(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String = "",
    val duration: Double = 0.0,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
) {
    val hasSyncedLyrics: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasPlainLyrics: Boolean get() = !plainLyrics.isNullOrBlank()
}

@Immutable
@Serializable
data class LyricsCacheEntry(
    val itemId: String,
    val provider: LyricsSource,
    val artistName: String? = null,
    val trackName: String? = null,
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val duration: Double? = null,
    val lrcLibId: Long? = null,
    val fetchedAt: Long,
)
