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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins the transport registration of the home row registry: the table is
 * complete over the enum (one registration per type, carrying both the batch
 * sub-call and the single-row refresh arm — or stating their absence), its
 * refresh-arm membership matches the model registry's [HomeRowModules]
 * edgeRefreshable gate (the two halves of the edge-pull decision cannot
 * drift), the rows without a refresh arm fail with the same caller-bug error
 * the former `else` arm threw, and the batch sub-calls carry exactly the
 * resume trio plus the Upcoming row (the only rows with a direct,
 * enabled-gated port read). The per-type refresh BEHAVIOR is
 * HomeSectionsFetcherTest's.
 */
class HomeRowArmsTest {

    private fun section(type: HomeSectionType) =
        HomeSection(id = "s1", title = "Section", type = type, items = emptyList())

    @Test
    fun transports_registerEverySectionType() {
        assertEquals(HomeSectionType.entries.toSet(), HomeRowTransports.keys)
    }

    @Test
    fun refreshArmServed_matchesTheModelRegistryRefreshableGate() = runTest {
        for (type in HomeSectionType.entries) {
            // With an inert transport, a REAL arm never produces the
            // caller-bug failure — it returns (possibly null, possibly a
            // section, possibly its own instance-shape error). Only the
            // shared not-refreshable arm produces that exact message, so the
            // message's presence is the wiring pin.
            val outcome = runCatching {
                homeRowRefreshArm(type).refresh(refreshContext(), section(type))
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
    fun refreshArmRegistration_matchesTheModelRegistryRefreshableGate() {
        for (type in HomeSectionType.entries) {
            // The registration STATES the arm (or its absence) once — and the
            // stated membership cannot drift from the model registry's gate.
            assertEquals(
                HomeRowModules[type].edgeRefreshable,
                HomeRowTransports.getValue(type).refreshArm != null,
                "type=$type",
            )
        }
    }

    @Test
    fun batchSubcalls_coverExactlyTheResumeTrioPlusUpcoming() {
        // Upcoming joined the trio: it rides the same per-type batch-arm shape
        // (enabled gate + fixed limit, no query parameters of its own).
        assertEquals(
            setOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.CONTINUE_READING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.UPCOMING,
            ),
            HomeRowTransports.filterValues { it.batchSubcall != null }.keys,
        )
        // The schedule dispatcher serves exactly the registrations' sub-calls
        // and fails loudly for the rows the schedule itself carries.
        for (type in HomeRowTransports.keys) {
            if (HomeRowTransports.getValue(type).batchSubcall != null) {
                assertEquals(
                    HomeRowTransports.getValue(type).batchSubcall,
                    homeRowBatchSubcall(type),
                )
            } else {
                val error = runCatching { homeRowBatchSubcall(type) }.exceptionOrNull()
                assertTrue(error?.message?.contains("no batch sub-call") == true, "type=$type")
            }
        }
    }

    @Test
    fun batchSubcalls_makeZeroPortCallsForDisabledSections() = runTest {
        val sources = CallCountingSources()
        val query = HomeSectionQuery(enabledSections = emptySet())
        for ((_, registration) in HomeRowTransports) {
            val result = registration.batchSubcall?.invoke(sources, query) ?: continue
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

        // One registered read serves both arms — the flag is stated once, so
        // the two arms cannot disagree on the wire flag.
        HomeRowTransports.getValue(HomeSectionType.CONTINUE_WATCHING).batchSubcall!!.invoke(sources, query)
        assertTrue(sources.recordedExcludeActiveSessions == true, "the batch sub-call must forward the flag")

        sources.recordedExcludeActiveSessions = null
        homeRowRefreshArm(HomeSectionType.CONTINUE_WATCHING).refresh(
            refreshContext(query = query, sources = sources),
            section(HomeSectionType.CONTINUE_WATCHING),
        )
        assertTrue(sources.recordedExcludeActiveSessions == true, "the refresh arm must forward the same flag")
    }

    @Test
    fun continueWatchingRefreshArm_bypassesTheEnabledGateOnBothHalvesOfTheMerge() = runTest {
        // The merge fold's Next Up half re-reads through the NEXT_UP
        // registration's read (ungated — a single-row refetch reads fresh
        // regardless of the batch's section gates).
        val sources = RecordingSources()
        val query = HomeSectionQuery(
            // NEXT_UP disabled at batch time, yet the merged CW refetch still
            // rebuilds the fold from its fresh read.
            enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING),
        )

        homeRowRefreshArm(HomeSectionType.CONTINUE_WATCHING).refresh(
            refreshContext(
                query = query,
                sources = sources,
                mergeNextUpIntoContinueWatching = true,
            ),
            section(HomeSectionType.CONTINUE_WATCHING),
        )
        assertTrue(sources.recordedNextUpCall, "the merge fold consumes the NEXT_UP registration's read")
    }

    @Test
    fun nonRefreshableRegistrations_renderEmptyRowsWithoutRefreshArms() = runTest {
        // The registrations state the absence once (a null arm) — the
        // not-refreshable dispatch arms it into the caller-bug failure.
        for (type in HomeRowTransports.keys) {
            if (HomeRowTransports.getValue(type).refreshArm == null) {
                val outcome = runCatching {
                    homeRowRefreshArm(type).refresh(refreshContext(), section(type))
                }
                assertEquals(
                    "Home section type $type is not refreshable",
                    outcome.exceptionOrNull()?.message,
                    "type=$type",
                )
            }
        }
        assertNull(HomeRowTransports.getValue(HomeSectionType.MY_MEDIA).refreshArm)
    }

    /** A context whose collaborators record rather than reach the network. */
    private fun refreshContext(
        query: HomeSectionQuery = HomeSectionQuery(),
        sources: HomeSectionSources = CallCountingSources(),
        mergeNextUpIntoContinueWatching: Boolean = false,
    ): HomeRowRefreshContext = HomeRowRefreshContext(
        sources = sources,
        query = query,
        force = true,
        mergeNextUpIntoContinueWatching = mergeNextUpIntoContinueWatching,
        latestForLibrary = { _, _, _ -> Result.success(emptyList()) },
        latestFanOut = { _ -> emptyList() },
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

    /** Stand-in that records the flag the arms pass to the resume reads. */
    private class RecordingSources : HomeSectionSources {
        var recordedExcludeActiveSessions: Boolean? = null
        var recordedNextUpCall: Boolean = false

        override suspend fun getContinueWatching(
            limit: Int,
            classicRows: Boolean,
            excludeActiveSessions: Boolean,
        ): Result<List<MediaItem>> {
            recordedExcludeActiveSessions = excludeActiveSessions
            return Result.success(emptyList())
        }
        override suspend fun getContinueReading(limit: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>> {
            recordedNextUpCall = true
            return Result.success(emptyList())
        }
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
