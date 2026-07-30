package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem

interface PlaylistRepository {

    suspend fun getPlaylists(limit: Int = 50): Result<List<Playlist>>

    suspend fun getPlaylistItems(playlistId: String, startIndex: Int = 0, limit: Int = 50): Result<List<PlaylistItem>>

    suspend fun createPlaylist(name: String, overview: String? = null, itemIds: List<String> = emptyList(), mediaType: MediaType = MediaType.AUDIO): Result<String>

    suspend fun updatePlaylist(playlistId: String, name: String? = null, overview: String? = null, isPublic: Boolean? = null): Result<Unit>

    suspend fun deletePlaylist(playlistId: String): Result<Unit>

    suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit>

    suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit>

    suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit>
}
