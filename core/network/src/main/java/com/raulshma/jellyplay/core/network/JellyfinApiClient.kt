package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.CreditTimestamps
import com.raulshma.jellyplay.core.model.IntroTimestamps
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.QuickConnectInfo
import com.raulshma.jellyplay.core.model.QuickConnectState
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.Flow

interface JellyfinApiClient {

    val currentServer: Flow<ServerInfo?>
    val currentUser: Flow<UserInfo?>

    suspend fun connectToServer(address: String): Result<ServerInfo>

    suspend fun authenticateUser(
        serverAddress: String,
        username: String,
        password: String,
    ): Result<UserInfo>

    suspend fun authenticateUser(
        serverInfo: ServerInfo,
        username: String,
        password: String,
    ): Result<UserInfo>

    suspend fun setServer(serverInfo: ServerInfo)

    suspend fun setUser(userInfo: UserInfo)

    suspend fun disconnect()

    suspend fun isQuickConnectEnabled(): Result<Boolean>

    suspend fun initiateQuickConnect(): Result<QuickConnectInfo>

    suspend fun getQuickConnectState(secret: String): Result<QuickConnectState>

    suspend fun authenticateWithQuickConnect(
        serverInfo: ServerInfo,
        secret: String,
    ): Result<UserInfo>

    suspend fun getHomeSections(): Result<List<HomeSection>>

    suspend fun getLatestMedia(parentId: String, limit: Int = 16): Result<List<MediaItem>>

    suspend fun getNextUp(limit: Int = 20): Result<List<MediaItem>>

    suspend fun getContinueWatching(limit: Int = 20): Result<List<MediaItem>>

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

    suspend fun getSearchHints(
        query: String,
        mediaTypes: List<MediaType>? = null,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getGenres(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): Result<List<Genre>>

    suspend fun getItemsByGenre(
        genreId: String,
        mediaTypes: List<MediaType>? = null,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<SearchResult>

    suspend fun getArtistAlbums(artistId: String, limit: Int = 50): Result<List<MediaItem>>

    suspend fun getSimilarItems(itemId: String, limit: Int = 12): Result<List<MediaItem>>

    suspend fun getItemsByPerson(
        personId: String,
        limit: Int = 50,
    ): Result<List<MediaItem>>

    suspend fun getSeasons(seriesId: String): Result<List<MediaItem>>

    suspend fun getEpisodes(
        seriesId: String,
        seasonId: String,
    ): Result<List<MediaItem>>

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

    suspend fun getPlaylists(limit: Int = 50): Result<List<Playlist>>

    suspend fun getPlaylistItems(playlistId: String, startIndex: Int = 0, limit: Int = 50): Result<List<PlaylistItem>>

    suspend fun markPlayed(itemId: String): Result<Unit>

    suspend fun markUnplayed(itemId: String): Result<Unit>

    suspend fun toggleFavorite(itemId: String): Result<Boolean>

    suspend fun reportPlaybackStart(
        itemId: String,
        sessionId: String,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod = com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY,
    ): Result<Unit>

    suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: com.raulshma.jellyplay.core.model.PlayMethod = com.raulshma.jellyplay.core.model.PlayMethod.DIRECT_PLAY,
    ): Result<Unit>

    suspend fun reportPlaybackStopped(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
    ): Result<Unit>

    fun getImageUrl(
        itemId: String,
        imageType: String = "Primary",
        maxWidth: Int = 400,
        tag: String? = null,
    ): String

    fun getBackdropImageUrl(
        itemId: String,
        maxWidth: Int = 1280,
        tag: String? = null,
    ): String

    fun getStreamUrl(
        itemId: String,
        mediaSourceId: String,
        startTimeTicks: Long = 0,
    ): String

    fun getSubtitleDeliveryUrl(
        deliveryUrl: String,
    ): String

    fun getServerUrl(): String?

    fun getAccessToken(): String?

    fun buildSubtitleDeliveryUrl(
        itemId: String,
        mediaSourceId: String,
        index: Int,
        codec: String?,
    ): String

    suspend fun getLiveTvChannels(
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<List<LiveTvChannel>>

    suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String? = null,
        endDateUtc: String? = null,
    ): Result<List<LiveTvProgram>>

    suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<EpgGuide>

    suspend fun getTimers(): Result<List<DvrTimer>>

    suspend fun getSeriesTimers(): Result<List<DvrSeriesTimer>>

    suspend fun createTimer(
        programId: String,
        channelId: String,
        startDate: String? = null,
        endDate: String? = null,
    ): Result<Unit>

    suspend fun cancelTimer(timerId: String): Result<Unit>

    suspend fun getSyncPlayGroups(): Result<List<com.raulshma.jellyplay.core.model.SyncPlayGroup>>

    suspend fun joinSyncPlayGroup(groupId: String): Result<Unit>

    suspend fun leaveSyncPlayGroup(): Result<Unit>

    suspend fun createSyncPlayGroup(groupName: String): Result<Unit>

    suspend fun syncPlayReady(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long? = null,
    ): Result<Unit>

    suspend fun syncPlayBuffering(
        positionTicks: Long = 0L,
        isPlaying: Boolean = false,
        playlistItemId: String? = null,
        whenMs: Long? = null,
    ): Result<Unit>

    suspend fun syncPlayPause(): Result<Unit>

    suspend fun syncPlayUnpause(): Result<Unit>

    suspend fun syncPlaySeek(positionTicks: Long): Result<Unit>

    suspend fun getSyncPlayInfo(groupId: String? = null): Result<com.raulshma.jellyplay.core.model.SyncPlayGroupInfo>

    suspend fun syncPlayStop(): Result<Unit>

    suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlaySetRepeatMode(mode: com.raulshma.jellyplay.core.model.SyncPlayRepeatMode): Result<Unit>

    suspend fun syncPlaySetShuffleMode(mode: com.raulshma.jellyplay.core.model.SyncPlayShuffleMode): Result<Unit>

    suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit>

    suspend fun syncPlayQueue(
        itemIds: List<String>,
        mode: String = "Queue",
    ): Result<Unit>

    suspend fun syncPlaySetPlaylistItem(playlistItemId: String): Result<Unit>

    suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit>

    suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit>

    suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit>

    suspend fun syncPlayPing(pingMs: Long): Result<Unit>

    suspend fun getIntroTimestamps(itemId: String): Result<IntroTimestamps>

    suspend fun getCreditTimestamps(itemId: String): Result<CreditTimestamps>

    suspend fun getMediaSegments(itemId: String): Result<List<MediaSegment>>

    suspend fun getRemoteSubtitles(itemId: String): Result<List<RemoteSubtitleInfo>>

    suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit>

    suspend fun getTrickplayTileImage(itemId: String, width: Int, index: Int): ByteArray?
    
    suspend fun getServerTime(): Result<com.raulshma.jellyplay.core.model.UtcTimeResponse>

    suspend fun postCapabilities(): Result<Unit>
}
