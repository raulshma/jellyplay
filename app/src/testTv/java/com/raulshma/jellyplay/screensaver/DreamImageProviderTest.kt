package com.raulshma.jellyplay.screensaver

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SortOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamImageProviderTest {

    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    /** itemId → backdrop URL; missing entry means "no backdrop" (blank). */
    private val backdrops = mutableMapOf(
        "movie-1" to "https://server/movie1.jpg",
        "series-1" to "https://server/series1.jpg",
        "album-1" to "https://server/album1.jpg",
    )

    private val imageUrlProvider = object : ImageUrlProvider {
        override fun getImageUrl(itemId: String, maxWidth: Int?) = backdrops[itemId] ?: ""
        override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?) = ""
        override fun getBackdropUrl(itemId: String, maxWidth: Int) = backdrops[itemId] ?: ""
    }

    private val provider = DreamImageProvider(mediaRepository, imageUrlProvider, context)

    private fun item(id: String, name: String, type: MediaType) =
        MediaItem(id = id, name = name, mediaType = type)

    @Test
    fun `fetchImages maps items to dream images with categories`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(
                    item("movie-1", "A Movie", MediaType.MOVIE),
                    item("series-1", "A Series", MediaType.SERIES),
                    item("album-1", "An Album", MediaType.ALBUM),
                ),
                totalRecordCount = 3,
                startIndex = 0,
            ),
        )

        val images = provider.fetchImages(setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES, DreamImageCategory.MUSIC))

        assertEquals(3, images.size)
        val byId = images.associateBy { it.itemId }
        assertEquals(DreamImageCategory.MOVIES, byId.getValue("movie-1").type)
        assertEquals(DreamImageCategory.SERIES, byId.getValue("series-1").type)
        assertEquals(DreamImageCategory.MUSIC, byId.getValue("album-1").type)
        assertEquals("https://server/movie1.jpg", byId.getValue("movie-1").backdropUrl)
    }

    @Test
    fun `fetchImages drops items without a backdrop and unknown media types`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(
                    item("movie-1", "Has Backdrop", MediaType.MOVIE),
                    item("movie-blank", "No Backdrop", MediaType.MOVIE), // provider returns blank URL
                    item("photo-1", "A Photo", MediaType.PHOTO),          // not a dream category
                    item("", "Blank Id", MediaType.MOVIE),                // filtered by id check
                ),
                totalRecordCount = 4,
                startIndex = 0,
            ),
        )

        val images = provider.fetchImages(setOf(DreamImageCategory.MOVIES))

        assertEquals(listOf("movie-1"), images.map { it.itemId })
    }

    @Test
    fun `fetchImages queries random sort with union of category media types`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        provider.fetchImages(setOf(DreamImageCategory.MOVIES, DreamImageCategory.MUSIC))

        coVerify {
            mediaRepository.getMediaItems(
                filters = match<LibraryFilters> {
                    it.mediaTypes.toSet() == setOf(MediaType.MOVIE, MediaType.AUDIO, MediaType.ALBUM) &&
                        it.sortBy == SortOption.RANDOM
                },
                limit = 50,
            )
        }
    }

    @Test
    fun `fetchImages returns empty when fetch fails`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(Exception("offline"))

        val images = provider.fetchImages(setOf(DreamImageCategory.MOVIES))

        assertTrue(images.isEmpty())
    }
}
