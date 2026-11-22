package com.raulshma.jellyplay.feature.player.video.session

import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.feature.player.video.PlaybackSessionTestGraph
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.StallCause
import com.raulshma.jellyplay.feature.player.video.state.RecoveryAction
import com.raulshma.jellyplay.feature.player.video.state.StallTracker
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The diagnostics slice's delivery guards, driven through the real
 * [PlaybackSessionTestGraph] session:
 *
 *  - the BoostBufferSize arm must not leak across engines (the reset
 *    restores the default threshold BEFORE the reset snapshot copies it),
 *    and the threshold republishes from the CURRENT stall history on every
 *    stall evaluation — the 30 s boost floor only while the boost holds;
 *  - the frame-loss deltas EXPIRE: fresh evidence classifies decoder
 *    overload immediately, stale evidence (a quiet stats flow) cannot
 *    override a NETWORK_STARVATION classification;
 *  - the throughput snapshot never copies the EWMA estimate into the
 *    measured-throughput field;
 *  - the SwitchQuality recovery apply rides the Playback Mode sheet's full
 *    eligibility (local control, forced Direct Play, downloaded item) —
 *    blocked sessions keep their preference mirror untouched.
 *
 * Lane (ADR-0006 rule 2): the subject is commonMain
 * [com.raulshma.jellyplay.feature.player.video.PlaybackSession] +
 * SessionSlices, but the drivable graph (MockK doubles, the scheduler-less
 * Unconfined harness, the real-time frame-evidence TTL sleep) is a JVM-only
 * seam — the subject's lane wins.
 */
class DiagnosticsSliceGuardsTest {

    private fun graph(): PlaybackSessionTestGraph = PlaybackSessionTestGraph(
        hooks = mockk(relaxed = true),
        initialItem = "item-1",
        initialPlaySessionId = null,
    )

    private fun diagnostics(g: PlaybackSessionTestGraph): DiagnosticsSessionSlice =
        g.session.createSessionSlices().diagnostics ?: error("diagnostics slice missing")

    /** One stall edge: BUFFERING classifies, READY clears (the pair records one stall). */
    private fun stall(g: PlaybackSessionTestGraph) {
        g.engine.playbackState.value = EnginePlaybackState.BUFFERING
        g.engine.playbackState.value = EnginePlaybackState.READY
    }

    // ── F12 + F15: the boost never leaks; the threshold tracks the live window ──

    @Test
    fun engineReset_restoresDefaultThreshold_afterBoost() {
        val g = graph()
        val d = diagnostics(g)
        d.applyRecovery(RecoveryAction.BoostBufferSize)
        assertEquals(30_000L, d.rebufferThresholdMs.value)
        g.engineFlow.value = null
        // The reset restores the DEFAULT threshold and publishes it into the
        // reset buffer snapshot — the next engine starts unboosted.
        assertEquals(StallTracker.DEFAULT_REBUFFER_THRESHOLD_MS, d.rebufferThresholdMs.value)
        assertEquals(StallTracker.DEFAULT_REBUFFER_THRESHOLD_MS, d.bufferHealth.value.rebufferThresholdMs)
    }

    @Test
    fun boostFloor_holdsAcrossEscalation_whileBoostActive() {
        val g = graph()
        val d = diagnostics(g)
        d.applyRecovery(RecoveryAction.BoostBufferSize)
        stall(g)
        assertEquals(30_000L, d.rebufferThresholdMs.value)
        stall(g)
        // Second stall escalates the tracker (6 s) — the 30 s boost stays the floor.
        assertEquals(30_000L, d.rebufferThresholdMs.value)
    }

    @Test
    fun unboostedEscalation_publishesAfterBoostWasReset() {
        val g = graph()
        val d = diagnostics(g)
        d.applyRecovery(RecoveryAction.BoostBufferSize)
        g.engineFlow.value = null
        g.engineFlow.value = g.engine
        stall(g)
        stall(g)
        // The reset cleared the boost: the same two stalls publish the
        // tracker's 6 s escalation, not the previous engine's 30 s.
        assertEquals(StallTracker.ESCALATED_REBUFFER_THRESHOLD_MS_2, d.rebufferThresholdMs.value)
    }

    // ── F14: frame-loss evidence expires ─────────────────────────────────────

    @Test
    fun freshFrameEvidence_classifiesDecoderOverload_thenStaleEvidenceCannotOverrideStarvation() {
        val g = graph()
        val d = diagnostics(g)
        // One stats emission with a heavy recent drop ratio (300/400) and a
        // starving supply (1 Mbps estimate vs 4 Mbps stream).
        g.engine.videoStatsState.value = EngineVideoStats(
            estimatedBandwidthBps = 1_000_000L,
            videoBitrate = 4_000_000,
            droppedFrames = 300,
            totalVideoFrames = 400,
        )
        g.engine.playbackState.value = EnginePlaybackState.BUFFERING
        // Fresh evidence: the immediate decoder classification is preserved.
        assertEquals(StallCause.DECODER_OVERLOAD, d.stallCause.value)
        // Past the evidence-freshness window the stats flow has gone quiet:
        // the deltas no longer describe the CURRENT stall.
        Thread.sleep(2_300)
        g.engine.playbackState.value = EnginePlaybackState.READY
        g.engine.playbackState.value = EnginePlaybackState.BUFFERING
        // Stale deltas read as zero — the starvation verdict (0.25 throughput
        // ratio) survives instead of being overridden by the old drop ratio.
        assertEquals(StallCause.NETWORK_STARVATION, d.stallCause.value)
    }

    // ── F18: the estimate rides its own field ────────────────────────────────

    @Test
    fun throughputPublishesEstimateSeparately_measuredRateStaysUnset() {
        val g = graph()
        val d = diagnostics(g)
        g.engine.videoStatsState.value = EngineVideoStats(
            estimatedBandwidthBps = 8_000_000L,
            videoBitrate = 4_000_000,
        )
        val snapshot = d.throughput.value
        assertEquals(0L, snapshot.currentThroughputBps)
        assertEquals(8_000_000L, snapshot.estimatedBandwidthBps)
        assertEquals(4_000_000, snapshot.activeBitrateBps)
    }

    // ── F16: the SwitchQuality apply rides the sheet's full eligibility ──────

    @Test
    fun switchQualityApply_blockedUnderForcedDirectPlay() {
        val g = graph()
        val d = diagnostics(g)
        seedQuality(g, StreamingQuality.FHD_1080P)
        g.session.cachedAggregate = g.session.cachedAggregate.copy(
            playback = g.session.cachedAggregate.playback.copy(
                playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
            ),
        )
        d.applyRecovery(
            RecoveryAction.SwitchQuality(
                targetQuality = StreamingQuality.HD_720P,
                height = 720,
                bitrateBps = 3_000_000,
            ),
        )
        assertEquals(StreamingQuality.FHD_1080P, g.uiState.value.uiPrefs.streamingQuality)
    }

    @Test
    fun switchQualityApply_blockedForDownloadedItem() {
        val g = graph()
        val d = diagnostics(g)
        seedQuality(g, StreamingQuality.FHD_1080P)
        g.sessionStateFlow.value = g.sessionStateFlow.value.copy(isOffline = true)
        d.applyRecovery(
            RecoveryAction.SwitchQuality(
                targetQuality = StreamingQuality.HD_720P,
                height = 720,
                bitrateBps = 3_000_000,
            ),
        )
        assertEquals(StreamingQuality.FHD_1080P, g.uiState.value.uiPrefs.streamingQuality)
    }

    @Test
    fun switchQualityApply_dispatchesForEligibleOnlineItem() {
        val g = graph()
        val d = diagnostics(g)
        seedQuality(g, StreamingQuality.FHD_1080P)
        d.applyRecovery(
            RecoveryAction.SwitchQuality(
                targetQuality = StreamingQuality.HD_720P,
                height = 720,
                bitrateBps = 3_000_000,
            ),
        )
        assertEquals(StreamingQuality.HD_720P, g.uiState.value.uiPrefs.streamingQuality)
    }

    private fun seedQuality(g: PlaybackSessionTestGraph, quality: StreamingQuality) {
        g.uiState.update { it.copy(uiPrefs = it.uiPrefs.copy(streamingQuality = quality)) }
    }
}
