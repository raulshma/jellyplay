package com.raulshma.jellyplay.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

class MediaPagingSource(
    private val mediaRepository: MediaRepository,
    private val parentId: String? = null,
    private val mediaTypes: List<MediaType>? = null,
    private val genres: List<String>? = null,
    private val years: List<Int>? = null,
    private val studioIds: List<String>? = null,
    private val sortBy: String = "SortName",
    private val sortOrder: String = "Ascending",
    private val tags: List<String>? = null,
    private val playedStatus: com.raulshma.jellyplay.core.model.PlayedStatus? = null,
    private val minRating: Float? = null,
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
                mediaTypes = mediaTypes,
                genres = genres,
                years = years,
                studioIds = studioIds,
                sortBy = sortBy,
                sortOrder = sortOrder,
                startIndex = startIndex,
                limit = pageSize,
                tags = tags,
                playedStatus = playedStatus,
                minRating = minRating,
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
