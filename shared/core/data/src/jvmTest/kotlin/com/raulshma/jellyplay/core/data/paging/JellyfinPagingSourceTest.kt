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
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * The ONE suite pinning [JellyfinPagingSource]'s pagination math — the
 * arithmetic the three per-flavor sources (library / favorites / search) used
 * to hand-copy. The boundary cases (totalRecordCount edge, prevKey clamp,
 * error mapping) run over EVERY flavor's fetch seam; the per-flavor
 * pass-throughs (library parameters, favorites mediaTypes, search query plus
 * the blank-query guard) are pinned on top. Search builds its source through
 * the production [MediaRepository.searchPagingSource] extension, so the guard
 * is exercised exactly where it lives.
 */
class JellyfinPagingSourceTest {

    private val repository: MediaRepository = mockk()

    private enum class Flavor { MEDIA, FAVORITES, SEARCH }

    /** Builds each flavor through the same wiring its production call site uses. */
    private fun source(flavor: Flavor): PagingSource<Int, MediaItem> = when (flavor) {
        Flavor.MEDIA -> JellyfinPagingSource { startIndex, limit ->
            repository.getMediaItems(
                parentId = null,
                filters = LibraryFilters(),
                studioIds = null,
                startIndex = startIndex,
                limit = limit,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }
        Flavor.FAVORITES -> JellyfinPagingSource { startIndex, limit ->
            repository.getFavorites(mediaTypes = null, limit = limit, startIndex = startIndex)
        }
        Flavor.SEARCH -> repository.searchPagingSource(query = "batman")
    }

    private fun stubSuccess(
        flavor: Flavor,
        startIndex: Int,
        count: Int,
        totalRecordCount: Int,
    ) {
        val items = (0 until count).map { mediaItem(startIndex + it) }
        val result = Result.success(SearchResult(items = items, totalRecordCount = totalRecordCount, startIndex = startIndex))
        when (flavor) {
            Flavor.MEDIA -> coEvery { repository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns result
            Flavor.FAVORITES -> coEvery { repository.getFavorites(any(), any(), any()) } returns result
            Flavor.SEARCH -> coEvery { repository.search(any(), any(), any(), any()) } returns result
        }
    }

    private fun stubFailure(flavor: Flavor, failure: Throwable) {
        when (flavor) {
            Flavor.MEDIA -> coEvery { repository.getMediaItems(any(), any(), any(), any(), any(), any()) } returns Result.failure(failure)
            Flavor.FAVORITES -> coEvery { repository.getFavorites(any(), any(), any()) } returns Result.failure(failure)
            Flavor.SEARCH -> coEvery { repository.search(any(), any(), any(), any()) } returns Result.failure(failure)
        }
    }

    private fun mediaItem(i: Int) = MediaItem(id = "i$i", name = "Item $i", mediaType = MediaType.MOVIE)

    private fun <K : Any> PagingSource.LoadResult<K, MediaItem>.page(): PagingSource.LoadResult.Page<K, MediaItem> =
        assertIs<PagingSource.LoadResult.Page<K, MediaItem>>(this)

    // ── shared boundary math, pinned once across the flavors ────────────

    @Test
    fun `Refresh with null key loads the first page at startIndex 0`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 0, count = 30, totalRecordCount = 90)

            val result = source(flavor).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
            )

            val page = result.page<Int>()
            assertEquals(30, page.data.size, "$flavor")
            assertNull(page.prevKey, "$flavor")
            assertEquals(30, page.nextKey, "$flavor")
        }
    }

    @Test
    fun `Append uses the key as startIndex and sizes the previous key by loadSize`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 60, count = 30, totalRecordCount = 91)

            val result = source(flavor).load(
                PagingSource.LoadParams.Append(key = 60, loadSize = 30, placeholdersEnabled = false),
            )

            val page = result.page<Int>()
            assertEquals(90, page.nextKey, "$flavor") // 60 + 30 = 90 < 91
            assertEquals(30, page.prevKey, "$flavor") // 60 - 30
        }
    }

    @Test
    fun `Prepend near the top clamps prevKey to 0`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 10, count = 20, totalRecordCount = 90)

            val result = source(flavor).load(
                PagingSource.LoadParams.Prepend(key = 10, loadSize = 30, placeholdersEnabled = false),
            )

            val page = result.page<Int>()
            assertEquals(0, page.prevKey, "$flavor") // max(0, 10 - 30)
            assertEquals(30, page.nextKey, "$flavor")
        }
    }

    @Test
    fun `boundary-exact last page has a null nextKey`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 60, count = 30, totalRecordCount = 90)

            val result = source(flavor).load(
                PagingSource.LoadParams.Append(key = 60, loadSize = 30, placeholdersEnabled = false),
            )

            val page = result.page<Int>()
            assertNull(page.nextKey, "$flavor") // 60 + 30 == 90, not <
            assertEquals(30, page.prevKey, "$flavor")
        }
    }

    @Test
    fun `partial page beyond totalRecordCount also terminates`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 80, count = 5, totalRecordCount = 85)

            val result = source(flavor).load(
                PagingSource.LoadParams.Append(key = 80, loadSize = 30, placeholdersEnabled = false),
            )

            assertNull(result.page<Int>().nextKey, "$flavor")
        }
    }

    @Test
    fun `empty result terminates paging`() = runTest {
        for (flavor in Flavor.values()) {
            stubSuccess(flavor, startIndex = 0, count = 0, totalRecordCount = 0)

            val result = source(flavor).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
            )

            val page = result.page<Int>()
            assertEquals(0, page.data.size, "$flavor")
            assertNull(page.nextKey, "$flavor")
            assertNull(page.prevKey, "$flavor")
        }
    }

    @Test
    fun `repository failure maps to LoadResult_Error carrying the exception`() = runTest {
        for (flavor in Flavor.values()) {
            val failure = IOException("server unreachable")
            stubFailure(flavor, failure)

            val result = source(flavor).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
            )

            val error = assertIs<PagingSource.LoadResult.Error<Int, MediaItem>>(result)
            assertSame(failure, error.throwable, "$flavor")
        }
    }

    @Test
    fun `repository throwing maps to LoadResult_Error instead of crashing`() = runTest {
        for (flavor in Flavor.values()) {
            when (flavor) {
                Flavor.MEDIA -> coEvery {
                    repository.getMediaItems(any(), any(), any(), any(), any(), any())
                } throws IllegalStateException("connection pool shut down")
                Flavor.FAVORITES -> coEvery {
                    repository.getFavorites(any(), any(), any())
                } throws IllegalStateException("connection pool shut down")
                Flavor.SEARCH -> coEvery {
                    repository.search(any(), any(), any(), any())
                } throws IllegalStateException("connection pool shut down")
            }

            val result = source(flavor).load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
            )

            assertTrue(result is PagingSource.LoadResult.Error<Int, MediaItem>, "$flavor")
        }
    }

    @Test
    fun `a cancelled fetch rethrows - cancellation never becomes a load error`() = runTest {
        val cancelled = JellyfinPagingSource { _, _ -> throw CancellationException("page load cancelled") }

        assertFailsWith<CancellationException> {
            cancelled.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
            )
        }
    }

    // ── per-flavor pass-through ─────────────────────────────────────────

    @Test
    fun `parentId filters and kindFilter pass through to the repository`() = runTest {
        val filters = LibraryFilters(genres = listOf("Sci-Fi"))
        stubSuccess(Flavor.MEDIA, startIndex = 0, count = 1, totalRecordCount = 1)
        val pagingSource = JellyfinPagingSource { startIndex, limit ->
            repository.getMediaItems(
                parentId = "lib-1",
                filters = filters,
                studioIds = null,
                startIndex = startIndex,
                limit = limit,
                kindFilter = ItemKindFilter.TOP_LEVEL,
            )
        }

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

    @Test
    fun `mediaTypes selection passes through to getFavorites`() = runTest {
        val mediaTypes = listOf(MediaType.MOVIE, MediaType.SERIES)
        coEvery {
            repository.getFavorites(mediaTypes = mediaTypes, limit = 30, startIndex = 0)
        } returns Result.success(
            SearchResult(items = (0 until 30).map(::mediaItem), totalRecordCount = 45, startIndex = 0),
        )
        val pagingSource = JellyfinPagingSource { startIndex, limit ->
            repository.getFavorites(mediaTypes = mediaTypes, limit = limit, startIndex = startIndex)
        }

        pagingSource.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        coVerify(exactly = 1) { repository.getFavorites(mediaTypes = mediaTypes, limit = 30, startIndex = 0) }
    }

    @Test
    fun `blank query returns an empty terminal page without calling the repository`() = runTest {
        val result = repository.searchPagingSource(query = "   ").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
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
            SearchResult(items = (0 until 30).map(::mediaItem), totalRecordCount = 100, startIndex = 30),
        )
        val pagingSource = repository.searchPagingSource(query = "batman", filters = filters)

        val result = pagingSource.load(
            PagingSource.LoadParams.Append(key = 30, loadSize = 30, placeholdersEnabled = false),
        )

        val page = result.page<Int>()
        assertEquals(30, page.data.size)
        assertEquals(60, page.nextKey) // 30 + 30 < 100
        assertEquals(0, page.prevKey) // max(0, 30 - 30)
        coVerify(exactly = 1) {
            repository.search(query = "batman", filters = filters, limit = 30, startIndex = 30)
        }
    }

    // ── getRefreshKey (defined once on JellyfinPagingSource) ────────────

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

        assertEquals(1, source(Flavor.MEDIA).getRefreshKey(state))
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

        assertEquals(29, source(Flavor.MEDIA).getRefreshKey(state))
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

        assertNull(source(Flavor.MEDIA).getRefreshKey(state))
    }
}
