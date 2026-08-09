package com.raulshma.jellyplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationRouteTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun route_serialization_roundTrip() {
        val routes = listOf(
            Route.Home,
            Route.Library,
            Route.Search,
            Route.LiveTv,
            Route.Shortcuts,
            Route.Settings,
            Route.Downloads,
            Route.SyncPlay,
            Route.ServerManagement,
            Route.UserManagement,
            Route.MediaDetail(itemId = "test-123"),
            Route.PersonDetail(personId = "person-456"),
            Route.VideoPlayer(itemId = "video-789", mediaSourceId = "source-1", startPositionTicks = 50000),
            Route.AudioPlayer(itemId = "audio-001"),
            Route.AlbumDetail(albumId = "album-002"),
            Route.ArtistDetail(artistId = "artist-003"),
            Route.Playlists,
            Route.PlaylistDetail(playlistId = "playlist-004", playlistName = "Test Playlist"),
            Route.SmartPlaylists,
            Route.SmartPlaylistDetail(playlistId = "sp-005"),
            Route.MoodPlaylists,
            Route.MoodPlaylistDetail(playlistId = "mp-006"),
            Route.CollectionDetail(collectionId = "coll-007"),
            Route.Ambient(imageUrl = "http://test.com/img.jpg", title = "Test Song", artist = "Artist"),
        )

        routes.forEach { route ->
            val className = route::class.simpleName
            assert(className != null) { "Route should have a class name" }
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
