package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
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
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ported verbatim from the legacy :core:data suite (same package) so the
 * coverage of the shared [PlaybackRepositoryImpl] survives the legacy shim's
 * deletion — this suite is its sole behavioral coverage.
 */
class PlaybackRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val outbox: PlaybackOutboxRepository = mockk(relaxed = true)
    private val offlineModeManager: OfflineModeManager = mockk()

    private lateinit var repository: PlaybackRepositoryImpl

    @BeforeTest
    fun setup() {
        // Default to online; offline-specific tests override via every { offlineModeManager.isOffline }.
        every { offlineModeManager.isOffline } returns false
        // Real HomeSession over a permanently-null session flow (this suite
        // never switches identity — CacheIdentity.UNKNOWN is the key surface)
        // plus the registry that owns identity reactions.
        every { apiClient.session } returns MutableStateFlow(null)
        val homeSession = com.raulshma.jellyplay.core.data.session.HomeSession(
            apiClient,
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
            ),
        )
        val sessionCacheRegistry = com.raulshma.jellyplay.core.data.session.SessionCacheRegistry(
            homeSession,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        )
        repository = PlaybackRepositoryImpl(apiClient, outbox, offlineModeManager, homeSession, sessionCacheRegistry)
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
        every { apiClient.getStreamUrl("item-1", "source-1", 0L, liveStreamId = null) } returns "https://test/stream"

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
    fun `getMediaSegments caches empty fallback when segments API succeeds with no segments`() = runTest {
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.success(emptyList())
        coEvery { apiClient.getIntroTimestamps("item-1") } returns Result.success(IntroTimestamps(itemId = "item-1"))
        coEvery { apiClient.getCreditTimestamps("item-1") } returns Result.success(CreditTimestamps(itemId = "item-1"))

        repository.getMediaSegments("item-1")
        repository.getMediaSegments("item-1")

        // Success-empty is cached: the legacy endpoints are not re-hit.
        coVerify(exactly = 1) { apiClient.getMediaSegments("item-1") }
    }

    @Test
    fun `getMediaSegments does not cache when segments API fails so the next call retries`() = runTest {
        // A transient network failure must not be masked as "no segments" for
        // the cache TTL — the next call should retry the API.
        coEvery { apiClient.getMediaSegments("item-1") } returns Result.failure(RuntimeException("network"))
        coEvery { apiClient.getIntroTimestamps("item-1") } returns Result.success(IntroTimestamps(itemId = "item-1"))
        coEvery { apiClient.getCreditTimestamps("item-1") } returns Result.success(CreditTimestamps(itemId = "item-1"))

        val first = repository.getMediaSegments("item-1")
        val second = repository.getMediaSegments("item-1")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        // Not cached on failure: the API is hit again on the second call.
        coVerify(exactly = 2) { apiClient.getMediaSegments("item-1") }
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
        every { apiClient.getStreamUrl("item-1", "source-1", 0L, liveStreamId = null) } returns "https://test/stream"
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
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
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
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
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
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

        assertNull(resolved)
    }

    @Test
    fun `resolvePlayback returns null when PlaybackInfo fetch fails`() = runTest {
        stubServer()
        coEvery {
            apiClient.fetchPlaybackInfo(any(), any(), any(), any(), any(), any(), any(), any(), any())
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

        assertNull(resolved)
    }

    // ── Offline outbox ────────────────────────────────────────────────

    @Test
    fun `reportPlaybackProgress enqueues to outbox and returns success when offline`() = runTest {
        every { offlineModeManager.isOffline } returns true
        val progress = PlaybackProgress(
            itemId = "item-1",
            sessionId = "session-1",
            positionTicks = 10_000_000L,
            isPaused = false,
            playMethod = PlayMethod.DIRECT_PLAY,
        )

        val result = repository.reportPlaybackProgress(progress)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            outbox.enqueueProgress(
                itemId = "item-1",
                sessionId = "session-1",
                positionTicks = 10_000_000L,
                isPaused = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                mediaSourceId = null,
            )
        }
        coVerify(exactly = 0) { apiClient.reportPlaybackProgress(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reportPlaybackProgress enqueues to outbox when online api call fails`() = runTest {
        coEvery {
            apiClient.reportPlaybackProgress("item-1", "session-1", 10_000_000L, false, PlayMethod.DIRECT_PLAY)
        } returns Result.failure(RuntimeException("timeout"))

        repository.reportPlaybackProgress(
            PlaybackProgress(
                itemId = "item-1",
                sessionId = "session-1",
                positionTicks = 10_000_000L,
                isPaused = false,
                playMethod = PlayMethod.DIRECT_PLAY,
            )
        )

        coVerify(exactly = 1) {
            outbox.enqueueProgress(
                itemId = "item-1",
                sessionId = "session-1",
                positionTicks = 10_000_000L,
                isPaused = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                mediaSourceId = null,
            )
        }
    }

    @Test
    fun `reportPlaybackStart enqueues to outbox when offline`() = runTest {
        every { offlineModeManager.isOffline } returns true
        val info = PlaybackStartInfo(
            itemId = "item-1",
            sessionId = "session-1",
            startPositionTicks = 5_000_000L,
            playMethod = PlayMethod.DIRECT_STREAM,
        )

        val result = repository.reportPlaybackStart(info)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            outbox.enqueueStart(
                itemId = "item-1",
                sessionId = "session-1",
                playMethod = PlayMethod.DIRECT_STREAM,
                startPositionTicks = 5_000_000L,
            )
        }
    }

    @Test
    fun `reportPlaybackStopped enqueues stop when offline and skips api`() = runTest {
        every { offlineModeManager.isOffline } returns true

        val result = repository.reportPlaybackStopped("item-1", "session-1", 8_000_000L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { outbox.enqueueStop("item-1", "session-1", 8_000_000L) }
        coVerify(exactly = 0) { apiClient.reportPlaybackStopped(any(), any(), any()) }
    }

    @Test
    fun `reportPlaybackStopped clears telemetry for item on online success`() = runTest {
        coEvery { apiClient.reportPlaybackStopped("item-1", "session-1", 8_000_000L) } returns
            Result.success(Unit)

        repository.reportPlaybackStopped("item-1", "session-1", 8_000_000L)

        coVerifyOrder {
            apiClient.reportPlaybackStopped("item-1", "session-1", 8_000_000L)
            outbox.deletePlaybackTelemetryForItem("item-1")
        }
        // Must NOT wipe the whole item — a pending PLAYED/UNPLAYED flip survives.
        coVerify(exactly = 0) { outbox.deleteForItem(any()) }
        coVerify(exactly = 0) { outbox.enqueueStop(any(), any(), any()) }
    }

    @Test
    fun `reportPlaybackStopped enqueues stop when online api call fails`() = runTest {
        coEvery { apiClient.reportPlaybackStopped("item-1", "session-1", 8_000_000L) } returns
            Result.failure(RuntimeException("server 500"))

        repository.reportPlaybackStopped("item-1", "session-1", 8_000_000L)

        coVerify(exactly = 1) { outbox.enqueueStop("item-1", "session-1", 8_000_000L) }
        coVerify(exactly = 0) { outbox.deleteForItem(any()) }
    }

    // ── Online success must NOT touch outbox ──────────────────────────

    @Test
    fun `reportPlaybackProgress online success does not enqueue outbox`() = runTest {
        coEvery {
            apiClient.reportPlaybackProgress("item-1", "s1", 1L, false, PlayMethod.DIRECT_PLAY)
        } returns Result.success(Unit)

        repository.reportPlaybackProgress(
            PlaybackProgress(itemId = "item-1", sessionId = "s1", positionTicks = 1L)
        )

        coVerify(exactly = 0) { outbox.enqueueProgress(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reportPlaybackStart online success does not enqueue outbox`() = runTest {
        coEvery { apiClient.reportPlaybackStart("item-1", "s1", PlayMethod.DIRECT_PLAY) } returns
            Result.success(Unit)

        repository.reportPlaybackStart(PlaybackStartInfo(itemId = "item-1", sessionId = "s1"))

        coVerify(exactly = 0) { outbox.enqueueStart(any(), any(), any(), any()) }
    }

    @Test
    fun `reportPlaybackStart enqueues to outbox when online api call fails`() = runTest {
        coEvery { apiClient.reportPlaybackStart("item-1", "s1", PlayMethod.TRANSCODE) } returns
            Result.failure(RuntimeException("timeout"))

        repository.reportPlaybackStart(
            PlaybackStartInfo(itemId = "item-1", sessionId = "s1", playMethod = PlayMethod.TRANSCODE)
        )

        coVerify(exactly = 1) {
            outbox.enqueueStart(
                itemId = "item-1",
                sessionId = "s1",
                playMethod = PlayMethod.TRANSCODE,
                startPositionTicks = null,
            )
        }
    }

    // ── Field propagation: mediaSourceId + playMethod + isPaused ──────

    @Test
    fun `reportPlaybackProgress offline propagates mediaSourceId and playMethod and paused`() = runTest {
        every { offlineModeManager.isOffline } returns true

        repository.reportPlaybackProgress(
            PlaybackProgress(
                itemId = "item-1",
                sessionId = "s1",
                positionTicks = 99L,
                isPaused = true,
                playMethod = PlayMethod.DIRECT_STREAM,
                mediaSourceId = "source-9",
            )
        )

        coVerify(exactly = 1) {
            outbox.enqueueProgress(
                itemId = "item-1",
                sessionId = "s1",
                positionTicks = 99L,
                isPaused = true,
                playMethod = PlayMethod.DIRECT_STREAM,
                mediaSourceId = "source-9",
            )
        }
    }

    @Test
    fun `reportPlaybackProgress online propagates all fields to apiClient`() = runTest {
        coEvery {
            apiClient.reportPlaybackProgress("item-1", "s1", 99L, true, PlayMethod.DIRECT_STREAM)
        } returns Result.success(Unit)

        repository.reportPlaybackProgress(
            PlaybackProgress(
                itemId = "item-1",
                sessionId = "s1",
                positionTicks = 99L,
                isPaused = true,
                playMethod = PlayMethod.DIRECT_STREAM,
                mediaSourceId = "source-9", // not consumed by apiClient today, but must not throw
            )
        )

        coVerify(exactly = 1) {
            apiClient.reportPlaybackProgress("item-1", "s1", 99L, true, PlayMethod.DIRECT_STREAM)
        }
    }

    // ── STOP edge: offline returns success regardless ─────────────────

    @Test
    fun `reportPlaybackStopped offline returns success and does not clear outbox`() = runTest {
        every { offlineModeManager.isOffline } returns true

        val result = repository.reportPlaybackStopped("item-1", "s1", 100L)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { outbox.enqueueStop("item-1", "s1", 100L) }
        // Offline path must NOT delete — entries still pending, drain hasn't run.
        coVerify(exactly = 0) { outbox.deleteForItem(any()) }
    }

    @Test
    fun `reportPlaybackStopped with zero position offline still enqueues`() = runTest {
        // Callers gate on position > 0 themselves, but the repository must not
        // silently drop a zero-position stop if one reaches it.
        every { offlineModeManager.isOffline } returns true

        repository.reportPlaybackStopped("item-1", "s1", 0L)

        coVerify(exactly = 1) { outbox.enqueueStop("item-1", "s1", 0L) }
    }

    // ── Repeated reports coalesce correctly via outbox ────────────────

    @Test
    fun `repeated online progress failures enqueue repeatedly relying on outbox coalescence`() = runTest {
        coEvery {
            apiClient.reportPlaybackProgress("item-1", "s1", any(), any(), PlayMethod.DIRECT_PLAY)
        } returns Result.failure(RuntimeException("down"))

        // Simulate the 10s reporter loop firing 3 times while offline-but-reported-online.
        repeat(3) { i ->
            repository.reportPlaybackProgress(
                PlaybackProgress(itemId = "item-1", sessionId = "s1", positionTicks = 10L * i)
            )
        }

        // Repository enqueues each call; outbox coalesces to one PROGRESS row
        // (verified in PlaybackOutboxRepositoryImplTest).
        coVerify(exactly = 3) { outbox.enqueueProgress(any(), any(), any(), any(), any(), any()) }
    }

    // ── replayOutboxEntry: entry-type → API-call mapping (the drain path) ──

    private fun outboxEntry(
        type: PlaybackOutboxEventType,
        itemId: String = "item-1",
        sessionId: String = "s1",
        positionTicks: Long = 100L,
        isPaused: Boolean = false,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    ) = PlaybackOutboxEntry(
        id = "e1",
        itemId = itemId,
        eventType = type,
        sessionId = sessionId,
        positionTicks = positionTicks,
        isPaused = isPaused,
        playMethod = playMethod,
        mediaSourceId = null,
        recordedAt = 1_000L,
        createdAt = 1_000L,
    )

    @Test
    fun `replayOutboxEntry START dispatches reportPlaybackStart`() = runTest {
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.START))

        coVerify(exactly = 1) { apiClient.reportPlaybackStart("item-1", "s1", PlayMethod.DIRECT_PLAY) }
        // Pure dispatch: never touches the outbox.
        coVerify(exactly = 0) { outbox.enqueueStart(any(), any(), any(), any()) }
    }

    @Test
    fun `replayOutboxEntry PROGRESS dispatches reportPlaybackProgress with full payload`() = runTest {
        repository.replayOutboxEntry(
            outboxEntry(PlaybackOutboxEventType.PROGRESS, positionTicks = 99L, isPaused = true),
        )

        coVerify(exactly = 1) {
            apiClient.reportPlaybackProgress("item-1", "s1", 99L, true, PlayMethod.DIRECT_PLAY)
        }
    }

    @Test
    fun `replayOutboxEntry STOP dispatches reportPlaybackStopped`() = runTest {
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.STOP, positionTicks = 5_000_000L))

        coVerify(exactly = 1) { apiClient.reportPlaybackStopped("item-1", "s1", 5_000_000L) }
    }

    @Test
    fun `replayOutboxEntry PLAYED and UNPLAYED dispatch markPlayed and markUnplayed`() = runTest {
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.PLAYED, itemId = "a"))
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.UNPLAYED, itemId = "b"))

        coVerify(exactly = 1) { apiClient.markPlayed("a") }
        coVerify(exactly = 1) { apiClient.markUnplayed("b") }
    }

    @Test
    fun `replayOutboxEntry FAVORITE and UNFAVORITE dispatch setFavorite with the right flag`() = runTest {
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.FAVORITE, itemId = "a"))
        repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.UNFAVORITE, itemId = "b"))

        coVerify(exactly = 1) { apiClient.setFavorite("a", isFavorite = true) }
        coVerify(exactly = 1) { apiClient.setFavorite("b", isFavorite = false) }
    }

    @Test
    fun `replayOutboxEntry returns true on server success`() = runTest {
        coEvery { apiClient.markPlayed("item-1") } returns Result.success(Unit)

        assertTrue(repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.PLAYED)))
    }

    @Test
    fun `replayOutboxEntry returns false on server failure`() = runTest {
        coEvery { apiClient.markPlayed("item-1") } returns Result.failure(RuntimeException("500"))

        assertEquals(false, repository.replayOutboxEntry(outboxEntry(PlaybackOutboxEventType.PLAYED)))
        // No re-enqueue — the drain loop owns retry/dead-letter.
        coVerify(exactly = 0) { outbox.enqueueStart(any(), any(), any(), any()) }
    }
}
