package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LyricsWord
import com.raulshma.jellyplay.core.network.api.JellyfinApiEngine
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsApi @Inject constructor(
    private val engine: JellyfinApiEngine,
) {

    suspend fun fetchLyrics(itemId: String): LyricsResult {
        val response = runCatching { engine.requireApi().lyricsApi.getLyrics(itemId.toUUID()).content }
        return response.fold(
            onSuccess = { dto -> dto.toLyricsResult() },
            onFailure = { LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN) },
        )
    }

    private fun LyricDto.toLyricsResult(): LyricsResult {
        val lines = lyrics.mapIndexedNotNull { idx, line ->
            val startMs = line.start?.let { it / 10_000 } ?: 0L
            val nextStartMs = if (idx + 1 < lyrics.size) {
                lyrics[idx + 1].start?.div(10_000) ?: startMs
            } else startMs
            val text = line.text
            val words = line.cues?.map { cue ->
                LyricsWord(
                    timeMs = cue.start / 10_000,
                    text = text.substring(cue.position, cue.endPosition.coerceAtMost(text.length)),
                    durationMs = ((cue.end ?: cue.start) - cue.start) / 10_000,
                )
            }.orEmpty()
            LyricsLine(
                timeMs = startMs,
                text = text,
                durationMs = (nextStartMs - startMs).coerceAtLeast(0L),
                words = words,
            )
        }
        val source = if (lines.isEmpty()) LyricsSource.UNKNOWN else LyricsSource.EXTERNAL
        return LyricsResult(lines = lines, source = source)
    }
}
