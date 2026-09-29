package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.network.api.LibraryApiClient

/**
 *  The uncached browse-read families of [MediaRepository] — [MediaExtrasReads]
 * (item-attached intros/extras), [MediaBrowseReads] (people + tag facets) and
 * [MediaCollectionReads] (the SearchResult-shaped items queries) — moved
 * verbatim from [MediaRepositoryImpl] (one-line forwards there, one-line
 * forwards here — the families own NO cache state, so this impl takes no
 * [MediaRepositoryInternals]; the LiveTvRepositoryImpl shape). One impl class
 * carries all three seams over the same client single — the same
 * one-impl-many-seams shape the media single uses for [MusicCatalogue] /
 * [UserDataWriteOperations] — so each consumer injects only the seam it reads.
 *
 * Ctor narrowed to the ONE API family client that owns every route
 * ([LibraryApiClient] — the family-seam precedent, not the JellyfinApiClient
 * union): the family single composes the same impl the union used to delegate
 * to, so the calls hit the same wire byte-identically. The repository's two
 * paged projections (getMediaItemsPaged / getFavoritesPaged) stayed on
 * [MediaRepositoryImpl] and reach the same client directly.
 */
class MediaUncachedReadsImpl(
    private val libraryApiClient: LibraryApiClient,
) : MediaExtrasReads,
    MediaBrowseReads,
    MediaCollectionReads {

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> =
        libraryApiClient.getIntros(itemId)

    override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> =
        libraryApiClient.getSpecialFeatures(itemId)

    override suspend fun getPeople(searchTerm: String?, limit: Int): Result<List<PersonRef>> =
        libraryApiClient.getPeople(searchTerm, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        libraryApiClient.getItemsByPerson(personId, limit)

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = libraryApiClient.getTags(parentId, startIndex, limit)

    override suspend fun getMediaItems(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        startIndex: Int,
        limit: Int,
        kindFilter: com.raulshma.jellyplay.core.model.ItemKindFilter,
    ): Result<SearchResult> = libraryApiClient.getMediaItems(
        parentId = parentId,
        filters = filters,
        studioIds = studioIds,
        startIndex = startIndex,
        limit = limit,
        kindFilter = kindFilter,
    )

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = libraryApiClient.getFavorites(mediaTypes, limit, startIndex)

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> =
        libraryApiClient.getSearchSuggestions(limit)
}
