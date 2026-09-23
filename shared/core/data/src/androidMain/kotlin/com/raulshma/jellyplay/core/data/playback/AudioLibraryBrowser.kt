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
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.StreamingQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

// The `X_|<id>` mediaId grammar every browse/controller client depends on —
// matchers and builders below share these constants so the grammar lives once.
private const val ARTIST_ID_PREFIX = "ARTIST_|"
private const val ALBUM_ID_PREFIX = "ALBUM_|"
private const val PLAYLIST_ID_PREFIX = "PLAYLIST_|"
private const val TRACK_ID_PREFIX = "TRACK_|"
private const val DOWNLOAD_ID_PREFIX = "DOWNLOAD_|"

class AudioLibraryBrowser(
    private val scope: CoroutineScope,
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val streamingQualityProvider: () -> StreamingQuality,
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector,
) {
    private val resolvePermits = Semaphore(4)

    /**
     * Order-preserving bounded-concurrency resolve over [resolvePermits] — the
     * shared [mapConcurrent] fan-out with this browser's resolve policy kept at
     * the call site: each item's body still hops to [Dispatchers.IO] (the
     * playback scope runs on `Main.immediate`, and resolution does synchronous
     * local-file checks), results keep input order, and null resolutions
     * (no local file and no server detail) are dropped, never padded.
     * Per-item failures propagate and cancel the siblings, as before.
     */
    private suspend fun <T, R : Any> mapConcurrently(items: List<T>, block: suspend (T) -> R?): List<R> =
        resolvePermits.mapConcurrent(items) { item ->
            withContext(Dispatchers.IO) { block(item) }
        }.filterNotNull()

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
                    parentId == "ARTISTS" || parentId == "ALBUMS" -> {
                        // The two arms differ only in the requested media type
                        // and the folder-node mapper — one paged fetch serves both.
                        val isArtists = parentId == "ARTISTS"
                        val result = mediaRepository.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(if (isArtists) MediaType.ARTIST else MediaType.ALBUM),
                            ),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { item ->
                            list.add(if (isArtists) mapArtistToMediaItem(item) else mapAlbumToMediaItem(item))
                        }
                    }
                    parentId == "PLAYLISTS" -> {
                        val result = playlistRepository.getPlaylists(limit = pageSize).getOrNull()
                        result?.forEach { playlist ->
                            list.add(mapPlaylistToMediaItem(playlist))
                        }
                    }
                    parentId == "FAVORITES" -> {
                        val result = mediaRepository.getFavorites(
                            mediaTypes = listOf(MediaType.MUSIC, MediaType.AUDIO),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                    parentId == "DOWNLOADS" -> {
                        val completedAudioDownloads = try {
                            downloadRepository.getCompletedAudioDownloads(
                                limit = pageSize,
                                offset = page * pageSize,
                            )
                        } catch (_: Exception) {
                            emptyList()
                        }
                        completedAudioDownloads.forEach { dl ->
                            list.add(mapDownloadToPlayableMediaItem(dl))
                        }
                    }
                    parentId.startsWith(ARTIST_ID_PREFIX) -> {
                        val artistId = parentId.removePrefix(ARTIST_ID_PREFIX)
                        val albums = mediaRepository.getArtistAlbums(artistId, limit = pageSize).getOrNull() ?: emptyList()
                        albums.forEach { album ->
                            list.add(mapAlbumToMediaItem(album))
                        }
                    }
                    parentId.startsWith(ALBUM_ID_PREFIX) -> {
                        val albumId = parentId.removePrefix(ALBUM_ID_PREFIX)
                        val tracks = mediaRepository.getAlbumTracks(albumId).getOrNull() ?: emptyList()
                        tracks.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                    parentId.startsWith(PLAYLIST_ID_PREFIX) -> {
                        val playlistId = parentId.removePrefix(PLAYLIST_ID_PREFIX)
                        val playlistItems = playlistRepository.getPlaylistItems(playlistId, startIndex = page * pageSize, limit = pageSize).getOrNull() ?: emptyList()
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
                        mediaId.startsWith(ARTIST_ID_PREFIX) -> {
                            val id = mediaId.removePrefix(ARTIST_ID_PREFIX)
                            mediaRepository.getMediaDetail(id).getOrNull()?.let { mapArtistToMediaItem(it.item) }
                        }
                        mediaId.startsWith(ALBUM_ID_PREFIX) -> {
                            val id = mediaId.removePrefix(ALBUM_ID_PREFIX)
                            mediaRepository.getMediaDetail(id).getOrNull()?.let { mapAlbumToMediaItem(it.item) }
                        }
                        mediaId.startsWith(PLAYLIST_ID_PREFIX) -> {
                            val id = mediaId.removePrefix(PLAYLIST_ID_PREFIX)
                            // Single-item detail fetch — the mapper only reads
                            // id + name, so a synthetic Playlist from the detail
                            // is equivalent without pulling every playlist from
                            // the server (the sibling ARTIST_/ALBUM_ approach).
                            mediaRepository.getMediaDetail(id).getOrNull()?.let { detail ->
                                mapPlaylistToMediaItem(
                                    Playlist(
                                        id = detail.item.id,
                                        name = detail.item.name,
                                    )
                                )
                            }
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
                        mediaId.startsWith(ARTIST_ID_PREFIX) -> {
                            val artistId = mediaId.removePrefix(ARTIST_ID_PREFIX)
                            val albums = mediaRepository.getArtistAlbums(artistId).getOrNull() ?: emptyList()
                            val tracks = mapConcurrently(albums) { album ->
                                mediaRepository.getAlbumTracks(album.id).getOrNull() ?: emptyList()
                            }.flatten()
                            resolvedList.addAll(mapConcurrently(tracks) { track ->
                                buildPlayableMediaItem(track.id)
                            })
                        }
                        mediaId.startsWith(ALBUM_ID_PREFIX) -> {
                            val albumId = mediaId.removePrefix(ALBUM_ID_PREFIX)
                            val tracks = mediaRepository.getAlbumTracks(albumId).getOrNull() ?: emptyList()
                            resolvedList.addAll(mapConcurrently(tracks) { track ->
                                buildPlayableMediaItem(track.id)
                            })
                        }
                        mediaId.startsWith(PLAYLIST_ID_PREFIX) -> {
                            val playlistId = mediaId.removePrefix(PLAYLIST_ID_PREFIX)
                            val playlistItems = playlistRepository.getPlaylistItems(playlistId).getOrNull() ?: emptyList()
                            resolvedList.addAll(mapConcurrently(playlistItems) { pi ->
                                buildPlayableMediaItem(pi.id)
                            })
                        }
                        mediaId.startsWith(TRACK_ID_PREFIX) -> {
                            val trackId = mediaId.removePrefix(TRACK_ID_PREFIX)
                            buildPlayableMediaItem(trackId)?.let { resolvedList.add(it) }
                        }
                        mediaId.startsWith(DOWNLOAD_ID_PREFIX) -> {
                            val downloadId = mediaId.removePrefix(DOWNLOAD_ID_PREFIX)
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

    /**
     * Artwork lookup for library nodes — [PlaybackRepository.getImageUrl]
     * throws on offline/unresolved ids; degrade to null exactly like the
     * per-mapper try/catch ladders this replaces (local-file playables
     * included — but NOT the server-stream playable branch, which
     * deliberately propagates: a missing artwork must not silently mask a
     * failing resolve there).
     */
    private fun artUri(itemId: String): Uri? = try {
        Uri.parse(playbackRepository.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
    } catch (_: Exception) {
        null
    }

    /**
     * Single construction path for every library [MediaItem] — the six map*
     * adapters below and both [buildPlayableMediaItem] branches fold into one
     * metadata ladder. Optional [artist]/[album]/[uri] are only set when
     * non-null, so folder nodes keep unset artist/album fields exactly as
     * before; [artUri] always flows into `artworkUri` (null ≙ unset).
     */
    private fun mediaItem(
        mediaId: String,
        title: String,
        browsable: Boolean,
        playable: Boolean,
        mediaType: Int,
        artist: String? = null,
        album: String? = null,
        artUri: Uri? = null,
        uri: String? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(browsable)
            .setIsPlayable(playable)
            .setMediaType(mediaType)
            .setArtworkUri(artUri)
        if (artist != null) metadata.setArtist(artist)
        if (album != null) metadata.setAlbumTitle(album)
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata.build())
        if (uri != null) builder.setUri(uri)
        return builder.build()
    }

    private fun mapArtistToMediaItem(artist: com.raulshma.jellyplay.core.model.MediaItem): MediaItem =
        mediaItem(
            mediaId = "$ARTIST_ID_PREFIX${artist.id}",
            title = artist.name,
            artUri = artUri(artist.id),
            browsable = true,
            playable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_ARTIST,
        )

    private fun mapAlbumToMediaItem(album: com.raulshma.jellyplay.core.model.MediaItem): MediaItem =
        mediaItem(
            mediaId = "$ALBUM_ID_PREFIX${album.id}",
            title = album.name,
            artist = album.albumArtist ?: album.artistItems.firstOrNull()?.name ?: "",
            artUri = artUri(album.id),
            browsable = true,
            playable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_ALBUM,
        )

    private fun mapPlaylistToMediaItem(playlist: Playlist): MediaItem =
        mediaItem(
            mediaId = "$PLAYLIST_ID_PREFIX${playlist.id}",
            title = playlist.name,
            artUri = artUri(playlist.id),
            browsable = true,
            playable = false,
            mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
        )

    private fun mapTrackToPlayableMediaItem(track: com.raulshma.jellyplay.core.model.MediaItem): MediaItem =
        mediaItem(
            mediaId = "$TRACK_ID_PREFIX${track.id}",
            title = track.name,
            artist = track.albumArtist ?: track.artistItems.firstOrNull()?.name ?: "",
            album = track.album ?: "",
            artUri = artUri(track.id),
            browsable = false,
            playable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        )

    private fun mapPlaylistItemToPlayableMediaItem(pi: PlaylistItem): MediaItem =
        mediaItem(
            mediaId = "$TRACK_ID_PREFIX${pi.id}",
            title = pi.name,
            artist = pi.artist ?: "",
            album = pi.album ?: "",
            artUri = artUri(pi.id),
            browsable = false,
            playable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        )

    private fun mapDownloadToPlayableMediaItem(dl: DownloadItem): MediaItem =
        mediaItem(
            mediaId = "$DOWNLOAD_ID_PREFIX${dl.mediaItemId}",
            title = dl.name,
            artist = dl.seriesName ?: "",
            artUri = artUri(dl.mediaItemId),
            browsable = false,
            playable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        )

    internal suspend fun buildPlayableMediaItem(itemId: String, startPositionMs: Long = 0L): MediaItem? {
        val (detail, local) = coroutineScope {
            val detailJob = async { mediaRepository.getMediaDetail(itemId).getOrNull() }
            // The completed-download predicate lives once in PlaybackSourceResolver.
            // resolveLocalSource returns the file URI + title (offlineItem name
            // preferred) without a getMediaDetail round-trip; artist/album still
            // come from the detail fetch.
            val localJob = async { playbackSourceResolver.resolveLocalSource(itemId) }
            detailJob.await() to localJob.await()
        }

        if (local != null) {
            return mediaItem(
                mediaId = itemId,
                uri = local.uri,
                title = detail?.item?.name ?: local.title,
                artist = detail?.item?.albumArtist ?: detail?.item?.artistItems?.firstOrNull()?.name ?: "",
                album = detail?.item?.album ?: "",
                artUri = artUri(itemId),
                browsable = false,
                playable = true,
                mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
            )
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
        // Unlike every other arm, this artwork read deliberately PROPAGATES a
        // getImageUrl throw (resolve-failing — it cancels the concurrent
        // ladder's siblings) instead of degrading to missing artwork, so the
        // swallowing [artUri] helper is intentionally NOT used here.
        return mediaItem(
            mediaId = itemId,
            uri = url,
            title = detail.item.name,
            artist = detail.item.albumArtist ?: detail.item.artistItems.firstOrNull()?.name ?: "",
            album = detail.item.album ?: "",
            artUri = Uri.parse(playbackRepository.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)),
            browsable = false,
            playable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        )
    }
}
