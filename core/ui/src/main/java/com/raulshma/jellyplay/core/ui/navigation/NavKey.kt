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
    @Serializable data class LibraryBrowse(
        val folderId: String,
        val folderName: String,
        val collectionType: String? = null,
    ) : Route()
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

    /** Offline detail screen for a single downloaded movie/album/track.
     *  Mirrors the online MediaDetail layout but reads entirely from the
     *  on-device offline store. */
    @Serializable data class OfflineDetail(val itemId: String) : Route()

    @Serializable data object Settings : Route()

    @Serializable data object Onboarding : Route()

    @Serializable data class ServerManagement(val highlightSettingId: String? = null) : Route()
    @Serializable data class UserManagement(val highlightSettingId: String? = null) : Route()
    @Serializable data class SeerrSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class AppearanceSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class PinnedHomeSections(val highlightSettingId: String? = null) : Route()
    @Serializable data class HomeLayoutPresets(val highlightSettingId: String? = null) : Route()
    @Serializable data class PlaybackSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class AudioSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class LanguageSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class NotificationSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class StorageSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class SecuritySettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class BackupSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class ExperimentalSettings(val highlightSettingId: String? = null) : Route()

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

    @Serializable data class StudioDetail(val studioId: String, val studioName: String = "") : Route()

    @Serializable data class LiveTvChannelPlayer(
        val channelId: String,
        val channelName: String,
        // Optional stream overrides. Defaulted so existing two-arg call sites
        // (LiveTvNavigation.kt) keep compiling; when a caller has a preferred
        // audio/subtitle index (e.g. a "default track" preference) it is now
        // honoured by the player exactly as Route.VideoPlayer honours it,
        // closing the previous inconsistency between the two entry points.
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
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

    @Serializable data object Plugins : Route()
    @Serializable data class PluginDetail(val pluginId: String, val pluginName: String) : Route()
    @Serializable data class PluginConfig(val pluginId: String, val pluginName: String) : Route()

    @Serializable data object Newsletter : Route()

    @Serializable data class NewsletterSectionList(
        val sectionType: String,
    ) : Route()

    @Serializable data object Favorites : Route()

    @Serializable data class MediaInfo(val itemId: String) : Route()

    @Serializable data class PhotoAlbum(val parentId: String, val folderName: String = "") : Route()
    @Serializable data class PhotoViewer(val itemId: String, val parentId: String? = null) : Route()

    @Serializable data object About : Route()

    @Serializable data object Licenses : Route()

    @Serializable data object WatchProgressHeatmap : Route()

    @Serializable data object Requests : Route()

    @Serializable data object Shortcuts : Route()
}

val VIDEO_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.Library to "Library",
    Route.Search to "Search",
    Route.LiveTv to "Live TV",
    Route.Shortcuts to "Shortcuts",
)

val MUSIC_TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.MusicBrowse to "Browse",
    Route.Search to "Search",
)

val TOP_LEVEL_ROUTES = VIDEO_TOP_LEVEL_ROUTES

val ALL_TOP_LEVEL_ROUTE_KEYS: Set<Route> =
    VIDEO_TOP_LEVEL_ROUTES.keys.union(MUSIC_TOP_LEVEL_ROUTES.keys)

/**
 * Routes that present as a modal/bottom-sheet-style overlay rather than a
 * push-on-the-stack navigation. Centralised here so the transition
 * `transitionSpec` and `popTransitionSpec` lambdas do not duplicate the set;
 * adding a new modal route is a single-line change.
 */
val Route.isModal: Boolean
    get() = when (this) {
        Route.Settings,
        Route.Downloads,
        Route.SyncPlay,
        Route.SeerrSettings,
        Route.AdminDashboard,
        Route.ScheduledTasks,
        Route.Devices,
        Route.Logs,
        Route.Requests -> true
        else -> false
    }

/**
 * Detail routes (MediaDetail, PersonDetail, AlbumDetail, etc.) — "drill into"
 * navigations rather than modal or top-level tab switches.
 */
val Route.isDetail: Boolean
    get() = when (this) {
        is Route.MediaDetail,
        is Route.MetadataEditor,
        is Route.SeerrDetail,
        is Route.PersonDetail,
        is Route.MediaInfo,
        is Route.CollectionDetail,
        is Route.OfflineSeries,
        is Route.OfflineDetail,
        is Route.ArtistDetail,
        is Route.AlbumDetail,
        is Route.SmartPlaylistDetail,
        is Route.MoodPlaylistDetail,
        is Route.PlaylistDetail,
        is Route.GenreDetail,
        is Route.StudioDetail,
        is Route.NewsletterSectionList,
        is Route.UserStatisticsDetail -> true
        else -> false
    }

/** Detail-route class simple names — used by scene-key inspection where the
 *  typed [Route] is not available (NavKey.toString() substring match). */
val DETAIL_ROUTE_CLASS_NAMES: Set<String> = setOf(
    "MediaDetail",
    "MetadataEditor",
    "SeerrDetail",
    "PersonDetail",
    "MediaInfo",
    "CollectionDetail",
    "OfflineSeries",
    "OfflineDetail",
    "ArtistDetail",
    "AlbumDetail",
    "SmartPlaylistDetail",
    "MoodPlaylistDetail",
    "PlaylistDetail",
    "GenreDetail",
    "StudioDetail",
    "NewsletterSectionList",
    "UserStatisticsDetail",
)
