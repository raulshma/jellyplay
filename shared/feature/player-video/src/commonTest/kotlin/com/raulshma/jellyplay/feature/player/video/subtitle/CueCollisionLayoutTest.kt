package com.raulshma.jellyplay.feature.player.video.subtitle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CueCollisionLayoutTest {

    @Test
    fun resolveVerticalOffsets_twoOverlappingBottomCues_stacksSecondUpward() {
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 200, top = 900, right = 800, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 1, left = 220, top = 900, right = 780, bottom = 950, direction = CueStackDirection.UP),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 8,
        )

        assertEquals(mapOf(1 to 842), moved)
    }

    @Test
    fun resolveVerticalOffsets_threeSimultaneousCues_stacksAllWithoutOverlap() {
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 200, top = 900, right = 800, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 1, left = 200, top = 900, right = 800, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 2, left = 200, top = 900, right = 800, bottom = 950, direction = CueStackDirection.UP),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 10,
        )

        assertEquals(mapOf(1 to 840, 2 to 780), moved)
    }

    @Test
    fun resolveVerticalOffsets_disjointHorizontalBoxes_doNotMove() {
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 40, top = 900, right = 400, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 1, left = 600, top = 900, right = 960, bottom = 950, direction = CueStackDirection.UP),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 8,
        )

        assertTrue(moved.isEmpty())
    }

    @Test
    fun resolveVerticalOffsets_topAnchoredCues_stackDownward() {
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 200, top = 40, right = 800, bottom = 90, direction = CueStackDirection.DOWN),
            CueLayoutBox(index = 1, left = 200, top = 40, right = 800, bottom = 90, direction = CueStackDirection.DOWN),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 6,
        )

        assertEquals(mapOf(1 to 96), moved)
    }

    @Test
    fun resolveVerticalOffsets_wrappedLowerCuePartiallyOverlapsUpper_pushesUpperClear() {
        // Mirrors WebVTT where cue 0 wrapped to 100px tall (top=850..950) and
        // cue 1 stepped up by only one 50px line (top=850..900).
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 200, top = 850, right = 800, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 1, left = 250, top = 850, right = 750, bottom = 900, direction = CueStackDirection.UP),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 8,
        )

        assertEquals(mapOf(1 to 792), moved)
    }

    @Test
    fun resolveVerticalOffsets_touchingBoxesWithGap_separatesByGap() {
        val boxes = listOf(
            CueLayoutBox(index = 0, left = 200, top = 900, right = 800, bottom = 950, direction = CueStackDirection.UP),
            CueLayoutBox(index = 1, left = 200, top = 850, right = 800, bottom = 900, direction = CueStackDirection.UP),
        )

        val moved = CueCollisionLayout.resolveVerticalOffsets(
            boxes = boxes,
            containerTop = 0,
            containerBottom = 1000,
            gapPx = 8,
        )

        assertEquals(mapOf(1 to 842), moved)
    }
}
