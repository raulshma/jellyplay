package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.SleepCountdown
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Tests for [SleepTimerController] after state ownership moved into the
 * controller: the test surface is the controller's [SleepTimerState]
 * flow + its commands — no [VideoPlayerUiState], no ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerControllerTest {

    private lateinit var sleepCountdown: SleepCountdown
    private lateinit var audioStore: AudioStore
    private lateinit var engine: MediaEngine
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var controller: SleepTimerController

    @BeforeTest
    fun setUp() {
        sleepCountdown = mockk<SleepCountdown>(relaxed = true)
        audioStore = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        every { engine.volume } returns 0.8f

        controller = SleepTimerController(
            sleepCountdown = sleepCountdown,
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

        verify { sleepCountdown.startSleepTimer(15_000L) }
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
        verify { sleepCountdown.setOnTimerExpired(capture(expireSlot)) }
        expireSlot.captured.invoke()
        verify { engine.pause() }
    }

    @Test
    fun startSleepTimerEndOfEpisode_configuresManagerAndUpdatesState() {
        controller.startSleepTimerEndOfEpisode()

        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
        verify { sleepCountdown.startEndOfEpisodeTimer() }
        assertTrue(controller.state.value.sleepTimerActive)
        assertTrue(controller.state.value.sleepTimerEndOfEpisode)
    }

    @Test
    fun cancelSleepTimer_restoresPreFadeVolume_andClearsActiveState() {
        // Start timer first to capture 0.8f volume
        controller.startSleepTimer(30_000L)

        // Cancel timer
        controller.cancelSleepTimer()

        verify { sleepCountdown.cancelSleepTimer() }
        // the restore is PROGRAMMATIC (isUserChange = false) — a
        // cancelled fade must never overwrite the remembered volume bucket.
        verify { engine.setVolume(0.8f, isUserChange = false) }
        assertFalse(controller.state.value.sleepTimerActive)
        assertFalse(controller.state.value.sleepTimerEndOfEpisode)
    }

    @Test
    fun triggerSleepTimerEndOfEpisode_delegatesToManager() {
        controller.triggerSleepTimerEndOfEpisode()
        verify { sleepCountdown.triggerEndOfEpisode() }
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
        verify { sleepCountdown.setOnExpiring(null) }
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
