package com.raulshma.jellyplay.core.data.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MusicCatalogue
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.streaming.AdaptiveBitrateSelector
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.SortOption
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.isMusicTrack
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
private const val GENRE_ID_PREFIX = "GENRE_|"

// Android Auto / Automotive content-style contract — the platform
// `android.media.browse` extras the head unit reads off LibraryParams to
// decide grid vs. list layout for a folder's children (declared here as
// literals because the media3-side constants ride @UnstableApi and the wire
// keys are frozen by the platform). Ignored by clients that don't understand
// them, so they degrade silently on non-Auto browsers.
private const val EXTRA_CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val EXTRA_CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_TYPE_LIST_ITEM = 1
private const val CONTENT_STYLE_TYPE_GRID_ITEM = 2
private const val CONTENT_STYLE_TYPE_CATEGORY_LIST_ITEM = 3
private const val CONTENT_STYLE_TYPE_CATEGORY_GRID_ITEM = 4

private const val TAG = "AudioLibraryBrowser"

// MainActivity lives in the `app` module; reference by class name, same
// convention as the video controller's PLAYER_ACTIVITY_CLASS_NAME.
private const val MAIN_ACTIVITY_CLASS_NAME = "com.raulshma.jellyplay.MainActivity"

class AudioLibraryBrowser(
    private val scope: CoroutineScope,
    /**
     * The detail read only ([getMediaDetail], the playable-node resolver) —
     * the browser is a mixed consumer. Its catalogue reads (artist albums,
     * album tracks) ride [musicCatalogue], the repository's narrow music
     * family seam.
     */
    private val mediaRepository: MediaRepository,
    private val musicCatalogue: MusicCatalogue,
    /**
     * The SearchResult-shaped collection reads (the union's getMediaItems /
     * getFavorites retired to this seam). The browser is otherwise a mixed
     * consumer — catalogue + detail reads still ride [mediaRepository].
     */
    private val libraryApiClient: LibraryApiClient,
    private val playlistRepository: PlaylistRepository,
    private val downloadRepository: DownloadRepository,
    private val playbackRepository: PlaybackRepository,
    /**
     * Artwork URLs for the browse rows and the resolved items — the narrow
     * [ImageUrlProvider] seam the image-URL builders retired off
     * [PlaybackRepository] into.
     */
    private val imageUrlProvider: ImageUrlProvider,
    private val playbackSourceResolver: PlaybackSourceResolver,
    private val streamingQualityProvider: () -> StreamingQuality,
    private val adaptiveBitrateSelector: AdaptiveBitrateSelector,
    /**
     * Call-time provider for the queue facade — the seam car-initiated
     * playback routes through (see [callback.onSetMediaItems]). A direct
     * constructor dependency would be circular: the facade resolves the
     * [AudioQueueManager] alias → [AudioPlaybackManager] → this browser.
     * Called only after the manager exists, so the indirection never recurses.
     */
    private val audioQueueFacadeProvider: () -> AudioQueueFacade,
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
            // The car's "open on phone" affordance and Assistant entry points
            // launch the session activity; MainActivity is singleTask like
            // PlayerActivity, so an existing instance is brought forward
            // instead of stacking. The notification keeps its own content
            // intent (JellyPlayNotificationProvider).
            .setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent().apply {
                        setClassName(context, MAIN_ACTIVITY_CLASS_NAME)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
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
                        // Android Auto landing order: history first (the
                        // cheapest re-entry), catalogue after. The folder
                        // mediaTypes tell the head unit what each tab holds.
                        list.add(buildBrowsableFolder("RECENT", "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("RECENT_ALBUMS", "Recently Added", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS))
                        list.add(buildBrowsableFolder("ARTISTS", "Artists", MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS))
                        list.add(buildBrowsableFolder("ALBUMS", "Albums", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS))
                        list.add(buildBrowsableFolder("GENRES", "Genres", MediaMetadata.MEDIA_TYPE_FOLDER_GENRES))
                        list.add(buildBrowsableFolder("PLAYLISTS", "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS))
                        list.add(buildBrowsableFolder("FAVORITES", "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                        list.add(buildBrowsableFolder("DOWNLOADS", "Downloads", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED))
                    }
                    parentId == "RECENT" -> {
                        // The MusicHome "recently played" query: audio tracks
                        // by DatePlayed descending.
                        val result = libraryApiClient.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(MediaType.AUDIO),
                                sortBy = SortOption.DATE_PLAYED,
                            ),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                    parentId == "RECENT_ALBUMS" -> {
                        val result = libraryApiClient.getMediaItems(
                            filters = LibraryFilters(
                                mediaTypes = listOf(MediaType.ALBUM),
                                sortBy = SortOption.DATE_ADDED,
                            ),
                            startIndex = page * pageSize,
                            limit = pageSize
                        ).getOrNull()
                        result?.items?.forEach { album ->
                            list.add(mapAlbumToMediaItem(album))
                        }
                    }
                    parentId == "GENRES" -> {
                        // The genres endpoint is unpaginated server-side; the
                        // set is small, so page it locally.
                        val genres = mediaRepository.getGenres().getOrNull() ?: emptyList()
                        genres.drop(page * pageSize).take(pageSize).forEach { genre ->
                            list.add(genreFolder(genre))
                        }
                    }
                    parentId == "ARTISTS" || parentId == "ALBUMS" -> {
                        // The two arms differ only in the requested media type
                        // and the folder-node mapper — one paged fetch serves both.
                        val isArtists = parentId == "ARTISTS"
                        val result = libraryApiClient.getMediaItems(
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
                        val result = libraryApiClient.getFavorites(
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
                        val albums = libraryApiClient.getArtistAlbums(artistId, limit = pageSize).getOrNull() ?: emptyList()
                        albums.forEach { album ->
                            list.add(mapAlbumToMediaItem(album))
                        }
                    }
                    parentId.startsWith(ALBUM_ID_PREFIX) -> {
                        val albumId = parentId.removePrefix(ALBUM_ID_PREFIX)
                        val tracks = musicCatalogue.getAlbumTracks(albumId, force = false).getOrNull() ?: emptyList()
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
                    parentId.startsWith(GENRE_ID_PREFIX) -> {
                        // The shared genre→tracks read (name-keyed filter —
                        // see [genreTracks]); an unresolvable id yields empty.
                        val tracks = genreTracks(
                            genreId = parentId.removePrefix(GENRE_ID_PREFIX),
                            limit = pageSize,
                            offset = page * pageSize,
                        )
                        tracks.forEach { track ->
                            list.add(mapTrackToPlayableMediaItem(track))
                        }
                    }
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(list), contentStyleParams(parentId, params))
            }
        }

        /**
         * Accepts a search query from a controller (Android Auto voice or the
         * head unit's search box). The result is empty; the query is validated
         * eagerly so a blank query fails fast instead of every later page
         * fetch returning an error future.
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return if (query.isBlank()) {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            } else {
                Futures.immediateFuture(LibraryResult.ofVoid(params))
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return resolveFuture {
                val result = mediaRepository.search(
                    query = query,
                    filters = LibraryFilters(
                        mediaTypes = listOf(MediaType.ARTIST, MediaType.ALBUM, MediaType.AUDIO, MediaType.MUSIC),
                    ),
                    limit = pageSize,
                    startIndex = page * pageSize,
                ).getOrNull()
                val items = result?.items?.mapNotNull { item -> mapSearchResultToMediaItem(item) } ?: emptyList()
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        /**
         * Controller-initiated `setMediaItems` — the Android Auto "play" tap
         * (track, album, artist, playlist node) and every voice "play X"
         * resolution. The default media3 behavior would write the resolved
         * list straight onto the ExoPlayer, bypassing the queue chassis: the
         * phone queue UI, persistence, and the [QueuePlaylistMirror] prefix
         * invariant would desync from whatever the car started. Instead the
         * expanded tracks are routed through [AudioQueueFacade.playTracks],
         * making the chassis queue the single source of truth, and the
         * returned playable list converges with the mirror rebuild media3
         * performs on this future's completion (same ids, same order, so
         * whichever write lands last leaves the playlist correct).
         *
         * The facade hop is best-effort: on failure (e.g. artwork URL
         * resolution without a server session) playback still works via the
         * plain direct-write path — only queue-chassis sync is lost.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = resolveFuture {
            val expanded = expandToDomainTracks(mediaItems.map { it.mediaId })
            val startIndexInExpanded = if (expanded.tracks.isEmpty()) 0 else expanded.startIndexFor(startIndex)
            if (expanded.tracks.isNotEmpty()) {
                try {
                    audioQueueFacadeProvider().playTracks(
                        expanded.tracks,
                        // Coerced like the media3 return below: a tapped node
                        // that expands to zero tracks would otherwise hand the
                        // chassis a start index one past the end of the list.
                        startIndex = startIndexInExpanded.coerceAtMost(expanded.tracks.lastIndex),
                        // The queue rows are a dense music list — the same
                        // MUSIC_MAX_WIDTH the facade's phone-side dense-list
                        // callers request. Image-URL building never throws
                        // (no server session → empty URL ≙ no artwork), so
                        // the car-started queue keeps phone-grade artwork.
                        imageMaxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Queue-chassis routing failed; falling back to direct player write", e)
                }
            }
            // Per-item degradation, never a failed future: by this point the
            // facade hop above has usually already started playback through
            // the queue chassis, so erroring the controller future would show
            // failure on the head unit while music actually plays. Tracks
            // that resolve to nothing (or whose resolve throws — e.g. artwork
            // URL with no server session) drop out of the controller-side
            // list instead; [ResolvedPlayable] keeps the tapped slot
            // identifiable so the returned start index still maps to the
            // right survivor.
            val resolved = mapConcurrently(expanded.tracks.withIndex().toList()) { (index, track) ->
                ResolvedPlayable(
                    runCatching {
                        buildPlayableMediaItem(
                            track.id,
                            startPositionMs = if (index == startIndexInExpanded) startPositionMs else 0L,
                        )
                    }
                        .onFailure { Log.w(TAG, "Playable resolve failed for ${track.id}; dropping it from the controller list", it) }
                        .getOrNull(),
                    isStart = index == startIndexInExpanded,
                )
            }
            val playables = resolved.mapNotNull { it.playable }
            // A resume position belongs to the tapped track — when that slot
            // dropped (or clamped to a survivor), the position must not slide
            // onto whatever track now occupies the start index.
            var effectiveStartPositionMs = startPositionMs
            val startIndexInPlayables = if (playables.isEmpty()) {
                0
            } else {
                val startSlot = resolved.indexOfFirst { it.isStart }
                when {
                    // Tapped node expanded to zero tracks — match the
                    // pre-existing clamp-to-last semantics.
                    startSlot < 0 -> {
                        effectiveStartPositionMs = 0L
                        playables.lastIndex
                    }
                    resolved[startSlot].playable != null -> resolved.subList(0, startSlot).count { it.playable != null }
                    // The tapped track itself failed to resolve — start on
                    // the first survivor at/after it (or the last if none).
                    else -> {
                        effectiveStartPositionMs = 0L
                        minOf(resolved.subList(0, startSlot).count { it.playable != null }, playables.lastIndex)
                    }
                }
            }
            MediaSession.MediaItemsWithStartPosition(playables, startIndexInPlayables, effectiveStartPositionMs)
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
                        mediaId.startsWith(GENRE_ID_PREFIX) -> {
                            mediaRepository.getGenres().getOrNull()
                                ?.firstOrNull { it.id == mediaId.removePrefix(GENRE_ID_PREFIX) }
                                ?.let(::genreFolder)
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
                // The expansion ladder lives once, in expandToDomainTracks —
                // this callback (the default direct-write path media3 uses for
                // controller-initiated adds) just resolves the domain tracks
                // to playable items, like the tail of onSetMediaItems.
                mapConcurrently(expandToDomainTracks(mediaItems.map { it.mediaId }).tracks) { track ->
                    buildPlayableMediaItem(track.id)
                }
            }
        }
    }

    /**
     * The genre→tracks read shared by the GENRE_| browse children and the
     * [callback.onAddMediaItems] / [callback.onSetMediaItems] expansions —
     * id→name resolution (the Jellyfin genres filter is name-keyed) plus the
     * paged audio query. An unresolvable genre id yields an empty list.
     */
    private suspend fun genreTracks(genreId: String, limit: Int, offset: Int): List<com.raulshma.jellyplay.core.model.MediaItem> {
        val genreName = mediaRepository.getGenres().getOrNull()?.firstOrNull { it.id == genreId }?.name
            ?: return emptyList()
        return libraryApiClient.getMediaItems(
            filters = LibraryFilters(
                mediaTypes = listOf(MediaType.AUDIO),
                genres = listOf(genreName),
            ),
            startIndex = offset,
            limit = limit,
        ).getOrNull()?.items ?: emptyList()
    }

    /**
     * Content-style [LibraryParams][MediaLibraryService.LibraryParams] for a
     * folder's children response — the grid/list hints Android Auto renders
     * by. Container folders (the root's folder tabs, artists/albums/genres)
     * ask for grids of category cards; track folders ask for a flat list;
     * playlists are the container exception (a flat list — Jellyfin playlists
     * rarely carry artwork, and Auto renders playlists as lists). The hints
     * are merged INTO the caller's params so client-supplied extras survive
     * (Auto announces its content-style support there); unknown parents get
     * the caller's params back verbatim.
     */
    private fun contentStyleParams(
        parentId: String,
        params: MediaLibraryService.LibraryParams?,
    ): MediaLibraryService.LibraryParams? {
        val browsableGrid = parentId == "ROOT" || parentId == "ARTISTS" || parentId == "ALBUMS" ||
            parentId == "RECENT_ALBUMS" || parentId == "GENRES" ||
            parentId.startsWith(ARTIST_ID_PREFIX)
        val browsableList = parentId == "PLAYLISTS"
        val playableList = parentId == "RECENT" || parentId == "FAVORITES" || parentId == "DOWNLOADS" ||
            parentId.startsWith(ALBUM_ID_PREFIX) || parentId.startsWith(PLAYLIST_ID_PREFIX) ||
            parentId.startsWith(GENRE_ID_PREFIX)
        if (!browsableGrid && !browsableList && !playableList) return params
        // Copy-construct so client extras survive (Robolectric's Bundle(null)
        // NPEs where real Android tolerates it).
        val extras = params?.extras?.let(::Bundle) ?: Bundle()
        if (browsableGrid) {
            extras.putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_TYPE_CATEGORY_GRID_ITEM)
        }
        if (browsableList) {
            extras.putInt(EXTRA_CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_TYPE_CATEGORY_LIST_ITEM)
        }
        if (playableList) {
            extras.putInt(EXTRA_CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_TYPE_LIST_ITEM)
        }
        return MediaLibraryService.LibraryParams.Builder().setExtras(extras).build()
    }

    /** One search hit → its browse-tree node: artists/albums stay browsable, tracks playable. */
    private fun mapSearchResultToMediaItem(item: com.raulshma.jellyplay.core.model.MediaItem): MediaItem? = when (item.mediaType) {
        MediaType.ARTIST -> mapArtistToMediaItem(item)
        MediaType.ALBUM -> mapAlbumToMediaItem(item)
        else -> if (item.mediaType.isMusicTrack) mapTrackToPlayableMediaItem(item) else null
    }

    /**
     * The result of expanding controller-supplied mediaIds into the DOMAIN
     * track list the queue facade accepts: [tracks] is the flattened list,
     * and [startIndexFor] translates the controller's startIndex (a position
     * in the ORIGINAL item list, where one album node may expand to many
     * tracks) into the expanded list's coordinates — the start of the tapped
     * node's expansion.
     */
    private class ExpandedTracks(val tracks: List<com.raulshma.jellyplay.core.model.MediaItem>, private val offsets: IntArray) {
        fun startIndexFor(originalIndex: Int): Int = when {
            originalIndex < 0 || originalIndex >= offsets.size -> 0
            else -> offsets[originalIndex]
        }
    }

    /**
     * One controller-started playable resolution from [callback.onSetMediaItems]:
     * [playable] is null when the track resolved to nothing (offline, no
     * detail) or its resolve threw — that path degrades per item instead of
     * failing the whole future, because the facade hop has usually already
     * started playback. [isStart] marks the controller's tapped track so the
     * surviving list's start index maps to the right slot even when
     * resolutions around it drop.
     */
    private class ResolvedPlayable(val playable: MediaItem?, val isStart: Boolean)

    /**
     * Expands browse-tree mediaIds (any prefix, or a bare track id) into
     * domain tracks — the same expansion ladder [callback.onAddMediaItems]
     * walks, but stopping at the DOMAIN model instead of resolving playable
     * URIs, because the queue facade maps domain tracks → queue rows itself.
     * Downloads and playlist entries build their domain rows from the local
     * records (name/artist/album) so an offline download never depends on a
     * server detail fetch.
     */
    private suspend fun expandToDomainTracks(mediaIds: List<String>): ExpandedTracks {
        val tracks = mutableListOf<com.raulshma.jellyplay.core.model.MediaItem>()
        val offsets = IntArray(mediaIds.size)
        mediaIds.forEachIndexed { index, mediaId ->
            offsets[index] = tracks.size
            when {
                mediaId.startsWith(ARTIST_ID_PREFIX) -> {
                    val albums = libraryApiClient.getArtistAlbums(mediaId.removePrefix(ARTIST_ID_PREFIX), limit = 50)
                        .getOrNull() ?: emptyList()
                    mapConcurrently(albums) { album ->
                        musicCatalogue.getAlbumTracks(album.id, force = false).getOrNull()
                    }.forEach { tracks.addAll(it) }
                }
                mediaId.startsWith(ALBUM_ID_PREFIX) -> {
                    tracks.addAll(
                        musicCatalogue.getAlbumTracks(mediaId.removePrefix(ALBUM_ID_PREFIX), force = false)
                            .getOrNull() ?: emptyList()
                    )
                }
                mediaId.startsWith(PLAYLIST_ID_PREFIX) -> {
                    val playlistItems = playlistRepository.getPlaylistItems(mediaId.removePrefix(PLAYLIST_ID_PREFIX))
                        .getOrNull() ?: emptyList()
                    tracks.addAll(playlistItems.map { it.toDomainTrack() })
                }
                mediaId.startsWith(GENRE_ID_PREFIX) -> {
                    tracks.addAll(genreTracks(mediaId.removePrefix(GENRE_ID_PREFIX), limit = Int.MAX_VALUE, offset = 0))
                }
                mediaId.startsWith(TRACK_ID_PREFIX) -> {
                    offlineSafeDomainTrack(mediaId.removePrefix(TRACK_ID_PREFIX))?.let(tracks::add)
                }
                mediaId.startsWith(DOWNLOAD_ID_PREFIX) -> {
                    offlineSafeDomainTrack(mediaId.removePrefix(DOWNLOAD_ID_PREFIX))?.let(tracks::add)
                }
                else -> {
                    offlineSafeDomainTrack(mediaId)?.let(tracks::add)
                }
            }
        }
        return ExpandedTracks(tracks, offsets)
    }

    /** Playlist entry → domain track (the queue row only needs id/name/artist/album). */
    private fun PlaylistItem.toDomainTrack(): com.raulshma.jellyplay.core.model.MediaItem =
        com.raulshma.jellyplay.core.model.MediaItem(
            id = id,
            name = name,
            mediaType = MediaType.AUDIO,
            albumArtist = artist,
            album = album,
        )

    /** Completed download → domain track, offline-safe (no server read). */
    private fun DownloadItem.toDomainTrack(): com.raulshma.jellyplay.core.model.MediaItem =
        com.raulshma.jellyplay.core.model.MediaItem(
            id = mediaItemId,
            name = name,
            mediaType = MediaType.AUDIO,
            albumArtist = seriesName,
        )

    /**
     * The leaf arm of [expandToDomainTracks] — one offline-safe itemId →
     * domain track read, shared by the `TRACK_|`, `DOWNLOAD_|`, and bare-id
     * arms so their prefix handling can't drift (a `TRACK_|` id once fell
     * through to the bare-id detail fetch — with the prefix still attached —
     * and resolved nothing): the completed-download record first (an offline
     * download must never depend on a server fetch), then the server detail
     * for online items.
     */
    private suspend fun offlineSafeDomainTrack(itemId: String): com.raulshma.jellyplay.core.model.MediaItem? {
        val download = try {
            downloadRepository.getCompletedAudioDownloads(limit = Int.MAX_VALUE, offset = 0)
                .firstOrNull { it.mediaItemId == itemId }
        } catch (_: Exception) {
            null
        }
        return download?.toDomainTrack()
            ?: mediaRepository.getMediaDetail(itemId).getOrNull()?.item
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

    /** Genre → browse node — the one place the `GENRE_|` id and its folder shape pair up. */
    private fun genreFolder(genre: Genre): MediaItem =
        buildBrowsableFolder("$GENRE_ID_PREFIX${genre.id}", genre.name, MediaMetadata.MEDIA_TYPE_FOLDER_GENRES)

    /**
     * Artwork lookup for library nodes — [ImageUrlProvider.getImageUrl]
     * throws on offline/unresolved ids; degrade to null exactly like the
     * per-mapper try/catch ladders this replaces (local-file playables
     * included — but NOT the server-stream playable branch, which
     * deliberately propagates: a missing artwork must not silently mask a
     * failing resolve there).
     */
    private fun artUri(itemId: String): Uri? = try {
        Uri.parse(imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH))
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
            artUri = Uri.parse(imageUrlProvider.getImageUrl(itemId, maxWidth = ImageUrlProvider.MUSIC_MAX_WIDTH)),
            browsable = false,
            playable = true,
            mediaType = MediaMetadata.MEDIA_TYPE_MUSIC,
        )
    }
}
