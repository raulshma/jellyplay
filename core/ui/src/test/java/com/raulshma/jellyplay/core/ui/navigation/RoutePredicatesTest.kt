package com.raulshma.jellyplay.core.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the membership of the categorical Route predicates ([Route.isDetail],
 * [Route.isFullScreen]).
 *
 * These predicates drive transition selection in `MainContent` /
 * `NavTransitionPolicy`. They were previously mirrored by a hand-synced
 * `DETAIL_ROUTE_CLASS_NAMES` string set (now deleted — it had zero callers and
 * was the worst drift hazard: two lists of the same 20 names kept in sync by
 * hand). This test guards the remaining typed lists against accidental
 * shrinkage: dropping a route silently flips its transition / layout, with only
 * sampled coverage in NavTransitionPolicyTest to catch it.
 *
 * Written without kotlin-reflect (the project deliberately avoids it — see
 * commit 2466b1df6), so routes are instantiated concretely.
 */
class RoutePredicatesTest {

    // A representative instance of every route currently in the isDetail list.
    private val detailRoutes: List<Route> = listOf(
        Route.MediaDetail("x"),
        Route.MetadataEditor("x"),
        Route.SeerrDetail(1, "movie"),
        Route.PersonDetail("x"),
        Route.ManageSeries("x"),
        Route.MediaInfo("x"),
        Route.CollectionDetail("x"),
        Route.ArtistDetail("x"),
        Route.AlbumDetail("x"),
        Route.SmartPlaylistDetail("x"),
        Route.MoodPlaylistDetail("x"),
        Route.PlaylistDetail("x"),
        Route.GenreDetail("x"),
        Route.StudioDetail("x"),
        Route.NewsletterSectionList("x"),
        Route.UserStatisticsDetail("x"),
        Route.ChannelDetail("x"),
        Route.UserDetail("x"),
    )

    private val fullScreenRoutes: List<Route> = listOf(
        Route.VideoPlayer("x"),
        Route.LiveTvChannelPlayer(channelId = "x", channelName = "x"),
        Route.AudioPlayer("x"),
        Route.Ambient(),
        Route.Onboarding,
        Route.PhotoViewer("x"),
    )

    @Test
    fun `every route in the detail set reports isDetail`() {
        detailRoutes.forEach { route ->
            assertTrue("${route::class.java.simpleName} should be detail", route.isDetail)
        }
    }

    @Test
    fun `detail routes are not fullscreen`() {
        detailRoutes.forEach { route ->
            assertFalse(
                "${route::class.java.simpleName} is both detail and fullscreen",
                route.isFullScreen,
            )
        }
    }

    @Test
    fun `every route in the fullscreen set reports isFullScreen`() {
        fullScreenRoutes.forEach { route ->
            assertTrue("${route::class.java.simpleName} should be fullscreen", route.isFullScreen)
        }
    }

    @Test
    fun `count of detail routes matches the documented set`() {
        // Guards against the list above losing an entry without the expected
        // set being updated. If a detail route is added, increment this number
        // and add it to [detailRoutes].
        assertEquals(18, detailRoutes.size)
    }

    @Test
    fun `count of fullscreen routes matches the documented set`() {
        assertEquals(6, fullScreenRoutes.size)
    }

    @Test
    fun `subtitle tester and top-level routes are not fullscreen`() {
        // Pins the exclusion side of the membership. SubtitleTester
        // intentionally overlays a fullscreen host (the player) instead of
        // being one, and the MainNavDisplay padding decorator relies on
        // isFullScreen to decide which hosts skip the mini-player bottom
        // padding — SubtitleTester must keep that padding.
        assertFalse(Route.SubtitleTester.isFullScreen)
        assertFalse(Route.Home.isFullScreen)
        assertFalse(Route.Settings.isFullScreen)
    }
}
