package com.raulshma.jellyplay.core.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDetailLayoutDecisionTest {

    // ── Compact (phone) ───────────────────────────────────────────────

    @Test
    fun compact_noDetail_showsListOnly() {
        val decision = resolveMasterDetailLayout(
            isExpanded = false,
            backStackSize = 1,
            topRouteIsDetail = false,
        )
        assertTrue(decision.showMasterPane)
        assertFalse(decision.showDetailPane)
        assertFalse(decision.isTwoPane)
    }

    @Test
    fun compact_withDetail_showsDetailOnly() {
        val decision = resolveMasterDetailLayout(
            isExpanded = false,
            backStackSize = 2,
            topRouteIsDetail = true,
        )
        assertFalse(decision.showMasterPane)
        assertTrue(decision.showDetailPane)
        assertFalse(decision.isTwoPane)
    }

    // ── Expanded (tablet) ─────────────────────────────────────────────

    @Test
    fun expanded_noDetail_showsListOnly() {
        val decision = resolveMasterDetailLayout(
            isExpanded = true,
            backStackSize = 1,
            topRouteIsDetail = false,
        )
        assertTrue(decision.showMasterPane)
        assertFalse(decision.showDetailPane)
        assertFalse(decision.isTwoPane)
    }

    @Test
    fun expanded_withDetail_showsBothPanes() {
        val decision = resolveMasterDetailLayout(
            isExpanded = true,
            backStackSize = 2,
            topRouteIsDetail = true,
        )
        assertTrue(decision.showMasterPane)
        assertTrue(decision.showDetailPane)
        assertTrue(decision.isTwoPane)
    }

    @Test
    fun expanded_detailOnTopButBackStackTooSmall_showsDetailOnly() {
        // Edge case: detail on top but back stack has only 1 item
        // (shouldn't happen in practice, but guard against it)
        val decision = resolveMasterDetailLayout(
            isExpanded = true,
            backStackSize = 1,
            topRouteIsDetail = true,
        )
        assertFalse(decision.isTwoPane)
        assertTrue(decision.showDetailPane)
    }

    @Test
    fun expanded_deepDetailNavigation_stillTwoPane() {
        // Multiple details pushed: [List, Detail1, Detail2]
        val decision = resolveMasterDetailLayout(
            isExpanded = true,
            backStackSize = 3,
            topRouteIsDetail = true,
        )
        assertTrue(decision.isTwoPane)
    }

    // ── Medium (foldable portrait) ───────────────────────────────────

    @Test
    fun medium_withDetail_showsBothPanes() {
        // Medium counts as "expanded" (isExpanded = true)
        val decision = resolveMasterDetailLayout(
            isExpanded = true,
            backStackSize = 2,
            topRouteIsDetail = true,
        )
        assertTrue(decision.isTwoPane)
    }

    // ── Exhaustive matrix ────────────────────────────────────────────

    @Test
    fun decisionMatrix_allCombinations() {
        // (isExpanded, backStackSize, topRouteIsDetail) -> (showMaster, showDetail, isTwoPane)
        data class Case(
            val isExpanded: Boolean,
            val backStackSize: Int,
            val topRouteIsDetail: Boolean,
            val expectedMaster: Boolean,
            val expectedDetail: Boolean,
            val expectedTwoPane: Boolean,
        )

        val cases = listOf(
            Case(false, 1, false, true, false, false),   // phone, list only
            Case(false, 2, true, false, true, false),    // phone, detail pushed
            Case(false, 3, true, false, true, false),    // phone, deep detail
            Case(true, 1, false, true, false, false),    // tablet, list only
            Case(true, 2, true, true, true, true),       // tablet, detail pushed
            Case(true, 3, true, true, true, true),       // tablet, deep detail
            Case(true, 1, true, false, true, false),     // tablet, detail but no list below
            Case(false, 0, false, true, false, false),   // phone, empty stack
        )

        cases.forEach { (exp, size, isDetail, master, detail, twoPane) ->
            val decision = resolveMasterDetailLayout(exp, size, isDetail)
            assertEquals(
                "isExpanded=$exp, size=$size, isDetail=$isDetail → showMasterPane",
                master,
                decision.showMasterPane,
            )
            assertEquals(
                "isExpanded=$exp, size=$size, isDetail=$isDetail → showDetailPane",
                detail,
                decision.showDetailPane,
            )
            assertEquals(
                "isExpanded=$exp, size=$size, isDetail=$isDetail → isTwoPane",
                twoPane,
                decision.isTwoPane,
            )
        }
    }
}
