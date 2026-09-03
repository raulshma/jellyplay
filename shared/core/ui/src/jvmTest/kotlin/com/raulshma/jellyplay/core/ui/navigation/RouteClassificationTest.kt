package com.raulshma.jellyplay.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.animation.NavRouteClass
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins the [Route] classification contract that the app shell dispatches on:
 *
 *  - the "plain screen" defaults (`isModal` / `isDetail` / `isFullScreen` /
 *    `isPlayer` all false) hold for unclassified routes;
 *  - every route that declares an override carries exactly the flags its
 *    section documents (player routes are also full-screen; ambient/onboarding/
 *    photo viewer are full-screen but NOT player routes);
 *  - [Route.toNavRouteClass] priority is Ambient > FullScreen > Modal >
 *    Detail > top-level tab > Default, with null -> DEFAULT;
 *  - [withHighlightSettingId] rewrites only [HighlightableRoute]s and returns
 *    the SAME instance for everything else;
 *  - the top-level tab maps and [SHORTCUTS_NAV_KEY] stay consistent with the
 *    route hierarchy; and
 *  - a representative route survives a kotlinx-serialization round-trip (the
 *    back-stack restore contract).
 */
class RouteClassificationTest {

    // ── Defaults ─────────────────────────────────────────────────────────

    @Test
    fun plainRoutes_carryNoClassificationFlags() {
        val plain = listOf<Route>(
            Route.Home,
            Route.Library,
            Route.Search,
            Route.ServerList,
            Route.Login("http://s"),
            Route.About,
            Route.PluginDetail("p", "Plugin"),
            Route.LibraryBrowse("f", "Folder"),
        )
        plain.forEach { route ->
            assertFalse(route.isModal, "$route must not be modal")
            assertFalse(route.isDetail, "$route must not be a detail route")
            assertFalse(route.isFullScreen, "$route must not be full-screen")
            assertFalse(route.isPlayer, "$route must not be a player route")
        }
    }

    @Test
    fun modalRoutes_setOnlyIsModal() {
        val modal = listOf<Route>(
            Route.Settings,
            Route.PlayOnCompanion,
            Route.Downloads,
            Route.AdminDashboard,
            Route.Users,
            Route.SyncPlay,
            Route.Requests,
            Route.ArrQueue,
            Route.UpcomingCalendar,
            Route.Shortcuts,
        )
        modal.forEach { route ->
            assertTrue(route.isModal, "$route must be modal")
            assertFalse(route.isDetail, "$route must not be a detail route")
            assertFalse(route.isFullScreen, "$route must not be full-screen")
            assertFalse(route.isPlayer, "$route must not be a player route")
        }
    }

    @Test
    fun detailRoutes_setOnlyIsDetail() {
        val details = listOf<Route>(
            Route.MediaDetail("m1"),
            Route.LibrarySection("Title"),
            Route.PersonDetail("p"),
            Route.ArtistDetail("a"),
            Route.ChannelDetail("c"),
            Route.UserStatisticsDetail("u"),
            Route.StudioDetail("s"),
        )
        details.forEach { route ->
            assertTrue(route.isDetail, "$route must be a detail route")
            assertFalse(route.isModal, "$route must not be modal")
            assertFalse(route.isFullScreen, "$route must not be full-screen")
            assertFalse(route.isPlayer, "$route must not be a player route")
        }
    }

    @Test
    fun playerRoutes_areBothFullScreenAndPlayer() {
        val players = listOf<Route>(
            Route.VideoPlayer("v"),
            Route.AudioPlayer("a"),
            Route.LiveTvChannelPlayer("c", "Channel"),
        )
        players.forEach { route ->
            assertTrue(route.isFullScreen, "$route must be full-screen")
            assertTrue(route.isPlayer, "$route must be a player route")
            assertFalse(route.isModal, "$route must not be modal")
        }
    }

    @Test
    fun transientFullScreenRoutes_areNotPlayerRoutes() {
        // Ambient/onboarding/photo viewer restore harmlessly after process
        // death — the player-strip logic in NavigationState must NOT touch them.
        val transient = listOf<Route>(
            Route.Ambient(),
            Route.Onboarding,
            Route.PhotoViewer("ph"),
        )
        transient.forEach { route ->
            assertTrue(route.isFullScreen, "$route must be full-screen")
            assertFalse(route.isPlayer, "$route must NOT be a player route")
        }
    }

    // ── toNavRouteClass priority ─────────────────────────────────────────

    @Test
    fun toNavRouteClass_nullIsDefault() {
        val nullRoute: Route? = null
        assertEquals(NavRouteClass.DEFAULT, nullRoute.toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_ambientBeatsFullScreen() {
        // Ambient is also isFullScreen; it must always cross-fade (AMBIENT).
        assertEquals(NavRouteClass.AMBIENT, (Route.Ambient() as Route?).toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_fullScreenOverModalAndDetail() {
        assertEquals(NavRouteClass.FULLSCREEN, (Route.VideoPlayer("v") as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.FULLSCREEN, (Route.Onboarding as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.FULLSCREEN, (Route.PhotoViewer("p") as Route?).toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_modalOverDetailAndTopLevel() {
        assertEquals(NavRouteClass.MODAL, (Route.Settings as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.MODAL, (Route.Downloads as Route?).toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_detailOverTopLevelTab() {
        assertEquals(NavRouteClass.DETAIL, (Route.MediaDetail("m") as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.DETAIL, (Route.AlbumDetail("a") as Route?).toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_topLevelTabsFromBothMaps() {
        assertEquals(NavRouteClass.TOP_LEVEL_TAB, (Route.Home as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.TOP_LEVEL_TAB, (Route.Library as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.TOP_LEVEL_TAB, (Route.Search as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.TOP_LEVEL_TAB, (Route.LiveTv as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.TOP_LEVEL_TAB, (Route.MusicBrowse as Route?).toNavRouteClass)
    }

    @Test
    fun toNavRouteClass_unclassifiedRoutesAreDefault() {
        // A plain screen that is neither modal/detail/full-screen nor a
        // registered tab falls through to the default transition bucket.
        assertEquals(NavRouteClass.DEFAULT, (Route.About as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.DEFAULT, (Route.Favorites as Route?).toNavRouteClass)
        assertEquals(NavRouteClass.DEFAULT, (Route.SubtitleTester as Route?).toNavRouteClass)
    }

    // ── HighlightableRoute / withHighlightSettingId ──────────────────────

    @Test
    fun highlightableRoute_withHighlightSettingId_returnsCopyWithId() {
        val route = Route.ServerManagement()
        assertNull(route.highlightSettingId)

        val updated = route.withHighlightSettingId("setting-1")
        assertEquals("setting-1", (updated as Route.ServerManagement).highlightSettingId)
        assertNotSame(route, updated)
    }

    @Test
    fun globalHelper_overwritesExistingHighlightId() {
        val route = Route.AppearanceSettings("old")
        val updated = route.withHighlightSettingId("new")
        assertEquals("new", (updated as Route.AppearanceSettings).highlightSettingId)
    }

    @Test
    fun globalHelper_leavesNonHighlightRoutesUntouched() {
        val detail = Route.MediaDetail("m")
        assertSame(detail, detail.withHighlightSettingId("ignored"))

        val tab = Route.Home
        assertSame(tab, tab.withHighlightSettingId("ignored"))
    }

    @Test
    fun everySettingsRouteImplementsHighlightableRoute() {
        // One representative per settings destination family — the dispatch in
        // the search flow relies on the interface being present at the route.
        val highlightables = listOf<Route>(
            Route.ServerManagement(),
            Route.UserManagement(),
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
        )
        highlightables.forEach { route ->
            assertTrue(route is HighlightableRoute, "$route must implement HighlightableRoute")
            val copied = (route as HighlightableRoute).withHighlightSettingId("x")
            assertEquals(
                route::class,
                copied::class,
                "withHighlightSettingId must keep the concrete route type",
            )
        }
    }

    @Test
    fun modalSettingsRoutes_stayModalAfterHighlightCopy() {
        val copied = Route.SeerrSettings().withHighlightSettingId("x")
        assertTrue(copied.isModal, "isModal is a member override — copy() must preserve it")
    }

    // ── Route tables & constants ─────────────────────────────────────────

    @Test
    fun topLevelRouteMaps_coverTheExpectedTabs() {
        assertEquals(
            linkedMapOf(
                Route.Home to "Home",
                Route.Library to "Library",
                Route.Search to "Search",
                Route.LiveTv to "Live TV",
            ),
            VIDEO_TOP_LEVEL_ROUTES,
        )
        assertEquals(
            linkedMapOf(
                Route.Home to "Home",
                Route.MusicBrowse to "Browse",
                Route.Search to "Search",
            ),
            MUSIC_TOP_LEVEL_ROUTES,
        )
        assertSame(VIDEO_TOP_LEVEL_ROUTES, TOP_LEVEL_ROUTES)
    }

    @Test
    fun allTopLevelRouteKeys_isUnionOfBothMaps() {
        assertEquals(
            setOf(Route.Home, Route.Library, Route.Search, Route.LiveTv, Route.MusicBrowse),
            ALL_TOP_LEVEL_ROUTE_KEYS,
        )
    }

    @Test
    fun shortcutsNavKey_matchesRouteSimpleName() {
        assertEquals("Shortcuts", SHORTCUTS_NAV_KEY)
        assertEquals(Route.Shortcuts::class.simpleName, SHORTCUTS_NAV_KEY)
    }

    // ── Serialization restore contract ───────────────────────────────────

    @Test
    fun route_survivesJsonRoundTrip() {
        val json = Json
        val original: Route = Route.MediaDetail(itemId = "item-42", openDownloadSheet = true)
        val encoded = json.encodeToString(Route.serializer(), original)
        val decoded = json.decodeFromString(Route.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.isDetail)
    }

    @Test
    fun route_defaultsSurviveJsonRoundTrip() {
        val json = Json
        val original: Route = Route.VideoPlayer("v")
        val decoded = json.decodeFromString(
            Route.serializer(),
            json.encodeToString(Route.serializer(), original),
        )
        assertEquals(original, decoded)
        assertEquals(0L, (decoded as Route.VideoPlayer).startPositionTicks)
        assertNull(decoded.subtitleStreamIndex)
    }
}
