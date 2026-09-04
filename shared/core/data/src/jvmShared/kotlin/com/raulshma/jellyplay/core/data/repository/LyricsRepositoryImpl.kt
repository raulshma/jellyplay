package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LyricsApi
import com.raulshma.jellyplay.core.network.LrcLibApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The lyrics engine: the LRC/LRCLIB fetch-parse-cache chain behind
 * [LyricsRepository]. Extracted from [MediaRepositoryImpl] (where it squatted
 * with two private constructor deps) so the media-catalogue module owns
 * catalogue concerns and the lyrics concern owns its own home and tests.
 *
 * Fetch choreography (verbatim from its former inline home): cache read →
 * Jellyfin lyrics endpoint → LRCLIB best-match (skipped on Local network) →
 * negative-result caching so a known-no-lyrics item doesn't re-query.
 */
class LyricsRepositoryImpl(
    private val apiClient: JellyfinApiClient,
    private val lrcLibApi: LrcLibApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val networkMonitor: NetworkMonitor,
) : LyricsRepository {

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = apiClient.getLyrics(itemId)

    override suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult> = runCatching {
        val cached = lyricsCacheDao.getByItemId(itemId)
        if (cached != null) {
            val cachedSynced = cached.syncedLyrics
            val cachedPlain = cached.plainLyrics
            if (!cachedSynced.isNullOrBlank()) {
                val lines = parseLrcOffMain(cachedSynced)
                if (lines.isNotEmpty()) {
                    return@runCatching LyricsResult(lines = lines, source = cachedSource(cached.provider))
                }
            }
            if (!cachedPlain.isNullOrBlank() && cachedSynced.isNullOrBlank()) {
                return@runCatching LyricsResult(lines = plainLines(cachedPlain), source = cachedSource(cached.provider))
            }
            if (cachedSynced == null && cachedPlain == null && cached.artistName != null) {
                return@runCatching noLyrics
            }
        }

        val jellyfinResult = apiClient.getLyrics(itemId)
        if (jellyfinResult.isSuccess) {
            val result = jellyfinResult.getOrThrow()
            if (result.lines.isNotEmpty()) {
                cacheLyrics(itemId, result.source, artistName, trackName, duration, result.lines)
                return@runCatching result
            }
        }

        if (artistName.isNullOrBlank() || trackName.isNullOrBlank()) {
            return@runCatching noLyrics
        }

        val isLocal = networkMonitor.networkStatus.value == NetworkStatus.Local
        if (!isLocal) {
            val lrcLibResult = lrcLibApi.getBestMatch(artistName, trackName, duration)
            if (lrcLibResult.isSuccess) {
                val track = lrcLibResult.getOrThrow()
                val trackSynced = track.syncedLyrics
                val trackPlain = track.plainLyrics
                if (track.instrumental) {
                    lyricsCacheDao.upsert(
                        cacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB,
                            artistName = artistName,
                            trackName = trackName,
                            duration = duration,
                            lrcLibId = track.id,
                        )
                    )
                    return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.LRCLIB)
                }
                if (!trackSynced.isNullOrBlank()) {
                    val lines = parseLrcOffMain(trackSynced)
                    if (lines.isNotEmpty()) {
                        lyricsCacheDao.upsert(
                            cacheEntity(
                                itemId = itemId,
                                provider = LyricsSource.LRCLIB,
                                artistName = artistName,
                                trackName = trackName,
                                syncedLyrics = trackSynced,
                                plainLyrics = trackPlain,
                                duration = duration,
                                lrcLibId = track.id,
                            )
                        )
                        return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                    }
                }
                if (!trackPlain.isNullOrBlank()) {
                    val lines = plainLines(trackPlain)
                    lyricsCacheDao.upsert(
                        cacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB,
                            artistName = artistName,
                            trackName = trackName,
                            plainLyrics = trackPlain,
                            duration = duration,
                            lrcLibId = track.id,
                        )
                    )
                    return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                }
            }
        }

        lyricsCacheDao.upsert(
            cacheEntity(
                itemId = itemId,
                provider = LyricsSource.UNKNOWN,
                artistName = artistName,
                trackName = trackName,
                duration = duration,
            )
        )
        noLyrics
    }

    override suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>> =
        lrcLibApi.search(query)

    override suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult> =
        lrcLibApi.getById(lrcLibId).mapCatching { track ->
            val trackSynced = track.syncedLyrics
            val trackPlain = track.plainLyrics
            val lines = if (!trackSynced.isNullOrBlank()) {
                parseLrcOffMain(trackSynced)
            } else if (!trackPlain.isNullOrBlank()) {
                plainLines(trackPlain)
            } else {
                emptyList()
            }
            lyricsCacheDao.upsert(
                cacheEntity(
                    itemId = itemId,
                    provider = LyricsSource.LRCLIB,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    lrcLibId = track.id,
                )
            )
            LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
        }

    private suspend fun cacheLyrics(
        itemId: String,
        source: LyricsSource,
        artistName: String?,
        trackName: String?,
        duration: Double?,
        lines: List<LyricsLine>,
    ) {
        val syncedLrc = buildString {
            val formatter = java.util.Formatter(this)
            lines.forEachIndexed { index, line ->
                if (index > 0) append('\n')
                val min = line.timeMs / 60_000
                val sec = (line.timeMs % 60_000) / 1000.0
                formatter.format("[%02d:%06.3f] %s", min, sec, line.text)
            }
        }
        lyricsCacheDao.upsert(
            cacheEntity(
                itemId = itemId,
                provider = source,
                artistName = artistName,
                trackName = trackName,
                syncedLyrics = syncedLrc,
                duration = duration,
            )
        )
        // Throttle eviction to at most once per hour. deleteOlderThan is a full
        // table scan; firing it on every lyrics fetch (which happens whenever a
        // user opens lyrics for a new track) was walking & re-locking the whole
        // lyrics_cache table unnecessarily. Eviction semantics (rows older than
        // 30 days eventually removed) preserved.
        val now = System.currentTimeMillis()
        if (now - lastLyricsEvictionMs > 60L * 60 * 1000) {
            lastLyricsEvictionMs = now
            try {
                lyricsCacheDao.deleteOlderThan(now - 30L * 24 * 60 * 60 * 1000)
            } catch (e: Exception) {
                Log.d("LyricsRepo", "Failed to evict old lyrics cache", e)
            }
        }
    }

    override suspend fun cleanupLyricsCache() {
        try {
            lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            Log.d("LyricsRepo", "Failed to cleanup lyrics cache", e)
        }
    }

    /** The empty UNKNOWN result shared by every no-lyrics exit. */
    private val noLyrics = LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)

    /** The single LyricsCacheEntity shape: one builder so the field wiring (and the fetchedAt stamp) is written once. */
    private fun cacheEntity(
        itemId: String,
        provider: LyricsSource,
        artistName: String? = null,
        trackName: String? = null,
        syncedLyrics: String? = null,
        plainLyrics: String? = null,
        duration: Double? = null,
        lrcLibId: Long? = null,
    ) = LyricsCacheEntity(
        itemId = itemId,
        provider = provider.name,
        artistName = artistName,
        trackName = trackName,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        duration = duration,
        lrcLibId = lrcLibId,
        fetchedAt = System.currentTimeMillis(),
    )

    private fun cachedSource(provider: String?): LyricsSource =
        LyricsSource.entries.find { it.name == provider } ?: LyricsSource.UNKNOWN

    /** Plain (untimed) text → per-line [LyricsLine]s at time 0. */
    private fun plainLines(plain: String): List<LyricsLine> =
        plain.lineSequence().filter { it.isNotBlank() }
            .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList()

    /**
     * Regex-heavy parse over the full synced blob — kept off the Main
     * dispatcher this suspend path is usually called on.
     */
    private suspend fun parseLrcOffMain(synced: String): List<LyricsLine> =
        withContext(Dispatchers.Default) { parseLrc(synced) }

    // Throttle for lyrics-cache eviction. cacheLyrics() is called on every
    // successful lyrics fetch, and each call used to fire a full
    // DELETE FROM lyrics_cache WHERE fetchedAt < :ts scan over the whole table
    // — so opening lyrics for a new track walked & re-locked the entire table.
    // Eviction is best-effort (wrapped in try/catch) and exact cadence isn't
    // observable, so we cap it at once per hour.
    @Volatile
    private var lastLyricsEvictionMs = 0L

    companion object {
        private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")

        private fun parseLrc(lrcContent: String): List<LyricsLine> {
            val lines = mutableListOf<LyricsLine>()
            lrcContent.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val times = TIME_REGEX.findAll(line).map { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toDouble()
                    minutes * 60_000 + (seconds * 1000).toLong()
                }.toList()
                if (times.isEmpty()) return@forEach
                val textStart = line.lastIndexOf(']') + 1
                val text = line.substring(textStart).trim()
                val words = parseInlineWordTimings(text)
                if (text.isEmpty()) {
                    times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = "")) }
                } else {
                    times.forEach { timeMs ->
                        // `words.map { it.copy(timeMs = it.timeMs) }` was an
                        // identity copy of every word-timed line — the parsed
                        // immutable list is already the final shape.
                        lines.add(
                            LyricsLine(
                                timeMs = timeMs,
                                text = text,
                                words = words,
                            )
                        )
                    }
                }
            }
            // LRC files are usually authored already in time order — skip the
            // sorted copy when the parse produced an ordered list.
            return if ((1 until lines.size).all { lines[it - 1].timeMs <= lines[it].timeMs }) {
                lines
            } else {
                lines.sortedBy { it.timeMs }
            }
        }

        /**
         * Parses Enhanced LRC inline word timings:
         * "[00:12.34]Hello [00:12.89]world [00:13.45]test"
         */
        private fun parseInlineWordTimings(text: String): List<com.raulshma.jellyplay.core.model.LyricsWord> {
            if (text.isBlank()) return emptyList()
            val matches = TIME_REGEX.findAll(text).toList()
            if (matches.isEmpty()) return emptyList()
            return matches.mapIndexed { index, match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toDouble()
                val timeMs = minutes * 60_000 + (seconds * 1000).toLong()
                val wordStart = match.range.last + 1
                val wordEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val rawWord = text.substring(wordStart, wordEnd).trim()
                com.raulshma.jellyplay.core.model.LyricsWord(timeMs = timeMs, text = rawWord)
            }.filter { it.text.isNotEmpty() }
        }
    }
}
