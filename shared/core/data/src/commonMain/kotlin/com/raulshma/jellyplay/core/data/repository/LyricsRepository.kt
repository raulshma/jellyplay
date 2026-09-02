package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsResult

/**
 * Lyrics concern of the media domain. Segregated from [MediaRepository]
 * so consumers that only fetch/search lyrics — e.g. `AudioLyricsManager` — depend
 * on this narrow contract instead of the full media God-interface. [MediaRepository]
 * extends this interface, therefore `MediaRepositoryImpl` satisfies it and the
 * Koin alias single in dataJvmModule provides the same singleton instance.
 */
interface LyricsRepository {

    suspend fun getLyrics(itemId: String): Result<LyricsResult>

    suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult>

    suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>>

    suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult>

    suspend fun cleanupLyricsCache()
}
