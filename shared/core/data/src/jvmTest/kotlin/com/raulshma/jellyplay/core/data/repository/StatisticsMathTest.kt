package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.ViewingStreak
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [StatisticsMath]'s pure watch-time-window and viewing-streak math —
 * the exact bodies moved out of [AdminStatisticsRepositoryImpl] — against
 * fixed dates, so the window boundaries, the recent-play weighting, the
 * result clamps and the streak walks can never drift silently.
 */
class StatisticsMathTest {

    /** Fixed "today": a Thursday, so week-boundary fixtures are explicit. */
    private val today: LocalDate = LocalDate.of(2026, 1, 1)

    // ── computeWatchTimeBreakdown ───────────────────────────────────────

    @Test
    fun `empty input yields an all-zero breakdown`() {
        assertEquals(
            StatisticsMath.WatchTimeBreakdown(0L, 0L, 0L, 0L),
            StatisticsMath.computeWatchTimeBreakdown(emptyList(), today),
        )
    }

    @Test
    fun `zero-runtime items are skipped and missing dates count toward the total only`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                // Zero runtime → skipped entirely.
                StatisticsMath.WatchTimeItem(runtimeTicks = 0L, playCount = 5, lastPlayedDate = "2025-12-31T00:00:00Z"),
                // Null ticks → skipped.
                StatisticsMath.WatchTimeItem(runtimeTicks = null, playCount = 5, lastPlayedDate = "2025-12-31T00:00:00Z"),
                // Play count 0 reads as one play; no date → total only.
                StatisticsMath.WatchTimeItem(runtimeTicks = 600L * 10_000_000L, playCount = 0, lastPlayedDate = null),
            ),
            today,
        )
        assertEquals(600L, breakdown.totalSeconds)
        assertEquals(0L, breakdown.last30DaysSeconds)
        assertEquals(0L, breakdown.last7DaysSeconds)
        assertEquals(0L, breakdown.previous30DaysSeconds)
    }

    @Test
    fun `undateable play still counts toward the total`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(StatisticsMath.WatchTimeItem(runtimeTicks = 300L * 10_000_000L, playCount = 1, lastPlayedDate = "not-a-date")),
            today,
        )
        assertEquals(300L, breakdown.totalSeconds)
        assertEquals(0L, breakdown.last30DaysSeconds)
    }

    @Test
    fun `window boundaries pin at 30 31 60 61 days and 7 8 days`() {
        fun item(daysAgo: Int, minutes: Long) = StatisticsMath.WatchTimeItem(
            runtimeTicks = minutes * 60L * 10_000_000L,
            playCount = 1,
            lastPlayedDate = today.minusDays(daysAgo.toLong()).toString(),
        )

        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                item(daysAgo = 30, minutes = 1), // last day of the 30-day window…
                item(daysAgo = 31, minutes = 2), // …31 opens the previous-30 window…
                item(daysAgo = 60, minutes = 4), // …which closes at 60…
                item(daysAgo = 61, minutes = 8), // …and 61 counts nowhere.
                item(daysAgo = 7, minutes = 16), // last day of the 7-day window…
                item(daysAgo = 8, minutes = 32), // …8 is outside it (still in last-30).
            ),
            today,
        )
        // Seconds: only daysAgo 30, 7, 8 land in the current-30 window
        // (31 opens the previous-30 window instead).
        assertEquals(60L + 960L + 1920L, breakdown.last30DaysSeconds)
        // Only day 7 (16 min) lands in the last-7 window.
        assertEquals(960L, breakdown.last7DaysSeconds)
        // Previous-30 holds days 31 and 60 (120s + 240s); its clamp
        // (total − last30 = 3780 − 2940 = 840) leaves it intact.
        assertEquals(360L, breakdown.previous30DaysSeconds)
        assertEquals(3780L, breakdown.totalSeconds)
    }

    @Test
    fun `previous-30 window holds exactly days 31 to 60 when the current windows are empty`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                StatisticsMath.WatchTimeItem(
                    runtimeTicks = 120L * 10_000_000L,
                    playCount = 1,
                    lastPlayedDate = today.minusDays(45).toString(),
                ),
            ),
            today,
        )
        assertEquals(120L, breakdown.totalSeconds)
        assertEquals(0L, breakdown.last30DaysSeconds)
        assertEquals(0L, breakdown.last7DaysSeconds)
        assertEquals(120L, breakdown.previous30DaysSeconds)
    }

    @Test
    fun `multiple plays today weight the full play count into the recent windows`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                StatisticsMath.WatchTimeItem(
                    runtimeTicks = 100L * 10_000_000L,
                    playCount = 3,
                    lastPlayedDate = today.toString(),
                ),
            ),
            today,
        )
        // daysAgo 0 → recentPlays = plays on both windows: 3 × 100s.
        assertEquals(300L, breakdown.totalSeconds)
        assertEquals(300L, breakdown.last30DaysSeconds)
        assertEquals(300L, breakdown.last7DaysSeconds)
    }

    @Test
    fun `aged multi-play weight decays to at least one play`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                StatisticsMath.WatchTimeItem(
                    runtimeTicks = 100L * 10_000_000L,
                    playCount = 3,
                    lastPlayedDate = today.minusDays(29).toString(),
                ),
            ),
            today,
        )
        // 3 × 30 / 59 integer-divides to 1 → the coerced single play.
        assertEquals(300L, breakdown.totalSeconds)
        assertEquals(100L, breakdown.last30DaysSeconds)
    }

    @Test
    fun `future-dated plays clamp the windows to the total`() {
        val breakdown = StatisticsMath.computeWatchTimeBreakdown(
            listOf(
                // daysAgo = −5 → weight 10 × 30 / 25 = 12 plays → 1200s > total.
                StatisticsMath.WatchTimeItem(
                    runtimeTicks = 100L * 10_000_000L,
                    playCount = 10,
                    lastPlayedDate = today.plusDays(5).toString(),
                ),
                // Previous-30 candidate — clamped to total − last30 = 0 below.
                StatisticsMath.WatchTimeItem(
                    runtimeTicks = 100L * 10_000_000L,
                    playCount = 5,
                    lastPlayedDate = today.minusDays(45).toString(),
                ),
            ),
            today,
        )
        assertEquals(1500L, breakdown.totalSeconds)
        // min(1200, 1500) = 1200.
        assertEquals(1200L, breakdown.last30DaysSeconds)
        // The 7-day weight (35 plays → 3500s) clamps to the total.
        assertEquals(1500L, breakdown.last7DaysSeconds)
        // min(500, 1500 − 1200) = 300.
        assertEquals(300L, breakdown.previous30DaysSeconds)
    }

    // ── calculateViewingStreak ──────────────────────────────────────────

    @Test
    fun `empty and all-inactive activity data yield the default streak`() {
        assertEquals(ViewingStreak(), StatisticsMath.calculateViewingStreak(emptyList(), today))
        assertEquals(
            ViewingStreak(),
            StatisticsMath.calculateViewingStreak(
                listOf(PlaybackActivityPoint(date = "2026-01-01", value = 0L)),
                today,
            ),
        )
    }

    @Test
    fun `same-day activity counts as a one-day streak regardless of point count`() {
        val streak = StatisticsMath.calculateViewingStreak(
            listOf(
                PlaybackActivityPoint(date = "2026-01-01", value = 5L),
                PlaybackActivityPoint(date = "2026-01-01", value = 7L),
                PlaybackActivityPoint(date = "2026-01-01", value = 0L), // inactive, ignored
            ),
            today,
        )
        assertEquals(ViewingStreak(currentStreak = 1, longestStreak = 1, streakStartDate = "2026-01-01"), streak)
    }

    @Test
    fun `current streak walks back across the week boundary`() {
        // Thu 2026-01-01 back through Sun 2025-12-28 → Monday continues the
        // Sunday run: 5 consecutive days spanning the week boundary.
        val dates = listOf("2025-12-28", "2025-12-29", "2025-12-30", "2025-12-31", "2026-01-01")
        val streak = StatisticsMath.calculateViewingStreak(
            dates.map { PlaybackActivityPoint(date = it, value = 1L) },
            today,
        )
        assertEquals(5, streak.currentStreak)
        assertEquals(5, streak.longestStreak)
        assertEquals("2025-12-28", streak.streakStartDate)
    }

    @Test
    fun `a one-day gap splits current and longest streaks`() {
        val streak = StatisticsMath.calculateViewingStreak(
            listOf(
                PlaybackActivityPoint(date = "2026-01-01", value = 1L),
                // 2025-12-31 missing → the gap.
                PlaybackActivityPoint(date = "2025-12-30", value = 1L),
                PlaybackActivityPoint(date = "2025-12-29", value = 1L),
            ),
            today,
        )
        assertEquals(1, streak.currentStreak)
        assertEquals(2, streak.longestStreak)
        assertEquals("2026-01-01", streak.streakStartDate)
    }

    @Test
    fun `an inactive today leaves the longest streak historical`() {
        val streak = StatisticsMath.calculateViewingStreak(
            (1L..5L).map { PlaybackActivityPoint(date = today.minusDays(it).toString(), value = 1L) },
            today,
        )
        assertEquals(0, streak.currentStreak)
        assertEquals(5, streak.longestStreak)
        assertEquals(null, streak.streakStartDate)
    }

    @Test
    fun `unparsable dates break a longest-streak run instead of throwing`() {
        val streak = StatisticsMath.calculateViewingStreak(
            listOf(
                PlaybackActivityPoint(date = "2025-12-05", value = 1L),
                PlaybackActivityPoint(date = "bogus", value = 1L),
                PlaybackActivityPoint(date = "2025-12-01", value = 1L),
                PlaybackActivityPoint(date = "2025-12-02", value = 1L),
            ),
            today, // inactive today → currentStreak 0.
        )
        assertEquals(0, streak.currentStreak)
        assertEquals(2, streak.longestStreak)
    }
}
