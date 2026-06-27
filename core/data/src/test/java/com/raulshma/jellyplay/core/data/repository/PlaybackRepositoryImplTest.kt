package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlaybackInfoResult
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ResolvedPlayback
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)

    private lateinit var repository: PlaybackRepositoryImpl

    @Before
    fun setup() {
        repository = PlaybackRepositoryImpl(apiClient)
    }

    @Test
    fun `reportPlaybackStart delegates to apiClient`() = runTest {
        val info = PlaybackStartInfo(
            itemId = "item-1",
            sessionId = "session-1",
            playMethod = PlayMethod.DIRECT_PLAY,
        )
        coEvery { apiClient.reportPlaybackStart("item-1", "session-1", PlayMethod.DIRECT_PLAY) } returns
            Result.success(Unit)

        val result = repository.reportPlaybackStart(info)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `reportPlaybackProgress delegates correctly`() = runTest {
        val progress = PlaybackProgress(
            itemId = "item-1",
            sessionId = "session-1",
            positionTicks = 10000000L,
            isPaused = false,
            playMethod = PlayMethod.DIRECT_PLAY,
        )
        coEvery {
            apiClient.reportPlaybackProgress("item-1", "session-1", 10000000L, false, PlayMethod.DIRECT_PLAY)
        } returns Result.success(Unit)

        val result = repository.reportPlaybackProgress(progress)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `reportPlaybackStopped delegates to apiClient`() = runTest {
        coEvery { apiClient.reportPlaybackStopped("item-1", "session-1", 5000000L) } returns
            Result.success(Unit)

        val result = repository.reportPlaybackStopped("item-1", "session-1", 5000000L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `getImageUrl delegates to apiClient`() {
        every { apiClient.getImageUrl("item-1", "Primary", 400) } returns "https://test/img"

        val url = repository.getImageUrl("item-1", "Primary", 400)

        assertEquals("https://test/img", url)
    }

    @Test
    fun `getStreamUrl delegates to apiClient`() {
        every { apiClient.getStreamUrl("item-1", "source-1", 0L) } returns "https://test/stream"

        val url = repository.getStreamUrl("item-1", "source-1", 0L)

        assertEquals("https://test/stream", url)
    }

    @Test
    fun `getMediaSegments returns API segments when available`() = runTest {
        val segments = listOf(
            MediaSegment("seg-1", "item-1", MediaSegmentType.INTRO, 1000L, 5000L),
        )
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(segments)

        val result = repository.getMediaSegments("item-1")

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals(MediaSegmentType.INTRO, result.getOrNull()!![0].type)
    }

    @Test
    fun `getMediaSegments falls back to intro and credit timestamps`() = runTest {
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(emptyList())
        coEvery { apiClient.getIntroTimestamps("item-1") } returns Result.success(
            IntroTimestamps(itemId = "item-1", introStartTicks = 100L, introEndTicks = 200L)
        )
        coEvery { apiClient.getCreditTimestamps("item-1") } returns Result.success(
            CreditTimestamps(itemId = "item-1", creditStartTicks = 300L, creditEndTicks = 400L)
        )

        val result = repository.getMediaSegments("item-1")

        assertTrue(result.isSuccess)
        val segs = result.getOrNull()!!
        assertEquals(2, segs.size)
        assertEquals(MediaSegmentType.INTRO, segs[0].type)
        assertEquals(MediaSegmentType.OUTRO, segs[1].type)
        assertEquals(100L, segs[0].startTicks)
        assertEquals(400L, segs[1].endTicks)
    }

    @Test
    fun `getMediaSegments falls back with only intro`() = runTest {
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(emptyList())
        coEvery { apiClient.getIntroTimestamps("item-1") } returns Result.success(
            IntroTimestamps(itemId = "item-1", introStartTicks = 100L, introEndTicks = 200L)
        )
        coEvery { apiClient.getCreditTimestamps("item-1") } returns Result.success(
            CreditTimestamps(itemId = "item-1")
        )

        val result = repository.getMediaSegments("item-1")

        assertTrue(result.isSuccess)
        val segs = result.getOrNull()!!
        assertEquals(1, segs.size)
        assertEquals(MediaSegmentType.INTRO, segs[0].type)
    }

    @Test
    fun `getMediaSegments returns empty when no segments found`() = runTest {
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(emptyList())
        coEvery { apiClient.getIntroTimestamps("item-1") } returns Result.success(
            IntroTimestamps(itemId = "item-1")
        )
        coEvery { apiClient.getCreditTimestamps("item-1") } returns Result.success(
            CreditTimestamps(itemId = "item-1")
        )

        val result = repository.getMediaSegments("item-1")

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun `getMediaSegments uses cache on second call`() = runTest {
        val segments = listOf(
            MediaSegment("seg-1", "item-1", MediaSegmentType.INTRO, 1000L, 5000L),
        )
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(segments)

        val first = repository.getMediaSegments("item-1")
        val second = repository.getMediaSegments("item-1")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        coVerify(exactly = 1) { apiClient.getMediaSegments("item-1") }
    }

    @Test
    fun `getServerUrl delegates to apiClient`() {
        every { apiClient.getServerUrl() } returns "https://test.example.com"

        assertEquals("https://test.example.com", repository.getServerUrl())
    }

    @Test
    fun `getAccessToken delegates to apiClient`() {
        every { apiClient.getAccessToken() } returns "token-123"

        assertEquals("token-123", repository.getAccessToken())
    }

    // ── resolvePlayback ───────────────────────────────────────────────

    private fun stubServer() {
        every { apiClient.getServerUrl() } returns "https://test.example.com"
        every { apiClient.getAccessToken() } returns "token-123"
    }

    @Test
    fun `resolvePlayback picks Direct Play when supported and uses static URL`() = runTest {
        stubServer()
        every { apiClient.getStreamUrl("item-1", "source-1", 0L) } returns "https://test/stream"
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = "session-xyz",
                mediaSources = listOf(
                    MediaSource(
                        id = "source-1",
                        name = "main",
                        supportsDirectPlay = true,
                        supportsDirectStream = true,
                        supportsTranscoding = true,
                    ),
                ),
            ),
        )

        val resolved = repository.resolvePlayback(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startTimeTicks = 0L,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = null,
            mode = PlaybackMode.AUTO,
            playerType = PlayerType.EXO_PLAYER,
        )

        assertEquals(PlayMethod.DIRECT_PLAY, resolved?.playMethod)
        assertEquals("https://test/stream", resolved?.streamUrl)
        assertEquals("session-xyz", resolved?.playSessionId)
    }

    @Test
    fun `resolvePlayback falls back to transcode URL when only transcoding is supported`() = runTest {
        stubServer()
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = "session-t",
                mediaSources = listOf(
                    MediaSource(
                        id = "source-1",
                        name = "main",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = true,
                        transcodeUrl = "/Videos/item-1/master.m3u8?PlaySessionId=session-t",
                    ),
                ),
            ),
        )

        val resolved = repository.resolvePlayback(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startTimeTicks = 0L,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = 3_000_000L,
            mode = PlaybackMode.FORCE_TRANSCODE,
            playerType = PlayerType.MPV,
        )

        assertEquals(PlayMethod.TRANSCODE, resolved?.playMethod)
        assertTrue(resolved?.streamUrl?.startsWith("https://test.example.com/Videos/item-1/master.m3u8") == true)
        assertTrue(resolved?.streamUrl?.contains("api_key=token-123") == true)
        assertEquals(3_000_000L, resolved?.maxStreamingBitrate)
    }

    @Test
    fun `resolvePlayback returns null when server offers no playable method`() = runTest {
        stubServer()
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            PlaybackInfoResult(
                playSessionId = null,
                mediaSources = listOf(
                    MediaSource(
                        id = "source-1",
                        name = "main",
                        supportsDirectPlay = false,
                        supportsDirectStream = false,
                        supportsTranscoding = false,
                    ),
                ),
            ),
        )

        val resolved = repository.resolvePlayback(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startTimeTicks = 0L,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = null,
            mode = PlaybackMode.FORCE_DIRECT_PLAY,
            playerType = PlayerType.EXO_PLAYER,
        )

        assertEquals(null, resolved)
    }

    @Test
    fun `resolvePlayback returns null when PlaybackInfo fetch fails`() = runTest {
        stubServer()
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(RuntimeException("network down"))

        val resolved = repository.resolvePlayback(
            itemId = "item-1",
            mediaSourceId = "source-1",
            startTimeTicks = 0L,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            maxStreamingBitrateBits = null,
            mode = PlaybackMode.AUTO,
            playerType = PlayerType.EXO_PLAYER,
        )

        assertEquals(null, resolved)
    }
}
