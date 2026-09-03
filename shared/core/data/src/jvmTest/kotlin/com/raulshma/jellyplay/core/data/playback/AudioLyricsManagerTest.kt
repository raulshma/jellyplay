package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [AudioLyricsManager]'s lyric line selection + offset bookkeeping:
 *  1. `updateCurrentLyricIndex` binary-searches the active line against
 *     position + offset — before the first line resolves to -1;
 *  2. the offset is clamped to ±500 ms and remembered per item, so returning
 *     to a previously-adjusted track restores its adjustment;
 *  3. a fetch success populates lyrics + source; a failure clears to UNKNOWN;
 *  4. a rapid skip cancels the in-flight fetch so a slow response from an
 *     older track can never overwrite the current one's lyrics;
 *  5. `reset` clears lyrics, index, source, fetching flag and offset.
 */
class AudioLyricsManagerTest {

    private lateinit var lyricsRepository: LyricsRepository
    private lateinit var manager: AudioLyricsManager

    @BeforeTest
    fun setup() {
        lyricsRepository = mockk()
        manager = AudioLyricsManager(lyricsRepository)
    }

    private fun lyricsOf(vararg timesMs: Long) = LyricsResult(
        lines = timesMs.map { LyricsLine(timeMs = it, text = "line@${it}") },
        source = LyricsSource.LRCLIB,
    )

    private fun stubFetch(itemId: String, result: Result<LyricsResult>) {
        coEvery {
            lyricsRepository.getLyricsWithFallback(itemId, any(), any(), any())
        } returns result
    }

    /** Primes lyric state the way a resolved fetch for [itemId] would. */
    private fun kotlinx.coroutines.test.TestScope.prime(itemId: String, result: LyricsResult) {
        stubFetch(itemId, Result.success(result))
        manager.fetchLyrics(itemId, artistName = null, trackName = null, durationSec = null)
        // The fetch launches on backgroundScope: pump the scheduler so the
        // stubbed response lands before the caller asserts (advanceUntilIdle
        // alone does not run these tasks — see the successful-fetch test).
        runCurrent()
    }

    @Test
    fun `updateCurrentLyricIndex selects the last line at or before position plus offset`() = runTest {
        manager.initialize(backgroundScope)
        prime("setup", lyricsOf(0, 1000, 2000))
        advanceUntilIdle()

        manager.updateCurrentLyricIndex(positionMs = 0)
        assertEquals(0, manager.currentLyricIndex.value, "0 + default 300ms offset crosses line@0")

        manager.updateCurrentLyricIndex(positionMs = 200)
        assertEquals(0, manager.currentLyricIndex.value, "200 + 300 crosses the first line")

        manager.updateCurrentLyricIndex(positionMs = 900)
        assertEquals(1, manager.currentLyricIndex.value)

        manager.updateCurrentLyricIndex(positionMs = 9_999)
        assertEquals(2, manager.currentLyricIndex.value, "past the end pins the last line")
    }

    @Test
    fun `the index stays -1 with no lyrics`() = runTest {
        manager.initialize(backgroundScope)

        manager.updateCurrentLyricIndex(positionMs = 5_000)

        assertEquals(-1, manager.currentLyricIndex.value)
    }

    @Test
    fun `the offset is clamped to the -500ms to 500ms window`() = runTest {
        manager.initialize(backgroundScope)

        manager.setLyricsOffset(5_000)
        assertEquals(500L, manager.lyricsOffsetMs.value)
        manager.setLyricsOffset(-9_999)
        assertEquals(-500L, manager.lyricsOffsetMs.value)
    }

    @Test
    fun `the offset is remembered per item and restored on return`() = runTest {
        manager.initialize(backgroundScope)
        prime("i1", lyricsOf(0))
        advanceUntilIdle()
        manager.setLyricsOffset(-250)

        // Switch away and back: i2 restores the default lead, i1 its own offset.
        prime("i2", lyricsOf(0))
        advanceUntilIdle()
        assertEquals(AudioLyricsManager.DEFAULT_OFFSET_MS, manager.lyricsOffsetMs.value)

        prime("i1", lyricsOf(0))
        advanceUntilIdle()
        assertEquals(-250L, manager.lyricsOffsetMs.value)
    }

    @Test
    fun `a successful fetch populates lyrics and source`() = runTest {
        manager.initialize(backgroundScope)
        stubFetch("i1", Result.success(lyricsOf(0, 1000)))

        manager.fetchLyrics("i1", artistName = "Artist", trackName = "Track", durationSec = 200.0)
        runCurrent()
        advanceUntilIdle()

        assertEquals(2, manager.lyrics.value.size)
        assertEquals(LyricsSource.LRCLIB, manager.lyricsSource.value)
        assertEquals(false, manager.isFetchingLyrics.value)
    }

    @Test
    fun `a failed fetch clears lyrics back to UNKNOWN`() = runTest {
        manager.initialize(backgroundScope)
        prime("seeded", lyricsOf(0))
        advanceUntilIdle()
        stubFetch("next", Result.failure(IllegalStateException("no match")))

        manager.fetchLyrics("next", artistName = null, trackName = null, durationSec = null)
        runCurrent()
        advanceUntilIdle()

        assertTrue(manager.lyrics.value.isEmpty())
        assertEquals(LyricsSource.UNKNOWN, manager.lyricsSource.value)
        assertEquals(false, manager.isFetchingLyrics.value)
    }

    @Test
    fun `a rapid skip cancels the stale fetch so it cannot overwrite the new track`() = runTest {
        manager.initialize(backgroundScope)
        coEvery {
            lyricsRepository.getLyricsWithFallback("slow", any(), any(), any())
        } coAnswers {
            delay(10_000)
            Result.success(lyricsOf(-1))
        }
        stubFetch("fast", Result.success(lyricsOf(7)))

        manager.fetchLyrics("slow", artistName = null, trackName = null, durationSec = null)
        runCurrent()
        assertEquals(true, manager.isFetchingLyrics.value)

        // The skip cancels the slow fetch and starts the fast one.
        manager.fetchLyrics("fast", artistName = null, trackName = null, durationSec = null)
        advanceTimeBy(20_000)
        advanceUntilIdle()

        assertEquals(1, manager.lyrics.value.size)
        assertEquals("line@7", manager.lyrics.value.single().text)
    }

    @Test
    fun `searchLyrics forwards the query to the repository callback`() = runTest {
        manager.initialize(backgroundScope)
        val tracks = listOf(LrcLibTrack(id = 1L, trackName = "T", artistName = "A"))
        coEvery { lyricsRepository.searchLyrics("query") } returns Result.success(tracks)

        var received: Result<List<LrcLibTrack>>? = null
        manager.searchLyrics("query") { received = it }
        runCurrent()
        advanceUntilIdle()

        assertEquals(tracks, received!!.getOrThrow())
    }

    @Test
    fun `reset clears every piece of lyric state`() = runTest {
        manager.initialize(backgroundScope)
        prime("seeded", lyricsOf(0))
        advanceUntilIdle()
        manager.updateCurrentLyricIndex(10_000)
        manager.setLyricsOffset(100)

        manager.reset()

        assertTrue(manager.lyrics.value.isEmpty())
        assertEquals(-1, manager.currentLyricIndex.value)
        assertEquals(LyricsSource.UNKNOWN, manager.lyricsSource.value)
        assertEquals(false, manager.isFetchingLyrics.value)
        assertEquals(AudioLyricsManager.DEFAULT_OFFSET_MS, manager.lyricsOffsetMs.value)
    }
}
