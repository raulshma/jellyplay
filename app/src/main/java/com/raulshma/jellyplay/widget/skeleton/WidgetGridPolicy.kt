package com.raulshma.jellyplay.widget.skeleton

import com.raulshma.jellyplay.widget.WidgetDimensions
import com.raulshma.jellyplay.widget.WidgetLayoutThresholds
import com.raulshma.jellyplay.widget.isTooSmallForText

/**
 * The widget grid rows' and provider chrome's pure render decisions —
 * everything computed from plain values, split out of the three
 * [WidgetGridFactory] adapters and the widget providers so the rules are
 * JVM-testable without RemoteViews or a widget host (same extraction as
 * `NowPlayingWidgetPolicy`). The factories/providers keep only the
 * RemoteViews plumbing that consumes these rules.
 *
 * Sizes are the dp values [WidgetDimensions] reports (app-widget options are
 * in dp); the numeric breakpoints are single-homed in
 * [WidgetLayoutThresholds].
 */

// ── Library recommendation rows ─────────────────────────────────────────

/**
 * Row title: blank/missing names render the fixed "Untitled" placeholder
 * (never an empty string).
 */
internal fun libraryRowTitle(name: String): String = name.ifBlank { "Untitled" }

/**
 * Row subtitle: `year  ·  ★ rating` (the star only for strictly positive
 * ratings), joined by a double-spaced middot; a lone space when neither
 * exists (never an empty string).
 */
internal fun libraryRowSubtitle(year: Int?, communityRating: Float?): String {
    val parts = mutableListOf<String>()
    year?.let { parts.add(it.toString()) }
    communityRating?.let { rating ->
        if (rating > 0f) parts.add("★ %.1f".format(rating))
    }
    if (parts.isEmpty()) return " "
    return parts.joinToString("  ·  ")
}

// ── Seerr recommendation rows ───────────────────────────────────────────

/**
 * Row subtitle: the server-provided subtitle and the year, joined by a
 * double-spaced middot, blank parts dropped; a lone space when nothing
 * remains (never an empty string).
 */
internal fun seerrRowSubtitle(subtitle: String?, year: Int?): String {
    val parts = mutableListOf<String>()
    parts += subtitle.orEmpty()
    year?.let { parts.add(it.toString()) }
    return parts.filter { it.isNotBlank() }.joinToString("  ·  ").ifBlank { " " }
}

/**
 * The star-rating label for [voteAverage], or null when the rating view must
 * hide (missing or non-positive average renders GONE, not "★ 0.0").
 */
internal fun seerrRatingText(voteAverage: Float?): String? =
    voteAverage?.takeIf { it > 0f }?.let { "★ %.1f".format(it) }

// ── Grid cell text container (Library + Seerr rows and loading views) ───

/**
 * True when the row's title/subtitle container must render: it hides
 * entirely whenever the widget reports the too-small-for-text size
 * ([isTooSmallForText]); an unresolved dimension read keeps the text.
 */
internal fun gridCellTextVisible(dims: WidgetDimensions?): Boolean =
    dims?.isTooSmallForText() != true

// ── Continue Watching rows ──────────────────────────────────────────────

/**
 * Final per-view visibility of one Continue Watching row at the widget's
 * width: `true` renders the element VISIBLE, `false` GONE.
 */
internal data class ContinueWatchingRowVisibility(
    val showProgress: Boolean,
    val showPoster: Boolean,
)

/**
 * The row's responsive width rules: the progress bar + remaining label hide
 * below 240dp and the poster below 180dp. An unresolved dimension read
 * (widthDp null) shows both — same as the pre-extraction `widgetDims?.let`
 * branch whose arms defaulted to false.
 */
internal fun continueWatchingRowVisibility(widthDp: Int?): ContinueWatchingRowVisibility {
    val width = widthDp ?: return ContinueWatchingRowVisibility(showProgress = true, showPoster = true)
    return ContinueWatchingRowVisibility(
        showProgress = width >= WidgetLayoutThresholds.CW_ROW_PROGRESS_HIDE_WIDTH_DP,
        showPoster = width >= WidgetLayoutThresholds.CW_ROW_POSTER_HIDE_WIDTH_DP,
    )
}

/**
 * Row subtitle: the series name (blank dropped) plus a season/episode tag —
 * `S2E03` with the episode zero-padded to two digits, `S2` when the episode
 * number is missing — joined by a single-spaced middot (narrower than the
 * recommendation grids' double-spaced separator).
 */
internal fun continueWatchingRowSubtitle(
    seriesName: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String {
    val parts = mutableListOf<String>()
    seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    seasonNumber?.let { season ->
        episodeNumber?.let { episode ->
            parts.add("S${season}E${episode.toString().padStart(2, '0')}")
        } ?: parts.add("S$season")
    }
    return parts.joinToString(" · ")
}

/**
 * Playback progress on the row's 0–100 bar scale, or null when either the
 * runtime or the position is unknown (<= 0 runtime has no meaningful
 * fraction). Truncates rather than rounds; positions outside the runtime
 * clamp to the bar ends.
 */
internal fun continueWatchingProgressPercent(
    runTimeTicks: Long?,
    playbackPositionTicks: Long?,
): Int? {
    val total = runTimeTicks ?: return null
    val position = playbackPositionTicks ?: return null
    if (total <= 0L) return null
    val pct = (position.toDouble() / total.toDouble() * 100.0).toInt()
    return pct.coerceIn(0, 100)
}

/**
 * Whole minutes left in the item (runtime − position, positive only), or
 * null when the "N min left" label must fall back to the progress percent.
 * Unknown ticks coalesce to zero, so a missing runtime never yields a
 * negative remainder.
 */
internal fun continueWatchingMinutesLeft(
    runTimeTicks: Long?,
    playbackPositionTicks: Long?,
): Int? {
    val totalTicks = runTimeTicks ?: 0L
    val positionTicks = playbackPositionTicks ?: 0L
    val leftTicks = totalTicks - positionTicks
    if (leftTicks > 0L) {
        val minsLeft = (leftTicks / 10_000_000L / 60L).toInt()
        if (minsLeft > 0) {
            return minsLeft
        }
    }
    return null
}

// ── Provider chrome ladders ─────────────────────────────────────────────

/**
 * Final header-ladder visibility for one recommendation grid (Library and
 * Seerr share the ladder): below 130dp the whole header hides; 130–179dp
 * keeps the header but drops the subtitle and refresh button; from 180dp
 * everything shows.
 */
internal data class RecommendationGridHeaderVisibility(
    val showHeader: Boolean,
    val showSubtitle: Boolean,
    val showRefresh: Boolean,
)

internal fun recommendationGridHeaderVisibility(heightDp: Int): RecommendationGridHeaderVisibility = when {
    heightDp < WidgetLayoutThresholds.GRID_HEADER_HIDE_HEIGHT_DP ->
        RecommendationGridHeaderVisibility(showHeader = false, showSubtitle = false, showRefresh = false)
    heightDp < WidgetLayoutThresholds.GRID_HEADER_COMPACT_HEIGHT_DP ->
        RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = false, showRefresh = false)
    else ->
        RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = true, showRefresh = true)
}

/**
 * Final chrome visibility for the Continue Watching widget: the header hides
 * below 150dp height and the "See all" action below 220dp width — the two
 * rules are independent (unlike the grids' nested ladder).
 */
internal data class ContinueWatchingChromeVisibility(
    val showHeader: Boolean,
    val showSeeAll: Boolean,
)

internal fun continueWatchingChromeVisibility(widthDp: Int, heightDp: Int): ContinueWatchingChromeVisibility =
    ContinueWatchingChromeVisibility(
        showHeader = heightDp >= WidgetLayoutThresholds.CW_HEADER_HIDE_HEIGHT_DP,
        showSeeAll = widthDp >= WidgetLayoutThresholds.CW_SEE_ALL_HIDE_WIDTH_DP,
    )
