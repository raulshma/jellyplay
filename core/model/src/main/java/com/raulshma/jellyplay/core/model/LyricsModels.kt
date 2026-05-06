package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LyricsLine(
    val timeMs: Long,
    val text: String,
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
    UNKNOWN,
}
