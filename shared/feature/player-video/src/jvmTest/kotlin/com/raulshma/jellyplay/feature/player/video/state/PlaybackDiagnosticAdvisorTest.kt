package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.engine.StallCause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackDiagnosticAdvisorTest {

    // ---- StallTracker & Rebuffer Escalation ----

    @Test
    fun `stall tracker escalates rebuffer thresholds on repeated stalls`() {
        var currentTime = 1_000L
        val tracker = StallTracker(windowMs = 60_000L, clock = { currentTime })

        // First stall
        val count1 = tracker.recordStall(currentTime)
        assertEquals(1, count1)
        assertEquals(3_000L, tracker.currentRebufferThresholdMs(currentTime))

        // Second stall within window (10s later)
        currentTime += 10_000L
        val count2 = tracker.recordStall(currentTime)
        assertEquals(2, count2)
        assertEquals(6_000L, tracker.currentRebufferThresholdMs(currentTime))

        // Third stall within window (15s later)
        currentTime += 15_000L
        val count3 = tracker.recordStall(currentTime)
        assertEquals(3, count3)
        assertEquals(12_000L, tracker.currentRebufferThresholdMs(currentTime))

        // Fourth stall still 12s
        currentTime += 5_000L
        val count4 = tracker.recordStall(currentTime)
        assertEquals(4, count4)
        assertEquals(12_000L, tracker.currentRebufferThresholdMs(currentTime))
    }

    @Test
    fun `stalls outside sliding window expire and do not escalate`() {
        var currentTime = 1_000L
        val tracker = StallTracker(windowMs = 60_000L, clock = { currentTime })

        tracker.recordStall(currentTime)
        assertEquals(1, tracker.stallCount(currentTime))

        // Advance past 60s
        currentTime += 65_000L
        assertEquals(0, tracker.stallCount(currentTime))
        assertEquals(3_000L, tracker.currentRebufferThresholdMs(currentTime))

        // New stall is treated as first stall
        tracker.recordStall(currentTime)
        assertEquals(1, tracker.stallCount(currentTime))
        assertEquals(3_000L, tracker.currentRebufferThresholdMs(currentTime))
    }

    @Test
    fun `tracker reset clears all history`() {
        val tracker = StallTracker()
        tracker.recordStall(1_000L)
        tracker.recordStall(2_000L)
        assertEquals(2, tracker.stallCount(3_000L))

        tracker.reset()
        assertEquals(0, tracker.stallCount(3_000L))
        assertEquals(3_000L, tracker.currentRebufferThresholdMs(3_000L))
    }

    // ---- isDropRateHigh ----

    @Test
    fun `drop rate evaluation with reported total frames`() {
        // 20 dropped out of 1000 = 2.0% -> high
        assertTrue(PlaybackDiagnosticAdvisor.isDropRateHigh(droppedFrames = 20, totalVideoFrames = 980))
        // 10 dropped out of 1000 = 1.0% -> not high
        assertFalse(PlaybackDiagnosticAdvisor.isDropRateHigh(droppedFrames = 10, totalVideoFrames = 990))
    }

    @Test
    fun `drop rate evaluation without reported total frames uses absolute threshold`() {
        assertTrue(PlaybackDiagnosticAdvisor.isDropRateHigh(droppedFrames = 250, totalVideoFrames = 0))
        assertFalse(PlaybackDiagnosticAdvisor.isDropRateHigh(droppedFrames = 249, totalVideoFrames = 0))
    }

    // ---- performanceStatsAdviceFor ----

    @Test
    fun `performance stats advice matches core degradation modes`() {
        // Direct play + high drops -> TRY_AUTO_OR_LOW
        assertEquals(
            StatsAdvice.TRY_AUTO_OR_LOW,
            PlaybackDiagnosticAdvisor.performanceStatsAdviceFor(
                droppedFrames = 300,
                totalVideoFrames = 0,
                isDirectPlay = true,
                estimatedBandwidthBps = 10_000_000L,
                streamBitrateBps = 5_000_000,
            ),
        )

        // Transcode + high drops -> TRY_LOW_OR_SWITCH_ENGINE
        assertEquals(
            StatsAdvice.TRY_LOW_OR_SWITCH_ENGINE,
            PlaybackDiagnosticAdvisor.performanceStatsAdviceFor(
                droppedFrames = 300,
                totalVideoFrames = 0,
                isDirectPlay = false,
                estimatedBandwidthBps = 10_000_000L,
                streamBitrateBps = 5_000_000,
            ),
        )

        // Bandwidth starvation -> LOWER_QUALITY_CHECK_WIFI
        assertEquals(
            StatsAdvice.LOWER_QUALITY_CHECK_WIFI,
            PlaybackDiagnosticAdvisor.performanceStatsAdviceFor(
                droppedFrames = 0,
                totalVideoFrames = 1000,
                isDirectPlay = true,
                estimatedBandwidthBps = 2_000_000L,
                streamBitrateBps = 8_000_000,
            ),
        )

        // Healthy playback -> null
        assertNull(
            PlaybackDiagnosticAdvisor.performanceStatsAdviceFor(
                droppedFrames = 0,
                totalVideoFrames = 1000,
                isDirectPlay = true,
                estimatedBandwidthBps = 20_000_000L,
                streamBitrateBps = 8_000_000,
            ),
        )
    }

    // ---- evaluateRecovery ----

    @Test
    fun `brief stall without chronic history does not trigger recovery`() {
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.NETWORK_STARVATION,
            stallDurationMs = 1_500L,
            stallsInWindow = 1,
            isDirectPlay = true,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 10_000_000,
            estimatedBandwidthBps = 2_000_000L,
        )
        assertNull(recovery)
    }

    @Test
    fun `network starvation on direct play suggests validated 720p step-down`() {
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.NETWORK_STARVATION,
            stallDurationMs = 4_000L,
            stallsInWindow = 1,
            isDirectPlay = true,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 18_000_000,
            estimatedBandwidthBps = 3_000_000L,
        )
        assertNotNull(recovery)
        assertEquals("network_step_down_720p", recovery.id)
        assertEquals(StallCause.NETWORK_STARVATION, recovery.cause)
        val action = recovery.primaryAction as RecoveryAction.SwitchQuality
        assertEquals(StreamingQuality.HD_720P, action.targetQuality)
        assertEquals(720, action.height)
        assertEquals(3_000_000, action.bitrateBps)
    }

    @Test
    fun `network starvation on transcode stream steps to the highest tier within bandwidth`() {
        // 480p (1.5 Mbps) exceeds the measured 1.2 Mbps bandwidth — the next
        // lower tier (360p, 0.5 Mbps) is the validated step-down.
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.NETWORK_STARVATION,
            stallDurationMs = 4_000L,
            stallsInWindow = 1,
            isDirectPlay = false,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 4_000_000,
            estimatedBandwidthBps = 1_200_000L,
        )
        assertNotNull(recovery)
        assertEquals("network_step_down_360p", recovery.id)
        val action = recovery.primaryAction as RecoveryAction.SwitchQuality
        assertEquals(StreamingQuality.LOW_360P, action.targetQuality)
        assertEquals(360, action.height)
    }

    @Test
    fun `no lower supported tier omits the quality-reduction action`() {
        // Every ladder tier sits at or above the 400 kbps stream — no valid
        // step-down exists, so the recovery is omitted entirely.
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.NETWORK_STARVATION,
            stallDurationMs = 4_000L,
            stallsInWindow = 1,
            isDirectPlay = true,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 400_000,
            estimatedBandwidthBps = 200_000L,
        )
        assertNull(recovery)
    }

    @Test
    fun `step-down validates bitrate only when no bandwidth estimate exists`() {
        // estimatedBandwidthBps = 0 (no live estimate): the current-bitrate
        // check alone qualifies 720p under a 4 Mbps stream.
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.NETWORK_STARVATION,
            stallDurationMs = 4_000L,
            stallsInWindow = 1,
            isDirectPlay = false,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 4_000_000,
            estimatedBandwidthBps = 0L,
        )
        assertNotNull(recovery)
        val action = recovery.primaryAction as RecoveryAction.SwitchQuality
        assertEquals(StreamingQuality.HD_720P, action.targetQuality)
    }

    @Test
    fun `server transcode bottleneck on ExoPlayer suggests MPV engine`() {
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.SERVER_TRANSCODE_BOTTLENECK,
            stallDurationMs = 4_000L,
            stallsInWindow = 1,
            isDirectPlay = false,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 4_000_000,
            estimatedBandwidthBps = 20_000_000L,
            transcodeSpeedRatio = 0.75,
        )
        assertNotNull(recovery)
        assertEquals("transcode_try_mpv", recovery.id)
        val action = recovery.primaryAction as RecoveryAction.SwitchEngine
        assertEquals(PlayerType.MPV, action.targetEngine)
    }

    @Test
    fun `decoder overload on ExoPlayer suggests MPV engine immediately`() {
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.DECODER_OVERLOAD,
            stallDurationMs = 500L, // Immediate, does not wait for 3.5s stall
            stallsInWindow = 1,
            isDirectPlay = true,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 10_000_000,
            estimatedBandwidthBps = 50_000_000L,
            droppedFrames = 500,
            totalVideoFrames = 1000,
        )
        assertNotNull(recovery)
        assertEquals("decoder_switch_mpv", recovery.id)
        val action = recovery.primaryAction as RecoveryAction.SwitchEngine
        assertEquals(PlayerType.MPV, action.targetEngine)
    }

    @Test
    fun `frequent rebuffers trigger buffer size boost`() {
        val recovery = PlaybackDiagnosticAdvisor.evaluateRecovery(
            stallCause = StallCause.BUFFER_UNDERRUN,
            stallDurationMs = 1_000L,
            stallsInWindow = 3, // 3 stalls in window
            isDirectPlay = true,
            currentEngine = PlayerType.EXO_PLAYER,
            streamBitrateBps = 8_000_000,
            estimatedBandwidthBps = 15_000_000L,
        )
        assertNotNull(recovery)
        assertEquals("frequent_stalls_boost_buffer", recovery.id)
        assertEquals(RecoveryAction.BoostBufferSize, recovery.primaryAction)
    }
}
