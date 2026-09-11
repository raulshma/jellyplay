package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [AudioSleepTimerController]: the two start modes (store writes, manager
 * start, synchronous slice update), cancel, the end-of-episode trigger, and —
 * the load-bearing contract — expiry PAUSES the engine from ONE shared
 * callback (the explicit-pause rationale previously hand-copied at both VM
 * start sites; a toggle would RESUME a manually-paused player).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSleepTimerControllerTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var sleepTimerManager: SleepTimerManager
    private lateinit var audioStore: AudioStore
    private lateinit var engine: AudioPlayerEngine

    private lateinit var slice: SleepTimerState

    private lateinit var controller: AudioSleepTimerController

    @BeforeTest
    fun setUp() {
        sleepTimerManager = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        slice = SleepTimerState()
        controller = AudioSleepTimerController(
            scope = testScope,
            sleepTimerManager = sleepTimerManager,
            audioStore = audioStore,
            engine = engine,
            updateState = { transform -> slice = transform(slice) },
        )
    }

    @Test
    fun startSleepTimer_persistsLastUsed_armsExpiry_startsAndUpdatesTheSlice() {
        controller.startSleepTimer(15 * 60 * 1000L)

        coVerify { audioStore.setSleepTimerDurationMs(15 * 60 * 1000L) }
        coVerify { audioStore.setSleepTimerEndOfEpisode(false) }
        verify { sleepTimerManager.start(15 * 60 * 1000L) }
        assertEquals(
            SleepTimerState(active = true, endOfEpisode = false, lastUsedDurationMs = 15 * 60 * 1000L),
            slice,
        )
    }

    @Test
    fun startSleepTimerEndOfEpisode_persistsTheFlag_startsAndUpdatesTheSlice() {
        controller.startSleepTimerEndOfEpisode()

        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
        verify { sleepTimerManager.startEndOfEpisode() }
        assertEquals(SleepTimerState(active = true, endOfEpisode = true), slice)
    }

    /**
     * BOTH start modes arm the same expiry callback, and it must PAUSE — never
     * toggle (a toggle would resume playback the user had manually paused
     * after arming the timer).
     */
    @Test
    fun expiryCallback_pausesTheEngineExplicitly_forBothStartModes() {
        val expiries = mutableListOf<() -> Unit>()
        controller.startSleepTimer(60_000L)
        controller.startSleepTimerEndOfEpisode()
        verify(exactly = 2) { sleepTimerManager.setOnTimerExpired(capture(expiries)) }

        for (expiry in expiries) {
            expiry.invoke()
        }

        verify(exactly = 2) { engine.pause() }
        verify(exactly = 0) { engine.togglePlayPause() }
    }

    @Test
    fun cancelSleepTimer_cancelsTheManager_andClearsTheActiveFlags() {
        controller.startSleepTimer(1_000L)
        controller.startSleepTimerEndOfEpisode()

        controller.cancelSleepTimer()

        verify { sleepTimerManager.cancel() }
        // Cancel clears active/endOfEpisode but PRESERVES the last-used
        // duration (the picker keeps offering it) — the pre-extraction
        // behaviour, now pinned.
        assertEquals(
            SleepTimerState(active = false, endOfEpisode = false, lastUsedDurationMs = 1_000L),
            slice,
        )
        assertFalse(slice.active)
    }

    @Test
    fun triggerSleepTimerEndOfEpisode_delegatesToTheManager() {
        controller.triggerSleepTimerEndOfEpisode()

        verify(exactly = 1) { sleepTimerManager.triggerEndOfEpisode() }
    }
}
