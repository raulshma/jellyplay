package com.raulshma.jellyplay.desktop.player

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
 * State-machine pin for [DesktopAudioEffectsManager] — the desktop twin of the
 * Android `AudioEffectsProcessor` (legacy `:core:data`). Two invariants:
 *
 *  1. Every setter flips its flow (the audio ViewModel persists
 *     `uiState.effects.<flag>` right after the call — a missed flip would let
 *     the store silently undo the toggle) AND folds through [snapshot][DesktopAudioEffectsManager.snapshotConfig]
 *     into the shared [com.raulshma.jellyplay.feature.player.video.engine.AudioEffectsConfig]
 *     that [DesktopAudioQueueManager] pushes onto the mpv `af` chain.
 *  2. The per-track ReplayGain computation mirrors
 *     `AudioEffectsProcessor.applyReplayGain` exactly, with the same golden
 *     values that pin already pin the engine config in
 *     [DesktopAudioQueueManagerTest] (TRACK 2.5 → 2.5 dB, ALBUM+shuffled →
 *     exactly 0): `TRACK → (trackGain ?: 0) + preAmp`; `ALBUM →` the same
 *     EXCEPT a shuffled queue pins the gain at 0 (checked BEFORE the pre-amp
 *     add); `DYNAMIC/NONE → null` (compressor stage / nothing).
 *
 * Strengths and night-mode params are deliberately NON-flow inputs (Android
 * keeps the same private fields) — pinned as snapshot-only mutations.
 */
class DesktopAudioEffectsManagerTest {

    private fun newManager(changes: MutableInt? = null): DesktopAudioEffectsManager {
        val manager = DesktopAudioEffectsManager()
        changes?.let { counter ->
            manager.onEffectsChanged = { counter.value++ }
        }
        return manager
    }

    private class MutableInt(var value: Int = 0)

    // ── initial state: AudioEffectsProcessor line-by-line ─────────────────

    @Test
    fun initialStateMirrorsTheAndroidProcessorDefaults() {
        val manager = newManager()
        assertFalse(manager.nightModeEnabled.value)
        assertFalse(manager.dialogueBoostEnabled.value)
        assertFalse(manager.equalizerEnabled.value)
        assertEquals(EqualizerSettings(), manager.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
        assertFalse(manager.bassBoostEnabled.value)
        assertEquals(EffectStrength.MODERATE, manager.bassBoostStrengthState)
        assertFalse(manager.virtualizerEnabled.value)
        assertEquals(500, manager.virtualizerStrength.value)
        assertEquals(ReverbPreset.NONE, manager.reverbPresetState.value)
        assertEquals(0f, manager.lrBalance.value)
        assertEquals(0f, manager.pitchSemitones.value)
        assertFalse(manager.autoEqByGenre.value)
        assertEquals(AudioNormalizationMode.NONE, manager.replayGainMode.value)
        assertEquals(0f, manager.replayGainPreAmpDb.value)
        assertEquals(ChannelMixMode.AUTO, manager.channelMixMode.value)
        assertFalse(manager.channelMixEnabled.value)
        // Visualizer taps stay empty on desktop (declared divergence).
        assertEquals(0, manager.fftData.value.size)
        assertEquals(0, manager.waveformData.value.size)
    }

    @Test
    fun freshSnapshotFoldsTheAndroidDefaultsIntoTheEngineConfig() {
        val config = newManager().snapshotConfig()
        assertFalse(config.dialogueBoostEnabled)
        assertEquals(EffectStrength.MODERATE, config.dialogueBoostStrength)
        assertFalse(config.nightModeEnabled)
        assertEquals(EffectStrength.MODERATE, config.nightModeStrength)
        // The manager seeds Android's runtime default (1200 mB), not the
        // AudioEffectsConfig data-class default of 0.
        assertEquals(1200, config.nightModeGain)
        assertFalse(config.equalizerEnabled)
        assertEquals(EqualizerSettings(), config.equalizerSettings)
        assertEquals(AudioNormalizationMode.NONE, config.audioNormalizationMode)
        assertFalse(config.audioNormalizationEnabled)
        assertEquals(ChannelMixMode.AUTO, config.channelMixMode)
        assertFalse(config.channelMixEnabled)
        assertFalse(config.bassBoostEnabled)
        assertEquals(EffectStrength.MODERATE, config.bassBoostStrength)
        assertFalse(config.virtualizerEnabled)
        assertEquals(500, config.virtualizerStrength)
        assertEquals(ReverbPreset.NONE, config.reverbPreset)
        assertEquals(0f, config.lrBalance)
        assertEquals(0f, config.pitchSemitones)
        assertNull(config.replayGainEffectiveDb)
    }

    // ── toggle setters flip flows AND the folded config ───────────────────

    @Test
    fun nightModeToggleFlipsFlowAndSnapshotBothWays() {
        val manager = newManager()
        manager.toggleNightMode()
        assertTrue(manager.nightModeEnabled.value)
        assertTrue(manager.snapshotConfig().nightModeEnabled)
        manager.toggleNightMode()
        assertFalse(manager.nightModeEnabled.value)
        assertFalse(manager.snapshotConfig().nightModeEnabled)
    }

    @Test
    fun dialogueBoostAndBassBoostAndVirtualizerTogglesReachTheConfig() {
        val manager = newManager()
        manager.toggleDialogueBoost()
        manager.toggleBassBoost()
        manager.toggleVirtualizer()
        val config = manager.snapshotConfig()
        assertTrue(config.dialogueBoostEnabled)
        assertTrue(config.bassBoostEnabled)
        assertTrue(config.virtualizerEnabled)
        assertTrue(manager.dialogueBoostEnabled.value)
        assertTrue(manager.bassBoostEnabled.value)
        assertTrue(manager.virtualizerEnabled.value)
    }

    @Test
    fun equalizerToggleFlipsFlowAndConfig() {
        val manager = newManager()
        manager.toggleEqualizer()
        assertTrue(manager.equalizerEnabled.value)
        assertTrue(manager.snapshotConfig().equalizerEnabled)
    }

    // ── non-flow strength/param inputs: snapshot-only mutations ───────────

    @Test
    fun strengthSettersLandInSnapshotWithoutFlippingAnyFlow() {
        val manager = newManager()
        manager.setDialogueBoostStrength(EffectStrength.HIGH)
        manager.setNightModeStrength(EffectStrength.LOW)
        manager.setBassBoostStrength(EffectStrength.NONE)
        val config = manager.snapshotConfig()
        assertEquals(EffectStrength.HIGH, config.dialogueBoostStrength)
        assertEquals(EffectStrength.LOW, config.nightModeStrength)
        assertEquals(EffectStrength.NONE, config.bassBoostStrength)
        // Strengths are params on Android too — no flow exists to flip.
        assertFalse(manager.dialogueBoostEnabled.value)
        assertFalse(manager.nightModeEnabled.value)
        assertFalse(manager.bassBoostEnabled.value)
    }

    @Test
    fun nightModeParamsMirrorTheAndroidPublicFields() {
        val manager = newManager()
        manager.setNightModeParams(volume = 0.2f, gain = 800)
        assertEquals(0.2f, manager.nightModeVolumeInternal)
        assertEquals(800, manager.nightModeGainInternal)
        assertEquals(800, manager.snapshotConfig().nightModeGain)
    }

    // ── equalizer bands + presets ─────────────────────────────────────────

    @Test
    fun setEqualizerBandUpdatesTheBandAndMarksThePresetCustom() {
        val manager = newManager()
        manager.setEqualizerBand(0, 600)
        assertEquals(600, manager.equalizerSettings.value.bandLevels[0])
        assertEquals(EqualizerPreset.CUSTOM, manager.equalizerPreset.value)
        assertEquals(600, manager.snapshotConfig().equalizerSettings.bandLevels[0])
    }

    @Test
    fun outOfRangeBandIndexIsAFullNoOp() {
        val changes = MutableInt()
        val manager = newManager(changes = changes)
        manager.setEqualizerBand(-1, 600)
        manager.setEqualizerBand(10, 600)
        assertEquals(EqualizerSettings(), manager.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
        assertEquals(0, changes.value, "out-of-range bands must not reach the af chain")
    }

    @Test
    fun presetSetAppliesItsLevelsButCustomKeepsTheUserCurve() {
        val manager = newManager()
        manager.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        assertEquals(EqualizerPreset.BASS_BOOST.bandLevels(), manager.equalizerSettings.value.bandLevels)
        manager.setEqualizerBand(3, -250)
        manager.setEqualizerPreset(EqualizerPreset.CUSTOM)
        assertEquals(EqualizerPreset.CUSTOM, manager.equalizerPreset.value)
        assertEquals(-250, manager.equalizerSettings.value.bandLevels[3], "CUSTOM must not clobber the user's curve")
    }

    @Test
    fun resetEqualizerRestoresFlatAndZeroLevels() {
        val manager = newManager()
        manager.setEqualizerPreset(EqualizerPreset.ROCK)
        manager.resetEqualizer()
        assertEquals(EqualizerSettings(), manager.equalizerSettings.value)
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
        assertFalse(manager.equalizerEnabled.value, "reset is band-level only — the enable flag is untouched (Android parity)")
    }

    @Test
    fun virtualizerStrengthAndReverbAndBalanceAndPitchFlowIntoConfig() {
        val manager = newManager()
        manager.setVirtualizerStrength(800)
        manager.setReverbPreset(ReverbPreset.LARGE_HALL)
        manager.setLrBalance(0.5f)
        manager.setPitchSemitones(-2f)
        assertEquals(800, manager.virtualizerStrength.value)
        assertEquals(ReverbPreset.LARGE_HALL, manager.reverbPresetState.value)
        assertEquals(0.5f, manager.lrBalance.value)
        assertEquals(-2f, manager.pitchSemitones.value)
        val config = manager.snapshotConfig()
        assertEquals(800, config.virtualizerStrength)
        assertEquals(ReverbPreset.LARGE_HALL, config.reverbPreset)
        assertEquals(0.5f, config.lrBalance)
        assertEquals(-2f, config.pitchSemitones)
    }

    @Test
    fun channelMixSetterDrivesBothModeAndEnabled() {
        val manager = newManager()
        manager.setChannelMix(ChannelMixMode.MONO, enabled = true)
        assertEquals(ChannelMixMode.MONO, manager.channelMixMode.value)
        assertTrue(manager.channelMixEnabled.value)
        val config = manager.snapshotConfig()
        assertEquals(ChannelMixMode.MONO, config.channelMixMode)
        assertTrue(config.channelMixEnabled)
    }

    // ── ReplayGain: AudioEffectsProcessor.applyReplayGain golden table ────

    @Test
    fun trackModeGainIsTrackGainPlusPreAmp() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.setReplayGainPreAmpDb(0f)
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = false)
        // Golden: 2.5 + 0 → 2.5 (same value DesktopAudioQueueManagerTest pins
        // reaching the engine config).
        assertEquals(2.5f, manager.snapshotConfig().replayGainEffectiveDb)
        assertTrue(manager.snapshotConfig().audioNormalizationEnabled)
    }

    @Test
    fun trackModeWithMissingTrackGainFallsBackToZeroPlusPreAmp() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.setReplayGainPreAmpDb(1.5f)
        manager.applyReplayGainForTrack(trackGainDb = null, isShuffled = false)
        // Golden: (null ?: 0) + 1.5 → 1.5.
        assertEquals(1.5f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun trackModeNegativeGainAndPreAmpSumExactly() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.setReplayGainPreAmpDb(1.5f)
        manager.applyReplayGainForTrack(trackGainDb = -6.5f, isShuffled = false)
        // Golden: -6.5 + 1.5 → -5.0.
        assertEquals(-5f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun albumModeNonShuffledMatchesTrackMath() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.ALBUM)
        manager.setReplayGainPreAmpDb(0f)
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = false)
        assertEquals(2.5f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun albumModeShuffledPinsTheGainAtExactlyZero() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.ALBUM)
        manager.setReplayGainPreAmpDb(1.5f)
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = true)
        // Android checks the shuffled flag BEFORE adding the pre-amp: the
        // pinned value is exactly 0, never 2.5 + 1.5.
        assertEquals(0f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun dynamicAndNoneModesYieldNullGainWithCompressorExclusivityFlag() {
        val manager = newManager()
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = false)

        manager.setReplayGainMode(AudioNormalizationMode.DYNAMIC)
        assertNull(manager.snapshotConfig().replayGainEffectiveDb, "DYNAMIC hands off to the compressor stage")
        assertTrue(manager.snapshotConfig().audioNormalizationEnabled)

        manager.setReplayGainMode(AudioNormalizationMode.NONE)
        assertNull(manager.snapshotConfig().replayGainEffectiveDb)
        assertFalse(manager.snapshotConfig().audioNormalizationEnabled)
    }

    @Test
    fun newTrackContextReFoldsGainWithoutAModeChange() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = false)
        assertEquals(2.5f, manager.snapshotConfig().replayGainEffectiveDb)
        // Next track without embedded gain: Android re-runs applyReplayGain
        // with item.normalizationGain == null → 0 + preAmp.
        manager.applyReplayGainForTrack(trackGainDb = null, isShuffled = false)
        assertEquals(0f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun preAmpChangeAloneReFoldsTheStoredTrackGain() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.applyReplayGainForTrack(trackGainDb = 2.5f, isShuffled = false)
        manager.setReplayGainPreAmpDb(-1f)
        // 2.5 + (-1.0) → 1.5 — the last track context survives the pre-amp edit.
        assertEquals(1.5f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    @Test
    fun modeSetWithoutTrackContextUsesTheZeroGainFallback() {
        val manager = newManager()
        manager.setReplayGainMode(AudioNormalizationMode.TRACK)
        manager.setReplayGainPreAmpDb(2f)
        // No applyReplayGainForTrack yet: (null ?: 0) + 2 → 2.
        assertEquals(2f, manager.snapshotConfig().replayGainEffectiveDb)
    }

    // ── auto-EQ by genre ──────────────────────────────────────────────────

    @Test
    fun autoEqResolvesTheFirstGenreMatchOntoTheEqualizer() {
        val manager = newManager()
        manager.setAutoEqByGenre(true)
        manager.applyAutoEqForGenre(listOf("Unmatchable", "Rock Classics", "Jazz"))
        assertEquals(EqualizerPreset.ROCK, manager.equalizerPreset.value)
        assertEquals(EqualizerPreset.ROCK.bandLevels(), manager.equalizerSettings.value.bandLevels)
    }

    @Test
    fun autoEqIsANoOpWhenTheAutoFlagIsOffOrNothingMatches() {
        val changes = MutableInt()
        val manager = newManager(changes = changes)

        manager.setAutoEqByGenre(false)
        manager.applyAutoEqForGenre(listOf("Rock"))
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
        assertEquals(1, changes.value, "only setAutoEqByGenre itself may notify")

        manager.setAutoEqByGenre(true) // 2nd notification
        manager.applyAutoEqForGenre(listOf("Unmatchable"))
        manager.applyAutoEqForGenre(emptyList())
        manager.applyAutoEqForGenre(null)
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
        assertEquals(2, changes.value, "unmatched / empty / null genres must not notify")
    }

    @Test
    fun autoEqSkipsTheReapplyWhenTheResolvedPresetIsAlreadyActive() {
        val changes = MutableInt()
        val manager = newManager(changes)
        manager.setAutoEqByGenre(true) // notification 1
        manager.applyAutoEqForGenre(listOf("Jazz")) // notification 2 — the apply
        assertEquals(EqualizerPreset.JAZZ, manager.equalizerPreset.value)

        manager.applyAutoEqForGenre(listOf("Jazz Fusion"))
        // Same resolved preset (JAZZ) as the active one → setEqualizerPreset
        // is skipped: no notification, no level rewrite.
        assertEquals(EqualizerPreset.JAZZ, manager.equalizerPreset.value)
        assertEquals(EqualizerPreset.JAZZ.bandLevels(), manager.equalizerSettings.value.bandLevels)
        assertEquals(2, changes.value)

        // Flip side: a user band edit marks the preset CUSTOM, so a later
        // genre match (JAZZ != CUSTOM) legitimately reapplies its levels.
        manager.setEqualizerBand(0, 111) // notification 3
        manager.applyAutoEqForGenre(listOf("Jazz")) // notification 4 — reapply
        assertEquals(EqualizerPreset.JAZZ.bandLevels()[0], manager.equalizerSettings.value.bandLevels[0])
        assertEquals(4, changes.value)
    }

    // ── visualizer: declared state-only divergence ────────────────────────

    @Test
    fun enableVisualizerNeverProducesTapsAndNeverNotifies() {
        val changes = MutableInt()
        val manager = newManager(changes = changes)
        manager.enableVisualizer(true)
        manager.enableVisualizer(false)
        assertEquals(0, manager.fftData.value.size)
        assertEquals(0, manager.waveformData.value.size)
        assertEquals(0, changes.value, "mpv offers no in-sink PCM tap — the toggle must be fully inert")
    }
}
