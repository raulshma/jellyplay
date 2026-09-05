package com.raulshma.jellyplay.widget

import com.raulshma.jellyplay.core.ui.components.formatDurationMsNoHours

/**
 * The Now Playing widget's pure render and seek decisions — everything that
 * is computed from plain numbers, split out of `NowPlayingWidget` so it is
 * unit-testable on the JVM (same extraction as [WidgetPushSnapshot]). The
 * widget keeps only the RemoteViews/PendingIntent plumbing that consumes
 * these rules.
 *
 * Units: durations are milliseconds, seek percents are whole numbers on the
 * closed 0–100 scale the seek zones broadcast, and widget sizes are the dp
 * values [WidgetDimensions] reports (app-widget options are in dp).
 */

/** Rewind/forward transport step behind the widget's seek buttons. */
internal const val SEEK_DELTA_MS = 10_000L

/**
 * The seek percents wired onto the widget's seven seek zones, in zone
 * order: evenly spaced stops so a tap anywhere on the progress row jumps to
 * the nearest seventh of the duration. Whole numbers, 0–100.
 */
internal val SEEK_PERCENTS = intArrayOf(0, 17, 33, 50, 67, 83, 100)

/**
 * Final per-view visibility the responsive ladder resolves for one widget
 * size: `true` renders the element VISIBLE, `false` GONE.
 */
internal data class NowPlayingWidgetLayout(
    val showAlbumArt: Boolean,
    val showProgressContainer: Boolean,
    val showPosition: Boolean,
    val showSubtitle: Boolean,
    val showRewind: Boolean,
    val showForward: Boolean,
    val showPrev: Boolean,
    val showNext: Boolean,
    val showPlayPause: Boolean,
)

/**
 * The responsive visibility ladder, as a pure decision on the widget's size
 * in dp ([WidgetDimensions] values):
 *
 *  - Width < 180dp (compact) keeps prev/next/play-pause so the widget stays
 *    usable down to its 110dp min-resize width; only the artwork, progress,
 *    subtitle, and the secondary seek buttons drop out.
 *  - 180–279dp (medium) restores the artwork and subtitle; the secondary
 *    rewind/forward buttons stay hidden.
 *  - >= 280dp (full) shows everything.
 *  - Height < 100dp hides the progress row (container + position label).
 *    The height rules run after the width rules and re-decide that row in
 *    both arms, so progress visibility depends on height alone — a
 *    compact-but-tall widget still shows it.
 *  - Height < 70dp additionally hides the subtitle.
 */
internal fun responsiveNowPlayingLayout(widthDp: Int, heightDp: Int): NowPlayingWidgetLayout {
    val wide = widthDp >= 180

    return NowPlayingWidgetLayout(
        showAlbumArt = wide,
        showProgressContainer = heightDp >= 100,
        showPosition = heightDp >= 100,
        showSubtitle = wide && heightDp >= 70,
        showRewind = widthDp >= 280,
        showForward = widthDp >= 280,
        // The transport row never hides — compact drops only the artwork,
        // progress, subtitle, and secondary seek buttons.
        showPrev = true,
        showNext = true,
        showPlayPause = true,
    )
}

/**
 * The position label: `cur / total` while playing, `Paused · cur / total`
 * when not. Unknown durations (<= 0) render the em-dash placeholder because
 * there is no meaningful fraction to show.
 */
internal fun formatPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean): String {
    if (durationMs <= 0L) return "—"
    val cur = formatDurationMsNoHours(positionMs)
    val total = formatDurationMsNoHours(durationMs)
    return if (isPlaying) "$cur / $total" else "Paused · $cur / $total"
}

/**
 * Gate for the seek broadcast extra: whole-number percents on the closed
 * 0–100 scale (the `-1` default for a missing extra is rejected).
 */
internal fun isValidSeekPercent(percent: Int): Boolean = percent in 0..100

/**
 * Position for a seek-zone tap: [percent] percent of [durationMs]. Null
 * when the duration is unknown (<= 0) — the widget skips the seek instead
 * of dividing by it. Valid percents can never leave `0..durationMs`; the
 * clamp keeps out-of-range percents on the endpoints (defensive — the
 * broadcast gate rejects them first). Integer division truncates, so e.g.
 * 33% of 100_001ms is 33_000ms.
 */
internal fun seekTargetMs(percent: Int, durationMs: Long): Long? {
    if (durationMs <= 0L) return null
    return (percent.toLong() * durationMs / 100L).coerceIn(0L, durationMs)
}

/**
 * Progress-bar level on the widget's 0–1000 per-mille scale
 * (`setProgressBar` max 1_000): the fraction of [durationMs] elapsed,
 * clamped to the bar ends, and 0 when the duration is unknown. Shared by
 * the full bind and the 1 Hz partial push so both render identical levels.
 */
internal fun progressPerMille(positionMs: Long, durationMs: Long): Int =
    if (durationMs <= 0L) {
        0
    } else {
        ((positionMs.toFloat() / durationMs) * 1_000f).toInt().coerceIn(0, 1_000)
    }

/**
 * Blank/missing metadata fallbacks for the full bind: the title falls back
 * to an em dash and the subtitle to a single space (never an empty string).
 */
internal fun widgetDisplayTitle(title: String?): String =
    title?.takeIf { it.isNotBlank() } ?: "—"

internal fun widgetDisplaySubtitle(subtitle: String?): String =
    subtitle?.takeIf { it.isNotBlank() } ?: " "
