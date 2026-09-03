package com.raulshma.jellyplay.core.data.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pins [MediaPagingSource]'s pagination math over the mocked
 * [MediaRepository.getMediaItems] seam:
 *
 *  - `params.key ?: 0` becomes `startIndex`, and `params.loadSize` becomes the
 *    `limit` (Refresh with a null key = the first page);
 *  - `nextKey` is `startIndex + items.size` only while more records remain
 *    (`< totalRecordCount`), null on the boundary-exact last page;
 *  - `prevKey` is `max(0, startIndex - loadSize)` for any non-zero start, null
 *    on the first page;
 *  - repository failure (Result.failure) and thrown exceptions both map to
 *    [PagingSource.LoadResult.Error] instead of crashing the pager;
 *  - [MediaPagingSource.getRefreshKey] derives the reload anchor from the
 *    closest page's prev/next key.
 */
class MediaPagingSourceTest {

    private val repository: MediaRepository = mockk()

    private fun source(
        parentId: String? = null,
        filters: LibraryFilters = LibraryFilters(),
        kindFilter: ItemKindFilter = ItemKindFilter.TOP_LEVEL,
    ) = MediaPagingSource(
        mediaRepository = repository,
        parentId = parentId,
        filters = filters,
        kindFilter = kindFilter,
    )

    private fun mediaItem(i: Int) = MediaItem(id = "i$i", name = "Item $i", mediaType = MediaType.MOVIE)

    private fun stubResult(
        startIndex: Int,
        count: Int,
        totalRecordCount: Int,
    ): SearchResult {
        val items = (0 until count).map { mediaItem(startIndex + it) }
        return SearchResult(items = items, totalRecordCount = totalRecordCount, startIndex = startIndex)
    }

    private fun <K : Any> PagingSource.LoadResult<K, MediaItem>.page(): PagingSource.LoadResult.Page<K, MediaItem> =
        assertIs<PagingSource.LoadResult.Page<K, MediaItem>>(this)

    // ── load: Refresh / Append / Prepend key math ───────────────────────

    @Test
    fun `Refresh with null key loads the first page at startIndex 0`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 0, count = 30, totalRecordCount = 90))

        val result = source().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
        assertEquals(30, page.data.size)
        assertNull(page.prevKey)
        assertEquals(30, page.nextKey)
    }

    @Test
    fun `Append uses the key as startIndex and sizes the previous key by loadSize`() = runTest {
        val startSlot = slot<Int>()
        val limitSlot = slot<Int>()
        coEvery {
            repository.getMediaItems(any(), any(), any(), capture(startSlot), capture(limitSlot), any())
        } returns Result.success(stubResult(startIndex = 60, count = 30, totalRecordCount = 91))

        val result = source().load(
            PagingSource.LoadParams.Append(key = 60, loadSize = 30, placeholdersEnabled = false),
        )

        assertEquals(60, startSlot.captured)
        assertEquals(30, limitSlot.captured)
        val page = result.page<Int>()
        assertEquals(90, page.nextKey) // 60 + 30 = 90 < 91
        assertEquals(30, page.prevKey) // 60 - 30
    }

    @Test
    fun `Prepend near the top clamps prevKey to 0`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 10, count = 20, totalRecordCount = 90))

        val result = source().load(
            PagingSource.LoadParams.Prepend(key = 10, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
        assertEquals(0, page.prevKey) // max(0, 10 - 30)
        assertEquals(30, page.nextKey)
    }

    @Test
    fun `boundary-exact last page has a null nextKey`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 60, count = 30, totalRecordCount = 90))

        val result = source().load(
            PagingSource.LoadParams.Append(key = 60, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
        assertNull(page.nextKey) // 60 + 30 == 90, not <
        assertEquals(30, page.prevKey)
    }

    @Test
    fun `partial page beyond totalRecordCount also terminates`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 80, count = 5, totalRecordCount = 85))

        val result = source().load(
            PagingSource.LoadParams.Append(key = 80, loadSize = 30, placeholdersEnabled = false),
        )

        assertNull(result.page<Int>().nextKey)
    }

    @Test
    fun `empty result terminates paging`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 0, count = 0, totalRecordCount = 0))

        val result = source().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
        assertEquals(0, page.data.size)
        assertNull(page.nextKey)
        assertNull(page.prevKey)
    }

    // ── load: pass-through of the source's construction parameters ──────

    @Test
    fun `parentId filters and kindFilter pass through to the repository`() = runTest {
        val filters = LibraryFilters(genres = listOf("Sci-Fi"))
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.success(stubResult(startIndex = 0, count = 1, totalRecordCount = 1))
        val pagingSource = source(parentId = "lib-1", filters = filters)

        pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        )

        coVerify(exactly = 1) {
            repository.getMediaItems(
                parentId = "lib-1",
                filters = filters,
                studioIds = null,
                startIndex = 0,
                limit = 50,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }
    }

    // ── load: failure mapping ───────────────────────────────────────────

    @Test
    fun `repository failure maps to LoadResult_Error carrying the exception`() = runTest {
        val failure = IOException("server unreachable")
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } returns Result.failure(failure)

        val result = source().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val error = assertIs<PagingSource.LoadResult.Error<Int, MediaItem>>(result)
        assertSame(failure, error.throwable)
    }

    @Test
    fun `repository throwing maps to LoadResult_Error instead of crashing`() = runTest {
        coEvery {
            repository.getMediaItems(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("connection pool shut down")

        val result = source().load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        assertTrue(result is PagingSource.LoadResult.Error<Int, MediaItem>)
    }

    // ── getRefreshKey ───────────────────────────────────────────────────

    @Test
    fun `getRefreshKey derives prevKey plus 1 from the closest page`() {
        val page = PagingSource.LoadResult.Page(
            data = (0 until 30).map(::mediaItem),
            prevKey = 0,
            nextKey = 30,
        )
        val state = PagingState(
            pages = listOf(page),
            anchorPosition = 10,
            config = PagingConfig(pageSize = 30),
            leadingPlaceholderCount = 0,
        )

        assertEquals(1, source().getRefreshKey(state))
    }

    @Test
    fun `getRefreshKey falls back to nextKey minus 1 when prevKey is null`() {
        val page = PagingSource.LoadResult.Page(
            data = (0 until 30).map(::mediaItem),
            prevKey = null,
            nextKey = 30,
        )
        val state = PagingState(
            pages = listOf(page),
            anchorPosition = 5,
            config = PagingConfig(pageSize = 30),
            leadingPlaceholderCount = 0,
        )

        assertEquals(29, source().getRefreshKey(state))
    }

    @Test
    fun `getRefreshKey is null without an anchor position`() {
        val state = PagingState(
            pages = listOf(
                PagingSource.LoadResult.Page(data = (0 until 30).map(::mediaItem), prevKey = 0, nextKey = 30),
            ),
            anchorPosition = null,
            config = PagingConfig(pageSize = 30),
            leadingPlaceholderCount = 0,
        )

        assertNull(source().getRefreshKey(state))
    }
}
