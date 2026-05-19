package com.raulshma.jellyplay.core.data.repository

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import kotlinx.coroutines.flow.Flow

interface MediaRepository {

    suspend fun getHomeSections(): Result<List<HomeSection>>

    suspend fun getLibraryFolders(): Result<List<LibraryFolder>>

    suspend fun getMediaItems(
        parentId: String? = null,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    suspend fun search(
        query: String,
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
    ): Result<SearchResult>

    fun getMediaItemsPaged(
        parentId: String? = null,
        mediaTypes: List<MediaType>? = null,
        genres: List<String>? = null,
        years: List<Int>? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
    ): Flow<PagingData<MediaItem>>

    fun searchPaged(
        query: String,
        mediaTypes: List<MediaType>? = null,
    ): Flow<PagingData<MediaItem>>

    suspend fun getGenres(parentId: String? = null): Result<List<Genre>>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    suspend fun getItemsByPerson(personId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>

    suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>>

    suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getTags(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<String>>

    suspend fun getFavorites(
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getLyrics(itemId: String): Result<LyricsResult>

    suspend fun getLyricsWithFallback(
        itemId: String,
        artistName: String?,
        trackName: String?,
        duration: Double?,
    ): Result<LyricsResult>

    suspend fun searchLyrics(query: String): Result<List<LrcLibTrack>>

    suspend fun getLyricsById(lrcLibId: Long, itemId: String): Result<LyricsResult>

    suspend fun getPlaylists(limit: Int = 50): Result<List<Playlist>>

    suspend fun getPlaylistItems(playlistId: String, startIndex: Int = 0, limit: Int = 50): Result<List<PlaylistItem>>

    suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>>

    suspend fun joinSyncPlayGroup(groupId: String): Result<Unit>

    suspend fun leaveSyncPlayGroup(): Result<Unit>

    suspend fun createSyncPlayGroup(groupName: String): Result<Unit>

    suspend fun getSyncPlayInfo(groupId: String? = null): Result<SyncPlayGroupInfo>

    suspend fun syncPlayReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
    ): Result<Unit>

    suspend fun syncPlayPause(): Result<Unit>

    suspend fun syncPlayUnpause(): Result<Unit>

    suspend fun syncPlaySeek(positionTicks: Long): Result<Unit>

    suspend fun syncPlayStop(): Result<Unit>

    suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit>

    suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit>

    suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit>

    suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit>

    suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit>

    suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit>

    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    suspend fun getLiveTvChannels(startIndex: Int = 0, limit: Int = 50): Result<List<LiveTvChannel>>

    suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String? = null, endDateUtc: String? = null): Result<List<LiveTvProgram>>

    suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int = 0, limit: Int = 50): Result<EpgGuide>

    suspend fun getTimers(): Result<List<DvrTimer>>

    suspend fun getSeriesTimers(): Result<List<DvrSeriesTimer>>

    suspend fun createTimer(programId: String, channelId: String, startDate: String? = null, endDate: String? = null): Result<Unit>

    suspend fun cancelTimer(timerId: String): Result<Unit>
}
