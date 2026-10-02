package com.raulshma.jellyplay.feature.player.video.engine.mpv

import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mpv `vf`-chain builder pin for BOTH mpv engines' video effects — the
 * shared-effect → mpv filter parity table in [MpvVideoEffectChain] is only as
 * good as these strings. Every case cites the Android MPV engine's
 * `applyVideoFilters` application path it mirrors (stage order, inclusion
 * rules, scalings). Pure functions: no mpv handle needed — the LIVE property
 * application is engine-tested in the desktop suite
 * (`MpvDesktopEngineVideoTest`). Moved from the desktop app's
 * `DesktopVideoEffectChainTest` when the builder became the shared contract
 * object (the chain body is pinned ONCE here, for both platforms).
 */
class MpvVideoEffectChainTest {

    private val neutral = VideoEffectsConfig()

    // ── empty chain ───────────────────────────────────────────────────────

    @Test
    fun neutralConfigBuildsNoChainAndNoRotation() {
        assertNull(MpvVideoEffectChain.buildVfChain(neutral))
        assertEquals(0, MpvVideoEffectChain.rotationDegrees(neutral))
    }

    // ── tonal stage (one eq filter carries every non-neutral knob) ────────

    @Test
    fun tonalKnobsCollapseIntoASingleEqStage() {
        val chain = MpvVideoEffectChain.buildVfChain(
            VideoEffectsConfig(brightness = 0.2f, contrast = 1.3f, saturation = 1.4f, hue = 90f),
        )!!
        assertEquals(
            "eq=brightness=0.20:contrast=1.30:saturation=1.40:hue=90.00",
            chain,
        )
    }

    @Test
    fun neutralTonalKnobsAreOmittedFromTheEqStage() {
        // Only brightness moved — contrast/saturation/hue stay at neutral and
        // must not pin the filter to their defaults (Android's exact rule).
        assertEquals(
            "eq=brightness=-0.50",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(brightness = -0.5f)),
        )
        assertEquals(
            "eq=saturation=0.00",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(saturation = 0f)),
        )
    }

    @Test
    fun rgbGainKnobsRideTheEqGammaParametersLikeAndroid() {
        // Android maps the channel GAIN sliders onto eq's per-channel GAMMA
        // parameters — the name lies on both platforms alike, kept for parity.
        assertEquals(
            "eq=gamma_r=1.10:gamma_g=0.90:gamma_b=1.20",
            MpvVideoEffectChain.buildVfChain(
                VideoEffectsConfig(redGain = 1.1f, greenGain = 0.9f, blueGain = 1.2f),
            ),
        )
    }

    // ── sharpness / blur ──────────────────────────────────────────────────

    @Test
    fun sharpnessMapsOntoUnsharpWithAndroidsScaling() {
        // Android: amount = sharpness × 1.5 clamped 0.5..3.0 over the fixed
        // 5:5 luma matrix — 1.0 → 1.5, half strength → 0.75, the 0..1 slider
        // saturates below 0.5.
        assertEquals(
            "unsharp=5:5:1.50",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 1f)),
        )
        assertEquals(
            "unsharp=5:5:0.75",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 0.5f)),
        )
        assertEquals(
            "unsharp=5:5:0.50",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 0.1f)),
        )
        assertNull(MpvVideoEffectChain.buildVfChain(neutral.copy(sharpness = 0f)))
    }

    @Test
    fun gaussianBlurHalvesIntoGblurSigma() {
        assertEquals(
            "lavfi=[gblur=sigma=2.00]",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(gaussianBlur = 4f)),
        )
        assertNull(MpvVideoEffectChain.buildVfChain(neutral.copy(gaussianBlur = 0f)))
    }

    // ── rotation (property, not a filter) ─────────────────────────────────

    @Test
    fun rotationRoundsToRightAnglesAndNormalizes() {
        assertEquals(90, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 90f)))
        // 45° rounds UP — kotlin.math ties go towards positive infinity,
        // the same rule as Android's kotlin.math.round-based snap.
        assertEquals(90, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 45f)))
        assertEquals(0, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 44f)))
        // Negatives normalize into mpv's 0..359 space.
        assertEquals(270, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = -90f)))
        assertEquals(180, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 180f)))
        assertEquals(0, MpvVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 360f)))
    }

    @Test
    fun rotationNeverEntersTheFilterChain() {
        val config = VideoEffectsConfig(rotationDegrees = 90f, brightness = 0.1f)
        val chain = MpvVideoEffectChain.buildVfChain(config)!!
        assertTrue(!chain.contains("rotate") && !chain.contains("transpose"), chain)
        assertEquals("eq=brightness=0.10", chain)
    }

    // ── stage order (eq → unsharp → gblur, Android's exact order) ────────

    @Test
    fun fullStackOrdersTonalSharpenBlur() {
        val stages = MpvVideoEffectChain.buildVfChain(
            VideoEffectsConfig(
                brightness = 0.2f,
                sharpness = 1f,
                gaussianBlur = 2f,
            ),
        )!!.split(",")
        assertEquals(3, stages.size, stages.toString())
        assertTrue(stages[0].startsWith("eq="), stages.toString())
        assertTrue(stages[1].startsWith("unsharp="), stages.toString())
        assertTrue(stages[2].startsWith("lavfi=[gblur="), stages.toString())
    }

    // ── the locale-independent two-decimal format ─────────────────────────
    //
    // The desktop builder's former "Locale.ROOT %.2f" spelling is the
    // formatting contract the moved chain must keep byte-identical WITHOUT
    // java.util.Locale. Java's formatter rounds the float's EXACT binary
    // expansion HALF_UP — not its shortest toString digits — and these pins
    // distinguish exactly that behavior (`0.145f → 0.14` while
    // `0.075f → 0.08` is impossible for any toString-then-round scheme).

    @Test
    fun fmtIsByteIdenticalToTheFormerLocaleRootFormat() {
        // 0.145f's exact expansion is 0.144999… → DOWN, while 0.075f's is
        // 0.075000003 → UP — a toString-then-round scheme cannot produce
        // this pair (both shortest reprs end in a bare '5').
        assertEquals("eq=brightness=0.14", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(brightness = 0.145f)))
        assertEquals("eq=hue=0.08", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(hue = 0.075f)))
        assertEquals("eq=brightness=-0.86", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(brightness = -0.855f)))
        // Exact binary ties round AWAY FROM ZERO (Java HALF_UP): 1.125f is
        // exactly representable → "1.13".
        assertEquals("eq=saturation=1.13", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(saturation = 1f + 0.125f)))
        assertEquals("eq=hue=2.67", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(hue = 2.675f)))
        // Sign kept on a magnitude that rounds to zero (Java's -0.00 rule);
        // positive zero renders as plain "0.00".
        assertEquals(
            "eq=brightness=-0.00",
            MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(brightness = -0.001f)),
        )
        assertEquals("eq=saturation=0.00", MpvVideoEffectChain.buildVfChain(VideoEffectsConfig(saturation = 0.001f)))
    }
}
