package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.HomeSectionType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class HomeSectionConfigSheetTest {

    @Test
    fun moveUpAndDown_boundsCalculations_atFirstPosition() {
        val position = 0
        val total = 5

        val canMoveUp = position > 0
        val canMoveDown = position < total - 1
        val positionLabel = "Position ${position + 1} of $total"

        assertFalse(canMoveUp)
        assertTrue(canMoveDown)
        assertEquals("Position 1 of 5", positionLabel)
    }

    @Test
    fun moveUpAndDown_boundsCalculations_atMiddlePosition() {
        val position = 2
        val total = 5

        val canMoveUp = position > 0
        val canMoveDown = position < total - 1
        val positionLabel = "Position ${position + 1} of $total"

        assertTrue(canMoveUp)
        assertTrue(canMoveDown)
        assertEquals("Position 3 of 5", positionLabel)
    }

    @Test
    fun moveUpAndDown_boundsCalculations_atLastPosition() {
        val position = 4
        val total = 5

        val canMoveUp = position > 0
        val canMoveDown = position < total - 1
        val positionLabel = "Position ${position + 1} of $total"

        assertTrue(canMoveUp)
        assertFalse(canMoveDown)
        assertEquals("Position 5 of 5", positionLabel)
    }

    @Test
    fun homeSectionType_displayNameAndDescription_verifyAllTypes() {
        assertEquals("Continue Watching", HomeSectionType.CONTINUE_WATCHING.displayName)
        assertEquals("Resume watching in-progress media", HomeSectionType.CONTINUE_WATCHING.description)

        assertEquals("Next Up", HomeSectionType.NEXT_UP.displayName)
        assertEquals("Next unwatched episodes of your shows", HomeSectionType.NEXT_UP.description)

        assertEquals("Recently Added", HomeSectionType.RECENTLY_ADDED.displayName)
        assertEquals("Latest Media", HomeSectionType.LATEST_MEDIA.displayName)
        assertEquals("Recommended For You", HomeSectionType.RECOMMENDATIONS.displayName)
    }
}
