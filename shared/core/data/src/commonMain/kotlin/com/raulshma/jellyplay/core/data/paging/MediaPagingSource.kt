package com.raulshma.jellyplay.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem

class MediaPagingSource(
    private val mediaRepository: MediaRepository,
    private val parentId: String? = null,
    private val filters: LibraryFilters = LibraryFilters(),
    private val studioIds: List<String>? = null,
    private val kindFilter: ItemKindFilter = ItemKindFilter.TOP_LEVEL,
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
            val result = mediaRepository.getMediaItems(
                parentId = parentId,
                filters = filters,
                studioIds = studioIds,
                startIndex = startIndex,
                limit = pageSize,
                kindFilter = kindFilter,
            )

            result.fold(
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
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }
}
