package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * State-machine pin for [AudioEffectsStateCore] — the shared half both
 * platform effects managers extend (Android `AudioEffectsProcessor`,
 * desktop `DesktopAudioEffectsManager`). Pins three things:
 *
 *  1. the initial values of every state cell (the twins' "line-by-line"
 *     parity, now one declaration);
 *  2. the setter interplay — what flips what, and that every command fires
 *     its hook exactly once AFTER the flip (the fine hooks default into the
 *     [RecordingCore] funnel, so counting funnel calls pins the fire rule;
 *     strength/param hooks fire UNCONDITIONALLY — the enabled-conditional
 *     re-apply is platform-half behavior, not core behavior);
 *  3. the pure computations — night-mode strength mapping and the per-track
 *     ReplayGain math (the same golden values the desktop manager test and
 *     `DesktopAudioQueueManagerTest` pin reaching the engine).
 *
 * The out-of-range equalizer band index (the FORMER state-level platform
 * divergence, retired) is pinned as the unconditional guarded no-op —
 * Android's `AudioEffectsProcessor` flipped off the historical unguarded
 * throw and the core's constructor flag is gone with it.
 */
class AudioEffectsStateCoreTest {

    /** Fake hook: counts coarse-funnel fires (the desktop half's only override). */
    private class RecordingCore() : AudioEffectsStateCore() {
        var notifications = 0
            private set

        override fun onEffectsStateChanged() {
            notifications++
        }
    }

    /** Fake hook: records the equalizer hook's `levelsRewritten` flag sequence. */
    private class RecordingEqualizerHook() : AudioEffectsStateCore() {
        val levelRewrites = mutableListOf<Boolean>()

        override fun onEqualizerSettingsChanged(levelsRewritten: Boolean) {
            levelRewrites += levelsRewritten
        }
    }

    private fun recordingCore() = RecordingCore()

    // ── initial state: every cell, both platform configurations ────────────

    @Test
    fun initialStatePinsEveryCell() {
        val core = recordingCore()
        assertFalse(core.nightModeEnabled.value)
        assertFalse(core.dialogueBoostEnabled.value)
        assertFalse(core.equalizerEnabled.value)
        assertEquals(EqualizerSettings(), core.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, core.equalizerPreset.value)
        assertFalse(core.bassBoostEnabled.value)
        assertEquals(EffectStrength.MODERATE, core.bassBoostStrengthState)
        assertEquals(EffectStrength.MODERATE, core.dialogueBoostStrengthState)
        assertEquals(EffectStrength.MODERATE, core.nightModeStrengthState)
        assertFalse(core.virtualizerEnabled.value)
        assertEquals(500, core.virtualizerStrength.value)
        assertEquals(ReverbPreset.NONE, core.reverbPresetState.value)
        assertEquals(0f, core.lrBalance.value)
        assertEquals(0f, core.pitchSemitones.value)
        assertFalse(core.autoEqByGenre.value)
        assertEquals(AudioNormalizationMode.NONE, core.replayGainMode.value)
        assertEquals(0f, core.replayGainPreAmpDb.value)
        assertEquals(ChannelMixMode.AUTO, core.channelMixMode.value)
        assertFalse(core.channelMixEnabled.value)
        // Runtime params default to Android's public-field values.
        assertEquals(0.4f, core.nightModeVolume)
        assertEquals(1200, core.nightModeGain)
        // Fresh ReplayGain context: NONE mode → null, nothing active.
        assertNull(core.replayGainContextEffectiveDb())
        assertFalse(core.replayGainNormalizationActive)
        // Visualizer taps start empty (desktop default; Android overrides).
        assertEquals(0, core.fftData.value.size)
        assertEquals(0, core.waveformData.value.size)
        assertEquals(0, core.notifications, "a fresh core fires nothing")
    }

    // ── toggle interplay: flip + exactly one hook fire ──────────────────────

    @Test
    fun togglesFlipTheirFlowsAndNotifyOnceEach() {
        val core = recordingCore()
        core.toggleNightMode()
        assertTrue(core.nightModeEnabled.value)
        core.toggleDialogueBoost()
        assertTrue(core.dialogueBoostEnabled.value)
        core.toggleEqualizer()
        assertTrue(core.equalizerEnabled.value)
        core.toggleBassBoost()
        assertTrue(core.bassBoostEnabled.value)
        core.toggleVirtualizer()
        assertTrue(core.virtualizerEnabled.value)
        assertEquals(5, core.notifications)

        core.toggleNightMode()
        assertFalse(core.nightModeEnabled.value)
        assertEquals(6, core.notifications, "the second toggle fires the hook again")
    }

    @Test
    fun explicitEnableCommandsFlipAndNotifyLikeTheToggles() {
        // Historically Android-only restore commands; additive on desktop.
        val core = recordingCore()
        core.setEqualizerEnabled(true)
        core.setBassBoostEnabled(true)
        core.setVirtualizerEnabled(true)
        core.setDialogueBoostEnabled(true)
        core.setNightModeEnabled(true)
        assertTrue(core.equalizerEnabled.value)
        assertTrue(core.bassBoostEnabled.value)
        assertTrue(core.virtualizerEnabled.value)
        assertTrue(core.dialogueBoostEnabled.value)
        assertTrue(core.nightModeEnabled.value)
        assertEquals(5, core.notifications)
        // Idempotent re-enable still fires (mirrors the Android setters).
        core.setEqualizerEnabled(true)
        assertTrue(core.equalizerEnabled.value)
        assertEquals(6, core.notifications)
    }

    @Test
    fun strengthAndParamSettersFireTheHookEvenWhileTheEffectIsOff() {
        // Core contract: the hook fires UNCONDITIONALLY. The historical
        // enabled-conditional re-apply is Android-half behavior; the desktop
        // funnel notifies on every mutation. Both start from their observe
        // of THIS fire.
        val core = recordingCore()
        core.setDialogueBoostStrength(EffectStrength.HIGH)
        core.setNightModeStrength(EffectStrength.LOW)
        core.setBassBoostStrength(EffectStrength.NONE)
        core.setVirtualizerStrength(800)
        core.setNightModeParams(volume = 0.2f, gain = 800)
        assertEquals(EffectStrength.HIGH, core.dialogueBoostStrengthState)
        assertEquals(EffectStrength.LOW, core.nightModeStrengthState)
        assertEquals(EffectStrength.NONE, core.bassBoostStrengthState)
        assertEquals(800, core.virtualizerStrength.value)
        assertEquals(0.2f, core.nightModeVolume)
        assertEquals(800, core.nightModeGain)
        // No enable flow flipped — strengths are non-flow cells.
        assertFalse(core.dialogueBoostEnabled.value)
        assertFalse(core.nightModeEnabled.value)
        assertFalse(core.bassBoostEnabled.value)
        assertEquals(5, core.notifications)
    }

    // ── equalizer bands + presets ────────────────────────────────────────────

    @Test
    fun setEqualizerBandRewritesTheBandAndMarksThePresetCustom() {
        val core = recordingCore()
        core.setEqualizerBand(0, 600)
        assertEquals(600, core.equalizerSettings.value.bandLevels[0])
        assertEquals(EqualizerPreset.CUSTOM, core.equalizerPreset.value)
        assertEquals(1, core.notifications)
    }

    @Test
    fun outOfRangeBandIndexIsAFullNoOp() {
        // The guard is unconditional (Android's AudioEffectsProcessor
        // flipped off its historical unguarded list write and the core's
        // constructor flag is gone): a bad index is a full no-op — no flip,
        // no hook.
        val core = recordingCore()
        core.setEqualizerBand(-1, 600)
        core.setEqualizerBand(10, 600)
        assertEquals(EqualizerSettings(), core.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, core.equalizerPreset.value)
        assertEquals(0, core.notifications, "out-of-range bands must not reach the platform hook")
    }

    @Test
    fun presetSetAppliesItsLevelsAndCustomKeepsTheUserCurve() {
        val core = recordingCore()
        core.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        assertEquals(EqualizerPreset.BASS_BOOST.bandLevels(), core.equalizerSettings.value.bandLevels)
        core.setEqualizerBand(3, -250)
        core.setEqualizerPreset(EqualizerPreset.CUSTOM)
        assertEquals(EqualizerPreset.CUSTOM, core.equalizerPreset.value)
        assertEquals(-250, core.equalizerSettings.value.bandLevels[3], "CUSTOM must not clobber the user's curve")
        // CUSTOM still notifies (desktop parity) — 3 mutations, 3 fires.
        assertEquals(3, core.notifications)
    }

    @Test
    fun equalizerHookCarriesTheLevelsRewrittenFlagForEveryEntry() {
        val core = RecordingEqualizerHook()
        core.setEqualizerBand(0, 100) // band write: levels rewritten
        core.resetEqualizer() // reset: levels rewritten
        core.setEqualizerPreset(EqualizerPreset.ROCK) // concrete preset: rewritten
        core.setEqualizerPreset(EqualizerPreset.CUSTOM) // curve-preserving: NOT rewritten
        assertEquals(listOf(true, true, true, false), core.levelRewrites)
    }

    @Test
    fun resetEqualizerRestoresFlatAndZeroLevels() {
        val core = recordingCore()
        core.setEqualizerPreset(EqualizerPreset.ROCK)
        core.resetEqualizer()
        assertEquals(EqualizerSettings(), core.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, core.equalizerPreset.value)
        assertFalse(core.equalizerEnabled.value, "reset is band-level only — the enable flag is untouched")
    }

    // ── night-mode computation ───────────────────────────────────────────────

    @Test
    fun nightModeStrengthDrivesTheSharedVolumeAndGainTables() {
        val core = recordingCore()
        // Default MODERATE: 0.4 attenuation, +3000 mB.
        assertEquals(0.4f, core.nightModeVolumeForStrength, 0.0001f)
        assertEquals(3000, core.nightModeGainForStrength)
        core.setNightModeStrength(EffectStrength.HIGH)
        assertEquals(0.2f, core.nightModeVolumeForStrength, 0.0001f)
        assertEquals(4500, core.nightModeGainForStrength)
        core.setNightModeStrength(EffectStrength.LOW)
        assertEquals(0.7f, core.nightModeVolumeForStrength, 0.0001f)
        assertEquals(1500, core.nightModeGainForStrength)
        core.setNightModeStrength(EffectStrength.NONE)
        assertEquals(1.0f, core.nightModeVolumeForStrength, 0.0001f)
        assertEquals(0, core.nightModeGainForStrength)
    }

    // ── ReplayGain: the shared golden table ─────────────────────────────────

    @Test
    fun replayGainGoldenTable() {
        val core = recordingCore()
        core.setReplayGainMode(AudioNormalizationMode.TRACK)
        // TRACK 2.5 + 0 → 2.5 (the value DesktopAudioQueueManagerTest pins
        // reaching the engine config).
        assertEquals(2.5f, core.effectiveReplayGainDb(2.5f, isShuffled = false))
        core.setReplayGainPreAmpDb(1.5f)
        // (null ?: 0) + 1.5 → 1.5; -6.5 + 1.5 → -5.
        assertEquals(1.5f, core.effectiveReplayGainDb(null, isShuffled = false))
        assertEquals(-5f, core.effectiveReplayGainDb(-6.5f, isShuffled = false))

        // ALBUM non-shuffled matches TRACK math; shuffled pins at exactly 0
        // (checked BEFORE the pre-amp add).
        core.setReplayGainMode(AudioNormalizationMode.ALBUM)
        assertEquals(2.5f + 1.5f, core.effectiveReplayGainDb(2.5f, isShuffled = false))
        assertEquals(0f, core.effectiveReplayGainDb(2.5f, isShuffled = true))

        // DYNAMIC/NONE → null; the compressor-handoff flag follows the mode.
        core.setReplayGainMode(AudioNormalizationMode.DYNAMIC)
        assertNull(core.effectiveReplayGainDb(2.5f, isShuffled = false))
        assertTrue(core.replayGainNormalizationActive)
        core.setReplayGainMode(AudioNormalizationMode.NONE)
        assertNull(core.effectiveReplayGainDb(2.5f, isShuffled = false))
        assertFalse(core.replayGainNormalizationActive)
    }

    @Test
    fun perTrackContextIsStoredAndRefoldedOnLaterEdits() {
        val core = recordingCore()
        core.setReplayGainMode(AudioNormalizationMode.TRACK)
        core.setReplayGainContext(trackGainDb = 2.5f, isShuffled = false)
        assertEquals(2.5f, core.replayGainContextEffectiveDb())
        // Next track without embedded gain: 0 + preAmp.
        core.setReplayGainContext(trackGainDb = null, isShuffled = false)
        assertEquals(0f, core.replayGainContextEffectiveDb())
        // A pre-amp edit alone re-folds the stored context.
        core.setReplayGainContext(trackGainDb = 2.5f, isShuffled = false)
        core.setReplayGainPreAmpDb(-1f)
        assertEquals(1.5f, core.replayGainContextEffectiveDb())
        // Mode set without any context: (null ?: 0) + preAmp fallback.
        val fresh = recordingCore()
        fresh.setReplayGainMode(AudioNormalizationMode.TRACK)
        fresh.setReplayGainPreAmpDb(2f)
        assertEquals(2f, fresh.replayGainContextEffectiveDb())
        // ALBUM + shuffled context pins at exactly 0 even with pre-amp.
        core.setReplayGainContext(trackGainDb = 2.5f, isShuffled = true)
        core.setReplayGainMode(AudioNormalizationMode.ALBUM)
        assertEquals(0f, core.replayGainContextEffectiveDb())
    }

    @Test
    fun replayGainCommandsNotifySoTheDesktopSnapshotRefolds() {
        val core = recordingCore()
        core.setReplayGainMode(AudioNormalizationMode.TRACK)
        core.setReplayGainPreAmpDb(1f)
        core.setReplayGainContext(trackGainDb = 2.5f, isShuffled = false)
        assertEquals(3, core.notifications)
        assertEquals(AudioNormalizationMode.TRACK, core.replayGainMode.value)
        assertEquals(1f, core.replayGainPreAmpDb.value)
    }

    // ── remaining commands: state + single fire ─────────────────────────────

    @Test
    fun channelMixSetterDrivesBothCells() {
        val core = recordingCore()
        core.setChannelMix(ChannelMixMode.MONO, enabled = true)
        assertEquals(ChannelMixMode.MONO, core.channelMixMode.value)
        assertTrue(core.channelMixEnabled.value)
        assertEquals(1, core.notifications)
    }

    @Test
    fun reverbBalanceAndPitchSettersFlipTheirCellsAndNotify() {
        val core = recordingCore()
        core.setReverbPreset(ReverbPreset.LARGE_HALL)
        core.setLrBalance(0.5f)
        core.setPitchSemitones(-2f)
        assertEquals(ReverbPreset.LARGE_HALL, core.reverbPresetState.value)
        assertEquals(0.5f, core.lrBalance.value)
        assertEquals(-2f, core.pitchSemitones.value)
        assertEquals(3, core.notifications)
    }

    @Test
    fun pitchMultiplierFollowsTwoPowSemitonesOverTwelve() {
        val core = recordingCore()
        assertEquals(1.0f, core.pitchMultiplier, 0.000001f)
        core.setPitchSemitones(12f)
        assertEquals(2.0f, core.pitchMultiplier, 0.000001f)
        core.setPitchSemitones(-12f)
        assertEquals(0.5f, core.pitchMultiplier, 0.000001f)
    }

    // ── auto-EQ by genre ─────────────────────────────────────────────────────

    @Test
    fun autoEqResolvesTheFirstGenreMatchOntoTheEqualizer() {
        val core = recordingCore()
        core.setAutoEqByGenre(true)
        core.applyAutoEqForGenre(listOf("Unmatchable", "Rock Classics", "Jazz"))
        assertEquals(EqualizerPreset.ROCK, core.equalizerPreset.value)
        assertEquals(EqualizerPreset.ROCK.bandLevels(), core.equalizerSettings.value.bandLevels)
    }

    @Test
    fun autoEqIsANoOpWhenTheAutoFlagIsOffOrNothingMatches() {
        val core = recordingCore()
        core.setAutoEqByGenre(false) // fire 1
        core.applyAutoEqForGenre(listOf("Rock"))
        assertEquals(EqualizerPreset.FLAT, core.equalizerPreset.value)
        assertEquals(1, core.notifications, "only setAutoEqByGenre itself may fire")

        core.setAutoEqByGenre(true) // fire 2
        core.applyAutoEqForGenre(listOf("Unmatchable"))
        core.applyAutoEqForGenre(emptyList())
        core.applyAutoEqForGenre(null)
        assertEquals(EqualizerPreset.FLAT, core.equalizerPreset.value)
        assertEquals(2, core.notifications, "unmatched / empty / null genres must not fire")
    }

    @Test
    fun autoEqSkipsTheReapplyWhenTheResolvedPresetIsAlreadyActive() {
        val core = recordingCore()
        core.setAutoEqByGenre(true) // fire 1
        core.applyAutoEqForGenre(listOf("Jazz")) // fire 2 — the apply
        assertEquals(EqualizerPreset.JAZZ, core.equalizerPreset.value)

        core.applyAutoEqForGenre(listOf("Jazz Fusion"))
        // Same resolved preset (JAZZ) as the active one → no reapply fire.
        assertEquals(EqualizerPreset.JAZZ, core.equalizerPreset.value)
        assertEquals(2, core.notifications)

        // A user band edit marks the preset CUSTOM, so a later genre match
        // (JAZZ != CUSTOM) legitimately reapplies its levels.
        core.setEqualizerBand(0, 111) // fire 3
        core.applyAutoEqForGenre(listOf("Jazz")) // fire 4 — reapply
        assertEquals(EqualizerPreset.JAZZ.bandLevels()[0], core.equalizerSettings.value.bandLevels[0])
        assertEquals(4, core.notifications)
    }

    // ── visualizer: declared state-only divergence ───────────────────────────

    @Test
    fun enableVisualizerIsFullyInertByDefault() {
        val core = recordingCore()
        core.enableVisualizer(true)
        core.enableVisualizer(false)
        assertEquals(0, core.notifications, "no PCM tap on desktop — the toggle must not reach the funnel")
        assertEquals(0, core.fftData.value.size)
        assertEquals(0, core.waveformData.value.size)
    }
}
