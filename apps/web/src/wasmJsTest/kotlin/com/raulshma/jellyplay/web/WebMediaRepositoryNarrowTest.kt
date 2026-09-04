package com.raulshma.jellyplay.web

import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Browser-free unit cover for [WebMediaRepositoryNarrow] (wave 16C) — the web
 * shell's deliberately NARROW MediaRepository binding. Invariants pinned:
 *
 *  - THE ONE SERVED MEMBER: findItemByProviderId (the SeerrDetail cross-link)
 *    is a pure passthrough — the exact (provider, id) pair reaches the client
 *    and the client's Result comes back untouched, success AND failure alike
 *    (the VM's best-effort "Available" cross-link depends on getting the
 *    failure, never a crash).
 *  - OFF-WEB MEMBERS THROW LOUDLY: members across ALL SIX super-interfaces
 *    (MediaRepository, LiveTv, SyncPlay, Newsletter, Playlist, Lyrics) throw
 *    [UnsupportedOperationException] carrying the member name — the "never a
 *    silently-wrong answer" contract.
 *  - CONSTRUCTION IS SIDE-EFFECT FREE: the userDataChanges getter must throw
 *    LAZILY (on access), never at construction — the eager-initializer
 *    regression the real browser pass caught (Koin's InstanceCreationException
 *    exploded SeerrDetailViewModel's ctor dep before the screen ever loaded).
 *    Construction succeeding here is that regression's gate.
 *
 * Runs on the Kotlin Node runner (wasmJsNodeTest): the client is a
 * hand-rolled fake (no mocking library exists on wasmJs) whose off-web
 * members are mechanical throw stubs written against the interface's exact
 * signatures — a future interface change fails to COMPILE here and forces
 * the narrow repository to be re-reviewed.
 */
class WebMediaRepositoryNarrowTest {

    /** Only findItemByProviderId answers; every other member throws. */
    private class FakeLibraryApiClient : LibraryApiClient {
        val lookupCalls = mutableListOf<Pair<String, String>>()
        var lookupResult: Result<String?> = Result.success(null)

        override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> {
            lookupCalls += provider to id
            return lookupResult
        }

        private fun unused(): Nothing = throw UnsupportedOperationException("unused in WebMediaRepositoryNarrowTest")

        override suspend fun getHomeSections(query: HomeSectionQuery, force: Boolean): Result<HomeSectionsResult> = unused()
        override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getNextUp(limit: Int, enableRewatching: Boolean, maxDays: Int): Result<List<MediaItem>> = unused()
        override suspend fun getContinueWatching(limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> = unused()
        override suspend fun getMediaItems(
            parentId: String?,
            filters: LibraryFilters,
            studioIds: List<String>?,
            startIndex: Int,
            limit: Int,
            searchTerm: String?,
            kindFilter: ItemKindFilter,
        ): Result<SearchResult> = unused()
        override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> = unused()
        override suspend fun getIntros(itemId: String): Result<List<MediaItem>> = unused()
        override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> = unused()
        override suspend fun getSearchHints(query: String, mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult> = unused()
        override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = unused()
        override suspend fun getGenres(parentId: String?, startIndex: Int, limit: Int): Result<List<Genre>> = unused()
        override suspend fun getItemsByGenre(genreId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> = unused()
        override suspend fun getStudios(parentId: String?, startIndex: Int, limit: Int): Result<List<Studio>> = unused()
        override suspend fun getItemsByStudio(studioId: String, mediaTypes: List<MediaType>?, startIndex: Int, limit: Int): Result<SearchResult> = unused()
        override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = unused()
        override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getRecommendations(limit: Int, seeds: List<MediaItem>): Result<RecommendationResult> = unused()
        override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> = unused()
        override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> = unused()
        override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = unused()
        override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> = unused()
        override suspend fun getAllEpisodes(seriesId: String): Result<List<MediaItem>> = unused()
        override suspend fun getCollectionItems(collectionId: String, startIndex: Int, limit: Int): Result<SearchResult> = unused()
        override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> = unused()
        override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> = unused()
        override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> = unused()
        override suspend fun getTags(parentId: String?, startIndex: Int, limit: Int): Result<List<String>> = unused()
        override suspend fun getFavorites(mediaTypes: List<MediaType>?, limit: Int, startIndex: Int): Result<SearchResult> = unused()
        override suspend fun getLyrics(itemId: String): Result<LyricsResult> = unused()
        override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = unused()
        override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> = unused()
        override suspend fun createPlaylist(name: String, overview: String?, itemIds: List<String>, mediaType: MediaType): Result<String> = unused()
        override suspend fun updatePlaylist(playlistId: String, name: String?, overview: String?, isPublic: Boolean?): Result<Unit> = unused()
        override suspend fun deletePlaylist(playlistId: String): Result<Unit> = unused()
        override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> = unused()
        override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> = unused()
        override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> = unused()
        override suspend fun markPlayed(itemId: String): Result<Unit> = unused()
        override suspend fun markUnplayed(itemId: String): Result<Unit> = unused()
        override suspend fun toggleFavorite(itemId: String, currentIsFavorite: Boolean?): Result<Boolean> = unused()
        override suspend fun setFavorite(itemId: String, isFavorite: Boolean): Result<Unit> = unused()
        override fun getImageUrl(itemId: String, imageType: String, maxWidth: Int?, imageIndex: Int?, tag: String?): String = unused()
        override fun getBackdropImageUrl(itemId: String, maxWidth: Int, tag: String?): String = unused()
        override suspend fun getChildItemImageUrls(parentId: String, limit: Int): List<String> = unused()
    }

    private fun repository(client: FakeLibraryApiClient = FakeLibraryApiClient()) =
        WebMediaRepositoryNarrow(libraryApiClient = client) to client

    // ── the one served member ──────────────────────────────────────────────

    @Test
    fun `findItemByProviderId forwards the provider pair and returns the client result`() = runTest {
        val (repo, client) = repository()
        client.lookupResult = Result.success("jellyfin-item-7")

        val result = repo.findItemByProviderId(provider = "tmdb", id = "42")

        assertEquals(listOf("tmdb" to "42"), client.lookupCalls, "the exact (provider, id) pair must reach the client")
        assertEquals(Result.success("jellyfin-item-7"), result)
    }

    @Test
    fun `findItemByProviderId passes a lookup failure through instead of crashing`() = runTest {
        val (repo, client) = repository()
        client.lookupResult = Result.failure(RuntimeException("no matching item"))

        val result = repo.findItemByProviderId(provider = "tvdb", id = "9")

        assertTrue(result.isFailure, "the VM's best-effort cross-link needs the failure, never a throw")
        assertEquals("no matching item", result.exceptionOrNull()?.message)
    }

    // ── construction side-effect freedom (the lazy-throw regression gate) ──

    @Test
    fun `construction never touches the off-web surface`() {
        // The SeerrDetailViewModel ctor resolves this binding through Koin —
        // an eager off-web throw here was the wave-16C browser crash.
        repository()
    }

    @Test
    fun `userDataChanges throws on access, not at construction`() {
        val (repo, _) = repository()
        assertFailsWith<UnsupportedOperationException> { repo.userDataChanges }
    }

    // ── the loud off-web throw contract, one member per super-interface ────

    @Test
    fun `off-web home-sections member throws loudly`() = runTest {
        val (repo, _) = repository()
        assertFailsWith<UnsupportedOperationException> { repo.getHomeSections(HomeSectionQuery(), force = false) }
    }

    @Test
    fun `off-web detail search browse and paging members throw loudly`() = runTest {
        val (repo, _) = repository()
        assertFailsWith<UnsupportedOperationException> { repo.getMediaDetail("item-1", force = false) }
        assertFailsWith<UnsupportedOperationException> { repo.search("query", LibraryFilters(), limit = 10, startIndex = 0) }
        assertFailsWith<UnsupportedOperationException> {
            repo.getMediaItems(null, LibraryFilters(), null, 0, 10, ItemKindFilter.TOP_LEVEL)
        }
        assertFailsWith<UnsupportedOperationException> { repo.searchPaged("query", LibraryFilters()) }
        assertFailsWith<UnsupportedOperationException> { repo.toggleFavorite("item-1") }
    }

    @Test
    fun `off-web live-tv syncplay newsletter playlist and lyrics members throw loudly`() = runTest {
        val (repo, _) = repository()
        assertFailsWith<UnsupportedOperationException> {
            repo.getLiveTvChannels(0, 10, addCurrentProgram = false, enableFavoriteSorting = false, isFavorite = null)
        }
        assertFailsWith<UnsupportedOperationException> { repo.getSyncPlayGroups() }
        assertFailsWith<UnsupportedOperationException> { repo.getNewsletterData("2026-01-01", 5) }
        assertFailsWith<UnsupportedOperationException> { repo.getPlaylists(limit = 10) }
        assertFailsWith<UnsupportedOperationException> { repo.getLyrics("item-1") }
    }

    @Test
    fun `the thrown message names the escaped member and the one served member`() = runTest {
        val (repo, _) = repository()
        val error = assertFailsWith<UnsupportedOperationException> {
            repo.getHomeSections(HomeSectionQuery(), force = false)
        }
        assertTrue(
            error.message?.contains("'getHomeSections'") == true,
            "the message must name the escaped member, got: ${error.message}",
        )
        assertTrue(
            error.message?.contains("findItemByProviderId") == true,
            "the message must point at the one served member, got: ${error.message}",
        )
    }
}
