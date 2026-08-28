package com.raulshma.jellyplay.desktop.player

import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 17B: the mpv `vf`-chain builder pin for the desktop video effects —
 * the shared-effect → mpv filter parity table in [DesktopVideoEffectChain] is
 * only as good as these strings. Every case cites the Android MPV engine's
 * `applyVideoFilters` application path it mirrors (stage order, inclusion
 * rules, scalings). Pure functions: no mpv handle needed — the LIVE property
 * application is engine-tested in [MpvDesktopEngineVideoTest].
 */
class DesktopVideoEffectChainTest {

    private val neutral = VideoEffectsConfig()

    // ── empty chain ───────────────────────────────────────────────────────

    @Test
    fun neutralConfigBuildsNoChainAndNoRotation() {
        assertNull(DesktopVideoEffectChain.buildVfChain(neutral))
        assertEquals(0, DesktopVideoEffectChain.rotationDegrees(neutral))
    }

    // ── tonal stage (one eq filter carries every non-neutral knob) ────────

    @Test
    fun tonalKnobsCollapseIntoASingleEqStage() {
        val chain = DesktopVideoEffectChain.buildVfChain(
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
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(brightness = -0.5f)),
        )
        assertEquals(
            "eq=saturation=0.00",
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(saturation = 0f)),
        )
    }

    @Test
    fun rgbGainKnobsRideTheEqGammaParametersLikeAndroid() {
        // Android maps the channel GAIN sliders onto eq's per-channel GAMMA
        // parameters — the name lies on both platforms alike, kept for parity.
        assertEquals(
            "eq=gamma_r=1.10:gamma_g=0.90:gamma_b=1.20",
            DesktopVideoEffectChain.buildVfChain(
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
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 1f)),
        )
        assertEquals(
            "unsharp=5:5:0.75",
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 0.5f)),
        )
        assertEquals(
            "unsharp=5:5:0.50",
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(sharpness = 0.1f)),
        )
        assertNull(DesktopVideoEffectChain.buildVfChain(neutral.copy(sharpness = 0f)))
    }

    @Test
    fun gaussianBlurHalvesIntoGblurSigma() {
        assertEquals(
            "lavfi=[gblur=sigma=2.00]",
            DesktopVideoEffectChain.buildVfChain(VideoEffectsConfig(gaussianBlur = 4f)),
        )
        assertNull(DesktopVideoEffectChain.buildVfChain(neutral.copy(gaussianBlur = 0f)))
    }

    // ── rotation (property, not a filter) ─────────────────────────────────

    @Test
    fun rotationRoundsToRightAnglesAndNormalizes() {
        assertEquals(90, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 90f)))
        // 45° rounds UP — kotlin.math ties go towards positive infinity,
        // the same rule as Android's kotlin.math.round-based snap.
        assertEquals(90, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 45f)))
        assertEquals(0, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 44f)))
        // Negatives normalize into mpv's 0..359 space.
        assertEquals(270, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = -90f)))
        assertEquals(180, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 180f)))
        assertEquals(0, DesktopVideoEffectChain.rotationDegrees(neutral.copy(rotationDegrees = 360f)))
    }

    @Test
    fun rotationNeverEntersTheFilterChain() {
        val config = VideoEffectsConfig(rotationDegrees = 90f, brightness = 0.1f)
        val chain = DesktopVideoEffectChain.buildVfChain(config)!!
        assertTrue(!chain.contains("rotate") && !chain.contains("transpose"), chain)
        assertEquals("eq=brightness=0.10", chain)
    }

    // ── stage order (eq → unsharp → gblur, Android's exact order) ────────

    @Test
    fun fullStackOrdersTonalSharpenBlur() {
        val stages = DesktopVideoEffectChain.buildVfChain(
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
}
