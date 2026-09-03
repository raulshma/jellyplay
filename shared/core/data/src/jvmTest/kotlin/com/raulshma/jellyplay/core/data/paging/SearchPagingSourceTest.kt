package com.raulshma.jellyplay.core.data.paging

import androidx.paging.PagingSource
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import io.mockk.Called
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
 * Pins [SearchPagingSource]'s two special behaviours on top of the shared
 * pagination math:
 *
 *  - a blank query short-circuits to an empty terminal page **without
 *    touching the repository** (no wasted search round-trip on an empty box);
 *  - a real query forwards query + filters + startIndex/limit math to
 *    [MediaRepository.search] and maps its key arithmetic like
 *    [MediaPagingSource] (append key = startIndex + items.size while records
 *    remain; prevKey clamped at 0; failure → [PagingSource.LoadResult.Error]).
 */
class SearchPagingSourceTest {

    private val repository: MediaRepository = mockk()

    private fun mediaItem(i: Int) = MediaItem(id = "s$i", name = "Hit $i", mediaType = MediaType.MOVIE)

    @Test
    fun `blank query returns an empty terminal page without calling the repository`() = runTest {
        val result = SearchPagingSource(repository, query = "   ").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertEquals(0, page.data.size)
        assertNull(page.prevKey)
        assertNull(page.nextKey)
        coVerify { repository wasNot Called }
    }

    @Test
    fun `query and filters plus startIndex math pass through to search`() = runTest {
        val filters = LibraryFilters(mediaTypes = listOf(MediaType.MOVIE))
        coEvery {
            repository.search(query = "batman", filters = filters, limit = 30, startIndex = 30)
        } returns Result.success(
            SearchResult(items = (0 until 30).map { mediaItem(it) }, totalRecordCount = 100, startIndex = 30),
        )
        val source = SearchPagingSource(repository, query = "batman", filters = filters)

        val result = source.load(
            PagingSource.LoadParams.Append(key = 30, loadSize = 30, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertEquals(30, page.data.size)
        assertEquals(60, page.nextKey) // 30 + 30 < 100
        assertEquals(0, page.prevKey) // max(0, 30 - 30)
        coVerify(exactly = 1) {
            repository.search(query = "batman", filters = filters, limit = 30, startIndex = 30)
        }
    }

    @Test
    fun `search failure maps to LoadResult_Error with the original exception`() = runTest {
        val failure = IOException("search backend down")
        coEvery {
            repository.search(any(), any(), any(), any())
        } returns Result.failure(failure)

        val result = SearchPagingSource(repository, query = "batman").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val error = assertIs<PagingSource.LoadResult.Error<Int, MediaItem>>(result)
        assertSame(failure, error.throwable)
    }

    @Test
    fun `last page terminates with a null nextKey`() = runTest {
        coEvery {
            repository.search(any(), any(), any(), any())
        } returns Result.success(
            SearchResult(items = (0 until 10).map { mediaItem(it) }, totalRecordCount = 40, startIndex = 30),
        )

        val result = SearchPagingSource(repository, query = "batman").load(
            PagingSource.LoadParams.Append(key = 30, loadSize = 30, placeholdersEnabled = false),
        )

        val page = assertIs<PagingSource.LoadResult.Page<Int, MediaItem>>(result)
        assertNull(page.nextKey) // 30 + 10 == 40
    }
}
