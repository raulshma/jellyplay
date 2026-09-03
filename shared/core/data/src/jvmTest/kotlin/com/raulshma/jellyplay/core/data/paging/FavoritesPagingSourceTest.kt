package com.raulshma.jellyplay.core.data.paging

import androidx.paging.PagingSource
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

/**
 * Pins [FavoritesPagingSource]'s seam: the `mediaTypes` selection and the
 * startIndex/limit math forwarded to [MediaRepository.getFavorites], the
 * next/prev key arithmetic (append while `startIndex + items.size <
 * totalRecordCount`, prevKey clamped at 0, null on both ends), and failure
 * mapping to [PagingSource.LoadResult.Error].
 */
class FavoritesPagingSourceTest {

    private val repository: MediaRepository = mockk()

    private fun mediaItem(i: Int) = MediaItem(id = "f$i", name = "Fav $i", mediaType = MediaType.MOVIE)

    @Test
    fun `first page loads at startIndex 0 with null prevKey`() = runTest {
        coEvery {
            repository.getFavorites(mediaTypes = null, limit = 30, startIndex = 0)
        } returns Result.success(
            SearchResult(items = (0 until 30).map { mediaItem(it) }, totalRecordCount = 45, startIndex = 0),
        )

        val result = FavoritesPagingSource(repository).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertEquals(30, page.data.size)
        assertNull(page.prevKey)
        assertEquals(30, page.nextKey)
        coVerify(exactly = 1) { repository.getFavorites(mediaTypes = null, limit = 30, startIndex = 0) }
    }

    @Test
    fun `mediaTypes selection passes through to getFavorites`() = runTest {
        val mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES)
        coEvery {
            repository.getFavorites(mediaTypes = mediaTypes, limit = 30, startIndex = 0)
        } returns Result.success(
            SearchResult(items = (0 until 30).map { mediaItem(it) }, totalRecordCount = 45, startIndex = 0),
        )

        FavoritesPagingSource(repository, mediaTypes = mediaTypes).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        coVerify(exactly = 1) { repository.getFavorites(mediaTypes = mediaTypes, limit = 30, startIndex = 0) }
    }

    @Test
    fun `append continues from the key and clamps prevKey at 0`() = runTest {
        coEvery {
            repository.getFavorites(any(), any(), any())
        } returns Result.success(
            SearchResult(items = (0 until 15).map { mediaItem(it) }, totalRecordCount = 46, startIndex = 30),
        )

        val result = FavoritesPagingSource(repository).load(
            PagingSource.LoadParams.Append(key = 30, loadSize = 30, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertEquals(45, page.nextKey) // 30 + 15 = 45 < 46
        assertEquals(0, page.prevKey) // max(0, 30 - 30)
    }

    @Test
    fun `final favorites page terminates`() = runTest {
        coEvery {
            repository.getFavorites(any(), any(), any())
        } returns Result.success(
            SearchResult(items = (0 until 15).map { mediaItem(it) }, totalRecordCount = 45, startIndex = 30),
        )

        val result = FavoritesPagingSource(repository).load(
            PagingSource.LoadParams.Append(key = 30, loadSize = 15, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertNull(page.nextKey) // 30 + 15 == 45
        assertEquals(15, page.prevKey)
    }

    @Test
    fun `getFavorites failure maps to LoadResult_Error with the original exception`() = runTest {
        val failure = IOException("favorites endpoint down")
        coEvery {
            repository.getFavorites(any(), any(), any())
        } returns Result.failure(failure)

        val result = FavoritesPagingSource(repository).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val error = assertIs<PagingSource.LoadResult.Error<Int, MediaItem>>(result)
        assertSame(failure, error.throwable)
    }
}
