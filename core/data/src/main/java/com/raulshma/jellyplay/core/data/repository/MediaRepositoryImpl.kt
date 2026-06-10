package com.raulshma.jellyplay.core.data.repository

import java.util.Collections
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
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
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
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
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.LrcLibApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val lrcLibApi: LrcLibApi,
    private val lyricsCacheDao: LyricsCacheDao,
    private val networkMonitor: NetworkMonitor,
) : MediaRepository {

    // Short-lived in-process cache for MediaDetail. Prevents redundant network calls
    // when the same item is opened from multiple screens (detail + player) in quick
    // succession. TTL is intentionally short (2 min) so server-side changes are
    // reflected promptly.
    private data class CachedDetail(val detail: MediaDetail, val fetchedAt: Long)
    private val detailCache: MutableMap<String, CachedDetail> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CachedDetail>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetail>?): Boolean =
                    size > DETAIL_CACHE_MAX_ENTRIES
            }
        )

    fun invalidateDetailCache(itemId: String? = null) {
        if (itemId != null) {
            detailCache.remove(itemId)
        } else {
            detailCache.clear()
        }
    }

    private data class CachedHomeSections(val sections: List<HomeSection>, val fetchedAt: Long)
    @Volatile
    private var cachedHomeSections: CachedHomeSections? = null
    private val homeSectionsLock = Any()

    override suspend fun getHomeSections(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
    ): Result<List<HomeSection>> {
        val cached = cachedHomeSections
        if (cached != null && android.os.SystemClock.elapsedRealtime() - cached.fetchedAt < HOME_SECTIONS_CACHE_TTL_MS) {
            return Result.success(cached.sections)
        }
        return apiClient.getHomeSections(enabledSections, hiddenLibraryIds).also { result ->
            result.getOrNull()?.let { sections ->
                synchronized(homeSectionsLock) {
                    cachedHomeSections = CachedHomeSections(sections, android.os.SystemClock.elapsedRealtime())
                }
            }
        }
    }

    override suspend fun getLibraryFolders(): Result<List<LibraryFolder>> =
        apiClient.getLibraryFolders()

    override suspend fun getMediaItems(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        sortBy: String,
        sortOrder: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = apiClient.getMediaItems(
        parentId = parentId,
        mediaTypes = mediaTypes,
        genres = genres,
        years = years,
        sortBy = sortBy,
        sortOrder = sortOrder,
        startIndex = startIndex,
        limit = limit,
    )

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> {
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = detailCache[itemId]
        if (cached != null && now - cached.fetchedAt < DETAIL_CACHE_TTL_MS) {
            return Result.success(cached.detail)
        }
        return apiClient.getMediaDetail(itemId).also { result ->
            result.getOrNull()?.let { detail ->
                val existing = detailCache[itemId]
                if (existing == null || existing.fetchedAt < now) {
                    detailCache[itemId] = CachedDetail(detail, android.os.SystemClock.elapsedRealtime())
                }
            }
        }
    }

    override suspend fun search(
        query: String,
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = apiClient.getSearchHints(query, mediaTypes, limit, startIndex)

    override fun getMediaItemsPaged(
        parentId: String?,
        mediaTypes: List<MediaType>?,
        genres: List<String>?,
        years: List<Int>?,
        sortBy: String,
        sortOrder: String,
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
                sortBy = sortBy,
                sortOrder = sortOrder,
            )
        },
    ).flow

    override fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>?,
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
            )
        },
    ).flow

    override suspend fun getGenres(parentId: String?): Result<List<Genre>> =
        apiClient.getGenres(parentId)

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getArtistAlbums(artistId, limit)

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> =
        apiClient.getAlbumTracks(albumId)

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getSimilarItems(itemId, limit)

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> =
        apiClient.getItemsByPerson(personId, limit)

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
        cachedHomeSections = null
        invalidateDetailCache(itemId)
        return apiClient.toggleFavorite(itemId)
    }

    override suspend fun markPlayed(itemId: String): Result<Unit> {
        cachedHomeSections = null
        invalidateDetailCache(itemId)
        return apiClient.markPlayed(itemId)
    }

    override suspend fun markUnplayed(itemId: String): Result<Unit> {
        cachedHomeSections = null
        invalidateDetailCache(itemId)
        return apiClient.markUnplayed(itemId)
    }

    override suspend fun getLiveTvChannels(startIndex: Int, limit: Int): Result<List<LiveTvChannel>> =
        apiClient.getLiveTvChannels(startIndex, limit)

    override suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String?, endDateUtc: String?): Result<List<LiveTvProgram>> =
        apiClient.getLiveTvPrograms(channelId, startDateUtc, endDateUtc)

    override suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int, limit: Int): Result<EpgGuide> =
        apiClient.getLiveTvGuide(startDateUtc, endDateUtc, startIndex, limit)

    override suspend fun getTimers(): Result<List<DvrTimer>> =
        apiClient.getTimers()

    override suspend fun getSeriesTimers(): Result<List<DvrSeriesTimer>> =
        apiClient.getSeriesTimers()

    override suspend fun createTimer(programId: String, channelId: String, startDate: String?, endDate: String?): Result<Unit> =
        apiClient.createTimer(programId, channelId, startDate, endDate)

    override suspend fun cancelTimer(timerId: String): Result<Unit> =
        apiClient.cancelTimer(timerId)

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
        try {
            lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        } catch (_: Exception) {
        }
    }

    override suspend fun cleanupLyricsCache() {
        try {
            lyricsCacheDao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        } catch (_: Exception) {
        }
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
}
