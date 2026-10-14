package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.model.ItemQualityBadge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the pure decision functions behind the card-surface features:
 *  - [spoilerSafeCardState] (the H15 anti-spoiler suppression policy, consumed
 *    by the scaffold's progress gate, the footers' time-left leg, the poster
 *    overlays' watched corner and the wide card's title) — the HomeBackdrop
 *    decision-test shape;
 *  - [qualityBadgesToRender] (H11's admission/order/cap fold for the quality
 *    chip row).
 *
 * No Compose needed — plain functions over data classes.
 */
class CardDisplayPreferencesDecisionTest {

    private fun prefs(
        antiSpoilerHome: Boolean = false,
        showWatchedCheckmark: Boolean = true,
        showUnwatchedBadge: Boolean = true,
        showQualityBadges: Boolean = true,
        showNewBadges: Boolean = true,
    ) = CardDisplayPreferences(
        showUnwatchedBadge = showUnwatchedBadge,
        showWatchedCheckmark = showWatchedCheckmark,
        hideWatchedItems = false,
        showQualityBadges = showQualityBadges,
        showNewBadges = showNewBadges,
        antiSpoilerHome = antiSpoilerHome,
    )

    // -- spoilerSafeCardState -------------------------------------------------

    @Test
    fun `everything renders when anti-spoiler is off`() {
        val state = spoilerSafeCardState(prefs(antiSpoilerHome = false))

        assertTrue(state.showProgress)
        assertTrue(state.showTimeLeft)
        assertTrue(state.showWatchedBadges)
        assertTrue(state.showEpisodeTitles)
    }

    @Test
    fun `every spoiler surface is suppressed when anti-spoiler is on`() {
        val state = spoilerSafeCardState(prefs(antiSpoilerHome = true))

        assertFalse(state.showProgress, "progress bars are watch-state spoilers")
        assertFalse(state.showTimeLeft, "the 'Xm left' footer is a watch-state spoiler")
        assertFalse(state.showWatchedBadges, "watched checkmark + unwatched count are spoilers")
        assertFalse(state.showEpisodeTitles, "episode titles on resume/next-up cards are spoilers")
    }

    @Test
    fun `anti-spoiler overrides the individual watched-badge prefs`() {
        // The documented precedence: the individual flags stay as configured
        // for their own semantics, but the checkmark/unwatched badges are
        // force-hidden when the mode is on — the fold's answer is
        // showWatchedBadges = false regardless of them.
        val state = spoilerSafeCardState(
            prefs(antiSpoilerHome = true, showWatchedCheckmark = true, showUnwatchedBadge = true),
        )

        assertFalse(state.showWatchedBadges)
    }

    // -- qualityBadgesToRender ------------------------------------------------

    @Test
    fun `no badges render for the not-fetched null`() {
        assertTrue(qualityBadgesToRender(null).isEmpty())
    }

    @Test
    fun `HD and SD are not badge-worthy`() {
        assertTrue(qualityBadgesToRender(listOf(ItemQualityBadge.HD)).isEmpty())
        assertTrue(qualityBadgesToRender(listOf(ItemQualityBadge.SD)).isEmpty())
    }

    @Test
    fun `badges render in the 4K DV HDR Atmos ladder regardless of derivation order`() {
        // The full ladder exceeds QUALITY_BADGE_CAP — lift the cap here so this
        // test isolates the ORDERING property (the cap is pinned separately).
        val badges = qualityBadgesToRender(
            listOf(ItemQualityBadge.ATMOS, ItemQualityBadge.HDR, ItemQualityBadge.FOUR_K, ItemQualityBadge.DOLBY_VISION),
            cap = 4,
        )

        assertEquals(
            listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.DOLBY_VISION, ItemQualityBadge.HDR, ItemQualityBadge.ATMOS),
            badges,
        )
    }

    @Test
    fun `the render caps at three chips`() {
        val badges = qualityBadgesToRender(
            listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.DOLBY_VISION, ItemQualityBadge.HDR, ItemQualityBadge.ATMOS),
        )

        // 4K > DV > HDR > Atmos: the cap keeps the ladder's head.
        assertEquals(
            listOf(ItemQualityBadge.FOUR_K, ItemQualityBadge.DOLBY_VISION, ItemQualityBadge.HDR),
            badges,
        )
        assertTrue(badges.size <= 3)
    }

    @Test
    fun `a subset of badges keeps its ladder order`() {
        assertEquals(
            listOf(ItemQualityBadge.HDR, ItemQualityBadge.ATMOS),
            qualityBadgesToRender(listOf(ItemQualityBadge.ATMOS, ItemQualityBadge.HDR)),
        )
    }
}
