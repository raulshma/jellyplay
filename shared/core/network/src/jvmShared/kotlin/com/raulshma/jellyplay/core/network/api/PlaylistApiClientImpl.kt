package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.isAudioType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaType as SdkMediaType
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*

/**
 * The [PlaylistApiClient] family adapter: the eight `/Playlists` reads/writes,
 * moved verbatim off [LibraryApiClientImpl] (whose monolith they only
 * ballooned — this family shares nothing with the library read paths or the
 * home hot path: no caches, no LRU, no fetcher, no favorite-flag store).
 * Constructed over the SAME [JellyfinApiEngine] single the wide client uses
 * (Koin passes the engine; the union's [com.raulshma.jellyplay.core.network.JellyfinApiClientImpl]
 * delegates this seam here), so the wire calls, the retry chassis
 * ([JellyfinApiEngine.withApi]) and the atomic-session identity reads are the
 * ones this client has always answered with — the split is pure relocation.
 */
class PlaylistApiClientImpl(
    private val engine: JellyfinApiEngine,
) : PlaylistApiClient {

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = engine.withApi { api ->
        val response = api.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            limit = limit,
            recursive = true,
            fields = LIST_ITEM_FIELDS + listOf(ItemFields.CAN_DELETE, ItemFields.DATE_CREATED),
        ).content
        val currentUserId = engine.currentUserId()
        response.items.map { item ->
            Playlist(
                id = item.id.toString(),
                name = item.name ?: "",
                overview = item.overview,
                itemCount = item.childCount ?: 0,
                imageTag = item.imageTags?.get(ImageType.PRIMARY)?.toString(),
                userId = currentUserId,
                isReadOnly = false,
                isPublic = false,
                canEdit = true,
                canDelete = item.canDelete ?: true,
                createdAt = item.dateCreated?.toString(),
            )
        }
    }

    override suspend fun getPlaylistItems(
        playlistId: String,
        startIndex: Int,
        limit: Int,
    ): Result<List<PlaylistItem>> = engine.withApi { api ->
        // The parental-rating filter (toFilteredMediaItems) is intentionally not applied: PlaylistItem
        // does not carry an officialRating, and the server enforces playlist
        // ACLs plus parental controls on the underlying item query.
        val response = api.itemsApi.getItems(
            parentId = playlistId.toUUID(),
            startIndex = startIndex,
            limit = limit,
            recursive = true,
            fields = LIST_ITEM_FIELDS,
        ).content
        response.items.map { item ->
            PlaylistItem(
                id = item.id.toString(),
                playlistItemId = item.playlistItemId?.toString(),
                name = item.name ?: "",
                artist = item.albumArtist ?: item.artistItems?.firstOrNull()?.name,
                album = item.album,
                mediaType = item.type?.toMediaType() ?: MediaType.UNKNOWN,
                runTimeTicks = item.runTimeTicks,
            )
        }
    }

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> = engine.withApi { api ->
        val userId = engine.currentUserId()?.toUUID()
        // Jellyfin tags a playlist with a single media type so the server can
        // sort/limit it correctly. Music callers (the default) keep AUDIO;
        // video detail screens pass VIDEO. Without this, a playlist created
        // from a movie would be mislabelled AUDIO.
        val sdkMediaType = if (mediaType.isAudioType) SdkMediaType.AUDIO else SdkMediaType.VIDEO
        val dto = CreatePlaylistDto(
            name = name,
            ids = itemIds.map { it.toUUID() },
            userId = userId,
            mediaType = sdkMediaType,
            users = emptyList(),
            isPublic = false,
        )
        val response = api.playlistsApi.createPlaylist(dto).content
        response.id?.toString() ?: throw IllegalStateException("Created playlist has no id")
    }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = engine.withApi { api ->
        val dto = UpdatePlaylistDto(
            name = name,
            isPublic = isPublic,
        )
        api.playlistsApi.updatePlaylist(playlistId.toUUID(), dto).content
        Unit
    }

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = engine.withApi { api ->
        api.request(
            method = HttpMethod.DELETE,
            pathTemplate = "Items/$playlistId",
        )
        Unit
    }

    override suspend fun addItemsToPlaylist(
        playlistId: String,
        itemIds: List<String>,
    ): Result<Unit> = engine.withApi { api ->
        val userId = engine.currentUserId()?.toUUID()
        api.playlistsApi.addItemToPlaylist(
            playlistId = playlistId.toUUID(),
            ids = itemIds.map { it.toUUID() },
            userId = userId,
        ).content
        Unit
    }

    override suspend fun removeItemsFromPlaylist(
        playlistId: String,
        entryIds: List<String>,
    ): Result<Unit> = engine.withApi { api ->
        api.playlistsApi.removeItemFromPlaylist(
            playlistId = playlistId,
            entryIds = entryIds,
        ).content
        Unit
    }

    override suspend fun movePlaylistItem(
        playlistId: String,
        entryId: String,
        newIndex: Int,
    ): Result<Unit> = engine.withApi { api ->
        api.playlistsApi.moveItem(
            playlistId = playlistId,
            itemId = entryId,
            newIndex = newIndex,
        ).content
        Unit
    }
}
