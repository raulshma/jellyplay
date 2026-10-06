@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.api.JellyPlayRowItem
import com.raulshma.jellyplay.core.network.api.JellyPlayRowResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the PLUGIN_ROW leaf — the companion-plugin home rows (ADR 0010) that
 * fetch beside the Seerr discover rows in [HomeSectionsFetcher]: the
 * capability gate (a null transport or a gate reading false makes ZERO port
 * calls and emits zero rows — silently absent, never an error), the
 * fetch-map shape (payload order preserved; `localItemId`s resolved through
 * ONE batched ids read; unresolved entries kept as fallback tiles), the
 * drop-don't-fail failure policy (a plugin 404/500 is a missing row, not a
 * failed home fetch), the TTL memo (hit across refreshes, force bypass,
 * identity-switch miss), and the single-row PLUGIN_ROW arm of
 * [HomeSectionsFetcher.refreshSection] (seasonal + the titled custom-row
 * plumbing, whose only blocker is the missing enumerate-rows capability).
 *
 * Mirrors the Seerr leaf pins in [HomeSectionsFetcherTest] (same fake-port
 * shape, same fetcher constructor).
 */
class HomeSectionsFetcherPluginRowTest {

    // ── doubles ─────────────────────────────────────────────────────────

    /** Neutral stand-in for the Jellyfin-side port: this suite never drives it. */
    private class StubHomeSectionSources : HomeSectionSources {
        override suspend fun getContinueWatching(limit: Int, classicRows: Boolean) = Result.success(emptyList<MediaItem>())
        override suspend fun getContinueReading(limit: Int) = Result.success(emptyList<MediaItem>())
        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int) = Result.success(emptyList<MediaItem>())
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
        override suspend fun getDiscoverRowItems(row: com.raulshma.jellyplay.core.model.DiscoverRowConfig) = Result.success(emptyList<MediaItem>())
    }

    /** The plugin-side port double: gate flags, call log, scripted results. */
    private class FakeJellyPlayHomeSectionSources : JellyPlayHomeSectionSources {
        private val catalog = java.util.concurrent.atomic.AtomicReference<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?>(
            com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog(rows = emptyList()),
        )
        override suspend fun getCustomRowCatalog(): kotlin.Result<com.raulshma.jellyplay.core.network.api.JellyPlayRowCatalog?> =
            kotlin.Result.success(catalog.get())
        var seasonalEnabled: Boolean = true
        var customEnabled: Boolean = true

        /** Port calls in issue order: "seasonal", "custom:<title>", "items:<n>". */
        val calls = mutableListOf<String>()

        val seasonalResults = ArrayDeque<Result<JellyPlayRowResult?>>()
        val customResults = ArrayDeque<Result<JellyPlayRowResult?>>()
        val itemResults = ArrayDeque<Result<List<MediaItem>>>()
        val requestedItemIds = mutableListOf<List<String>>()

        override suspend fun seasonalRowsEnabled(): Boolean = seasonalEnabled

        override suspend fun customRowsEnabled(): Boolean = customEnabled

        override suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?> {
            calls += "seasonal"
            assertTrue(keyword == null, "the v1 seasonal fetch passes no keyword (the plugin picks the season)")
            return seasonalResults.removeFirstOrNull() ?: Result.success(null)
        }

        override suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?> {
            calls += "custom:$title"
            return customResults.removeFirstOrNull() ?: Result.success(null)
        }

        override suspend fun getItemsByIds(ids: List<String>): Result<List<MediaItem>> {
            calls += "items:${ids.size}"
            requestedItemIds += ids
            return itemResults.removeFirstOrNull() ?: Result.success(emptyList())
        }
    }

    private val identityA = CacheIdentity.ofOrNull("server-1", "user-1")
    private val identityB = CacheIdentity.ofOrNull("server-1", "user-2")

    private fun fetcher(
        plugin: FakeJellyPlayHomeSectionSources?,
        identity: () -> CacheIdentity? = { identityA },
    ) = HomeSectionsFetcher(
        sources = StubHomeSectionSources(),
        seerrSources = null,
        cacheIdentity = identity,
        today = { "2026-10-06" },
        jellyPlaySources = plugin,
    )

    private fun mediaItem(id: String) = MediaItem(id = id, name = "Local $id", mediaType = MediaType.MOVIE)

    private fun rowItem(
        title: String,
        year: String? = null,
        localItemId: String? = null,
    ) = JellyPlayRowItem(title = title, year = year, localItemId = localItemId)

    private fun seasonalPayload(
        items: List<JellyPlayRowItem>,
        title: String = "Spooky Season",
    ): Result<JellyPlayRowResult?> = Result.success(
        JellyPlayRowResult(title = title, source = "letterboxd", items = items),
    )

    private fun pluginRow(result: HomeSectionsResult) =
        result.sections.singleOrNull { it.type == HomeSectionType.PLUGIN_ROW }

    // ── the gate ────────────────────────────────────────────────────────

    @Test
    fun `no transport wired makes zero port calls and zero rows`() = runTest {
        val fetched = fetcher(plugin = null).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertTrue(fetched.sections.isEmpty())
        assertTrue(fetched.failedSectionTypes.isEmpty())
    }

    @Test
    fun `gate off makes zero port calls and zero rows`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply { seasonalEnabled = false }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertTrue(plugin.calls.isEmpty(), "a gate reading false must not touch the transport")
        assertNull(pluginRow(fetched))
    }

    // ── fetch + map ─────────────────────────────────────────────────────

    @Test
    fun `seasonal row fetches, resolves local items in one batch, keeps payload order`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(
                items = listOf(
                    rowItem("Local One", year = "2001", localItemId = "l1"),
                    rowItem("Unmatched Classic", year = "1954"),
                    rowItem("Local Two", localItemId = "l2"),
                ),
            )
            itemResults += Result.success(listOf(mediaItem("l2"), mediaItem("l1")))
        }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertEquals(listOf("seasonal", "items:2"), plugin.calls)
        assertEquals(listOf("l1", "l2"), plugin.requestedItemIds.single())
        val row = assertNotNull(pluginRow(fetched))
        assertEquals("jellyplay_seasonal", row.id)
        assertEquals("Spooky Season", row.title, "the plugin's row title is authoritative")
        assertTrue(row.items.isEmpty(), "plugin rows ride jellyPlayRowEntries, never items")
        val entries = row.jellyPlayRowEntries
        assertEquals(3, entries.size)
        assertEquals("l1", entries[0].localItem?.id)
        assertEquals("Local l1", entries[0].localItem?.name)
        assertEquals("2001", entries[0].year)
        assertNull(entries[1].localItem, "an unmatched entry stays a fallback tile")
        assertEquals("Unmatched Classic", entries[1].title)
        assertEquals("l2", entries[2].localItem?.id)
    }

    @Test
    fun `seasonal row absent (null payload) renders no row`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += Result.success(null)
        }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertNull(pluginRow(fetched))
        assertTrue(fetched.failedSectionTypes.isEmpty())
    }

    @Test
    fun `seasonal row empty payload renders no row`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = emptyList())
        }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertNull(pluginRow(fetched))
    }

    @Test
    fun `seasonal row failure drops the row without failing the fetch`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += Result.failure(IllegalStateException("plugin 500"))
        }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertNull(pluginRow(fetched))
        assertTrue(fetched.failedSectionTypes.isEmpty(), "a curated plugin row failing is not a home failure")
    }

    @Test
    fun `lost item resolution degrades entries to fallback tiles, keeps the row`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = listOf(rowItem("Gone", localItemId = "l1")))
            itemResults += Result.failure(IllegalStateException("ids read lost"))
        }

        val fetched = fetcher(plugin).fetch(HomeSectionQuery(enabledSections = emptySet()))

        val row = assertNotNull(pluginRow(fetched))
        val entry = row.jellyPlayRowEntries.single()
        assertNull(entry.localItem)
        assertEquals("Gone", entry.title)
    }

    // ── the TTL memo ────────────────────────────────────────────────────

    @Test
    fun `seasonal row is memoised across refreshes and force bypasses the memo`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = listOf(rowItem("One", localItemId = "l1")))
            seasonalResults += seasonalPayload(items = listOf(rowItem("Two", localItemId = "l2")))
            itemResults += Result.success(listOf(mediaItem("l1")))
            itemResults += Result.success(listOf(mediaItem("l2")))
        }
        val home = fetcher(plugin)
        val query = HomeSectionQuery(enabledSections = emptySet())

        home.fetch(query)
        home.fetch(query)
        assertEquals(listOf("seasonal", "items:1"), plugin.calls, "the second ordinary fetch serves the memo")

        home.fetch(query, force = true)
        assertEquals(
            listOf("seasonal", "items:1", "seasonal", "items:1"),
            plugin.calls,
            "force (pull-to-refresh) bypasses the memo read but re-memoises the write",
        )
    }

    @Test
    fun `plugin row memo is identity-scoped`() = runTest {
        var identity: CacheIdentity? = identityA
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = listOf(rowItem("One")))
            seasonalResults += seasonalPayload(items = listOf(rowItem("Two")))
        }
        val home = fetcher(plugin, identity = { identity })
        val query = HomeSectionQuery(enabledSections = emptySet())

        home.fetch(query)
        identity = identityB
        home.fetch(query)

        assertEquals(2, plugin.calls.count { it == "seasonal" }, "a user switch misses the memo by construction")
    }

    // ── the titled custom-row plumbing ──────────────────────────────────

    @Test
    fun `custom row plumbing fetches a titled row and caches by title`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            customResults += seasonalPayload(
                items = listOf(rowItem("Only", localItemId = "l1")),
                title = "Top 10 of 2025",
            )
            itemResults += Result.success(listOf(mediaItem("l1")))
        }
        val home = fetcher(plugin)

        val row = home.fetchPluginCustomRow("Top 10 of 2025")
        home.fetchPluginCustomRow("Top 10 of 2025")

        assertEquals(listOf("custom:Top 10 of 2025", "items:1"), plugin.calls, "the second call serves the title-keyed memo")
        assertNotNull(row)
        assertEquals("jellyplay_custom_Top 10 of 2025", row.id)
        assertEquals("Top 10 of 2025", row.title)
        assertEquals("l1", row.jellyPlayRowEntries.single().localItem?.id)

        val other = home.fetchPluginCustomRow("Another List")
        assertNull(other, "an unscripted title reads as plugin-404 → no row")
    }

    @Test
    fun `custom row gate off yields no row and no calls`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply { customEnabled = false }

        val row = fetcher(plugin).fetchPluginCustomRow("Top 10 of 2025")

        assertNull(row)
        assertTrue(plugin.calls.isEmpty())
    }

    // ── the single-row refetch arm ──────────────────────────────────────

    @Test
    fun `refreshSection refetches the seasonal row in place`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = listOf(rowItem("Old", localItemId = "l1")))
            seasonalResults += seasonalPayload(items = listOf(rowItem("New", localItemId = "l2")))
            itemResults += Result.success(listOf(mediaItem("l1")))
            itemResults += Result.success(listOf(mediaItem("l2")))
        }
        val home = fetcher(plugin)
        val query = HomeSectionQuery(enabledSections = emptySet())
        val stale = assertNotNull(pluginRow(home.fetch(query)))

        val fresh = home.refreshSection(stale, query, force = true).getOrThrow()

        val row = assertNotNull(fresh)
        assertEquals(stale.id, row.id, "identity preserved so the keyed list animates, not removes")
        assertEquals("l2", row.jellyPlayRowEntries.single().localItem?.id)
    }

    @Test
    fun `refreshSection refetches a titled custom row by its id`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            customResults += seasonalPayload(items = listOf(rowItem("Old")), title = "Top 10 of 2025")
            customResults += seasonalPayload(items = listOf(rowItem("New")), title = "Top 10 of 2025")
        }
        val home = fetcher(plugin)
        val stale = home.fetchPluginCustomRow("Top 10 of 2025")
        val fresh = home.refreshSection(assertNotNull(stale), HomeSectionQuery(enabledSections = emptySet()))

        assertEquals("New", fresh.getOrThrow()?.jellyPlayRowEntries?.single()?.title)
    }

    @Test
    fun `refreshSection on an emptied seasonal row drops it`() = runTest {
        val plugin = FakeJellyPlayHomeSectionSources().apply {
            seasonalResults += seasonalPayload(items = listOf(rowItem("One")))
            seasonalResults += Result.success(null)
        }
        val home = fetcher(plugin)
        val query = HomeSectionQuery(enabledSections = emptySet())
        val stale = assertNotNull(pluginRow(home.fetch(query)))

        val fresh = home.refreshSection(stale, query, force = true).getOrThrow()

        assertNull(fresh, "the row legitimately emptied — the batch's zero-items policy")
    }
}
