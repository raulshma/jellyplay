package com.raulshma.jellyplay.feature.player.video.subtitle

/**
 * Direction a cue should shift when it collides with an already-placed cue.
 */
internal enum class CueStackDirection {
    /** Bottom/middle-anchored cues stack upward when colliding. */
    UP,

    /** Top-anchored cues stack downward when colliding. */
    DOWN,
}

/**
 * Measured layout rectangle (in pixels) for one subtitle cue inside the
 * subtitle viewport.
 */
internal data class CueLayoutBox(
    val index: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val direction: CueStackDirection,
) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

/**
 * Pure bounding-box collision solver for simultaneously active subtitle cues.
 *
 * Media3's `CanvasSubtitleOutput` renders each `Cue` with an independent
 * `SubtitlePainter` and does not prevent multiple active cues from landing at
 * the exact same vertical position (e.g. overlapping SRT entries or multi-line
 * wrapped WebVTT cues). Given each cue's measured box, this solver shifts
 * colliding cues vertically so simultaneous dialogue stacks cleanly without
 * overlapping.
 */
internal object CueCollisionLayout {

    /**
     * Resolves vertical collisions among [boxes] within `[containerTop, containerBottom]`,
     * leaving at least [gapPx] between stacked boxes whose horizontal spans overlap.
     *
     * Returns a map from `box.index` to the new `top` pixel coordinate ONLY for
     * cues that actually moved; untouched cues are omitted.
     */
    fun resolveVerticalOffsets(
        boxes: List<CueLayoutBox>,
        containerTop: Int,
        containerBottom: Int,
        gapPx: Int = 0,
    ): Map<Int, Int> {
        if (boxes.size < 2 || containerBottom <= containerTop) return emptyMap()
        val gap = gapPx.coerceAtLeast(0)

        val placed = ArrayList<PlacedBox>(boxes.size)
        val movedTops = LinkedHashMap<Int, Int>()

        // Place UP-stacking (bottom/mid) cues from lowest bottom edge upward,
        // keeping earlier cues in the input list anchored first on ties.
        val upCues = boxes
            .filter { it.direction == CueStackDirection.UP }
            .sortedWith(compareByDescending<CueLayoutBox> { it.bottom }.thenBy { it.index })

        for (box in upCues) {
            val h = box.height
            var top = box.top
            var bottom = top + h
            var changed = true
            var guard = 0
            val maxPasses = placed.size + 1
            while (changed && guard < maxPasses) {
                changed = false
                guard++
                for (other in placed) {
                    if (horizontallyOverlaps(box.left, box.right, other.left, other.right) &&
                        verticallyOverlaps(top, bottom, other.top, other.bottom, gap)
                    ) {
                        val candidateTop = (other.top - gap - h).coerceAtLeast(containerTop)
                        if (candidateTop < top) {
                            top = candidateTop
                            bottom = top + h
                            changed = true
                        }
                    }
                }
            }
            placed += PlacedBox(box.left, top, box.right, bottom)
            if (top != box.top) {
                movedTops[box.index] = top
            }
        }

        // Place DOWN-stacking (top-anchored) cues from highest top edge downward.
        val downCues = boxes
            .filter { it.direction == CueStackDirection.DOWN }
            .sortedWith(compareBy<CueLayoutBox> { it.top }.thenBy { it.index })

        for (box in downCues) {
            val h = box.height
            var top = box.top
            var bottom = top + h
            var changed = true
            var guard = 0
            val maxPasses = placed.size + 1
            while (changed && guard < maxPasses) {
                changed = false
                guard++
                for (other in placed) {
                    if (horizontallyOverlaps(box.left, box.right, other.left, other.right) &&
                        verticallyOverlaps(top, bottom, other.top, other.bottom, gap)
                    ) {
                        val candidateTop = (other.bottom + gap).coerceAtMost((containerBottom - h).coerceAtLeast(containerTop))
                        if (candidateTop > top) {
                            top = candidateTop
                            bottom = top + h
                            changed = true
                        }
                    }
                }
            }
            placed += PlacedBox(box.left, top, box.right, bottom)
            if (top != box.top) {
                movedTops[box.index] = top
            }
        }

        return movedTops
    }

    private fun horizontallyOverlaps(l1: Int, r1: Int, l2: Int, r2: Int): Boolean =
        l1 < r2 && r1 > l2

    private fun verticallyOverlaps(t1: Int, b1: Int, t2: Int, b2: Int, gap: Int): Boolean =
        t1 < b2 + gap && b1 + gap > t2

    private data class PlacedBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
