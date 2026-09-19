package com.raulshma.jellyplay.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Pins [ReaderSleepTimer]: the 1 s-tick countdown fires once and resets,
 * cancel disarms, END_OF_CHAPTER fires only on a genuinely different chapter
 * label (page turns within a chapter re-report the same label), arming before
 * any location exists adopts the first label as the armed chapter, and a
 * restart while running replaces the arm. Virtual-time advancement drives
 * the countdown (the ticker rides `delay`, never wall-clock reads).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSleepTimerTest {

    @Test
    fun `timed countdown ticks down and fires once`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.start(ReaderSleepOption.Timed(minutes = 5))
        assertTrue(timer.state.value.running)
        assertEquals(ReaderSleepOption.Timed(5), timer.state.value.option)
        assertEquals(5 * 60_000L, timer.state.value.remainingMillis)

        // runCurrent after advanceTimeBy: the boundary tick scheduled exactly
        // at the new clock position only runs on it.
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4 * 60_000L, timer.state.value.remainingMillis)

        advanceTimeBy(4 * 60_000)
        runCurrent()
        assertEquals(1, fired)
        assertEquals(ReaderSleepTimerState(), timer.state.value)

        // No double fire after the expiry.
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun `cancel disarms the countdown`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.start(ReaderSleepOption.Timed(minutes = 5))
        advanceTimeBy(60_000)
        runCurrent()
        timer.cancel()
        assertEquals(ReaderSleepTimerState(), timer.state.value)

        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(0, fired)
    }

    @Test
    fun `restart while running replaces the arm`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.start(ReaderSleepOption.Timed(minutes = 5))
        advanceTimeBy(60_000)
        runCurrent()
        timer.start(ReaderSleepOption.Timed(minutes = 15))
        assertEquals(15 * 60_000L, timer.state.value.remainingMillis)

        // The 5-minute arm is gone: advancing past it must not fire.
        advanceTimeBy(5 * 60_000)
        runCurrent()
        assertTrue(timer.state.value.running)
        assertEquals(0, fired)

        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertEquals(1, fired)
    }

    @Test
    fun `end of chapter fires on a different label only`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.onChapterLabel("Chapter One")
        timer.start(ReaderSleepOption.EndOfChapter)
        assertTrue(timer.state.value.running)
        assertNull(timer.state.value.remainingMillis)

        // Page turns inside the chapter re-report the same label.
        timer.onChapterLabel("Chapter One")
        assertTrue(timer.state.value.running)

        timer.onChapterLabel("Chapter Two")
        assertEquals(1, fired)
        assertEquals(ReaderSleepTimerState(), timer.state.value)
    }

    @Test
    fun `arming before a location adopts the first label as the armed chapter`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.start(ReaderSleepOption.EndOfChapter)
        timer.onChapterLabel("Chapter One") // must arm, not fire
        assertTrue(timer.state.value.running)
        assertEquals(0, fired)

        timer.onChapterLabel("Chapter Two")
        assertEquals(1, fired)
    }

    @Test
    fun `blank labels are ignored`() = runTest {
        var fired = 0
        val timer = ReaderSleepTimer(backgroundScope) { fired++ }

        timer.onChapterLabel("Chapter One")
        timer.start(ReaderSleepOption.EndOfChapter)
        timer.onChapterLabel("")
        timer.onChapterLabel("   ")
        assertTrue(timer.state.value.running)
        assertEquals(0, fired)
    }

    @Test
    fun `countdown label formats minutes seconds hours and ceils`() {
        assertEquals("5:00", formatSleepCountdown(5 * 60_000L))
        assertEquals("4:59", formatSleepCountdown(4 * 60_000L + 59_000L))
        assertEquals("12:05", formatSleepCountdown(12 * 60_000L + 4_500L)) // ceil
        assertEquals("1:00:00", formatSleepCountdown(3_600_000L))
        assertEquals("1:59:59", formatSleepCountdown(3_600_000L + 3_599_000L))
        assertEquals("0:01", formatSleepCountdown(500L))
        assertEquals("0:00", formatSleepCountdown(0L))
    }
}
