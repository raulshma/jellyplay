package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.LibraryResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AudioLibraryBrowser(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val streamingQualityProvider: () -> StreamingQuality,
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector,
) {
    /**
     * Builds the [MediaLibrarySession] every audio path uses — the initial
     * session and the post-crossfade rebuild. Both call sites share this one
     * construction path so the media service host never sees a plain
     * [MediaSession] downgrade: [JellyPlayPlaybackService.onGetSession] casts
     * the active session with `as? MediaLibrarySession`, and a plain session
     * makes the cast return null (now-playing notification + headset buttons
     * die until app restart). Keep this the single source of truth for audio.
     */
    internal fun buildMediaSession(context: Context, player: Player, sessionId: String): MediaLibrarySession =
        MediaLibrarySession.Builder(context, player, callback)
            .setId(sessionId)
            .build()

    val callback: MediaLibrarySession.Callback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("JellyPlay")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId("ROOT")
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return resolveFuture {
                val list = mutableListOf<MediaItem>()
                when {
                    parentId == "ROOT" -> {
                        list.add(buildBrowsableFolder("ARTISTS", "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("ALBUMS", "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("PLAYLISTS", "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("FAVORITES", "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("DOWNLOADS", "Downloads", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                    }
                    parentId == "ARTISTS" -> {
                        val result = mediaRepository.getMediaItems(
                            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                                mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.ARTIST),
                            ),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { artist ->
                            list.add(mapArtistToMediaItem(artist))
                        }
                    }
                    parentId == "ALBUMS" -> {
                        val result = mediaRepository.getMediaItems(
                            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                                mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.ALBUM),
                            ),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { album ->
                            list.add(mapAlbumToMediaItem(album))
                        }
                    }
                    parentId == "PLAYLISTS" -> {
                        val result = mediaRepository.getPlaylists(limit = pageSize).getOrNull()
                        result?.forEach { playlist ->
                            list.add(mapPlaylistToMediaItem(playlist))
                        }
                    }
                    parentId == "FAVORITES" -> {
                        val result = mediaRepository.getFavorites(
                            mediaTypes = listOf(com.raulshma.jellyplay.core.model.MediaType.MUSIC, com.raulshma.jellyplay.core.model.MediaType.AUDIO),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                    parentId == "DOWNLOADS" -> {
                        val downloads = try {
                            downloadRepository.getAllDownloads().first()
                        } catch (_: Exception) {
                            emptyList()
                        }
                        val completedAudioDownloads = downloads.filter {
                            it.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED &&
                                    (it.mediaType == com.raulshma.jellyplay.core.model.MediaType.MUSIC ||
                                     it.mediaType == com.raulshma.jellyplay.core.model.MediaType.AUDIO)
                        }
                        val start = (page * pageSize).coerceAtMost(completedAudioDownloads.size)
                        val end = ((page + 1) * pageSize).coerceAtMost(completedAudioDownloads.size)
                        if (start < end) {
                            completedAudioDownloads.subList(start, end).forEach { dl ->
                                list.add(mapDownloadToPlayableMediaItem(dl))
                            }
                        }
                    }
                    parentId.startsWith("ARTIST_|") -> {
                        val artistId = parentId.removePrefix("ARTIST_|")
                        val albums = mediaRepository.getArtistAlbums(artistId, limit = pageSize).getOrNull() ?: emptyList()
                        albums.forEach { album ->
                            list.add(mapAlbumToMediaItem(album))
                        }
                    }
                    parentId.startsWith("ALBUM_|") -> {
                        val albumId = parentId.removePrefix("ALBUM_|")
                        val tracks = mediaRepository.getAlbumTracks(albumId).getOrNull() ?: emptyList()
                        tracks.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                    parentId.startsWith("PLAYLIST_|") -> {
                        val playlistId = parentId.removePrefix("PLAYLIST_|")
                        val playlistItems = mediaRepository.getPlaylistItems(playlistId, startIndex = page * pageSize, limit = pageSize).getOrNull() ?: emptyList()
                        playlistItems.forEach { pi ->
                            list.add(mapPlaylistItemToPlayableMediaItem(pi))
                        }
                    }
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(list), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return resolveFuture {
                val playable = buildPlayableMediaItem(mediaId)
                if (playable != null) {
                    LibraryResult.ofItem(playable, null)
                } else {
                    val item = when {
                        mediaId.startsWith("ARTIST_|") -> {
                            val id = mediaId.removePrefix("ARTIST_|")
                            mediaRepository.getMediaDetail(id).getOrNull()?.let { mapArtistToMediaItem(it.item) }
                        }
                        mediaId.startsWith("ALBUM_|") -> {
                            val id = mediaId.removePrefix("ALBUM_|")
                            mediaRepository.getMediaDetail(id).getOrNull()?.let { mapAlbumToMediaItem(it.item) }
                        }
                        mediaId.startsWith("PLAYLIST_|") -> {
                            val id = mediaId.removePrefix("PLAYLIST_|")
                            val playlists = mediaRepository.getPlaylists().getOrNull() ?: emptyList()
                            playlists.find { it.id == id }?.let { mapPlaylistToMediaItem(it) }
                        }
                        else -> null
                    }
                    if (item != null) {
                        LibraryResult.ofItem(item, null)
                    } else {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    }
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return resolveFuture {
                val resolvedList = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    val mediaId = item.mediaId
                    when {
                        mediaId.startsWith("ARTIST_|") -> {
                            val artistId = mediaId.removePrefix("ARTIST_|")
                            val albums = mediaRepository.getArtistAlbums(artistId).getOrNull() ?: emptyList()
                            for (album in albums) {
                                val tracks = mediaRepository.getAlbumTracks(album.id).getOrNull() ?: emptyList()
                                for (track in tracks) {
                                    buildPlayableMediaItem(track.id)?.let { resolvedList.add(it) }
                                }
                            }
                        }
                        mediaId.startsWith("ALBUM_|") -> {
                            val albumId = mediaId.removePrefix("ALBUM_|")
                            val tracks = mediaRepository.getAlbumTracks(albumId).getOrNull() ?: emptyList()
                            for (track in tracks) {
                                buildPlayableMediaItem(track.id)?.let { resolvedList.add(it) }
                            }
                        }
                        mediaId.startsWith("PLAYLIST_|") -> {
                            val playlistId = mediaId.removePrefix("PLAYLIST_|")
                            val playlistItems = mediaRepository.getPlaylistItems(playlistId).getOrNull() ?: emptyList()
                            for (pi in playlistItems) {
                                buildPlayableMediaItem(pi.id)?.let { resolvedList.add(it) }
                            }
                        }
                        mediaId.startsWith("TRACK_|") -> {
                            val trackId = mediaId.removePrefix("TRACK_|")
                            buildPlayableMediaItem(trackId)?.let { resolvedList.add(it) }
                        }
                        mediaId.startsWith("DOWNLOAD_|") -> {
                            val downloadId = mediaId.removePrefix("DOWNLOAD_|")
                            buildPlayableMediaItem(downloadId)?.let { resolvedList.add(it) }
                        }
                        else -> {
                            buildPlayableMediaItem(mediaId)?.let { resolvedList.add(it) }
                        }
                    }
                }
                resolvedList
            }
        }
    }

    private fun <T> resolveFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun buildBrowsableFolder(id: String, title: String, mediaType: Int): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    private fun mapArtistToMediaItem(artist: com.raulshma.jellyplay.core.model.MediaItem): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(artist.id, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("ARTIST_|${artist.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(artist.name)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun mapAlbumToMediaItem(album: com.raulshma.jellyplay.core.model.MediaItem): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(album.id, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("ALBUM_|${album.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(album.name)
                    .setArtist(album.albumArtist ?: album.artistItems.firstOrNull()?.name ?: "")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun mapPlaylistToMediaItem(playlist: com.raulshma.jellyplay.core.model.Playlist): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(playlist.id, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("PLAYLIST_|${playlist.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun mapTrackToPlayableMediaItem(track: com.raulshma.jellyplay.core.model.MediaItem): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(track.id, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("TRACK_|${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "")
                    .setAlbumTitle(track.album ?: "")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun mapPlaylistItemToPlayableMediaItem(pi: com.raulshma.jellyplay.core.model.PlaylistItem): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(pi.id, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("TRACK_|${pi.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(pi.name)
                    .setArtist(pi.artist ?: "")
                    .setAlbumTitle(pi.album ?: "")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    private fun mapDownloadToPlayableMediaItem(dl: com.raulshma.jellyplay.core.model.DownloadItem): MediaItem {
        val artUri = try {
            Uri.parse(playbackRepository.getImageUrl(dl.mediaItemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        } catch (_: Exception) {
            null
        }
        return MediaItem.Builder()
            .setMediaId("DOWNLOAD_|${dl.mediaItemId}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(dl.name)
                    .setArtist(dl.seriesName ?: "")
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(artUri)
                    .build()
            )
            .build()
    }

    internal suspend fun buildPlayableMediaItem(itemId: String, startPositionMs: Long = 0L): MediaItem? {
        val detail = mediaRepository.getMediaDetail(itemId).getOrNull()
        // The completed-download predicate lives once in PlaybackSourceResolver.
        // resolveLocalSource returns the file URI + title (offlineItem name
        // preferred) without a getMediaDetail round-trip; artist/album still
        // come from the detail fetch above.
        val local = playbackSourceResolver.resolveLocalSource(itemId)

        if (local != null) {
            val name = detail?.item?.name ?: local.title
            val artist = detail?.item?.albumArtist ?: detail?.item?.artistItems?.firstOrNull()?.name ?: ""
            val album = detail?.item?.album ?: ""
            val artUri = try {
                Uri.parse(playbackRepository.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
            } catch (_: Exception) {
                null
            }
            return MediaItem.Builder()
                .setMediaId(itemId)
                .setUri(local.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setArtworkUri(artUri)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .build()
                )
                .build()
        }

        if (detail == null) return null
        val source = detail.mediaSources.firstOrNull()
        val tier = adaptiveBitrateSelector.resolveBitrate(streamingQualityProvider())
        val maxBitrate = tier.targetKbps * 1000
        val url = playbackRepository.getStreamUrl(
            itemId = itemId,
            mediaSourceId = source?.id ?: "",
            startTimeTicks = if (startPositionMs > 0) startPositionMs * 10_000 else 0L,
            maxBitrate = maxBitrate,
            useAudioEndpoint = false,
        )
        val artUri = Uri.parse(playbackRepository.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
        return MediaItem.Builder()
            .setMediaId(itemId)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(detail.item.name)
                    .setArtist(detail.item.albumArtist ?: detail.item.artistItems.firstOrNull()?.name ?: "")
                    .setAlbumTitle(detail.item.album ?: "")
                    .setArtworkUri(artUri)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }
}
