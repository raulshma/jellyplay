package com.raulshma.jellyplay.feature.admin.statistics.components

import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.ui.components.formatDurationFromMinutes
import java.util.Locale

/**
 * Compose-free geometry and policy core for the statistics charts, extracted
 * verbatim from [ChartComponents] (the insights `HeatmapGridModel` precedent):
 * the bar/trend label-admission ladders, the value→pixel normalizations, the
 * pie's sweep/start-angle accumulation + top-5 legend + percentage math, and
 * the number/duration formatters. The composables keep only the Canvas
 * drawing, the color assignment, and the animation shells that call this
 * model — chartWidth/chartHeight are parameters, so every decision here is
 * deterministically testable without composition.
 *
 * Everything is a verbatim move: the formulas, the `coerceAtLeast(1L)`
 * clamps, and the ladder rungs reproduce the pre-extraction inline code
 * exactly (pinned by ChartGeometryTest).
 */
internal object ChartGeometry {

    // ── value normalization ───────────────────────────────────────────────

    /**
     * The normalization ceiling shared by every value-scaled chart: the data's
     * max, clamped to ≥ 1 so a zero/all-zero dataset divides by 1 instead of
     * collapsing to NaN/0-div.
     */
    fun maxValueOrOne(values: List<Long>): Long =
        values.maxOfOrNull { it }?.coerceAtLeast(1L) ?: 1L

    /**
     * One activity bar's pixel height: the point's share of [maxValue] scaled
     * into [chartHeight], times the entrance [animProgress]. A non-positive
     * [maxValue] contributes nothing (the pre-extraction `if (maxValue > 0)`
     * guard).
     */
    fun barHeight(value: Long, maxValue: Long, chartHeight: Float, animProgress: Float = 1f): Float =
        if (maxValue > 0) {
            (value.toFloat() / maxValue.toFloat()) * chartHeight * animProgress
        } else 0f

    // ── label admission (which x-axis ticks render) ───────────────────────

    /**
     * ActivityBarChart's label-admission ladder: every point labels itself at
     * ≤7 points, every 2nd up to 15, every 5th up to 31, then `size / 6` so
     * the row never grows with the data.
     */
    fun activityLabelStep(size: Int): Int = when {
        size <= 7 -> 1
        size <= 15 -> 2
        size <= 31 -> 5
        else -> size / 6
    }

    /**
     * TrendLineChart's denser variant of the same ladder (3/7 mid-rungs and a
     * `size / 5` tail — the trend canvas is shorter, so it skips more labels
     * sooner).
     */
    fun trendLabelStep(size: Int): Int = when {
        size <= 7 -> 1
        size <= 15 -> 3
        size <= 31 -> 7
        else -> size / 5
    }

    /**
     * Whether tick [index] renders under [step]: always the first and last
     * point (chart extent markers), plus every [step]-th in between.
     */
    fun admitsLabel(index: Int, step: Int, lastIndex: Int): Boolean =
        index == 0 || index % step == 0 || index == lastIndex

    // ── trend-line point normalization ────────────────────────────────────

    /**
     * X pixel for trend point [index]: spread evenly across [chartWidth], or
     * the horizontal center for the single-point dataset (no line, one dot).
     */
    fun trendX(index: Int, pointCount: Int, chartWidth: Float): Float =
        if (pointCount > 1) {
            (index.toFloat() / (pointCount - 1)) * chartWidth
        } else chartWidth / 2

    /**
     * Y pixel for a trend point of [value]: baseline at [chartHeight], rising
     * by the value's share of [maxValue], scaled by the entrance [progress].
     */
    fun trendY(value: Long, maxValue: Long, chartHeight: Float, progress: Float): Float =
        chartHeight - (value.toFloat() / maxValue.toFloat()) * chartHeight * progress

    // ── pie geometry + legend ─────────────────────────────────────────────

    /** One resolved pie segment: draw an arc at [startAngle] for [sweepAngle] degrees. */
    data class PieSlice(val startAngle: Float, val sweepAngle: Float)

    /**
     * The pie's value total, clamped to ≥ 1 so an all-zero dataset still
     * yields 0° slices instead of a division by zero.
     */
    fun pieTotal(data: List<ContentBreakdown>): Long =
        data.sumOf { it.value }.coerceAtLeast(1L)

    /**
     * Sweep + start-angle accumulation for the whole pie: the first segment
     * starts at −90° (12 o'clock) and each subsequent one picks up where the
     * previous ended, so segments stay adjacent at every [progress] value
     * (each sweep is scaled BEFORE accumulation — the entrance animation
     * sweeps the whole ring out from the top, never rotating segments apart).
     * At progress 1 the sweeps sum to exactly 360°.
     */
    fun pieSlices(data: List<ContentBreakdown>, total: Long, progress: Float = 1f): List<PieSlice> {
        var startAngle = -90f
        return data.map { item ->
            val sweepAngle = (item.value.toFloat() / total.toFloat()) * 360f * progress
            val slice = PieSlice(startAngle, sweepAngle)
            startAngle += sweepAngle
            slice
        }
    }

    /** One legend row: the item plus its percentage share of the pie total. */
    data class PieLegendEntry(val item: ContentBreakdown, val percentage: Float)

    /**
     * The legend shows at most the top 5 items (a donut with more slices is
     * unreadable); order follows the data (the repository's descending order).
     */
    fun topLegendEntries(data: List<ContentBreakdown>, total: Long): List<PieLegendEntry> =
        data.take(5).map { item ->
            PieLegendEntry(item, (item.value.toFloat() / total.toFloat()) * 100)
        }

    /** Legend percentage text: whole percents, rounded (33.33% → "33%"). */
    fun formatPercentage(percentage: Float): String =
        String.format(Locale.getDefault(), "%.0f%%", percentage)

    // ── number / duration formatting ──────────────────────────────────────

    /**
     * Stat-card value compaction: M/K suffixes at the million/thousand
     * thresholds with one decimal; below 1 000 the value renders as-is
     * (including 0 and negatives — only the positive thresholds compact).
     */
    fun formatNumber(value: Long): String = when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }

    /** Comparison-card duration line, delegated to the shared core/ui formatter. */
    fun formatDuration(totalMinutes: Long): String =
        formatDurationFromMinutes(totalMinutes)
}
