package com.raulshma.jellyplay.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the idle state machine: the [shouldEnterIdle] decision ladder
 * (feature gate, playback veto, window-active veto, debounce) and the
 * [DesktopIdleMonitor]'s input-reset / playback-cancel transitions over
 * fake probes and a virtual clock.
 */
class DesktopIdleMonitorTest {

    private fun settings(enabled: Boolean = true, timeoutMin: Long = 5L) =
        IdleAmbientSettings(enabled = enabled, timeoutMin = timeoutMin)

    // ── shouldEnterIdle: the decision ladder ─────────────────────────────

    @Test
    fun `enters idle after the debounce with nothing playing and the window active`() {
        assertTrue(
            shouldEnterIdle(
                enabled = true, timeoutMin = 5L,
                lastInputMs = 0L, nowMs = 5 * 60_000L,
                audioPlaying = false, videoActive = false, windowActive = true,
            ),
        )
    }

    @Test
    fun `one millisecond before the debounce does not enter`() {
        assertFalse(
            shouldEnterIdle(
                enabled = true, timeoutMin = 5L,
                lastInputMs = 0L, nowMs = 5 * 60_000L - 1,
                audioPlaying = false, videoActive = false, windowActive = true,
            ),
        )
    }

    @Test
    fun `disabled feature or zero timeout never idles`() {
        assertFalse(
            shouldEnterIdle(
                enabled = false, timeoutMin = 5L,
                lastInputMs = 0L, nowMs = 60 * 60_000L,
                audioPlaying = false, videoActive = false, windowActive = true,
            ),
        )
        assertFalse(
            shouldEnterIdle(
                enabled = true, timeoutMin = 0L,
                lastInputMs = 0L, nowMs = 60 * 60_000L,
                audioPlaying = false, videoActive = false, windowActive = true,
            ),
        )
    }

    @Test
    fun `active audio or video playback vetoes idle`() {
        for ((audio, video) in listOf(true to false, false to true)) {
            assertFalse(
                shouldEnterIdle(
                    enabled = true, timeoutMin = 1L,
                    lastInputMs = 0L, nowMs = 60 * 60_000L,
                    audioPlaying = audio, videoActive = video, windowActive = true,
                ),
                "audio=$audio video=$video",
            )
        }
    }

    @Test
    fun `an inactive window never idles`() {
        assertFalse(
            shouldEnterIdle(
                enabled = true, timeoutMin = 1L,
                lastInputMs = 0L, nowMs = 60 * 60_000L,
                audioPlaying = false, videoActive = false, windowActive = false,
            ),
        )
    }

    // ── DesktopIdleMonitor: transitions ──────────────────────────────────

    @Test
    fun `user input resets the debounce and dismisses a shown overlay`() {
        var now = 0L
        var playing = false
        val monitor = DesktopIdleMonitor(
            settings = { settings(enabled = true, timeoutMin = 1L) },
            isAudioPlaying = { playing },
            isVideoActive = { false },
            isWindowActive = { true },
            nowMs = { now },
        )

        now = 60_000L
        monitor.tick()
        assertTrue(monitor.isIdle.value, "one idle minute enters")

        now = 60_100L
        monitor.onUserInput()
        assertFalse(monitor.isIdle.value, "input dismisses immediately")

        now = 60_200L
        monitor.tick()
        assertFalse(monitor.isIdle.value, "the debounce restarted at the input stamp")

        now = 60_100L + 60_000L
        monitor.tick()
        assertTrue(monitor.isIdle.value, "a full minute after the input re-enters")
    }

    @Test
    fun `playback start cancels the idle state on the next tick`() {
        var now = 0L
        var playing = false
        val monitor = DesktopIdleMonitor(
            settings = { settings(enabled = true, timeoutMin = 1L) },
            isAudioPlaying = { playing },
            isVideoActive = { false },
            isWindowActive = { true },
            nowMs = { now },
        )

        now = 60_000L
        monitor.tick()
        assertTrue(monitor.isIdle.value)

        playing = true
        now = 61_000L
        monitor.tick()
        assertFalse(monitor.isIdle.value, "playback cancels the idle overlay")
    }

    @Test
    fun `a settings change to off cancels on the next tick`() {
        var now = 0L
        var current = settings(enabled = true, timeoutMin = 1L)
        val monitor = DesktopIdleMonitor(
            settings = { current },
            isAudioPlaying = { false },
            isVideoActive = { false },
            isWindowActive = { true },
            nowMs = { now },
        )

        now = 60_000L
        monitor.tick()
        assertTrue(monitor.isIdle.value)

        current = settings(enabled = false, timeoutMin = 1L)
        now = 61_000L
        monitor.tick()
        assertFalse(monitor.isIdle.value)
    }
}
