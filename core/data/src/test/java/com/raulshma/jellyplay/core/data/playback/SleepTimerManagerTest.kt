package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var manager: SleepTimerManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        manager = SleepTimerManager()
    }

    @After
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
