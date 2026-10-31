package com.raulshma.jellyplay.e2e.player

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.raulshma.jellyplay.core.model.PlayerType
import android.os.SystemClock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tracks & controls sheets against the multi-track fixture clip (2 audio
 * tracks + embedded mov_text + external .srt): the audio sheet lists both
 * tracks and the switch sticks across a reopen, the subtitle hub lists and
 * selects the external .srt, and a 1.5x speed pick moves the playhead ~1.5x
 * realtime. Runs on ExoPlayer (track switching is engine-driven; mpv's
 * engine-side switch is exercised by the mpv lifecycle run).
 */
@LargeTest
class TrackSelectionE2eTest : PlayerE2eBase() {

    private lateinit var tracksId: String

    @Before
    fun setUpItem() {
        setPreferredPlayer(PlayerType.EXO_PLAYER)
        tracksId = fixture.itemId(PlayerE2eEnv.TRACKS_CLIP)
        fixture.resetUserData(tracksId)
    }

    private fun openTracksPlayer() {
        openPlayer(tracksId)
        waitFor("playback never started", timeoutMs = 60_000) {
            device.findObject(By.desc("Pause"))
        }
        showControls()
    }

    private fun openSheetByDesc(desc: String) {
        showControls()
        // The speed button carries no icon contentDescription — it renders the
        // current speed as text, so it is addressed by its tag.
        if (desc == "Speed") {
            device.findObject(By.res("player-speed-button"))?.click()
                ?: throw AssertionError("speed button not found in the control bar")
        } else {
            device.findObject(By.desc(desc))?.click()
                ?: throw AssertionError("control '$desc' not found in the control bar")
        }
        waitFor("sheet never appeared after tapping '$desc'", timeoutMs = 10_000) {
            device.findObject(By.res("player-sheet"))
        }
    }

    private fun closeSheet() {
        device.pressBack()
        waitForTruthy("sheet never closed on back") { !hasRes("player-sheet") }
        SystemClock.sleep(500)
    }

    /** All rendered track rows, regardless of stream index. */
    private fun trackRows(): List<UiObject2> =
        device.findObjects(By.res(java.util.regex.Pattern.compile("player-track-row-\\d+")))

    /** Selected track rows carry the trailing check circle (one extra child). */
    private fun rowLooksSelected(row: UiObject2): Boolean = row.childCount >= 2

    private fun rowLabel(row: UiObject2): String? = row.children.firstOrNull()?.text

    @Test
    fun audioSheetListsBothTracksAndSwitchPersists() {
        openTracksPlayer()
        try {
            openSheetByDesc("Audio")

            device.findObject(By.res("player-track-row-1"))
                ?: throw AssertionError("audio track row 1 never rendered")
            val track2 = device.findObject(By.res("player-track-row-2"))
                ?: throw AssertionError("audio track row 2 missing — fixture clip has 2 audio tracks")

            track2.click()
            closeSheet()

            // Reopen: the switch must have stuck.
            openSheetByDesc("Audio")
            val row2 = device.findObject(By.res("player-track-row-2"))
                ?: throw AssertionError("audio track row 2 missing on reopen")
            assertTrue(
                "audio selection did not persist across sheet reopen (row 2 not marked selected)",
                rowLooksSelected(row2),
            )
            closeSheet()
        } finally {
            exitPlayer()
        }
    }

    @Test
    fun subtitleHubListsAndSelectsExternalSrt() {
        openTracksPlayer()
        try {
            openSheetByDesc("Subtitles")

            // The external track's label carries the .srt file name.
            val srt = waitFor("external .srt subtitle row never rendered") {
                trackRows().firstOrNull { rowLabel(it)?.contains(".srt", ignoreCase = true) == true }
            }
            checkNotNull(srt) {
                "no external .srt row in the subtitle hub (rows seen: ${trackRows().mapNotNull(::rowLabel)})"
            }
            srt.click()
            closeSheet()

            // Reopen: the .srt row is the selected one.
            openSheetByDesc("Subtitles")
            val selected = waitFor("subtitle hub never rendered rows on reopen") {
                trackRows().firstOrNull { rowLabel(it)?.contains(".srt", ignoreCase = true) == true }
            }
            assertTrue(
                "external .srt selection did not persist across reopen",
                rowLooksSelected(selected),
            )
            closeSheet()
        } finally {
            exitPlayer()
        }
    }

    @Test
    fun speedChipChangesPlayheadRate() {
        openTracksPlayer()
        try {
            openSheetByDesc("Speed")

            val chip = device.findObject(By.res("player-speed-chip-1.5"))
                ?: throw AssertionError("1.5x speed chip missing from the speed sheet")
            chip.click()
            waitForTruthy("speed sheet never closed after picking 1.5x") { !hasRes("player-sheet") }

            // Playhead must now gain ~1.5s per wall second. Measure over ~6s.
            showControls()
            val t0 = positionSeconds()
            hideControls()
            SystemClock.sleep(6_000)
            showControls()
            val t1 = positionSeconds()
            hideControls()
            checkNotNull(t0); checkNotNull(t1)
            val gain = t1 - t0
            check(gain in 6..14) { "6s wall-clock at 1.5x advanced the playhead ${gain}s, expected ~9s" }

            // Back to 1x so later runs aren't surprised.
            openSheetByDesc("Speed")
            device.findObject(By.res("player-speed-chip-1.0"))?.click()
                ?: throw AssertionError("1.0x chip missing on reset pass")
            waitForTruthy { !hasRes("player-sheet") }
        } finally {
            exitPlayer()
        }
    }
}
