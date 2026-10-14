package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.home.HomeRowModules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins the transport half of the home row registry: the refresh-arm table is
 * complete over the enum, its refreshable membership matches the model
 * registry's [HomeRowModules] edgeRefreshable gate (the two halves of the
 * edge-pull decision cannot drift), the non-refreshable rows fail with the
 * same caller-bug error the former `else` arm threw, and the batch sub-call
 * table carries exactly the resume trio plus the H4 Upcoming row (the only
 * rows with a direct, enabled-gated port call). The per-type refresh BEHAVIOR
 * is HomeSectionsFetcherTest's.
 */
class HomeRowArmsTest {

    private fun section(type: HomeSectionType) =
        HomeSection(id = "s1", title = "Section", type = type, items = emptyList())

    @Test
    fun refreshArms_resolveForEverySectionType() = runTest {
        for (type in HomeSectionType.entries) {
            HomeRowRefreshArms.forType(type)
        }
    }

    @Test
    fun armServed_matchesTheModelRegistryRefreshableGate() = runTest {
        for (type in HomeSectionType.entries) {
            // With an inert transport, a REAL arm never produces the
            // caller-bug failure — it returns (possibly null, possibly a
            // section, possibly its own instance-shape error). Only the
            // shared not-refreshable arm produces that exact message, so the
            // message's presence is the wiring pin.
            val outcome = runCatching {
                HomeRowRefreshArms.forType(type).refresh(refreshContext(), section(type))
            }
            val servedCallerBugArm =
                outcome.exceptionOrNull()?.message == "Home section type $type is not refreshable"
            assertEquals(
                !HomeRowModules[type].edgeRefreshable,
                servedCallerBugArm,
                "type=$type",
            )
        }
    }

    @Test
    fun batchSubcalls_coverExactlyTheResumeTrioPlusUpcoming() {
        // H4 joined the trio: Upcoming rides the same per-type batch-arm shape
        // (enabled gate + fixed limit, no query parameters of its own).
        assertEquals(
            setOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.CONTINUE_READING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.UPCOMING,
            ),
            HomeRowBatchSubcalls.keys,
        )
    }

    @Test
    fun batchSubcalls_makeZeroPortCallsForDisabledSections() = runTest {
        val sources = CallCountingSources()
        val query = HomeSectionQuery(enabledSections = emptySet())
        for ((_, subcall) in HomeRowBatchSubcalls) {
            val result = subcall(sources, query)
            assertTrue(result.getOrDefault(emptyList()).isEmpty())
        }
        assertEquals(0, sources.calls, "a disabled section makes ZERO port calls")
    }

    @Test
    fun batchAndRefreshArms_forwardExcludeActiveSessionsToContinueWatching() = runTest {
        val sources = RecordingSources()
        val query = HomeSectionQuery(
            enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING),
            excludeActiveSessions = true,
        )

        HomeRowBatchSubcalls.getValue(HomeSectionType.CONTINUE_WATCHING)(sources, query)
        assertTrue(sources.recordedExcludeActiveSessions == true, "the batch sub-call must forward the H6 flag")

        sources.recordedExcludeActiveSessions = null
        HomeRowRefreshArms.forType(HomeSectionType.CONTINUE_WATCHING).refresh(
            refreshContext(query = query, sources = sources),
            section(HomeSectionType.CONTINUE_WATCHING),
        )
        assertTrue(sources.recordedExcludeActiveSessions == true, "the refresh arm must forward the same H6 flag")
    }

    /** A context whose collaborators record rather than reach the network. */
    private fun refreshContext(
        query: HomeSectionQuery = HomeSectionQuery(),
        sources: HomeSectionSources = CallCountingSources(),
    ): HomeRowRefreshContext = HomeRowRefreshContext(
        sources = sources,
        query = query,
        force = true,
        mergeNextUpIntoContinueWatching = false,
        latestForLibrary = { _, _, _ -> Result.success(emptyList()) },
        continueWatchingFilterIds = { emptySet() },
        discoverRowItems = { _, _ -> Result.success(emptyList()) },
        fetchPluginCustomRow = { _, _ -> null },
        fetchSeasonalPluginRow = { emptyList() },
        pinnedSectionItems = { emptyList() },
    )

    /** Neutral stand-in for the Jellyfin-side port: counts calls, returns empties. */
    private class CallCountingSources : HomeSectionSources {
        var calls = 0
            private set

        private fun items(): Result<List<MediaItem>> {
            calls++
            return Result.success(emptyList())
        }

        private fun search(): Result<SearchResult> {
            calls++
            return Result.success(SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
        }

        override suspend fun getContinueWatching(limit: Int, classicRows: Boolean, excludeActiveSessions: Boolean) = items()
        override suspend fun getContinueReading(limit: Int) = items()
        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int) = items()
        override suspend fun getUpcoming(userId: String?, parentId: String?, limit: Int) = items()
        override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> {
            calls++
            return Result.success(emptyList())
        }
        override suspend fun getLatestMedia(parentId: String, limit: Int, classicEpisodePool: Int?) = items()
        override suspend fun getSimilarItems(itemId: String, limit: Int) = items()
        override suspend fun getSearchSuggestions(limit: Int) = search()
        override suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int) = search()
        override suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int) = search()
        override suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int) = search()
        override suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int) = search()
        override suspend fun getDiscoverRowItems(row: DiscoverRowConfig) = items()
    }

    /** Stand-in that records the H6 flag the arms pass to the resume read. */
    private class RecordingSources : HomeSectionSources {
        var recordedExcludeActiveSessions: Boolean? = null

        override suspend fun getContinueWatching(
            limit: Int,
            classicRows: Boolean,
            excludeActiveSessions: Boolean,
        ): Result<List<MediaItem>> {
            recordedExcludeActiveSessions = excludeActiveSessions
            return Result.success(emptyList())
        }
        override suspend fun getContinueReading(limit: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getUpcoming(userId: String?, parentId: String?, limit: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getLibraryFolders() = Result.success(emptyList<LibraryFolder>())
        override suspend fun getLatestMedia(parentId: String, limit: Int, classicEpisodePool: Int?) = Result.success(emptyList<MediaItem>())
        override suspend fun getSimilarItems(itemId: String, limit: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getSearchSuggestions(limit: Int) = Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        override suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int) = Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        override suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int) = Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        override suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int) = Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        override suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int) = Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        override suspend fun getDiscoverRowItems(row: DiscoverRowConfig) = Result.success(emptyList<MediaItem>())
    }
}
