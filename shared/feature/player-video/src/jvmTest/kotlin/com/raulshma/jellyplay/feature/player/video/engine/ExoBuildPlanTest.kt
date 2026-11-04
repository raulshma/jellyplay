package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.ExoBufferPosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins for the Exo rebuild's build-plan fold ([ExoBuildPlan.of] over
 * [ExoBuildInputs], commonMain): the passthrough-vs-in-sink sink split, the
 * decoder-mode → extension-renderer-mode mapping, the ASS session verdict
 * with its extractor pairing co-derivation, and the load-control derivation
 * (the back-buffer clamp, the verbatim -1 target sentinel, and the
 * historical literals riding as defaulted inputs). The androidMain engine
 * executes the plan as Exo API assembly and is not unit-testable; this is
 * the pin that the verdict ladder cannot drift.
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
        // no literal lives in the plan anymore — the historical numbers ride
        // as the posture's defaults, so a default-input plan reproduces the
        // historical posture and the executor carries no numbers of its own.
        assertEquals(ExoBufferPosture(), loadControl.bufferPosture)
    }

    @Test
    fun loadControl_startThresholdsTargetAndRetain_flowThroughFromInputs() {
        // Every load-control number must trace to an input: explicit inputs
        // land verbatim in the plan, no default substitution anywhere.
        val posture = ExoBufferPosture(
            initialBufferMs = 2_500,
            rebufferBufferMs = 7_500,
            targetBufferBytes = 64 * 1024 * 1024,
            retainBackBuffer = true,
        )
        val plan = ExoBuildPlan.of(baseline.copy(bufferPosture = posture))

        assertEquals(posture, plan.loadControl.bufferPosture)
    }

    @Test
    fun targetBufferBytesSentinel_roundTripsVerbatim() {
        // -1 is Media3's C.LENGTH_UNSET sentinel ("derive the target from
        // the selected tracks"): it must survive the fold untouched — the
        // back-buffer clamp must NOT generalize to it, or "auto" would
        // silently become "0 bytes".
        assertEquals(
            ExoBufferPosture(targetBufferBytes = -1),
            ExoBuildPlan.of(
                baseline.copy(bufferPosture = ExoBufferPosture(targetBufferBytes = -1)),
            ).loadControl.bufferPosture,
        )
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
