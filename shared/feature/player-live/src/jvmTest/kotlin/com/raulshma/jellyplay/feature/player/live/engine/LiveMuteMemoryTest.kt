package com.raulshma.jellyplay.feature.player.live.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the live mute-memory chip ([LiveMuteMemory]) — the exact pre-mute
 * restore (not the VOD policy's 0.05-floored level), the null-restore
 * "leave untouched" arm, the unconditional re-mute overwrite and the
 * stale-clear-on-stop reset. The ViewModel's mute suite
 * (LiveTvPlayerViewModelGapsTest) pins the same semantics end to end through
 * the [com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel]
 * seam; these tests pin the chip in isolation.
 */
class LiveMuteMemoryTest {

    @Test
    fun freshChip_restoresNothing() {
        assertNull(LiveMuteMemory().restorationVolume())
    }

    @Test
    fun onMute_capturesTheExactPreMuteLevel() {
        val memory = LiveMuteMemory().onMute(0.7f)

        assertEquals(0.7f, memory.restorationVolume())
    }

    @Test
    fun onUnmute_clearsTheChipSoASecondUnmuteRestoresNothing() {
        val memory = LiveMuteMemory().onMute(0.7f)

        assertEquals(0.7f, memory.restorationVolume())
        assertNull(memory.onUnmute().restorationVolume())
    }

    @Test
    fun aSecondMute_overwritesTheRememberedLevel() {
        val memory = LiveMuteMemory().onMute(0.7f).onMute(0.3f)

        assertEquals(0.3f, memory.restorationVolume())
    }

    @Test
    fun anEmptyChip_restoresNull_leavingTheCurrentVolumeUntouched() {
        // The mute was set externally (or the player swapped between the
        // arms): unmute must not slam to a fixed default.
        val memory = LiveMuteMemory()

        assertNull(memory.restorationVolume())
        assertNull(memory.onUnmute().restorationVolume())
    }

    @Test
    fun onStopped_clearsACapturedLevel_staleClearOnStop() {
        val memory = LiveMuteMemory().onMute(0.7f)

        assertNull(
            memory.onStopped().restorationVolume(),
            "a level captured before stop() must never restore onto a later player",
        )
    }
}
