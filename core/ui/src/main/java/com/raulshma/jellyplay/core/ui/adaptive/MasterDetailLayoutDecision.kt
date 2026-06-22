package com.raulshma.jellyplay.core.ui.adaptive

/**
 * Pure layout decision for the master-detail (two-pane) pattern.
 *
 * Extracted as a top-level function so it can be unit-tested without
 * instantiating composables, NavDisplay, or Hilt-injected dependencies.
 *
 * Rules:
 * 1. When the window is **not** [isExpanded] (Compact), always single-pane —
 *    the detail replaces the list (existing phone behaviour).
 * 2. When [isExpanded] **and** the top of the back stack is a detail route
 *    that was pushed on top of a list ([backStackSize] >= 2), show both panes
 *    side-by-side.
 * 3. When [isExpanded] but no detail is on top, show only the list pane
 *    (single-pane, full width).
 *
 * @param isExpanded `true` when the window size class is Medium or Expanded
 *   (tablet / foldable / desktop).
 * @param backStackSize The size of the current tab's back stack.
 * @param topRouteIsDetail `true` when the topmost entry on the back stack is a
 *   detail route (e.g. [com.raulshma.jellyplay.core.ui.navigation.Route.isDetail]).
 */
data class MasterDetailLayoutDecision(
    val showMasterPane: Boolean,
    val showDetailPane: Boolean,
    val isTwoPane: Boolean,
)

fun resolveMasterDetailLayout(
    isExpanded: Boolean,
    backStackSize: Int,
    topRouteIsDetail: Boolean,
): MasterDetailLayoutDecision {
    val shouldShowTwoPane = isExpanded && topRouteIsDetail && backStackSize >= 2
    return when {
        shouldShowTwoPane -> MasterDetailLayoutDecision(
            showMasterPane = true,
            showDetailPane = true,
            isTwoPane = true,
        )
        topRouteIsDetail -> MasterDetailLayoutDecision(
            showMasterPane = false,
            showDetailPane = true,
            isTwoPane = false,
        )
        else -> MasterDetailLayoutDecision(
            showMasterPane = true,
            showDetailPane = false,
            isTwoPane = false,
        )
    }
}
