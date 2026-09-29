package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [EngineStateChassis]'s published-state reset semantics — the lists the
 * chassis owns so "a field can never be silently dropped from one engine's
 * teardown". Both hosts (androidMain BasePlayerEngine's three adapters and
 * the desktop mpv engine) inherit these exact resets; the desktop's release()
 * adopted the full choreography in the chassis merge, so a regression here
 * would silently leak the previous item's cues/tracks/buffer/stats into the
 * next session on either platform.
 */
class EngineStateChassisTest {

    /**
     * Minimal chassis harness: every engine-specific member is a no-op, with
     * recorders for the hooks the resets fire and drivers for the protected
     * leaves.
     */
    private class HarnessChassis(
        errorReplay: Int = 0,
        errorExtraBufferCapacity: Int = 1,
    ) : EngineStateChassis(
        errorReplay = errorReplay,
        errorExtraBufferCapacity = errorExtraBufferCapacity,
    ) {
        var itemScopedHookCalls = 0
        var configChangedCalls = 0

        override fun onResetItemScopedState() {
            itemScopedHookCalls++
        }

        override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
            configChangedCalls++
        }

        fun publishItemScopedResidue() {
            _currentCues.value = listOf(TimedCue(0L, 1L, "line"))
            _availableTracks.value = listOf(
                MediaTrack(
                    id = "a1",
                    index = 1,
                    label = "audio",
                    language = null,
                    isSelected = true,
                    type = TrackType.AUDIO,
                ),
            )
            _bufferedPositionMs.value = 4_000L
            _bufferedRanges.value = listOf(0L..4_000L)
            _videoStats.value = EngineVideoStats(videoCodec = "h264")
            _liveSubtitleCue.value = "live line"
        }

        fun publishTransportResidue() {
            _playbackState.value = EnginePlaybackState.READY
            _isPlaying.value = true
        }

        fun runItemScopedReset() = resetItemScopedPublishedState()
        fun runFullReset() = resetPublishedEngineState()
        fun emit(error: EngineError) {
            _errorFlow.tryEmit(error)
        }

        // Engine-specific MediaEngine members — no-ops.
        override fun release() {}
        override fun load(request: PlaybackRequest) {}
        override fun play() {}
        override fun pause() {}
        override fun stop() {}
        override fun seekTo(positionMs: Long) {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun selectTrack(type: TrackType, index: Int) {}
        override fun setMaxVideoBitrate(bps: Int?) {}
        override fun applySubtitleStyle(style: SubtitleStyle) {}
        override fun setAspectRatio(ratio: AspectRatio) {}
        override fun setVolume(value: Float, isUserChange: Boolean) {}
        override fun increaseVolume(delta: Float) {}
        override fun decreaseVolume(delta: Float) {}
        override fun setMuted(muted: Boolean) {}

        override val displayName: String = "chassis-harness"
        override val currentPositionMs: Long = 0L
        override val durationMs: Long = 0L
        override val playbackSpeed: Float = 1f
        override val positionFlow: Flow<Long> = emptyFlow()
        override val audioSessionId: Int = -1
        override val capabilities: EngineCapabilities = EngineCapabilities()
        override val volume: Float = 1f
    }

    @Test
    fun `fresh chassis publishes the idle surface`() {
        val chassis = HarnessChassis()
        assertEquals(EnginePlaybackState.IDLE, chassis.playbackState.value)
        assertEquals(false, chassis.isPlaying.value)
        assertEquals(emptyList(), chassis.currentCues.value)
        assertEquals(emptyList(), chassis.availableTracks.value)
        assertEquals(0L, chassis.bufferedPositionMs.value)
        assertEquals(emptyList(), chassis.bufferedRanges.value)
        assertEquals(EngineVideoStats(), chassis.videoStats.value)
        assertEquals(1000L, chassis.pollingIntervalMs.value)
        assertEquals(false, chassis.videoStatsEnabled.value)
        assertEquals(null, chassis.liveSubtitleCue.value)
    }

    @Test
    fun `item-scoped reset clears the per-item leaves and fires the hook`() {
        val chassis = HarnessChassis()
        chassis.publishItemScopedResidue()

        chassis.runItemScopedReset()

        assertEquals(emptyList(), chassis.currentCues.value)
        assertEquals(emptyList(), chassis.availableTracks.value)
        assertEquals(0L, chassis.bufferedPositionMs.value)
        assertEquals(emptyList(), chassis.bufferedRanges.value)
        assertEquals(EngineVideoStats(), chassis.videoStats.value)
        assertEquals(1, chassis.itemScopedHookCalls)
    }

    @Test
    fun `item-scoped reset deliberately does NOT touch the transport leaves`() {
        val chassis = HarnessChassis()
        chassis.publishTransportResidue()

        chassis.runItemScopedReset()

        // The ExoPlayer reuse path's granularity: a mid-session item swap must
        // not flip playbackState/isPlaying.
        assertEquals(EnginePlaybackState.READY, chassis.playbackState.value)
        assertEquals(true, chassis.isPlaying.value)
    }

    @Test
    fun `full reset parks the transport leaves AND clears the item-scoped leaves`() {
        val chassis = HarnessChassis()
        chassis.publishTransportResidue()
        chassis.publishItemScopedResidue()

        chassis.runFullReset()

        assertEquals(EnginePlaybackState.IDLE, chassis.playbackState.value)
        assertEquals(false, chassis.isPlaying.value)
        assertEquals(emptyList(), chassis.currentCues.value)
        assertEquals(emptyList(), chassis.availableTracks.value)
        assertEquals(0L, chassis.bufferedPositionMs.value)
        assertEquals(emptyList(), chassis.bufferedRanges.value)
        assertEquals(EngineVideoStats(), chassis.videoStats.value)
        assertEquals(1, chassis.itemScopedHookCalls)
    }

    @Test
    fun `session prefs survive the full reset`() {
        val chassis = HarnessChassis()
        chassis.setPollingIntervalMs(5_000L)
        chassis.setVideoStatsEnabled(true)

        chassis.runFullReset()

        assertEquals(5_000L, chassis.pollingIntervalMs.value)
        assertEquals(true, chassis.videoStatsEnabled.value)
    }

    @Test
    fun `updateConfig dedup guard routes only real diffs to the hook`() {
        val chassis = HarnessChassis()

        chassis.updateConfig(EngineConfig(subtitleDelayMs = 20L))
        assertEquals(1, chassis.configChangedCalls)
        // Identical push is deduped — the hook is not consulted.
        chassis.updateConfig(EngineConfig(subtitleDelayMs = 20L))
        assertEquals(1, chassis.configChangedCalls)
        chassis.updateConfig(EngineConfig(subtitleDelayMs = 30L))
        assertEquals(2, chassis.configChangedCalls)
    }

    @Test
    fun `buffer capacities are parameterized — replay redelivers to a late subscriber`() {
        runTest {
            // The desktop's replay=1 divergence: a construction-time error is
            // still delivered to the EngineEventCoordinator that subscribes a
            // beat later (the "missing libmpv" black-screen fix).
            val chassis = HarnessChassis(errorReplay = 1, errorExtraBufferCapacity = 8)
            val error = EngineError.Source(httpStatus = null, cause = null)
            chassis.emit(error)

            val received = mutableListOf<EngineError>()
            val job = launch {
                val replayed: EngineError = chassis.errorFlow.first()
                received += replayed
            }
            job.join()
            assertEquals(listOf<EngineError>(error), received)
        }
    }

    @Test
    fun `default capacities — no replay, a late subscriber receives nothing`() {
        runTest {
            // The Android base's historical no-replay contract is the default.
            val chassis = HarnessChassis()
            chassis.emit(EngineError.Source(httpStatus = null, cause = null))

            val received = mutableListOf<EngineError>()
            val job = launch { chassis.errorFlow.collect { received += it } }
            repeat(5) { yield() }
            assertTrue(received.isEmpty(), "replay must default to 0")
            job.cancel()
        }
    }
}
