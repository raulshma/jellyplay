package com.raulshma.jellyplay.navigation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.ResolvedShortcut
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.ShortcutCatalogTarget
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.StringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the overflow menu's enter transition at the composition seam: hosts
 * [OverflowMenuItems] exactly the way the shell hosts it (the menu is ALWAYS
 * composed; only `visible` flips) and drives the enter transition with the
 * Compose test clock:
 * - Closed → no pill nodes composed.
 * - A few frames after open → the first-stagger pill's final-slot pixels
 *   DIFFER from its settled pixels (it is still mid fade/scale/slide).
 * - Fully advanced → pixels match the settled capture.
 *
 * Red if the enter transition snaps to its end state (a real regression:
 * e.g. the tweens replaced by snap() or the AnimatedVisibility removed),
 * green while the dock animation runs. The platform animator duration scale
 * (0.0 = "remove animations") is intentionally NOT emulated here — Robolectric
 * runs at scale 1.0, which is the contract this test pins.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverflowMenuItemsAnimationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(InternalResourceApi::class)
    private fun fakeEntries(): List<OverflowMenuItem> =
        (1..4).map { i ->
            val label = "LoopPill$i"
            OverflowMenuItem(
                key = "loop:$i",
                shortcut = ResolvedShortcut(
                    id = "default:loop$i",
                    target = ShortcutCatalogTarget.Screen(route = Route.Settings, key = "settings"),
                    icon = Tabler.Outline.Home,
                    // Never resolved (labelOverride wins) — the dummy StringResource
                    // instance is only there to satisfy the constructor.
                    labelRes = StringResource("loop:never", "", emptySet()),
                    labelOverride = label,
                ),
                onClick = {},
            )
        }

    /** Pixel color at the CENTER of a pill's final layout slot, sampled from the root capture. */
    private fun pillCenterPixel(label: String): Int {
        val bounds = composeTestRule
            .onAllNodesWithText(label, useUnmergedTree = true)[0]
            .fetchSemanticsNode()
            .boundsInRoot
        val root = composeTestRule.onRoot().captureToImage()
        val x = ((bounds.left + bounds.right) / 2).toInt().coerceIn(0, root.width - 1)
        val y = ((bounds.top + bounds.bottom) / 2).toInt().coerceIn(0, root.height - 1)
        return root.toPixelMap()[x, y].toArgb()
    }

    @Test
    fun `enter transition animates - pill pixels mid-flight differ from settled`() {
        val entries = fakeEntries()
        var visible by mutableStateOf(false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MaterialTheme {
                Box {
                    OverflowMenuItems(entries = entries, visible = visible)
                }
            }
        }
        composeTestRule.waitForIdle()

        // Closed: the pills are not composed at all.
        composeTestRule.onAllNodesWithText("LoopPill4", useUnmergedTree = true).assertCountEquals(0)

        visible = true
        // Robolectric's frame granularity needs small steps: a single
        // advanceTimeBy(48) can skip the frame that composes the pill.
        composeTestRule.mainClock.advanceTimeBy(16)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(32)
        composeTestRule.waitForIdle()

        // The pill exists in the tree from the first frame of the transition.
        composeTestRule.onAllNodesWithText("LoopPill4", useUnmergedTree = true).assertCountEquals(1)

        // LoopPill4 is the bottom pill (enter stagger 0 — animates first).
        val midFlight = pillCenterPixel("LoopPill4")

        // Let every stagger + tween finish.
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        val settled = pillCenterPixel("LoopPill4")

        assert(midFlight != settled) {
            "Pill pixels 48ms into the enter transition equal the settled pixels " +
                "(mid=$midFlight settled=$settled) — the enter animation snapped to its end state"
        }
    }
}
