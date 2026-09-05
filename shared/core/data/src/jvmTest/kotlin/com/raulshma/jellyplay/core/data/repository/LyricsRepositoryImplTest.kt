package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lyrics engine's suite. Ported from the legacy `:core:data`
 * MediaRepositoryImplTest (where the getLyricsWithFallback chain squatted
 * before LyricsRepositoryImpl was extracted, commit fb71223fb) — every case
 * now targets [LyricsRepositoryImpl]'s own constructor and interface.
 *
 * Two legacy pins were re-homed rather than copied verbatim:
 *  - The four `parseLrc` cases: the legacy file tested a file-PRIVATE copy of
 *    the parser (a dead assertion — it never exercised production code). They
 *    now pin the real parser through the public cached-synced path, and the
 *    "invalid input" case pins the resulting fall-through to the Jellyfin
 *    endpoint.
 *  - Two new characterization pins for behaviour the extraction added:
 *    negative-result caching and the once-per-hour eviction throttle.
 */
class LyricsRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val lrcLibApi: LrcLibApi = mockk(relaxed = true)
    private val lyricsCacheDao: LyricsCacheDao = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor = mockk(relaxed = true)

    /** Controllable wall clock behind the eviction throttle + fetchedAt stamps. */
    private val fakeTimeSource = FakeTimeSource()

    private lateinit var repository: LyricsRepositoryImpl

    @BeforeTest
    fun setup() {
        every { networkMonitor.networkStatus } returns MutableStateFlow(NetworkStatus.Online)
        repository = LyricsRepositoryImpl(
            apiClient,
            lrcLibApi,
            lyricsCacheDao,
            networkMonitor,
            fakeTimeSource,
        )
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
    fun `getLyricsWithFallback caches the negative result when nothing is found`() = runTest {
        // After Jellyfin comes back empty and LRCLIB fails, the miss itself is
        // cached (provider UNKNOWN) so a known-no-lyrics item doesn't re-query
        // both sources on the next open.
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )
        coEvery { lrcLibApi.getBestMatch("Artist", "Track", null) } returns
            Result.failure(RuntimeException("lrclib down"))

        val result = repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        assertTrue(result.isSuccess)
        assertEquals(LyricsSource.UNKNOWN, result.getOrNull()!!.source)
        val cached = slot<LyricsCacheEntity>()
        coVerify(exactly = 1) { lyricsCacheDao.upsert(capture(cached)) }
        assertEquals("item-1", cached.captured.itemId)
        assertEquals(LyricsSource.UNKNOWN.name, cached.captured.provider)
        assertEquals("Artist", cached.captured.artistName)
        assertEquals("Track", cached.captured.trackName)
    }

    @Test
    fun `cacheLyrics evicts old rows at most once per hour`() = runTest {
        // The eviction is throttled (deleteOlderThan is a full table scan), so
        // the first successful fetch triggers it, a second fetch within the
        // hour must not, and crossing the hour on the injected fake clock
        // re-arms it. The cutoff is pinned to prove it derives from the
        // injected [TimeSource] (now − 30 days), not the system clock.
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns null
        coEvery { lyricsCacheDao.getByItemId("item-2") } returns null
        coEvery { lyricsCacheDao.getByItemId("item-3") } returns null
        coEvery { apiClient.getLyrics(any()) } returns Result.success(
            LyricsResult(
                lines = listOf(LyricsLine(timeMs = 1000L, text = "Hi")),
                source = LyricsSource.EMBEDDED,
            )
        )

        // Start past the throttle window (lastLyricsEvictionMs starts at 0, so
        // the first eviction needs now > 1h in fake-clock terms).
        fakeTimeSource.nowMs = 10 * 60 * 60 * 1000L
        repository.getLyricsWithFallback("item-1", "Artist", "Track", null)

        fakeTimeSource.nowMs += 30 * 60_000L // 30 min later — inside the window
        repository.getLyricsWithFallback("item-2", "Artist", "Track", null)

        fakeTimeSource.nowMs += 31 * 60_000L // past the hour — throttle re-armed
        repository.getLyricsWithFallback("item-3", "Artist", "Track", null)

        coVerify(exactly = 3) { lyricsCacheDao.upsert(any()) }
        val evictedBelow = mutableListOf<Long>()
        coVerify(exactly = 2) { lyricsCacheDao.deleteOlderThan(capture(evictedBelow)) }
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000
        assertEquals(10 * 60 * 60 * 1000L - thirtyDaysMs, evictedBelow[0], "first cutoff = fetch time − 30 days")
        assertEquals((10 * 60 * 60 * 1000L + 61 * 60_000L) - thirtyDaysMs, evictedBelow[1], "second cutoff follows the fake clock")
    }

    // ------------------------------------------------------------------
    // The four legacy parseLrc pins, re-homed: the legacy file tested a
    // file-private COPY of the parser (dead assertion). These drive the real
    // parser through the public cached-synced path of getLyricsWithFallback.
    // ------------------------------------------------------------------

    @Test
    fun `parseLrc parses single timestamp correctly`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "[00:01.500]Hello World",
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()!!.lines
        assertEquals(1, lines.size)
        assertEquals(1500L, lines[0].timeMs)
        assertEquals("Hello World", lines[0].text)
    }

    @Test
    fun `parseLrc sorts lines by time`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "[00:10.000]Second\n[00:05.000]First\n[00:15.000]Third",
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()!!.lines
        assertEquals(3, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
        assertEquals("Third", lines[2].text)
    }

    @Test
    fun `parseLrc handles empty lines`() = runTest {
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "[00:01.000]\n[00:05.000]Hello",
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()!!.lines
        assertEquals(2, lines.size)
        assertEquals("", lines[0].text)
        assertEquals(1000L, lines[0].timeMs)
    }

    @Test
    fun `unparseable cached synced lyrics fall through to the Jellyfin endpoint`() = runTest {
        // Adaptation of the legacy `parseLrc returns empty list for invalid
        // input` pin: a synced blob with no timestamps parses to zero lines,
        // and the fallback chain must treat the cache entry as a miss and
        // query the Jellyfin endpoint instead of returning a dead result.
        coEvery { lyricsCacheDao.getByItemId("item-1") } returns LyricsCacheEntity(
            itemId = "item-1",
            provider = "LRCLIB",
            syncedLyrics = "no timestamps here",
        )
        coEvery { apiClient.getLyrics("item-1") } returns Result.success(
            LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        )

        val result = repository.getLyricsWithFallback("item-1", null, null, null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.lines.size)
        coVerify(exactly = 1) { apiClient.getLyrics("item-1") }
    }

    /**
     * Controllable [TimeSource] for the eviction throttle — same shape as the
     * fake in MediaRepositoryHomeSectionsCacheTest (core:data deliberately
     * hosts no shared test fakes; see TimeSource's KDoc).
     */
    private class FakeTimeSource(var nowMs: Long = 1_000L) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
