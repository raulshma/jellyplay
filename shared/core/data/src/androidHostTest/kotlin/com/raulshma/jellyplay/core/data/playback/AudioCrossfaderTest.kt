package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins [AudioCrossfader]'s trigger, teardown and transition invariants:
 *
 * - `maybeStart` fires only when a crossfade is configured (duration > 0), the
 *   repeat mode is not repeat-all (2), the primary player is inside the
 *   crossfade window (time remaining ≤ duration, > 0), a next queue entry
 *   exists (or repeat ≥ 1 wraps to index 0), and no crossfade is already
 *   running. Every unmet gate is a complete no-op.
 * - The positive path resolves the next item's source, builds a secondary
 *   ExoPlayer, ramps primary → 0 and secondary → full volume, stops+releases
 *   the primary (after detaching its listener) and hands the secondary over
 *   via `onCrossfadeTransition`.
 * - A failed detail fetch releases the crossfading flag (so the next attempt
 *   is not blocked) and reports `onCrossfadeFailed` for queue reconciliation.
 * - `cancel()` releases the crossfading flag, stops+releases the secondary
 *   player and restores the primary volume to 1.0.
 *
 * The secondary player is a real ExoPlayer built on Robolectric's main looper
 * (matching production construction); the primary is a mock.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioCrossfaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val effectsProcessor: AudioEffectsProcessor = mockk(relaxed = true) {
        // Relaxed-mock StateFlow.value returns a bare Object, which explodes
        // when the crossfade ramp unboxes it as Boolean — stub the flows the
        // ramp reads with the real defaults.
        every { nightModeEnabled } returns MutableStateFlow(false)
        every { nightModeVolumeForStrength } returns 1.0f
    }
    private val playbackSourceResolver: PlaybackSourceResolver = mockk(relaxed = true)
    private val primaryPlayer: ExoPlayer = mockk(relaxed = true)
    private val dataSourceFactory: DataSource.Factory = mockk(relaxed = true)

    private var repeatMode = 0
    private var crossfadeMs = 1_000L
    private var crossfading = false
    private var queueSize = 2
    private var detailResult: Result<MediaDetail> = Result.success(
        MediaDetail(item = MediaItem(id = "next", name = "Next Song", mediaType = MediaType.MUSIC)),
    )

    private val primaryVolumes = mutableListOf<Float>()
    private var transitioned: Triple<ExoPlayer, Int, AudioQueueItem>? = null
    private val failedIndices = mutableListOf<Int>()

    @Test
    fun `a zero crossfade duration never starts a transition`() {
        crossfadeMs = 0L

        crossfader().maybeStart()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
        assertFalse(crossfading)
    }

    @Test
    fun `repeat-all never starts a transition`() {
        repeatMode = 2

        crossfader().maybeStart()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `a position outside the crossfade window never starts a transition`() {
        every { primaryPlayer.duration } returns 30_000L
        every { primaryPlayer.currentPosition } returns 24_000L // 6 s remaining > 1 s crossfade

        crossfader().maybeStart()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `the end of the queue without repeat never starts a transition`() {
        queueSize = 1
        every { primaryPlayer.duration } returns 30_000L
        every { primaryPlayer.currentPosition } returns 29_500L
        every { primaryPlayer.currentMediaItemIndex } returns 0

        crossfader().maybeStart()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `an in-flight crossfade blocks a second trigger`() {
        crossfading = true
        every { primaryPlayer.duration } returns 30_000L
        every { primaryPlayer.currentPosition } returns 29_500L
        every { primaryPlayer.currentMediaItemIndex } returns 0

        crossfader().maybeStart()

        coVerify(exactly = 0) { mediaRepository.getMediaDetail(any(), any()) }
    }

    @Test
    fun `the positive path transitions to the secondary player and releases the primary`() {
        every { primaryPlayer.duration } returns 30_000L
        every { primaryPlayer.currentPosition } returns 29_500L
        every { primaryPlayer.currentMediaItemIndex } returns 0
        every { primaryPlayer.volume = any<Float>() } answers { primaryVolumes += firstArg<Float>() }
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns detailResult
        coEvery { playbackSourceResolver.resolvePlaybackSource(any(), any(), any()) } returns
            ResolvedPlaybackSource.Stream(itemId = "next", url = "http://stream", title = "Next Song", mediaSourceId = "ms1")
        every { playbackRepository.getImageUrl(any(), any(), any()) } returns "http://img"

        val crossfader = crossfader()
        crossfader.maybeStart()

        assertTrue("crossfade flag must be raised synchronously", crossfading)

        val transition = awaitTransition()
        val secondary = transition.first
        assertEquals(1, transition.second)
        assertEquals("next", transition.third.id)
        assertFalse("secondary must be a fresh player", secondary === primaryPlayer)
        assertEquals("primary fades to silence", 0.0f, primaryVolumes.last(), 0.001f)
        assertTrue(primaryVolumes.first() < 1.0f) // ramp started immediately
        verify(exactly = 1) { primaryPlayer.stop() }
        verify(exactly = 1) { primaryPlayer.release() }
    }

    @Test
    fun `a failed detail fetch releases the flag and reports the failure index`() {
        every { primaryPlayer.duration } returns 30_000L
        every { primaryPlayer.currentPosition } returns 29_500L
        every { primaryPlayer.currentMediaItemIndex } returns 0
        coEvery { mediaRepository.getMediaDetail(any(), any()) } returns Result.failure(IllegalStateException("down"))

        val crossfader = crossfader()
        crossfader.maybeStart()

        assertTrue("flag released on failure", awaitCondition { !crossfading })
        assertEquals(listOf(1), failedIndices)
    }

    @Test
    fun `cancel restores the primary volume and drops the flag`() {
        every { primaryPlayer.volume = any<Float>() } answers { primaryVolumes += firstArg<Float>() }

        crossfader().cancel()

        assertFalse(crossfading)
        assertEquals(listOf(1.0f), primaryVolumes)
    }

    // ── plumbing ─────────────────────────────────────────────────────────

    private val nextItem = AudioQueueItem(
        id = "next",
        name = "Next Song",
        artist = "Artist",
        album = "Album",
        imageUrl = null,
        mediaSourceId = "ms1",
    )

    private fun crossfader(): AudioCrossfader = AudioCrossfader(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        context = context,
        effectsProcessor = effectsProcessor,
        mediaRepository = mediaRepository,
        playbackRepository = playbackRepository,
        playbackSourceResolver = playbackSourceResolver,
        repeatModeProvider = { repeatMode },
        crossfadeDurationMsProvider = { crossfadeMs },
        isCrossfadingProvider = { crossfading },
        isCrossfadingSetter = { crossfading = it },
        exoPlayerProvider = { primaryPlayer },
        queueSizeProvider = { queueSize },
        onGetNextItem = { index -> if (index == 1) nextItem else null },
        speedProvider = { 1.0f },
        audioBufferProvider = { 50_000 to 500_000 },
        onCrossfadeTransition = { secondary, nextIndex, nextItem ->
            transitioned = Triple(secondary, nextIndex, nextItem)
        },
        detachPrimaryListener = { },
        onCrossfadeError = { },
        onCrossfadeFailed = { failedIndices += it },
        dataSourceFactoryProvider = { dataSourceFactory },
        crossfadePlayerFactory = {
            mockk<ExoPlayer>(relaxed = true) {
                every { duration } returns 30_000L
                every { currentMediaItemIndex } returns 1
            }
        },
    )

    private var lastTransition: Triple<ExoPlayer, Int, AudioQueueItem>? = null

    private fun awaitTransition(): Triple<ExoPlayer, Int, AudioQueueItem> {
        pumpMainLooperUntil { transitioned != null }
        return transitioned!!
    }

    private fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    private fun pumpMainLooperUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            // The crossfade ramp parks on delay(stepDelay) scheduled on the
            // Robolectric main looper's VIRTUAL clock; idle() only drains tasks
            // that are already due, so the pump must advance the clock for the
            // queued delays to ever fire.
            shadowOf(Looper.getMainLooper()).runToEndOfTasks()
            Thread.sleep(10)
        }
        assertTrue("condition not met within ${timeoutMs}ms", condition())
    }
}
