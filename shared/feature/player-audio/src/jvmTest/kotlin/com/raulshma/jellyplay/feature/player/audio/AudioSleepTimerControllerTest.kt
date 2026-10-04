package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import io.mockk.coVerify
import io.mockk.every
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
 *
 * Fade pins: the timed arm captures the pre-fade level and ramps the
 * engine's software volume programmatically, cancel restores the captured
 * level, expiry pauses THEN restores (the audio semantic: resuming tomorrow
 * must not start silent), and the end-of-episode arm clears the capture so no
 * stale level can be restored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSleepTimerControllerTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var sleepCountdown: SleepCountdown
    private lateinit var audioStore: AudioStore
    private lateinit var engine: AudioPlayerEngine

    private lateinit var slice: SleepTimerState

    private lateinit var controller: AudioSleepTimerController

    @BeforeTest
    fun setUp() {
        sleepCountdown = mockk<SleepCountdown>(relaxed = true)
        audioStore = mockk(relaxed = true)
        engine = mockk(relaxed = true)
        every { engine.volume } returns 0.8f
        slice = SleepTimerState()
        controller = AudioSleepTimerController(
            scope = testScope,
            sleepCountdown = sleepCountdown,
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
        verify { sleepCountdown.startSleepTimer(15 * 60 * 1000L) }
        assertEquals(
            SleepTimerState(active = true, endOfEpisode = false, lastUsedDurationMs = 15 * 60 * 1000L),
            slice,
        )
    }

    @Test
    fun startSleepTimerEndOfEpisode_persistsTheFlag_startsAndUpdatesTheSlice() {
        controller.startSleepTimerEndOfEpisode()

        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
        verify { sleepCountdown.startEndOfEpisodeTimer() }
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
        verify(exactly = 2) { sleepCountdown.setOnTimerExpired(capture(expiries)) }

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

        verify { sleepCountdown.cancelSleepTimer() }
        // Cancel clears active/endOfEpisode but PRESERVES the last-used
        // duration (the picker keeps offering it) — the pre-extraction
        // behaviour, now pinned.
        assertEquals(
            SleepTimerState(active = false, endOfEpisode = false, lastUsedDurationMs = 1_000L),
            slice,
        )
        assertFalse(slice.active)
    }

    /** The timed arm captures the engine's current level as the fade's restore point. */
    @Test
    fun startSleepTimer_capturesThePreFadeVolume() {
        controller.startSleepTimer(60_000L)

        verify { engine.volume }
    }

    /** Fade ticks write the ramp PROGRAMMATICALLY — never as a user level. */
    @Test
    fun fadeTick_writesVolumeAsAProgrammaticChange() {
        val fades = mutableListOf<(Float) -> Unit>()
        controller.startSleepTimer(60_000L)
        verify { sleepCountdown.setOnExpiring(capture(fades)) }

        fades.single().invoke(0.4f)

        verify { engine.setVolume(0.4f, isUserChange = false) }
    }

    /** Cancel restores the captured pre-fade level — programmatically. */
    @Test
    fun cancelSleepTimer_restoresThePreFadeVolume() {
        controller.startSleepTimer(60_000L)

        controller.cancelSleepTimer()

        verify { engine.setVolume(0.8f, isUserChange = false) }
    }

    /**
     * expiry PAUSES first, then restores the captured level — the audio
     * semantic the video host does not have (resuming tomorrow must not start
     * silent), and the restore is programmatic too.
     */
    @Test
    fun expiry_pausesThenRestoresThePreFadeVolume() {
        val expiries = mutableListOf<() -> Unit>()
        controller.startSleepTimer(60_000L)
        verify { sleepCountdown.setOnTimerExpired(capture(expiries)) }

        expiries.single().invoke()

        io.mockk.verifyOrder {
            engine.pause()
            engine.setVolume(0.8f, isUserChange = false)
        }
    }

    /**
     * the end-of-episode arm clears the capture — its expiry pauses but
     * restores NOTHING (no fade happened), mirroring the video controller's
     * capture/clear discipline.
     */
    @Test
    fun endOfEpisodeArm_clearsTheCapture_expiryRestoresNothing() {
        // A timed arm first (captures 0.8f), then the eoe arm must clear it.
        controller.startSleepTimer(60_000L)
        controller.startSleepTimerEndOfEpisode()

        val expiries = mutableListOf<() -> Unit>()
        verify { sleepCountdown.setOnTimerExpired(capture(expiries)) }
        expiries.last().invoke()

        verify { engine.pause() }
        verify(exactly = 0) { engine.setVolume(any(), isUserChange = false) }
    }

    /** A second timed arm re-captures — the restore point tracks the latest arm. */
    @Test
    fun reArming_reCapturesTheCurrentVolume() {
        controller.startSleepTimer(60_000L)
        every { engine.volume } returns 0.5f
        controller.startSleepTimer(120_000L)

        controller.cancelSleepTimer()

        verify { engine.setVolume(0.5f, isUserChange = false) }
    }
}
