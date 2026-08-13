package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure tests for [toAvailability] — the projection from a [MediaSegment] list
 * into the two skip affordances the detail chip advertises. Only INTRO/OUTRO
 * map to a flag; every other [MediaSegmentType] is ignored.
 */
class SegmentAvailabilityTest {

    private fun segment(type: MediaSegmentType): MediaSegment = MediaSegment(
        id = "${type.name}-1",
        itemId = "item-1",
        type = type,
        startTicks = 0L,
        endTicks = 10_000_000L,
    )

    @Test
    fun emptyList_yieldsNoAvailability() {
        val availability = emptyList<MediaSegment>().toAvailability()
        assertFalse(availability.hasIntro)
        assertFalse(availability.hasCredits)
    }

    @Test
    fun introOnly_setsHasIntroNotCredits() {
        val availability = listOf(segment(MediaSegmentType.INTRO)).toAvailability()
        assertTrue(availability.hasIntro)
        assertFalse(availability.hasCredits)
    }

    @Test
    fun outroOnly_setsHasCreditsNotIntro() {
        val availability = listOf(segment(MediaSegmentType.OUTRO)).toAvailability()
        assertFalse(availability.hasIntro)
        assertTrue(availability.hasCredits)
    }

    @Test
    fun introAndOutro_setsBoth() {
        val availability = listOf(
            segment(MediaSegmentType.INTRO),
            segment(MediaSegmentType.OUTRO),
        ).toAvailability()
        assertTrue(availability.hasIntro)
        assertTrue(availability.hasCredits)
    }

    @Test
    fun otherSegmentTypes_areIgnored() {
        val availability = listOf(
            segment(MediaSegmentType.PREVIEW),
            segment(MediaSegmentType.RECAP),
            segment(MediaSegmentType.COMMERCIAL),
            segment(MediaSegmentType.UNKNOWN),
        ).toAvailability()
        assertFalse(availability.hasIntro)
        assertFalse(availability.hasCredits)
    }

    @Test
    fun mixedIntroOutroAndOthers_picksUpOnlyIntroAndOutro() {
        val availability = listOf(
            segment(MediaSegmentType.COMMERCIAL),
            segment(MediaSegmentType.INTRO),
            segment(MediaSegmentType.PREVIEW),
            segment(MediaSegmentType.OUTRO),
        ).toAvailability()
        assertTrue(availability.hasIntro)
        assertTrue(availability.hasCredits)
    }
}
