package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {

    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var audioStore: AudioStore
    private lateinit var engine: MediaEngine
    private var uiState = VideoPlayerUiState()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var controller: SleepTimerController

    @Before
    fun setUp() {
        sleepTimerManager = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        every { engine.volume } returns 0.8f

        controller = SleepTimerController(
            sleepTimerManager = sleepTimerManager,
            audioStore = audioStore,
            scope = testScope,
            getEngine = { engine },
            isMuted = { false },
            updateUiState = { transform -> uiState = transform(uiState) },
        )
    }

    @Test
    fun startSleepTimer_capturesVolume_configuresManager_andUpdatesUiState() {
        controller.startSleepTimer(15_000L)

        coVerify { audioStore.setSleepTimerDurationMs(15_000L) }
        coVerify { audioStore.setSleepTimerEndOfEpisode(false) }

        verify { sleepTimerManager.start(15_000L) }
        assertTrue(uiState.sleepTimerActive)
        assertFalse(uiState.sleepTimerEndOfEpisode)
        assertEquals(15_000L, uiState.sleepTimerLastUsedDurationMs)

        // Verify timer expiration callback invokes pause on engine
        val expireSlot = slot<() -> Unit>()
        verify { sleepTimerManager.setOnTimerExpired(capture(expireSlot)) }
        expireSlot.captured.invoke()
        verify { engine.pause() }
    }

    @Test
    fun startSleepTimerEndOfEpisode_configuresManagerAndUpdatesState() {
        controller.startSleepTimerEndOfEpisode()

        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
        verify { sleepTimerManager.startEndOfEpisode() }
        assertTrue(uiState.sleepTimerActive)
        assertTrue(uiState.sleepTimerEndOfEpisode)
    }

    @Test
    fun cancelSleepTimer_restoresPreFadeVolume_andClearsActiveState() {
        // Start timer first to capture 0.8f volume
        controller.startSleepTimer(30_000L)

        // Cancel timer
        controller.cancelSleepTimer()

        verify { sleepTimerManager.cancel() }
        verify { engine.setVolume(0.8f) }
        assertFalse(uiState.sleepTimerActive)
        assertFalse(uiState.sleepTimerEndOfEpisode)
    }

    @Test
    fun triggerSleepTimerEndOfEpisode_delegatesToManager() {
        controller.triggerSleepTimerEndOfEpisode()
        verify { sleepTimerManager.triggerEndOfEpisode() }
    }
}
