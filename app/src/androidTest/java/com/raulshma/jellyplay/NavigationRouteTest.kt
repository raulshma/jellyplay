package com.raulshma.jellyplay

import androidx.navigation3.runtime.serialization.NavKeySerializer
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteTest {

    /**
     * A representative instance of every [Route] subclass — objects plus one
     * non-default sample per data class. Keep in sync with the sealed
     * hierarchy; `route_serialization_roundTrip` covers them all.
     */
    private val allRoutes: List<Route> = listOf(
        Route.ServerList,
        Route.AddServer,
        Route.Login("https://example.com"),
        Route.QuickConnect("https://example.com"),
        Route.UserSelection("server-1", "https://example.com", "Example"),
        Route.Home,
        Route.Library,
        Route.LibraryBrowse("folder-1", "Folder", collectionType = "movies"),
        Route.LibrarySection(
            title = "Latest Movies",
            parentId = "parent-1",
            collectionType = "movies",
            sortBy = "DateCreated",
            mediaTypes = listOf("Movie"),
            genre = "Action",
            tag = "4k",
        ),
        Route.Search,
        Route.LiveTv,
        Route.MediaDetail("item-1"),
        Route.MetadataEditor("item-1"),
        Route.SeerrDetail(tmdbId = 42, mediaType = "movie"),
        Route.PersonDetail("person-1"),
        Route.CastAndCrew("item-1"),
        Route.ManageSeries("series-1"),
        Route.CollectionDetail("collection-1"),
        Route.StudioDetail("studio-1", "Studio"),
        Route.MediaInfo("item-1"),
        Route.VideoPlayer(
            itemId = "video-789",
            mediaSourceId = "source-1",
            startPositionTicks = 50000,
            subtitleStreamIndex = 2,
            audioStreamIndex = 1,
        ),
        Route.AudioPlayer("audio-001"),
        Route.PlayOnCompanion,
        Route.Ambient(imageUrl = "http://test.com/img.jpg", title = "Test Song", artist = "Artist"),
        Route.LiveTvChannelPlayer(
            channelId = "channel-1",
            channelName = "Channel 1",
            subtitleStreamIndex = 1,
            audioStreamIndex = 2,
        ),
        Route.Settings,
        Route.ServerManagement(highlightSettingId = "server_management"),
        Route.UserManagement(highlightSettingId = "user_management"),
        Route.SeerrSettings(),
        Route.AppearanceSettings(),
        Route.PinnedHomeSections(),
        Route.HomeLayoutPresets(),
        Route.LibraryHomeSections(),
        Route.PlaybackSettings(),
        Route.AudioSettings(),
        Route.LanguageSettings(),
        Route.NotificationSettings(),
        Route.StorageSettings(),
        Route.SecuritySettings(),
        Route.PrivacyData(),
        Route.BackupSettings(),
        Route.ExperimentalSettings(),
        Route.FactoryReset(),
        Route.Integrations(),
        Route.ArrSettings(),
        Route.SubtitleProviderSettings(),
        Route.MusicBrowse,
        Route.Artists,
        Route.Albums,
        Route.Tracks,
        Route.Genres,
        Route.ArtistDetail("artist-1"),
        Route.AlbumDetail("album-002"),
        Route.SmartPlaylists,
        Route.SmartPlaylistDetail("sp-005"),
        Route.MoodPlaylists,
        Route.MoodPlaylistDetail("mp-006"),
        Route.Playlists,
        Route.PlaylistDetail(playlistId = "playlist-004", playlistName = "Test Playlist"),
        Route.GenreDetail("genre-1", "Action"),
        Route.ChannelDetail("channel-1", "Channel 1"),
        Route.AdminDashboard,
        Route.ScheduledTasks,
        Route.Devices,
        Route.Logs,
        Route.UserStatistics,
        Route.UserStatisticsDetail("user-1"),
        Route.Users,
        Route.UserDetail("user-1"),
        Route.StaleMedia,
        Route.WatchedMediaCleanup,
        Route.Plugins,
        Route.PluginDetail("plugin-1", "Plugin"),
        Route.PluginConfig("plugin-1", "Plugin"),
        Route.Downloads,
        Route.OfflineLibrary,
        Route.Onboarding,
        Route.SyncPlay,
        Route.Newsletter,
        Route.NewsletterSectionList("new"),
        Route.Favorites,
        Route.PhotoAlbum("parent-1", "Photos"),
        Route.PhotoViewer("item-1", "parent-1"),
        Route.About,
        Route.Licenses,
        Route.WatchProgressHeatmap,
        Route.Requests,
        Route.ArrQueue,
        Route.UpcomingCalendar,
        Route.Shortcuts,
        Route.SubtitleTester,
    )

    @Test
    fun route_serialization_roundTrip() {
        // Real round-trip through nav3's reflective NavKeySerializer — the
        // exact path rememberNavBackStack uses to save/restore back stacks on
        // Android. Each entry is persisted as {type = <binary class name>,
        // value} and restored via Class.forName, so this pins the FQCN restore
        // contract: any accidental package/nesting move fails here.
        val serializer = NavKeySerializer<Route>()

        allRoutes.forEach { route ->
            val encoded = Json.encodeToString(serializer, route)
            assertTrue(
                "type discriminator must be the binary class name for $route",
                encoded.contains("\"type\":\"${route::class.java.name}\""),
            )
            val restored = Json.decodeFromString(serializer, encoded)
            assertEquals("round-trip must restore $route", route, restored)
        }
    }

    @Test
    fun topLevelRoutes_containsExpectedRoutes() {
        val topLevel = com.raulshma.jellyplay.core.ui.navigation.TOP_LEVEL_ROUTES
        assertEquals(4, topLevel.size)
        assert(topLevel.containsKey(Route.Home))
        assert(topLevel.containsKey(Route.Library))
        assert(topLevel.containsKey(Route.Search))
        assert(topLevel.containsKey(Route.LiveTv))
        // Shortcuts moved out of the top-level tab set into the nav ⋮ overflow
        // (and an explicit TV drawer item); no longer a top-level route.
        assert(!topLevel.containsKey(Route.Shortcuts))
    }

    @Test
    fun topLevelRoutes_labelsCorrect() {
        val topLevel = com.raulshma.jellyplay.core.ui.navigation.TOP_LEVEL_ROUTES
        assertEquals("Home", topLevel[Route.Home])
        assertEquals("Library", topLevel[Route.Library])
        assertEquals("Search", topLevel[Route.Search])
        assertEquals("Live TV", topLevel[Route.LiveTv])
    }
}
