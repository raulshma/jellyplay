package com.raulshma.jellyplay.feature.player.video.engine

import com.raulshma.jellyplay.core.model.DecoderMode

/**
 * The rebuild-path inputs the build decisions read. Every field is a plain
 * value (core.model enums + scalars — the [LoadRebuildInputs] pattern), so
 * the plan fold over it stays commonMain and jvmTest-pinnable with no Media3
 * type crossing the boundary; the androidMain engine owns the input reads
 * and the Exo API assembly.
 */
internal data class ExoBuildInputs(
    val decoderMode: DecoderMode,
    val audioPassthrough: Boolean,
    val assSession: Boolean,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val backBufferDurationMs: Int,
)

/**
 * The ordered build decisions of the Exo rebuild path as one pure plan (the
 * `MpvLoadOptionsPlan` split: the plan owns the DECISIONS, the engine owns
 * only the transport — [ExoPlayerEngine.load] executes it as a `when`-per-
 * verdict over Exo API assembly). Four independent verdicts:
 *
 *  - [sinkShape] — the construction-time passthrough-vs-in-sink split. The
 *    renderer set (and its audio sink) is fixed at player build, so the
 *    verdict rides the build: a mid-session passthrough flip applies on the
 *    next rebuild (the mode/decoder reload path), while the track-selection
 *    half re-pushes live (see [ExoPassthroughOffload.offloadMode]).
 *  - [assSession] — whether the libass (ass-media) overlay path engages.
 *  - [extractorPairing] — the ASS-aware vs plain extractor set; derived from
 *    the same input as [assSession] inside [of], so the two cannot disagree.
 *  - [loadControl] — the buffer numbers, with the back-buffer clamp folded.
 */
internal data class ExoBuildPlan(
    val extensionRendererMode: ExtensionRendererMode,
    val sinkShape: SinkShape,
    val assSession: AssSessionVerdict,
    val extractorPairing: ExtractorPairing,
    val loadControl: LoadControlNumbers,
) {
    /**
     * The decoder-mode → extension-renderer-mode mapping, spelled as the
     * three media3 constants the executor feeds [DefaultRenderersFactory]
     * (plain enum here so no Media3 type crosses into commonMain).
     */
    enum class ExtensionRendererMode { ON, OFF, PREFER }

    /**
     * The audio-sink shape the renderers factory builds. A processor-free
     * sink is OFFLOAD-CAPABLE in media3 1.11 (per-format offload support is
     * resolved intrinsically); audio processors force the decode-to-PCM path
     * and are incompatible with offload — the passthrough session forgoes
     * the in-sink DSP, which is the point of bitstreaming.
     */
    sealed interface SinkShape {
        /** Bitstream passthrough: the bare sink, no PCM in-sink chain. */
        data object PassthroughBitstream : SinkShape

        /** Decode-to-PCM: the shared `inSinkAudioChain` (channel mix first). */
        data object InSinkProcessorChain : SinkShape
    }

    /**
     * The libass (ass-media) session verdict: only an ASS/SSA subtitle
     * source engages the libass path; non-ASS sessions keep the native
     * DefaultSubtitleParserFactory path untouched.
     */
    sealed interface AssSessionVerdict {
        /**
         * libass engages: the AssHandler is constructed (canvas renderer
         * pre-P, OpenGL after), the parser chain is the
         * AssStyleOverrideParserFactory (FORCE-mode header rewrite) wrapping
         * the concrete AssSubtitleParserFactory, and the extractors pair
         * with [ExtractorPairing.AssAwareExtractors].
         *
         * KNOWN LIMITATION: embedded ASS-in-MKV extraction still bypasses the
         * OffsettingSubtitleParserFactory — the subtitle-delay slider has no
         * effect on embedded MKV ASS tracks in this engine. Preserved from the
         * previous wiring (which passed the bare concrete factory) rather than
         * destabilizing the working ASS path; mpv and libVLC render embedded
         * ASS at the correct offset via sub-delay/setSpuDelay regardless.
         */
        data object LibassEngages : AssSessionVerdict

        /** The plain native path — byte-identical to the pre-ASS build. */
        data object Discharges : AssSessionVerdict
    }

    /**
     * The extractor-set pairing for the media source factory: the ASS-aware
     * Matroska wrappers only exist on the libass arm.
     */
    sealed interface ExtractorPairing {
        /** ASS-aware extractors (Matroska + ASS, style-override wrapper). */
        data object AssAwareExtractors : ExtractorPairing

        /** The plain [DefaultExtractorsFactory]-equivalent set. */
        data object PlainExtractors : ExtractorPairing
    }

    companion object {
        /** The total fold: every input set yields a plan; nothing defaults silently. */
        fun of(inputs: ExoBuildInputs): ExoBuildPlan = ExoBuildPlan(
            extensionRendererMode = when (inputs.decoderMode) {
                DecoderMode.HW_PREFERRED -> ExtensionRendererMode.ON
                DecoderMode.HW_ONLY -> ExtensionRendererMode.OFF
                DecoderMode.SW_ONLY -> ExtensionRendererMode.PREFER
            },
            sinkShape = if (inputs.audioPassthrough) {
                SinkShape.PassthroughBitstream
            } else {
                SinkShape.InSinkProcessorChain
            },
            assSession = if (inputs.assSession) {
                AssSessionVerdict.LibassEngages
            } else {
                AssSessionVerdict.Discharges
            },
            extractorPairing = if (inputs.assSession) {
                ExtractorPairing.AssAwareExtractors
            } else {
                ExtractorPairing.PlainExtractors
            },
            loadControl = LoadControlNumbers(
                minBufferMs = inputs.minBufferMs,
                maxBufferMs = inputs.maxBufferMs,
                backBufferDurationMs = inputs.backBufferDurationMs.coerceAtLeast(0),
            ),
        )
    }
}

/**
 * The [DefaultLoadControl] numbers for one rebuild: the request's buffer
 * window plus the engine's back-buffer posture. The historical literals ride
 * as defaulted fields so the executor carries no numbers of its own.
 */
internal data class LoadControlNumbers(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int = 1_000,
    val bufferForPlaybackAfterRebufferMs: Int = 3_000,
    val targetBufferBytes: Int = -1,
    val backBufferDurationMs: Int,
    val retainBackBuffer: Boolean = false,
)
