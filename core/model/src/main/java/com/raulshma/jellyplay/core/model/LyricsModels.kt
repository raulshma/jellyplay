package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LyricsLine(
    val timeMs: Long,
    val text: String,
)

@Serializable
data class LyricsResult(
    val lines: List<LyricsLine>,
    val source: LyricsSource = LyricsSource.UNKNOWN,
)

@Serializable
enum class LyricsSource {
    EMBEDDED,
    LRC_FILE,
    EXTERNAL,
    UNKNOWN,
}
