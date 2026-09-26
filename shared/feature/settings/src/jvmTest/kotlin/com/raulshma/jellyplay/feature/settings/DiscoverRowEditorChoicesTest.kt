package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the editor's hoisted chip ladders (DiscoverRowEditorChoices): the
 * exact rungs ARE the shipped editor behavior — a changed rung here is a
 * product decision, not a refactor. Label formatting stays in the screen;
 * only the selectable values live here.
 */
class DiscoverRowEditorChoicesTest {

    @Test
    fun `rating chips step Any-6-7-7_5-8`() {
        assertEquals(listOf(0f, 6f, 7f, 7.5f, 8f), DiscoverRowEditorChoices.ratingChipSteps)
    }

    @Test
    fun `year buckets are the four shipped ranges, newest first`() {
        assertEquals(
            listOf(2020..2026, 2010..2019, 2000..2009, 1990..1999),
            DiscoverRowEditorChoices.yearRanges,
        )
    }

    @Test
    fun `added-within chips ladder Any then 7-30-90-365 days`() {
        assertEquals(listOf<Int?>(null, 7, 30, 90, 365), DiscoverRowEditorChoices.addedWithinDays)
    }

    @Test
    fun `premiered-within chips ladder Any then 1-5-10-25 years`() {
        assertEquals(listOf<Int?>(null, 1, 5, 10, 25), DiscoverRowEditorChoices.premieredWithinYears)
    }
}
