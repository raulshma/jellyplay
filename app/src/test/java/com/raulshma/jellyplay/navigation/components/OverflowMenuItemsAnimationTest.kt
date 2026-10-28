package com.raulshma.jellyplay.navigation.components

import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the overflow menu's macOS dock item maximizing and minimizing animation transitions:
 * - Enter stagger order starts from the 3-dot icon (bottom-end on phone) and flows bottom-to-top.
 * - Exit stagger order collapses top-to-bottom back down into the 3-dot icon.
 * - Tablet order flows top-down from the NavigationRail anchor.
 * - Transform origins anchor to the 3-dot icon (bottom-end on phone, top-start on tablet).
 * - Scale and duration constants match the macOS dock pop & fluid retreat specs.
 */
class OverflowMenuItemsAnimationTest {

    @Test
    fun `phone enter stagger runs bottom to top starting from 3-dot icon`() {
        val total = 5
        // index 4 is the bottom item, closest to the 3-dot icon
        assertEquals(0, overflowEnterStaggerIndex(index = 4, totalItems = total, alignToStart = false))
        assertEquals(1, overflowEnterStaggerIndex(index = 3, totalItems = total, alignToStart = false))
        assertEquals(2, overflowEnterStaggerIndex(index = 2, totalItems = total, alignToStart = false))
        assertEquals(3, overflowEnterStaggerIndex(index = 1, totalItems = total, alignToStart = false))
        // index 0 is the top item, furthest from the 3-dot icon
        assertEquals(4, overflowEnterStaggerIndex(index = 0, totalItems = total, alignToStart = false))
    }

    @Test
    fun `phone exit stagger runs top to bottom collapsing back into 3-dot icon`() {
        val total = 5
        // index 0 is the top item, collapses first
        assertEquals(0, overflowExitStaggerIndex(index = 0, totalItems = total, alignToStart = false))
        assertEquals(1, overflowExitStaggerIndex(index = 1, totalItems = total, alignToStart = false))
        assertEquals(2, overflowExitStaggerIndex(index = 2, totalItems = total, alignToStart = false))
        assertEquals(3, overflowExitStaggerIndex(index = 3, totalItems = total, alignToStart = false))
        // index 4 is the bottom item, collapses last into the 3-dot icon
        assertEquals(4, overflowExitStaggerIndex(index = 4, totalItems = total, alignToStart = false))
    }

    @Test
    fun `tablet enter and exit stagger respects rail top-start anchor`() {
        val total = 4
        // Tablet flows top-down on enter
        assertEquals(0, overflowEnterStaggerIndex(index = 0, totalItems = total, alignToStart = true))
        assertEquals(3, overflowEnterStaggerIndex(index = 3, totalItems = total, alignToStart = true))

        // Tablet collapses bottom-up toward rail top anchor on exit
        assertEquals(0, overflowExitStaggerIndex(index = 3, totalItems = total, alignToStart = true))
        assertEquals(3, overflowExitStaggerIndex(index = 0, totalItems = total, alignToStart = true))
    }

    @Test
    fun `stagger handles single item and empty list boundary cases safely`() {
        assertEquals(0, overflowEnterStaggerIndex(index = 0, totalItems = 1, alignToStart = false))
        assertEquals(0, overflowExitStaggerIndex(index = 0, totalItems = 1, alignToStart = false))
        assertEquals(0, overflowEnterStaggerIndex(index = 0, totalItems = 0, alignToStart = false))
        assertEquals(0, overflowExitStaggerIndex(index = 0, totalItems = 0, alignToStart = false))
    }

    @Test
    fun `transform origin anchors to bottom-end for phone and top-start for tablet`() {
        val phoneOrigin = overflowMenuTransformOrigin(alignToStart = false)
        assertEquals(1f, phoneOrigin.pivotFractionX)
        assertEquals(1f, phoneOrigin.pivotFractionY)

        val tabletOrigin = overflowMenuTransformOrigin(alignToStart = true)
        assertEquals(0f, tabletOrigin.pivotFractionX)
        assertEquals(0f, tabletOrigin.pivotFractionY)
    }

    @Test
    fun `phone item offsets land on the exact 3-dot anchor from every slot`() {
        val total = 5
        val itemHeight = 100
        val step = (itemHeight * OVERFLOW_MENU_ANCHOR_STEP_FACTOR).roundToInt() // 115
        val expectedAnchorY = (total - 1) * step + itemHeight // 460 + 100 = 560

        for (i in 0 until total) {
            val offset = overflowItemAnchorOffsetY(index = i, totalItems = total, alignToStart = false, itemHeight = itemHeight)

            // The same fold serves the enter initial offset and the exit target
            // offset, so maximize-start and minimize-end are the same anchor.
            val itemSlotY = i * step
            assertEquals("Item $i must start/end at the exact 3-dot anchor", expectedAnchorY, itemSlotY + offset)
        }
    }

    @Test
    fun `tablet item offsets land on the exact rail toggle anchor from every slot`() {
        val total = 4
        val itemHeight = 80
        val step = (itemHeight * OVERFLOW_MENU_ANCHOR_STEP_FACTOR).roundToInt() // 92
        val expectedAnchorY = -itemHeight // -80

        for (i in 0 until total) {
            val offset = overflowItemAnchorOffsetY(index = i, totalItems = total, alignToStart = true, itemHeight = itemHeight)

            val itemSlotY = i * step
            assertEquals("Item $i must start/end at the rail toggle anchor", expectedAnchorY, itemSlotY + offset)
        }
    }

    @Test
    fun `offset calculations handle boundary conditions safely`() {
        assertEquals(0, overflowItemAnchorOffsetY(index = 0, totalItems = 0, alignToStart = false, itemHeight = 100))
        assertEquals(0, overflowItemAnchorOffsetY(index = 0, totalItems = 5, alignToStart = false, itemHeight = 0))
    }

    @Test
    fun `total exit duration accounts for cascading item collapse delays`() {
        assertEquals(OVERFLOW_MENU_EXIT_DURATION_MS, overflowMenuTotalExitDurationMs(0))
        assertEquals(OVERFLOW_MENU_EXIT_DURATION_MS, overflowMenuTotalExitDurationMs(1))
        assertEquals(280, overflowMenuTotalExitDurationMs(5)) // (5 - 1) * 25 + 180 = 280
        assertEquals(330, overflowMenuTotalExitDurationMs(7)) // (7 - 1) * 25 + 180 = 330
    }

    @Test
    fun `animation constants provide macOS dock scale expansion and timing`() {
        assertTrue(OVERFLOW_MENU_INITIAL_SCALE < 0.5f)
        assertTrue(OVERFLOW_MENU_TARGET_EXIT_SCALE < 0.5f)
        assertTrue(OVERFLOW_MENU_ENTER_DURATION_MS in 200..400)
        assertTrue(OVERFLOW_MENU_EXIT_DURATION_MS in 100..300)
        assertTrue(OVERFLOW_MENU_ENTER_DELAY_STEP_MS in 20..60)
        assertTrue(OVERFLOW_MENU_EXIT_DELAY_STEP_MS in 15..50)
    }
}
