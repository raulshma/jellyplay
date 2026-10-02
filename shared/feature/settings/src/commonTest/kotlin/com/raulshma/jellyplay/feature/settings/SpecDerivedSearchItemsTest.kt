package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the spec-derived catalog lists (over the playback, appearance, home
 * and screensaver/system domains) against the retired hand-written declarations, field for field: the derivation
 * (`List<SettingsRowRecord>.toSearchItems(specEntries, bindings, routes, …)`)
 * must be byte-equivalent in behavior — same ids in the same order, same
 * bound resources, keywords from the specs, routes from the route maps,
 * icons from the bindings, isAdvanced flags and platform availability — so
 * the switch to spec-derived entries changes nothing downstream (deep-link
 * highlighting, group membership, search tie-break order, platform gating).
 *
 * The list INITIALIZATIONS themselves are the fail-fast drift ratchet: any
 * unbound spec id, stale binding, resource-key drift, missing route kind,
 * binding-icon drift or a converted record that still carries hand search
 * faces throws at catalog init — every test here touching the catalog keeps
 * that check exercised.
 */
class SpecDerivedSearchItemsTest {

    // ── Playback ────────────────────────────────────────────────────────

    @Test
    fun `playback player list keeps its 37-row order and projects the spec faces`() {
        val items = PlaybackSettingsSearchItems
        assertEquals(37, items.size)
        assertEquals(PlaybackSettingsIds.PLAYER_ENGINE, items.first().id)
        assertEquals(PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE, items.last().id)

        val engine = items.first()
        assertEquals("ss_player_engine_title", engine.titleRes.key)
        assertEquals("ss_player_engine_subtitle", engine.subtitleRes.key)
        assertEquals("ss_cat_playback", engine.categoryRes.key)
        assertEquals(listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"), engine.keywords)
        assertEquals(Route.PlaybackSettings(), engine.route)
        assertFalse(engine.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), engine.platforms)

        // A spec-declared advanced row…
        val controls = items.single { it.id == PlaybackSettingsIds.CONTROLS_TIMEOUT }
        assertTrue(controls.isAdvanced)
        // …and the spec-declared static Android-only rows.
        listOf(PlaybackSettingsIds.SEEK_DURATION, PlaybackSettingsIds.AUTO_ENTER_PIP, PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT, PlaybackSettingsIds.TV_ZOOM_MODE)
            .forEach { id ->
                val item = items.single { it.id == id }
                assertEquals(setOf(PlatformKind.ANDROID), item.platforms, "$id must stay Android-tagged")
            }
        // The capability-derived rows keep the runtime seam's tag (desktop
        // JVM: no orientation / touch gestures → Android-only).
        listOf(PlaybackSettingsIds.ORIENTATION, PlaybackSettingsIds.GESTURES, PlaybackSettingsIds.GESTURE_INDICATOR_SIDE).forEach { id ->
            assertEquals(setOf(PlatformKind.ANDROID), items.single { it.id == id }.platforms, "$id must follow the capability seam")
        }
        // The residual volume-memory row keeps its hand desktop tag.
        assertEquals(setOf(PlatformKind.DESKTOP), items.last().platforms)
    }

    @Test
    fun `advanced video list keeps its 16-row order with the three residuals in place`() {
        val items = PlaybackAdvancedVideoSearchItems
        assertEquals(16, items.size)
        assertEquals(
            listOf(
                PlaybackSettingsIds.DIALOGUE_BOOST,
                PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH,
                PlaybackSettingsIds.DECODER,
                PlaybackSettingsIds.AUDIO_PASSTHROUGH,
                PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3,
                PlaybackSettingsIds.PASSTHROUGH_CODEC_EAC3,
                PlaybackSettingsIds.PASSTHROUGH_CODEC_DTS,
                PlaybackSettingsIds.PASSTHROUGH_CODEC_DTSHD,
                PlaybackSettingsIds.PASSTHROUGH_CODEC_TRUEHD,
                PlaybackSettingsIds.MAX_AUDIO_CHANNELS,
                PlaybackSettingsIds.DOWNMIX_BOOST,
                PlaybackSettingsIds.FRAME_RATE_MATCHING,
                PlaybackSettingsIds.OFFLINE_PLAYBACK,
                PlaybackSettingsIds.STREAMING_QUALITY,
                PlaybackSettingsIds.AUDIO_DELAY,
                PlaybackSettingsIds.LIVE_STREAM_OPTION,
            ),
            items.map { it.id },
        )
        // Every row (spec-derived or residual) stays advanced — the group
        // only composes behind the advanced toggle.
        assertTrue(items.all { it.isAdvanced })
        // The per-codec rows restate their screen titles (the fold).
        assertEquals("settings_passthrough_codec_ac3", items.single { it.id == PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3 }.titleRes.key)
        assertEquals("ss_passthrough_codec_subtitle", items.single { it.id == PlaybackSettingsIds.PASSTHROUGH_CODEC_AC3 }.subtitleRes.key)
    }

    @Test
    fun `external engine row derives from the playback spec`() {
        val items = ExternalEngineSearchItems
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(PlaybackSettingsIds.EXTERNAL_PLAYER_APP, item.id)
        assertEquals("ss_external_player_app_title", item.titleRes.key)
        assertEquals(Route.PlaybackSettings(), item.route)
        assertFalse(item.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), item.platforms)
    }

    @Test
    fun `live tv list keeps its order - dvr residuals then the spec-backed segment rows`() {
        val items = LiveTvSearchItems
        assertEquals(10, items.size)
        assertEquals(
            listOf(
                PlaybackSettingsIds.DVR_PRE_PADDING,
                PlaybackSettingsIds.DVR_POST_PADDING,
                PlaybackSettingsIds.DVR_RECORDING_QUALITY,
                PlaybackSettingsIds.MEDIA_SEGMENT_INTRO,
                PlaybackSettingsIds.MEDIA_SEGMENT_OUTRO,
                PlaybackSettingsIds.MEDIA_SEGMENT_PREVIEW,
                PlaybackSettingsIds.MEDIA_SEGMENT_RECAP,
                PlaybackSettingsIds.MEDIA_SEGMENT_COMMERCIAL,
                PlaybackSettingsIds.MEDIA_SEGMENT_UNKNOWN,
                PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK,
            ),
            items.map { it.id },
        )
        // The segment rows restate the enum's core_segment faces (the fold).
        val intro = items.single { it.id == PlaybackSettingsIds.MEDIA_SEGMENT_INTRO }
        assertEquals("core_segment_intro", intro.titleRes.key)
        assertEquals("core_segment_intro_desc", intro.subtitleRes.key)
        assertTrue(items.drop(3).all { it.isAdvanced })
        assertFalse(items.take(2).all { it.isAdvanced })
    }

    // ── Appearance ──────────────────────────────────────────────────────

    @Test
    fun `appearance theme list keeps its 19-row order with the two residuals in place`() {
        val items = AppearanceThemeSearchItems
        assertEquals(19, items.size)
        assertEquals(AppearanceSettingsIds.DATE_FORMAT, items.first().id)
        assertEquals(AppearanceSettingsIds.SCHEDULED_END, items.last().id)
        assertEquals(
            listOf(AppearanceSettingsIds.LIBRARY_VIEW_MODE, AppearanceSettingsIds.NAV_LABELS),
            items.filter { it.id in setOf(AppearanceSettingsIds.LIBRARY_VIEW_MODE, AppearanceSettingsIds.NAV_LABELS) }.map { it.id },
            "the two residuals keep their catalog positions",
        )
        // The spec-derived alias row keeps its advanced flag…
        assertTrue(items.single { it.id == AppearanceSettingsIds.THEME_SCHEDULER }.isAdvanced)
        // …and the capability row keeps the runtime seam's tag (the JVM
        // desktop binary has no dynamic color → Android-only).
        assertEquals(setOf(PlatformKind.ANDROID), items.single { it.id == AppearanceSettingsIds.DYNAMIC_THEMING }.platforms)
    }

    @Test
    fun `appearance library list keeps its 8-row order with the five residuals in place`() {
        // The home-discovery watch-state quartet moved to HomeCardsSearchItems
        // (PS-4); the appearance side keeps the five library residuals plus
        // the haptics / share-media / hide-search-history spec-backed trio.
        val items = AppearanceLibrarySearchItems
        assertEquals(8, items.size)
        assertEquals(
            listOf(
                AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS,
                AppearanceSettingsIds.COMPACT_EPISODE_LIST,
                AppearanceSettingsIds.SKIP_SPECIALS,
                AppearanceSettingsIds.SHOW_MISSING_EPISODES,
                AppearanceSettingsIds.PREFER_LOGOS,
                AppearanceSettingsIds.HAPTICS_ENABLED,
                AppearanceSettingsIds.SHOW_SHARE_MEDIA,
                AppearanceSettingsIds.HIDE_SEARCH_HISTORY,
            ),
            items.map { it.id },
        )
    }

    @Test
    fun `appearance performance and eye care lists derive wholly from specs`() {
        assertEquals(2, AppearancePerformanceSearchItems.size)
        assertEquals(2, AppearanceEyeCareSearchItems.size)
        assertTrue(AppearancePerformanceSearchItems.all { it.isAdvanced })
        assertTrue(AppearanceEyeCareSearchItems.all { it.isAdvanced })
        assertEquals(Route.AppearanceSettings(), AppearanceEyeCareSearchItems.single { it.id == AppearanceSettingsIds.BLUE_LIGHT_STRENGTH }.route)
    }

    @Test
    fun `appearance navigation and newsletter lists stay hand-maintained projections`() {
        // The spec-less residual groups: records still project directly.
        assertEquals(2, AppearanceNavigationSearchItems.size)
        assertEquals(3, AppearanceNewsletterSearchItems.size)
    }

    // ── Home ────────────────────────────────────────────────────────────

    @Test
    fun `home lists keep their sizes and derive their routes from the spec kinds`() {
        assertEquals(10, HomeDisplaySearchItems.size)
        assertEquals(HomeSettingsIds.HOME_MODE, HomeDisplaySearchItems.first().id)
        assertEquals(HomeSettingsIds.UNHIDE_CW, HomeDisplaySearchItems.last().id)
        assertEquals(3, HomeNextUpSearchItems.size)
        assertEquals(4, HomeLayoutSearchItems.size)
        assertEquals(4, HomeCardsSearchItems.size)

        // The drill-in rows resolve their routes through the spec route kinds.
        assertIs<Route.PinnedHomeSections>(HomeLayoutSearchItems.single { it.id == HomeSettingsIds.PINNED_HOME_SECTIONS }.route)
        assertIs<Route.HomeLayoutPresets>(HomeLayoutSearchItems.single { it.id == HomeSettingsIds.HOME_LAYOUT_PRESETS }.route)
        assertIs<Route.LibraryHomeSections>(HomeLayoutSearchItems.single { it.id == HomeSettingsIds.CONFIGURE_LIBRARIES }.route)
        assertIs<Route.DiscoverRows>(HomeLayoutSearchItems.single { it.id == HomeSettingsIds.DISCOVER_ROWS }.route)
        assertIs<Route.NextUpExcluded>(HomeDisplaySearchItems.single { it.id == HomeSettingsIds.NEXT_UP_HIDDEN }.route)
        // The cards group (the PS-4 moved quartet) deep-links into the hub…
        assertIs<Route.HomeSettings>(HomeCardsSearchItems.first().route)
        // …and every home row renders unconditionally (no advanced flags).
        assertTrue(HomeDisplaySearchItems.none { it.isAdvanced })
        assertTrue(HomeNextUpSearchItems.none { it.isAdvanced })
        assertTrue(HomeLayoutSearchItems.none { it.isAdvanced })
        assertTrue(HomeCardsSearchItems.none { it.isAdvanced })
    }

    // ── System ──────────────────────────────────────────────────────────

    @Test
    fun `system list keeps its 19-row order with the two navigation residuals in place`() {
        val items = SystemSearchItems
        assertEquals(19, items.size)
        assertEquals(SettingsScreenIds.ADMIN_DASHBOARD, items.first().id)
        assertEquals(SettingsScreenIds.SETUP_WIZARD, items[1].id)
        assertEquals(SettingsScreenIds.HOOKS_IDLE_ENDED_CMD, items.last().id)
        // The dream rows deep-link into the settings screen itself…
        assertEquals(Route.Settings, items.single { it.id == SettingsScreenIds.SCREENSAVER_KEN_BURNS }.route)
        // …and the desktop-shell rows keep their DESKTOP-only tag from the
        // spec platform rules.
        listOf(
            SettingsScreenIds.DISCORD_PRESENCE_ENABLED,
            SettingsScreenIds.HOOKS_ENABLED,
            SettingsScreenIds.HOOKS_PLAY_CMD,
            SettingsScreenIds.HOOKS_STOP_CMD,
            SettingsScreenIds.HOOKS_ENDED_CMD,
            SettingsScreenIds.HOOKS_IDLE_CMD,
            SettingsScreenIds.HOOKS_IDLE_ENDED_CMD,
        ).forEach { id ->
            assertEquals(setOf(PlatformKind.DESKTOP), items.single { it.id == id }.platforms, "$id must stay desktop-tagged")
        }
    }

    @Test
    fun `the spec-derived lists land in their screen groups unchanged`() {
        // The group decorations reference the same list vals — the ids the
        // groups expose must be exactly the derived lists'.
        assertEquals(PlaybackSettingsSearchItems.map { it.id }, SettingsScreenGroups.playbackPlayer.itemIds)
        assertEquals(PlaybackAdvancedVideoSearchItems.map { it.id }, SettingsScreenGroups.playbackAdvancedVideo.itemIds)
        assertEquals(AppearanceThemeSearchItems.map { it.id }, SettingsScreenGroups.appearanceTheme.itemIds)
        assertEquals(HomeDisplaySearchItems.map { it.id }, SettingsScreenGroups.homeDisplay.itemIds)
        assertEquals(SystemSearchItems.map { it.id }, SystemSearchItems.map { it.id })
        // …and the catalog is still the flat concatenation of the groups
        // (the matcher tie-break order).
        assertEquals(
            SettingsScreenGroups.all.flatMap { it.items }.map { it.id },
            SettingsSearchCatalog.items.map { it.id },
        )
    }
}
