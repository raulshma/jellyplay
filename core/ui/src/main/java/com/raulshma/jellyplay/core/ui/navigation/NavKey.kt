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
    /**
     * Library screen opened from a home-section "See All" action. Carries a
     * [title] to display, an optional [parentId] to scope to a single library
     * (per-library Latest Media), and a pre-applied [sortBy] / [mediaTypes]
     * derived from the source section. Renders the same [LibraryScreen] in
     * "section mode" — see [com.raulshma.jellyplay.feature.library.LibraryViewModel.configureSection].
     */
    @Serializable data class LibrarySection(
        val title: String,
        val parentId: String? = null,
        val collectionType: String? = null,
        val sortBy: String? = null,
        val mediaTypes: List<String> = emptyList(),
    ) : Route()
    @Serializable data object Search : Route()
    @Serializable data object LiveTv : Route()

    @Serializable data class MediaDetail(val itemId: String) : Route()
    @Serializable data class MetadataEditor(val itemId: String) : Route()
    @Serializable data class SeerrDetail(val tmdbId: Int, val mediaType: String) : Route()
    @Serializable data class PersonDetail(val personId: String) : Route()
    /** Sonarr-style series management screen (collapsible seasons, per-episode actions). */
    @Serializable data class ManageSeries(val seriesId: String) : Route()

    @Serializable data class VideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : Route()

    @Serializable data class AudioPlayer(val itemId: String) : Route()

    /**
     * Full-screen remote-control overlay for an active "Play On" (Jellyfin
     * remote session) cast. Reached by tapping the persistent [PlayOnMiniBar].
     * Modal — transient control surface, not a drill-in detail destination.
     */
    @Serializable data object PlayOnCompanion : Route()

    @Serializable data object Downloads : Route()

    @Serializable data object OfflineLibrary : Route()

    @Serializable data object Settings : Route()

    @Serializable data object Onboarding : Route()

    @Serializable data class ServerManagement(val highlightSettingId: String? = null) : Route()
    @Serializable data class UserManagement(val highlightSettingId: String? = null) : Route()
    @Serializable data class SeerrSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class AppearanceSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class PinnedHomeSections(val highlightSettingId: String? = null) : Route()
    @Serializable data class HomeLayoutPresets(val highlightSettingId: String? = null) : Route()
    @Serializable data class LibraryHomeSections(val highlightSettingId: String? = null) : Route()
    @Serializable data class PlaybackSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class AudioSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class LanguageSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class NotificationSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class StorageSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class SecuritySettings(val highlightSettingId: String? = null) : Route()
    /**
     * Privacy & Data hub — consolidates scattered destructive data actions
     * (clear cache, clear image cache, clear search history, factory reset,
     * sign out) into a single discoverable screen. Re-exposes the same
     * actions the dedicated screens already perform; it does not move or
     * delete them.
     */
    @Serializable data class PrivacyData(val highlightSettingId: String? = null) : Route()
    @Serializable data class BackupSettings(val highlightSettingId: String? = null) : Route()
    @Serializable data class ExperimentalSettings(val highlightSettingId: String? = null) : Route()

    /**
     * Factory-reset review screen. Lists every preference category with its
     * current-vs-factory values and exposes Reset-All + per-category Reset
     * actions. Reached from [BackupSettings].
     */
    @Serializable data class FactoryReset(val highlightSettingId: String? = null) : Route()

    /**
     * Integrations hub — top-level list of every third-party service JellyPlay
     * talks to (Seerr, Radarr/Sonarr). Each entry drills into its own screen
     * ([SeerrSettings] / [ArrSettings]).
     */
    @Serializable data class Integrations(val highlightSettingId: String? = null) : Route()

    /**
     * Direct Radarr/Sonarr integration settings — manual server override +
     * Seerr auto-discovery toggle. Gated by
     * [com.raulshma.jellyplay.core.model.ExperimentalFeature.DIRECT_ARR_INTEGRATION].
     */
    @Serializable data class ArrSettings(val highlightSettingId: String? = null) : Route()

    /**
     * Direct subtitle-provider integration settings — API keys + enable toggles
     * for Wyzie Subs and OpenSubtitles, used by the in-player + edit-metadata
     * subtitle search. Mirrors [ArrSettings] / [SeerrSettings].
     */
    @Serializable data class SubtitleProviderSettings(val highlightSettingId: String? = null) : Route()

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

    /**
     * Drill-in detail screen for a Live TV channel, reached from the home /
     * search / related-items item-routing surfaces. Shows a today program
     * timeline with a live progress bar and a current-program backdrop, and
     * tunes the live channel via [LiveTvChannelPlayer] on play.
     */
    @Serializable data class ChannelDetail(
        val channelId: String,
        val channelName: String = "",
    ) : Route()

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
    @Serializable data object Users : Route()
    @Serializable data class UserDetail(val userId: String) : Route()
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

    @Serializable data object ArrQueue : Route()

    /**
     * Combined Sonarr + Radarr upcoming-releases calendar. Modal entry gated
     * by [com.raulshma.jellyplay.core.model.ExperimentalFeature.DIRECT_ARR_INTEGRATION],
     * mirroring [ArrQueue] / [Requests].
     */
    @Serializable data object UpcomingCalendar : Route()

    @Serializable data object Shortcuts : Route()

    @Serializable data object SubtitleTester : Route()
}

/**
 * The string key under which [Route.Shortcuts] is hidden via
 * [com.raulshma.jellyplay.core.model.UserPreferences.hiddenNavItems].
 *
 * Equals `Route.Shortcuts::class.simpleName`. Centralised here so the nav-bar composition
 * (`JellyPlayApp`) and the customization UI (`NavigationCustomizationGroup`) reference one
 * source of truth instead of a bare `"Shortcuts"` literal that can silently drift from the
 * route's class name.
 */
val SHORTCUTS_NAV_KEY: String = Route.Shortcuts::class.simpleName!!

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

/**
 * Routes that render in the bare full-screen layout (player / onboarding /
 * ambient / photo viewer). Centralised so the layout-branch picker in
 * `MainContent` can match by membership rather than re-listing routes, and so
 * a back stack can be scanned for *any* full-screen route (not just the top
 * entry) — see `MainContent` for why that matters for the player↔subtitle-tester
 * round trip.
 */
val Route.isFullScreen: Boolean
    get() = this is Route.VideoPlayer || this is Route.LiveTvChannelPlayer ||
        this is Route.AudioPlayer || this is Route.Ambient ||
        this is Route.Onboarding || this is Route.PhotoViewer

/**
 * Routes that mount a *player* — i.e. hold live playback state (ExoPlayer /
 * equalizer / live stream) that does **not** round-trip across state loss.
 * Unlike [isFullScreen], this excludes transient full-screen overlays
 * (onboarding / ambient / photo) whose restore is harmless. Used by
 * [rememberNavigationState] to strip only the routes that would auto-play a
 * stale item/position after the OS reclaimed their in-memory state.
 */
val Route.isPlayer: Boolean
    get() = this is Route.VideoPlayer || this is Route.LiveTvChannelPlayer ||
        this is Route.AudioPlayer

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
        Route.PlayOnCompanion,
        Route.SeerrSettings,
        Route.Integrations,
        Route.ArrSettings,
        Route.SubtitleProviderSettings,
        Route.AdminDashboard,
        Route.ScheduledTasks,
        Route.Devices,
        Route.Logs,
        Route.Requests,
        Route.Shortcuts -> true
        Route.Users -> true
        Route.ArrQueue -> true
        Route.UpcomingCalendar -> true
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
        is Route.ManageSeries,
        is Route.MediaInfo,
        is Route.LibrarySection,
        is Route.CollectionDetail,
        is Route.ArtistDetail,
        is Route.AlbumDetail,
        is Route.SmartPlaylistDetail,
        is Route.MoodPlaylistDetail,
        is Route.PlaylistDetail,
        is Route.GenreDetail,
        is Route.StudioDetail,
        is Route.NewsletterSectionList,
        is Route.UserStatisticsDetail,
        is Route.ChannelDetail,
        is Route.UserDetail -> true
        else -> false
    }

/**
 * Maps a [Route] to a coarse [NavRouteClass] for transition selection.
 * Ambient is checked first (before fullscreen) because [Route.Ambient] is a
 * distinct immersive overlay that should always cross-fade.
 */
val Route?.toNavRouteClass: com.raulshma.jellyplay.core.ui.animation.NavRouteClass
    get() = when {
        this == null ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.DEFAULT
        this is Route.Ambient ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.AMBIENT
        this.isFullScreen ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.FULLSCREEN
        this.isModal ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.MODAL
        this.isDetail ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.DETAIL
        com.raulshma.jellyplay.core.ui.navigation.ALL_TOP_LEVEL_ROUTE_KEYS.contains(this) ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.TOP_LEVEL_TAB
        else ->
            com.raulshma.jellyplay.core.ui.animation.NavRouteClass.DEFAULT
    }
