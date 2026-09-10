package com.raulshma.jellyplay.core.data.paging

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * The ONE Jellyfin paging source: all page math lives here exactly once —
 * `params.key ?: 0` becomes the fetch's `startIndex`, `params.loadSize` its
 * `limit`; `nextKey` is `startIndex + items.size` only while more records
 * remain (`< totalRecordCount`, so a boundary-exact last page terminates);
 * `prevKey` is `max(0, startIndex - loadSize)` for any non-zero start, null
 * on the first page; a failing [fetch] (Result.failure or a throw) maps to
 * [PagingSource.LoadResult.Error] instead of crashing the pager — but a
 * CANCELLED fetch rethrows: cancellation is never converted to a load error.
 *
 * [fetch] is the only per-flavor part (library browse / favorites / search);
 * the blank-query guard for search lives in [MediaRepository.searchPagingSource].
 * Order-preserving: pages carry the items exactly as the repository returned
 * them.
 */
internal class JellyfinPagingSource(
    private val fetch: suspend (startIndex: Int, limit: Int) -> Result<SearchResult>,
) : PagingSource<Int, MediaItem>() {

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val startIndex = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            fetch(startIndex, pageSize).fold(
                onSuccess = { searchResult ->
                    val items = searchResult.items
                    val totalRecordCount = searchResult.totalRecordCount

                    val nextKey = if (startIndex + items.size < totalRecordCount) {
                        startIndex + items.size
                    } else {
                        null
                    }

                    val prevKey = if (startIndex > 0) {
                        maxOf(0, startIndex - pageSize)
                    } else {
                        null
                    }

                    LoadResult.Page(
                        data = items,
                        prevKey = prevKey,
                        nextKey = nextKey,
                    )
                },
                onFailure = { exception ->
                    LoadResult.Error(exception)
                },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }
}

/**
 * The single [Pager] shape for every paged media flow in
 * [MediaRepository] — one [PagingConfig] instead of the three hand-copied
 * `Pager(PagingConfig(...))` blocks it replaces. The factory is re-invoked by
 * paging on every invalidation, so each closure must capture its call site's
 * parameters (they do).
 */
internal fun pagedMediaPager(
    pagingSourceFactory: () -> JellyfinPagingSource,
): Flow<PagingData<MediaItem>> = Pager(
    config = PagingConfig(
        pageSize = PAGE_SIZE,
        enablePlaceholders = false,
        prefetchDistance = PREFETCH_DISTANCE,
    ),
    pagingSourceFactory = pagingSourceFactory,
).flow

// Shared by every [pagedMediaPager] consumer — one page-shape policy for the
// whole app (library browse, favorites and search load with the same sizes).
private const val PAGE_SIZE = 50
private const val PREFETCH_DISTANCE = 20

/**
 * Search flavor of [JellyfinPagingSource]: a blank query short-circuits to an
 * empty TERMINAL page (`prevKey`/`nextKey` null) without a repository
 * round-trip — a whitespace-only search bar must not fire network calls on
 * every keystroke-backed pager refresh.
 */
internal fun MediaRepository.searchPagingSource(
    query: String,
    filters: LibraryFilters = LibraryFilters(),
): JellyfinPagingSource = JellyfinPagingSource { startIndex, limit ->
    if (query.isBlank()) {
        Result.success(SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0))
    } else {
        search(query = query, filters = filters, limit = limit, startIndex = startIndex)
    }
}
