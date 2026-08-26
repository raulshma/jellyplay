package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPlayerUiPreferences
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.ReverbPreset
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var queueManager: AudioQueueManager
    private lateinit var effectsManager: AudioEffectsManager
    private lateinit var engine: AudioPlayerEngine
    private lateinit var projections: PreferenceProjections
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var downloadIntake: DownloadIntake
    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var cast: AudioPlayerCast

    private lateinit var viewModel: AudioPlayerViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        queueManager = mockk(relaxed = true)
        effectsManager = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        downloadIntake = mockk(relaxed = true)
        sleepTimerManager = mockk(relaxed = true)
        cast = mockk(relaxed = true)

        every { projections.audioPlayerUiPreferences } returns MutableStateFlow(AudioPlayerUiPreferences())
        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(AudioEffectsSlice())

        viewModel = AudioPlayerViewModel(
            queueManager = queueManager,
            effectsManager = effectsManager,
            engine = engine,
            projections = projections,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            mediaRepository = mediaRepository,
            userDataMutator = userDataMutator,
            downloadRepository = downloadRepository,
            downloadIntake = downloadIntake,
            sleepTimerManager = sleepTimerManager,
            cast = cast,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setKaraokeModeEnabled_setsState() {
        viewModel.setKaraokeModeEnabled(true)
        assertTrue(viewModel.karaokeMode)
        assertTrue(viewModel.uiState.value.lyrics.karaokeMode)
        viewModel.setKaraokeModeEnabled(false)
        assertFalse(viewModel.karaokeMode)
        assertFalse(viewModel.uiState.value.lyrics.karaokeMode)
    }

    @Test
    fun toggleKaraokeMode_flipsState() {
        val before = viewModel.karaokeMode
        viewModel.toggleKaraokeMode()
        assertEquals(!before, viewModel.karaokeMode)
        viewModel.toggleKaraokeMode()
        assertEquals(before, viewModel.karaokeMode)
    }

    @Test
    fun hasKaraokeLyrics_falseWhenEmpty() {
        assertFalse(viewModel.uiState.value.lyrics.hasKaraokeLyrics)
    }

    @Test
    fun seekTo_delegatesToManager() {
        viewModel.seekTo(12_000L)
        verify { engine.seekTo(12_000L) }
    }

    /** Plan 03: the favorite toggle routes through the mutator; the resolved target drives the scalar flip. */
    @Test
    fun toggleFavorite_delegatesToMutatorAndFlipsScalarFromResolvedTarget() {
        every { queueManager.currentPlayingItemId } returns MutableStateFlow("track-1")
        io.mockk.coEvery {
            userDataMutator.setFavorite("track-1")
        } returns kotlin.Result.success(
            com.raulshma.jellyplay.core.data.repository.AppliedMutation("track-1", favorite = true),
        )

        viewModel.toggleFavorite()

        io.mockk.coVerify(exactly = 1) { userDataMutator.setFavorite("track-1") }
        assertTrue(viewModel.uiState.value.isFavorite)
    }

    @Test
    fun toggleFavorite_withoutCurrentItem_isNoOp() {
        every { queueManager.currentPlayingItemId } returns MutableStateFlow(null)

        viewModel.toggleFavorite()

        io.mockk.coVerify(exactly = 0) { userDataMutator.setFavorite(any()) }
    }

    @Test
    fun togglePlayPause_delegatesToManager() {
        viewModel.togglePlayPause()
        verify { engine.togglePlayPause() }
    }

    @Test
    fun changePlaybackSpeed_delegatesToManager() {
        viewModel.changePlaybackSpeed(1.25f)
        verify { engine.changePlaybackSpeed(1.25f) }
    }

    @Test
    fun toggleShuffle_delegatesToManager() {
        viewModel.toggleShuffle()
        verify { queueManager.toggleShuffle() }
    }

    @Test
    fun cycleRepeatMode_delegatesToManager() {
        viewModel.cycleRepeatMode()
        verify { queueManager.cycleRepeatMode() }
    }

    @Test
    fun skipToNext_delegatesToManager() {
        viewModel.skipToNext()
        verify { queueManager.skipToNext() }
    }

    @Test
    fun skipToPrevious_delegatesToManager() {
        viewModel.skipToPrevious()
        verify { queueManager.skipToPrevious() }
    }

    @Test
    fun playFromQueue_delegatesToManager() {
        viewModel.playFromQueue(3)
        verify { queueManager.playFromQueue(3) }
    }

    @Test
    fun setDialogueBoostStrength_updatesStateAndDelegates() {
        viewModel.setDialogueBoostStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.effects.dialogueBoostStrength)
        verify { effectsManager.setDialogueBoostStrength(EffectStrength.HIGH) }
    }

    @Test
    fun setNightModeStrength_updatesStateAndDelegates() {
        viewModel.setNightModeStrength(EffectStrength.LOW)
        assertEquals(EffectStrength.LOW, viewModel.uiState.value.effects.nightModeStrength)
        verify { effectsManager.setNightModeStrength(EffectStrength.LOW) }
    }

    @Test
    fun toggleDialogueBoost_delegatesToManager() {
        viewModel.toggleDialogueBoost()
        verify { effectsManager.toggleDialogueBoost() }
    }

    @Test
    fun toggleNightMode_delegatesToManager() {
        viewModel.toggleNightMode()
        verify { effectsManager.toggleNightMode() }
    }

    @Test
    fun toggleEqualizer_delegatesToManager() {
        viewModel.toggleEqualizer()
        verify { effectsManager.toggleEqualizer() }
    }

    @Test
    fun setEqualizerBand_delegatesToManager() {
        viewModel.setEqualizerBand(2, 3)
        verify { effectsManager.setEqualizerBand(2, 3) }
    }

    @Test
    fun resetEqualizer_delegatesToManager() {
        viewModel.resetEqualizer()
        verify { effectsManager.resetEqualizer() }
    }

    @Test
    fun setReplayGainMode_delegatesToManager() {
        viewModel.setReplayGainMode(AudioNormalizationMode.TRACK)
        verify { effectsManager.setReplayGainMode(AudioNormalizationMode.TRACK) }
    }

    @Test
    fun setReplayGainPreAmpDb_delegatesToManager() {
        viewModel.setReplayGainPreAmpDb(-2.5f)
        verify { effectsManager.setReplayGainPreAmpDb(-2.5f) }
    }

    // ─── Persistence side-effects (handlers also write to the domain stores) ─────

    @Test
    fun setDialogueBoostStrength_persistsToStore() {
        viewModel.setDialogueBoostStrength(EffectStrength.HIGH)
        coVerify { audioEffectsStore.setDialogueBoostStrength(EffectStrength.HIGH) }
    }

    @Test
    fun setNightModeStrength_persistsToStore() {
        viewModel.setNightModeStrength(EffectStrength.LOW)
        coVerify { audioEffectsStore.setNightModeStrength(EffectStrength.LOW) }
    }

    @Test
    fun setReplayGainMode_persistsToStore() {
        viewModel.setReplayGainMode(AudioNormalizationMode.ALBUM)
        verify { effectsManager.setReplayGainMode(AudioNormalizationMode.ALBUM) }
        coVerify { audioStore.setAudioNormalizationMode(AudioNormalizationMode.ALBUM) }
    }

    @Test
    fun toggleEqualizer_persistsEnabledFlagToStore() {
        viewModel.toggleEqualizer()
        coVerify { audioEffectsStore.setEqualizerEnabled(any()) }
    }

    @Test
    fun setEqualizerBand_persistsSettingsToStore() {
        viewModel.setEqualizerBand(bandIndex = 2, levelDb = 3)
        coVerify { audioEffectsStore.setEqualizerSettings(any()) }
    }

    @Test
    fun resetEqualizer_persistsSettingsAndPreset() {
        viewModel.resetEqualizer()
        coVerify { audioEffectsStore.setEqualizerSettings(any()) }
        coVerify { audioEffectsStore.setEqualizerPreset(any()) }
    }

    // ─── Untested effect handlers (bass / virtualizer / reverb / lr / pitch) ───

    @Test
    fun setBassBoostStrength_updatesStateDelegatesAndPersists() {
        viewModel.setBassBoostStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.effects.bassBoostStrength)
        verify { effectsManager.setBassBoostStrength(EffectStrength.HIGH) }
        coVerify { audioEffectsStore.setBassBoostStrength(EffectStrength.HIGH) }
    }

    @Test
    fun toggleBassBoost_delegatesAndPersists() {
        viewModel.toggleBassBoost()
        verify { effectsManager.toggleBassBoost() }
        coVerify { audioEffectsStore.setBassBoostEnabled(any()) }
    }

    @Test
    fun toggleVirtualizer_delegatesAndPersists() {
        viewModel.toggleVirtualizer()
        verify { effectsManager.toggleVirtualizer() }
        coVerify { audioEffectsStore.setVirtualizerEnabled(any()) }
    }

    @Test
    fun applyVirtualizerStrength_delegatesAndPersists() {
        viewModel.applyVirtualizerStrength(800)
        verify { effectsManager.setVirtualizerStrength(800) }
        coVerify { audioEffectsStore.setVirtualizerStrength(800) }
    }

    @Test
    fun applyReverbPreset_delegatesAndPersists() {
        viewModel.applyReverbPreset(ReverbPreset.LARGE_HALL)
        verify { effectsManager.setReverbPreset(ReverbPreset.LARGE_HALL) }
        coVerify { audioEffectsStore.setReverbPreset(ReverbPreset.LARGE_HALL) }
    }

    @Test
    fun applyLrBalance_delegatesAndPersists() {
        viewModel.applyLrBalance(-0.5f)
        verify { effectsManager.setLrBalance(-0.5f) }
        coVerify { audioEffectsStore.setLrBalance(-0.5f) }
    }

    @Test
    fun applyPitchSemitones_delegatesAndPersists() {
        viewModel.applyPitchSemitones(2f)
        verify { effectsManager.setPitchSemitones(2f) }
        coVerify { audioEffectsStore.setPitchSemitones(2f) }
    }

    @Test
    fun applyAutoEqByGenre_delegatesAndPersists() {
        viewModel.applyAutoEqByGenre(true)
        verify { effectsManager.setAutoEqByGenre(true) }
        coVerify { audioEffectsStore.setAutoEqByGenre(true) }
    }

    @Test
    fun applyEqualizerPreset_delegatesAndPersists() {
        viewModel.applyEqualizerPreset(EqualizerPreset.ROCK)
        verify { effectsManager.setEqualizerPreset(EqualizerPreset.ROCK) }
        coVerify { audioEffectsStore.setEqualizerPreset(EqualizerPreset.ROCK) }
    }

    @Test
    fun updateCrossfadeDuration_delegatesAndPersists() {
        viewModel.updateCrossfadeDuration(4_000L)
        verify { engine.setCrossfadeDurationMs(4_000L) }
        coVerify { audioStore.setAudioCrossfadeDurationMs(4_000L) }
    }

    @Test
    fun updateGaplessPlayback_delegatesAndPersists() {
        viewModel.updateGaplessPlayback(false)
        verify { engine.setGaplessEnabled(false) }
        coVerify { audioStore.setAudioGaplessEnabled(false) }
    }

    // ─── Sleep timer configuration ─────────────────────────────────────────────

    @Test
    fun startSleepTimer_activatesStateAndPersistsLastUsed() {
        viewModel.startSleepTimer(15 * 60 * 1000L)

        with(viewModel.uiState.value.sleepTimer) {
            assertTrue(active)
            assertFalse(endOfEpisode)
            assertEquals(15 * 60 * 1000L, lastUsedDurationMs)
        }
        verify { sleepTimerManager.setOnTimerExpired(any()) }
        verify { sleepTimerManager.start(15 * 60 * 1000L) }
        coVerify { audioStore.setSleepTimerDurationMs(15 * 60 * 1000L) }
        coVerify { audioStore.setSleepTimerEndOfEpisode(false) }
    }

    @Test
    fun startSleepTimerEndOfEpisode_activatesEndOfEpisodeState() {
        viewModel.startSleepTimerEndOfEpisode()

        with(viewModel.uiState.value.sleepTimer) {
            assertTrue(active)
            assertTrue(endOfEpisode)
        }
        verify { sleepTimerManager.setOnTimerExpired(any()) }
        verify { sleepTimerManager.startEndOfEpisode() }
        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
    }

    @Test
    fun cancelSleepTimer_clearsState() {
        viewModel.startSleepTimer(1_000L)
        viewModel.cancelSleepTimer()

        with(viewModel.uiState.value.sleepTimer) {
            assertFalse(active)
            assertFalse(endOfEpisode)
        }
        verify { sleepTimerManager.cancel() }
    }

    @Test
    fun triggerSleepTimerEndOfEpisode_delegatesToManager() {
        viewModel.triggerSleepTimerEndOfEpisode()
        verify { sleepTimerManager.triggerEndOfEpisode() }
    }

    // ─── Queue operations ──────────────────────────────────────────────────────

    @Test
    fun removeFromQueue_delegatesToManager() {
        viewModel.removeFromQueue(4)
        verify { queueManager.removeFromQueue(4) }
    }

    @Test
    fun cycleAbLoop_delegatesToManager() {
        viewModel.cycleAbLoop()
        verify { engine.cycleAbLoop() }
    }

    @Test
    fun stopPlayback_delegatesToManager() {
        viewModel.stopPlayback()
        verify { engine.stopAndRelease() }
    }

    @Test
    fun setLyricsVisible_persistsToStore() {
        viewModel.setLyricsVisible(true)
        coVerify { audioStore.setAudioLyricsVisible(true) }
    }

    @Test
    fun clearLyricsSearch_emptiesResults() {
        viewModel.clearLyricsSearch()
        assertTrue(viewModel.uiState.value.lyrics.searchResults.isEmpty())
    }

    // ─── Cast — early-return when no current item ──────────────────────────────

    @Test
    fun castToDevice_noCurrentItem_doesNothing() {
        every { queueManager.currentPlayingItemId } returns MutableStateFlow(null)
        viewModel.castToDevice()
        verify(exactly = 0) { cast.loadMedia(any(), any()) }
        verify(exactly = 0) { engine.pause() }
    }

    @Test
    fun castPlayPauseSeekVolume_delegateToCastManager() {
        viewModel.castPlay()
        viewModel.castPause()
        viewModel.castSeekTo(30_000L)
        viewModel.setCastVolume(0.7f)
        verify { cast.play() }
        verify { cast.pause() }
        verify { cast.seekTo(30_000L) }
        verify { cast.setVolume(0.7f) }
    }

    @Test
    fun play_appliesConfigFromPreferencesToManager() {
        every { audioStore.audio } returns MutableStateFlow(
            AudioSlice(
                audioDefaultSpeed = 1.5f,
                audioNightModeVolume = 0.6f,
                audioNightModeGain = 900,
                audioSkipPreviousThresholdMs = 5_000L,
            ),
        )
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(
            AudioEffectsSlice(
                dialogueBoostStrength = EffectStrength.HIGH,
                nightModeStrength = EffectStrength.LOW,
                bassBoostStrength = EffectStrength.MODERATE,
            ),
        )

        viewModel.play("item-1")

        verify { engine.play("item-1") }
        verify { engine.changePlaybackSpeed(1.5f) }
        verify { effectsManager.setNightModeParams(0.6f, 900) }
        verify { engine.setSkipPreviousThreshold(5_000L) }
        verify { effectsManager.setDialogueBoostStrength(EffectStrength.HIGH) }
        verify { effectsManager.setNightModeStrength(EffectStrength.LOW) }
        verify { effectsManager.setBassBoostStrength(EffectStrength.MODERATE) }
        assertEquals(0.6f, viewModel.nightModeVolume, 0.0001f)
        assertEquals(900, viewModel.nightModeGain)
        assertEquals(5_000L, viewModel.skipPreviousThresholdMs)
    }

    @Test
    fun play_defaultSpeed_skipsChangePlaybackSpeedCall() {
        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(AudioEffectsSlice())
        viewModel.play("item-2")
        verify(exactly = 0) { engine.changePlaybackSpeed(any()) }
    }

    @Test
    fun uiState_defaults_haveExpectedConfigurationDefaults() {
        with(viewModel.uiState.value) {
            assertEquals(1.0f, speed)
            assertFalse(isPlaying)
            assertEquals(0L, duration)
            with(effects) {
                assertEquals(EffectStrength.MODERATE, dialogueBoostStrength)
                assertEquals(EffectStrength.MODERATE, nightModeStrength)
                assertEquals(EffectStrength.MODERATE, bassBoostStrength)
                assertEquals(500, virtualizerStrength)
                assertEquals(ReverbPreset.NONE, reverbPreset)
                assertEquals(AudioNormalizationMode.NONE, normalizationMode)
                assertEquals(EqualizerPreset.FLAT, equalizerPreset)
            }
        }
    }

    @Test
    fun currentPlayingItemId_nullByDefault() {
        every { queueManager.currentPlayingItemId } returns MutableStateFlow(null)
        assertNull(viewModel.currentPlayingItemId)
    }
}
