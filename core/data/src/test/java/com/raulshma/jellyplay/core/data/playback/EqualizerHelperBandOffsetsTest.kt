package com.raulshma.jellyplay.core.data.playback

import android.media.audiofx.Equalizer
import com.raulshma.jellyplay.core.model.EqualizerSettings
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the additive band-offset formula in [EqualizerHelper] — the seam that
 * lets [DialogueBoostHelper] ride on the user's EQ without opening a second
 * priority-0 `android.media.audiofx.Equalizer` (there is exactly one per audio
 * session). At apply time, band `i` receives
 *
 * ```
 * mB(i) = (userLevel_dB(i) * 100 + offset(i)).coerceIn(minLevel, maxLevel)
 * ```
 *
 * with the range read from the attached effect's `bandLevelRange` (typically
 * ±1500 mB). The user's [EqualizerSettings] base levels are never mutated;
 * only the applied level changes, and an empty overlay restores the pure user
 * levels. Bands beyond the effect's `numberOfBands` are skipped, and the whole
 * overlay is a no-op without an attached effect.
 *
 * The `Equalizer` is intercepted via `mockkConstructor` (its real constructor
 * runs against Robolectric's `ShadowAudioEffect`), with a deterministic 3-band
 * / ±1500 mB geometry so the millibel values can be asserted exactly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EqualizerHelperBandOffsetsTest {

    /** Band → applied millibels, recording every `setBandLevel` in order. */
    private val applied = mutableListOf<Pair<Int, Int>>()

    private lateinit var helper: EqualizerHelper

    @Before
    fun setUp() {
        mockkConstructor(Equalizer::class)
        every { anyConstructed<Equalizer>().numberOfBands } returns 3
        every { anyConstructed<Equalizer>().bandLevelRange } returns shortArrayOf(-1500, 1500)
        every { anyConstructed<Equalizer>().setBandLevel(any(), any()) } answers {
            applied += firstArg<Short>().toInt() to secondArg<Short>().toInt()
        }
        helper = EqualizerHelper()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun appliedFor(band: Int) = applied.last { it.first == band }.second

    @Test
    fun `user base levels are applied in millibels without any offset`() {
        helper.attach(audioSessionId = 42)
        applied.clear()

        helper.setSettings(EqualizerSettings(bandLevels = listOf(0, -6, 5, 9, 12)))

        // dB → mB is *100; bands 3+ are skipped (numberOfBands = 3).
        assertEquals(listOf(0 to 0, 1 to -600, 2 to 500), applied)
    }

    @Test
    fun `offsets overlay additively on top of the user base levels`() {
        helper.attach(audioSessionId = 42)
        helper.setSettings(EqualizerSettings(bandLevels = listOf(0, -6, 5)))
        applied.clear()

        helper.setBandOffsets(mapOf(0 to 300, 1 to -200, 2 to 300))

        assertEquals(listOf(0 to 300, 1 to -800, 2 to 800), applied)
        // The overlay is additive, not absolute: 5 dB base + 300 mB offset = 800 mB.
        assertEquals(800, appliedFor(2))
    }

    @Test
    fun `applied level clamps to the equalizer band level range`() {
        helper.attach(audioSessionId = 42)
        helper.setSettings(EqualizerSettings(bandLevels = listOf(15, -15, 0)))
        applied.clear()

        // 1500 + 500 = 2000 → clamped to +1500; -1500 - 500 = -2000 → clamped to -1500.
        helper.setBandOffsets(mapOf(0 to 500, 1 to -500, 2 to 0))

        assertEquals(listOf(0 to 1500, 1 to -1500, 2 to 0), applied)
    }

    @Test
    fun `empty offsets clear the overlay and restore the pure user levels`() {
        helper.attach(audioSessionId = 42)
        helper.setSettings(EqualizerSettings(bandLevels = listOf(0, 4, -8)))
        helper.setBandOffsets(mapOf(0 to 300, 1 to 300, 2 to -300))
        applied.clear()

        helper.setBandOffsets(emptyMap())

        assertEquals(listOf(0 to 0, 1 to 400, 2 to -800), applied)
    }

    @Test
    fun `bands beyond the effect band count are skipped on every apply`() {
        helper.attach(audioSessionId = 42)
        applied.clear()

        // Default settings carry 10 bands; only the attached effect's 3 receive levels.
        helper.setBandOffsets(mapOf(5 to 999))

        assertEquals(3, applied.size)
        assertEquals(setOf(0, 1, 2), applied.map { it.first }.toSet())
    }

    @Test
    fun `setBandOffsets is a no-op without an attached equalizer`() {
        // No attach(): the helper records the overlay but nothing is applied.
        helper.setBandOffsets(mapOf(0 to 300))

        assertEquals(emptyList<Pair<Int, Int>>(), applied)
    }

    @Test
    fun `detach releases the effect so further overlays apply nothing`() {
        helper.attach(audioSessionId = 42)
        helper.detach()
        applied.clear()

        helper.setBandOffsets(mapOf(0 to 300))

        assertEquals(emptyList<Pair<Int, Int>>(), applied)
    }
}
