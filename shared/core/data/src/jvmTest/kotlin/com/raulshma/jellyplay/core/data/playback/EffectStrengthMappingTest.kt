package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.EffectStrength
import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Tests [EffectStrengthMapping] — the single home for the night-mode
 * strength→gain (mB) and strength→volume-attenuation tables previously
 * duplicated in [NightModeHelper] and [AudioEffectsProcessor] (and at risk of
 * drifting further across the video engines).
 */
class EffectStrengthMappingTest {

    @Test
    fun `night mode gain table matches the documented values`() {
        assertEquals(0, EffectStrengthMapping.nightModeGainMb(EffectStrength.NONE))
        assertEquals(1500, EffectStrengthMapping.nightModeGainMb(EffectStrength.LOW))
        assertEquals(3000, EffectStrengthMapping.nightModeGainMb(EffectStrength.MODERATE))
        assertEquals(4500, EffectStrengthMapping.nightModeGainMb(EffectStrength.HIGH))
    }

    @Test
    fun `night mode gain is monotonic with strength`() {
        // Higher strength must boost more — a regression here would invert
        // night-mode behaviour (louder at NONE than at HIGH).
        val none = EffectStrengthMapping.nightModeGainMb(EffectStrength.NONE)
        val low = EffectStrengthMapping.nightModeGainMb(EffectStrength.LOW)
        val moderate = EffectStrengthMapping.nightModeGainMb(EffectStrength.MODERATE)
        val high = EffectStrengthMapping.nightModeGainMb(EffectStrength.HIGH)
        assert(none < low) { "NONE=$none should be < LOW=$low" }
        assert(low < moderate) { "LOW=$low should be < MODERATE=$moderate" }
        assert(moderate < high) { "MODERATE=$moderate should be < HIGH=$high" }
    }

    @Test
    fun `night mode volume attenuation table matches the documented values`() {
        assertEquals(1.0f, EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.NONE), 0.0001f)
        assertEquals(0.7f, EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.LOW), 0.0001f)
        assertEquals(0.4f, EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.MODERATE), 0.0001f)
        assertEquals(0.2f, EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.HIGH), 0.0001f)
    }

    @Test
    fun `night mode attenuation is monotonic decreasing with strength`() {
        // Higher strength attenuates more (compresses dynamic range harder).
        val none = EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.NONE)
        val low = EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.LOW)
        val moderate = EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.MODERATE)
        val high = EffectStrengthMapping.nightModeVolumeAttenuation(EffectStrength.HIGH)
        assert(none > low) { "NONE=$none should be > LOW=$low" }
        assert(low > moderate) { "LOW=$low should be > MODERATE=$moderate" }
        assert(moderate > high) { "MODERATE=$moderate should be > HIGH=$high" }
    }
}
