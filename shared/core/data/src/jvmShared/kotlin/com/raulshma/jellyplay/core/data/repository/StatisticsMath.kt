package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.ViewingStreak
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The pure watch-statistics math of [AdminStatisticsRepositoryImpl], split
 * out so it can be pinned by deterministic tests: every input is a value
 * (dates included — the caller resolves "today" through its clock seam) and
 * nothing in here reads a clock, a DAO, or the network. Behaviour is moved
 * verbatim from the repo's former private `computeWatchTimeBreakdown` /
 * `calculateViewingStreak` bodies.
 */
internal object StatisticsMath {

    /** The per-item inputs of [computeWatchTimeBreakdown] (a [com.raulshma.jellyplay.core.model.MediaItem] slice). */
    data class WatchTimeItem(
        val runtimeTicks: Long?,
        val playCount: Int,
        val lastPlayedDate: String?,
    )

    data class WatchTimeBreakdown(
        val totalSeconds: Long,
        val last30DaysSeconds: Long,
        val last7DaysSeconds: Long,
        val previous30DaysSeconds: Long,
    )

    /**
     * Window math over the user's played items: runtime × plays totals, with
     * the 30/7-day windows weighted toward recent plays and the previous-30
     * window kept out of the current-30 total. Items with zero runtime are
     * skipped; play counts below 1 read as a single play; undatesable items
     * count toward the total only.
     */
    fun computeWatchTimeBreakdown(items: List<WatchTimeItem>, today: LocalDate): WatchTimeBreakdown {
        var totalSec = 0L
        var last30Sec = 0L
        var last7Sec = 0L
        var prev30Sec = 0L

        for (item in items) {
            val runtimeSec = (item.runtimeTicks ?: 0L) / 10_000_000L
            if (runtimeSec == 0L) continue
            val plays = item.playCount.coerceAtLeast(1)
            totalSec += runtimeSec * plays

            val lastPlayed = item.lastPlayedDate?.take(10) ?: continue
            val playedDate = try { LocalDate.parse(lastPlayed) } catch (_: Exception) { continue }
            val daysAgo = ChronoUnit.DAYS.between(playedDate, today)

            if (daysAgo <= 30) {
                val recentPlays = if (plays == 1) 1 else maxOf(1, plays * 30 / (daysAgo.toInt() + 30))
                last30Sec += runtimeSec * recentPlays
            }
            if (daysAgo <= 7) {
                val recentPlays = if (plays == 1) 1 else maxOf(1, plays * 7 / (daysAgo.toInt() + 7))
                last7Sec += runtimeSec * recentPlays
            }
            if (daysAgo in 31..60) {
                prev30Sec += runtimeSec * plays
            }
        }

        return WatchTimeBreakdown(
            totalSeconds = totalSec,
            last30DaysSeconds = last30Sec.coerceAtMost(totalSec),
            last7DaysSeconds = last7Sec.coerceAtMost(totalSec),
            previous30DaysSeconds = prev30Sec.coerceAtMost(totalSec - last30Sec).coerceAtLeast(0L),
        )
    }

    /**
     * Current streak (consecutive active days walking back from [today]) and
     * longest streak over the activity series; zero-value points are inactive
     * days, and an unparsable date string breaks a longest-streak run (the
     * original body's per-parse catch).
     */
    fun calculateViewingStreak(activityData: List<PlaybackActivityPoint>, today: LocalDate): ViewingStreak {
        val activeDates = activityData
            .filter { it.value > 0 }
            .map { it.date }
            .toSet()

        if (activeDates.isEmpty()) {
            return ViewingStreak()
        }

        var currentStreak = 0
        var streakStartDate: String? = null

        var checkDate = today
        while (activeDates.contains(checkDate.toString())) {
            currentStreak++
            streakStartDate = checkDate.toString()
            checkDate = checkDate.minusDays(1)
        }

        val sortedDates = activeDates.sorted()
        var longestStreak = 0
        var tempStreak = 1
        for (i in 1 until sortedDates.size) {
            try {
                val prev = LocalDate.parse(sortedDates[i - 1])
                val curr = LocalDate.parse(sortedDates[i])
                if (ChronoUnit.DAYS.between(prev, curr) == 1L) {
                    tempStreak++
                } else {
                    longestStreak = maxOf(longestStreak, tempStreak)
                    tempStreak = 1
                }
            } catch (_: Exception) {
                longestStreak = maxOf(longestStreak, tempStreak)
                tempStreak = 1
            }
        }
        longestStreak = maxOf(longestStreak, tempStreak)

        return ViewingStreak(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            streakStartDate = streakStartDate,
        )
    }
}
