package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the [AudioFxHelper] lifecycle skeleton (shared by BassBoost /
 * Virtualizer / PresetReverb / LoudnessEnhancer) plus each wrapper's
 * effect-specific state:
 *
 * - `attach(UNSET)` never opens an effect; attaching the same session twice is
 *   idempotent; attaching a different session releases the prior effect first
 *   (never two live effects); `detach` releases and clears.
 * - Creation applies the wrapper's current state, then the remembered enabled
 *   flag — so `attach` after `setEnabled(true)` comes up enabled.
 * - A throw during construction is contained: `createSafely` releases the
 *   native handle, `attach` logs instead of throwing, and the helper stays
 *   usable for the next attach.
 * - [BassBoostHelper] maps EffectStrength → millibels (NONE=0, LOW=400,
 *   MODERATE=700, HIGH=1000); [VirtualizerHelper] coerces 0..1000;
 *   [LoudnessEnhancerHelper] re-pushes its gain when enabled; [ReverbHelper]
 *   skips attach at preset NONE and re-opens the effect on preset change.
 *
 * The `android.media.audiofx` effect objects are supplied through each
 * helper's `effectFactory` constructor seam returning a plain mock:
 * `mockkConstructor` on Robolectric-shadowed framework classes intercepts
 * unreliably — shadowed native methods such as `release()` bypass the mock and
 * Robolectric's shadow answers instead, and `setEnabled`'s `int` return type
 * clashes with a `just runs` stub (Unit cannot unbox to the status int).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFxHelpersTest {

    private val bassApplied = mutableListOf<Short>()
    private val virtualizerApplied = mutableListOf<Short>()
    private val reverbApplied = mutableListOf<Short>()
    private val gainApplied = mutableListOf<Int>()
    private val reverbEnabledCalls = mutableListOf<Boolean>()

    private fun bassFx(): BassBoost {
        val fx = mockk<BassBoost>(relaxed = true)
        every { fx.enabled } returns false
        every { fx.setEnabled(any()) } answers { AudioEffect.SUCCESS.toInt() }
        every { fx.setStrength(any()) } answers { bassApplied += firstArg<Short>(); AudioEffect.SUCCESS.toInt() }
        every { fx.release() } just runs
        return fx
    }

    private fun virtualizerFx(): Virtualizer {
        val fx = mockk<Virtualizer>(relaxed = true)
        every { fx.enabled } returns false
        every { fx.setEnabled(any()) } answers { AudioEffect.SUCCESS.toInt() }
        every { fx.setStrength(any()) } answers { virtualizerApplied += firstArg<Short>(); AudioEffect.SUCCESS.toInt() }
        every { fx.release() } just runs
        return fx
    }

    private fun reverbFx(): PresetReverb {
        val fx = mockk<PresetReverb>(relaxed = true)
        every { fx.enabled } returns false
        every { fx.setEnabled(any()) } answers {
            reverbEnabledCalls += firstArg<Boolean>()
            AudioEffect.SUCCESS.toInt()
        }
        every { fx.setPreset(any()) } answers { reverbApplied += firstArg<Short>(); AudioEffect.SUCCESS.toInt() }
        every { fx.release() } just runs
        return fx
    }

    private fun loudnessFx(): LoudnessEnhancer {
        val fx = mockk<LoudnessEnhancer>(relaxed = true)
        every { fx.enabled } returns false
        every { fx.setEnabled(any()) } answers { AudioEffect.SUCCESS.toInt() }
        every { fx.setTargetGain(any()) } answers { gainApplied += firstArg<Int>(); AudioEffect.SUCCESS.toInt() }
        every { fx.release() } just runs
        return fx
    }

    @After
    fun tearDown() {
        bassApplied.clear()
        virtualizerApplied.clear()
        reverbApplied.clear()
        gainApplied.clear()
        reverbEnabledCalls.clear()
    }

    // ── AudioFxHelper skeleton (via BassBoostHelper) ─────────────────────

    @Test
    fun `attach with UNSET session never opens an effect`() {
        val helper = BassBoostHelper(effectFactory = { bassFx() })

        helper.attach(androidx.media3.common.C.AUDIO_SESSION_ID_UNSET)
        helper.setEnabled(true) // flag only, no effect to apply to

        assertEquals(0, bassApplied.size)
        assertTrue(helper.isEnabled)
    }

    @Test
    fun `attach applies the current strength then the enabled flag`() {
        val fx = bassFx()
        val helper = BassBoostHelper(effectFactory = { fx }) // default strength MODERATE, enabled false

        helper.attach(audioSessionId = 42)

        verifyOrder {
            fx.setStrength(700)
            fx.setEnabled(false)
        }
    }

    @Test
    fun `attaching the same session twice is idempotent`() {
        val helper = BassBoostHelper(effectFactory = { bassFx() })

        helper.attach(audioSessionId = 42)
        helper.attach(audioSessionId = 42)

        assertEquals(1, bassApplied.size)
    }

    @Test
    fun `attaching a different session releases the prior effect first`() {
        val fx = bassFx()
        val helper = BassBoostHelper(effectFactory = { fx })

        helper.attach(audioSessionId = 42)
        bassApplied.clear()
        helper.attach(audioSessionId = 43)

        verify(exactly = 1) { fx.release() }
        assertEquals(1, bassApplied.size) // exactly one live effect was re-created
    }

    @Test
    fun `detach releases the effect so later enables apply nothing`() {
        val fx = bassFx()
        val helper = BassBoostHelper(effectFactory = { fx })
        helper.attach(audioSessionId = 42)

        helper.detach()
        helper.setEnabled(true)

        verify(exactly = 1) { fx.release() }
        verify(exactly = 1) { fx.setEnabled(any()) } // only from the original attach
        // `isEnabled` is the remembered flag (it must survive detach so the
        // next attach comes up enabled); setEnabled(true) above set it again.
        assertTrue(helper.isEnabled)
    }

    @Test
    fun `setEnabled flips the flag and applies it to the attached effect`() {
        val fx = bassFx()
        val helper = BassBoostHelper(effectFactory = { fx })
        helper.attach(audioSessionId = 42)

        helper.setEnabled(true)
        helper.setEnabled(false)

        verifyOrder {
            fx.setEnabled(true)
            fx.setEnabled(false)
        }
    }

    @Test
    fun `attach after setEnabled(true) creates the effect already enabled`() {
        val fx = bassFx()
        val helper = BassBoostHelper(effectFactory = { fx })
        helper.setEnabled(true)

        helper.attach(audioSessionId = 42)

        verifyOrder {
            fx.setStrength(700)
            fx.setEnabled(true)
        }
        assertTrue(helper.isEnabled)
    }

    @Test
    fun `a throw while configuring the effect is contained and releases the native handle`() {
        val fx = bassFx()
        every { fx.setStrength(any()) } throws RuntimeException("boom")

        val helper = BassBoostHelper(effectFactory = { fx })
        helper.attach(audioSessionId = 42) // must not throw

        // createSafely released the half-configured handle.
        verify(exactly = 1) { fx.release() }

        // The helper stays usable for the next attach.
        every { fx.setStrength(any()) } answers { bassApplied += firstArg<Short>(); AudioEffect.SUCCESS.toInt() }
        helper.attach(audioSessionId = 43)
        assertEquals(1, bassApplied.size)
    }

    // ── BassBoostHelper strength table ───────────────────────────────────

    @Test
    fun `BassBoost strength mapping matches the pinned millibel table`() {
        listOf(
            EffectStrength.NONE to 0,
            EffectStrength.LOW to 400,
            EffectStrength.MODERATE to 700,
            EffectStrength.HIGH to 1000,
        ).forEach { (strength, expectedMb) ->
            val helper = BassBoostHelper(effectFactory = { bassFx() })
            helper.setStrength(strength)
            helper.attach(audioSessionId = 42)

            assertEquals(expectedMb.toShort(), bassApplied.last())
            bassApplied.clear()
        }
    }

    @Test
    fun `BassBoost strength change while enabled retargets the live effect`() {
        val helper = BassBoostHelper(effectFactory = { bassFx() })
        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)

        helper.setStrength(EffectStrength.HIGH)

        assertEquals(EffectStrength.HIGH, helper.strength)
        assertEquals(1000.toShort(), bassApplied.last())
    }

    // ── VirtualizerHelper coercion ───────────────────────────────────────

    @Test
    fun `Virtualizer strength is coerced into 0-1000`() {
        val helper = VirtualizerHelper(effectFactory = { virtualizerFx() })

        helper.setStrength(1500)
        assertEquals(1000, helper.strength)
        helper.setStrength(-5)
        assertEquals(0, helper.strength)

        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)
        helper.setStrength(700)

        assertEquals(700.toShort(), virtualizerApplied.last())
    }

    // ── LoudnessEnhancerHelper gain re-push ──────────────────────────────

    @Test
    fun `LoudnessEnhancer attach pushes the stored gain and enabling re-pushes it`() {
        val fx = loudnessFx()
        val helper = LoudnessEnhancerHelper(effectFactory = { fx })
        helper.setGain(3000)
        gainApplied.clear()

        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)

        verifyOrder {
            fx.setTargetGain(3000) // create() applies initial state
            fx.setTargetGain(3000) // applyEnabled re-push on enable
            fx.setEnabled(true)
        }
    }

    @Test
    fun `LoudnessEnhancer gain change while enabled forwards to the effect`() {
        val helper = LoudnessEnhancerHelper(effectFactory = { loudnessFx() })
        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)
        gainApplied.clear()

        helper.setGain(-600)

        assertEquals(-600, helper.gainmB)
        assertEquals(listOf(-600), gainApplied)
    }

    // ── ReverbHelper preset handling ─────────────────────────────────────

    @Test
    fun `Reverb attach with preset NONE skips opening the effect`() {
        val helper = ReverbHelper(effectFactory = { reverbFx() }) // default preset NONE

        helper.attach(audioSessionId = 42)

        assertEquals(0, reverbApplied.size)
    }

    @Test
    fun `Reverb preset change while attached re-opens the effect with the new preset`() {
        val fx = reverbFx()
        val helper = ReverbHelper(effectFactory = { fx })
        helper.setPreset(ReverbPreset.SMALL_ROOM)
        helper.attach(audioSessionId = 7)
        assertEquals(1.toShort(), reverbApplied.last())

        helper.setPreset(ReverbPreset.MEDIUM_HALL)

        // detach + re-attach: the new preset is applied to a freshly opened effect
        // and the helper comes back up enabled.
        assertEquals(ReverbPreset.MEDIUM_HALL, helper.preset)
        verifyOrder {
            fx.setPreset(1)
            fx.setPreset(4)
            fx.setEnabled(true)
        }
    }

    @Test
    fun `Reverb setPreset NONE while attached disables the effect`() {
        val helper = ReverbHelper(effectFactory = { reverbFx() })
        helper.setPreset(ReverbPreset.PLATE)
        helper.attach(audioSessionId = 7)

        helper.setPreset(ReverbPreset.NONE)

        assertFalse(helper.isEnabled)
        assertEquals(listOf(false, false), reverbEnabledCalls) // attach-time flag, then the NONE disable
    }
}
