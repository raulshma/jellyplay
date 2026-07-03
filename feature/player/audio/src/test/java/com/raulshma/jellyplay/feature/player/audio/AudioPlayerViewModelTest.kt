package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.UserPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AudioPlayerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var playbackRepository: PlaybackRepository
    private lateinit var sleepTimerManager: SleepTimerManager

    private lateinit var viewModel: AudioPlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        audioPlaybackManager = mockk(relaxed = true)
        preferencesStore = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        downloadRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        sleepTimerManager = mockk(relaxed = true)

        every { preferencesStore.preferences } returns MutableStateFlow(UserPreferences())

        viewModel = AudioPlayerViewModel(
            audioPlaybackManager = audioPlaybackManager,
            preferencesStore = preferencesStore,
            mediaRepository = mediaRepository,
            downloadRepository = downloadRepository,
            playbackRepository = playbackRepository,
            sleepTimerManager = sleepTimerManager,
        )
    }

    @After
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
        verify { audioPlaybackManager.seekTo(12_000L) }
    }

    @Test
    fun togglePlayPause_delegatesToManager() {
        viewModel.togglePlayPause()
        verify { audioPlaybackManager.togglePlayPause() }
    }

    @Test
    fun changePlaybackSpeed_delegatesToManager() {
        viewModel.changePlaybackSpeed(1.25f)
        verify { audioPlaybackManager.changePlaybackSpeed(1.25f) }
    }

    @Test
    fun toggleShuffle_delegatesToManager() {
        viewModel.toggleShuffle()
        verify { audioPlaybackManager.toggleShuffle() }
    }

    @Test
    fun cycleRepeatMode_delegatesToManager() {
        viewModel.cycleRepeatMode()
        verify { audioPlaybackManager.cycleRepeatMode() }
    }

    @Test
    fun skipToNext_delegatesToManager() {
        viewModel.skipToNext()
        verify { audioPlaybackManager.skipToNext() }
    }

    @Test
    fun skipToPrevious_delegatesToManager() {
        viewModel.skipToPrevious()
        verify { audioPlaybackManager.skipToPrevious() }
    }

    @Test
    fun playFromQueue_delegatesToManager() {
        viewModel.playFromQueue(3)
        verify { audioPlaybackManager.playFromQueue(3) }
    }

    @Test
    fun setDialogueBoostStrength_updatesStateAndDelegates() {
        viewModel.setDialogueBoostStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.effects.dialogueBoostStrength)
        verify { audioPlaybackManager.setDialogueBoostStrength(EffectStrength.HIGH) }
    }

    @Test
    fun setNightModeStrength_updatesStateAndDelegates() {
        viewModel.setNightModeStrength(EffectStrength.LOW)
        assertEquals(EffectStrength.LOW, viewModel.uiState.value.effects.nightModeStrength)
        verify { audioPlaybackManager.setNightModeStrength(EffectStrength.LOW) }
    }

    @Test
    fun toggleDialogueBoost_delegatesToManager() {
        viewModel.toggleDialogueBoost()
        verify { audioPlaybackManager.toggleDialogueBoost() }
    }

    @Test
    fun toggleNightMode_delegatesToManager() {
        viewModel.toggleNightMode()
        verify { audioPlaybackManager.toggleNightMode() }
    }

    @Test
    fun toggleEqualizer_delegatesToManager() {
        viewModel.toggleEqualizer()
        verify { audioPlaybackManager.toggleEqualizer() }
    }

    @Test
    fun setEqualizerBand_delegatesToManager() {
        viewModel.setEqualizerBand(2, 3)
        verify { audioPlaybackManager.setEqualizerBand(2, 3) }
    }

    @Test
    fun resetEqualizer_delegatesToManager() {
        viewModel.resetEqualizer()
        verify { audioPlaybackManager.resetEqualizer() }
    }

    @Test
    fun setReplayGainMode_delegatesToManager() {
        viewModel.setReplayGainMode(AudioNormalizationMode.TRACK)
        verify { audioPlaybackManager.setReplayGainMode(AudioNormalizationMode.TRACK) }
    }

    @Test
    fun setReplayGainPreAmpDb_delegatesToManager() {
        viewModel.setReplayGainPreAmpDb(-2.5f)
        verify { audioPlaybackManager.setReplayGainPreAmpDb(-2.5f) }
    }
}
