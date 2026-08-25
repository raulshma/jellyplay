package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Moved from legacy `:core:data` JUnit4 to shared kotlin.test with the impl:
 * the monotonic clock now rides the [TimeSource] seam (the legacy build read
 * `SystemClock.elapsedRealtime`, which the old module's unit-test preset
 * stubbed to 0). The fake below pins elapsed time explicitly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    /** Hand fake: elapsed monotonic millis the manager will read. */
    private class FakeTimeSource(var elapsedMillis: Long = 0L) : TimeSource {
        override fun nowEpochMillis(): Long = elapsedMillis
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
        override fun nowElapsedRealtimeMillis(): Long = elapsedMillis
    }

    private val testDispatcher = StandardTestDispatcher()
    private val timeSource = FakeTimeSource()
    private lateinit var manager: SleepTimerManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        manager = SleepTimerManager(timeSource)
    }

    @AfterTest
    fun tearDown() {
        manager.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun start_setsActiveAndRemaining() {
        manager.start(60_000L)

        assertTrue(manager.isActive.value)
        assertEquals(60_000L, manager.remainingMs.value)
        assertFalse(manager.isEndOfEpisodeMode.value)
    }

    @Test
    fun cancel_resetsState() {
        manager.start(60_000L)
        manager.cancel()

        assertFalse(manager.isActive.value)
        assertEquals(0L, manager.remainingMs.value)
    }

    @Test
    fun cancel_firesFadeProgress_withFullVolume() {
        val fadeValues = mutableListOf<Float>()
        manager.setOnFadeProgress { fadeValues.add(it) }

        manager.start(60_000L)
        manager.cancel()

        assertTrue(fadeValues.isNotEmpty())
        assertEquals(1f, fadeValues.last())
    }

    @Test
    fun endOfEpisodeMode_setsCorrectState() {
        manager.startEndOfEpisode()

        assertTrue(manager.isActive.value)
        assertTrue(manager.isEndOfEpisodeMode.value)
        assertEquals(0L, manager.remainingMs.value)
    }

    @Test
    fun displayText_activeTimer() {
        manager.start(3_661_000L)
        val text = manager.getDisplayText()
        assertEquals("1:01:01", text)
    }

    @Test
    fun displayText_endOfEpisodeMode() {
        manager.startEndOfEpisode()
        assertEquals("End of episode", manager.getDisplayText())
    }

    @Test
    fun displayText_inactive() {
        assertEquals("", manager.getDisplayText())
    }

    @Test
    fun fadeProgress_callbackSetAndCleared() {
        val fadeValues = mutableListOf<Float>()
        manager.setOnFadeProgress { fadeValues.add(it) }

        manager.start(60_000L)
        assertTrue(manager.isActive.value)

        manager.setOnFadeProgress(null)
        manager.cancel()
        assertTrue(fadeValues.isEmpty() || fadeValues.last() == 1f)
    }

    @Test
    fun start_withFadeOutDuration_setsCorrectState() {
        manager.start(5_000L, 2_000L)

        assertTrue(manager.isActive.value)
        assertEquals(5_000L, manager.remainingMs.value)
    }

    @Test
    fun cancel_afterFadeStarted_restoresVolume() {
        val fadeValues = mutableListOf<Float>()
        manager.setOnFadeProgress { fadeValues.add(it) }

        manager.start(1_000L, 500L)
        manager.cancel()

        assertTrue(fadeValues.isNotEmpty())
        assertEquals(1f, fadeValues.last())
    }

    @Test
    fun multipleStartCancels_previousTimerStopped() {
        manager.start(60_000L)
        assertTrue(manager.isActive.value)

        manager.start(30_000L)
        assertTrue(manager.isActive.value)
        assertEquals(30_000L, manager.remainingMs.value)

        manager.cancel()
        assertFalse(manager.isActive.value)
    }
}
