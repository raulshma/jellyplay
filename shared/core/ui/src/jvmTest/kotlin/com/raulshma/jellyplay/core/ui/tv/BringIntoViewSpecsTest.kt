package com.raulshma.jellyplay.core.ui.tv

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the scroll-distance math of the two custom [BringIntoViewSpec]s used on
 * TV (the specs are pure objects — composition only *installs* them):
 *
 *  - [CenterBringIntoViewSpec] centers the target: the scroll delta is
 *    `offset - (containerSize - size) / 2`, i.e. the item's leading edge moves
 *    to the container midpoint minus half the item — and is independent of any
 *    extra reservation;
 *  - [ScrollToTopBringIntoViewSpec] lands the target `spaceAbovePx` below the
 *    top: the delta is `offset - spaceAbovePx`, independent of item and
 *    container size; the shared [DefaultScrollToTopBringIntoViewSpec] keeps
 *    the 100px default.
 */
class BringIntoViewSpecsTest {

    @Test
    fun centerSpec_smallItemInLargeContainer_positiveScroll() {
        // Item at offset 1000, size 100, container 800:
        // center of container is at 400; item should land at 350..450.
        assertEquals(
            1000f - (800f - 100f) / 2f,
            CenterBringIntoViewSpec.calculateScrollDistance(offset = 1000f, size = 100f, containerSize = 800f),
        )
    }

    @Test
    fun centerSpec_targetAboveViewport_negativeScroll() {
        // offset -400: item is above; scroll distance is negative.
        assertEquals(
            -400f - (600f - 200f) / 2f,
            CenterBringIntoViewSpec.calculateScrollDistance(-400f, 200f, 600f),
        )
    }

    @Test
    fun centerSpec_itemFillsContainer_scrollsLeadingEdgeToZeroSlot() {
        // (containerSize - size) / 2 is 0, so the delta is the raw offset.
        assertEquals(
            250f,
            CenterBringIntoViewSpec.calculateScrollDistance(offset = 250f, size = 500f, containerSize = 500f),
        )
    }

    @Test
    fun scrollToTopSpec_subtractsSpaceAboveOnly() {
        val spec = ScrollToTopBringIntoViewSpec(spaceAbovePx = 250f)

        assertEquals(1000f - 250f, spec.calculateScrollDistance(1000f, size = 40f, containerSize = 600f))
        assertEquals(-250f + 100f, spec.calculateScrollDistance(100f, size = 9000f, containerSize = 100f))
    }

    @Test
    fun scrollToTopSpec_isIndependentOfItemAndContainerSize() {
        val spec = ScrollToTopBringIntoViewSpec(spaceAbovePx = 100f)

        assertEquals(
            spec.calculateScrollDistance(500f, 1f, 1f),
            spec.calculateScrollDistance(500f, 9999f, 3f),
        )
    }

    @Test
    fun defaultSharedSpec_uses100PxReservation() {
        assertEquals(100f, DefaultScrollToTopBringIntoViewSpec.spaceAbovePx)
        assertEquals(
            500f - 100f,
            DefaultScrollToTopBringIntoViewSpec.calculateScrollDistance(500f, 50f, 400f),
        )
    }
}
