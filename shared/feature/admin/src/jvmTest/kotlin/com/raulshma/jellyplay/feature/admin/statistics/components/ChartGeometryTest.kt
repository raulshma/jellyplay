package com.raulshma.jellyplay.feature.admin.statistics.components

import com.raulshma.jellyplay.core.model.ContentBreakdown
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [ChartGeometry] — the Compose-free chart decision core extracted from
 * ChartComponents. Each test reproduces the pre-extraction inline behaviour:
 * the two label-admission ladders (including their exact rung edges), the
 * normalization formulas and their clamps, the pie's 360° sweep conservation
 * and −90° start, the top-5 legend cut, and the number/duration formatter
 * corners (0, negative, threshold boundaries, large values).
 *
 * formatNumber/formatPercentage ride `String.format` with the default
 * locale, so the locale-sensitive decimal-point assertions pin the default
 * to US for the test's duration (the thresholds and rounding — the actual
 * decisions — are locale-independent).
 */
class ChartGeometryTest {

    private var previousDefaultLocale: Locale? = null

    @BeforeTest
    fun pinDefaultLocale() {
        previousDefaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreDefaultLocale() {
        previousDefaultLocale?.let(Locale::setDefault)
    }

    // ── value normalization ───────────────────────────────────────────────

    @Test
    fun `maxValueOrOne takes the data max and clamps to 1`() {
        assertEquals(9L, ChartGeometry.maxValueOrOne(listOf(1L, 9L, 4L)))
        // All-zero data: clamped to 1 so normalizations never divide by zero.
        assertEquals(1L, ChartGeometry.maxValueOrOne(listOf(0L, 0L)))
        assertEquals(1L, ChartGeometry.maxValueOrOne(emptyList()))
    }

    @Test
    fun `barHeight scales by share of max and entrance progress`() {
        // Half of max in a 100px chart = 50px at full progress.
        assertEquals(50f, ChartGeometry.barHeight(5L, 10L, 100f, 1f))
        // Entrance progress scales linearly; 0 progress = 0 height.
        assertEquals(25f, ChartGeometry.barHeight(5L, 10L, 100f, 0.5f))
        assertEquals(0f, ChartGeometry.barHeight(5L, 10L, 100f, 0f))
        // Max itself reaches the full chart height (baseline y = 0).
        assertEquals(100f, ChartGeometry.barHeight(10L, 10L, 100f, 1f))
    }

    @Test
    fun `barHeight collapses for zero or negative max and zero value`() {
        assertEquals(0f, ChartGeometry.barHeight(5L, 0L, 100f, 1f))
        assertEquals(0f, ChartGeometry.barHeight(0L, 10L, 100f, 1f))
    }

    // ── label admission ladders ───────────────────────────────────────────

    @Test
    fun `activity ladder rung edges 7-8, 15-16, 31-32`() {
        assertEquals(1, ChartGeometry.activityLabelStep(7))
        assertEquals(2, ChartGeometry.activityLabelStep(8))
        assertEquals(2, ChartGeometry.activityLabelStep(15))
        assertEquals(5, ChartGeometry.activityLabelStep(16))
        assertEquals(5, ChartGeometry.activityLabelStep(31))
        // Above 31 the step is size/6 (integer division): 32/6 = 5, growing with size.
        assertEquals(5, ChartGeometry.activityLabelStep(32))
        assertEquals(10, ChartGeometry.activityLabelStep(60))
    }

    @Test
    fun `trend ladder rung edges 7-8, 15-16, 31-32`() {
        assertEquals(1, ChartGeometry.trendLabelStep(7))
        assertEquals(3, ChartGeometry.trendLabelStep(8))
        assertEquals(3, ChartGeometry.trendLabelStep(15))
        assertEquals(7, ChartGeometry.trendLabelStep(16))
        assertEquals(7, ChartGeometry.trendLabelStep(31))
        assertEquals(6, ChartGeometry.trendLabelStep(32))
        assertEquals(12, ChartGeometry.trendLabelStep(60))
    }

    @Test
    fun `admitsLabel always keeps first and last, else only multiples of step`() {
        val lastIndex = 9
        assertTrue(ChartGeometry.admitsLabel(0, 3, lastIndex))
        assertTrue(ChartGeometry.admitsLabel(9, 3, lastIndex))
        assertTrue(ChartGeometry.admitsLabel(6, 3, lastIndex))
        assertFalse(ChartGeometry.admitsLabel(4, 3, lastIndex))
        // The last index can also be a step multiple — still one label.
        assertTrue(ChartGeometry.admitsLabel(9, 3, 9))
        // Every point admits at step 1.
        assertTrue(ChartGeometry.admitsLabel(7, 1, 9))
    }

    // ── trend point normalization ─────────────────────────────────────────

    @Test
    fun `trendX spreads points evenly and centers a single point`() {
        assertEquals(0f, ChartGeometry.trendX(0, 5, 100f))
        assertEquals(100f, ChartGeometry.trendX(4, 5, 100f))
        assertEquals(50f, ChartGeometry.trendX(2, 5, 100f))
        assertEquals(50f, ChartGeometry.trendX(0, 1, 100f))
    }

    @Test
    fun `trendY runs baseline to top and clamps max value to y=0 at full progress`() {
        assertEquals(100f, ChartGeometry.trendY(0L, 10L, 100f, 1f))
        assertEquals(0f, ChartGeometry.trendY(10L, 10L, 100f, 1f))
        assertEquals(50f, ChartGeometry.trendY(5L, 10L, 100f, 1f))
        // Entrance progress lifts the whole line from the baseline.
        assertEquals(100f, ChartGeometry.trendY(10L, 10L, 100f, 0f))
        assertEquals(50f, ChartGeometry.trendY(10L, 10L, 100f, 0.5f))
    }

    // ── pie geometry + legend ─────────────────────────────────────────────

    private fun breakdown(value: Long) = ContentBreakdown(label = "v$value", value = value)

    @Test
    fun `pieSlices start at -90 and sweeps sum to 360 degrees`() {
        val data = listOf(breakdown(50), breakdown(25), breakdown(25))
        val slices = ChartGeometry.pieSlices(data, ChartGeometry.pieTotal(data))

        assertEquals(3, slices.size)
        assertEquals(-90f, slices[0].startAngle)
        assertEquals(180f, slices[0].sweepAngle)
        // Each segment picks up where the previous ended — no gaps, no overlaps.
        assertEquals(slices[0].startAngle + slices[0].sweepAngle, slices[1].startAngle)
        assertEquals(slices[1].startAngle + slices[1].sweepAngle, slices[2].startAngle)
        assertEquals(360f, slices.sumOf { it.sweepAngle.toDouble() }.toFloat())
    }

    @Test
    fun `pieScales sweeps by progress and stays adjacent during the entrance animation`() {
        val data = listOf(breakdown(75), breakdown(25))
        val half = ChartGeometry.pieSlices(data, ChartGeometry.pieTotal(data), progress = 0.5f)
        assertEquals(270f * 0.5f, half[0].sweepAngle)
        assertEquals(90f * 0.5f, half[1].sweepAngle)
        assertEquals(half[0].startAngle + half[0].sweepAngle, half[1].startAngle)
        assertEquals(180f, half.sumOf { it.sweepAngle.toDouble() }.toFloat())

        val none = ChartGeometry.pieSlices(data, ChartGeometry.pieTotal(data), progress = 0f)
        assertEquals(0f, none.sumOf { it.sweepAngle.toDouble() }.toFloat())
    }

    @Test
    fun `pieTotal clamps an all-zero dataset to 1 so slices collapse to zero degrees`() {
        assertEquals(0L, listOf(0L, 0L).sum())
        val total = ChartGeometry.pieTotal(listOf(breakdown(0), breakdown(0)))
        assertEquals(1L, total)
        val slices = ChartGeometry.pieSlices(listOf(breakdown(0), breakdown(0)), total)
        assertEquals(0f, slices.sumOf { it.sweepAngle.toDouble() }.toFloat())
    }

    @Test
    fun `topLegendEntries cuts at five and keeps data order with percentage shares`() {
        val data = (1L..7L).map { breakdown(it * 10) }
        val legend = ChartGeometry.topLegendEntries(data, ChartGeometry.pieTotal(data))
        assertEquals(5, legend.size)
        assertEquals(data.take(5), legend.map { it.item })

        val two = ChartGeometry.topLegendEntries(listOf(breakdown(75), breakdown(25)), 100L)
        assertEquals(75f, two[0].percentage)
        assertEquals(25f, two[1].percentage)
    }

    @Test
    fun `formatPercentage rounds to whole percents`() {
        assertEquals("75%", ChartGeometry.formatPercentage(75f))
        assertEquals("33%", ChartGeometry.formatPercentage(100f / 3f))
        assertEquals("0%", ChartGeometry.formatPercentage(0f))
        assertEquals("100%", ChartGeometry.formatPercentage(100f))
    }

    // ── number / duration formatting corners ─────────────────────────────

    @Test
    fun `formatNumber corners - zero, negative and sub-threshold values stay literal`() {
        assertEquals("0", ChartGeometry.formatNumber(0L))
        assertEquals("-42", ChartGeometry.formatNumber(-42L))
        assertEquals("999", ChartGeometry.formatNumber(999L))
        assertEquals("7", ChartGeometry.formatNumber(7L))
    }

    @Test
    fun `formatNumber compacts at the thousand and million thresholds with one decimal`() {
        assertEquals("1.0K", ChartGeometry.formatNumber(1_000L))
        assertEquals("1.2K", ChartGeometry.formatNumber(1_234L))
        // Values just under a million still compact as K (even past 1000.0K).
        assertEquals("1000.0K", ChartGeometry.formatNumber(999_999L))
        assertEquals("1.0M", ChartGeometry.formatNumber(1_000_000L))
        assertEquals("1.5M", ChartGeometry.formatNumber(1_500_000L))
        assertEquals("12.3M", ChartGeometry.formatNumber(12_345_678L))
    }

    @Test
    fun `formatDuration corners - zero, negative and sub-minute durations`() {
        // 0 minutes (and any sub-minute remainder) renders as "0m".
        assertEquals("0m", ChartGeometry.formatDuration(0L))
        assertEquals("45m", ChartGeometry.formatDuration(45L))
        assertEquals("1h", ChartGeometry.formatDuration(60L))
        assertEquals("2h 5m", ChartGeometry.formatDuration(125L))
        // Negatives are not special-cased: integer division floors the hour
        // count and the modulo keeps the negative remainder.
        assertEquals("-5m", ChartGeometry.formatDuration(-5L))
    }
}
