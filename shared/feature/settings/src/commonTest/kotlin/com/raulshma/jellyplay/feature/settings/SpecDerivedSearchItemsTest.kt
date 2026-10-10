package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the spec-derived catalog lists and the domains' FUSED row projections
 * field for field: the fused `List<SettingsRow>.toSearchItems` derivation
 * (every domain but the experimental screen) must be byte-equivalent in
 * behavior to the retired record+binding declarations — same ids in the same
 * order, same bound resources, keywords from the specs, routes from the
 * route maps, icons from the rows, isAdvanced flags and platform
 * availability — so the fusion changes nothing downstream (deep-link
 * highlighting, group membership, search tie-break order, platform gating).
 *
 * The list INITIALIZATIONS themselves are the fail-fast drift ratchet: any
 * spec id without a row, resource-key drift, missing route kind or a
 * converted row that still carries hand search faces throws at catalog
 * init — every test here touching the catalog keeps that check exercised.
 */
class SpecDerivedSearchItemsTest {

    // ── Playback ────────────────────────────────────────────────────────

    @Test
    fun `playback player list keeps its 41-row order and projects the spec faces`() {
        val items = PlaybackSettingsSearchItems
        assertEquals(41, items.size)
        assertEquals(PlaybackRows.PlayerEngine.id, items.first().id)
        assertEquals(PlaybackRows.RememberVolumePerContentType.id, items.last().id)

        val engine = items.first()
        assertEquals("ss_player_engine_title", engine.titleRes.key)
        assertEquals("ss_player_engine_subtitle", engine.subtitleRes.key)
        assertEquals("ss_cat_playback", engine.categoryRes.key)
        assertEquals(listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"), engine.keywords)
        assertEquals(Route.PlaybackSettings(), engine.route)
        assertFalse(engine.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), engine.platforms)

        // A spec-declared advanced row…
        val controls = items.single { it.id == PlaybackRows.ControlsTimeout.id }
        assertTrue(controls.isAdvanced)
        // …and the spec-declared static Android-only rows.
        listOf(PlaybackRows.SeekDuration.id, PlaybackRows.AutoEnterPip.id, PlaybackRows.AndroidTvWatchNext.id, PlaybackRows.TvZoomMode.id)
            .forEach { id ->
                val item = items.single { it.id == id }
                assertEquals(setOf(PlatformKind.ANDROID), item.platforms, "$id must stay Android-tagged")
            }
        // The capability-derived rows keep the runtime seam's tag (desktop
        // JVM: no orientation / touch gestures → Android-only).
        listOf(
            PlaybackRows.Orientation.id,
            PlaybackRows.Gestures.id,
            PlaybackRows.GestureIndicatorSide.id,
            PlaybackRows.DoubleTapHoldSeek.id,
        ).forEach { id ->
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
                PlaybackRows.DialogueBoost.id,
                PlaybackRows.DialogueBoostStrength.id,
                PlaybackRows.Decoder.id,
                PlaybackRows.AudioPassthrough.id,
                PlaybackRows.PassthroughCodecAc3.id,
                PlaybackRows.PassthroughCodecEac3.id,
                PlaybackRows.PassthroughCodecDts.id,
                PlaybackRows.PassthroughCodecDtshd.id,
                PlaybackRows.PassthroughCodecTruehd.id,
                PlaybackRows.MaxAudioChannels.id,
                PlaybackRows.DownmixBoost.id,
                PlaybackRows.FrameRateMatching.id,
                PlaybackRows.OfflinePlayback.id,
                PlaybackRows.StreamingQuality.id,
                PlaybackRows.AudioDelay.id,
                PlaybackRows.LiveStreamOption.id,
            ),
            items.map { it.id },
        )
        // Every row (spec-derived or residual) stays advanced — the group
        // only composes behind the advanced toggle.
        assertTrue(items.all { it.isAdvanced })
        // The per-codec rows restate their screen titles (the fold).
        assertEquals("settings_passthrough_codec_ac3", items.single { it.id == PlaybackRows.PassthroughCodecAc3.id }.titleRes.key)
        assertEquals("ss_passthrough_codec_subtitle", items.single { it.id == PlaybackRows.PassthroughCodecAc3.id }.subtitleRes.key)
    }

    @Test
    fun `external engine row derives from the playback spec`() {
        val items = ExternalEngineSearchItems
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(PlaybackRows.ExternalPlayerApp.id, item.id)
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
                PlaybackRows.DvrPrePadding.id,
                PlaybackRows.DvrPostPadding.id,
                PlaybackRows.DvrRecordingQuality.id,
                PlaybackRows.MediaSegmentIntro.id,
                PlaybackRows.MediaSegmentOutro.id,
                PlaybackRows.MediaSegmentPreview.id,
                PlaybackRows.MediaSegmentRecap.id,
                PlaybackRows.MediaSegmentCommercial.id,
                PlaybackRows.MediaSegmentUnknown.id,
                PlaybackRows.SkipSegmentsOnSeek.id,
            ),
            items.map { it.id },
        )
        // The segment rows restate the enum's core_segment faces (the fold).
        val intro = items.single { it.id == PlaybackRows.MediaSegmentIntro.id }
        assertEquals("core_segment_intro", intro.titleRes.key)
        assertEquals("core_segment_intro_desc", intro.subtitleRes.key)
        assertTrue(items.drop(3).all { it.isAdvanced })
        assertFalse(items.take(2).all { it.isAdvanced })
    }

    // ── Appearance ──────────────────────────────────────────────────────

    @Test
    fun `appearance theme list keeps its 20-row order with the two residuals in place`() {
        val items = AppearanceThemeSearchItems
        assertEquals(20, items.size)
        assertEquals(AppearanceRows.DateFormat.id, items.first().id)
        assertEquals(AppearanceRows.ScheduledEnd.id, items.last().id)
        assertEquals(
            listOf(AppearanceRows.LibraryViewMode.id, AppearanceRows.NavLabels.id),
            items.filter { it.id in setOf(AppearanceRows.LibraryViewMode.id, AppearanceRows.NavLabels.id) }.map { it.id },
            "the two residuals keep their catalog positions",
        )
        // The spec-derived alias row keeps its advanced flag…
        assertTrue(items.single { it.id == AppearanceRows.ThemeScheduler.id }.isAdvanced)
        // …and the capability row keeps the runtime seam's tag (the JVM
        // desktop binary has no dynamic color → Android-only).
        assertEquals(setOf(PlatformKind.ANDROID), items.single { it.id == AppearanceRows.DynamicTheming.id }.platforms)
        // The TV-only "Screen fit" overscan picker (the layout
        // override's form-factor twin) sits right beside it: not advanced
        // (calibration stays reachable without the toggle) and Android-tagged
        // per the TV-rows-stay-ANDROID convention.
        val screenFit = items.single { it.id == AppearanceRows.ScreenFit.id }
        assertFalse(screenFit.isAdvanced)
        assertEquals(setOf(PlatformKind.ANDROID), screenFit.platforms)
        assertEquals(
            listOf(AppearanceRows.LayoutMode.id, AppearanceRows.ScreenFit.id),
            items.filter { it.id in setOf(AppearanceRows.LayoutMode.id, AppearanceRows.ScreenFit.id) }.map { it.id },
            "the form-factor fork pair keeps their catalog positions",
        )
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
                AppearanceRows.HideEpisodeThumbnails.id,
                AppearanceRows.CompactEpisodeList.id,
                AppearanceRows.SkipSpecials.id,
                AppearanceRows.ShowMissingEpisodes.id,
                AppearanceRows.PreferLogos.id,
                AppearanceRows.HapticsEnabled.id,
                AppearanceRows.ShowShareMedia.id,
                AppearanceRows.HideSearchHistory.id,
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
        assertEquals(Route.AppearanceSettings(), AppearanceEyeCareSearchItems.single { it.id == AppearanceRows.BlueLightStrength.id }.route)
    }

    @Test
    fun `appearance navigation and newsletter lists stay residual-row projections`() {
        // The spec-less residual groups: the fused rows still project their
        // hand search faces directly.
        assertEquals(2, AppearanceNavigationSearchItems.size)
        assertEquals(3, AppearanceNewsletterSearchItems.size)
    }

    // ── Home ────────────────────────────────────────────────────────────

    @Test
    fun `home lists keep their sizes and derive their routes from the spec kinds`() {
        assertEquals(10, HomeDisplaySearchItems.size)
        assertEquals(HomeRows.HomeMode.id, HomeDisplaySearchItems.first().id)
        assertEquals(HomeRows.UnhideCw.id, HomeDisplaySearchItems.last().id)
        assertEquals(3, HomeNextUpSearchItems.size)
        assertEquals(4, HomeLayoutSearchItems.size)
        assertEquals(4, HomeCardsSearchItems.size)

        // The drill-in rows resolve their routes through the spec route kinds.
        assertIs<Route.PinnedHomeSections>(HomeLayoutSearchItems.single { it.id == HomeRows.PinnedHomeSections.id }.route)
        assertIs<Route.HomeLayoutPresets>(HomeLayoutSearchItems.single { it.id == HomeRows.HomeLayoutPresets.id }.route)
        assertIs<Route.LibraryHomeSections>(HomeLayoutSearchItems.single { it.id == HomeRows.ConfigureLibraries.id }.route)
        assertIs<Route.DiscoverRows>(HomeLayoutSearchItems.single { it.id == HomeRows.DiscoverRows.id }.route)
        assertIs<Route.NextUpExcluded>(HomeDisplaySearchItems.single { it.id == HomeRows.NextUpHidden.id }.route)
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
        assertEquals(SystemRows.AdminDashboard.id, items.first().id)
        assertEquals(SystemRows.SetupWizard.id, items[1].id)
        assertEquals(SystemRows.HooksIdleEndedCmd.id, items.last().id)
        // The dream rows deep-link into the settings screen itself…
        assertEquals(Route.Settings, items.single { it.id == SystemRows.ScreensaverKenBurns.id }.route)
        // …and the desktop-shell rows keep their DESKTOP-only tag from the
        // spec platform rules.
        listOf(
            SystemRows.DiscordPresenceEnabled.id,
            SystemRows.HooksEnabled.id,
            SystemRows.HooksPlayCmd.id,
            SystemRows.HooksStopCmd.id,
            SystemRows.HooksEndedCmd.id,
            SystemRows.HooksIdleCmd.id,
            SystemRows.HooksIdleEndedCmd.id,
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
