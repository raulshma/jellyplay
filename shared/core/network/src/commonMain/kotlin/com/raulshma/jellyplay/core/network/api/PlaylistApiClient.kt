package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.SearchResult

/**
 * The playlist family seam of [LibraryApiClient]: the eight `/Playlists`
 * reads/writes only the playlist surfaces call (the PlaylistRepositoryImpl
 * over-the-impl pattern — the same [com.raulshma.jellyplay.core.network.api.LibraryApiClientImpl]
 * single implements this seam alongside the wide interface, so a consumer
 * narrows without a second client or a family supertype creeping back onto
 * [LibraryApiClient]). Members moved verbatim off [LibraryApiClient]; the
 * [JellyfinApiClient][com.raulshma.jellyplay.core.network.JellyfinApiClient]
 * union carries both.
 */
interface PlaylistApiClient {
    suspend fun getPlaylists(limit: Int = 50): Result<List<Playlist>>
    suspend fun getPlaylistItems(playlistId: String, startIndex: Int = 0, limit: Int = 50): Result<List<PlaylistItem>>
    suspend fun createPlaylist(name: String, overview: String? = null, itemIds: List<String> = emptyList(), mediaType: MediaType = MediaType.AUDIO): Result<String>
    suspend fun updatePlaylist(playlistId: String, name: String? = null, overview: String? = null, isPublic: Boolean? = null): Result<Unit>
    suspend fun deletePlaylist(playlistId: String): Result<Unit>
    suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit>
    suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit>
    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit>
}

/**
 * The collection family seam of [LibraryApiClient]: the four BoxSet
 * reads/writes the detail screen's "Add to Collection" paths and the
 * collection browse drive (same one-impl-many-seams shape as
 * [PlaylistApiClient]).
 */
interface CollectionApiClient {
    suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    /**
     * Lists the user's collections (Jellyfin BoxSet items) for the detail
     * screen's "Add to Collection" picker. Remote-only — collections are a
     * server-side library construct. Returns a lightweight summary per
     * collection (id, name, item count, primary image tag).
     */
    suspend fun getCollections(limit: Int = 100): Result<List<CollectionSummary>>

    /**
     * Creates a new collection (BoxSet) via Jellyfin's `/Collections` endpoint,
     * optionally seeded with [itemIds]. Returns the new collection's id.
     * Remote-only.
     */
    suspend fun createCollection(name: String, itemIds: List<String> = emptyList()): Result<String>

    /**
     * Adds the given item ids to an existing collection via Jellyfin's
     * `/Collections/{collectionId}/Items` endpoint. Remote-only.
     */
    suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit>
}
