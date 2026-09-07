package com.raulshma.jellyplay.widget.skeleton

import com.raulshma.jellyplay.widget.WidgetDimensions
import com.raulshma.jellyplay.widget.WidgetLayoutThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the widget grid rows' and provider chrome's render
 * decisions, extracted from the three `RemoteViewsFactory` adapters and the
 * widget providers so the rules are testable without RemoteViews (same
 * extraction as `NowPlayingWidgetPolicy`).
 *
 * Pins:
 *  - the Library row title/subtitle rules: the "Untitled" fallback, the
 *    strictly-positive star rating, the double-spaced middot join, and the
 *    lone-space empty fallback;
 *  - the Seerr row subtitle (blank parts dropped) and the star rating that
 *    hides for missing/non-positive averages;
 *  - the shared grid-cell text rule's exact height<200 / width<180
 *    boundaries and the unresolved-dimension default (text stays);
 *  - the Continue Watching row's independent width rules (progress <240dp,
 *    poster <180dp) and the unresolved-dimension default (both shown);
 *  - the Continue Watching subtitle's season/episode tag formats, the
 *    progress percent's truncation and clamping, and the minutes-left
 *    computation's tick math and fallback;
 *  - both provider chrome ladders at their exact thresholds (grid header
 *    130/180, Continue Watching header 150 / see-all 220);
 *  - the threshold constants themselves, so a value can never drift from
 *    its documented meaning.
 */
class WidgetGridPolicyTest {

    // ── libraryRowTitle ──────────────────────────────────────────────────

    @Test
    fun `library titles pass through unchanged`() {
        assertEquals("Inception", libraryRowTitle("Inception"))
    }

    @Test
    fun `blank library titles fall back to Untitled`() {
        assertEquals("Untitled", libraryRowTitle(""))
        assertEquals("Untitled", libraryRowTitle("   "))
    }

    // ── libraryRowSubtitle ───────────────────────────────────────────────

    @Test
    fun `library subtitle with neither year nor rating is a lone space`() {
        assertEquals(" ", libraryRowSubtitle(year = null, communityRating = null))
        assertEquals(" ", libraryRowSubtitle(year = null, communityRating = 0f))
    }

    @Test
    fun `library subtitle joins year and star rating with a double-spaced middot`() {
        assertEquals("1985  ·  ★ 8.1", libraryRowSubtitle(year = 1985, communityRating = 8.1f))
    }

    @Test
    fun `library subtitle renders year or rating alone`() {
        assertEquals("1985", libraryRowSubtitle(year = 1985, communityRating = null))
        assertEquals("★ 7.5", libraryRowSubtitle(year = null, communityRating = 7.5f))
    }

    @Test
    fun `library star rating requires a strictly positive average`() {
        assertEquals(" ", libraryRowSubtitle(year = null, communityRating = -0.5f))
        assertEquals("★ 0.5", libraryRowSubtitle(year = null, communityRating = 0.5f))
    }

    // ── seerrRowSubtitle ─────────────────────────────────────────────────

    @Test
    fun `seerr subtitle with nothing usable is a lone space`() {
        assertEquals(" ", seerrRowSubtitle(subtitle = null, year = null))
        assertEquals(" ", seerrRowSubtitle(subtitle = "  ", year = null))
    }

    @Test
    fun `seerr subtitle joins the server subtitle and year`() {
        assertEquals("Action  ·  2020", seerrRowSubtitle(subtitle = "Action", year = 2020))
    }

    @Test
    fun `seerr subtitle drops blank parts but keeps year`() {
        assertEquals("2020", seerrRowSubtitle(subtitle = "   ", year = 2020))
        assertEquals("Action", seerrRowSubtitle(subtitle = "Action", year = null))
    }

    // ── seerrRatingText ──────────────────────────────────────────────────

    @Test
    fun `seerr rating hides for missing or non-positive averages`() {
        assertNull(seerrRatingText(null))
        assertNull(seerrRatingText(0f))
        assertNull(seerrRatingText(-2f))
    }

    @Test
    fun `seerr rating formats strictly positive averages as one decimal star`() {
        assertEquals("★ 7.5", seerrRatingText(7.5f))
        assertEquals("★ 0.5", seerrRatingText(0.5f))
    }

    // ── gridCellTextVisible ──────────────────────────────────────────────

    @Test
    fun `grid cell text stays when the dimension read failed`() {
        assertTrue(gridCellTextVisible(null))
    }

    @Test
    fun `grid cell text hides strictly below 200 height or 180 width`() {
        // Strict inequalities: exactly (180, 200) still shows text.
        assertFalse(gridCellTextVisible(WidgetDimensions(179, 300)))
        assertFalse(gridCellTextVisible(WidgetDimensions(300, 199)))
        assertTrue(gridCellTextVisible(WidgetDimensions(180, 200)))
        assertTrue(gridCellTextVisible(WidgetDimensions(300, 250)))
    }

    // ── continueWatchingRowVisibility ────────────────────────────────────

    @Test
    fun `continue watching rows show everything when the dimension read failed`() {
        assertEquals(
            ContinueWatchingRowVisibility(showProgress = true, showPoster = true),
            continueWatchingRowVisibility(null),
        )
    }

    @Test
    fun `continue watching progress hides below 240dp width`() {
        assertFalse(continueWatchingRowVisibility(239).showProgress)
        assertTrue(continueWatchingRowVisibility(240).showProgress)
    }

    @Test
    fun `continue watching poster hides below 180dp width`() {
        assertFalse(continueWatchingRowVisibility(179).showPoster)
        assertTrue(continueWatchingRowVisibility(180).showPoster)
    }

    @Test
    fun `a wide continue watching row shows progress and poster`() {
        val visibility = continueWatchingRowVisibility(300)
        assertTrue(visibility.showProgress)
        assertTrue(visibility.showPoster)
    }

    // ── continueWatchingRowSubtitle ──────────────────────────────────────

    @Test
    fun `continue watching subtitle is empty with nothing to show`() {
        assertEquals("", continueWatchingRowSubtitle(null, null, null))
        assertEquals("", continueWatchingRowSubtitle("  ", null, null))
    }

    @Test
    fun `continue watching subtitle joins series and padded season-episode tag`() {
        // Single-spaced middot — narrower than the recommendation grids'.
        assertEquals("Show · S2E03", continueWatchingRowSubtitle("Show", 2, 3))
        assertEquals("Show · S12E05", continueWatchingRowSubtitle("Show", 12, 5))
    }

    @Test
    fun `continue watching subtitle without an episode number renders the season alone`() {
        assertEquals("Show · S2", continueWatchingRowSubtitle("Show", 2, null))
        assertEquals("S2", continueWatchingRowSubtitle(null, 2, null))
    }

    @Test
    fun `continue watching subtitle pads only the episode number`() {
        assertEquals("Show · S3E07", continueWatchingRowSubtitle("Show", 3, 7))
    }

    // ── continueWatchingProgressPercent ──────────────────────────────────

    @Test
    fun `continue watching progress is null when ticks are unknown`() {
        assertNull(continueWatchingProgressPercent(null, null))
        assertNull(continueWatchingProgressPercent(1_000L, null))
        assertNull(continueWatchingProgressPercent(null, 500L))
        assertNull(continueWatchingProgressPercent(0L, 500L))
    }

    @Test
    fun `continue watching progress is the truncated percent of the runtime`() {
        assertEquals(50, continueWatchingProgressPercent(1_000L, 500L))
        assertEquals(99, continueWatchingProgressPercent(1_000L, 999L))
        assertEquals(33, continueWatchingProgressPercent(3L, 1L))
    }

    @Test
    fun `continue watching progress clamps to the bar ends`() {
        assertEquals(100, continueWatchingProgressPercent(1_000L, 1_500L))
        assertEquals(0, continueWatchingProgressPercent(1_000L, -100L))
    }

    // ── continueWatchingMinutesLeft ──────────────────────────────────────

    @Test
    fun `minutes left is null when the remainder is not a positive whole minute`() {
        assertNull(continueWatchingMinutesLeft(null, null))
        assertNull(continueWatchingMinutesLeft(60 * TICKS_PER_MINUTE, 60 * TICKS_PER_MINUTE))
        assertNull(continueWatchingMinutesLeft(60 * TICKS_PER_MINUTE, 59 * TICKS_PER_MINUTE + 30 * TICKS_PER_SECOND))
        assertNull(continueWatchingMinutesLeft(30 * TICKS_PER_MINUTE, 60 * TICKS_PER_MINUTE))
    }

    @Test
    fun `minutes left floors the remainder to whole minutes`() {
        assertEquals(30, continueWatchingMinutesLeft(60 * TICKS_PER_MINUTE, 30 * TICKS_PER_MINUTE))
        // A 60s remainder floors to exactly 1 minute.
        assertEquals(1, continueWatchingMinutesLeft(2 * TICKS_PER_MINUTE, TICKS_PER_MINUTE))
    }

    // ── recommendationGridHeaderVisibility ───────────────────────────────

    @Test
    fun `grid header hides entirely below 130dp height`() {
        assertEquals(
            RecommendationGridHeaderVisibility(showHeader = false, showSubtitle = false, showRefresh = false),
            recommendationGridHeaderVisibility(129),
        )
    }

    @Test
    fun `grid header keeps only the title between 130 and 179dp`() {
        assertEquals(
            RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = false, showRefresh = false),
            recommendationGridHeaderVisibility(130),
        )
        assertEquals(
            RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = false, showRefresh = false),
            recommendationGridHeaderVisibility(179),
        )
    }

    @Test
    fun `grid header shows everything from 180dp`() {
        assertEquals(
            RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = true, showRefresh = true),
            recommendationGridHeaderVisibility(180),
        )
        assertEquals(
            RecommendationGridHeaderVisibility(showHeader = true, showSubtitle = true, showRefresh = true),
            recommendationGridHeaderVisibility(250),
        )
    }

    // ── continueWatchingChromeVisibility ─────────────────────────────────

    @Test
    fun `continue watching header hides below 150dp height regardless of width`() {
        assertFalse(continueWatchingChromeVisibility(widthDp = 300, heightDp = 149).showHeader)
        assertTrue(continueWatchingChromeVisibility(widthDp = 300, heightDp = 150).showHeader)
        assertFalse(continueWatchingChromeVisibility(widthDp = 100, heightDp = 149).showHeader)
    }

    @Test
    fun `continue watching see-all hides below 220dp width regardless of height`() {
        assertFalse(continueWatchingChromeVisibility(widthDp = 219, heightDp = 250).showSeeAll)
        assertTrue(continueWatchingChromeVisibility(widthDp = 220, heightDp = 250).showSeeAll)
        assertFalse(continueWatchingChromeVisibility(widthDp = 219, heightDp = 100).showSeeAll)
    }

    @Test
    fun `the two continue watching chrome rules are independent`() {
        // Short but wide: header gone, see-all shown.
        val shortWide = continueWatchingChromeVisibility(widthDp = 300, heightDp = 100)
        assertFalse(shortWide.showHeader)
        assertTrue(shortWide.showSeeAll)
        // Tall but narrow: header shown, see-all gone.
        val tallNarrow = continueWatchingChromeVisibility(widthDp = 150, heightDp = 250)
        assertTrue(tallNarrow.showHeader)
        assertFalse(tallNarrow.showSeeAll)
    }

    // ── threshold constants ──────────────────────────────────────────────

    @Test
    fun `the threshold constants hold their documented values`() {
        assertEquals(220, WidgetLayoutThresholds.CONTINUE_WATCHING_DEFAULT_HEIGHT_DP)
        assertEquals(250, WidgetLayoutThresholds.RECOMMENDATION_GRID_DEFAULT_HEIGHT_DP)
        assertEquals(110, WidgetLayoutThresholds.NOW_PLAYING_DEFAULT_HEIGHT_DP)
        assertEquals(240, WidgetLayoutThresholds.CW_ROW_PROGRESS_HIDE_WIDTH_DP)
        assertEquals(180, WidgetLayoutThresholds.CW_ROW_POSTER_HIDE_WIDTH_DP)
        assertEquals(150, WidgetLayoutThresholds.CW_HEADER_HIDE_HEIGHT_DP)
        assertEquals(220, WidgetLayoutThresholds.CW_SEE_ALL_HIDE_WIDTH_DP)
        assertEquals(130, WidgetLayoutThresholds.GRID_HEADER_HIDE_HEIGHT_DP)
        assertEquals(180, WidgetLayoutThresholds.GRID_HEADER_COMPACT_HEIGHT_DP)
        assertEquals(200, WidgetLayoutThresholds.GRID_CELL_TEXT_HIDE_HEIGHT_DP)
        assertEquals(180, WidgetLayoutThresholds.GRID_CELL_TEXT_HIDE_WIDTH_DP)
    }

    private companion object {
        const val TICKS_PER_SECOND = 10_000_000L
        const val TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND
    }
}
