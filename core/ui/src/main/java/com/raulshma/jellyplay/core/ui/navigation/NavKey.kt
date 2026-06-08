package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed class Route : NavKey {

    @Serializable data object ServerList : Route()
    @Serializable data object AddServer : Route()
    @Serializable data class Login(val serverAddress: String) : Route()
    @Serializable data class QuickConnect(val serverAddress: String) : Route()
    @Serializable data class UserSelection(
        val serverId: String,
        val serverAddress: String,
        val serverName: String,
    ) : Route()

    @Serializable data object Home : Route()
    @Serializable data object Library : Route()
    @Serializable data object Search : Route()
    @Serializable data object LiveTv : Route()

    @Serializable data class MediaDetail(val itemId: String) : Route()
    @Serializable data class MetadataEditor(val itemId: String) : Route()
    @Serializable data class SeerrDetail(val tmdbId: Int, val mediaType: String) : Route()
    @Serializable data class PersonDetail(val personId: String) : Route()

    @Serializable data class VideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : Route()

    @Serializable data class AudioPlayer(val itemId: String) : Route()

    @Serializable data object Downloads : Route()

    @Serializable data object OfflineLibrary : Route()

    @Serializable data class OfflineSeries(val seriesId: String) : Route()

    @Serializable data object Settings : Route()

    @Serializable data object Onboarding : Route()

    @Serializable data object ServerManagement : Route()
    @Serializable data object UserManagement : Route()
    @Serializable data object SeerrSettings : Route()
    @Serializable data object AppearanceSettings : Route()
    @Serializable data object PlaybackSettings : Route()
    @Serializable data object AudioSettings : Route()
    @Serializable data object LanguageSettings : Route()
    @Serializable data object NotificationSettings : Route()
    @Serializable data object StorageSettings : Route()
    @Serializable data object SecuritySettings : Route()
    @Serializable data object BackupSettings : Route()

    @Serializable data object Artists : Route()
    @Serializable data object Albums : Route()
    @Serializable data object Tracks : Route()
    @Serializable data object Genres : Route()
    @Serializable data class ArtistDetail(val artistId: String) : Route()
    @Serializable data class AlbumDetail(val albumId: String) : Route()
    @Serializable data object SmartPlaylists : Route()
    @Serializable data class SmartPlaylistDetail(val playlistId: String) : Route()
    @Serializable data object MoodPlaylists : Route()
    @Serializable data class MoodPlaylistDetail(val playlistId: String) : Route()
    @Serializable data class CollectionDetail(val collectionId: String) : Route()
    @Serializable data object Playlists : Route()
    @Serializable data class PlaylistDetail(val playlistId: String, val playlistName: String = "") : Route()

    @Serializable data class GenreDetail(val genreId: String, val genreName: String = "") : Route()

    @Serializable data class OfflinePlayer(
        val filePath: String,
        val title: String,
    ) : Route()

    @Serializable data class LiveTvChannelPlayer(
        val channelId: String,
        val channelName: String,
    ) : Route()

    @Serializable data object LiveTvGuide : Route()

    @Serializable data object Dvr : Route()

    @Serializable data object SyncPlay : Route()

    @Serializable data class Ambient(
        val imageUrl: String? = null,
        val title: String = "",
        val artist: String = "",
    ) : Route()

    @Serializable data object MusicBrowse : Route()

    @Serializable data object AdminDashboard : Route()
    @Serializable data object ScheduledTasks : Route()
    @Serializable data object Devices : Route()
    @Serializable data object Logs : Route()
    @Serializable data object UserStatistics : Route()
    @Serializable data class UserStatisticsDetail(val userId: String) : Route()
    @Serializable data object StaleMedia : Route()
    @Serializable data object WatchedMediaCleanup : Route()

    @Serializable data object Newsletter : Route()

    @Serializable data class NewsletterSectionList(
        val sectionType: String,
    ) : Route()

    @Serializable data object Favorites : Route()

    @Serializable data class MediaInfo(val itemId: String) : Route()

    @Serializable data object About : Route()

    @Serializable data object Licenses : Route()

    @Serializable data object WatchProgressHeatmap : Route()
}

val VIDEO_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.Library to "Library",
    Route.Search to "Search",
    Route.LiveTv to "Live TV",
)

val MUSIC_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.MusicBrowse to "Browse",
    Route.Search to "Search",
)

val TOP_LEVEL_ROUTES = VIDEO_TOP_LEVEL_ROUTES

val ALL_TOP_LEVEL_ROUTE_KEYS: Set<Route> =
    VIDEO_TOP_LEVEL_ROUTES.keys.union(MUSIC_TOP_LEVEL_ROUTES.keys)
