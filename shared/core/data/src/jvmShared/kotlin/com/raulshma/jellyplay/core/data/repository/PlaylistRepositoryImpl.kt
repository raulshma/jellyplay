package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.network.api.LibraryApiClient

/**
 *  MediaRepository facade split: the eight PlaylistRepository members moved
 * verbatim from [MediaRepositoryImpl].
 *
 * The two reads are uncached forwards over [LibraryApiClient] (the
 * PlaybackRepositoryImpl family-seam precedent — the family single composes
 * the same impl the JellyfinApiClient union delegates to, so the wire
 * behavior is byte-identical). The six edits self-invalidate the playlist's
 * ONE cached projection — its detail entry — through
 * [MediaRepositoryInternals.detailCaches], the Koin single shared with
 * [MediaRepositoryImpl]: the drop reaches the same epoch-guarded
 * [DetailCacheGroup] the media repo's getMediaDetail reads go through, so
 * an edit made through THIS surface invalidates a detail cached through the
 * media surface (and vice versa) — one instance, not two hand-synced
 * caches.
 *
 * Declared divergence: the primary constructor is `internal` (its
 * [MediaRepositoryInternals] parameter is module-machinery — every caller,
 * the dataJvmModule Koin definition and this module's test suites, is
 * inside the module).
 */
class PlaylistRepositoryImpl internal constructor(
    private val libraryApiClient: LibraryApiClient,
    private val internals: MediaRepositoryInternals,
) : PlaylistRepository {

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = libraryApiClient.getPlaylists(limit)

    override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> =
        libraryApiClient.getPlaylistItems(playlistId, startIndex, limit)

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> =
        // Plan 08: playlist edits self-invalidate. getPlaylistItems is an
        // uncached passthrough, so the one cached projection of a playlist is
        // its detail entry — one detailCaches.invalidateItem(playlistId) per edit
        // (PlaylistDetailViewModel used to drop it by hand on refresh).
        libraryApiClient.createPlaylist(name, overview, itemIds, mediaType)
            .onSuccess { internals.detailCaches.invalidateItem(it) }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> =
        libraryApiClient.updatePlaylist(playlistId, name, overview, isPublic)
            .onSuccess { internals.detailCaches.invalidateItem(playlistId) }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        libraryApiClient.deletePlaylist(playlistId)
            .onSuccess { internals.detailCaches.invalidateItem(playlistId) }

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> =
        libraryApiClient.addItemsToPlaylist(playlistId, itemIds)
            .onSuccess { internals.detailCaches.invalidateItem(playlistId) }

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> =
        libraryApiClient.removeItemsFromPlaylist(playlistId, entryIds)
            .onSuccess { internals.detailCaches.invalidateItem(playlistId) }

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        libraryApiClient.movePlaylistItem(playlistId, entryId, newIndex)
            .onSuccess { internals.detailCaches.invalidateItem(playlistId) }
}
