package com.raulshma.jellyplay.feature.player.video.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [SeekChipCommitClock] — the seek-chip commit clock
 * extracted from `VideoPlayerScreen`'s two `snapshotFlow` + `collectLatest`
 * debouncers (the [TvSeekControllerTest] pattern: no Compose, no
 * coroutines — the folds are total functions over the chip's observation).
 * Pins the behaviors the former inline collectors carried with zero
 * coverage:
 *
 *  - the keyboard commit verdict (the stale-timestamp stand-down, the
 *    direction ladder, and the direction-0 no-op a linger reset inside the
 *    commit window produces);
 *  - the chip-live filter the linger collector's `filter` arms on;
 *  - the two injected delay constants and the load-bearing ordering
 *    between them (a keyboard-driven chip must commit before the linger
 *    reset clears its direction — the documented race rationale).
 */
class SeekChipCommitClockTest {

    // ---- keyboardCommit: the stale-timestamp guard ----

    @Test
    fun `keyboard commit stands down when the chip moved on after the last keyboard contribution`() {
        // A drag/swipe/double-tap seek inside the commit window bumped the
        // chip's timestamp past lastKeyboardSeekChipTimestamp: a commit from
        // the keyboard's stale base position would yank playback back over
        // the newer, user-driven seek.
        assertEquals(
            SeekChipCommitClock.Verdict.StandDown,
            SeekChipCommitClock.keyboardCommit(
                chipTimestamp = 7L,
                direction = 1,
                lastKeyboardSeekChipTimestamp = 5L,
            ),
        )
        assertEquals(
            SeekChipCommitClock.Verdict.StandDown,
            SeekChipCommitClock.keyboardCommit(
                chipTimestamp = 4L,
                direction = -1,
                lastKeyboardSeekChipTimestamp = 5L,
            ),
        )
    }

    @Test
    fun `keyboard commit fires when the chip still reflects the keyboard contribution`() {
        assertEquals(
            SeekChipCommitClock.Verdict.CommitForward,
            SeekChipCommitClock.keyboardCommit(
                chipTimestamp = 5L,
                direction = 1,
                lastKeyboardSeekChipTimestamp = 5L,
            ),
        )
        assertEquals(
            SeekChipCommitClock.Verdict.CommitBackward,
            SeekChipCommitClock.keyboardCommit(
                chipTimestamp = 5L,
                direction = -1,
                lastKeyboardSeekChipTimestamp = 5L,
            ),
        )
    }

    // ---- keyboardCommit: the direction ladder ----

    @Test
    fun `keyboard commit with direction zero is a no-op - the linger reset cleared the direction without bumping the timestamp`() {
        // DpadSeekState.reset() clears direction/offset but leaves the
        // timestamp: a reset that fired inside the commit window yields
        // matching timestamps with direction 0 — no commit, no haptic.
        assertEquals(
            SeekChipCommitClock.Verdict.StandDown,
            SeekChipCommitClock.keyboardCommit(
                chipTimestamp = 5L,
                direction = 0,
                lastKeyboardSeekChipTimestamp = 5L,
            ),
        )
    }

    // ---- chipIsLive: the linger filter ----

    @Test
    fun `chip is live exactly while a seek direction is held`() {
        assertTrue(SeekChipCommitClock.chipIsLive(1))
        assertTrue(SeekChipCommitClock.chipIsLive(-1))
        assertFalse(SeekChipCommitClock.chipIsLive(0))
    }

    // ---- the injected delay constants ----

    @Test
    fun `linger reset keeps the screen's shipped 800 ms`() {
        assertEquals(800L, SeekChipCommitClock.LINGER_RESET_MS)
    }

    @Test
    fun `keyboard commit delay is the chrome policy's 500 ms`() {
        assertEquals(500L, SeekChipCommitClock.KEYBOARD_COMMIT_DELAY_MS)
    }

    @Test
    fun `the keyboard commit wins the race against the linger reset`() {
        // The load-bearing ordering: a reset fired inside the commit window
        // clears the chip's direction WITHOUT bumping its timestamp, so a
        // linger-first race would silently swallow every keyboard commit.
        assertTrue(
            SeekChipCommitClock.KEYBOARD_COMMIT_DELAY_MS < SeekChipCommitClock.LINGER_RESET_MS,
            "the keyboard commit delay must be shorter than the linger reset",
        )
    }
}
