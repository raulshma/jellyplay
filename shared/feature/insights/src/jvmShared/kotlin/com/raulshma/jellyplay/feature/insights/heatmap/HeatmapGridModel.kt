package com.raulshma.jellyplay.feature.insights.heatmap

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** One heatmap cell: calendar day, intensity level 0–4, and the raw value. */
@Immutable
internal data class HeatmapCell(
    val date: LocalDate,
    val level: Int,
    val value: Long,
)

/**
 * Compose-free geometry and policy core for the watch-progress heatmap,
 * extracted verbatim from [WatchProgressHeatmapScreen]: grid construction
 * (week-column layout + quartile level policy), the TV focus cursor's
 * clamped movement, month-label placement, and the viewport scroll-target
 * coercion. The screen keeps only dp/px conversion, Canvas drawing, and the
 * effect shells that call this model — `today` is a parameter so the whole
 * object is deterministically testable.
 */
internal object HeatmapGridModel {

    /** Sunday-first column index (Sun=0 … Sat=6), matching the canvas layout. */
    fun dayOfWeekIndex(date: LocalDate): Int = when (date.dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
    }

    /**
     * First grid day for [year]: when the user's activity only starts mid-year
     * (server-side retention), back up to that week's Sunday so the first
     * column is whole; otherwise the calendar year's first day.
     */
    fun gridStartDate(year: Int, minActivityDate: LocalDate?): LocalDate =
        if (minActivityDate != null && minActivityDate.year == year) {
            minActivityDate.minusDays(dayOfWeekIndex(minActivityDate).toLong())
        } else {
            LocalDate.of(year, 1, 1)
        }

    /**
     * Quartile level policy: 0 for no activity, else 1–4 by the value's ratio
     * to the grid's max (≤0.25 → 1, ≤0.50 → 2, ≤0.75 → 3, otherwise 4).
     */
    fun levelFor(value: Long, maxValue: Long): Int {
        if (value <= 0) return 0
        val ratio = (value.toDouble() / maxValue).coerceIn(0.0, 1.0)
        return when {
            ratio <= 0.25 -> 1
            ratio <= 0.50 -> 2
            ratio <= 0.75 -> 3
            else -> 4
        }
    }

    /**
     * Builds the week-column grid for [year]: a flat `week * 7 + day` array
     * (nullable — days outside the populated run stay null) plus the week
     * count. Cells are laid from [gridStartDate] up to the earlier of
     * year end and [today]; activity values are joined by ISO date string.
     */
    fun calculateGrid(
        year: Int,
        dailyActivities: List<DailyWatchActivity>,
        minActivityDate: LocalDate?,
        today: LocalDate,
    ): Pair<Array<HeatmapCell?>, Int> {
        val startDate = gridStartDate(year, minActivityDate)
        val endDate = LocalDate.of(year, 12, 31)
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE

        val valueByDate = mutableMapOf<LocalDate, Long>()
        for (activity in dailyActivities) {
            runCatching { LocalDate.parse(activity.date, formatter) }
                .getOrNull()
                ?.let { date -> valueByDate[date] = activity.value }
        }

        val maxValue = valueByDate.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L

        val numWeeks = ChronoUnit.WEEKS.between(
            startDate.with(DayOfWeek.SUNDAY),
            endDate.with(DayOfWeek.SATURDAY),
        ).toInt() + 1
        val grid = arrayOfNulls<HeatmapCell>(numWeeks * 7)

        var current = startDate
        while (!current.isAfter(endDate) && !current.isAfter(today)) {
            val weekIndex = ChronoUnit.WEEKS.between(
                startDate.with(DayOfWeek.SUNDAY),
                current.with(DayOfWeek.SUNDAY),
            ).toInt()
            val dayIndex = dayOfWeekIndex(current)
            val pos = weekIndex * 7 + dayIndex
            if (pos in grid.indices) {
                val value = valueByDate[current] ?: 0L
                grid[pos] = HeatmapCell(date = current, level = levelFor(value, maxValue), value = value)
            }
            current = current.plusDays(1)
        }

        return grid to numWeeks
    }

    /**
     * Start the TV cursor on [today] when the year covers it, otherwise on
     * the first populated cell.
     */
    fun initialFocusedCellIndex(grid: Array<HeatmapCell?>, today: LocalDate): Int {
        val todayIndex = grid.indexOfFirst { it?.date == today }
        if (todayIndex >= 0) return todayIndex
        return grid.indexOfFirst { it != null }.coerceAtLeast(0)
    }

    /**
     * Month-label placement: week column index → 3-letter month name, one
     * entry per month whose first day is at or before [today] (later months
     * are not labelled). A first-of-month before the grid start (mid-year
     * retention) clamps to the grid start's week.
     */
    fun monthLabels(year: Int, gridStartDate: LocalDate, today: LocalDate): Map<Int, String> {
        val months = mutableMapOf<Int, String>()
        val startMonth = gridStartDate.monthValue
        for (month in startMonth..12) {
            val firstOfMonth = LocalDate.of(year, month, 1)
            if (firstOfMonth.isAfter(today)) break
            val targetDate = if (firstOfMonth.isBefore(gridStartDate)) gridStartDate else firstOfMonth
            val weekIndex = ChronoUnit.WEEKS.between(
                gridStartDate.with(DayOfWeek.SUNDAY),
                targetDate.with(DayOfWeek.SUNDAY),
            ).toInt()
            months[weekIndex] = firstOfMonth.month.name.take(3)
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        }
        return months
    }

    /**
     * TV D-pad cursor movement: clamp [current] + [delta] to the populated
     * cell range. Returns null when the cursor cannot move (empty grid, or
     * already at the edge) so direction keys can be left unconsumed for focus
     * traversal — the cursor never wraps.
     */
    fun clampFocus(current: Int, delta: Int, minIndex: Int, maxIndex: Int): Int? {
        if (minIndex < 0) return null
        val next = (current + delta).coerceIn(minIndex, maxIndex)
        if (next == current) return null
        return next
    }

    /**
     * Viewport scroll-target coercion for the focused week: the x position
     * that brings the focused cell's column fully into view, or null when it
     * is already visible (no scroll). All inputs/outputs are pixels.
     */
    fun scrollTargetForFocus(
        focusedIndex: Int,
        cellStridePx: Float,
        cellSizePx: Float,
        scrollValuePx: Int,
        viewportWidthPx: Float,
    ): Float? {
        val cellLeft = (focusedIndex / 7) * cellStridePx
        return when {
            cellLeft < scrollValuePx -> cellLeft
            cellLeft + cellSizePx > scrollValuePx + viewportWidthPx -> cellLeft + cellSizePx - viewportWidthPx
            else -> null
        }
    }
}
