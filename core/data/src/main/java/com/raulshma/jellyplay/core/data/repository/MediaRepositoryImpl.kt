package com.raulshma.jellyplay.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import android.util.Log
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.entity.LyricsCacheEntity
import com.raulshma.jellyplay.core.data.paging.FavoritesPagingSource
import com.raulshma.jellyplay.core.data.paging.MediaPagingSource
import com.raulshma.jellyplay.core.data.paging.SearchPagingSource
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.TtlCache
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.RecordingFolder
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PinnedHomeSection
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val lrcLibApi: LrcLibApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val networkMonitor: NetworkMonitor,
) : MediaRepository {

    private val detailCache = TtlCache<MediaDetail>(
        maxSize = DETAIL_CACHE_MAX_ENTRIES,
        ttlMs = DETAIL_CACHE_TTL_MS,
    )

    private val libraryFoldersCache = TtlCache<List<LibraryFolder>>(ttlMs = FOLDERS_CACHE_TTL_MS)
    private val genresCache = TtlCache<List<Genre>>(maxSize = 64, ttlMs = FOLDERS_CACHE_TTL_MS)
    private val studiosCache = TtlCache<List<Studio>>(maxSize = 64, ttlMs = FOLDERS_CACHE_TTL_MS)
    private val latestMediaCache = TtlCache<List<MediaItem>>(maxSize = 64, ttlMs = LATEST_CACHE_TTL_MS)

    override fun invalidateDetailCache(itemId: String?) {
        if (itemId != null) {
            detailCache.remove(itemId)
        } else {
            detailCache.clear()
        }
    }

    @Volatile
    private var cachedHomeSections: List<HomeSection>? = null
    @Volatile
    private var cachedHomeSectionsTimestamp: Long = 0L
    @Volatile
    private var cachedHomeSectionsKey: String = ""
    private val homeSectionsLock = Any()

    // Throttle for lyrics-cache eviction. cacheLyrics() is called on every
    // successful lyrics fetch, and each call used to fire a full
    // DELETE FROM lyrics_cache WHERE fetchedAt < :ts scan over the whole table
    // — so opening lyrics for a new track walked & re-locked the entire table.
    // Eviction is best-effort (wrapped in try/catch) and exact cadence isn't
    // observable, so we cap it at once per hour.
    @Volatile
    private var lastLyricsEvictionMs = 0L

    /**
     * Long-lived scope for the cache-invalidation observer. Never cancelled —
     * [MediaRepositoryImpl] is a `@Singleton` and lives for the process lifetime.
     */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Tracks the last observed stable identity. `null` means we're in an "empty" state
     * (logged out / restoring session). Non-null means we have a stable (serverId|userId)
     * identity whose replacement by a different value should trigger cache invalidation.
     */
    private val lastStableIdentityKey = AtomicReference<String?>(null)

    init {
        // Observe active server/user changes and self-invalidate caches. This closes a
        // privacy + correctness gap where the previous user's home sections / detail data was
        // served for up to 10 minutes (the longest TTL) after `switchUser` or
        // `switchServerAddress`. Implemented as a self-collection so we don't introduce a
        // `data → auth` dependency edge; the flows are exposed by `JellyfinApiClient` which
        // `MediaRepositoryImpl` already depends on.
        //
        // Invalidation rules:
        //  - Empty  → Stable : no invalidation (session restore from a fresh process).
        //  - Stable → Empty  : invalidate (user logged out — clear their data for privacy).
        //  - Stable → Stable : invalidate only if the identity actually changed.
        //  - Empty  → Empty  : never invalidates.
        cacheScope.launch {
            combine(apiClient.currentServer, apiClient.currentUser) { server, user ->
                if (server != null && user != null) "${server.id}|${user.id}" else null
            }.collect { identityKey ->
                val previous = lastStableIdentityKey.getAndSet(identityKey)
                val shouldInvalidate = when {
                    previous == null && identityKey == null -> false
                    previous == null && identityKey != null -> false // session restore, no prior data
                    previous != null && identityKey == null -> true  // logout: clear for privacy
                    previous != identityKey -> true                  // user or server switch
                    else -> false
                }
                if (shouldInvalidate) {
                    invalidateCaches()
                }
            }
        }
    }

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
        nextUpRewatching: Boolean,
        nextUpMaxDays: Int,
        nextUpExcludedSeriesIds: Set<String>,
        hiddenCwItemIds: Set<String>,
        pinnedSections: List<PinnedHomeSection>,
    ): Result<List<HomeSection>> {
        val cacheKey = "${enabledSections.sortedBy { it.name }}|$hiddenLibraryIds|$nextUpRewatching|$nextUpMaxDays|$nextUpExcludedSeriesIds|$hiddenCwItemIds|$pinnedSections"
        val cached = cachedHomeSections
        val timestamp = cachedHomeSectionsTimestamp
        if (cached != null && cacheKey == cachedHomeSectionsKey &&
            android.os.SystemClock.elapsedRealtime() - timestamp < HOME_SECTIONS_CACHE_TTL_MS
        ) {
            return Result.success(cached)
        }
        return apiClient.getHomeSections(
            enabledSections,
            hiddenLibraryIds,
            nextUpRewatching,
            nextUpMaxDays,
            nextUpExcludedSeriesIds,
            hiddenCwItemIds,
            pinnedSections,
        ).also { result ->
            result.getOrNull()?.let { sections ->
                synchronized(homeSectionsLock) {
                    cachedHomeSections = sections
                    cachedHomeSectionsKey = cacheKey
                    cachedHomeSectionsTimestamp = android.os.SystemClock.elapsedRealtime()
                }
            }
        }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> {
        libraryFoldersCache.get("folders")?.let { return Result.success(it) }
        return apiClient.getLibraryFolders().also { result ->
            result.getOrNull()?.let { libraryFoldersCache.put("folders", it) }
        }
    }

    override suspend fun getLatestMedia(
        parentId: String,
        limit: Int,
    ): Result<List<MediaItem>> {
        val cacheKey = "latest_${parentId}_$limit"
        latestMediaCache.get(cacheKey)?.let { return Result.success(it) }
        return apiClient.getLatestMedia(parentId = parentId, limit = limit).also { result ->
            result.getOrNull()?.let { latestMediaCache.put(cacheKey, it) }
        }
    }

    override suspend fun getMediaItems(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        studioIds: List<String>?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
        tags: List<String>?,
    ): Result<SearchResult> = apiClient.getMediaItems(
        parentId = parentId,
        mediaTypes = mediaTypes,
        genres = genres,
        years = years,
        studioIds = studioIds,
        sortBy = sortBy,
        sortOrder = sortOrder,
        startIndex = startIndex,
        limit = limit,
        tags = tags,
    )

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> {
        val cached = detailCache.get(itemId)
        if (cached != null) {
            return Result.success(cached)
        }
        return apiClient.getMediaDetail(itemId).also { result ->
            result.getOrNull()?.let { detail ->
                detailCache.put(itemId, detail)
            }
        }
    }

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> =
        apiClient.getIntros(itemId)

    override suspend fun search(
        query: String,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        tags: List<String>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> {
        // The Jellyfin /Search/Hints endpoint doesn't accept genre/year/tags filters,
        // so when they're present fall through to the filtered items query.
        return if (genres.isNullOrEmpty() && years.isNullOrEmpty() && tags.isNullOrEmpty()) {
            apiClient.getSearchHints(query, mediaTypes, limit, startIndex)
        } else {
            apiClient.getMediaItems(
                parentId = null,
                mediaTypes = mediaTypes,
                genres = genres,
                years = years,
                studioIds = null,
                sortBy = "SortName",
                sortOrder = "Ascending",
                startIndex = startIndex,
                limit = limit,
                searchTerm = query,
                tags = tags,
            )
        }
    }

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        apiClient.findItemByProviderId(provider, id)

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> =
        apiClient.getSearchSuggestions(limit)

    override fun getMediaItemsPaged(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        studioIds: List<String>?,
        sortBy: String,
        sortOrder: String,
        tags: List<String>?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            MediaPagingSource(
                mediaRepository = this,
                parentId = parentId,
                mediaTypes = mediaTypes,
                genres = genres,
                years = years,
                studioIds = studioIds,
                sortBy = sortBy,
                sortOrder = sortOrder,
                tags = tags,
            )
        },
    ).flow

    override fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        tags: List<String>?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            SearchPagingSource(
                mediaRepository = this,
                query = query,
                mediaTypes = mediaTypes,
                genres = genres,
                years = years,
                tags = tags,
            )
        },
    ).flow

    override suspend fun getGenres(parentId: String?): Result<List<Genre>> {
        val cacheKey = "genres_${parentId ?: "root"}"
        genresCache.get(cacheKey)?.let { return Result.success(it) }
        return apiClient.getGenres(parentId).also { result ->
            result.getOrNull()?.let { genresCache.put(cacheKey, it) }
        }
    }

    override suspend fun getStudios(parentId: String?): Result<List<Studio>> {
        val cacheKey = "studios_${parentId ?: "root"}"
        studiosCache.get(cacheKey)?.let { return Result.success(it) }
        return apiClient.getStudios(parentId).also { result ->
            result.getOrNull()?.let { studiosCache.put(cacheKey, it) }
        }
    }

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getItemsByStudio(studioId, mediaTypes, startIndex, limit)

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getArtistAlbums(artistId, limit)

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> =
        apiClient.getAlbumTracks(albumId)

    override suspend fun getMusicVideos(parentId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getMediaItems(
            parentId = parentId,
            mediaTypes = listOf(MediaType.MUSIC_VIDEO),
            limit = limit,
        ).map { it.items }

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getSimilarItems(itemId, limit)

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getInstantMix(itemId, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getItemsByPerson(personId, limit)

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> =
        apiClient.getThemeSongs(itemId)

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> =
        apiClient.getSeasons(seriesId)

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> =
        apiClient.getEpisodes(seriesId, seasonId)

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getCollectionItems(collectionId, startIndex, limit)

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = apiClient.getTags(parentId, startIndex, limit)

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiClient.getFavorites(mediaTypes, limit, startIndex)

    override fun getFavoritesPaged(
        mediaTypes: List<MediaType>?,
    ): Flow<PagingData<MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            enablePlaceholders = false,
            prefetchDistance = PREFETCH_DISTANCE,
        ),
        pagingSourceFactory = {
            FavoritesPagingSource(
                mediaRepository = this,
                mediaTypes = mediaTypes,
            )
        },
    ).flow

    override suspend fun getLyrics(itemId: String): Result<LyricsResult> = apiClient.getLyrics(itemId)

    override suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult> = runCatching {
        val cached = lyricsCacheDao.getByItemId(itemId)
        if (cached != null) {
            val cachedSynced = cached.syncedLyrics
            val cachedPlain = cached.plainLyrics
            if (!cachedSynced.isNullOrBlank()) {
                val lines = parseLrc(cachedSynced)
                if (lines.isNotEmpty()) {
                    return@runCatching LyricsResult(
                        lines = lines,
                        source = LyricsSource.entries.find { it.name == cached.provider } ?: LyricsSource.UNKNOWN,
                    )
                }
            }
            if (!cachedPlain.isNullOrBlank() && cachedSynced.isNullOrBlank()) {
                return@runCatching LyricsResult(
                    lines = cachedPlain.lineSequence().filter { it.isNotBlank() }
                        .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList(),
                    source = LyricsSource.entries.find { it.name == cached.provider } ?: LyricsSource.UNKNOWN,
                )
            }
            if (cachedSynced == null && cachedPlain == null && cached.artistName != null) {
                return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
            }
        }

        val jellyfinResult = apiClient.getLyrics(itemId)
        if (jellyfinResult.isSuccess) {
            val result = jellyfinResult.getOrThrow()
            if (result.lines.isNotEmpty()) {
                cacheLyrics(itemId, result.source, artistName, trackName, duration, result.lines)
                return@runCatching result
            }
        }

        if (artistName.isNullOrBlank() || trackName.isNullOrBlank()) {
            return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
        }

        val isLocal = networkMonitor.networkStatus.value == NetworkStatus.Local
        if (!isLocal) {
            val lrcLibResult = lrcLibApi.getBestMatch(artistName, trackName, duration)
            if (lrcLibResult.isSuccess) {
                val track = lrcLibResult.getOrThrow()
                val trackSynced = track.syncedLyrics
                val trackPlain = track.plainLyrics
                if (track.instrumental) {
                    lyricsCacheDao.upsert(
                        LyricsCacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB.name,
                            artistName = artistName,
                            trackName = trackName,
                            duration = duration,
                            lrcLibId = track.id,
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                    return@runCatching LyricsResult(lines = emptyList(), source = LyricsSource.LRCLIB)
                }
                if (!trackSynced.isNullOrBlank()) {
                    val lines = parseLrc(trackSynced)
                    if (lines.isNotEmpty()) {
                        lyricsCacheDao.upsert(
                            LyricsCacheEntity(
                                itemId = itemId,
                                provider = LyricsSource.LRCLIB.name,
                                artistName = artistName,
                                trackName = trackName,
                                syncedLyrics = trackSynced,
                                plainLyrics = trackPlain,
                                duration = duration,
                                lrcLibId = track.id,
                                fetchedAt = System.currentTimeMillis(),
                            )
                        )
                        return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                    }
                }
                if (!trackPlain.isNullOrBlank()) {
                    val lines = trackPlain.lineSequence().filter { it.isNotBlank() }
                        .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList()
                    lyricsCacheDao.upsert(
                        LyricsCacheEntity(
                            itemId = itemId,
                            provider = LyricsSource.LRCLIB.name,
                            artistName = artistName,
                            trackName = trackName,
                            plainLyrics = trackPlain,
                            duration = duration,
                            lrcLibId = track.id,
                            fetchedAt = System.currentTimeMillis(),
                        )
                    )
                    return@runCatching LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
                }
            }
        }

        lyricsCacheDao.upsert(
            LyricsCacheEntity(
                itemId = itemId,
                provider = LyricsSource.UNKNOWN.name,
                artistName = artistName,
                trackName = trackName,
                duration = duration,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        LyricsResult(lines = emptyList(), source = LyricsSource.UNKNOWN)
    }

    override suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>> =
        lrcLibApi.search(query)

    override suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult> =
        lrcLibApi.getById(lrcLibId).mapCatching { track ->
            val trackSynced = track.syncedLyrics
            val trackPlain = track.plainLyrics
            val lines = if (!trackSynced.isNullOrBlank()) {
                parseLrc(trackSynced)
            } else if (!trackPlain.isNullOrBlank()) {
                trackPlain.lineSequence().filter { it.isNotBlank() }
                    .map { LyricsLine(timeMs = 0L, text = it.trim()) }.toList()
            } else {
                emptyList()
            }
            lyricsCacheDao.upsert(
                LyricsCacheEntity(
                    itemId = itemId,
                    provider = LyricsSource.LRCLIB.name,
                    syncedLyrics = track.syncedLyrics,
                    plainLyrics = track.plainLyrics,
                    lrcLibId = track.id,
                    fetchedAt = System.currentTimeMillis(),
                )
            )
            LyricsResult(lines = lines, source = LyricsSource.LRCLIB)
        }

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = apiClient.getPlaylists(limit)

    override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> =
        apiClient.getPlaylistItems(playlistId, startIndex, limit)

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
    ): Result<String> = apiClient.createPlaylist(name, overview, itemIds)

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = apiClient.updatePlaylist(playlistId, name, overview, isPublic)

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = apiClient.deletePlaylist(playlistId)

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> =
        apiClient.addItemsToPlaylist(playlistId, itemIds)

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> =
        apiClient.removeItemsFromPlaylist(playlistId, entryIds)

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        apiClient.movePlaylistItem(playlistId, entryId, newIndex)

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> =
        apiClient.getSyncPlayGroups()

    override suspend fun joinSyncPlayGroup(groupId: String): Result<Unit> =
        apiClient.joinSyncPlayGroup(groupId)

    override suspend fun leaveSyncPlayGroup(): Result<Unit> =
        apiClient.leaveSyncPlayGroup()

    override suspend fun createSyncPlayGroup(groupName: String): Result<Unit> =
        apiClient.createSyncPlayGroup(groupName)

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> =
        apiClient.getSyncPlayInfo(groupId)

    override suspend fun syncPlayReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
    ): Result<Unit> =
        apiClient.syncPlayReady(positionTicks, isPlaying, playlistItemId)

    override suspend fun syncPlayPause(): Result<Unit> =
        apiClient.syncPlayPause()

    override suspend fun syncPlayUnpause(): Result<Unit> =
        apiClient.syncPlayUnpause()

    override suspend fun syncPlaySeek(positionTicks: Long): Result<Unit> =
        apiClient.syncPlaySeek(positionTicks)

    override suspend fun syncPlayStop(): Result<Unit> =
        apiClient.syncPlayStop()

    override suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayNextItem(playlistItemId)

    override suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayPreviousItem(playlistItemId)

    override suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit> =
        apiClient.syncPlaySetRepeatMode(mode)

    override suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit> =
        apiClient.syncPlaySetShuffleMode(mode)

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> =
        apiClient.syncPlaySetNewQueue(itemIds, playingItemId, mediaSourceId, startPositionTicks)

    override suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit> =
        apiClient.syncPlaySetIgnoreWait(ignore)

    override suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit> =
        apiClient.syncPlayRemoveFromPlaylist(playlistItemId)

    override suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> =
        apiClient.syncPlayMovePlaylistItem(playlistItemId, newIndex)

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> {
        cachedHomeSectionsTimestamp = 0L
        invalidateDetailCache(itemId)
        return apiClient.toggleFavorite(itemId)
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> {
        cachedHomeSectionsTimestamp = 0L
        invalidateDetailCache(itemId)
        return apiClient.markPlayed(itemId)
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> {
        cachedHomeSectionsTimestamp = 0L
        invalidateDetailCache(itemId)
        return apiClient.markUnplayed(itemId)
    }

    override suspend fun deleteMediaItem(itemId: String): Result<Unit> {
        // Drop caches so a stale snapshot isn't served after the file is gone.
        // Home sections may surface this item (Latest / Continue Watching), so
        // force a re-fetch there too.
        cachedHomeSectionsTimestamp = 0L
        invalidateDetailCache(itemId)
        return apiClient.deleteItem(itemId)
    }

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
        addCurrentProgram: Boolean,
        enableFavoriteSorting: Boolean,
        isFavorite: Boolean?,
    ): Result<List<LiveTvChannel>> =
        apiClient.getLiveTvChannels(startIndex, limit, addCurrentProgram, enableFavoriteSorting, isFavorite)

    override suspend fun getRecommendedPrograms(
        filters: ProgramFilters,
        limit: Int,
    ): Result<List<LiveTvProgram>> =
        apiClient.getRecommendedPrograms(filters, limit)

    override suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String?, endDateUtc: String?): Result<List<LiveTvProgram>> =
        apiClient.getLiveTvPrograms(channelId, startDateUtc, endDateUtc)

    override suspend fun getPrograms(channelIds: List<String>, startDateUtc: String, endDateUtc: String): Result<List<LiveTvProgram>> =
        apiClient.getPrograms(channelIds, startDateUtc, endDateUtc)

    override suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int, limit: Int): Result<EpgGuide> =
        apiClient.getLiveTvGuide(startDateUtc, endDateUtc, startIndex, limit)

    override suspend fun getGuideInfo(): Result<GuideInfo> = apiClient.getGuideInfo()

    override suspend fun getRecordings(limit: Int?, isInProgress: Boolean?): Result<List<LiveTvRecording>> =
        apiClient.getRecordings(limit, isInProgress)

    override suspend fun getRecordingFolders(): Result<List<RecordingFolder>> = apiClient.getRecordingFolders()

    override suspend fun getTimers(isActive: Boolean?, isScheduled: Boolean?): Result<List<DvrTimer>> =
        apiClient.getTimers(isActive, isScheduled)

    override suspend fun getSeriesTimers(sortBy: String?): Result<List<DvrSeriesTimer>> =
        apiClient.getSeriesTimers(sortBy)

    override suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer> =
        apiClient.getDefaultTimer(programId)

    override suspend fun createTimer(programId: String): Result<Unit> = apiClient.createTimer(programId)

    override suspend fun createSeriesTimer(programId: String): Result<Unit> = apiClient.createSeriesTimer(programId)

    override suspend fun cancelTimer(timerId: String): Result<Unit> = apiClient.cancelTimer(timerId)

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> = apiClient.cancelSeriesTimer(seriesTimerId)

    private suspend fun cacheLyrics(
        itemId: String,
        source: LyricsSource,
        artistName: String?,
        trackName: String?,
        duration: Double?,
        lines: List<LyricsLine>,
    ) {
        val syncedLrc = lines.joinToString("\n") { line ->
            val min = line.timeMs / 60_000
            val sec = (line.timeMs % 60_000) / 1000.0
            "[%02d:%06.3f] %s".format(min, sec, line.text)
        }
        lyricsCacheDao.upsert(
            LyricsCacheEntity(
                itemId = itemId,
                provider = source.name,
                artistName = artistName,
                trackName = trackName,
                syncedLyrics = syncedLrc,
                duration = duration,
                fetchedAt = System.currentTimeMillis(),
            )
        )
        // Throttle eviction to at most once per hour. deleteOlderThan is a full
        // table scan; firing it on every lyrics fetch (which happens whenever a
        // user opens lyrics for a new track) was walking & re-locking the whole
        // lyrics_cache table unnecessarily. Eviction semantics (rows older than
        // 30 days eventually removed) preserved.
        val now = System.currentTimeMillis()
        if (now - lastLyricsEvictionMs > 60L * 60 * 1000) {
            lastLyricsEvictionMs = now
            try {
                lyricsCacheDao.deleteOlderThan(now - 30L * 24 * 60 * 60 * 1000)
            } catch (e: Exception) {
                Log.d("MediaRepo", "Failed to evict old lyrics cache", e)
            }
        }
    }

    override suspend fun cleanupLyricsCache() {
        try {
            lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            Log.d("MediaRepo", "Failed to cleanup lyrics cache", e)
        }
    }

    override suspend fun invalidateCaches() {
        invalidateDetailCache()
        synchronized(homeSectionsLock) {
            cachedHomeSections = null
            cachedHomeSectionsTimestamp = 0L
            cachedHomeSectionsKey = ""
        }
        // Also clear the secondary caches — they hold user-scoped data (library folders,
        // latest media, genres, studios) that would otherwise leak across user/server
        // switches until their TTL expires.
        libraryFoldersCache.clear()
        latestMediaCache.clear()
        genresCache.clear()
        studiosCache.clear()
    }

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> =
        apiClient.getNewsletterData(sinceDate, limit)

    companion object {
        private const val PAGE_SIZE = 50
        private const val PREFETCH_DISTANCE = 20
        private const val DETAIL_CACHE_MAX_ENTRIES = 30
        /** 2 minutes — short enough that server changes are reflected quickly. */
        private const val DETAIL_CACHE_TTL_MS = 2 * 60 * 1000L
        /** 60 seconds — prevents burst API calls on repeated home screen loads. */
        private const val HOME_SECTIONS_CACHE_TTL_MS = 60 * 1000L
        /** 10 minutes — library folders change rarely during a session. */
        private const val FOLDERS_CACHE_TTL_MS = 10 * 60 * 1000L
        /** 2 minutes — "latest" content should feel fresh on re-entry. */
        private const val LATEST_CACHE_TTL_MS = 2 * 60 * 1000L
        private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{2}\.\d{2,3})]""")

        private fun parseLrc(lrcContent: String): List<LyricsLine> {
            val lines = mutableListOf<LyricsLine>()
            lrcContent.lineSequence().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val times = TIME_REGEX.findAll(line).map { match ->
                    val minutes = match.groupValues[1].toLong()
                    val seconds = match.groupValues[2].toDouble()
                    minutes * 60_000 + (seconds * 1000).toLong()
                }.toList()
                if (times.isEmpty()) return@forEach
                val textStart = line.lastIndexOf(']') + 1
                val text = line.substring(textStart).trim()
                val words = parseInlineWordTimings(text)
                if (text.isEmpty()) {
                    times.forEach { timeMs -> lines.add(LyricsLine(timeMs = timeMs, text = "")) }
                } else {
                    times.forEach { timeMs ->
                        val adjustedWords = if (words.isNotEmpty()) {
                            words.map { it.copy(timeMs = it.timeMs) }
                        } else emptyList()
                        lines.add(
                            LyricsLine(
                                timeMs = timeMs,
                                text = text,
                                words = adjustedWords,
                            )
                        )
                    }
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        /**
         * Parses Enhanced LRC inline word timings:
         * "[00:12.34]Hello [00:12.89]world [00:13.45]test"
         */
        private fun parseInlineWordTimings(text: String): List<com.raulshma.jellyplay.core.model.LyricsWord> {
            if (text.isBlank()) return emptyList()
            val matches = TIME_REGEX.findAll(text).toList()
            if (matches.isEmpty()) return emptyList()
            return matches.mapIndexed { index, match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toDouble()
                val timeMs = minutes * 60_000 + (seconds * 1000).toLong()
                val wordStart = match.range.last + 1
                val wordEnd = matches.getOrNull(index + 1)?.range?.first ?: text.length
                val rawWord = text.substring(wordStart, wordEnd).trim()
                com.raulshma.jellyplay.core.model.LyricsWord(timeMs = timeMs, text = rawWord)
            }.filter { it.text.isNotEmpty() }
        }
    }

    private val photoFolderChildUrlCache = TtlCache<List<String>>(
        maxSize = 200,
        ttlMs = 5 * 60 * 1000L,
    )

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> {
        photoFolderChildUrlCache.get(folderId)?.let { return it }
        val urls = apiClient.getChildItemImageUrls(folderId, limit)
        photoFolderChildUrlCache.put(folderId, urls)
        return urls
    }
}
