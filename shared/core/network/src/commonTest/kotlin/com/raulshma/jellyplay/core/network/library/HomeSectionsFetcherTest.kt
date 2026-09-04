@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.PinnedSectionType
import com.raulshma.jellyplay.core.model.SearchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the commonMain home-sections fetch choreography ([HomeSectionsFetcher])
 * that replaced the two hand-copied client implementations: the disabled-
 * section gating (zero port calls), the deliberate serialization of the
 * recommendations chain behind Continue Watching / Next Up (its seeds), the
 * seed-fallback wart, the music-filtered folder-order latest fan-out, the
 * suggestions pre-fetch condition (same predicate the assembler's fallback
 * branch renders on — the cross-module agreement), the partial-failure vs
 * throw-when-nothing-rendered error policy, always-fetched pinned rows, and
 * the identity-keyed TTL sub-call cache semantics (hit / force-bypass /
 * memoise / identity-switch miss).
 */
class HomeSectionsFetcherTest {

    private class FakeHomeSectionSources : HomeSectionSources {

        /** Leaf calls in issue order, e.g. "latest:f1:16". */
        val calls = mutableListOf<String>()

        /** Created by a test BEFORE the call happens; the call suspends until completed. */
        private val returnGates = mutableMapOf<String, CompletableDeferred<Unit>>()

        /** Pre-register a completion gate for the call named [key]. */
        fun gate(key: String): CompletableDeferred<Unit> = returnGates.getOrPut(key) { CompletableDeferred() }

        /** Calls named [key] THROW this instead of returning (fatal-leaf paths, e.g. a failing pin). */
        val throwables = mutableMapOf<String, Throwable>()

        val continueWatchingResults = ArrayDeque<Result<List<MediaItem>>>()
        val nextUpResults = ArrayDeque<Result<List<MediaItem>>>()
        val foldersResults = ArrayDeque<Result<List<LibraryFolder>>>()
        val latestResults = ArrayDeque<Result<List<MediaItem>>>()
        val similarResults = ArrayDeque<Result<List<MediaItem>>>()
        val suggestionsResults = ArrayDeque<Result<SearchResult>>()
        val collectionResults = ArrayDeque<Result<SearchResult>>()

        private suspend fun <T> resolve(queue: ArrayDeque<Result<T>>, key: String, empty: () -> T): Result<T> {
            calls += key
            // The outcome is bound at ISSUE time; a registered gate only
            // delays its DELIVERY (so scrambled gate completion cannot swap
            // which caller gets which scripted result).
            val scripted = queue.removeFirstOrNull() ?: Result.success(empty())
            returnGates[key]?.await()
            throwables[key]?.let { throw it }
            return scripted
        }

        override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> =
            resolve(continueWatchingResults, "cw:$limit") { emptyList() }

        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>> =
            resolve(nextUpResults, "nu:$limit:$enableRewatching:$maxDays") { emptyList() }

        override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> =
            resolve(foldersResults, "folders") { emptyList() }

        override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> =
            resolve(latestResults, "latest:$parentId:$limit") { emptyList() }

        override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
            resolve(similarResults, "similar:$itemId:$limit") { emptyList() }

        override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> =
            resolve(suggestionsResults, "suggestions:$limit") { SearchResult(emptyList(), 0, 0) }

        override suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int): Result<SearchResult> =
            resolve(collectionResults, "collection:$collectionId:$limit") { SearchResult(emptyList(), 0, 0) }

        // Unused by the choreography under test — fail loudly if ever reached.
        override suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult> =
            throw UnsupportedOperationException()

        override suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> =
            throw UnsupportedOperationException()

        override suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> =
            throw UnsupportedOperationException()
    }

    private fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE)

    private fun folder(id: String, collectionType: String? = "movies") =
        LibraryFolder(id = id, name = "Lib $id", collectionType = collectionType)

    private fun searchResultOf(vararg ids: String) = Result.success(
        SearchResult(items = ids.map { item(it) }, totalRecordCount = ids.size, startIndex = 0),
    )

    private val identityA = CacheIdentity.ofOrNull("server-1", "user-1")

    private fun fetcher(fake: FakeHomeSectionSources, identity: () -> CacheIdentity? = { identityA }) =
        HomeSectionsFetcher(fake, identity)

    private fun latestRows(result: HomeSectionsResult) =
        result.sections.filter { it.type == HomeSectionType.LATEST_MEDIA }

    @Test
    fun `disabled sections make zero port calls`() = runTest {
        val fake = FakeHomeSectionSources()

        val fetched = fetcher(fake).fetch(HomeSectionQuery(enabledSections = emptySet()))

        assertTrue(fake.calls.isEmpty(), "no transport call may fire for a fully disabled query")
        assertTrue(fetched.sections.isEmpty())
        assertTrue(fetched.failedSectionTypes.isEmpty())
    }

    @Test
    fun `recommendations chain launches after continue watching and next up resolve and seeds from their items`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.continueWatchingResults += Result.success(listOf(item("cw1")))
        fake.nextUpResults += Result.success(listOf(item("nu1")))
        fake.similarResults += Result.success(listOf(item("recA")))
        fake.similarResults += Result.success(listOf(item("recB")))
        // Close the Continue Watching gate (keyed like the recorded call):
        // the whole seed chain stalls behind it.
        val cwGate = fake.gate("cw:20")

        var fetched: HomeSectionsResult? = null
        val job = launch {
            fetched = fetcher(fake).fetch(
                HomeSectionQuery(
                    enabledSections = setOf(
                        HomeSectionType.CONTINUE_WATCHING,
                        HomeSectionType.NEXT_UP,
                        HomeSectionType.RECOMMENDATIONS,
                    ),
                ),
            )
        }
        runCurrent()

        // CW + Next Up were issued, but the recommendations chain has NOT run
        // yet (deliberate serialization — do not "improve" it).
        assertTrue("cw:20" in fake.calls)
        assertTrue("nu:20:false:0" in fake.calls)
        assertTrue(fake.calls.none { it.startsWith("similar:") }, "similar fan-out must wait for the seeds")

        cwGate.complete(Unit)
        advanceUntilIdle()
        job.join()

        // Seeds = CW + Next Up items → 2 seeds → per-seed limit = 20/2 + 2 = 12.
        assertEquals(
            listOf("similar:cw1:12", "similar:nu1:12"),
            fake.calls.filter { it.startsWith("similar:") },
        )
        // Suggestions must NOT be fetched (recommendations succeeded with items).
        assertTrue(fake.calls.none { it.startsWith("suggestions") })

        val result = fetched!!
        assertEquals(
            listOf(
                HomeSectionType.CONTINUE_WATCHING,
                HomeSectionType.NEXT_UP,
                HomeSectionType.RECOMMENDATIONS,
            ),
            result.sections.map { it.type },
        )
        val recSection = result.sections.last()
        assertEquals(listOf("recA", "recB"), recSection.items.map { it.id })
        assertEquals("cw1", recSection.seedItem?.id)
    }

    @Test
    fun `empty seeds fall back to the recommendations chain's own 5-limit seed calls`() = runTest {
        val fake = FakeHomeSectionSources()
        // CW / Next Up SECTIONS disabled → the recs chain starts seedless and
        // fetches its own with the historical wart (limit 5, default flags).
        fake.continueWatchingResults += Result.success(listOf(item("cw5")))
        fake.nextUpResults += Result.success(listOf(item("nu5")))
        fake.similarResults += Result.success(listOf(item("recA")))
        fake.similarResults += Result.success(listOf(item("recB")))

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(enabledSections = setOf(HomeSectionType.RECOMMENDATIONS)),
        )

        assertTrue("cw:5" in fake.calls, "wart verbatim: own getContinueWatching(limit = 5)")
        assertTrue("nu:5:false:0" in fake.calls, "wart verbatim: own getNextUp(5, rewatching false, maxDays 0)")
        // 2 seeds → per-seed limit = 20/2 + 2 = 12.
        assertEquals(
            listOf("similar:cw5:12", "similar:nu5:12"),
            fake.calls.filter { it.startsWith("similar:") },
        )
        assertEquals(listOf("recA", "recB"), fetched.sections.single().items.map { it.id })
        assertEquals("cw5", fetched.sections.single().seedItem?.id)
    }

    @Test
    fun `latest fan out filters music folders and preserves folder order under scrambled completion`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.foldersResults += Result.success(
            listOf(
                folder("f1"),
                folder("musicLib", collectionType = "music"),
                folder("f3", collectionType = "tvshows"),
            ),
        )
        fake.latestResults += Result.success(listOf(item("a1")))              // f1
        fake.latestResults += Result.success(listOf(item("c1"), item("c2")))  // f3
        val f1Gate = fake.gate("latest:f1:16")
        val f3Gate = fake.gate("latest:f3:16")

        var fetched: HomeSectionsResult? = null
        val job = launch {
            fetched = fetcher(fake).fetch(
                HomeSectionQuery(
                    enabledSections = setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
                ),
            )
        }
        runCurrent()

        // One call per NON-music folder, both issued concurrently.
        assertEquals(setOf("folders", "latest:f1:16", "latest:f3:16"), fake.calls.toSet())

        // Complete in reverse folder order…
        f3Gate.complete(Unit)
        f1Gate.complete(Unit)
        advanceUntilIdle()
        job.join()

        val result = fetched!!
        // …rows still render in FOLDER order, Recently Added aggregates the same way.
        assertEquals(
            listOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED),
            result.sections.map { it.type },
        )
        assertEquals("latest_f1", result.sections[0].id)
        assertEquals("latest_f3", result.sections[1].id)
        assertEquals(listOf("a1", "c1", "c2"), result.sections[2].items.map { it.id })
        assertTrue(fake.calls.none { it.contains("musicLib") }, "music folder filtered before any port call")
    }

    @Test
    fun `suggestions fetched only when recommendations succeeded empty and assembler renders them as the recommendations row`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.suggestionsResults += searchResultOf("s1", "s2")

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(enabledSections = setOf(HomeSectionType.RECOMMENDATIONS)),
        )

        // Recs succeeded (empty seeds → empty result) and ONLY THEN was the
        // suggestions pre-fetch issued — the same predicate the assembler's
        // fallback branch (~162) renders on; this pair is the cross-module pin.
        assertTrue("suggestions:20" in fake.calls)
        val recSection = fetched.sections.single()
        assertEquals(HomeSectionType.RECOMMENDATIONS, recSection.type)
        assertEquals(listOf("s1", "s2"), recSection.items.map { it.id })
        assertEquals(null, recSection.seedItem)
    }

    @Test
    fun `suggestions not fetched when recommendations returned items`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.continueWatchingResults += Result.success(listOf(item("cw1")))
        fake.similarResults += Result.success(listOf(item("recA")))

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(
                enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.RECOMMENDATIONS),
            ),
        )

        assertTrue(fake.calls.none { it.startsWith("suggestions") })
        assertEquals(listOf("recA"), fetched.sections.last().items.map { it.id })
    }

    @Test
    fun `partial failure records failedSectionTypes without throwing`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.continueWatchingResults += Result.failure(RuntimeException("cw down"))
        fake.nextUpResults += Result.success(listOf(item("nu1")))

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(
                enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
            ),
        )

        assertEquals(setOf(HomeSectionType.CONTINUE_WATCHING), fetched.failedSectionTypes)
        assertEquals(listOf(HomeSectionType.NEXT_UP), fetched.sections.map { it.type })
    }

    @Test
    fun `all failed nothing rendered throws the first error`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.continueWatchingResults += Result.failure(RuntimeException("boom"))

        val thrown = runCatching {
            fetcher(fake).fetch(HomeSectionQuery(enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING)))
        }.exceptionOrNull()

        // The assembler records the first error and fetch rethrows it when
        // NOTHING rendered. (Identity is not asserted: kotlinx stack-trace
        // recovery clones the exception crossing the coroutineScope boundary
        // — the pure firstError identity is pinned by HomeSectionsAssemblerTest.)
        assertIs<RuntimeException>(thrown)
        assertEquals("boom", thrown.message)
    }

    @Test
    fun `pinned sections are fetched even with empty enabled sections`() = runTest {
        val fake = FakeHomeSectionSources()
        fake.collectionResults += searchResultOf("p1")
        val pin = PinnedHomeSection(PinnedSectionType.COLLECTION, sourceId = "col1", title = "My Collection")

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(enabledSections = emptySet(), pinnedSections = listOf(pin)),
        )

        // The pin's leaf is the ONLY port call — everything else is disabled.
        assertEquals(listOf("collection:col1:20"), fake.calls)
        val section = fetched.sections.single()
        assertEquals(HomeSectionType.PINNED, section.type)
        assertEquals("pinned_COLLECTION_col1", section.id)
        assertEquals("My Collection", section.title)
        assertEquals(listOf("p1"), section.items.map { it.id })
    }

    @Test
    fun `failing and empty pins are dropped and pin order is preserved`() = runTest {
        val fake = FakeHomeSectionSources()
        // A pin whose leaf THROWS (e.g. a deleted collection) must be dropped.
        fake.throwables["collection:col_bad:20"] = RuntimeException("gone")
        // Results bind at issue order: the failing pin burns a throwaway
        // entry, the EMPTY pin's empty result → row dropped, the GOOD pin's
        // items render.
        fake.collectionResults += Result.success(SearchResult(emptyList(), 0, 0)) // burned by the failing pin
        fake.collectionResults += Result.success(SearchResult(emptyList(), 0, 0)) // empty → dropped
        fake.collectionResults += searchResultOf("p3")
        val pins = listOf(
            PinnedHomeSection(PinnedSectionType.COLLECTION, sourceId = "col_bad", title = "Bad"),
            PinnedHomeSection(PinnedSectionType.PLAYLIST, sourceId = "pl_empty", title = "Empty"),
            PinnedHomeSection(PinnedSectionType.COLLECTION, sourceId = "col_good", title = "Good"),
        )

        val fetched = fetcher(fake).fetch(
            HomeSectionQuery(enabledSections = emptySet(), pinnedSections = pins),
        )

        // Only the non-empty surviving pin renders, in pin order.
        assertEquals(listOf("pinned_COLLECTION_col_good"), fetched.sections.map { it.id })
        assertEquals("Good", fetched.sections.single().title)
        assertEquals(listOf("p3"), fetched.sections.single().items.map { it.id })
    }

    @Test
    fun `back to back fetches serve latest media from the sub-call cache`() = runTest {
        val fake = FakeHomeSectionSources()
        repeat(2) { fake.foldersResults += Result.success(listOf(folder("f1"))) }
        fake.latestResults += Result.success(listOf(item("a1")))

        val f = fetcher(fake)
        val query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.LATEST_MEDIA))
        f.fetch(query)
        val second = f.fetch(query)

        // The second fetch served the folder's latest row from the sub-cache
        // (folders themselves are uncached — one port call per fetch).
        assertEquals(1, fake.calls.count { it.startsWith("latest:") })
        assertEquals(listOf("a1"), latestRows(second).single().items.map { it.id })
    }

    @Test
    fun `force bypasses the sub-call cache read but memoises the pulled rows`() = runTest {
        val fake = FakeHomeSectionSources()
        repeat(3) { fake.foldersResults += Result.success(listOf(folder("f1"))) }
        fake.latestResults += Result.success(listOf(item("stale-row")))
        fake.latestResults += Result.success(listOf(item("pulled-row")))

        val f = fetcher(fake)
        val query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.LATEST_MEDIA))
        f.fetch(query)                          // fetch 1: stale-row cached
        val forced = f.fetch(query, force = true) // bypasses the read: fetch 2
        assertEquals(listOf("pulled-row"), latestRows(forced).single().items.map { it.id })

        // The forced fetch MEMOISED: the next plain fetch hits the cache
        // (still 2 fetches) and serves the PULLED rows, not the pre-pull ones.
        val after = f.fetch(query)
        assertEquals(2, fake.calls.count { it.startsWith("latest:") })
        assertEquals(listOf("pulled-row"), latestRows(after).single().items.map { it.id })
    }

    @Test
    fun `identity switch misses and unknown identity memoises`() = runTest {
        val fake = FakeHomeSectionSources()
        repeat(5) { fake.foldersResults += Result.success(listOf(folder("f1"))) }
        repeat(3) { fake.latestResults += Result.success(listOf(item("row-$it"))) }

        var identity: CacheIdentity? = identityA
        val f = fetcher(fake) { identity }
        val query = HomeSectionQuery(enabledSections = setOf(HomeSectionType.LATEST_MEDIA))

        f.fetch(query)                    // miss under A → fetch 1
        identity = CacheIdentity.ofOrNull("server-2", "user-2")
        f.fetch(query)                    // wrong identity misses by construction → fetch 2
        identity = identityA
        f.fetch(query)                    // hit under A again
        assertEquals(2, fake.calls.count { it.startsWith("latest:") })

        // Unified pre-login semantics: null normalizes to UNKNOWN, which
        // memoises like any identity (wasm previously skipped caching there).
        identity = null
        f.fetch(query)                    // miss under UNKNOWN → fetch 3
        f.fetch(query)                    // hit under UNKNOWN
        assertEquals(3, fake.calls.count { it.startsWith("latest:") })
    }
}
