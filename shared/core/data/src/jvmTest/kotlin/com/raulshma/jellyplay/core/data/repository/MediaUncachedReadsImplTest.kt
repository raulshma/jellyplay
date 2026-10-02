package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Port of the two forwarding pins the uncached browse-read families left
 * [MediaRepositoryImplTest] with (the getSpecialFeatures delegation pair) plus
 * one per remaining seam member: the extracted [MediaUncachedReadsImpl] is the
 * LiveTvRepositoryImpl shape — one client, pure forwards, no cache state — so
 * the only behavior to pin is the argument-exact delegation per family
 * (MediaExtrasReads / MediaBrowseReads / MediaCollectionReads).
 */
class MediaUncachedReadsImplTest {

    private val apiClient: LibraryApiClient = mockk(relaxed = true)

    private lateinit var reads: MediaUncachedReadsImpl

    @BeforeTest
    fun setup() {
        reads = MediaUncachedReadsImpl(apiClient)
    }

    // ── MediaExtrasReads ──────────────────────────────────────────────

    @Test
    fun `getSpecialFeatures delegates to apiClient and maps items`() = runTest {
        val extras = listOf(
            MediaItem(id = "extra-1", name = "Making Of", mediaType = MediaType.MOVIE),
            MediaItem(id = "extra-2", name = "Deleted Scenes", mediaType = MediaType.MOVIE),
        )
        coEvery { apiClient.getSpecialFeatures("item-1") } returns Result.success(extras)

        val result = reads.getSpecialFeatures("item-1")

        assertTrue(result.isSuccess)
        assertEquals(extras, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSpecialFeatures("item-1") }
    }

    @Test
    fun `getSpecialFeatures empty when apiClient returns empty`() = runTest {
        coEvery { apiClient.getSpecialFeatures("item-1") } returns Result.success(emptyList())

        val result = reads.getSpecialFeatures("item-1")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull().isNullOrEmpty())
    }

    @Test
    fun `getIntros delegates to apiClient`() = runTest {
        val intros = listOf(MediaItem(id = "intro-1", name = "Trailer", mediaType = MediaType.MOVIE))
        coEvery { apiClient.getIntros("item-1") } returns Result.success(intros)

        val result = reads.getIntros("item-1")

        assertEquals(intros, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getIntros("item-1") }
    }

    // ── MediaBrowseReads ──────────────────────────────────────────────

    @Test
    fun `getPeople delegates searchTerm and limit`() = runTest {
        val people = listOf(PersonRef(id = "p1", name = "Ada"))
        coEvery { apiClient.getPeople("ada", 30) } returns Result.success(people)

        val result = reads.getPeople("ada", 30)

        assertEquals(people, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getPeople("ada", 30) }
    }

    @Test
    fun `getItemsByPerson delegates personId and limit`() = runTest {
        val filmography = listOf(mediaItem("m1"))
        coEvery { apiClient.getItemsByPerson("p1", 50) } returns Result.success(filmography)

        val result = reads.getItemsByPerson("p1", 50)

        assertEquals(filmography, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getItemsByPerson("p1", 50) }
    }

    @Test
    fun `getTags delegates parent and paging window`() = runTest {
        coEvery { apiClient.getTags(null, 0, 100) } returns Result.success(listOf("kids"))

        val result = reads.getTags()

        assertEquals(listOf("kids"), result.getOrNull())
        coVerify(exactly = 1) { apiClient.getTags(null, 0, 100) }
    }

    // ── MediaCollectionReads ──────────────────────────────────────────

    @Test
    fun `getMediaItems delegates every query dimension`() = runTest {
        val page = SearchResult(items = listOf(mediaItem("m1")), totalRecordCount = 1, startIndex = 0)
        val filters = LibraryFilters(mediaTypes = listOf(MediaType.MOVIE))
        coEvery {
            apiClient.getMediaItems(parentId = "lib-1", filters = filters, studioIds = listOf("s1"), startIndex = 20, limit = 10, kindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter(includeEpisodes = true))
        } returns Result.success(page)

        val result = reads.getMediaItems(
            parentId = "lib-1",
            filters = filters,
            studioIds = listOf("s1"),
            startIndex = 20,
            limit = 10,
            kindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter(includeEpisodes = true),
        )

        assertEquals(page, result.getOrNull())
        coVerify(exactly = 1) {
            apiClient.getMediaItems(parentId = "lib-1", filters = filters, studioIds = listOf("s1"), startIndex = 20, limit = 10, kindFilter = com.raulshma.jellyplay.core.model.ItemKindFilter(includeEpisodes = true))
        }
    }

    @Test
    fun `getFavorites delegates mediaTypes and paging window`() = runTest {
        val page = SearchResult(items = listOf(mediaItem("m1")), totalRecordCount = 1, startIndex = 0)
        coEvery { apiClient.getFavorites(listOf(MediaType.AUDIO), 50, 20) } returns Result.success(page)

        val result = reads.getFavorites(mediaTypes = listOf(MediaType.AUDIO), limit = 50, startIndex = 20)

        assertEquals(page, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getFavorites(listOf(MediaType.AUDIO), 50, 20) }
    }

    @Test
    fun `getSearchSuggestions delegates limit`() = runTest {
        val page = SearchResult(items = listOf(mediaItem("m1")), totalRecordCount = 1, startIndex = 0)
        coEvery { apiClient.getSearchSuggestions(20) } returns Result.success(page)

        val result = reads.getSearchSuggestions(20)

        assertEquals(page, result.getOrNull())
        coVerify(exactly = 1) { apiClient.getSearchSuggestions(20) }
    }
}

private fun mediaItem(id: String) = MediaItem(
    id = id,
    name = id,
    mediaType = MediaType.MOVIE,
)
