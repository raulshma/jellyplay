package com.raulshma.jellyplay.core.ui.animation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SwipeActionPolicyTest {

    @Test
    fun revealDistanceIsAFractionOfRowWidth() {
        assertEquals(350f, SwipeActionPolicy.revealDistance(1000f), absoluteTolerance = 0.01f)
        assertEquals(0f, SwipeActionPolicy.revealDistance(0f))
    }

    @Test
    fun positionalThresholdIsAFractionOfTravel() {
        assertEquals(110f, SwipeActionPolicy.positionalThreshold(200f), absoluteTolerance = 0.01f)
    }

    @Test
    fun thresholdExceedsHalfTheRevealDistanceSoAccidentalDriftDoesNotCommit() {
        // A drag that barely reveals the background (below the reveal distance)
        // must never cross the commit threshold measured over the same travel.
        val rowWidth = 1080f
        val reveal = SwipeActionPolicy.revealDistance(rowWidth)
        val thresholdAtReveal = SwipeActionPolicy.positionalThreshold(reveal)
        assertTrue(
            thresholdAtReveal > reveal * 0.5f,
            "commit threshold ($thresholdAtReveal) should sit past half the reveal distance ($reveal)",
        )
    }
}
