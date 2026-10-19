package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
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
 * Pins the shared TV focus cursor's fallback contract ([TvFocusCursor] —
 * [TvItemFocusFallback] + the [rememberTvFocusCursor] ladder) through the REAL
 * [TvFocusableColumn] (the same contract serves the grid and item row via
 * their [TvFocusLayout] parameterization):
 *
 *  - the one-shot initial grab lands focus on the tracked item once data
 *    arrives after first composition;
 *  - the cursor tracks focus moves (the leaf's onFocused ladder) and group
 *    re-entry lands on the TRACKED item, not the first one — the fallback
 *    requester is attached to the cursor's item;
 *  - a [TvFocusableColumn]'s refreshGeneration bump resets the cursor and
 *    re-grabs the first item (the reload-orphan fix).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp")
class TvFocusCursorContractTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness(
        val itemCount: MutableState<Int> = mutableStateOf(0),
        val refreshGeneration: MutableState<Int> = mutableStateOf(0),
        val focusedTag: MutableState<String?> = mutableStateOf(null),
        val trackedIndexes: MutableState<List<Int>> = mutableStateOf(emptyList()),
    ) {
        var focusManager: FocusManager? = null
        var outsideRequester: FocusRequester? = null
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun Screen(h: Harness) {
        val outside = FocusRequester().also { h.outsideRequester = it }
        h.focusManager = LocalFocusManager.current
        Column(Modifier.fillMaxSize()) {
            Text(
                "outside",
                Modifier
                    .focusRequester(outside)
                    .onFocusChanged { if (it.isFocused) h.focusedTag.value = "outside" }
                    .focusable(),
            )
            TvFocusableColumn(
                items = List(h.itemCount.value) { it },
                key = { it },
                requestInitialFocus = true,
                refreshGeneration = h.refreshGeneration.value,
                onFocusedIndexChange = { index ->
                    h.trackedIndexes.value = h.trackedIndexes.value + index
                },
                modifier = Modifier.weight(1f),
            ) { index, _, itemModifier ->
                Text(
                    "item$index",
                    itemModifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .onFocusChanged { if (it.isFocused) h.focusedTag.value = "item$index" }
                        .focusable(),
                )
            }
        }
    }

    private fun setup(initialItemCount: Int): Harness {
        val h = Harness(itemCount = mutableStateOf(initialItemCount))
        composeRule.setContent {
            CompositionLocalProvider(LocalTvMode provides true) {
                Screen(h)
            }
        }
        return h
    }

    private fun Harness.move(direction: FocusDirection) {
        composeRule.runOnIdle { checkNotNull(focusManager).moveFocus(direction) }
        composeRule.waitForIdle()
    }

    @Test
    fun `initial grab lands on the first item once data arrives`() {
        val h = setup(initialItemCount = 0)

        composeRule.runOnIdle { h.itemCount.value = 4 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("item0").assertIsFocused()
    }

    @Test
    fun `cursor tracks moves and re-entry lands on the tracked item`() {
        val h = setup(initialItemCount = 4)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("item0").assertIsFocused()

        h.move(FocusDirection.Down)

        composeRule.onNodeWithText("item1").assertIsFocused()

        // Focus leaves the column for the node above it…
        composeRule.runOnIdle { checkNotNull(h.outsideRequester).requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("outside").assertIsFocused()

        // …and re-entering must land on the TRACKED item (the cursor's fallback
        // attachment), not the column's first item.
        h.move(FocusDirection.Down)

        composeRule.onNodeWithText("item1").assertIsFocused()
    }

    @Test
    fun `onFocusedIndexChange follows the cursor through the initial grab and moves`() {
        val h = setup(initialItemCount = 4)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("item0").assertIsFocused()
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("item1").assertIsFocused()

        org.junit.Assert.assertEquals(listOf(0, 1), h.trackedIndexes.value)
    }

    @Test
    fun `refresh generation resets the cursor and regrabs the first item`() {
        val h = setup(initialItemCount = 4)
        composeRule.waitForIdle()
        h.move(FocusDirection.Down)
        composeRule.onNodeWithText("item1").assertIsFocused()

        composeRule.runOnIdle { h.refreshGeneration.value = 1 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("item0").assertIsFocused()
    }
}
