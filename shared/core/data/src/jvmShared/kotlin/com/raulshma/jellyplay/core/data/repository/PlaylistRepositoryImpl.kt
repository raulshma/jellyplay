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

    /**
     * The edits' shared invalidation suffix (Plan 08): playlist edits
     * self-invalidate the playlist's ONE cached projection — its detail
     * entry — through [MediaRepositoryInternals.detailCaches], the Koin
     * single shared with [MediaRepositoryImpl] (one instance, not two
     * hand-synced caches). getPlaylistItems above is an uncached passthrough,
     * so the detail entry is the only thing to drop
     * (PlaylistDetailViewModel used to drop it by hand on refresh).
     */
    private suspend fun <T> editing(playlistId: String, block: suspend () -> Result<T>): Result<T> =
        block().onSuccess { internals.detailCaches.invalidateItem(playlistId) }

    /** [editing] for [createPlaylist], whose invalidation key is the CREATED id — the success value, not a parameter. */
    private suspend fun creating(block: suspend () -> Result<String>): Result<String> =
        block().onSuccess { internals.detailCaches.invalidateItem(it) }

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> = creating { libraryApiClient.createPlaylist(name, overview, itemIds, mediaType) }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = editing(playlistId) { libraryApiClient.updatePlaylist(playlistId, name, overview, isPublic) }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> =
        editing(playlistId) { libraryApiClient.deletePlaylist(playlistId) }

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> =
        editing(playlistId) { libraryApiClient.addItemsToPlaylist(playlistId, itemIds) }

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> =
        editing(playlistId) { libraryApiClient.removeItemsFromPlaylist(playlistId, entryIds) }

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        editing(playlistId) { libraryApiClient.movePlaylistItem(playlistId, entryId, newIndex) }
}
