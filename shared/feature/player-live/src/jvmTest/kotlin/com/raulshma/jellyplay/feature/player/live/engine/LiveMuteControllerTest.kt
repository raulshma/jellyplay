package com.raulshma.jellyplay.feature.player.live.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [LiveMuteController] — the live engine's host of the SHARED
 * volume/mute template ([com.raulshma.jellyplay.feature.player.video.engine.VolumeCommandTemplates])
 * — and, through it, live's declared divergences on the template call:
 * the EXACT pre-mute restore (no audible floor — the VOD policy's
 * `planUnmute` would floor 0.03 to 0.05), the empty-memory LEAVE_UNCHANGED
 * arm, the chip clear after unmute, and the reload re-assert that makes mute
 * real state instead of a volume-0 write the next load can silently lose.
 * The ViewModel routing that drives it is pinned by
 * LiveTvPlayerViewModelGapsTest.
 */
class LiveMuteControllerTest {

    /** Native handle double: the recorded writes are the template's observable output. */
    private class FakeHandle(initial: Float?) {
        val writes = mutableListOf<Float>()
        var level: Float? = initial

        fun controller(): LiveMuteController = LiveMuteController(
            readVolume = { level },
            writeVolume = { writes.add(it); level = it },
        )
    }

    @Test
    fun mute_capturesTheExactPreMuteLevel_andWritesZero() {
        val handle = FakeHandle(0.7f)
        val controller = handle.controller()

        controller.setMuted(true)

        assertTrue(controller.isMuted)
        assertEquals(listOf(0.0f), handle.writes, "mute silences the native handle")
    }

    @Test
    fun unmute_restoresTheExactPreMuteLevel_withNoAudibleFloor() {
        val handle = FakeHandle(0.03f)
        val controller = handle.controller()

        controller.setMuted(true)
        controller.setMuted(false)

        assertFalse(controller.isMuted)
        // The live divergence vs the VOD policy: 0.03 stays 0.03 — the
        // template's unmuteRestoreFloor is declared 0f here, so the shared
        // planUnmute must not floor it to 0.05.
        assertEquals(listOf(0.0f, 0.03f), handle.writes, "unmute restores the exact pre-mute level")
    }

    @Test
    fun unmuteWithAnEmptyMemory_leavesTheCurrentVolumeUntouched() {
        val handle = FakeHandle(0.4f)
        val controller = handle.controller()

        controller.setMuted(false) // no mute ever ran — the chip's null arm

        assertFalse(controller.isMuted)
        assertTrue(
            handle.writes.isEmpty(),
            "no remembered level — the native handle must not be slammed to a default",
        )
    }

    @Test
    fun aSecondMute_overwritesTheRememberedLevel() {
        val handle = FakeHandle(0.7f)
        val controller = handle.controller()
        controller.setMuted(true)
        controller.setMuted(false) // restores 0.7f, clears the chip
        // External volume change between the arms (focus duck, player swap).
        handle.level = 0.3f

        controller.setMuted(true)
        controller.setMuted(false)

        assertEquals(
            listOf(0.0f, 0.7f, 0.0f, 0.3f),
            handle.writes,
            "a re-mute captures the CURRENT level, never the stale first one",
        )
    }

    @Test
    fun unmute_clearsTheChip_soASecondUnmuteRestoresNothing() {
        val handle = FakeHandle(0.7f)
        val controller = handle.controller()
        controller.setMuted(true)
        controller.setMuted(false)
        handle.writes.clear()

        controller.setMuted(false)

        assertTrue(handle.writes.isEmpty(), "the stale level must never outlive its mute")
    }

    @Test
    fun reassertAfterLoad_whileMuted_reAssertsSilence() {
        val handle = FakeHandle(0.7f)
        val controller = handle.controller()
        controller.setMuted(true)
        // A reload/track change externally reset the handle (the former
        // desync: the uiState said muted while audio played).
        handle.level = 0.7f
        handle.writes.clear()

        controller.reassertAfterLoad()

        assertEquals(listOf(0.0f), handle.writes, "mute is real state — a reload re-asserts silence")
    }

    @Test
    fun reassertAfterLoad_whileUnmuted_touchesNothing() {
        val handle = FakeHandle(0.5f)
        val controller = handle.controller()

        controller.reassertAfterLoad()

        assertTrue(handle.writes.isEmpty())
    }
}
