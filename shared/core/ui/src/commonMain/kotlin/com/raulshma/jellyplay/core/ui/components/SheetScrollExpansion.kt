package com.raulshma.jellyplay.core.ui.components

/**
 * Whether a partially expanded Material3 sheet on this platform can grow to
 * fully expanded by scrolling its content.
 *
 * True on Android: a touch drag feeds the nested-scroll chain with
 * [androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput], and
 * the sheet's ConsumeSwipeWithinBottomSheetBounds connection dispatches that
 * leftover to its anchors — the classic scroll-to-expand behavior.
 *
 * False on desktop/web: mouse-wheel (and trackpad) scroll reaches the sheet's
 * nested-scroll connection as `NestedScrollSource.SideEffect`, which the
 * connection explicitly ignores, so a sheet that opens PartlyExpanded can
 * never grow from scrolling (verified against JB material3 1.11.0-alpha07 —
 * see DesktopSheetWheelRobotTest). On those platforms [TvSafeSheet]
 * substitutes a `skipPartiallyExpanded = true` state so sheets open fully
 * expanded instead of getting stuck at half height.
 */
internal expect fun sheetExpandsFromContentScroll(): Boolean
