package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.animation.NavRouteClass
import kotlinx.serialization.Serializable

/**
 * The app's navigation vocabulary — one sealed hierarchy naming every
 * destination of every feature module.
 *
 * ## Route ownership & classification
 *
 * Each route owns its own classification as member overrides
 * (`override val isModal = true`) declared on the route itself, so a route and
 * its flags are always reviewed as one hunk and "added the route, forgot the
 * list" cannot happen — there is no hand-maintained classification list
 * anywhere. The [isModal] / [isDetail] / [isFullScreen] / [isPlayer] members
 * below are the "plain screen" defaults every unclassified route inherits.
 *
 * Settings routes that can scroll to / focus a specific setting additionally
 * implement [HighlightableRoute] where they are declared, which is what lets
 * the global [withHighlightSettingId] helper stay a single-branch dispatch.
 *
 * The declarations are organized into per-feature-group sections (auth,
 * library, details, players, settings, music, live tv, admin, misc) so one
 * feature's routes land as one contiguous hunk.
 *
 * ## Restore contract
 *
 * Saved back stacks persist each entry as `{type = <binary class name>,
 * value = ...}` via nav3's reflective `NavKeySerializer` (`Class.forName`), and
 * the R8 keep rule `-keep class com.raulshma.jellyplay.core.ui.navigation.Route**`
 * in `app/proguard-rules.pro` pins those same names. The concrete binary class
 * name (`...Route$X` for these nested subclasses) — *not* the sealed-ness, and
 * *not* any `@SerialName` — is the entire persistence contract. Do not move a
 * route class out of this hierarchy/nesting or into another package without
 * (a) keeping the binary name stable, (b) updating the keep rule in the same
 * commit, and (c) accepting a one-time back-stack drop or swapping in an
 * explicit polymorphic `SerializersModule` with a type-alias compat map. (This
 * is also why the subclasses stay nested here rather than in per-feature
 * files: Kotlin cannot declare members of [Route] outside its body, and
 * un-nesting them would change every binary name.)
 */
@Immutable
@Serializable
sealed class Route : NavKey {

    /**
     * Routes that present as a modal/bottom-sheet-style overlay rather than a
     * push-on-the-stack navigation. Consumed by the shell's transition
     * `transitionSpec` / `popTransitionSpec` (via [toNavRouteClass]); declare
     * `override val isModal = true` on the route itself.
     */
    open val isModal: Boolean get() = false

    /**
     * Detail routes (MediaDetail, PersonDetail, AlbumDetail, etc.) — "drill
     * into" navigations rather than modal or top-level tab switches.
     */
    open val isDetail: Boolean get() = false

    /**
     * Routes that render in the bare full-screen layout (player / onboarding /
     * ambient / photo viewer). Used by the layout-branch picker in
     * `MainContent`, which matches by membership and scans a whole back stack
     * for *any* full-screen route (not just the top entry) — see `MainContent`
     * for why that matters for the player↔subtitle-tester round trip.
     */
    open val isFullScreen: Boolean get() = false

    /**
     * Routes that mount a *player* — i.e. hold live playback state (ExoPlayer /
     * equalizer / live stream) that does **not** round-trip across state loss.
     * Unlike [isFullScreen], this excludes transient full-screen overlays
     * (onboarding / ambient / photo) whose restore is harmless. Used by
     * [rememberNavigationState] to strip only the routes that would auto-play a
     * stale item/position after the OS reclaimed their in-memory state.
     */
    open val isPlayer: Boolean get() = false

    // ─────────────────────────── Auth ───────────────────────────

    @Serializable data object ServerList : Route()
    @Serializable data object AddServer : Route()
    @Serializable data class Login(val serverAddress: String) : Route()
    @Serializable data class QuickConnect(val serverAddress: String) : Route()
    @Serializable data class UserSelection(
        val serverId: String,
        val serverAddress: String,
        val serverName: String,
    ) : Route()

    // ─────────────────────── Library & tabs ───────────────────────

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
        // Optional pre-applied genre/tag filter (by name) for deep-links from a
        // detail-screen chip. The library query layer filters genres + tags by
        // name (see LibraryApiClientImpl.getItems), so a single name is enough.
        val genre: String? = null,
        val tag: String? = null,
    ) : Route() {
        override val isDetail = true
    }

    @Serializable data object Search : Route()

    // ───────────────────────── Details ─────────────────────────

    @Serializable data class MediaDetail(val itemId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class MetadataEditor(val itemId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class SeerrDetail(val tmdbId: Int, val mediaType: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class PersonDetail(val personId: String) : Route() {
        override val isDetail = true
    }

    /** Full cast & crew screen reached from "See all" on a detail screen's cast row. */
    @Serializable data class CastAndCrew(val itemId: String) : Route() {
        override val isDetail = true
    }

    /** Sonarr-style series management screen (collapsible seasons, per-episode actions). */
    @Serializable data class ManageSeries(val seriesId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class CollectionDetail(val collectionId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class StudioDetail(val studioId: String, val studioName: String = "") : Route() {
        override val isDetail = true
    }

    @Serializable data class MediaInfo(val itemId: String) : Route() {
        override val isDetail = true
    }

    // ─────────────────────── Players & playback ───────────────────────

    @Serializable data class VideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0,
        val subtitleStreamIndex: Int? = null,
        val audioStreamIndex: Int? = null,
    ) : Route() {
        override val isFullScreen = true
        override val isPlayer = true
    }

    @Serializable data class AudioPlayer(val itemId: String) : Route() {
        override val isFullScreen = true
        override val isPlayer = true
    }

    /**
     * Full-screen remote-control overlay for an active "Play On" (Jellyfin
     * remote session) cast. Reached by tapping the persistent [PlayOnMiniBar].
     * Modal — transient control surface, not a drill-in detail destination.
     */
    @Serializable data object PlayOnCompanion : Route() {
        override val isModal = true
    }

    /**
     * Immersive ambient-mode overlay (muted backdrop art + track metadata
     * while music plays). Classified full-screen; [toNavRouteClass] maps it to
     * [NavRouteClass.AMBIENT] ahead of the fullscreen bucket so it always
     * cross-fades.
     */
    @Serializable data class Ambient(
        val imageUrl: String? = null,
        val title: String = "",
        val artist: String = "",
    ) : Route() {
        override val isFullScreen = true
    }

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
    ) : Route() {
        override val isFullScreen = true
        override val isPlayer = true
    }

    // ─────────────────────── Settings ───────────────────────

    @Serializable data object Settings : Route() {
        override val isModal = true
    }

    @Serializable data class ServerManagement(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class UserManagement(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class SeerrSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override val isModal = true
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class AppearanceSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class PinnedHomeSections(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class HomeLayoutPresets(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class LibraryHomeSections(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class PlaybackSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class AudioSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class LanguageSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class NotificationSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class StorageSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class SecuritySettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Privacy & Data hub — consolidates scattered destructive data actions
     * (clear cache, clear image cache, clear search history, factory reset,
     * sign out) into a single discoverable screen. Re-exposes the same
     * actions the dedicated screens already perform; it does not move or
     * delete them.
     */
    @Serializable data class PrivacyData(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class BackupSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    @Serializable data class ExperimentalSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Factory-reset review screen. Lists every preference category with its
     * current-vs-factory values and exposes Reset-All + per-category Reset
     * actions. Reached from [BackupSettings].
     */
    @Serializable data class FactoryReset(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Integrations hub — top-level list of every third-party service JellyPlay
     * talks to (Seerr, Radarr/Sonarr). Each entry drills into its own screen
     * ([SeerrSettings] / [ArrSettings]).
     */
    @Serializable data class Integrations(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override val isModal = true
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Direct Radarr/Sonarr integration settings — manual server override +
     * Seerr auto-discovery toggle. Gated by
     * [com.raulshma.jellyplay.core.model.ExperimentalFeature.DIRECT_ARR_INTEGRATION].
     */
    @Serializable data class ArrSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override val isModal = true
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Direct subtitle-provider integration settings — API keys + enable toggles
     * for Wyzie Subs and OpenSubtitles, used by the in-player + edit-metadata
     * subtitle search. Mirrors [ArrSettings] / [SeerrSettings].
     */
    @Serializable data class SubtitleProviderSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override val isModal = true
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    // ───────────────────────── Music ─────────────────────────

    @Serializable data object MusicBrowse : Route()

    @Serializable data object Artists : Route()
    @Serializable data object Albums : Route()
    @Serializable data object Tracks : Route()
    @Serializable data object Genres : Route()

    @Serializable data class ArtistDetail(val artistId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data class AlbumDetail(val albumId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data object SmartPlaylists : Route()

    @Serializable data class SmartPlaylistDetail(val playlistId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data object MoodPlaylists : Route()

    @Serializable data class MoodPlaylistDetail(val playlistId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data object Playlists : Route()

    @Serializable data class PlaylistDetail(val playlistId: String, val playlistName: String = "") : Route() {
        override val isDetail = true
    }

    @Serializable data class GenreDetail(val genreId: String, val genreName: String = "") : Route() {
        override val isDetail = true
    }

    // ───────────────────────── Live TV ─────────────────────────

    @Serializable data object LiveTv : Route()

    /**
     * Drill-in detail screen for a Live TV channel, reached from the home /
     * search / related-items item-routing surfaces. Shows a today program
     * timeline with a live progress bar and a current-program backdrop, and
     * tunes the live channel via [LiveTvChannelPlayer] on play.
     */
    @Serializable data class ChannelDetail(
        val channelId: String,
        val channelName: String = "",
    ) : Route() {
        override val isDetail = true
    }

    // ───────────────────────── Admin ─────────────────────────

    @Serializable data object AdminDashboard : Route() {
        override val isModal = true
    }

    @Serializable data object ScheduledTasks : Route() {
        override val isModal = true
    }

    @Serializable data object Devices : Route() {
        override val isModal = true
    }

    @Serializable data object Logs : Route() {
        override val isModal = true
    }

    @Serializable data object UserStatistics : Route()

    @Serializable data class UserStatisticsDetail(val userId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data object Users : Route() {
        override val isModal = true
    }

    @Serializable data class UserDetail(val userId: String) : Route() {
        override val isDetail = true
    }

    @Serializable data object StaleMedia : Route()
    @Serializable data object WatchedMediaCleanup : Route()

    @Serializable data object Plugins : Route()
    @Serializable data class PluginDetail(val pluginId: String, val pluginName: String) : Route()
    @Serializable data class PluginConfig(val pluginId: String, val pluginName: String) : Route()

    // ───────────────────────── Misc ─────────────────────────

    @Serializable data object Downloads : Route() {
        override val isModal = true
    }

    @Serializable data object OfflineLibrary : Route()

    @Serializable data object Onboarding : Route() {
        override val isFullScreen = true
    }

    @Serializable data object SyncPlay : Route() {
        override val isModal = true
    }

    @Serializable data object Newsletter : Route()

    @Serializable data class NewsletterSectionList(
        val sectionType: String,
    ) : Route() {
        override val isDetail = true
    }

    @Serializable data object Favorites : Route()

    @Serializable data class PhotoAlbum(val parentId: String, val folderName: String = "") : Route()
    @Serializable data class PhotoViewer(val itemId: String, val parentId: String? = null) : Route() {
        override val isFullScreen = true
    }

    @Serializable data object About : Route()

    @Serializable data object Licenses : Route()

    @Serializable data object WatchProgressHeatmap : Route()

    @Serializable data object Requests : Route() {
        override val isModal = true
    }

    @Serializable data object ArrQueue : Route() {
        override val isModal = true
    }

    /**
     * Combined Sonarr + Radarr upcoming-releases calendar. Modal entry gated
     * by [com.raulshma.jellyplay.core.model.ExperimentalFeature.DIRECT_ARR_INTEGRATION],
     * mirroring [ArrQueue] / [Requests].
     */
    @Serializable data object UpcomingCalendar : Route() {
        override val isModal = true
    }

    @Serializable data object Shortcuts : Route() {
        override val isModal = true
    }

    @Serializable data object SubtitleTester : Route()
}

/**
 * Implemented by settings-destination routes that carry a `highlightSettingId`
 * (the settings-search item id the screen can scroll to / focus). Declaring the
 * interface *next to the route* is what makes the highlight dispatch
 * drift-proof: the global [withHighlightSettingId] helper has one branch, not
 * one per route.
 */
interface HighlightableRoute {
    /** Returns a copy of this route with its `highlightSettingId` set to [id]. */
    fun withHighlightSettingId(id: String): Route
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

val VIDEO_TOP_LEVEL_ROUTES: Map<Route, String> = linkedMapOf(
    Route.Home to "Home",
    Route.Library to "Library",
    Route.Search to "Search",
    Route.LiveTv to "Live TV",
)

val MUSIC_TOP_LEVEL_ROUTES: Map<Route, String> = linkedMapOf(
    Route.Home to "Home",
    Route.MusicBrowse to "Browse",
    Route.Search to "Search",
)

val TOP_LEVEL_ROUTES = VIDEO_TOP_LEVEL_ROUTES

// The two maps above carry explicit Map<Route, String> annotations — without
// them the wasmJs frontend unifies the vararg Pair keys up to `out Any` (a
// first attempt at an explicit union type argument failed differently) and
// every downstream Set<Route> use breaks. JVM/android inference is
// unaffected. Spike w-10C class E.
val ALL_TOP_LEVEL_ROUTE_KEYS: Set<Route> =
    VIDEO_TOP_LEVEL_ROUTES.keys.union(MUSIC_TOP_LEVEL_ROUTES.keys)

/**
 * Maps a [Route] to a coarse [NavRouteClass] for transition selection — the
 * shell's adapter from per-route classification metadata to the transition
 * policy's input. A pure projection over the member flags, so no route list
 * lives here. Ambient is checked first (before fullscreen) because
 * [Route.Ambient] is a distinct immersive overlay that should always
 * cross-fade.
 */
val Route?.toNavRouteClass: NavRouteClass
    get() = when {
        this == null -> NavRouteClass.DEFAULT
        this is Route.Ambient -> NavRouteClass.AMBIENT
        isFullScreen -> NavRouteClass.FULLSCREEN
        isModal -> NavRouteClass.MODAL
        isDetail -> NavRouteClass.DETAIL
        ALL_TOP_LEVEL_ROUTE_KEYS.contains(this) -> NavRouteClass.TOP_LEVEL_TAB
        else -> NavRouteClass.DEFAULT
    }

/**
 * Returns a copy of this route with its `highlightSettingId` set to [id] for any
 * route implementing [HighlightableRoute] (the settings data classes that carry
 * one). All other routes are returned unchanged.
 *
 * The registry stores each [com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem]'s
 * `route` with a *null* `highlightSettingId`; the specific setting to scroll to /
 * focus is the item's own `id`, injected here through the interface so the
 * in-settings search and the **home** header search share one dispatch — a
 * settings result tapped from either lands scrolled/focused on the matched row.
 * See [com.raulshma.jellyplay.feature.settings.HighlightScroll].
 */
fun Route.withHighlightSettingId(id: String): Route =
    (this as? HighlightableRoute)?.withHighlightSettingId(id) ?: this
