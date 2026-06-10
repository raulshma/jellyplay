package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.network.LrcLibApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val lrcLibApi: LrcLibApi = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    private lateinit var repository: MediaRepositoryImpl

    @Before
    fun setup() {
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        repository = MediaRepositoryImpl(apiClient, lrcLibApi, lyricsCacheDao, networkMonitor)
    }

    @Test
    fun `getLyricsWithFallback returns cached synced lyrics`() = runTest {
        val cachedEntity = LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "[00:05.000]Hello\n[00:10.000]World",
        )
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns cachedEntity

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()!!
        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].text)
        assertEquals(5000L, lyrics.lines[0].timeMs)
        assertEquals("World", lyrics.lines[1].text)
        assertEquals(10000L, lyrics.lines[1].timeMs)
        assertEquals(LyricsSource.LRCLIB, lyrics.source)
    }

    @Test
    fun `getLyricsWithFallback returns cached plain lyrics when synced is null`() = runTest {
        val cachedEntity = LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = null,
            plainLyrics = "Line 1\nLine 2\n\nLine 3",
        )
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns cachedEntity

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        val lyrics = result.getOrNull()!!
        assertEquals(3, lyrics.lines.size)
        assertEquals("Line 1", lyrics.lines[0].text)
        assertEquals(0L, lyrics.lines[0].timeMs)
        assertEquals("Line 3", lyrics.lines[2].text)
    }

    @Test
    fun `getLyricsWithFallback falls back to Jellyfin API when no cache`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(
                lines = listOf(
                    LyricsLine(timeMs = 1000L, text = "From API"),
                ),
                source = LyricsSource.EMBEDDED,
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals("From API", result.getOrNull()!!.lines[0].text)
        coVerify { lyricsCacheDao.upsert(any()) }
    }

    @Test
    fun `getLyricsWithFallback falls back to LrcLib when Jellyfin returns empty`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )
        coEvery { lrcLibApi.getBestMatch("Artist", "Track", null) } returns Result.success(
            LrcLibTrack(
                id = 1L,
                trackName = "Track",
                artistName = "Artist",
                syncedLyrics = "[00:03.500]LrcLib Line",
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals("LrcLib Line", result.getOrNull()!!.lines[0].text)
        assertEquals(LyricsSource.LRCLIB, result.getOrNull()!!.source)
    }

    @Test
    fun `getLyricsWithFallback returns empty when no artist or track name`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
    }

    @Test
    fun `getLyricsWithFallback handles instrumental track from LrcLib`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )
        coEvery { lrcLibApi.getBestMatch("Artist", "Track", null) } returns Result.success(
            LrcLibTrack(
                id = 1L,
                trackName = "Track",
                artistName = "Artist",
                instrumental = true,
            )
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
        assertEquals(LyricsSource.LRCLIB, result.getOrNull()!!.source)
    }

    @Test
    fun `getLyricsWithFallback skips LrcLib in Local mode`() = runTest {
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Local)
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
        assertEquals(LyricsSource.UNKNOWN, result.getOrNull()!!.source)
        coVerify(exactly = 0) { lrcLibApi.getBestMatch(any(), any(), any()) }
    }

    @Test
    fun `getMediaDetail caches result`() = runTest {
        coEvery { apiClient.getMediaDetail("item-1") } returns Result.success(
            mockk(relaxed = true)
        )

        val first = repository.getMediaDetail("item-1")
        assertTrue(first.isSuccess)

        val second = repository.getMediaDetail("item-1")
        assertTrue(second.isSuccess)

        coVerify(exactly = 1) { apiClient.getMediaDetail("item-1") }
    }

    @Test
    fun `parseLrc parses single timestamp correctly`() {
        val lrc = "[00:01.500]Hello World"
        val lines = parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello World", lines[0].text)
    }

    @Test
    fun `parseLrc sorts lines by time`() {
        val lrc = "[00:10.000]Second\n[00:05.000]First\n[00:15.000]Third"
        val lines = parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
        assertEquals("Third", lines[2].text)
    }

    @Test
    fun `parseLrc handles empty lines`() {
        val lrc = "[00:01.000]\n[00:05.000]Hello"
        val lines = parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertEquals(1000L, lines[0].timeMs)
    }

    @Test
    fun `parseLrc returns empty list for invalid input`() {
        val lines = parseLrc("no timestamps here")
        assertEquals(0, lines.size)
    }
}

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
        if (text.isEmpty()) {
            times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = "")) }
        } else {
            times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = text)) }
        }
    }
    return lines.sortedBy { it.timeMs }
}
