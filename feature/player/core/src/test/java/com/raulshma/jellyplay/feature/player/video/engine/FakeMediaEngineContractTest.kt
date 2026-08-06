package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The first [MediaEngineContractTest] specimen: the [FakeMediaEngine] is the
 * reference backend for the contract because every reactive property is a
 * mutable flow the test can drive directly. It satisfies the full Level-0 +
 * Level-1 suite, which is the red-green cycle that proved the suite before any
 * real engine was wired in.
 *
 * This test is also the only place in the repo that instantiates
 * [FakeMediaEngine], closing the "dead test double" smell flagged in the
 * architecture review.
 */
@RunWith(RobolectricTestRunner::class)
class FakeMediaEngineContractTest : MediaEngineContractTest() {

    override fun createEngine(): MediaEngine = FakeMediaEngine()

    override fun behavioralDrivingSupported(): Boolean = true
    override fun supportsSynchronousSeek(): Boolean = true
    override fun supportsViewCreation(): Boolean = true
    override fun expectedDisplayName(): String = "FakeMediaEngine"

    private fun fake(): FakeMediaEngine = engine as FakeMediaEngine

    // ── Driver hook implementations: map the abstract hooks onto the fake's
    //    exposed MutableStateFlows / SharedFlows. ──

    override fun drivePlaybackState(state: EnginePlaybackState) {
        fake().playbackState.value = state
    }

    override fun driveIsPlaying(value: Boolean) {
        fake().isPlayingState.value = value
    }

    override fun driveBufferedPosition(ms: Long) {
        fake().bufferedPositionMs.value = ms
    }

    override fun drivePosition(ms: Long) {
        fake().positionEmissions.value = flowOf(ms)
    }

    override fun driveCurrentCues(cues: List<TimedCue>) {
        fake().currentCuesState.value = cues
    }

    override fun driveLiveSubtitleCue(text: CharSequence?) {
        fake().liveSubtitleCueState.value = text
    }

    override fun emitError(error: EngineError) {
        fake().errorEmissions.tryEmit(error)
    }

    override fun emitSubtitleEvent(event: SubtitleEvent) {
        fake().subtitleEventEmissions.tryEmit(event)
    }

    // ── Fake-only assertions: the invocation record + rounding contract that
    //    belong to the fake as a test double, not to MediaEngine as a contract. ──

    @Test
    fun load_recordsInvocationAndRequest() {
        val request = PlaybackRequest(uri = "content://x", title = "title")
        fake().load(request)
        assertEquals(1, fake().loadCount)
        assertEquals(request, fake().lastRequest)
    }

    @Test
    fun release_marksReleased() {
        assertFalse(fake().released)
        fake().release()
        assertTrue(fake().released)
    }

    @Test
    fun advanceTo_updatesCurrentPosition() {
        fake().advanceTo(12_000L)
        assertEquals(12_000L, engine.currentPositionMs)
    }

    @Test
    fun seekTo_recordsPosition() {
        engine.seekTo(7_500L)
        assertEquals(7_500L, engine.currentPositionMs)
    }

    @Test
    fun volume_isRoundedToClampedRange() {
        engine.setVolume(0.3f)
        assertEquals(0.3f, engine.volume, 0.0001f)
    }

    @Test
    fun selectTrack_isTolerated() {
        engine.selectTrack(TrackType.SUBTITLE, 2)
        // No state to assert on the fake; the contract is "does not throw".
    }
}
