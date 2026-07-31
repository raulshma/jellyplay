package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionQueryTest {

    @Test
    fun homeSectionQuery_defaultValues_matchRepositoryDefaults() {
        val query = HomeSectionQuery()

        assertEquals(HomeSectionType.CONFIGURABLE.toSet(), query.enabledSections)
        assertTrue(query.libraryHomeSectionOverrides.isEmpty())
        assertFalse(query.nextUpRewatching)
        assertEquals(0, query.nextUpMaxDays)
        assertTrue(query.nextUpExcludedSeriesIds.isEmpty())
        assertTrue(query.hiddenCwItemIds.isEmpty())
        assertTrue(query.pinnedSections.isEmpty())
    }

    @Test
    fun homeSectionQuery_customValues_preservedCorrectly() {
        val customSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP)
        val libraryOverrides = mapOf("lib1" to setOf(HomeSectionType.LATEST_MEDIA))
        val excludedSeries = setOf("series1", "series2")
        val hiddenItems = setOf("cw_item_1")
        val pinned = listOf(PinnedHomeSection(type = PinnedSectionType.COLLECTION, sourceId = "shelf_1", title = "Favorites Shelf"))

        val query = HomeSectionQuery(
            enabledSections = customSections,
            libraryHomeSectionOverrides = libraryOverrides,
            nextUpRewatching = true,
            nextUpMaxDays = 14,
            nextUpExcludedSeriesIds = excludedSeries,
            hiddenCwItemIds = hiddenItems,
            pinnedSections = pinned,
        )

        assertEquals(customSections, query.enabledSections)
        assertEquals(libraryOverrides, query.libraryHomeSectionOverrides)
        assertTrue(query.nextUpRewatching)
        assertEquals(14, query.nextUpMaxDays)
        assertEquals(excludedSeries, query.nextUpExcludedSeriesIds)
        assertEquals(hiddenItems, query.hiddenCwItemIds)
        assertEquals(pinned, query.pinnedSections)
    }

    @Test
    fun homeSectionQuery_copyModifications_workAsExpected() {
        val baseQuery = HomeSectionQuery()
        val modifiedQuery = baseQuery.copy(
            nextUpMaxDays = 30,
            hiddenCwItemIds = setOf("hidden1"),
        )

        assertEquals(30, modifiedQuery.nextUpMaxDays)
        assertEquals(setOf("hidden1"), modifiedQuery.hiddenCwItemIds)
        assertEquals(baseQuery.enabledSections, modifiedQuery.enabledSections)
    }
}
