package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [AudioEffectsController]'s single write choreography. The load-bearing
 * case is the toggle hazard the extraction fixed: the persisted value must come
 * from the manager's synchronous `StateFlow.value` (computed by the apply leg),
 * NEVER from the lagging uiState mirror — the former inline VM setters read the
 * mirror inside a `launch`, which was only correct by dispatch-order luck.
 *
 * Uses a hand-rolled [FakeEffectsManager] (real state, processor semantics)
 * rather than a relaxed mock so the apply leg actually mutates readable state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioEffectsControllerTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var manager: FakeEffectsManager
    private lateinit var engine: AudioPlayerEngine
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore

    /** The uiState effects slice, via the same seam the VM wires in. */
    private lateinit var mirror: AudioEffectsState

    private lateinit var controller: AudioEffectsController

    @BeforeTest
    fun setUp() {
        manager = FakeEffectsManager()
        engine = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        mirror = AudioEffectsState()
        controller = AudioEffectsController(
            scope = testScope,
            effectsManager = manager,
            engine = engine,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            updateEffects = { transform -> mirror = transform(mirror) },
        )
    }

    // ── The toggle hazard: persist the manager-computed value ────────────────

    @Test
    fun toggleNightMode_persistsTheManagerComputedValue_whileTheMirrorStaysStale() {
        // The mirror says OFF and no collector has run — the former mirror
        // read-back would persist `false`, silently undoing the toggle.
        assertFalse(mirror.nightModeEnabled)

        controller.toggleNightMode()

        assertTrue(manager.nightModeEnabled.value, "the apply leg flipped the manager")
        coVerify(exactly = 1) { audioEffectsStore.setNightModeEnabled(true) }
        assertFalse(mirror.nightModeEnabled)
    }

    @Test
    fun toggleDialogueBoost_persistsTheManagerComputedValue_whileTheMirrorStaysStale() {
        assertFalse(mirror.dialogueBoostEnabled)

        controller.toggleDialogueBoost()

        coVerify(exactly = 1) { audioEffectsStore.setDialogueBoostEnabled(true) }
    }

    @Test
    fun toggleBackOff_persistsTheComputedFalse_evenThoughTheMirrorNeverCaughtUp() {
        controller.toggleNightMode()
        controller.toggleNightMode()

        coVerify(exactly = 1) { audioEffectsStore.setNightModeEnabled(true) }
        coVerify(exactly = 1) { audioEffectsStore.setNightModeEnabled(false) }
        assertFalse(mirror.nightModeEnabled, "the mirror was never a persistence input")
    }

    @Test
    fun setEqualizerBand_persistsTheManagerFoldedSettings_notTheMirror() {
        // The manager folds band+level into a NEW settings object; the mirror
        // holds a stale default. The persist leg must write the manager's.
        val staleMirrorSettings = mirror.equalizerSettings

        controller.setEqualizerBand(bandIndex = 2, levelDb = 3)

        coVerify(exactly = 1) { audioEffectsStore.setEqualizerSettings(manager.equalizerSettings.value) }
        coVerify(exactly = 0) { audioEffectsStore.setEqualizerSettings(staleMirrorSettings) }
    }

    @Test
    fun resetEqualizer_persistsManagerSettingsAndPreset() {
        manager.setEqualizerBand(0, 5)

        controller.resetEqualizer()

        coVerify(exactly = 1) { audioEffectsStore.setEqualizerSettings(manager.equalizerSettings.value) }
        coVerify(exactly = 1) { audioEffectsStore.setEqualizerPreset(manager.equalizerPreset.value) }
        assertEquals(EqualizerPreset.FLAT, manager.equalizerPreset.value)
    }

    @Test
    fun setEqualizerPreset_persistsThePresetAndTheManagerFoldedSettings() {
        controller.setEqualizerPreset(EqualizerPreset.ROCK)

        coVerify(exactly = 1) { audioEffectsStore.setEqualizerPreset(EqualizerPreset.ROCK) }
        coVerify(exactly = 1) { audioEffectsStore.setEqualizerSettings(manager.equalizerSettings.value) }
    }

    // ── Strength setters: persist the argument, mirror through the manager ───

    @Test
    fun setDialogueBoostStrength_persistsItsArgument_andMirrorsThroughTheManager() {
        controller.setDialogueBoostStrength(EffectStrength.HIGH)

        assertEquals(EffectStrength.HIGH, manager.dialogueBoostStrengthState)
        coVerify(exactly = 1) { audioEffectsStore.setDialogueBoostStrength(EffectStrength.HIGH) }
        assertEquals(EffectStrength.HIGH, mirror.dialogueBoostStrength)
    }

    @Test
    fun setNightModeStrength_persistsItsArgument_andMirrorsThroughTheManager() {
        controller.setNightModeStrength(EffectStrength.LOW)

        assertEquals(EffectStrength.LOW, manager.nightModeStrengthState)
        coVerify(exactly = 1) { audioEffectsStore.setNightModeStrength(EffectStrength.LOW) }
        assertEquals(EffectStrength.LOW, mirror.nightModeStrength)
    }

    @Test
    fun setBassBoostStrength_persistsItsArgument_andMirrorsThroughTheManager() {
        controller.setBassBoostStrength(EffectStrength.NONE)

        assertEquals(EffectStrength.NONE, manager.bassBoostStrengthState)
        coVerify(exactly = 1) { audioEffectsStore.setBassBoostStrength(EffectStrength.NONE) }
        assertEquals(EffectStrength.NONE, mirror.bassBoostStrength)
    }

    // ── Choreography ordering: apply → mirror → persist ──────────────────────

    @Test
    fun applyAndPersist_appliesBeforePersisting() {
        coEvery { audioEffectsStore.setLrBalance(any()) } coAnswers { manager.applyLog += "persist" }

        controller.setLrBalance(-0.5f)

        assertEquals(
            listOf("apply", "persist"),
            manager.applyLog,
            "the manager must see the value in the same frame",
        )
    }

    @Test
    fun applyAndPersist_runsTheMirrorSynchronouslyBetweenApplyAndPersist() {
        val recording = AudioEffectsController(
            scope = testScope,
            effectsManager = manager,
            engine = engine,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            updateEffects = { transform ->
                manager.applyLog += "mirror"
                mirror = transform(mirror)
            },
        )

        coEvery { audioEffectsStore.setBassBoostStrength(any()) } coAnswers { manager.applyLog += "persist" }

        recording.setBassBoostStrength(EffectStrength.HIGH)

        assertEquals(
            listOf("apply", "mirror", "persist"),
            manager.applyLog,
            "apply and mirror land in the same frame; persist is launched after them",
        )
    }

    // ── Crossfade / gapless: engine apply + audio-store persist ───────────────

    @Test
    fun updateCrossfadeDuration_appliesToEngine_andPersists() {
        controller.updateCrossfadeDuration(4_000L)

        verify(exactly = 1) { engine.setCrossfadeDurationMs(4_000L) }
        coVerify(exactly = 1) { audioStore.setAudioCrossfadeDurationMs(4_000L) }
    }

    @Test
    fun updateGaplessPlayback_appliesToEngine_andPersists() {
        controller.updateGaplessPlayback(false)

        verify(exactly = 1) { engine.setGaplessEnabled(false) }
        coVerify(exactly = 1) { audioStore.setAudioGaplessEnabled(false) }
    }

    // ── Seeding: the ONE prefs→effects field list ─────────────────────────────

    @Test
    fun seedForPlayback_appliesEverySeededFieldFromTheSlices_andPersistsNothing() {
        val audio = AudioSlice(
            audioNightModeVolume = 0.6f,
            audioNightModeGain = 900,
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            replayGainPreAmpDb = -2.5f,
            audioGaplessEnabled = false,
            audioCrossfadeDurationMs = 3_000L,
        )
        val fx = AudioEffectsSlice(
            dialogueBoostStrength = EffectStrength.LOW,
            nightModeStrength = EffectStrength.HIGH,
            bassBoostStrength = EffectStrength.NONE,
            virtualizerStrength = 800,
            lrBalance = -0.5f,
            autoEqByGenre = true,
            pitchSemitones = 2f,
        )

        controller.seedForPlayback(audio, fx)

        // Manager fields (distinct values from BOTH slices).
        assertEquals(0.6f, manager.nightModeVolume)
        assertEquals(900, manager.nightModeGain)
        assertEquals(EffectStrength.LOW, manager.dialogueBoostStrengthState)
        assertEquals(EffectStrength.HIGH, manager.nightModeStrengthState)
        assertEquals(AudioNormalizationMode.TRACK, manager.replayGainMode.value)
        assertEquals(-2.5f, manager.replayGainPreAmpDb.value)
        assertEquals(EffectStrength.NONE, manager.bassBoostStrengthState)
        assertEquals(800, manager.virtualizerStrength.value)
        assertEquals(-0.5f, manager.lrBalance.value)
        assertEquals(2f, manager.pitchSemitones.value)
        assertTrue(manager.autoEqByGenre.value)
        // Engine fields.
        verify(exactly = 1) { engine.setCrossfadeDurationMs(3_000L) }
        verify(exactly = 1) { engine.setGaplessEnabled(false) }
        // The strength mirror reads back through the manager accessors.
        assertEquals(EffectStrength.LOW, mirror.dialogueBoostStrength)
        assertEquals(EffectStrength.HIGH, mirror.nightModeStrength)
        assertEquals(EffectStrength.NONE, mirror.bassBoostStrength)
        // Apply-ONLY: the values came FROM the stores; nothing round-trips.
        coVerify { audioEffectsStore wasNot Called }
        coVerify { audioStore wasNot Called }
    }

    /**
     * The ResetCoverageGuard-style guard: every [AudioEffectsState] field must
     * be accounted for by the seeding contract — either fed to the manager by
     * [AudioEffectsController.seedForPlayback] or declared deliberately-not-
     * seeded (the enabled/preset flags bind to the manager's own startup state
     * and their flows mirror straight into uiState). A newly added field fails
     * here until its seeding membership is decided and recorded.
     */
    @Test
    fun everyAudioEffectsStateField_isAccountedForByTheSeedingContract() {
        val declaredFields = AudioEffectsState::class.java.declaredMethods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar { c -> c.lowercase() } }
            .toSet()
        assertTrue(declaredFields.isNotEmpty(), "reflection found no AudioEffectsState getters")

        for (name in declaredFields) {
            assertTrue(
                name in seededByPlayback || name in deliberatelyNotSeeded,
                "AudioEffectsState.$name is neither seeded by play() nor declared " +
                    "deliberately-not-seeded — decide its membership in " +
                    "AudioEffectsController.seedForPlayback and record it here",
            )
        }
        assertEquals(emptySet(), seededByPlayback.keys - declaredFields, "stale seeded entries")
        assertEquals(emptySet(), deliberatelyNotSeeded.keys - declaredFields, "stale not-seeded entries")
    }

    /** Fields [AudioEffectsController.seedForPlayback] feeds from the prefs slices. */
    private val seededByPlayback = mapOf(
        "dialogueBoostStrength" to "effects slice → manager",
        "nightModeStrength" to "effects slice → manager",
        "bassBoostStrength" to "effects slice → manager",
        "virtualizerStrength" to "effects slice → manager",
        "lrBalance" to "effects slice → manager",
        "pitchSemitones" to "effects slice → manager",
        "autoEqByGenre" to "effects slice → manager",
        "normalizationMode" to "audio slice (audioNormalizationMode) → manager replayGain",
        "preAmpDb" to "audio slice (replayGainPreAmpDb) → manager",
    )

    /** Fields deliberately NOT re-seeded per track (manager owns their startup state). */
    private val deliberatelyNotSeeded = mapOf(
        "dialogueBoostEnabled" to "manager startup state; flow mirrors into uiState",
        "nightModeEnabled" to "manager startup state; flow mirrors into uiState",
        "equalizerEnabled" to "manager startup state; flow mirrors into uiState",
        "equalizerSettings" to "manager startup state; flow mirrors into uiState",
        "equalizerPreset" to "manager startup state; flow mirrors into uiState",
        "bassBoostEnabled" to "manager startup state; flow mirrors into uiState",
        "virtualizerEnabled" to "manager startup state; flow mirrors into uiState",
        "reverbPreset" to "manager startup state; flow mirrors into uiState",
    )
}

/**
 * Processor-semantics fake: setters mutate real state synchronously (that is
 * exactly the contract the controller's "read .value after apply" persistence
 * relies on, on both the Android processor and the desktop state machine).
 */
private class FakeEffectsManager : AudioEffectsManager {

    /**
     * Chronological apply-leg log for the ordering pins (the store mocks append
     * their own "persist" entries, so one timeline carries both legs). Only the
     * setters an ordering test drives need to log.
     */
    val applyLog = mutableListOf<String>()

    val nightModeEnabledFlow = MutableStateFlow(false)
    override val nightModeEnabled: StateFlow<Boolean> get() = nightModeEnabledFlow

    val dialogueBoostEnabledFlow = MutableStateFlow(false)
    override val dialogueBoostEnabled: StateFlow<Boolean> get() = dialogueBoostEnabledFlow

    val equalizerEnabledFlow = MutableStateFlow(false)
    override val equalizerEnabled: StateFlow<Boolean> get() = equalizerEnabledFlow

    val equalizerSettingsFlow = MutableStateFlow(EqualizerSettings())
    override val equalizerSettings: StateFlow<EqualizerSettings> get() = equalizerSettingsFlow

    val equalizerPresetFlow = MutableStateFlow(EqualizerPreset.FLAT)
    override val equalizerPreset: StateFlow<EqualizerPreset> get() = equalizerPresetFlow

    val bassBoostEnabledFlow = MutableStateFlow(false)
    override val bassBoostEnabled: StateFlow<Boolean> get() = bassBoostEnabledFlow

    var bassBoostStrengthInternal = EffectStrength.MODERATE
    override val bassBoostStrengthState: EffectStrength get() = bassBoostStrengthInternal

    var dialogueBoostStrengthInternal = EffectStrength.MODERATE
    override val dialogueBoostStrengthState: EffectStrength get() = dialogueBoostStrengthInternal

    var nightModeStrengthInternal = EffectStrength.MODERATE
    override val nightModeStrengthState: EffectStrength get() = nightModeStrengthInternal

    val virtualizerEnabledFlow = MutableStateFlow(false)
    override val virtualizerEnabled: StateFlow<Boolean> get() = virtualizerEnabledFlow

    val virtualizerStrengthFlow = MutableStateFlow(500)
    override val virtualizerStrength: StateFlow<Int> get() = virtualizerStrengthFlow

    val reverbPresetFlow = MutableStateFlow(ReverbPreset.NONE)
    override val reverbPresetState: StateFlow<ReverbPreset> get() = reverbPresetFlow

    val lrBalanceFlow = MutableStateFlow(0f)
    override val lrBalance: StateFlow<Float> get() = lrBalanceFlow

    val pitchSemitonesFlow = MutableStateFlow(0f)
    override val pitchSemitones: StateFlow<Float> get() = pitchSemitonesFlow

    val autoEqByGenreFlow = MutableStateFlow(false)
    override val autoEqByGenre: StateFlow<Boolean> get() = autoEqByGenreFlow

    override val fftData: StateFlow<ByteArray> = MutableStateFlow(ByteArray(0))
    override val waveformData: StateFlow<ByteArray> = MutableStateFlow(ByteArray(0))

    val replayGainModeFlow = MutableStateFlow(AudioNormalizationMode.NONE)
    override val replayGainMode: StateFlow<AudioNormalizationMode> get() = replayGainModeFlow

    val replayGainPreAmpDbFlow = MutableStateFlow(0f)
    override val replayGainPreAmpDb: StateFlow<Float> get() = replayGainPreAmpDbFlow

    val channelMixModeFlow = MutableStateFlow(ChannelMixMode.AUTO)
    override val channelMixMode: StateFlow<ChannelMixMode> get() = channelMixModeFlow

    val channelMixEnabledFlow = MutableStateFlow(false)
    override val channelMixEnabled: StateFlow<Boolean> get() = channelMixEnabledFlow

    var nightModeVolume = 0.4f
    var nightModeGain = 1200

    override fun toggleNightMode() {
        nightModeEnabledFlow.value = !nightModeEnabledFlow.value
    }

    override fun toggleDialogueBoost() {
        dialogueBoostEnabledFlow.value = !dialogueBoostEnabledFlow.value
    }

    override fun setDialogueBoostStrength(strength: EffectStrength) {
        dialogueBoostStrengthInternal = strength
    }

    override fun setNightModeStrength(strength: EffectStrength) {
        nightModeStrengthInternal = strength
    }

    override fun toggleEqualizer() {
        equalizerEnabledFlow.value = !equalizerEnabledFlow.value
    }

    override fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        val newLevels = equalizerSettingsFlow.value.bandLevels.toMutableList()
        if (bandIndex in newLevels.indices) {
            newLevels[bandIndex] = levelDb
            equalizerSettingsFlow.value = EqualizerSettings(newLevels)
            equalizerPresetFlow.value = EqualizerPreset.CUSTOM
        }
    }

    override fun resetEqualizer() {
        equalizerSettingsFlow.value = EqualizerSettings()
        equalizerPresetFlow.value = EqualizerPreset.FLAT
    }

    override fun setNightModeParams(volume: Float, gain: Int) {
        nightModeVolume = volume
        nightModeGain = gain
    }

    override fun setReplayGainMode(mode: AudioNormalizationMode) {
        replayGainModeFlow.value = mode
    }

    override fun setReplayGainPreAmpDb(db: Float) {
        replayGainPreAmpDbFlow.value = db
    }

    override fun setChannelMix(mode: ChannelMixMode, enabled: Boolean) {
        channelMixModeFlow.value = mode
        channelMixEnabledFlow.value = enabled
    }

    override fun setEqualizerPreset(preset: EqualizerPreset) {
        equalizerPresetFlow.value = preset
    }

    override fun toggleBassBoost() {
        bassBoostEnabledFlow.value = !bassBoostEnabledFlow.value
    }

    override fun setBassBoostStrength(strength: EffectStrength) {
        bassBoostStrengthInternal = strength
        applyLog += "apply"
    }

    override fun toggleVirtualizer() {
        virtualizerEnabledFlow.value = !virtualizerEnabledFlow.value
    }

    override fun setVirtualizerStrength(strength: Int) {
        virtualizerStrengthFlow.value = strength
    }

    override fun setReverbPreset(preset: ReverbPreset) {
        reverbPresetFlow.value = preset
    }

    override fun setLrBalance(balance: Float) {
        lrBalanceFlow.value = balance
        applyLog += "apply"
    }

    override fun setPitchSemitones(semitones: Float) {
        pitchSemitonesFlow.value = semitones
    }

    override fun setAutoEqByGenre(enabled: Boolean) {
        autoEqByGenreFlow.value = enabled
    }

    override fun applyAutoEqForGenre(genres: List<String>?) = Unit

    override fun enableVisualizer(enabled: Boolean) = Unit
}
