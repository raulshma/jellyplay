package com.raulshma.jellyplay.widget

/**
 * Single home for the widget layout breakpoints that used to sit as magic
 * numbers across 7+ call sites (the factories' `onDataSetChanged`/`getViewAt`
 * and the providers' responsive ladders). Each widget family keeps its OWN
 * value — 220 vs 250 vs 110 are per-widget divergences, so they are declared
 * here as named constants, not unified.
 *
 * | Constant | dp | Consumer |
 * | --- | --- | --- |
 * | [CONTINUE_WATCHING_DEFAULT_HEIGHT_DP] | 220 | Continue Watching factory + provider fallback height |
 * | [RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP] | 250 | Library/Seerr factory + provider fallback height |
 * | [NOW_PLAYING_DEFAULT_HEIGHT_DP] | 110 | Now Playing provider fallback height |
 * | [CW_ROW_PROGRESS_HIDE_WIDTH_DP] | 240 | Continue Watching row: progress bar + remaining label hidden below |
 * | [CW_ROW_POSTER_HIDE_WIDTH_DP] | 180 | Continue Watching row: poster hidden below |
 * | [CW_HEADER_HIDE_HEIGHT_DP] | 150 | Continue Watching chrome: header hidden below |
 * | [CW_SEE_ALL_HIDE_WIDTH_DP] | 220 | Continue Watching chrome: "See all" hidden below |
 * | [GRID_HEADER_HIDE_HEIGHT_DP] | 130 | grid chrome: whole header hidden below |
 * | [GRID_HEADER_COMPACT_HEIGHT_DP] | 180 | grid chrome: header stays, subtitle + refresh hidden below |
 * | [GRID_CELL_TEXT_HIDE_HEIGHT_DP] | 200 | grid rows: text container hidden below (via [isTooSmallForText]) |
 * | [GRID_CELL_TEXT_HIDE_WIDTH_DP] | 180 | grid rows: text container hidden below (via [isTooSmallForText]) |
 *
 * The Now Playing widget's responsive ladder constants (70/100/180/280 dp)
 * are NOT here — they were already single-homed in `NowPlayingWidgetPolicy`.
 */
object WidgetLayoutThresholds {

    // ── Fallback heights (dp) when app-widget options report ≤ 0 ────────

    /** Continue Watching family. */
    const val CONTINUE_WATCHING_DEFAULT_HEIGHT_DP = 220

    /** Library + Seerr recommendation grids. */
    const val RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP = 250

    /** Now Playing widget. */
    const val NOW_PLAYING_DEFAULT_HEIGHT_DP = 110

    // ── Continue Watching row (factory `getViewAt`) ─────────────────────

    /** Below this width the row hides its progress bar and remaining label. */
    const val CW_ROW_PROGRESS_HIDE_WIDTH_DP = 240

    /** Below this width the row hides its poster. */
    const val CW_ROW_POSTER_HIDE_WIDTH_DP = 180

    // ── Continue Watching widget chrome (provider) ──────────────────────

    /** Below this height the widget header is hidden. */
    const val CW_HEADER_HIDE_HEIGHT_DP = 150

    /** Below this width the "See all" action is hidden. */
    const val CW_SEE_ALL_HIDE_WIDTH_DP = 220

    // ── Recommendation grid chrome (provider header ladder) ─────────────

    /** Below this height the whole grid header is hidden. */
    const val GRID_HEADER_HIDE_HEIGHT_DP = 130

    /** Below this height the header stays but subtitle + refresh drop out. */
    const val GRID_HEADER_COMPACT_HEIGHT_DP = 180

    // ── Recommendation grid rows (both factories + loading views) ───────

    /** Cell text container hides below this height (or the width below). */
    const val GRID_CELL_TEXT_HIDE_HEIGHT_DP = 200

    /** Cell text container hides below this width (or the height above). */
    const val GRID_CELL_TEXT_HIDE_WIDTH_DP = 180
}
