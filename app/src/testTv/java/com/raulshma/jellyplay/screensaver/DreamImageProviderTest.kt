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

    /** itemId → Primary image URL (photos); missing entry means blank. */
    private val primaryImages = mutableMapOf<String, String>()

    private val imageUrlProvider = object : ImageUrlProvider {
        override fun getImageUrl(itemId: String, maxWidth: Int?) = primaryImages[itemId] ?: ""
        override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?) = ""
        override fun getBackdropUrl(itemId: String, maxWidth: Int) = backdrops[itemId] ?: ""
        override fun getLogoUrl(itemId: String) = ""
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
        assertEquals("https://server/movie1.jpg", byId.getValue("movie-1").imageUrl)
    }

    @Test
    fun `fetchImages drops items without a backdrop and unknown media types`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(
                items = listOf(
                    item("movie-1", "Has Backdrop", MediaType.MOVIE),
                    item("movie-blank", "No Backdrop", MediaType.MOVIE), // provider returns blank URL
                    item("photo-1", "A Photo", MediaType.PHOTO),          // no Primary image in the fake → dropped
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
    fun `fetchImages applies the local parental rating cap`() = runTest {
        // The rating test's ids need backdrop entries too — a blank URL drops
        // the item before the rating filter's result can be observed.
        backdrops["movie-g"] = "https://server/movie-g.jpg"
        backdrops["movie-pg13"] = "https://server/movie-pg13.jpg"
        backdrops["movie-r"] = "https://server/movie-r.jpg"
        backdrops["movie-unrated"] = "https://server/movie-unrated.jpg"
        val items = listOf(
            item("movie-g", "G Film", MediaType.MOVIE).copy(officialRating = "G"),
            item("movie-pg13", "PG-13 Film", MediaType.MOVIE).copy(officialRating = "PG-13"),
            item("movie-r", "R Film", MediaType.MOVIE).copy(officialRating = "R"),
            item("movie-unrated", "Unrated Film", MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(items = items, totalRecordCount = items.size, startIndex = 0),
        )

        // No local cap = unfiltered (the server policy's cap is applied upstream).
        assertEquals(
            setOf("movie-g", "movie-pg13", "movie-r", "movie-unrated"),
            provider.fetchImages(setOf(DreamImageCategory.MOVIES)).map { it.itemId }.toSet(),
        )
        // PG-13 cap: G and PG-13 pass, R is dropped, unrated passes (the helper's rule).
        assertEquals(
            setOf("movie-g", "movie-pg13", "movie-unrated"),
            provider.fetchImages(setOf(DreamImageCategory.MOVIES), maxParentalRating = 13).map { it.itemId }.toSet(),
        )
        // G cap (canonical age 0): only G and unrated survive.
        assertEquals(
            setOf("movie-g", "movie-unrated"),
            provider.fetchImages(setOf(DreamImageCategory.MOVIES), maxParentalRating = 0).map { it.itemId }.toSet(),
        )
    }

    @Test
    fun `fetchImages queries photos and maps the primary image url`() = runTest {
        primaryImages["photo-1"] = "https://server/photo1-primary.jpg"
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(items = listOf(item("photo-1", "A Photo", MediaType.PHOTO)), totalRecordCount = 1, startIndex = 0),
        )

        val images = provider.fetchImages(setOf(DreamImageCategory.PHOTOS))

        coVerify {
            mediaRepository.getMediaItems(
                filters = match<LibraryFilters> {
                    it.mediaTypes == listOf(MediaType.PHOTO) && it.sortBy == SortOption.RANDOM
                },
                limit = 50,
            )
        }
        assertEquals(1, images.size)
        assertEquals(DreamImageCategory.PHOTOS, images.single().type)
        // Photos surface their Primary image, not the backdrop.
        assertEquals("https://server/photo1-primary.jpg", images.single().imageUrl)
    }

    @Test
    fun `fetchImages unions photo media types with the other categories`() = runTest {
        coEvery { mediaRepository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        provider.fetchImages(setOf(DreamImageCategory.MOVIES, DreamImageCategory.PHOTOS))

        coVerify {
            mediaRepository.getMediaItems(
                filters = match<LibraryFilters> {
                    it.mediaTypes.toSet() == setOf(MediaType.MOVIE, MediaType.PHOTO) &&
                        it.sortBy == SortOption.RANDOM
                },
                limit = 50,
            )
        }
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
