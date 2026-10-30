package com.raulshma.jellyplay.core.testfixtures

import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.library.HomeSectionSources
import kotlinx.coroutines.CompletableDeferred

/**
 * The canonical [HomeSectionSources] transport double for the home pipeline's
 * suites — the single copy behind the two private twins it replaces
 * (core:network's `HomeSectionsFetcherTest` and core:data's
 * `HomeFeedRealStackContractTest`, whose former local copy carried the
 * "internal to that module and cannot be reused" apology). Consumed today by
 * those two suites plus feature/home's `DiscoverRollContractTest`, whose
 * roll-protocol stack runs the REAL `HomeSectionsFetcher` over this fake.
 *
 * Lives in commonMain (not jvmShared) per this module's source-set rule: the
 * port and every model type its signatures carry are commonMain, and the
 * first consumer is a commonTest lane.
 *
 * Semantics (the union of the two former copies — no redesign at the move):
 *  - Every member records its leaf call under a deterministic key
 *    (`"cw:$limit:$classicRows:$excludeActiveSessions"`,
 *    `"latest:$parentId:$limit:$classicEpisodePool"`,
 *    `"discover:${row.id}:${row.limit}"`, …) and pops the head of its
 *    scripted queue; an EMPTY queue resolves `Result.success(empty)` —
 *    unscripted members render nothing and never throw.
 *  - [gate] parks the FIRST call named [key] before it returns: created by a
 *    test BEFORE the call happens, one gate per call (a later call with the
 *    same name — e.g. the roll op's raw fetch arriving while a fetch parks —
 *    runs ungated). The outcome is bound at ISSUE time (the queue pop); a
 *    registered gate only delays its DELIVERY, so scrambled gate completion
 *    cannot swap which caller gets which scripted result.
 *  - [throwables] makes calls named [key] THROW instead of returning
 *    (fatal-leaf paths, e.g. a failing pin).
 *
 * House rules honored: this is the richest existing copy (the fetcher suite's
 * gates + throwables, the real-stack suite's resolving favorites/genre/studio
 * members) moved verbatim, with the fetcher suite's call-key spellings — the
 * exact strings its pins assert.
 */
class FakeHomeSectionSources : HomeSectionSources {

    /** Leaf calls in issue order, e.g. "latest:f1:16:null". */
    val calls = mutableListOf<String>()

    /**
     * Created by a test BEFORE the call happens; the FIRST call named
     * [key] suspends until completed (one gate per call — a later call with
     * the same name, e.g. the roll op's raw fetch arriving while a fetch
     * parks, runs ungated).
     */
    private val returnGates = mutableMapOf<String, ArrayDeque<CompletableDeferred<Unit>>>()

    /** Pre-register a completion gate for the next call named [key]. */
    fun gate(key: String): CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { returnGates.getOrPut(key) { ArrayDeque() }.addLast(it) }

    /** Calls named [key] THROW this instead of returning (fatal-leaf paths, e.g. a failing pin). */
    val throwables = mutableMapOf<String, Throwable>()

    val continueWatchingResults = ArrayDeque<Result<List<MediaItem>>>()
    val continueReadingResults = ArrayDeque<Result<List<MediaItem>>>()
    val nextUpResults = ArrayDeque<Result<List<MediaItem>>>()
    val upcomingResults = ArrayDeque<Result<List<MediaItem>>>()
    val foldersResults = ArrayDeque<Result<List<LibraryFolder>>>()
    val latestResults = ArrayDeque<Result<List<MediaItem>>>()
    val similarResults = ArrayDeque<Result<List<MediaItem>>>()
    val suggestionsResults = ArrayDeque<Result<SearchResult>>()
    val collectionResults = ArrayDeque<Result<SearchResult>>()
    val favoritesResults = ArrayDeque<Result<SearchResult>>()
    val genreResults = ArrayDeque<Result<SearchResult>>()
    val studioResults = ArrayDeque<Result<SearchResult>>()
    val discoverRowResults = ArrayDeque<Result<List<MediaItem>>>()

    private suspend fun <T> resolve(queue: ArrayDeque<Result<T>>, key: String, empty: () -> T): Result<T> {
        calls += key
        // The outcome is bound at ISSUE time; a registered gate only
        // delays its DELIVERY (so scrambled gate completion cannot swap
        // which caller gets which scripted result). The gate is consumed:
        // one registered gate holds exactly one call.
        val scripted = queue.removeFirstOrNull() ?: Result.success(empty())
        returnGates[key]?.removeFirstOrNull()?.await()
        throwables[key]?.let { throw it }
        return scripted
    }

    override suspend fun getContinueWatching(limit: Int, classicRows: Boolean, excludeActiveSessions: Boolean): Result<List<MediaItem>> =
        resolve(continueWatchingResults, "cw:$limit:$classicRows:$excludeActiveSessions") { emptyList() }

    override suspend fun getContinueReading(limit: Int): Result<List<MediaItem>> =
        resolve(continueReadingResults, "cr:$limit") { emptyList() }

    override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>> =
        resolve(nextUpResults, "nu:$limit:$enableRewatching:$maxDays") { emptyList() }

    override suspend fun getUpcoming(userId: String?, parentId: String?, limit: Int): Result<List<MediaItem>> =
        resolve(upcomingResults, "upcoming:$userId:$parentId:$limit") { emptyList() }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> =
        resolve(foldersResults, "folders") { emptyList() }

    override suspend fun getLatestMedia(parentId: String, limit: Int, classicEpisodePool: Int?): Result<List<MediaItem>> =
        resolve(latestResults, "latest:$parentId:$limit:$classicEpisodePool") { emptyList() }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        resolve(similarResults, "similar:$itemId:$limit") { emptyList() }

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> =
        resolve(suggestionsResults, "suggestions:$limit") { SearchResult(emptyList(), 0, 0) }

    override suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int): Result<SearchResult> =
        resolve(collectionResults, "collection:$collectionId:$limit") { SearchResult(emptyList(), 0, 0) }

    override suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult> =
        resolve(favoritesResults, "favorites:$limit:$startIndex") { SearchResult(emptyList(), 0, 0) }

    override suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> =
        resolve(genreResults, "genre:$genreId:$startIndex:$limit") { SearchResult(emptyList(), 0, 0) }

    override suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> =
        resolve(studioResults, "studio:$studioId:$startIndex:$limit") { SearchResult(emptyList(), 0, 0) }

    override suspend fun getDiscoverRowItems(row: DiscoverRowConfig): Result<List<MediaItem>> =
        resolve(discoverRowResults, "discover:${row.id}:${row.limit}") { emptyList() }
}
