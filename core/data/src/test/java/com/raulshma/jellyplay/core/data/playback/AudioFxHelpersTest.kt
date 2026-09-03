package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.every
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * The `android.media.audiofx` objects are intercepted via `mockkConstructor`
 * (their real constructors run against Robolectric's `ShadowAudioEffect`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioFxHelpersTest {

    private val bassApplied = mutableListOf<Short>()
    private val virtualizerApplied = mutableListOf<Short>()
    private val reverbApplied = mutableListOf<Short>()
    private val gainApplied = mutableListOf<Int>()
    private val reverbEnabledCalls = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        mockkConstructor(BassBoost::class)
        every { anyConstructed<BassBoost>().setStrength(any()) } answers { bassApplied += firstArg<Short>() }
        every { anyConstructed<BassBoost>().enabled } returns false
        every { anyConstructed<BassBoost>().enabled = any() } just runs
        every { anyConstructed<BassBoost>().release() } just runs

        mockkConstructor(Virtualizer::class)
        every { anyConstructed<Virtualizer>().setStrength(any()) } answers { virtualizerApplied += firstArg<Short>() }
        every { anyConstructed<Virtualizer>().enabled } returns false
        every { anyConstructed<Virtualizer>().enabled = any() } just runs
        every { anyConstructed<Virtualizer>().release() } just runs

        mockkConstructor(PresetReverb::class)
        every { anyConstructed<PresetReverb>().setPreset(any()) } answers { reverbApplied += firstArg<Short>() }
        every { anyConstructed<PresetReverb>().enabled } returns false
        every { anyConstructed<PresetReverb>().enabled = any<Boolean>() } answers { reverbEnabledCalls += firstArg<Boolean>() }
        every { anyConstructed<PresetReverb>().release() } just runs

        mockkConstructor(LoudnessEnhancer::class)
        every { anyConstructed<LoudnessEnhancer>().setTargetGain(any()) } answers { gainApplied += firstArg<Int>() }
        every { anyConstructed<LoudnessEnhancer>().enabled } returns false
        every { anyConstructed<LoudnessEnhancer>().enabled = any() } just runs
        every { anyConstructed<LoudnessEnhancer>().release() } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── AudioFxHelper skeleton (via BassBoostHelper) ─────────────────────

    @Test
    fun `attach with UNSET session never opens an effect`() {
        val helper = BassBoostHelper()

        helper.attach(androidx.media3.common.C.AUDIO_SESSION_ID_UNSET)
        helper.setEnabled(true) // flag only, no effect to apply to

        verify(exactly = 0) { anyConstructed<BassBoost>().setStrength(any()) }
        verify(exactly = 0) { anyConstructed<BassBoost>().release() }
    }

    @Test
    fun `attach applies the current strength then the enabled flag`() {
        val helper = BassBoostHelper() // default strength MODERATE, enabled false

        helper.attach(audioSessionId = 42)

        verifyOrder {
            anyConstructed<BassBoost>().setStrength(700)
            anyConstructed<BassBoost>().enabled = false
        }
    }

    @Test
    fun `attaching the same session twice is idempotent`() {
        val helper = BassBoostHelper()

        helper.attach(audioSessionId = 42)
        helper.attach(audioSessionId = 42)

        assertEquals(1, bassApplied.size)
    }

    @Test
    fun `attaching a different session releases the prior effect first`() {
        val helper = BassBoostHelper()

        helper.attach(audioSessionId = 42)
        bassApplied.clear()
        helper.attach(audioSessionId = 43)

        verify(exactly = 1) { anyConstructed<BassBoost>().release() }
        assertEquals(1, bassApplied.size) // exactly one live effect was re-created
    }

    @Test
    fun `detach releases the effect so later enables apply nothing`() {
        val helper = BassBoostHelper()
        helper.attach(audioSessionId = 42)

        helper.detach()
        helper.setEnabled(true)

        verify(exactly = 1) { anyConstructed<BassBoost>().release() }
        verify(exactly = 1) { anyConstructed<BassBoost>().enabled = any() } // only from the original attach
        assertFalse(helper.isEnabled)
    }

    @Test
    fun `setEnabled flips the flag and applies it to the attached effect`() {
        val helper = BassBoostHelper()
        helper.attach(audioSessionId = 42)

        helper.setEnabled(true)
        helper.setEnabled(false)

        verifyOrder {
            anyConstructed<BassBoost>().enabled = true
            anyConstructed<BassBoost>().enabled = false
        }
    }

    @Test
    fun `attach after setEnabled(true) creates the effect already enabled`() {
        val helper = BassBoostHelper()
        helper.setEnabled(true)

        helper.attach(audioSessionId = 42)

        verifyOrder {
            anyConstructed<BassBoost>().setStrength(700)
            anyConstructed<BassBoost>().enabled = true
        }
        assertTrue(helper.isEnabled)
    }

    @Test
    fun `a throw while configuring the effect is contained and releases the native handle`() {
        every { anyConstructed<BassBoost>().setStrength(any()) } throws RuntimeException("boom")

        val helper = BassBoostHelper()
        helper.attach(audioSessionId = 42) // must not throw

        // createSafely released the half-configured handle.
        verify(exactly = 1) { anyConstructed<BassBoost>().release() }

        // The helper stays usable for the next attach.
        every { anyConstructed<BassBoost>().setStrength(any()) } answers { bassApplied += firstArg<Short>() }
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
            val helper = BassBoostHelper()
            helper.setStrength(strength)
            helper.attach(audioSessionId = 42)

            assertEquals(expectedMb.toShort(), bassApplied.last())
        }
    }

    @Test
    fun `BassBoost strength change while enabled retargets the live effect`() {
        val helper = BassBoostHelper()
        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)

        helper.setStrength(EffectStrength.HIGH)

        assertEquals(EffectStrength.HIGH, helper.strength)
        assertEquals(1000.toShort(), bassApplied.last())
    }

    // ── VirtualizerHelper coercion ───────────────────────────────────────

    @Test
    fun `Virtualizer strength is coerced into 0-1000`() {
        val helper = VirtualizerHelper()

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
        val helper = LoudnessEnhancerHelper()
        helper.setGain(3000)
        gainApplied.clear()

        helper.attach(audioSessionId = 42)
        helper.setEnabled(true)

        verifyOrder {
            anyConstructed<LoudnessEnhancer>().setTargetGain(3000) // create() applies initial state
            anyConstructed<LoudnessEnhancer>().setTargetGain(3000) // applyEnabled re-push on enable
            anyConstructed<LoudnessEnhancer>().enabled = true
        }
    }

    @Test
    fun `LoudnessEnhancer gain change while enabled forwards to the effect`() {
        val helper = LoudnessEnhancerHelper()
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
        val helper = ReverbHelper() // default preset NONE

        helper.attach(audioSessionId = 42)

        verify(exactly = 0) { anyConstructed<PresetReverb>().setPreset(any()) }
    }

    @Test
    fun `Reverb preset change while attached re-opens the effect with the new preset`() {
        val helper = ReverbHelper()
        helper.setPreset(ReverbPreset.SMALL_ROOM)
        helper.attach(audioSessionId = 7)
        assertEquals(1.toShort(), reverbApplied.last())

        helper.setPreset(ReverbPreset.MEDIUM_HALL)

        // detach + re-attach: the new preset is applied to a freshly opened effect
        // and the helper comes back up enabled.
        assertEquals(ReverbPreset.MEDIUM_HALL, helper.preset)
        verifyOrder {
            anyConstructed<PresetReverb>().setPreset(1)
            anyConstructed<PresetReverb>().setPreset(4)
            anyConstructed<PresetReverb>().enabled = true
        }
    }

    @Test
    fun `Reverb setPreset NONE while attached disables the effect`() {
        val helper = ReverbHelper()
        helper.setPreset(ReverbPreset.PLATE)
        helper.attach(audioSessionId = 7)

        helper.setPreset(ReverbPreset.NONE)

        assertFalse(helper.isEnabled)
        assertEquals(listOf(false, false), reverbEnabledCalls) // attach-time flag, then the NONE disable
    }
}
