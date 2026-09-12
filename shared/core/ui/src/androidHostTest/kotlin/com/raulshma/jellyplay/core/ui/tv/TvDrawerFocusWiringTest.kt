package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression harness for the TV drawer + library-screen focus wiring. Mirrors the
 * production modifier chains:
 *  - DrawerSheet: onFocusChanged(auto open/close) + focusGroup
 *  - Drawer content Box: focusRequester + onFocusChanged + focusGroup + tvFocusRestorer()
 *  - Header rows: focusGroup + tvFocusRestorer()
 *  - TvFocusableGrid: focusProperties{onEnter} + focusGroup + tvFocusRestorer(fallback) + focusRequester
 *
 * Compose aggregates FocusProperties from a focus target outward with the
 * OUTERMOST node winning each single-slot property (onEnter/onExit). A
 * focusRestorer above all content therefore clobbers every inner exit/enter
 * hook and injects restore-on-enter at every descendant boundary — these tests
 * pin the wiring against that class of regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp")
class TvDrawerFocusWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness(
        val contentRestorer: Boolean,
        val leftExitOpens: MutableState<Boolean> = mutableStateOf(false),
        val sheetAutoOpened: MutableState<Boolean> = mutableStateOf(false),
        val focusedTag: MutableState<String?> = mutableStateOf(null),
        val requesters: MutableMap<String, FocusRequester> = mutableMapOf(),
    ) {
        var focusManager: FocusManager? = null
    }

    private fun Modifier.tracked(tag: String, h: Harness): Modifier =
        onFocusChanged { if (it.isFocused || it.hasFocus) h.focusedTag.value = tag }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun Screen(h: Harness) {
        Row(Modifier.fillMaxSize()) {
            // ── Drawer sheet mimic (tv-material3 DrawerSheet + rail restorer) ─────
            val railSelected = remember { FocusRequester() }
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .onFocusChanged { h.sheetAutoOpened.value = it.hasFocus }
                    .tvFocusRestorer(railSelected)
                    .focusGroup(),
            ) {
                Column {
                    repeat(4) { i ->
                        val fr = remember { FocusRequester() }.also { h.requesters["rail$i"] = it }
                        val sel = if (i == 1) Modifier.focusRequester(railSelected) else Modifier
                        Text(
                            "rail$i",
                            sel.then(Modifier.focusRequester(fr)).tracked("rail$i", h).focusable(),
                        )
                    }
                }
            }

            // ── Drawer content Box mimic (TvNavigationDrawer) ────────────────────
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusGroup()
                    .let { if (h.contentRestorer) it.tvFocusRestorer() else it },
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Header row A (folder pills)
                    Row(Modifier.focusGroup().tvFocusRestorer()) {
                        listOf("rowA0", "rowA1").forEach { tag ->
                            val fr = remember { FocusRequester() }.also { h.requesters[tag] = it }
                            Text(tag, Modifier.focusRequester(fr).tracked(tag, h).focusable())
                        }
                    }
                    // Header row B (filter chips)
                    Row(Modifier.focusGroup().tvFocusRestorer()) {
                        listOf("rowB0", "rowB1").forEach { tag ->
                            val fr = remember { FocusRequester() }.also { h.requesters[tag] = it }
                            Text(tag, Modifier.focusRequester(fr).tracked(tag, h).focusable())
                        }
                    }
                    // Content-area Box mimic (PullToRefreshBox) carrying the
                    // openDrawerOnLeftExit hook, with the grid mimic inside using
                    // TvFocusableGrid's ordering: tvFocusRestorer(fallback).focusGroup().
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .focusProperties {
                                @Suppress("DEPRECATION")
                                exit = { direction ->
                                    if (direction == FocusDirection.Left) {
                                        h.leftExitOpens.value = true
                                    }
                                    FocusRequester.Default
                                }
                            },
                    ) {
                        val fallback = remember { FocusRequester() }
                        var focusedIndex by remember { mutableIntStateOf(0) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .tvFocusRestorer(fallback)
                                .focusGroup(),
                        ) {
                            Column {
                                repeat(8) { i ->
                                    Box(
                                        Modifier
                                            .then(
                                                if (i == focusedIndex) {
                                                    Modifier.focusRequester(fallback)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .onFocusChanged { if (it.isFocused) focusedIndex = i }
                                            .fillMaxWidth()
                                            .height(60.dp),
                                    ) {
                                        val fr = remember { FocusRequester() }
                                            .also { h.requesters["card$i"] = it }
                                        Text(
                                            "card$i",
                                            Modifier.focusRequester(fr)
                                                .tracked("card$i", h)
                                                .focusable(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setup(contentRestorer: Boolean): Harness {
        val h = Harness(contentRestorer = contentRestorer)
        composeRule.setContent {
            CompositionLocalProvider(LocalTvMode provides true) {
                h.focusManager = LocalFocusManager.current
                Screen(h)
            }
        }
        return h
    }

    private fun Harness.requestFocusOn(tag: String) {
        composeRule.runOnIdle { requesters.getValue(tag).requestFocus() }
    }

    private fun Harness.move(direction: FocusDirection) {
        composeRule.runOnIdle { checkNotNull(focusManager).moveFocus(direction) }
        composeRule.runOnIdle { println("FOCUS after $direction -> ${focusedTag.value}") }
    }

    // ── Bug 1: Down from the header must enter the grid, not bounce to the top row ─

    @Test
    fun `grid entry from header bounces to top header row with content restorer (bug repro)`() {
        val h = setup(contentRestorer = true)

        h.requestFocusOn("card0")
        h.move(FocusDirection.Up)
        composeRule.onNodeWithText("rowB0").assertIsFocused()
        h.move(FocusDirection.Up)
        composeRule.onNodeWithText("rowA0").assertIsFocused()
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("rowB0").assertIsFocused()
        // Down from row B should re-enter the grid — the content restorer instead
        // redirects to row A's saved child (the reported "resets to the top row").
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("rowA0").assertIsFocused()
    }

    @Test
    fun `grid entry from header returns into grid without content restorer (fixed)`() {
        val h = setup(contentRestorer = false)

        h.requestFocusOn("card0")
        h.move(FocusDirection.Up)
        composeRule.onNodeWithText("rowB0").assertIsFocused()
        h.move(FocusDirection.Up)
        composeRule.onNodeWithText("rowA0").assertIsFocused()
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("rowB0").assertIsFocused()
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("card0").assertIsFocused()
    }

    // ── Bug 2: D-pad Left from the grid's left edge must open the drawer ──────────

    @Test
    fun `left from grid does not fire exit hook with content restorer (bug repro)`() {
        val h = setup(contentRestorer = true)

        h.requestFocusOn("card3")
        h.move(FocusDirection.Left)
        composeRule.runOnIdle {
            check(!h.leftExitOpens.value) { "expected left-exit hook to be clobbered (bug repro)" }
        }
    }

    @Test
    fun `left from grid opens drawer without content restorer (fixed)`() {
        val h = setup(contentRestorer = false)

        h.requestFocusOn("card3")
        h.move(FocusDirection.Left)
        composeRule.runOnIdle {
            // Focus moved into the rail (geometric) and/or the exit hook fired —
            // either path must leave the drawer "open".
            check(h.sheetAutoOpened.value || h.leftExitOpens.value) {
                "Left neither moved focus into the rail (autoOpened=${h.sheetAutoOpened.value}) " +
                    "nor fired the exit hook (leftExit=${h.leftExitOpens.value})"
            }
        }
    }
}
