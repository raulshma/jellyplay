package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AudioPrefetchEngineTest {

    private val audioStreamCache: AudioStreamCache = mockk(relaxed = true)
    private val policyGuard: AudioCachePolicyGuard = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val preferencesStore: AudioCacheStore = mockk(relaxed = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val queue = mutableListOf<AudioQueueItem>()
    private var currentIndex = 0
    private var position = 0L
    private var duration = 200_000L

    private lateinit var engine: AudioPrefetchEngine

    @Before
    fun setup() {
        every { preferencesStore.audioCache } returns MutableStateFlow(
            AudioCacheSlice(audioCachingEnabled = true, audioPrefetchLookahead = 3)
        )
        every { policyGuard.isPrefetchAllowed } returns MutableStateFlow(true)
        every { audioStreamCache.getCachedBytes(any()) } returns 0L
        every { audioStreamCache.cacheSpaceBytes() } returns 0L
        coEvery { audioStreamCache.warmTrack(any()) } returns Result.success(5_000_000L)
        every {
            playbackRepository.getStreamUrl(any(), any(), any(), any(), any())
        } returns "https://server/Audio/track/universal?api_key=k"

        engine = AudioPrefetchEngine(
            audioStreamCache = audioStreamCache,
            policyGuard = policyGuard,
            playbackRepository = playbackRepository,
            audioCacheStore = preferencesStore,
            backgroundScope = scope,
        )
        engine.bindProviders(
            queueProvider = { queue.toList() },
            currentIndexProvider = { currentIndex },
            positionProvider = { position },
            durationProvider = { duration },
        )
    }

    @Test
    fun `does not warm when position below 50 percent`() = runTest {
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 50_000L // 25% of 200s
        engine.start()
        advanceUntilIdle()
        assertTrue(engine.warmingState.value.isEmpty())
        engine.stop()
    }

    @Test
    fun `warms upcoming tracks when position crosses 50 percent`() = runTest {
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 110_000L // 55% of 200s
        engine.start()
        advanceUntilIdle()
        // t1, t2, t3 (lookahead=3) — but they complete + clear from state.
        // Verify warmTrack was called 3 times.
        io.mockk.coVerify(atLeast = 3) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `lookahead zero never warms`() = runTest {
        every { preferencesStore.audioCache } returns MutableStateFlow(
            AudioCacheSlice(audioCachingEnabled = true, audioPrefetchLookahead = 0)
        )
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1")))
        currentIndex = 0
        position = 150_000L
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `audioCachingDisabled never warms`() = runTest {
        every { preferencesStore.audioCache } returns MutableStateFlow(
            AudioCacheSlice(audioCachingEnabled = false, audioPrefetchLookahead = 3)
        )
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1")))
        currentIndex = 0
        position = 150_000L
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `skips already-cached tracks`() = runTest {
        every { audioStreamCache.getCachedBytes(any()) } returns 10_000_000L // > avgTrackBytes (8M seed)
        coEvery { audioStreamCache.warmTrack(any()) } returns Result.success(10_000_000L)
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1")))
        currentIndex = 0
        position = 150_000L
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `unknown duration skips warming`() = runTest {
        duration = 0L
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1")))
        currentIndex = 0
        position = 1_000_000L
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    // ── Trigger watcher (position/track-change re-evaluations) ──────────

    private val triggerPositionFlow = MutableStateFlow(0L)
    private val triggerDurationFlow = MutableStateFlow(200_000L)
    private val triggerIndexFlow = MutableStateFlow(0)

    private fun bindTriggerFlows() {
        engine.bindTriggerFlows(
            positionFlow = triggerPositionFlow,
            durationFlow = triggerDurationFlow,
            currentIndexFlow = triggerIndexFlow,
        )
    }

    @Test
    fun `trigger watcher warms on threshold crossing exactly once per track`() = runTest {
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 50_000L
        triggerPositionFlow.value = 50_000L
        bindTriggerFlows()
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }

        // Cross 50%: one evaluation warming t1..t3 (lookahead 3).
        position = 110_000L
        triggerPositionFlow.value = 110_000L
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 3) { audioStreamCache.warmTrack(any()) }

        // Still beyond the threshold: the Boolean gate must not re-fire.
        position = 150_000L
        triggerPositionFlow.value = 150_000L
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 3) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `trigger watcher re-arms after track change and warms the next crossing`() = runTest {
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 110_000L
        triggerPositionFlow.value = 110_000L
        bindTriggerFlows()
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 3) { audioStreamCache.warmTrack(any()) }

        // Move to t1 near its start: the track-change edge fires one
        // evaluation (below threshold → no-op) and re-arms the Boolean.
        // Pull position is lowered BEFORE the cursor move so the track-change
        // evaluation reads the already-reset position.
        position = 10_000L
        currentIndex = 1
        triggerIndexFlow.value = 1
        triggerPositionFlow.value = 10_000L
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 3) { audioStreamCache.warmTrack(any()) }

        // Cross t1's threshold: warms t2, t3 only (t4 doesn't exist).
        position = 120_000L
        triggerPositionFlow.value = 120_000L
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 5) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `trigger watcher warms when a skip resumes past the threshold`() = runTest {
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 20_000L
        triggerPositionFlow.value = 20_000L
        bindTriggerFlows()
        engine.start()
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }

        // Jump to t2 resumed at 75%: the track-change edge re-evaluates with
        // the position already beyond the threshold.
        currentIndex = 2
        triggerIndexFlow.value = 2
        position = 150_000L
        triggerPositionFlow.value = 150_000L
        advanceUntilIdle()
        io.mockk.coVerify(atLeast = 1) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `trigger watcher respects the policy gate`() = runTest {
        every { policyGuard.isPrefetchAllowed } returns MutableStateFlow(false)
        queue.clear()
        queue.addAll(listOf(track("t0"), track("t1"), track("t2"), track("t3")))
        currentIndex = 0
        position = 20_000L
        triggerPositionFlow.value = 20_000L
        bindTriggerFlows()
        engine.start()
        advanceUntilIdle()

        currentIndex = 1
        triggerIndexFlow.value = 1
        position = 150_000L
        triggerPositionFlow.value = 150_000L
        advanceUntilIdle()
        io.mockk.coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    private fun track(id: String) = AudioQueueItem(
        id = id, name = id, artist = "artist", album = "album", imageUrl = null, mediaSourceId = null,
    )
}
