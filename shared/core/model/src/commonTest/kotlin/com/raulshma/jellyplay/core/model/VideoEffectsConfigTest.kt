package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the invariants of [VideoEffectsConfig.isNeutral] — the "reset to
 * defaults / skip filter chain" predicate:
 *
 *  - A default-constructed config is neutral.
 *  - Perturbing ANY single filter away from its neutral value makes the config
 *    non-neutral (no field may be silently ignored by the predicate).
 *  - Restoring that field restores neutrality (the predicate reads all fields,
 *    not a dirty flag).
 *
 * Neutral values mirror the FFmpeg/mpv/VLC parameter defaults documented on
 * the class: brightness 0, contrast 1, saturation 1, sharpness 0, hue 0,
 * rotation 0, RGB gains 1, gaussianBlur 0.
 */
class VideoEffectsConfigTest {

    @Test
    fun `default config is neutral`() {
        assertTrue(VideoEffectsConfig().isNeutral)
    }

    @Test
    fun `perturbing any single field breaks neutrality`() {
        val perturbations: List<VideoEffectsConfig> = listOf(
            VideoEffectsConfig(brightness = 0.1f),
            VideoEffectsConfig(contrast = 1.1f),
            VideoEffectsConfig(saturation = 0.9f),
            VideoEffectsConfig(sharpness = 0.2f),
            VideoEffectsConfig(hue = 90f),
            VideoEffectsConfig(rotationDegrees = 90f),
            VideoEffectsConfig(redGain = 1.5f),
            VideoEffectsConfig(greenGain = 0.5f),
            VideoEffectsConfig(blueGain = 2f),
            VideoEffectsConfig(gaussianBlur = 1f),
        )
        perturbations.forEachIndexed { index, config ->
            assertFalse(config.isNeutral, "perturbation #$index reported neutral")
        }
    }

    @Test
    fun `restoring the perturbed field restores neutrality`() {
        val config = VideoEffectsConfig(contrast = 1.4f)
        assertFalse(config.isNeutral)
        assertTrue(config.copy(contrast = 1f).isNeutral)
    }

    @Test
    fun `neutral-identical non-default construction is still neutral`() {
        // An explicitly-constructed config with the neutral values equals the
        // default and must read as neutral (value semantics, not provenance).
        val config = VideoEffectsConfig(
            brightness = 0f,
            contrast = 1f,
            saturation = 1f,
            sharpness = 0f,
            hue = 0f,
            rotationDegrees = 0f,
            redGain = 1f,
            greenGain = 1f,
            blueGain = 1f,
            gaussianBlur = 0f,
        )
        assertEquals(VideoEffectsConfig(), config)
        assertTrue(config.isNeutral)
    }
}
