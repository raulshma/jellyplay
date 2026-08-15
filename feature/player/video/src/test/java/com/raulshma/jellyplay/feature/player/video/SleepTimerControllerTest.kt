package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.state.SleepTimerState
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

/**
 * Tests for [SleepTimerController] after state ownership moved into the
 * controller: the test surface is the controller's [SleepTimerState]
 * flow + its commands — no [VideoPlayerUiState], no ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {

    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var audioStore: AudioStore
    private lateinit var engine: MediaEngine
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
        )
    }

    @Test
    fun startSleepTimer_capturesVolume_configuresManager_andUpdatesState() {
        controller.startSleepTimer(15_000L)

        coVerify { audioStore.setSleepTimerDurationMs(15_000L) }
        coVerify { audioStore.setSleepTimerEndOfEpisode(false) }

        verify { sleepTimerManager.start(15_000L) }
        assertEquals(
            SleepTimerState(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = false,
                sleepTimerLastUsedDurationMs = 15_000L,
            ),
            controller.state.value,
        )

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
        assertTrue(controller.state.value.sleepTimerActive)
        assertTrue(controller.state.value.sleepTimerEndOfEpisode)
    }

    @Test
    fun cancelSleepTimer_restoresPreFadeVolume_andClearsActiveState() {
        // Start timer first to capture 0.8f volume
        controller.startSleepTimer(30_000L)

        // Cancel timer
        controller.cancelSleepTimer()

        verify { sleepTimerManager.cancel() }
        verify { engine.setVolume(0.8f) }
        assertFalse(controller.state.value.sleepTimerActive)
        assertFalse(controller.state.value.sleepTimerEndOfEpisode)
    }

    @Test
    fun triggerSleepTimerEndOfEpisode_delegatesToManager() {
        controller.triggerSleepTimerEndOfEpisode()
        verify { sleepTimerManager.triggerEndOfEpisode() }
    }

    /**
     * Item-switch semantics: a running timer deliberately
     * PERSISTS across episodes — the former reset whitelist kept these three
     * fields. There is no resetForItem(); this test pins that default.
     */
    @Test
    fun `item switch does not reset an active timer`() {
        controller.startSleepTimer(20_000L)
        controller.startSleepTimerEndOfEpisode()

        // The item-switch path (releaseInternals) performs no reset call on the
        // sleep-timer slice — state must be unchanged afterwards.
        assertEquals(
            SleepTimerState(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = true,
                sleepTimerLastUsedDurationMs = 20_000L,
            ),
            controller.state.value,
        )
    }

    /** Release detaches the fade callback so a released engine is never touched. */
    @Test
    fun `release clears fade callback`() {
        controller.startSleepTimer(20_000L)
        controller.onRelease()
        verify { sleepTimerManager.setOnFadeProgress(null) }
    }

    /** Prefs seed (the former SettingsProjector projection of the last-used duration). */
    @Test
    fun `seedLastUsedDurationMs updates only when different`() {
        controller.startSleepTimer(30_000L)
        assertEquals(30_000L, controller.state.value.sleepTimerLastUsedDurationMs)

        // Same value: no re-emission (state object identity preserved).
        val before = controller.state.value
        controller.seedLastUsedDurationMs(30_000L)
        assertTrue(before === controller.state.value)

        controller.seedLastUsedDurationMs(60_000L)
        assertEquals(60_000L, controller.state.value.sleepTimerLastUsedDurationMs)
    }
}
