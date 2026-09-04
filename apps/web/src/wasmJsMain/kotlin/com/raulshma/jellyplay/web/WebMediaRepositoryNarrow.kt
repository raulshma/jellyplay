package com.raulshma.jellyplay.web

import androidx.paging.PagingData
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.NewsletterRepository
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.ItemKindFilter
import com.raulshma.jellyplay.core.model.LibraryFilters
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.model.Playlist
import com.raulshma.jellyplay.core.model.PlaylistItem
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.Studio
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import kotlinx.coroutines.flow.Flow

/**
 * Wave 16C: the web shell's [MediaRepository] binding — deliberately NARROW.
 *
 * WHY NARROW: the production [com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl]
 * is Room-backed (offline caches, user-data writes, paging) and Room has no
 * wasm build — the whole MediaDetail cluster stays off web this wave. The ONE
 * member the web shell actually needs is [findItemByProviderId], the Seerr
 * detail screen's cross-link that resolves a TMDB/TVDB/IMDB id to a Jellyfin
 * item id; it is a pure network passthrough (JVM impl ~line 557 →
 * apiClient.findItemByProviderId) and [KtorWasmLibraryApiClient] already
 * implements the identical wire call, so the delegation below is the same
 * network behavior minus the Room layer.
 *
 * WHO CONSUMES IT: [com.raulshma.jellyplay.feature.details.SeerrDetailViewModel]
 * only (its `resolveJellyfinItemId` best-effort "Available" cross-link).
 *
 * IF ANOTHER MEMBER IS CALLED: loud [UnsupportedOperationException], never a
 * silently-wrong answer — a crash in development is the honest signal that a
 * code path escaped the web cut, matching the Room-blocked cluster's
 * off-web posture.
 */
internal class WebMediaRepositoryNarrow(
    private val libraryApiClient: LibraryApiClient,
) : MediaRepository,
    LiveTvRepository,
    SyncPlayRepository,
    NewsletterRepository,
    PlaylistRepository {

    private fun offWeb(member: String): Nothing = throw UnsupportedOperationException(
        "MediaRepository on web serves only findItemByProviderId (SeerrDetail cross-link); " +
            "Room-backed impl is jvm-only — '$member' has no web story (wave 16C cut)",
    )

    // ── The one served member ──────────────────────────────────────────────

    override suspend fun findItemByProviderId(provider: String, id: String): Result<String?> =
        libraryApiClient.findItemByProviderId(provider, id)

    // ── MediaRepository ────────────────────────────────────────────────────

    override suspend fun getHomeSections(
        query: HomeSectionQuery,
        force: Boolean,
    ): Result<HomeSectionsResult> = offWeb("getHomeSections")

    override suspend fun getCachedHomeSections(query: HomeSectionQuery): HomeSectionsResult? = offWeb("getCachedHomeSections")

    override suspend fun getLibraryFolders(force: Boolean): Result<List<LibraryFolder>> = offWeb("getLibraryFolders")

    override suspend fun getLatestMedia(parentId: String, limit: Int): Result<List<MediaItem>> = offWeb("getLatestMedia")

    override suspend fun getMediaItems(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        startIndex: Int,
        limit: Int,
        kindFilter: ItemKindFilter,
    ): Result<SearchResult> = offWeb("getMediaItems")

    override suspend fun getMediaDetail(itemId: String, force: Boolean): Result<MediaDetail> = offWeb("getMediaDetail")

    override suspend fun getIntros(itemId: String): Result<List<MediaItem>> = offWeb("getIntros")

    override suspend fun getSpecialFeatures(itemId: String): Result<List<MediaItem>> = offWeb("getSpecialFeatures")

    override suspend fun search(
        query: String,
        filters: LibraryFilters,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = offWeb("search")

    override suspend fun getSearchSuggestions(limit: Int): Result<SearchResult> = offWeb("getSearchSuggestions")

    override fun getMediaItemsPaged(
        parentId: String?,
        filters: LibraryFilters,
        studioIds: List<String>?,
        kindFilter: ItemKindFilter,
    ): Flow<PagingData<MediaItem>> = offWeb("getMediaItemsPaged")

    override fun searchPaged(query: String, filters: LibraryFilters): Flow<PagingData<MediaItem>> = offWeb("searchPaged")

    override suspend fun getGenres(parentId: String?, force: Boolean): Result<List<Genre>> = offWeb("getGenres")

    override suspend fun getStudios(parentId: String?): Result<List<Studio>> = offWeb("getStudios")

    override suspend fun getItemsByStudio(
        studioId: String,
        mediaTypes: List<MediaType>?,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = offWeb("getItemsByStudio")

    override suspend fun getArtistAlbums(artistId: String, limit: Int): Result<List<MediaItem>> = offWeb("getArtistAlbums")

    override suspend fun getAlbumTracks(albumId: String): Result<List<MediaItem>> = offWeb("getAlbumTracks")

    override suspend fun getMusicVideos(parentId: String, limit: Int): Result<List<MediaItem>> = offWeb("getMusicVideos")

    override suspend fun getSimilarItems(itemId: String, limit: Int): Result<List<MediaItem>> = offWeb("getSimilarItems")

    override suspend fun getInstantMix(itemId: String, limit: Int): Result<List<MediaItem>> = offWeb("getInstantMix")

    override suspend fun getItemsByPerson(personId: String, limit: Int): Result<List<MediaItem>> = offWeb("getItemsByPerson")

    override suspend fun getThemeSongs(itemId: String): Result<List<MediaItem>> = offWeb("getThemeSongs")

    override suspend fun getSeasons(seriesId: String): Result<List<MediaItem>> = offWeb("getSeasons")

    override suspend fun getEpisodes(seriesId: String, seasonId: String): Result<List<MediaItem>> = offWeb("getEpisodes")

    override suspend fun getAllEpisodesGrouped(seriesId: String): Result<Map<String, List<MediaItem>>> = offWeb("getAllEpisodesGrouped")

    override suspend fun getCollectionItems(
        collectionId: String,
        startIndex: Int,
        limit: Int,
    ): Result<SearchResult> = offWeb("getCollectionItems")

    override suspend fun getCollections(limit: Int): Result<List<CollectionSummary>> = offWeb("getCollections")

    override suspend fun createCollection(name: String, itemIds: List<String>): Result<String> = offWeb("createCollection")

    override suspend fun addItemsToCollection(collectionId: String, itemIds: List<String>): Result<Unit> = offWeb("addItemsToCollection")

    override suspend fun getTags(
        parentId: String?,
        startIndex: Int,
        limit: Int,
    ): Result<List<String>> = offWeb("getTags")

    override suspend fun getFavorites(
        mediaTypes: List<MediaType>?,
        limit: Int,
        startIndex: Int,
    ): Result<SearchResult> = offWeb("getFavorites")

    override fun getFavoritesPaged(mediaTypes: List<MediaType>?): Flow<PagingData<MediaItem>> = offWeb("getFavoritesPaged")

    // PROPERTY overrides must throw LAZILY (getter, not initializer): an
    // eager `= offWeb(...)` runs at construction, so merely RESOLVING this
    // single (SeerrDetailViewModel's ctor dep) exploded before the screen
    // ever loaded — the browser pass caught exactly that (Koin's
    // InstanceCreationException wrapping this throw). Fun members are lazy
    // by nature; this getter keeps the same loud-throw contract.
    override val userDataChanges: Flow<UserDataChange> get() = offWeb("userDataChanges")

    override fun notifyUserDataChanged(itemIds: List<String>) = offWeb("notifyUserDataChanged")

    override suspend fun toggleFavorite(itemId: String): Result<Boolean> = offWeb("toggleFavorite")

    override suspend fun markPlayed(itemId: String): Result<Unit> = offWeb("markPlayed")

    override suspend fun markUnplayed(itemId: String): Result<Unit> = offWeb("markUnplayed")

    override suspend fun markSeasonPlayed(seasonId: String, seriesId: String): Result<Unit> = offWeb("markSeasonPlayed")

    override suspend fun markSeasonUnplayed(seasonId: String, seriesId: String): Result<Unit> = offWeb("markSeasonUnplayed")

    override suspend fun getPhotoFolderChildImageUrls(folderId: String, limit: Int): List<String> = offWeb("getPhotoFolderChildImageUrls")

    // ── LiveTvRepository ───────────────────────────────────────────────────

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
        addCurrentProgram: Boolean,
        enableFavoriteSorting: Boolean,
        isFavorite: Boolean?,
    ): Result<List<LiveTvChannel>> = offWeb("getLiveTvChannels")

    override suspend fun getRecommendedPrograms(
        filters: ProgramFilters,
        limit: Int,
    ): Result<List<LiveTvProgram>> = offWeb("getRecommendedPrograms")

    override suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String?,
        endDateUtc: String?,
    ): Result<List<LiveTvProgram>> = offWeb("getLiveTvPrograms")

    override suspend fun getPrograms(
        channelIds: List<String>,
        startDateUtc: String,
        endDateUtc: String,
    ): Result<List<LiveTvProgram>> = offWeb("getPrograms")

    override suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int,
        limit: Int,
    ): Result<EpgGuide> = offWeb("getLiveTvGuide")

    override suspend fun getGuideInfo(): Result<GuideInfo> = offWeb("getGuideInfo")

    override suspend fun getRecordings(limit: Int?, isInProgress: Boolean?): Result<List<LiveTvRecording>> = offWeb("getRecordings")

    override suspend fun deleteRecording(recordingId: String): Result<Unit> = offWeb("deleteRecording")

    override suspend fun getTimers(isActive: Boolean?, isScheduled: Boolean?): Result<List<DvrTimer>> = offWeb("getTimers")

    override suspend fun getSeriesTimers(sortBy: String?): Result<List<DvrSeriesTimer>> = offWeb("getSeriesTimers")

    override suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer> = offWeb("getDefaultTimer")

    override suspend fun createTimer(programId: String): Result<Unit> = offWeb("createTimer")

    override suspend fun createSeriesTimer(programId: String): Result<Unit> = offWeb("createSeriesTimer")

    override suspend fun cancelTimer(timerId: String): Result<Unit> = offWeb("cancelTimer")

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> = offWeb("cancelSeriesTimer")

    // ── SyncPlayRepository ─────────────────────────────────────────────────

    override suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>> = offWeb("getSyncPlayGroups")

    override suspend fun joinSyncPlayGroup(groupId: String): Result<Unit> = offWeb("joinSyncPlayGroup")

    override suspend fun leaveSyncPlayGroup(): Result<Unit> = offWeb("leaveSyncPlayGroup")

    override suspend fun createSyncPlayGroup(groupName: String): Result<Unit> = offWeb("createSyncPlayGroup")

    override suspend fun getSyncPlayInfo(groupId: String?): Result<SyncPlayGroupInfo> = offWeb("getSyncPlayInfo")

    override suspend fun syncPlayReady(
        positionTicks: Long,
        isPlaying: Boolean,
        playlistItemId: String?,
    ): Result<Unit> = offWeb("syncPlayReady")

    override suspend fun syncPlayPause(): Result<Unit> = offWeb("syncPlayPause")

    override suspend fun syncPlayUnpause(): Result<Unit> = offWeb("syncPlayUnpause")

    override suspend fun syncPlaySeek(positionTicks: Long): Result<Unit> = offWeb("syncPlaySeek")

    override suspend fun syncPlayStop(): Result<Unit> = offWeb("syncPlayStop")

    override suspend fun syncPlayNextItem(playlistItemId: String): Result<Unit> = offWeb("syncPlayNextItem")

    override suspend fun syncPlayPreviousItem(playlistItemId: String): Result<Unit> = offWeb("syncPlayPreviousItem")

    override suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit> = offWeb("syncPlaySetRepeatMode")

    override suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit> = offWeb("syncPlaySetShuffleMode")

    override suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String?,
        startPositionTicks: Long,
    ): Result<Unit> = offWeb("syncPlaySetNewQueue")

    override suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit> = offWeb("syncPlaySetIgnoreWait")

    override suspend fun syncPlayRemoveFromPlaylist(playlistItemId: String): Result<Unit> = offWeb("syncPlayRemoveFromPlaylist")

    override suspend fun syncPlayMovePlaylistItem(playlistItemId: String, newIndex: Int): Result<Unit> = offWeb("syncPlayMovePlaylistItem")

    // ── NewsletterRepository ───────────────────────────────────────────────

    override suspend fun getNewsletterData(sinceDate: String, limit: Int): Result<NewsletterData> = offWeb("getNewsletterData")

    override suspend fun sendNewsletter(): Result<Unit> = offWeb("sendNewsletter")

    override suspend fun sendTestNewsletter(): Result<Unit> = offWeb("sendTestNewsletter")

    // ── PlaylistRepository ─────────────────────────────────────────────────

    override suspend fun getPlaylists(limit: Int): Result<List<Playlist>> = offWeb("getPlaylists")

    override suspend fun getPlaylistItems(playlistId: String, startIndex: Int, limit: Int): Result<List<PlaylistItem>> = offWeb("getPlaylistItems")

    override suspend fun createPlaylist(
        name: String,
        overview: String?,
        itemIds: List<String>,
        mediaType: MediaType,
    ): Result<String> = offWeb("createPlaylist")

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        overview: String?,
        isPublic: Boolean?,
    ): Result<Unit> = offWeb("updatePlaylist")

    override suspend fun deletePlaylist(playlistId: String): Result<Unit> = offWeb("deletePlaylist")

    override suspend fun addItemsToPlaylist(playlistId: String, itemIds: List<String>): Result<Unit> = offWeb("addItemsToPlaylist")

    override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: List<String>): Result<Unit> = offWeb("removeItemsFromPlaylist")

    override suspend fun movePlaylistItem(playlistId: String, entryId: String, newIndex: Int): Result<Unit> = offWeb("movePlaylistItem")

}


/**
 * The web shell's details platform pick (wave 16C): binds the narrow
 * [MediaRepository] over `networkWasmModule`'s [LibraryApiClient]. Named with
 * the `web` platform prefix per the KoinModuleRegistrationGuardTest rule —
 * platform-prefixed modules are the app's own to place, shared feature/core
 * names must resolve to shared definitions.
 *
 * Registered alongside `detailsModule` in Main.kt. All other details-module
 * defs stay latent on web: only [com.raulshma.jellyplay.feature.details.SeerrDetailViewModel]'s
 * dependency closure is ever resolved in the browser.
 */
fun webDetailsPlatformModule(): org.koin.core.module.Module = org.koin.dsl.module {
    single<MediaRepository> { WebMediaRepositoryNarrow(libraryApiClient = get()) }
}
