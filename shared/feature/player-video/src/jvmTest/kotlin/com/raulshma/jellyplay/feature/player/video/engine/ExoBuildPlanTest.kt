package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pins for the Exo rebuild's build-plan fold ([ExoBuildPlan.of] over
 * [ExoBuildInputs], commonMain): the passthrough-vs-in-sink sink split, the
 * decoder-mode → extension-renderer-mode mapping, the ASS session verdict
 * with its extractor pairing co-derivation, and the load-control derivation
 * (including the back-buffer clamp). The androidMain engine executes the
 * plan as Exo API assembly and is not unit-testable; this is the pin that
 * the verdict ladder cannot drift.
 */
class ExoBuildPlanTest {

    private val baseline = ExoBuildInputs(
        decoderMode = DecoderMode.HW_PREFERRED,
        audioPassthrough = false,
        assSession = false,
        minBufferMs = 50_000,
        maxBufferMs = 90_000,
        backBufferDurationMs = 25_000,
    )

    @Test
    fun passthroughRequested_buildsTheBareBitstreamSink() {
        val plan = ExoBuildPlan.of(baseline.copy(audioPassthrough = true))

        assertIs<ExoBuildPlan.SinkShape.PassthroughBitstream>(plan.sinkShape)
    }

    @Test
    fun passthroughNotRequested_buildsTheInSinkProcessorChain() {
        val plan = ExoBuildPlan.of(baseline)

        assertIs<ExoBuildPlan.SinkShape.InSinkProcessorChain>(plan.sinkShape)
    }

    @Test
    fun decoderMode_mapsOntoTheExtensionRendererModeLadder() {
        // HW_PREFERRED keeps extensions available, HW_ONLY sheds them,
        // SW_ONLY prefers them — the platform decoder posture rides the
        // renderers factory set at build.
        assertEquals(
            ExoBuildPlan.ExtensionRendererMode.ON,
            ExoBuildPlan.of(baseline).extensionRendererMode,
        )
        assertEquals(
            ExoBuildPlan.ExtensionRendererMode.OFF,
            ExoBuildPlan.of(baseline.copy(decoderMode = DecoderMode.HW_ONLY)).extensionRendererMode,
        )
        assertEquals(
            ExoBuildPlan.ExtensionRendererMode.PREFER,
            ExoBuildPlan.of(baseline.copy(decoderMode = DecoderMode.SW_ONLY)).extensionRendererMode,
        )
    }

    @Test
    fun assSource_engagesLibass_andPairsTheAssAwareExtractors() {
        val plan = ExoBuildPlan.of(baseline.copy(assSession = true))

        assertIs<ExoBuildPlan.AssSessionVerdict.LibassEngages>(plan.assSession)
        assertIs<ExoBuildPlan.ExtractorPairing.AssAwareExtractors>(plan.extractorPairing)
    }

    @Test
    fun noAssSource_dischargesLibass_andKeepsThePlainExtractors() {
        val plan = ExoBuildPlan.of(baseline)

        assertIs<ExoBuildPlan.AssSessionVerdict.Discharges>(plan.assSession)
        assertIs<ExoBuildPlan.ExtractorPairing.PlainExtractors>(plan.extractorPairing)
    }

    @Test
    fun extractorPairing_isCoDerivedFromTheAssInput_bothArms() {
        // The pairing derives from the same input as the session verdict
        // inside of() — no input set can produce a mixed plan.
        listOf(true, false).forEach { ass ->
            val plan = ExoBuildPlan.of(baseline.copy(assSession = ass))
            val engaged = plan.assSession is ExoBuildPlan.AssSessionVerdict.LibassEngages
            val aware = plan.extractorPairing is ExoBuildPlan.ExtractorPairing.AssAwareExtractors
            assertEquals(engaged, aware, "assSession=$ass must pair the extractors with the verdict")
        }
    }

    @Test
    fun loadControl_carriesTheRequestWindow_andTheHistoricalLiterals() {
        val plan = ExoBuildPlan.of(baseline.copy(minBufferMs = 10_000, maxBufferMs = 30_000))

        val loadControl = plan.loadControl
        assertEquals(10_000, loadControl.minBufferMs)
        assertEquals(30_000, loadControl.maxBufferMs)
        // The DefaultLoadControl start thresholds and the unbounded target:
        // the executor carries no numbers of its own.
        assertEquals(1_000, loadControl.bufferForPlaybackMs)
        assertEquals(3_000, loadControl.bufferForPlaybackAfterRebufferMs)
        assertEquals(-1, loadControl.targetBufferBytes)
        assertEquals(false, loadControl.retainBackBuffer)
    }

    @Test
    fun negativeBackBufferDuration_clampsToZero() {
        // A mis-configured store value must not hand DefaultLoadControl a
        // negative back buffer.
        assertEquals(
            0,
            ExoBuildPlan.of(baseline.copy(backBufferDurationMs = -1)).loadControl.backBufferDurationMs,
        )
        assertEquals(
            25_000,
            ExoBuildPlan.of(baseline).loadControl.backBufferDurationMs,
        )
    }

    @Test
    fun verdictsAreIndependent_passthroughAndAssCompose() {
        // The four verdicts are independent axes: a passthrough ASS session
        // carries the bitstream sink AND the libass path.
        val plan = ExoBuildPlan.of(baseline.copy(audioPassthrough = true, assSession = true))

        assertIs<ExoBuildPlan.SinkShape.PassthroughBitstream>(plan.sinkShape)
        assertIs<ExoBuildPlan.AssSessionVerdict.LibassEngages>(plan.assSession)
    }
}
