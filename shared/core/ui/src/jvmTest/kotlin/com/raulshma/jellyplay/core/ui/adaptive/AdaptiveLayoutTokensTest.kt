package com.raulshma.jellyplay.core.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the pure responsive-layout token dispatch over [AdaptiveInfo]
 * (the `remember*` factories are composition-bound and untested):
 *
 *  - every `AdaptiveInfo.*` helper resolves TV first, then windowSizeClass in
 *    the order Expanded > Medium > Compact, with the exact dp constants of the
 *    Adaptive* tables;
 *  - `settingsColumns` is 2 for Expanded, landscape-gated 2/1 for Medium and
 *    always 1 for Compact;
 *  - `detailBodyMaxWidth` is [Dp.Infinity] for Compact and TV, finite
 *    otherwise;
 *  - [AdaptiveInfo.toLayoutTokens] fills every layout token from those same
 *    helpers (TV rows win over window class);
 *  - the [JellyPlayUiEnvironment] derived flags (`isTv`, `usesRemoteInput`)
 *    follow their device/input enums, and TV input mode always pairs with the
 *    Tv device class in the environment factory contract.
 */
class AdaptiveLayoutTokensTest {

    private fun info(windowSizeClass: WindowSizeClass, isLandscape: Boolean = false) =
        AdaptiveInfo(windowSizeClass, isLandscape)

    // ── gridCellSize ─────────────────────────────────────────────────────

    @Test
    fun gridCellSize_followsTvThenWindowClass() {
        assertEquals(220.dp, info(WindowSizeClass.Compact).gridCellSize(isTv = true))
        assertEquals(190.dp, info(WindowSizeClass.Expanded).gridCellSize())
        assertEquals(170.dp, info(WindowSizeClass.Medium).gridCellSize())
        assertEquals(150.dp, info(WindowSizeClass.Compact).gridCellSize())
    }

    @Test
    fun gridMinSize_followsTvThenWindowClass() {
        assertEquals(200.dp, info(WindowSizeClass.Compact).gridMinSize(isTv = true))
        assertEquals(170.dp, info(WindowSizeClass.Expanded).gridMinSize())
        assertEquals(155.dp, info(WindowSizeClass.Medium).gridMinSize())
        assertEquals(140.dp, info(WindowSizeClass.Compact).gridMinSize())
    }

    @Test
    fun rowCardWidth_followsTvThenWindowClass() {
        assertEquals(240.dp, info(WindowSizeClass.Compact).rowCardWidth(isTv = true))
        assertEquals(200.dp, info(WindowSizeClass.Expanded).rowCardWidth())
        assertEquals(180.dp, info(WindowSizeClass.Medium).rowCardWidth())
        assertEquals(160.dp, info(WindowSizeClass.Compact).rowCardWidth())
    }

    @Test
    fun contentPadding_followsTvThenWindowClass() {
        assertEquals(48.dp, info(WindowSizeClass.Compact).contentPadding(isTv = true))
        assertEquals(32.dp, info(WindowSizeClass.Expanded).contentPadding())
        assertEquals(24.dp, info(WindowSizeClass.Medium).contentPadding())
        assertEquals(16.dp, info(WindowSizeClass.Compact).contentPadding())
    }

    @Test
    fun itemSpacing_followsTvThenWindowClass() {
        assertEquals(20.dp, info(WindowSizeClass.Compact).itemSpacing(isTv = true))
        assertEquals(16.dp, info(WindowSizeClass.Expanded).itemSpacing())
        assertEquals(12.dp, info(WindowSizeClass.Medium).itemSpacing())
        assertEquals(8.dp, info(WindowSizeClass.Compact).itemSpacing())
    }

    @Test
    fun bottomPadding_followsTvThenWindowClass() {
        // Note the deliberate inversion in the table: TV (80) is not the
        // largest value — Expanded (80) ties TV and Compact (100) is largest.
        assertEquals(80.dp, info(WindowSizeClass.Compact).bottomPadding(isTv = true))
        assertEquals(80.dp, info(WindowSizeClass.Expanded).bottomPadding())
        assertEquals(90.dp, info(WindowSizeClass.Medium).bottomPadding())
        assertEquals(100.dp, info(WindowSizeClass.Compact).bottomPadding())
    }

    // ── settingsColumns ──────────────────────────────────────────────────

    @Test
    fun settingsColumns_expandedIsAlwaysTwo() {
        assertEquals(2, info(WindowSizeClass.Expanded, isLandscape = false).settingsColumns())
        assertEquals(2, info(WindowSizeClass.Expanded, isLandscape = true).settingsColumns())
    }

    @Test
    fun settingsColumns_mediumIsLandscapeGated() {
        assertEquals(2, info(WindowSizeClass.Medium, isLandscape = true).settingsColumns())
        assertEquals(1, info(WindowSizeClass.Medium, isLandscape = false).settingsColumns())
    }

    @Test
    fun settingsColumns_compactIsAlwaysOne() {
        assertEquals(1, info(WindowSizeClass.Compact, isLandscape = true).settingsColumns())
        assertEquals(1, info(WindowSizeClass.Compact, isLandscape = false).settingsColumns())
    }

    // ── detailBodyMaxWidth ───────────────────────────────────────────────

    @Test
    fun detailBodyMaxWidth_infinityForCompactAndTv() {
        assertEquals(Dp.Infinity, info(WindowSizeClass.Compact).detailBodyMaxWidth())
        assertEquals(Dp.Infinity, info(WindowSizeClass.Compact).detailBodyMaxWidth(isTv = true))
    }

    @Test
    fun detailBodyMaxWidth_finiteForMediumAndExpanded() {
        assertEquals(680.dp, info(WindowSizeClass.Medium).detailBodyMaxWidth())
        assertEquals(840.dp, info(WindowSizeClass.Expanded).detailBodyMaxWidth())
        // TV wins over the Expanded window class.
        assertEquals(Dp.Infinity, info(WindowSizeClass.Expanded).detailBodyMaxWidth(isTv = true))
    }

    // ── toLayoutTokens ───────────────────────────────────────────────────

    @Test
    fun toLayoutTokens_compactPhoneTable_matchesCompactConstants() {
        val tokens = info(WindowSizeClass.Compact).toLayoutTokens(isTv = false)

        assertEquals(150.dp, tokens.gridCellSize)
        assertEquals(140.dp, tokens.gridMinSize)
        assertEquals(160.dp, tokens.rowCardWidth)
        assertEquals(16.dp, tokens.contentPadding)
        assertEquals(8.dp, tokens.itemSpacing)
        assertEquals(100.dp, tokens.bottomPadding)
        assertEquals(Dp.Infinity, tokens.detailBodyMaxWidth)
    }

    @Test
    fun toLayoutTokens_tvBeatsWindowClass() {
        val tokens = info(WindowSizeClass.Expanded).toLayoutTokens(isTv = true)

        assertEquals(220.dp, tokens.gridCellSize)
        assertEquals(200.dp, tokens.gridMinSize)
        assertEquals(240.dp, tokens.rowCardWidth)
        assertEquals(48.dp, tokens.contentPadding)
        assertEquals(20.dp, tokens.itemSpacing)
        assertEquals(80.dp, tokens.bottomPadding)
        assertEquals(Dp.Infinity, tokens.detailBodyMaxWidth)
    }

    // ── JellyPlayUiEnvironment derived flags ─────────────────────────────

    @Test
    fun uiEnvironment_derivedFlagsFollowEnums() {
        val tv = JellyPlayUiEnvironment(
            deviceClass = DeviceClass.Tv,
            inputMode = InputMode.Remote,
            layout = info(WindowSizeClass.Expanded).toLayoutTokens(isTv = true),
            focus = JellyPlayFocusTokens(true, 1.08f, 1.04f, 2.dp, 16.dp),
        )
        assertTrue(tv.isTv)
        assertTrue(tv.usesRemoteInput)

        val phone = JellyPlayUiEnvironment(
            deviceClass = DeviceClass.Phone,
            inputMode = InputMode.Touch,
            layout = info(WindowSizeClass.Compact).toLayoutTokens(isTv = false),
            focus = JellyPlayFocusTokens(true, 1f, 1f, 1.5.dp, 0.dp),
        )
        assertFalse(phone.isTv)
        assertFalse(phone.usesRemoteInput)
    }

    @Test
    fun uiEnvironment_layoutTokenFields_flowThroughVerbatim() {
        val layout = JellyPlayLayoutTokens(
            gridCellSize = 1.dp,
            gridMinSize = 2.dp,
            rowCardWidth = 3.dp,
            contentPadding = 4.dp,
            itemSpacing = 5.dp,
            bottomPadding = 6.dp,
            detailBodyMaxWidth = Dp.Infinity,
        )
        val env = JellyPlayUiEnvironment(
            deviceClass = DeviceClass.Tablet,
            inputMode = InputMode.Touch,
            layout = layout,
            focus = JellyPlayFocusTokens(false, 1f, 1f, 0.dp, 0.dp),
        )

        assertEquals(layout, env.layout)
        assertEquals(DeviceClass.Tablet, env.deviceClass)
        assertEquals(InputMode.Touch, env.inputMode)
    }
}
