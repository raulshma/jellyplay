package com.raulshma.jellyplay.feature.details

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure tests for the upcoming strip's presentation folds ([upcomingAirBadge] and
 * [airingCountdownDays]) — the badge/countdown decisions the
 * [UpcomingEpisodeCard] composes. The renderer supplies `today`, so both
 * folds are clock-free and directly assertable. No Android / Compose.
 */
class UpcomingPresentationTest {

    private val today = LocalDate(2026, 10, 12)

    // ── the air-date badge (MissingEpisodeBadge.Airs reuse) ───────────────

    @Test
    fun `parseable premiere stamp yields the Airs badge`() {
        val badge = upcomingAirBadge("2026-10-20T20:00:00.000Z")
        assertEquals(MissingEpisodeBadge.Airs(LocalDate(2026, 10, 20)), badge)
    }

    @Test
    fun `missing or malformed stamp yields no badge - never Missing`() {
        // An upcoming episode is by construction unaired, not missing: no
        // parseable date → NO badge, not the "Missing" placeholder.
        assertNull(upcomingAirBadge(null))
        assertNull(upcomingAirBadge(""))
        assertNull(upcomingAirBadge("not-a-date"))
    }

    // ── the countdown ─────────────────────────────────────────────────────

    @Test
    fun `counts whole days until the air date`() {
        assertEquals(3L, airingCountdownDays("2026-10-15T00:00:00.000Z", today))
        assertEquals(0L, airingCountdownDays("2026-10-12T23:59:00.000Z", today))
    }

    @Test
    fun `today renders the zero-day countdown`() {
        assertEquals(0L, airingCountdownDays("2026-10-12T00:00:00.000Z", today))
    }

    @Test
    fun `past dates and unparsable stamps yield no countdown`() {
        // A strip card never counts down to yesterday (the /Shows/Upcoming
        // source only returns unaired episodes; a stale payload degrades to
        // no line).
        assertNull(airingCountdownDays("2026-10-11T00:00:00.000Z", today))
        assertNull(airingCountdownDays(null, today))
        assertNull(airingCountdownDays("garbage", today))
    }

    @Test
    fun `future countdown always non-negative`() {
        val days = airingCountdownDays("2027-01-01T00:00:00.000Z", today)
        assertTrue(days != null && days > 0L)
    }
}
