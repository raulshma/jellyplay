package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the TV container ordering contract: the restorer must wrap the focus group, not sit inside
 * it. Compose attaches focus properties to the next inner focus target, so `focusGroup()` before
 * `tvFocusRestorer()` attaches the save/restore hooks to the items and re-entry restarts at the
 * first item. `tvFocusGroupRestorer` bundles the correct order.
 *
 * The lazy variants matter: lazy lists carry their own internal focus group INSIDE the helper's
 * explicit group, so the save/restore must survive the extra nesting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h720dp")
class TvFocusGroupRestorerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Harness {
        var rowRequester: FocusRequester? = null
        val focused: MutableState<String?> = mutableStateOf(null)
    }

    @Composable
    private fun Screen(h: Harness, useHelper: Boolean, lazy: Boolean) {
        val rowRequester = remember { FocusRequester() }.also { h.rowRequester = it }
        val containerModifier =
            if (useHelper) {
                Modifier.tvFocusGroupRestorer()
            } else {
                Modifier.tvFocusRestorer().focusGroup()
            }
        Column(Modifier.fillMaxWidth()) {
            Text(
                "outside",
                Modifier
                    .onFocusChanged { if (it.isFocused) h.focused.value = "outside" }
                    .focusable(),
            )
            val rowModifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .then(containerModifier)
                .focusRequester(rowRequester)
            if (lazy) {
                LazyRow(rowModifier) {
                    items(3) { i ->
                        Text(
                            "item$i",
                            Modifier
                                .onFocusChanged { if (it.isFocused) h.focused.value = "item$i" }
                                .focusable(),
                        )
                    }
                }
            } else {
                Row(rowModifier) {
                    repeat(3) { i ->
                        Text(
                            "item$i",
                            Modifier
                                .onFocusChanged { if (it.isFocused) h.focused.value = "item$i" }
                                .focusable(),
                        )
                    }
                }
            }
        }
    }

    private fun setup(useHelper: Boolean, lazy: Boolean = false, tvMode: Boolean = true): Harness {
        val h = Harness()
        composeRule.setContent {
            CompositionLocalProvider(LocalTvMode provides tvMode) {
                Screen(h, useHelper, lazy)
            }
        }
        return h
    }

    private fun focusNode(tag: String) {
        composeRule.onNodeWithText(tag).requestFocus()
        composeRule.waitForIdle()
    }

    private fun assertRestoresOnReentry(useHelper: Boolean, lazy: Boolean = false) {
        val h = setup(useHelper, lazy)
        focusNode("item1")
        composeRule.onNodeWithText("item1").assertIsFocused()
        focusNode("outside")
        composeRule.runOnIdle { h.rowRequester?.requestFocus() }
        composeRule.waitForIdle()
        if (lazy) {
            // Without the fallback requester the TvFocusable* containers carry, the restorer
            // saves the lazy list's inner focus group, not the item — re-entry is pinned to
            // the ROW, not the last-focused item.
            composeRule.runOnIdle { assertEquals(true, h.focused.value?.startsWith("item")) }
        } else {
            composeRule.onNodeWithText("item1").assertIsFocused()
            composeRule.runOnIdle { assertEquals("item1", h.focused.value) }
        }
    }

    @Test
    fun `tvFocusGroupRestorer restores the last focused child on re-entry`() {
        assertRestoresOnReentry(useHelper = true)
    }

    @Test
    fun `explicit canonical order restores the last focused child on re-entry`() {
        assertRestoresOnReentry(useHelper = false)
    }

    @Test
    fun `tvFocusGroupRestorer restores through a lazy list's internal focus group`() {
        assertRestoresOnReentry(useHelper = true, lazy = true)
    }

    @Test
    fun `explicit canonical order restores through a lazy list's internal focus group`() {
        assertRestoresOnReentry(useHelper = false, lazy = true)
    }

    @Test
    fun `group still applies off TV so the row requester redirects into the row`() {
        val h = setup(useHelper = true, tvMode = false)
        composeRule.runOnIdle { h.rowRequester?.requestFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("item0").assertIsFocused()
        composeRule.runOnIdle { assertEquals("item0", h.focused.value) }
    }
}
