package com.raulshma.jellyplay.feature.player.video.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the handled-offer marker's format contract ([SessionRestoreDismissal]):
 * both writer arms' fingerprints against the matcher, the stamp-vs-position
 * decision rules, the self-invalidation property, and the legacy two-part
 * format's preserved meaning (markers written by the pre-teardown builds).
 */
class SessionRestoreDismissalTest {

    private fun snapshot(
        itemId: String = "item-1",
        positionMs: Long = 600_000L,
        timestampMs: Long? = 1_799_999_000_000L,
    ) = SessionRestorationSnapshot(
        itemId = itemId,
        title = "Episode 1",
        positionMs = positionMs,
        timestampMs = timestampMs,
    )

    // ── the dismiss arm: item + stamp + position ─────────────────────────

    @Test
    fun dismissFingerprint_carriesTheStampAndPosition() {
        assertEquals("item-1|1799999000000|600000", dismissalFingerprint(snapshot()))
    }

    @Test
    fun dismissFingerprint_zeroOrMissingStamp_staysEmpty() {
        assertEquals("item-1||600000", dismissalFingerprint(snapshot(timestampMs = null)))
        assertEquals("item-1||600000", dismissalFingerprint(snapshot(timestampMs = 0L)))
    }

    @Test
    fun dismissMarker_matchesTheExactOffer() {
        val s = snapshot()
        assertTrue(isDismissedOffer(dismissalFingerprint(s), s))
    }

    @Test
    fun dismissMarker_advancedStamp_onTheSameItem_isANewSession() {
        // The self-invalidation property: fresh playback moved the stamp, the
        // stored stamp no longer decides, and the position fallback must NOT
        // rescue a stamp-keyed marker.
        val dismissed = snapshot()
        val replayed = snapshot(timestampMs = dismissed.timestampMs!! + 1)
        assertFalse(isDismissedOffer(dismissalFingerprint(dismissed), replayed))
    }

    @Test
    fun dismissMarker_differentItem_neverMatches() {
        assertFalse(isDismissedOffer(dismissalFingerprint(snapshot()), snapshot(itemId = "item-2")))
    }

    // ── the teardown arm: item + position only ───────────────────────────

    @Test
    fun teardownFingerprint_isItemAndPositionOnly() {
        assertEquals("item-1||600000", playbackHandledFingerprint("item-1", 600_000L))
    }

    @Test
    fun teardownMarker_matchesTheRowTheStopReportCreated() {
        // The next launch's snapshot: same item, the stop's position, the
        // server's NEW stamp (which the writer could not have known).
        val row = snapshot(timestampMs = 1_800_000_100_000L)
        assertTrue(isDismissedOffer(playbackHandledFingerprint("item-1", 600_000L), row))
    }

    @Test
    fun teardownMarker_rowWithinTheStopRaceTolerance_stillMatches() {
        // The row after a close is the last ~10 s PROGRESS report, not
        // necessarily the stop's value — the process can die before the stop
        // lands and a delivered stop can be overwritten by the progress
        // loop's last cycle. Within the tolerance it is the same handled
        // state (both directions: a late progress below the stop, and a row
        // a little past it).
        val row = snapshot(positionMs = 600_000L + 20_000L)
        assertTrue(isDismissedOffer(playbackHandledFingerprint("item-1", 600_000L), row))
        val rowBelow = snapshot(positionMs = 600_000L - 45_000L)
        assertTrue(isDismissedOffer(playbackHandledFingerprint("item-1", 600_000L), rowBelow))
        val atEdge = snapshot(positionMs = 600_000L + HANDLED_POSITION_TOLERANCE_MS)
        assertTrue(isDismissedOffer(playbackHandledFingerprint("item-1", 600_000L), atEdge))
    }

    @Test
    fun teardownMarker_movedPosition_isANewSession() {
        // The user resumed elsewhere (or kept watching past the close) — the
        // row no longer sits at the handled position.
        assertFalse(isDismissedOffer(playbackHandledFingerprint("item-1", 600_000L), snapshot(positionMs = 700_000L)))
    }

    // ── the legacy two-part format keeps its meaning ─────────────────────

    @Test
    fun legacyMarker_stampForm_stillMatchesItsOffer() {
        assertTrue(isDismissedOffer("item-1|1799999000000", snapshot()))
    }

    @Test
    fun legacyMarker_positionForm_theStampLessServerFallback_stillMatches() {
        assertTrue(isDismissedOffer("item-1|600000", snapshot(timestampMs = null)))
    }

    @Test
    fun legacyMarker_otherValue_neverMatches() {
        assertFalse(isDismissedOffer("item-1|42", snapshot()))
        assertFalse(isDismissedOffer("item-1|42", snapshot(timestampMs = null)))
    }

    // ── malformed / absent markers ───────────────────────────────────────

    @Test
    fun nullEmptyAndGarbageMarkers_neverMatch() {
        val s = snapshot()
        assertFalse(isDismissedOffer(null, s))
        assertFalse(isDismissedOffer("", s))
        assertFalse(isDismissedOffer("garbage", s))
        assertFalse(isDismissedOffer("item-1|not-a-number|also-not", s))
    }

    // ── the arms share one builder ───────────────────────────────────────

    @Test
    fun dismissalAndTeardownFingerprints_agreeOnAStampLessRow() {
        val s = snapshot(timestampMs = null)
        assertEquals(dismissalFingerprint(s), playbackHandledFingerprint("item-1", 600_000L))
    }
}
