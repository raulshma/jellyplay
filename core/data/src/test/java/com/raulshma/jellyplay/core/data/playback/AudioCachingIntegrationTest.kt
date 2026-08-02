package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AudioCachingIntegrationTest {

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
    fun setup() = runTest {
        every { preferencesStore.audioCache } returns MutableStateFlow(
            AudioCacheSlice(
                audioCachingEnabled = true,
                audioPrefetchLookahead = 2,
            )
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
    fun `playback at 55 percent triggers warming of next 2 tracks`() = runTest {
        queue.clear()
        queue.addAll(listOf(
            AudioQueueItem("t0", "Track 0", "Artist", "Album", null, null),
            AudioQueueItem("t1", "Track 1", "Artist", "Album", null, null),
            AudioQueueItem("t2", "Track 2", "Artist", "Album", null, null),
            AudioQueueItem("t3", "Track 3", "Artist", "Album", null, null),
        ))
        currentIndex = 0
        position = 110_000L // 55%

        engine.start()
        advanceUntilIdle()

        // warmTrack called for t1 and t2 (lookahead=2)
        coVerify(atLeast = 2) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }

    @Test
    fun `network policy blocking cancels warming`() = runTest {
        every { policyGuard.isPrefetchAllowed } returns MutableStateFlow(false)
        queue.clear()
        queue.addAll(listOf(
            AudioQueueItem("t0", "Track 0", "Artist", "Album", null, null),
            AudioQueueItem("t1", "Track 1", "Artist", "Album", null, null),
        ))
        currentIndex = 0
        position = 150_000L

        engine.start()
        advanceUntilIdle()

        coVerify(exactly = 0) { audioStreamCache.warmTrack(any()) }
        engine.stop()
    }
}
