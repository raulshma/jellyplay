package com.raulshma.jellyplay.core.ui.navigation

import androidx.navigation3.runtime.serialization.NavKeySerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Route classification contract tests.
 *
 * Classification is per-route member metadata ([Route.isModal], [Route.isDetail],
 * [Route.isFullScreen], [Route.isPlayer] overridden at each declaration) — there
 * are no hand-maintained predicate lists left to pin. What still needs guarding
 * is *coverage*: every route must be deliberately classified, and the classes
 * must not overlap in ways the transition policy does not expect.
 *
 * The guard works without kotlin-reflect (the project deliberately avoids it —
 * see commit 2466b1df6): the compiler-generated sealed serializer enumerates
 * every subclass descriptor (via [sealedSubclassDescriptors]), so it cannot
 * drift from the hierarchy. [allRoutes] is a hand-listed sample set, but the
 * `every route subclass has exactly one sample instance` assertion fails the
 * moment a new route lacks a sample — and the residual "plain" assertion below
 * then forces an explicit classification decision for it.
 *
 * This replaces the pre-refactor membership lists, which had already drifted
 * (the old test pinned 18 detail routes while the predicate listed 20).
 */
class RoutePredicatesTest {

    /**
     * A representative instance of every [Route] subclass — objects plus one
     * cheap sample per data class. Sync any additions with
     * `every route subclass has exactly one sample instance`.
     */
    private val allRoutes: List<Route> = listOf(
        // auth
        Route.ServerList,
        Route.AddServer,
        Route.Login("https://example.com"),
        Route.QuickConnect("https://example.com"),
        Route.UserSelection("server-1", "https://example.com", "Example"),
        // library & tabs
        Route.Home,
        Route.Library,
        Route.LibraryBrowse("folder-1", "Folder"),
        Route.LibrarySection(title = "Latest Movies"),
        Route.Search,
        // details
        Route.MediaDetail("item-1"),
        Route.MetadataEditor("item-1"),
        Route.SeerrDetail(tmdbId = 42, mediaType = "movie"),
        Route.PersonDetail("person-1"),
        Route.CastAndCrew("item-1"),
        Route.ManageSeries("series-1"),
        Route.CollectionDetail("collection-1"),
        Route.StudioDetail("studio-1"),
        Route.MediaInfo("item-1"),
        // players & playback
        Route.VideoPlayer("item-1"),
        Route.AudioPlayer("item-1"),
        Route.PlayOnCompanion,
        Route.Ambient(),
        Route.LiveTvChannelPlayer(channelId = "channel-1", channelName = "Channel 1"),
        // settings (+ every highlight-carrying settings route)
        Route.Settings,
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
        Route.ImportPreview("content://test"),
        Route.ExperimentalSettings(),
        Route.FactoryReset(),
        Route.Integrations(),
        Route.ArrSettings(),
        Route.SubtitleProviderSettings(),
        // music
        Route.MusicBrowse,
        Route.Artists,
        Route.Albums,
        Route.Tracks,
        Route.Genres,
        Route.ArtistDetail("artist-1"),
        Route.AlbumDetail("album-1"),
        Route.SmartPlaylists,
        Route.SmartPlaylistDetail("playlist-1"),
        Route.MoodPlaylists,
        Route.MoodPlaylistDetail("playlist-1"),
        Route.Playlists,
        Route.PlaylistDetail("playlist-1"),
        Route.GenreDetail("genre-1"),
        // live tv
        Route.LiveTv,
        Route.ChannelDetail("channel-1"),
        // admin
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
        // misc
        Route.Downloads,
        Route.OfflineLibrary,
        Route.Onboarding,
        Route.SyncPlay,
        Route.Newsletter,
        Route.NewsletterSectionList("new"),
        Route.Favorites,
        Route.PhotoAlbum("parent-1"),
        Route.PhotoViewer("item-1"),
        Route.About,
        Route.Licenses,
        Route.WatchProgressHeatmap,
        Route.Requests,
        Route.ArrQueue,
        Route.UpcomingCalendar,
        Route.Shortcuts,
        Route.SubtitleTester,
    )

    /**
     * The union descriptor listing every [Route] subclass the compiler
     * generated into the sealed serializer — cannot drift from the hierarchy.
     *
     * kotlinx.serialization (1.11) shapes a sealed descriptor as
     * `{type: String, value: Sealed<Route>}` where the second element is a
     * CONTEXTUAL-kind union with one element per subclass — so the subclass
     * descriptors are one hop below the sealed descriptor itself.
     */
    private fun sealedSubclassDescriptors(): List<SerialDescriptor> =
        Route.serializer().descriptor.elementDescriptors
            .single { it.kind == SerialKind.CONTEXTUAL }
            .elementDescriptors
            .toList()

    /** Every subclass the compiler generated into the sealed serializer. */
    private fun descriptorNames(): Set<String> =
        sealedSubclassDescriptors()
            .map { it.serialName.substringAfterLast('.') }
            .toSet()

    /** Subclass descriptors that declare a `highlightSettingId` field. */
    private fun highlightCarrierNames(): Set<String> =
        sealedSubclassDescriptors()
            .filter { descriptor ->
                (0 until descriptor.elementsCount).any {
                    descriptor.getElementName(it) == "highlightSettingId"
                }
            }
            .map { it.serialName.substringAfterLast('.') }
            .toSet()

    @Test
    fun `every route subclass has exactly one sample instance`() {
        val sampledNames = allRoutes.map { it::class.simpleName!! }
        // Set equality pins no missing route; size equality pins no duplicates.
        assertEquals(descriptorNames(), sampledNames.toSet())
        assertEquals(sampledNames.size, sampledNames.distinct().size)
    }

    @Test
    fun `every route is deliberately classified`() {
        val classified = allRoutes
            .filter {
                it.isPlayer || it.isFullScreen || it.isModal || it.isDetail ||
                    it in ALL_TOP_LEVEL_ROUTE_KEYS
            }
            .map { it::class.simpleName!! }
            .toSet()

        // Whatever is left must be exactly the known "plain" screens — a new
        // unclassified route fails here instead of silently animating wrong.
        // (Membership in this set implies none of the flags are set; it also
        // keeps the old pins — e.g. SubtitleTester and the tab roots are not
        // fullscreen, which the MainNavDisplay padding decorator relies on.)
        assertEquals(
            setOf(
                // auth
                "ServerList", "AddServer", "Login", "QuickConnect", "UserSelection",
                // library
                "LibraryBrowse",
                // downloads
                "OfflineLibrary",
                // settings sub-screens without modal classification
                "ServerManagement", "UserManagement", "AppearanceSettings",
                "PinnedHomeSections", "HomeLayoutPresets", "LibraryHomeSections",
                "PlaybackSettings", "AudioSettings", "LanguageSettings",
                "NotificationSettings", "StorageSettings", "SecuritySettings",
                "PrivacyData", "BackupSettings", "ImportPreview",
                "ExperimentalSettings", "FactoryReset",
                // music
                "Artists", "Albums", "Tracks", "Genres", "SmartPlaylists",
                "MoodPlaylists", "Playlists",
                // admin
                "UserStatistics", "StaleMedia", "WatchedMediaCleanup",
                "Plugins", "PluginDetail", "PluginConfig",
                // misc
                "Newsletter", "Favorites", "PhotoAlbum", "About", "Licenses",
                "WatchProgressHeatmap", "SubtitleTester",
            ),
            descriptorNames() - classified,
        )
    }

    // ---- Cross-class invariants (fed by the descriptor enumeration) ----

    @Test
    fun `player routes are a subset of fullscreen routes`() {
        allRoutes.filter { it.isPlayer }.forEach { route ->
            assertTrue(
                "${route::class.simpleName} is a player but not fullscreen",
                route.isFullScreen,
            )
        }
    }

    @Test
    fun `modal routes are not top-level tab roots`() {
        val topLevel = ALL_TOP_LEVEL_ROUTE_KEYS
        allRoutes.filter { it.isModal }.forEach { route ->
            assertFalse(
                "${route::class.simpleName} is both modal and a top-level tab root",
                route in topLevel,
            )
        }
    }

    @Test
    fun `detail routes are not fullscreen`() {
        allRoutes.filter { it.isDetail }.forEach { route ->
            assertFalse(
                "${route::class.simpleName} is both detail and fullscreen",
                route.isFullScreen,
            )
        }
    }

    // ---- Settings-highlight seam ----

    @Test
    fun `every highlight-carrying route participates in the highlight seam`() {
        // Every subclass that declares a highlightSettingId field must be
        // sampled here AND implement HighlightableRoute — a settings route
        // added with the field but without the interface fails loudly.
        val implementors = allRoutes.filterIsInstance<HighlightableRoute>()
        assertEquals(
            highlightCarrierNames(),
            implementors.map { (it as Route)::class.simpleName!! }.toSet(),
        )

        implementors.forEach { implementor ->
            val route = implementor as Route
            // The helper must return a *copy* carrying the id (never the same
            // instance, never a different route type):
            val out = route.withHighlightSettingId("highlight-x")
            assertEquals("class must be preserved for $route", route::class, out::class)
            assertNotSame("a copy must be returned for $route", route, out)
            // ...and the id is actually carried (polymorphic encode exposes it):
            assertTrue(
                "encoded copy of $route must carry the injected id",
                Json.encodeToString(Route.serializer(), out)
                    .contains("\"highlightSettingId\":\"highlight-x\""),
            )
        }
    }

    @Test
    fun `withHighlightSettingId leaves non-highlight routes untouched`() {
        listOf<Route>(Route.Home, Route.Settings, Route.MediaDetail("x"), Route.Shortcuts)
            .forEach { route ->
                assertSame(
                    "non-highlightable route must be returned as-is",
                    route,
                    route.withHighlightSettingId("x"),
                )
            }
    }

    // ---- Restore contract (JVM mirror of NavigationRouteTest's androidTest) ----

    @Test
    fun `every route round-trips through nav3 NavKeySerializer`() {
        // Pins the FQCN restore contract the route-ownership design depends on:
        // nav3 persists each entry as {type = <binary class name>, value} and
        // restores it via Class.forName. Any accidental package/nesting move
        // breaks here (and in the on-device NavigationRouteTest).
        val serializer = NavKeySerializer<Route>()
        allRoutes.forEach { route ->
            val encoded = Json.encodeToString(serializer, route)
            assertTrue(
                "type discriminator must be the binary class name",
                encoded.contains("\"type\":\"${route::class.java.name}\""),
            )
            val restored = Json.decodeFromString(serializer, encoded)
            assertEquals("round-trip must restore $route", route, restored)
        }
    }
}
