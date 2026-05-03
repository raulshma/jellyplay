package com.raulshma.jellyplay.core.network

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LyricsResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
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

    suspend fun setServer(serverInfo: ServerInfo)

    suspend fun setUser(userInfo: UserInfo)

    suspend fun disconnect()

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

    suspend fun reportPlaybackStart(itemId: String, sessionId: String): Result<Unit>

    suspend fun reportPlaybackProgress(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
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
}
