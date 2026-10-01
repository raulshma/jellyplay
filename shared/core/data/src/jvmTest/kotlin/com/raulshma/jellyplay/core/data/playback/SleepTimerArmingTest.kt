package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the shared [SleepTimerArming] core — the arm/cancel state machine both
 * player hosts' sleep-timer controllers previously hand-copied (the store
 * writes, the expiry/ramp callback arming over [SleepCountdown], and the
 * countdown dispatch). The load-bearing ordering: callbacks arm BEFORE the
 * countdown starts, so a zero-duration arm expires into a live callback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerArmingTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var sleepCountdown: SleepCountdown
    private lateinit var audioStore: AudioStore

    private lateinit var arming: SleepTimerArming

    private var expiryFired = 0
    private var lastFadeProgress: Float? = null

    @BeforeTest
    fun setUp() {
        sleepCountdown = mockk<SleepCountdown>(relaxed = true)
        audioStore = mockk(relaxed = true)
        expiryFired = 0
        lastFadeProgress = null
        arming = SleepTimerArming(
            sleepCountdown = sleepCountdown,
            audioStore = audioStore,
            scope = testScope,
            onExpirePause = { expiryFired++ },
        )
    }

    @Test
    fun armTimed_persistsLastUsed_clearsTheFlag_armsCallbacks_thenStarts() {
        val fade: (Float) -> Unit = { lastFadeProgress = it }

        arming.armTimed(15 * 60 * 1000L, fade = fade)

        coVerify { audioStore.setSleepTimerDurationMs(15 * 60 * 1000L) }
        coVerify { audioStore.setSleepTimerEndOfEpisode(false) }
        val expiredSlot = slot<() -> Unit>()
        val fadeSlot = slot<(Float) -> Unit>()
        verify(exactly = 1) { sleepCountdown.setOnTimerExpired(capture(expiredSlot)) }
        verify(exactly = 1) { sleepCountdown.setOnExpiring(capture(fadeSlot)) }
        // Callback arming precedes the countdown start (a zero-duration arm
        // must expire into a live callback).
        verifyOrder {
            sleepCountdown.setOnExpiring(any())
            sleepCountdown.startSleepTimer(any(), any())
        }

        // The armed callbacks route back to the host's pause + fade lambda.
        expiredSlot.captured.invoke()
        assertEquals(1, expiryFired)
        fadeSlot.captured.invoke(0.5f)
        assertEquals(0.5f, lastFadeProgress)
    }

    @Test
    fun armEndOfEpisode_persistsTheFlag_clearsAnyRamp_andStartsTheBoundaryArm() {
        arming.armEndOfEpisode()

        coVerify { audioStore.setSleepTimerEndOfEpisode(true) }
        verify(exactly = 1) { sleepCountdown.setOnTimerExpired(any()) }
        verify(exactly = 1) { sleepCountdown.setOnExpiring(null) }
        verify(exactly = 1) { sleepCountdown.startEndOfEpisodeTimer() }
        verify(exactly = 0) { sleepCountdown.startSleepTimer(any(), any()) }
    }

    @Test
    fun disarm_andTriggerEndOfEpisode_delegateToTheCountdown() {
        arming.disarm()
        verify(exactly = 1) { sleepCountdown.cancelSleepTimer() }

        arming.triggerEndOfEpisode()
        verify(exactly = 1) { sleepCountdown.triggerEndOfEpisode() }
    }

    @Test
    fun release_clearsTheRampOnly() {
        arming.release()

        verify(exactly = 1) { sleepCountdown.setOnExpiring(null) }
        verify(exactly = 0) { sleepCountdown.cancelSleepTimer() }
        assertNull(lastFadeProgress)
    }
}
