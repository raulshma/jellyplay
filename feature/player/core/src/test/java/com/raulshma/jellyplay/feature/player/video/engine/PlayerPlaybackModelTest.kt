package com.raulshma.jellyplay.feature.player.video.engine

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerPlaybackModelTest {

    @Test
    fun state_beforeBind_isIdle() = runTest {
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        assertEquals(EnginePlaybackState.IDLE, model.state.value)
    }

    @Test
    fun capabilities_beforeBind_areDefault() = runTest {
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        assertEquals(EngineCapabilities(), model.capabilities)
    }

    @Test
    fun bind_delegatesPlaybackStateAndCapabilities() = runTest {
        val fake = FakeMediaEngine()
        fake.capabilities = EngineCapabilities(supportsPip = true)
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(fake.playbackState.value, model.state.value)
        assertTrue(model.capabilities.supportsPip)
    }

    @Test
    fun bind_conflatesPositionTo1Hz() = runTest {
        val fake = FakeMediaEngine()
        fake.currentPositionMs = 400L
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        advanceTimeBy(1_500) // one tick
        assertEquals(400L, model.positionMs.value)
    }

    @Test
    fun bind_durationReflectsEngine() = runTest {
        val fake = FakeMediaEngine()
        fake.durationValue = 5_000L
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(5_000L, model.durationMs.value)
    }

    @Test
    fun bind_bufferedPositionReflectsEngine() = runTest {
        val fake = FakeMediaEngine()
        fake.bufferedPositionMs.value = 2_000L
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(2_000L, model.bufferedMs.value)
    }

    @Test
    fun bind_availableTracksReflectEngine() = runTest {
        val fake = FakeMediaEngine()
        val track = MediaTrack(id = "a1", index = 0, label = "Eng", language = "en", isSelected = true, type = com.raulshma.jellyplay.core.model.TrackType.AUDIO)
        fake.tracks.value = listOf(track)
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(listOf(track), model.availableTracks.value)
    }

    @Test
    fun bind_mapsEmptyErrorFlowToNoErrors() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        // No emission expected.
        assertNull(model.errors.replayCache.firstOrNull())
    }

    @Test
    fun unbind_resetsToIdle() = runTest {
        val fake = FakeMediaEngine()
        fake.playbackState.value = EnginePlaybackState.READY
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(EnginePlaybackState.READY, model.state.value)
        model.unbind()
        assertEquals(EnginePlaybackState.IDLE, model.state.value)
    }

    @Test
    fun bind_stateReactsToEnginePlaybackState() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(EnginePlaybackState.IDLE, model.state.value)
        fake.playbackState.value = EnginePlaybackState.BUFFERING
        advanceUntilIdle()
        assertEquals(EnginePlaybackState.BUFFERING, model.state.value)
        fake.playbackState.value = EnginePlaybackState.READY
        advanceUntilIdle()
        assertEquals(EnginePlaybackState.READY, model.state.value)
    }

    @Test
    fun bind_isPlayingReactsToEngine() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertFalse(model.isPlaying.value)
        fake.isPlayingState.value = true
        advanceUntilIdle()
        assertTrue(model.isPlaying.value)
    }

    @Test
    fun bind_bufferedReactsToEngine() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        fake.bufferedPositionMs.value = 9_000L
        advanceUntilIdle()
        assertEquals(9_000L, model.bufferedMs.value)
    }

    @Test
    fun bind_tracksReactsToEngine() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        val track = MediaTrack("s1", 0, "Sub", "en", false, com.raulshma.jellyplay.core.model.TrackType.SUBTITLE)
        fake.tracks.value = listOf(track)
        advanceUntilIdle()
        assertEquals(listOf(track), model.availableTracks.value)
    }

    @Test
    fun bind_videoStatsReactsToEngine() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        val stats = EngineVideoStats(videoCodec = "hevc")
        fake.videoStatsState.value = stats
        advanceUntilIdle()
        assertEquals("hevc", model.videoStats.value.videoCodec)
    }

    @Test
    fun bind_passesThroughStructuredEngineError() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        val collected = backgroundScope.async { model.errors.first() }
        val structured = EngineError.Network(cause = null)
        fake.errorEmissions.emit(structured)
        val error = collected.await()
        assertSame(structured, error)
        assertTrue(error.retryable)
    }

    @Test
    fun unbind_cancelsReactiveCollection() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        model.unbind()
        // Mutating the engine after unbind must not affect the model.
        fake.playbackState.value = EnginePlaybackState.READY
        advanceUntilIdle()
        assertEquals(EnginePlaybackState.IDLE, model.state.value)
    }

    @Test
    fun unbind_clearsErrorReplayCache() = runTest {
        val fake = FakeMediaEngine()
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        // Emit an error, then unbind before subscribing.
        fake.errorEmissions.emit(EngineError.Unknown("Playback error (mpv): -7"))
        model.unbind()
        // Re-bind a fresh engine — the stale error must not carry over.
        val fake2 = FakeMediaEngine()
        model.bind(fake2)
        // No error should be immediately replayed to a new subscriber.
        assertNull(model.errors.replayCache.firstOrNull())
    }

    @Test
    fun bind_durationUpdatesEachPollTick() = runTest {
        val fake = FakeMediaEngine()
        fake.durationValue = 0L
        val model = DefaultPlayerPlaybackModel(scope = backgroundScope)
        model.bind(fake)
        assertEquals(0L, model.durationMs.value)
        // Simulate a late duration resolution (HLS/transcoded stream).
        fake.durationValue = 5_400_000L
        advanceTimeBy(1_500) // one position-poll tick
        assertEquals(5_400_000L, model.durationMs.value)
    }
}
