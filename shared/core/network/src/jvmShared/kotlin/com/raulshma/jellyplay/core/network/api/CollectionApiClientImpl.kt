package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.SearchResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*

/**
 * The [CollectionApiClient] family adapter: the four BoxSet reads/writes the
 * detail screen's "Add to Collection" paths and the collection browse drive,
 * moved verbatim off [LibraryApiClientImpl]. Like its playlist sibling
 * ([PlaylistApiClientImpl]) it closes over nothing but the SAME
 * [JellyfinApiEngine] single the wide client uses — no caches, no fetcher, no
 * favorite-flag store — so Koin constructs it over the engine alone and the
 * wide client forwards its one collection obligation here; the split is pure
 * relocation.
 *
 * One deliberate backward hop: [LibraryApiClientImpl] keeps satisfying
 * [com.raulshma.jellyplay.core.network.library.HomeSectionSources] (the home
 * fetcher's pinned collection sections read collection members through it),
 * and it discharges that one obligation by forwarding to this adapter — the
 * family's members live in exactly one place.
 */
class CollectionApiClientImpl(
    private val engine: JellyfinApiEngine,
) : CollectionApiClient {

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = engine.withApi { api ->
        // The query stays hand-assembled (a fixed single-path shape, no spec);
        // only the page tail adopts the shared fold ([toSearchResult] — the
        // exact SearchResult assembly this used to hand-copy, with the
        // call's own startIndex echoed back).
        val response = api.itemsApi.getItems(
            parentId = collectionId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = LIST_ITEM_FIELDS,
        ).content
        response.toSearchResult(engine.currentMaxParentalRating, startIndex)
    }

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> =
        engine.withApi { api ->
            // Collections are BoxSet items. Mirrors the getPlaylists query
            // (includeItemTypes + recursive) but targets BOX_SET. CHILD_COUNT is
            // requested so the picker can show "N items" per collection.
            val response = api.itemsApi.getItems(
                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                limit = limit,
                recursive = true,
                fields = listOf(ItemFields.CHILD_COUNT),
            ).content
            response.items.map { item ->
                CollectionSummary(
                    id = item.id.toString(),
                    name = item.name ?: "",
                    itemCount = item.childCount ?: 0,
                    imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
                )
            }
        }

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> =
        engine.withApi { api ->
            val result = api.collectionApi.createCollection(
                name = name,
                ids = itemIds,
            ).content
            result.id.toString()
        }

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> =
        engine.withApi { api ->
            api.collectionApi.addToCollection(
                collectionId = collectionId.toUUID(),
                ids = itemIds.map { it.toUUID() },
            ).content
            Unit
        }
}
