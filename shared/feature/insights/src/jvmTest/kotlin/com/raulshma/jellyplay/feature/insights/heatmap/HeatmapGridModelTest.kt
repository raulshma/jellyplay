package com.raulshma.jellyplay.feature.insights.heatmap

import com.raulshma.jellyplay.core.data.repository.DailyWatchActivity
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Compose-free [HeatmapGridModel] folds extracted from
 * [WatchProgressHeatmapScreen]: leap-year grid coverage, the mid-year
 * min-activity-date start, month-label week placement, the quartile level
 * thresholds, the TV focus clamp, the viewport scroll-target coercion, and
 * the pointer hit resolution (`dayAt`/`isInsideGrid`).
 */
class HeatmapGridModelTest {

    /** Production stride at density 1.0: cellSize 11dp + cellGap 2dp = 12px. */
    private val stride = 12f

    private fun fullYear2024Grid(): Array<HeatmapCell?> = HeatmapGridModel.calculateGrid(
        year = 2024,
        dailyActivities = listOf(DailyWatchActivity(date = "2024-02-29", value = 5)),
        minActivityDate = null,
        today = LocalDate.of(2024, 12, 31),
    ).first


    // ── calculateGrid: leap-year February ────────────────────────────────

    @Test
    fun leapYearGrid_containsFebruary29() {
        val (grid, numWeeks) = HeatmapGridModel.calculateGrid(
            year = 2024,
            dailyActivities = listOf(DailyWatchActivity(date = "2024-02-29", value = 5)),
            minActivityDate = null,
            today = LocalDate.of(2024, 12, 31),
        )

        // Week anchors use `with(DayOfWeek)` inside the ISO Mon–Sun week:
        // Jan 1 2024 (Mon) anchors to Sunday Jan 7, Dec 31 (Tue) to Saturday
        // Jan 4 2025 → 51 complete weeks + 1 = 52 columns. The two trailing
        // days of Dec 2024 land beyond the last column and are dropped —
        // pinned production behavior.
        assertEquals(52, numWeeks)
        val leapDay = grid.firstNotNullOfOrNull { cell -> cell?.takeIf { it.date == LocalDate.of(2024, 2, 29) } }
        assertNotNull(leapDay, "leap day must be populated")
        assertEquals(5L, leapDay.value)
        // The sole active day is the grid max → ratio 1.0 → top level.
        assertEquals(4, leapDay.level)
    }

    @Test
    fun nonLeapYearGrid_hasNoFebruary29() {
        val (grid, _) = HeatmapGridModel.calculateGrid(
            year = 2023,
            dailyActivities = emptyList(),
            minActivityDate = null,
            today = LocalDate.of(2023, 12, 31),
        )

        // No Feb-29 constructibility assumption — 2023 has none by definition.
        assertTrue(grid.none { it != null && it.date.monthValue == 2 && it.date.dayOfMonth == 29 })
    }

    // ── gridStartDate: mid-year minActivityDate start ────────────────────

    @Test
    fun midYearActivityStart_backsUpToThatWeeksSunday() {
        // 2024-07-10 is a Wednesday → the first column starts Sunday 07-07.
        assertEquals(
            LocalDate.of(2024, 7, 7),
            HeatmapGridModel.gridStartDate(2024, LocalDate.of(2024, 7, 10)),
        )
        // A min date from another year is ignored — full calendar year.
        assertEquals(
            LocalDate.of(2024, 1, 1),
            HeatmapGridModel.gridStartDate(2024, LocalDate.of(2023, 12, 20)),
        )
    }

    @Test
    fun midYearStartGrid_populatesOnlyFromTheGridStartAndStopsAtToday() {
        val (grid, _) = HeatmapGridModel.calculateGrid(
            year = 2024,
            dailyActivities = emptyList(),
            minActivityDate = LocalDate.of(2024, 7, 10),
            today = LocalDate.of(2024, 12, 31),
        )

        assertEquals(LocalDate.of(2024, 7, 7), grid.firstOrNull { it != null }?.date)
        assertTrue(grid.none { it != null && it.date.isBefore(LocalDate.of(2024, 7, 7)) })

        val (cutoff, _) = HeatmapGridModel.calculateGrid(
            year = 2024,
            dailyActivities = emptyList(),
            minActivityDate = null,
            today = LocalDate.of(2024, 8, 15),
        )
        assertTrue(cutoff.none { it != null && it.date.isAfter(LocalDate.of(2024, 8, 15)) })
    }

    // ── monthLabels: month-boundary week placement ───────────────────────

    @Test
    fun fullYearMonthLabels_landOnTheWeekContainingTheFirstOfMonth() {
        val labels = HeatmapGridModel.monthLabels(
            year = 2024,
            gridStartDate = LocalDate.of(2024, 1, 1),
            today = LocalDate.of(2024, 12, 31),
        )

        // Week index = weeks between the grid's Sunday anchors (`with(DayOfWeek)`
        // inside the ISO Mon–Sun week): Sep 1 2024 is already a Sunday (week
        // 34), Dec 1 likewise (week 47) — no forward hop.
        assertEquals(
            mapOf(
                0 to "Jan", 4 to "Feb", 8 to "Mar", 13 to "Apr",
                17 to "May", 21 to "Jun", 26 to "Jul", 30 to "Aug",
                34 to "Sep", 39 to "Oct", 43 to "Nov", 47 to "Dec",
            ),
            labels,
        )
    }

    @Test
    fun midYearStartMonthLabels_clampTheFirstMonthToTheGridStart() {
        val labels = HeatmapGridModel.monthLabels(
            year = 2024,
            gridStartDate = LocalDate.of(2024, 7, 7),
            today = LocalDate.of(2024, 12, 31),
        )

        // Jul 1 predates the grid start → clamps to week 0; Aug 1 (Thursday)
        // anchors to its week's Sunday (Aug 4) = 4 weeks from Jul 7.
        assertEquals(
            mapOf(0 to "Jul", 4 to "Aug", 8 to "Sep", 13 to "Oct", 17 to "Nov", 21 to "Dec"),
            labels,
        )
    }

    @Test
    fun monthsAfterToday_areNotLabelled() {
        val labels = HeatmapGridModel.monthLabels(
            year = 2024,
            gridStartDate = LocalDate.of(2024, 1, 1),
            today = LocalDate.of(2024, 3, 15),
        )

        assertEquals(mapOf(0 to "Jan", 4 to "Feb", 8 to "Mar"), labels)
    }

    // ── levelFor: quartile thresholds ────────────────────────────────────

    @Test
    fun levelFor_quartileEdges() {
        val max = 100L
        assertEquals(0, HeatmapGridModel.levelFor(0, max))
        assertEquals(1, HeatmapGridModel.levelFor(1, max))
        assertEquals(1, HeatmapGridModel.levelFor(25, max))  // ≤ 0.25 edge → 1
        assertEquals(2, HeatmapGridModel.levelFor(26, max))
        assertEquals(2, HeatmapGridModel.levelFor(50, max))  // ≤ 0.50 edge → 2
        assertEquals(3, HeatmapGridModel.levelFor(51, max))
        assertEquals(3, HeatmapGridModel.levelFor(75, max))  // ≤ 0.75 edge → 3
        assertEquals(4, HeatmapGridModel.levelFor(76, max))
        assertEquals(4, HeatmapGridModel.levelFor(100, max))
        // Above-max values coerce to ratio 1.0.
        assertEquals(4, HeatmapGridModel.levelFor(200, max))
    }

    // ── clampFocus: TV cursor movement, no wrap-around ───────────────────

    @Test
    fun clampFocus_movesWithinThePopulatedRange() {
        assertEquals(4, HeatmapGridModel.clampFocus(5, -1, 0, 10))
        // Large deltas coerce, they do not reject.
        assertEquals(10, HeatmapGridModel.clampFocus(5, 7, 0, 10))
        assertEquals(0, HeatmapGridModel.clampFocus(5, -7, 0, 10))
    }

    @Test
    fun clampFocus_neverWraps() {
        // At either edge the cursor stays put → null (key left unconsumed).
        assertNull(HeatmapGridModel.clampFocus(0, -1, 0, 10))
        assertNull(HeatmapGridModel.clampFocus(10, 1, 0, 10))
        // A delta that coerces back onto the current cell is also a no-move.
        assertNull(HeatmapGridModel.clampFocus(0, -5, 0, 10))
        // Flat-index movement: end of one week steps into the same weekday of
        // the next week, never to the row start.
        assertEquals(7, HeatmapGridModel.clampFocus(6, 1, 0, 48))
        // Empty-grid sentinel (minIndex < 0) never moves.
        assertNull(HeatmapGridModel.clampFocus(5, 1, -1, 10))
    }

    // ── initialFocusedCellIndex ──────────────────────────────────────────

    @Test
    fun initialFocus_prefersToday_elseFirstPopulatedCell() {
        val today = LocalDate.of(2024, 6, 15)
        val (grid, _) = HeatmapGridModel.calculateGrid(2024, emptyList(), null, today)
        assertEquals(
            grid.indexOfFirst { it?.date == today },
            HeatmapGridModel.initialFocusedCellIndex(grid, today),
        )

        // today outside the year → first populated cell.
        assertEquals(
            grid.indexOfFirst { it != null },
            HeatmapGridModel.initialFocusedCellIndex(grid, LocalDate.of(2025, 1, 1)),
        )
    }

    // ── scrollTargetForFocus: viewport coercion ──────────────────────────

    @Test
    fun scrollTarget_bringsTheFocusedWeekIntoView() {
        // Focused week 0 (cellLeft 0) scrolled past the viewport's left edge
        // → scroll back to 0.
        assertEquals(
            0f,
            HeatmapGridModel.scrollTargetForFocus(
                focusedIndex = 0, cellStridePx = 13f, cellSizePx = 11f,
                scrollValuePx = 39, viewportWidthPx = 50f,
            ),
        )
        // Focused week 3 (cellLeft 39) clipped at the right edge → align the
        // cell's right side with the viewport's right edge.
        assertEquals(
            10f,
            HeatmapGridModel.scrollTargetForFocus(
                focusedIndex = 21, cellStridePx = 13f, cellSizePx = 11f,
                scrollValuePx = 0, viewportWidthPx = 40f,
            ),
        )
        // Already fully visible → no scroll.
        assertNull(
            HeatmapGridModel.scrollTargetForFocus(
                focusedIndex = 21, cellStridePx = 13f, cellSizePx = 11f,
                scrollValuePx = 10, viewportWidthPx = 40f,
            ),
        )
        assertNull(
            HeatmapGridModel.scrollTargetForFocus(
                focusedIndex = 3, cellStridePx = 13f, cellSizePx = 11f,
                scrollValuePx = 0, viewportWidthPx = 50f,
            ),
        )
    }

    // ── dayAt / isInsideGrid: pointer hit resolution ─────────────────────

    @Test
    fun dayAt_originAndCellCenters_resolveToTheirDates() {
        val grid = fullYear2024Grid()

        // Origin corner: Jan 1 2024 is a Monday, so week 0's Sunday cell (0,0)
        // receives Jan 7 — the Monday-start grid's pinned indexing. Cell
        // centers sit at index*stride + 5.5.
        assertEquals(
            LocalDate.of(2024, 1, 7),
            HeatmapGridModel.dayAt(5.5f, 5.5f, stride, 52, grid),
        )
        assertEquals(
            LocalDate.of(2024, 1, 1),
            HeatmapGridModel.dayAt(5.5f, 17.5f, stride, 52, grid),
        )
        // Leap-year February arm: 2024-02-29 is a Thursday; its ISO Mon–Sun
        // week (Feb 26 – Mar 3) anchors to the Sunday Mar 3 → cell (8,4).
        assertEquals(
            LocalDate.of(2024, 2, 29),
            HeatmapGridModel.dayAt(8 * stride + 5.5f, 4 * stride + 5.5f, stride, 52, grid),
        )
    }

    @Test
    fun dayAt_exactHalfStrideBoundaries_roundUpToTheNextCell() {
        val grid = fullYear2024Grid()

        // x = 6.0 is exactly half a stride → roundToInt rounds up to week 1.
        assertEquals(
            LocalDate.of(2024, 1, 14),
            HeatmapGridModel.dayAt(6f, 5.5f, stride, 52, grid),
        )
        // Same on the y axis → day 1.
        assertEquals(
            LocalDate.of(2024, 1, 1),
            HeatmapGridModel.dayAt(5.5f, 6f, stride, 52, grid),
        )
    }

    @Test
    fun dayAt_gapPositions_resolveToTheNearerCell_neverAMiss() {
        val grid = fullYear2024Grid()

        // The 2px gap between the week-0 and week-1 cells spans x ∈ [11, 13),
        // but the round-to-nearest-stride conversion assigns the whole of it
        // to week 1 — gaps are not misses under the pinned math.
        assertEquals(
            LocalDate.of(2024, 1, 14),
            HeatmapGridModel.dayAt(11.5f, 5.5f, stride, 52, grid),
        )
    }

    @Test
    fun dayAt_beyondEdge_isOutsideTheGrid() {
        val grid = fullYear2024Grid()

        // x = 618.0 = 51.5 strides → rounds to week 52, beyond the 52 columns;
        // the canvas is 52*12 = 624px wide, so the last 6px band is dead.
        assertFalse(HeatmapGridModel.isInsideGrid(618f, 5.5f, stride, 52))
        assertNull(HeatmapGridModel.dayAt(618f, 5.5f, stride, 52, grid))
        // y = 78.0 = 6.5 strides → rounds to day 7; the canvas's +4px bottom
        // padding band (canvas height 88px) is dead too.
        assertFalse(HeatmapGridModel.isInsideGrid(5.5f, 78f, stride, 52))
        assertNull(HeatmapGridModel.dayAt(5.5f, 80f, stride, 52, grid))
    }

    @Test
    fun dayAt_inGridUnpopulatedCell_isInsideButNull() {
        // 2023 starts on Sunday Jan 1: it fills only the (0,0) slot, while
        // Jan 2 onward anchor to the NEXT column (ISO Mon–Sun weeks) — so
        // (0,1..6) stay null inside the grid.
        val (grid, numWeeks) = HeatmapGridModel.calculateGrid(
            year = 2023,
            dailyActivities = emptyList(),
            minActivityDate = null,
            today = LocalDate.of(2023, 12, 31),
        )
        assertEquals(LocalDate.of(2023, 1, 1), grid[0]?.date)
        assertNull(grid[1])

        // The screen keeps dispatching in-grid taps on these cells as a null
        // day — unlike the dead bands beyond the edge.
        val x = 0 * stride + 5.5f
        val y = 1 * stride + 5.5f
        assertTrue(HeatmapGridModel.isInsideGrid(x, y, stride, numWeeks))
        assertNull(HeatmapGridModel.dayAt(x, y, stride, numWeeks, grid))
    }

    @Test
    fun dayAt_midYearStart_firstWeekResolvesFromItsSunday() {
        val (grid, numWeeks) = HeatmapGridModel.calculateGrid(
            year = 2024,
            dailyActivities = emptyList(),
            minActivityDate = LocalDate.of(2024, 7, 10),
            today = LocalDate.of(2024, 12, 31),
        )

        // The mid-year grid starts on its backed-up Sunday 2024-07-07 → the
        // origin corner resolves to it.
        assertEquals(
            LocalDate.of(2024, 7, 7),
            HeatmapGridModel.dayAt(5.5f, 5.5f, stride, numWeeks, grid),
        )
    }

    @Test
    fun dayAt_afterToday_isInsideButNull() {
        val (grid, numWeeks) = HeatmapGridModel.calculateGrid(
            year = 2024,
            dailyActivities = emptyList(),
            minActivityDate = null,
            today = LocalDate.of(2024, 8, 15),
        )

        // Everything after `today` (Aug 15, a Thursday) is null. Tap the
        // first unpopulated cell's center — positions taken from the grid
        // itself, no hand-derived week math.
        val lastPopulated = grid.indexOfLast { it != null }
        val firstNull = grid.indexOfFirst { it == null }
        assertTrue(firstNull in 1 until lastPopulated)
        assertEquals(LocalDate.of(2024, 8, 15), grid[lastPopulated]?.date)
        assertNull(grid[firstNull])

        val x = (firstNull / 7) * stride + 5.5f
        val y = (firstNull % 7) * stride + 5.5f
        assertTrue(HeatmapGridModel.isInsideGrid(x, y, stride, numWeeks))
        assertNull(HeatmapGridModel.dayAt(x, y, stride, numWeeks, grid))
    }
}
