package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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

    @Test
    fun cacheKey_equalContent_producesEqualKeyRegardlessOfSetIterationOrder() {
        // enabledSections is the field whose fingerprint must be normalized:
        // cacheKey() sorts it by enum name, so two sets with identical content
        // but different insertion (and therefore iteration) orders must hash
        // to the same key — the home VM rebuilds this set from preferences on
        // every refresh and must not churn the SWR cache key.
        val insertionOrder = setOf(
            HomeSectionType.CONTINUE_WATCHING,
            HomeSectionType.LATEST_MEDIA,
            HomeSectionType.NEXT_UP,
        )
        val reverseInsertionOrder = insertionOrder.toList().reversed().toSet()

        // Sanity: equal content, different iteration order.
        assertEquals(insertionOrder, reverseInsertionOrder)
        assertNotEquals(insertionOrder.toList(), reverseInsertionOrder.toList())

        val a = HomeSectionQuery(enabledSections = insertionOrder)
        val b = HomeSectionQuery(enabledSections = reverseInsertionOrder)

        assertEquals(a.cacheKey(), b.cacheKey())
    }

    @Test
    fun cacheKey_anySingleFieldDifference_producesDifferentKey() {
        val base = HomeSectionQuery(
            enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
            libraryHomeSectionOverrides = mapOf("lib1" to setOf(HomeSectionType.LATEST_MEDIA)),
            nextUpRewatching = true,
            nextUpMaxDays = 14,
            nextUpExcludedSeriesIds = setOf("series1"),
            hiddenCwItemIds = setOf("cw_item_1"),
            pinnedSections = listOf(
                PinnedHomeSection(type = PinnedSectionType.COLLECTION, sourceId = "shelf_1", title = "Favorites Shelf"),
            ),
        )
        // One variant per field, everything else identical to [base].
        val variants = listOf(
            base.copy(enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING)),
            base.copy(libraryHomeSectionOverrides = emptyMap()),
            base.copy(nextUpRewatching = false),
            base.copy(nextUpMaxDays = 30),
            base.copy(nextUpExcludedSeriesIds = emptySet()),
            base.copy(hiddenCwItemIds = emptySet()),
            base.copy(pinnedSections = emptyList()),
        )

        variants.forEach { variant ->
            assertNotEquals(base.cacheKey(), variant.cacheKey())
        }
    }

    @Test
    fun cacheKey_copyInstance_producesSameKeyAsOriginal() {
        val original = HomeSectionQuery(
            enabledSections = setOf(HomeSectionType.NEXT_UP),
            nextUpMaxDays = 7,
            hiddenCwItemIds = setOf("cw_item_1"),
        )
        val copied = original.copy()

        // copy() yields a fresh instance (and so a freshly-computed lazy memo)
        // with identical content — the key value must be equal.
        assertTrue(original !== copied)
        assertEquals(original.cacheKey(), copied.cacheKey())
    }
}
