package com.raulshma.jellyplay.core.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.model.ShortcutSurface
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkTarget
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

    /**
     * @param openDownloadSheet When true, the detail screen pre-presents the
     *   series download sheet once the item loads — the deep-link from a card
     *   long-press Download on a series (the series flow needs the user's
     *   season/episode selection before anything is enqueued).
     */
    @Serializable data class MediaDetail(
        val itemId: String,
        val openDownloadSheet: Boolean = false,
    ) : Route() {
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

    /** Full TMDB reviews screen reached from "All reviews (N)" on a detail screen's reviews preview. */
    @Serializable data class Reviews(val itemId: String) : Route() {
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
        /**
         * Resolve the session to [com.raulshma.jellyplay.feature.player.video.PlaybackSource.Online]
         * even when a completed download exists — the "Stream instead" action
         * on a downloaded item. Default false keeps the Auto behavior (local
         * file wins when usable).
         */
        val forceStream: Boolean = false,
    ) : Route() {
        override val isFullScreen = true
        override val isPlayer = true
    }

    @Serializable data class AudioPlayer(val itemId: String) : Route() {
        override val isFullScreen = true
        override val isPlayer = true
    }

    // Optional one-shot deep-link destination (detail "Contents" tap):
    // jumpHref = EPUB spine href, jumpPage = 0-based PDF page. Defaults keep
    // plain Read/Continue call sites compiling.
    @Serializable data class BookReader(
        val itemId: String,
        val jumpHref: String? = null,
        val jumpPage: Int? = null,
    ) : Route() {
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

    /**
     * The appearance settings HUB (ADR-0017's domain hub split): the domain's
     * four entrance rows (Theme & display / Library views / Detail screen /
     * Navigation & haptics), each navigating a leaf screen, with the
     * advanced-gated Newsletter group rendered below them — the glossary's
     * declared hub exception. The domain's settings rows themselves deep-link
     * the leaf routes below (their specs carry the per-leaf route kinds); this
     * route stays the domain's root destination (the settings root's
     * Appearance entrance, the search pane's quick category) and its
     * domain-wide reset action. A stale deep-link into a moved row id
     * resolves to -1 here (no highlight) and lands on the owning leaf through
     * the catalog instead.
     */
    @Serializable data class AppearanceSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Theme & display — the appearance theme leaf (ADR-0017): the theme
     * mode/style/accent cluster, display & accessibility knobs, the TV
     * overscan pair, and the advanced Performance (Reduce Motion) and Eye
     * care groups. Deep-linked by the rows whose specs carry the
     * `theme_settings` route kind ([Route.AppearanceSettings] is the hub).
     */
    @Serializable data class ThemeSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Library views — the library/card browsing leaf (ADR-0017): the
     * library-content display toggles (episode lists, badges, logos,
     * trailers, haptics, the search-history toggle) and the screen-local
     * confirm-library-reset row. Deep-linked by the rows whose specs carry
     * the `library_display_settings` route kind.
     */
    @Serializable data class LibraryDisplaySettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Detail screen — the media-detail presentation leaf (ADR-0017): every
     * `detail_*` row (chips, rails, limits, section order + its reorderable
     * editor) plus the filmography pair. Deep-linked by the rows whose specs
     * carry the `detail_display_settings` route kind.
     */
    @Serializable data class DetailDisplaySettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Navigation & haptics — the app-chrome leaf (ADR-0017): the floating
     * navigation-bar customization group with its global interface rows
     * (start tab, 24h clock, press/haptics/density/overscroll). Deep-linked
     * by the rows whose specs carry the `navigation_display_settings` route
     * kind.
     */
    @Serializable data class NavigationDisplaySettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * The home settings HUB (ADR-0017's domain hub split): the domain's three
     * entrance rows (Home display / Rows & layout / Cards & badges), each
     * navigating a leaf screen, with the advanced-gated Fine-tuning group
     * rendered below them — the glossary's declared hub exception (the
     * appearance hub's newsletter precedent). The domain's settings rows
     * themselves deep-link the leaf routes below (their specs carry the
     * per-leaf route kinds; the fine-tuning rows keep the hub kind); this
     * route stays the domain's root destination (the settings root's Home
     * entrance, the search pane's quick category) and its domain-wide reset
     * action (the HOME_DISCOVERY reset spans every leaf, so it stays HERE).
     * A stale deep-link into a moved row id resolves to -1 here (no
     * highlight) and lands on the owning leaf through the catalog instead.
     */
    @Serializable data class HomeSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Home display — the mainstream home-presentation leaf (ADR-0017's home
     * split): home mode, the hero/backdrop cluster (with its gates), the
     * clock/dock/chrome toggles, the stats-card and genre-rail features and
     * their presentation picks. Deep-linked by the rows whose specs carry the
     * `home_display_settings` route kind ([Route.HomeSettings] is the hub).
     */
    @Serializable data class HomeDisplaySettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Rows & layout — the home-rows curation leaf (ADR-0017's home split):
     * the Next Up behavior group, the Home Screen Layout group (the four
     * drill-ins, the header-shortcuts editor, the layout sync/upload/restore
     * actions and the reorderable section toggles) and the hidden/restore
     * curation quartet. Deep-linked by the rows whose specs carry the
     * `home_rows_settings` route kind; the drill-in rows deep-link their own
     * dedicated editors ([Route.PinnedHomeSections] et al.).
     */
    @Serializable data class HomeRowsSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Cards & badges — the card-display leaf (ADR-0017's home split): the
     * badge/ratings/spoiler toggles, the TV-gated trickplay pair and the
     * card-chrome trio (scale, corner radius, row animations). Deep-linked by
     * the rows whose specs carry the `home_cards_settings` route kind.
     */
    @Serializable data class HomeCardsSettings(val highlightSettingId: String? = null) : Route(),
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

    /** Discover Rows manage screen (list / reorder / toggle / templates). */
    @Serializable data class DiscoverRows(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /** Discover row editor; null [rowId] = new row. */
    @Serializable data class DiscoverRowEditor(val rowId: String? = null) : Route()

    /**
     * "Hidden from Next Up" management screen — the series the user excluded
     * from the home Next Up row (the home-discovery store's
     * `NEXT_UP_EXCLUDED_SERIES_IDS` set), with per-series and bulk restore.
     * Reached from the Home settings hub's "Hidden from Next Up" row.
     */
    @Serializable data object NextUpExcluded : Route()

    /**
     * "Hidden libraries" management screen — the libraries the user hid from
     * the home My Media tiles row (the home-discovery store's
     * `MY_MEDIA_HIDDEN_LIBRARY_IDS` set), with per-library and bulk restore.
     * Reached from the Home settings hub's "Hidden libraries" row.
     */
    @Serializable data object MyMediaHidden : Route()

    /**
     * The playback settings HUB (ADR-0017's domain hub split): the playback
     * domain's eight entrance rows (Basics / Controls & gestures / Player
     * chrome / Video & transcoding / Player engines / Skipping & segments /
     * SyncPlay & casting / Live TV & DVR), each navigating a leaf screen. The
     * domain's settings rows themselves deep-link the leaf routes below
     * (their specs carry the per-leaf route kinds); this route stays the
     * domain's root destination (the settings root's Playback entrance, the
     * search pane's quick category) and its domain-wide reset action.
     */
    @Serializable data class PlaybackSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Playback Basics — the mainstream playback-behavior leaf (ADR-0017):
     * engine choice, transport defaults, the autoplay/resume family, session
     * restore, the pause-screen experience and the streaming/privacy
     * toggles. Deep-linked by the rows whose specs carry the
     * `playback_basics` route kind ([Route.PlaybackSettings] is the hub).
     */
    @Serializable data class PlaybackBasics(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Playback Controls & gestures — the input-surface leaf (ADR-0017):
     * screen orientation, the touch-gesture set, the input-binding editor
     * drill-in and the keyboard/hold-seek cadence presets. Deep-linked by
     * the rows whose specs carry the `playback_controls` route kind.
     */
    @Serializable data class PlaybackControls(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Player chrome — the player-HUD customization leaf (ADR-0017): the
     * video_* overlay/transport/gesture-curve/seek-bar presentation cluster
     * plus the bitmap-subtitle scale and the TV zoom. Every row is
     * advanced-gated. Deep-linked by the rows whose specs carry the
     * `player_chrome_settings` route kind.
     */
    @Serializable data class PlayerChromeSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Video & transcoding — the whole Advanced Video group as one
     * advanced-only leaf (ADR-0017): decoder, passthrough + per-codec set,
     * streaming quality, the performance profile + Re-detect, HDR, the
     * transcode caps and the custom device profile. Deep-linked by the rows
     * whose specs carry the `playback_video_quality` route kind.
     */
    @Serializable data class PlaybackVideoQuality(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Player engines — all four engine-config branches as ONE advanced-only
     * leaf (ADR-0017): the MPV/VLC/ExoPlayer/external branch admission rides
     * the preferred player exactly as on the former combined screen.
     * Deep-linked by the rows whose specs carry the `player_engine_settings`
     * route kind.
     */
    @Serializable data class PlayerEngineSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Skipping & segments — the media-segment leaf (ADR-0017): the six
     * per-type skip behaviors, the skip-on-seek toggle, the skip-prompt
     * duration and the Next Up preroll. Deep-linked by the rows whose specs
     * carry the `media_segment_settings` route kind.
     */
    @Serializable data class MediaSegmentSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * SyncPlay & casting — the watch-party pair of groups as one leaf
     * (ADR-0017): the SyncPlay trio and the casting trio. Deep-linked by the
     * rows whose specs carry the `syncplay_casting_settings` route kind.
     */
    @Serializable data class SyncPlayCastingSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Live TV & DVR — the DVR group as one leaf (ADR-0017): recording
     * pre/post padding and quality. Deep-linked by the rows whose specs
     * carry the `live_tv_dvr_settings` route kind.
     */
    @Serializable data class LiveTvDvrSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Performance hub — the master level dial (STANDARD/BALANCED/PERFORMANCE)
     * plus the granular tri-state effect overrides, the image knobs and the
     * ExoPlayer buffer quartet. The search rows whose specs carry the
     * `performance_settings` route kind deep-link here (the
     * `AppearancePreferenceSpecs.ROUTE_PERFORMANCE_SETTINGS` constant).
     */
    @Serializable data class PerformanceSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * The player input-binding editor (issue #171 generalized): per-input
     * action assignment + enable switches over touch / wheel / keyboard /
     * D-pad. Reached from Playback settings' "Customize controls" row.
     */
    @Serializable data object InputBindings : Route()

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

    /**
     * Storage & network — the cache and network halves of the former combined
     * storage screen (ADR-0017's storage split): the Storage/cache group
     * (clear actions + the advanced tuning rows + the screen-local cache-used
     * info row) and the Network & Offline group. The Downloads group moved to
     * the sibling [Route.DownloadsSettings] screen; the settings root's
     * entrance row carries this route (relabeled "Storage & network", the row
     * id unchanged).
     */
    @Serializable data class StorageSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * Downloads — the download-behavior sibling screen (ADR-0017's storage
     * split): the whole Downloads group (quality, smart/auto-downloads with
     * their retention clusters, the schedule window and the storage caps)
     * moved here intact from the former combined screen; the settings root's
     * Downloads entrance row (static subtitle) carries this route.
     */
    @Serializable data class DownloadsSettings(val highlightSettingId: String? = null) : Route(),
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
     * The unified restore wizard — ONE flow for restoring settings regardless
     * of source: pick source (server restore point / backup file) → diff vs
     * the live state → select categories → confirm → automatic pre-apply
     * snapshot → apply. Reached from the backup screen (a picked file rides
     * in [uri]) and the sync screen (a tapped restore point rides in
     * [snapshotId]; a bare entry opens the wizard's own source step).
     *
     * Replaces the retired `ImportPreview` route (the per-file preview) and
     * the sync screen's one-click snapshot restore + import-from-file rows.
     */
    @Serializable data class RestoreWizard(
        val uri: String? = null,
        val snapshotId: String? = null,
    ) : Route()

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

    /**
     * The JellyPlay companion-plugin's inbox messages screen (ADR 0010) — the
     * durable counterpart of the plugin's live events stream: admin-published
     * messages with read state, tapping one marks it read. Reached ONLY from
     * the Settings root's capability-gated "Messages" entry (plugin probe
     * AVAILABLE + the `messages` feature key), so no in-screen gate chrome.
     */
    @Serializable data object JellyPlayMessages : Route()

    /**
     * The JellyPlay companion-plugin's settings-sync screen (ADR 0010) — the
     * sync engine's whole UI: opt-in toggle, sync-now, server usage + quotas,
     * per-device last sync, this device's resolved profile, the sync history
     * ledger, and the namespace reset / force-re-pull recovery actions.
     * Reached ONLY from the Settings root's capability-gated "Sync" entry
     * (plugin probe AVAILABLE + the `settings-sync` meta feature key — no
     * user toggle exists for meta keys), so no in-screen gate chrome.
     */
    @Serializable data object JellyPlaySync : Route()

    /**
     * Ambient settings — the TV dream/screensaver group and the desktop
     * idle-ambient group as ONE capability-gated screen (ADR-0017: the root's
     * inline tuning groups moved behind one Look & feel entrance; the TV
     * screensaver rows render only in TV mode, the idle-ambient rows only
     * where the platform backs the idle screen). Reached from the settings
     * root's Ambient entrance; the search rows whose specs carry the
     * `ambient_settings` route kind deep-link here.
     */
    @Serializable data class AmbientSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * The JellyPlay companion-plugin's settings screen (ADR-0017): the root
     * screen's former inline plugin section (the four plugin-surface entries
     * — sync / messages / my ratings / your watching — plus the per-feature
     * switches) as ONE screen behind one Integrations entrance. The screen
     * stays gated on the plugin probe exactly as the inline section was
     * (unavailable = graceful-absent), so it carries no extra gate chrome.
     */
    @Serializable data class JellyPlayPluginSettings(val highlightSettingId: String? = null) : Route(),
        HighlightableRoute {
        override fun withHighlightSettingId(id: String) = copy(highlightSettingId = id)
    }

    /**
     * The JellyPlay companion-plugin's "My ratings" screen (ADR 0010) — the
     * user's synced likes / dislikes / ratings as the plugin recorded them,
     * tapping a row opens the item's detail. Reached ONLY from the Settings
     * root's capability-gated entry (plugin probe AVAILABLE + the
     * `user-ratings` feature key), so no in-screen gate chrome.
     */
    @Serializable data object JellyPlayUserRatings : Route()

    /**
     * The JellyPlay companion-plugin's "Your watching" screen — the signed-in
     * user's OWN play aggregates (plays, watch time, per-day chart, top
     * titles) over a 7/30/90-day window, the per-user face of the plugin's
     * analytics (any user, unlike the admin dashboard). Reached ONLY from the
     * Settings root's capability-gated entry (plugin probe AVAILABLE + the
     * `analytics` feature key + the user's toggle), so no in-screen gate
     * chrome.
     */
    @Serializable data object JellyPlayYourWatching : Route()

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

    /**
     * Admin transcodes monitor — the companion plugin's live active-transcode
     * list (ADR 0010, `transcodes` feature). An admin tool presented like
     * [Devices] / [Logs]; the admin gate is the shared admin area's
     * AdminRouteContainer, so the route itself carries no extra classification.
     */
    @Serializable data object JellyPlayTranscodes : Route() {
        override val isModal = true
    }

    /**
     * Admin analytics dashboard — the companion plugin's play-history
     * aggregates (ADR 0010, `analytics` feature). Same presentation contract
     * as [JellyPlayTranscodes]: an admin tool behind the shared admin area's
     * AdminRouteContainer, so the route itself carries no extra classification.
     */
    @Serializable data object JellyPlayAnalytics : Route() {
        override val isModal = true
    }

    /** Admin dashboard's backup management (list / create / restore). */
    @Serializable data object AdminBackups : Route() {
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

    /**
     * What's New archive — the browsable list of every known release's
     * curated entries (the structured feed behind the post-update sheet).
     * Reached from the Settings hub; entries can deep-link elsewhere.
     */
    @Serializable data object WhatsNew : Route()

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

    /**
     * The shortcuts-customization editor (the pinned header/overflow lists,
     * per-shortcut icon + label), presented as two tabs — one per surface.
     * Modal like [Shortcuts]; reachable from the Shortcuts screen's Customize
     * action, the Settings rows (Home Screen's header-shortcuts row, the
     * Navigation group's customize row), and a header shortcut's touch
     * long-press. [initialSurface] is the tab the editor opens on, so each
     * Settings entry point lands on its own surface; the default matches the
     * shipped defaults (the overflow menu is what's pinned out of the box).
     */
    @Serializable data class ShortcutsCustomize(
        val initialSurface: ShortcutSurface = ShortcutSurface.OVERFLOW,
    ) : Route() {
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

/**
 * The parsed deep-link target → [Route] fold — the ONE target→destination
 * table every platform shell consumes. [DeepLinkTarget] lives in core:model
 * (it must stay UI-free for the link grammar), [Route] here; this extension
 * sits beside [Route] so the Android shell's DeepLinkHandler and the desktop
 * shell's DesktopLinkOpenPolicy consume the same rows instead of two
 * hand-mirrored when-expressions kept in agreement by "same rows, same nulls"
 * KDoc. The shells keep only their Uri→target parsing glue.
 *
 * Rows (pinned by DeepLinkTargetRouteTest):
 *  - MediaDetail / NewsletterSection / SeerrDetail → their detail routes;
 *  - Search / Settings / Downloads / Library → their top-level routes;
 *  - SyncPlayJoin → `null` on EVERY shell: it is the Discord Rich Presence
 *    Join button's payload, consumed by the desktop's
 *    DiscordPresenceService.handleJoinSecret — Android has no join-by-link
 *    surface either (SyncPlay groups are joined through the group browser),
 *    so the link parses but routes nowhere;
 *  - `null` (a grammar parse miss) → `null`.
 */
fun DeepLinkTarget?.toRoute(): Route? = when (this) {
    is DeepLinkTarget.MediaDetail -> Route.MediaDetail(itemId)
    is DeepLinkTarget.NewsletterSection -> Route.NewsletterSectionList(section)
    is DeepLinkTarget.SeerrDetail -> Route.SeerrDetail(tmdbId = tmdbId, mediaType = mediaType)
    DeepLinkTarget.Search -> Route.Search
    DeepLinkTarget.Settings -> Route.Settings
    DeepLinkTarget.Downloads -> Route.Downloads
    DeepLinkTarget.Library -> Route.Library
    is DeepLinkTarget.SyncPlayJoin -> null
    null -> null
}
