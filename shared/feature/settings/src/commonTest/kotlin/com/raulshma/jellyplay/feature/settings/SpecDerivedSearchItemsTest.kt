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
 * (every domain but the experimental screen) must match the spec+row
 * declarations in behavior — same ids in the same
 * order, same bound resources, keywords from the specs, routes from the
 * route maps, icons from the rows, isAdvanced flags and platform
 * availability — so the fusion changes nothing downstream (deep-link
 * highlighting, group membership, search tie-break order, platform gating).
 *
 * The list INITIALIZATIONS themselves are the fail-fast drift guard: any
 * spec id without a row, resource-key drift, missing route kind or a
 * converted row that still carries hand search faces throws at catalog
 * init — every test here touching the catalog keeps that check exercised.
 */
class SpecDerivedSearchItemsTest {

    // ── Playback ────────────────────────────────────────────────────────

    @Test
    fun `playback basics list keeps its 45-row order and projects the spec faces`() {
        // ADR-0017: the former 83-row player list split — Basics is the
        // mainstream behavior set, in the player list's relative order.
        val items = PlaybackBasicsSearchItems
        assertEquals(45, items.size)
        assertEquals(PlaybackRows.PlayerEngine.id, items.first().id)
        // The pass-2 QoL tail; confirm-exit stays the leaf's catalog tail.
        assertEquals(PlaybackRows.ConfirmExit.id, items.last().id)

        val engine = items.first()
        assertEquals("ss_player_engine_title", engine.titleRes.key)
        assertEquals("ss_player_engine_subtitle", engine.subtitleRes.key)
        assertEquals("ss_cat_playback", engine.categoryRes.key)
        assertEquals(listOf("player", "engine", "mpv", "exoplayer", "vlc", "playback"), engine.keywords)
        assertEquals(Route.PlaybackBasics(), engine.route)
        assertFalse(engine.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), engine.platforms)

        // A spec-declared advanced row…
        val controls = items.single { it.id == PlaybackRows.ControlsTimeout.id }
        assertTrue(controls.isAdvanced)
        // …and the spec-declared static Android-only rows (the TV zoom
        // moved to the chrome leaf).
        listOf(PlaybackRows.SeekDuration.id, PlaybackRows.AutoEnterPip.id, PlaybackRows.AndroidTvWatchNext.id)
            .forEach { id ->
                val item = items.single { it.id == id }
                assertEquals(setOf(PlatformKind.ANDROID), item.platforms, "$id must stay Android-tagged")
            }
        // The residual volume-memory row keeps its hand desktop tag (it no
        // longer closes the list — the player QoL wave appended after it).
        assertEquals(
            setOf(PlatformKind.DESKTOP),
            items.single { it.id == PlaybackRows.RememberVolumePerContentType.id }.platforms,
        )
    }

    @Test
    fun `playback controls list keeps its 10-row order and follows the capability seam`() {
        val items = PlaybackControlsSearchItems
        assertEquals(10, items.size)
        assertEquals(PlaybackRows.Orientation.id, items.first().id)
        assertEquals(PlaybackRows.HoldSeekCadence.id, items.last().id)
        // Every row rides the domain's spec kinds (playback_controls for
        // this leaf) — including the drill-in launcher row: a search hit
        // lands on the controls leaf highlighting the launcher (exactly the
        // former combined-screen behavior, narrowed to the row's leaf).
        assertEquals(Route.PlaybackControls(), items.single { it.id == PlaybackRows.InputBindings.id }.route)
        assertEquals(Route.PlaybackControls(), items.single { it.id == PlaybackRows.Orientation.id }.route)
        // The capability-derived rows keep the runtime seam's tag: a
        // capability the running actual supports tags the row for every
        // platform (`platformsForCapability(true)`), a missing one collapses
        // to Android-only. The expectation derives from the same
        // `settingsCapabilities` actual the derivation reads — the desktop
        // JVM actual has no orientation/touch (Android-only tag), the Android
        // actual does (all-platform tag) — so the test is platform-portable
        // across the jvmTest and androidHostTest lanes.
        listOf(
            PlaybackRows.Orientation.id to settingsCapabilities.supportsScreenOrientation,
            PlaybackRows.Gestures.id to settingsCapabilities.supportsTouchGestures,
            PlaybackRows.GestureIndicatorSide.id to settingsCapabilities.supportsTouchGestures,
            PlaybackRows.DoubleTapHoldSeek.id to settingsCapabilities.supportsTouchGestures,
        ).forEach { (id, supported) ->
            assertEquals(
                platformsForCapability(supported),
                items.single { it.id == id }.platforms,
                "$id must follow the capability seam",
            )
        }
    }

    @Test
    fun `playback chrome list keeps its 29-row order all-advanced`() {
        val items = PlaybackPlayerChromeSearchItems
        assertEquals(29, items.size)
        assertEquals(PlaybackRows.BitmapSubtitleScale.id, items.first().id)
        assertEquals(PlaybackRows.PlaybackDiagnostics.id, items.last().id)
        // Every row rides the advanced toggle (the group composes only
        // behind it).
        assertTrue(items.all { it.isAdvanced })
        // The TV overscan zoom deep-links this leaf like its neighbors.
        assertEquals(Route.PlayerChromeSettings(), items.single { it.id == PlaybackRows.TvZoomMode.id }.route)
        assertEquals(setOf(PlatformKind.ANDROID), items.single { it.id == PlaybackRows.TvZoomMode.id }.platforms)
    }

    @Test
    fun `the playback hub list is the eight entrance rows in leaf order`() {
        val items = PlaybackHubSearchItems
        assertEquals(8, items.size)
        assertEquals(
            listOf(
                PlaybackRows.BasicsEntrance.id,
                PlaybackRows.ControlsEntrance.id,
                PlaybackRows.PlayerChromeEntrance.id,
                PlaybackRows.VideoQualityEntrance.id,
                PlaybackRows.PlayerEnginesEntrance.id,
                PlaybackRows.SegmentsEntrance.id,
                PlaybackRows.SyncPlayCastingEntrance.id,
                PlaybackRows.LiveTvDvrEntrance.id,
            ),
            items.map { it.id },
        )
        // Every entrance deep-links the leaf it opens, carries hand search
        // faces (residual launcher rows) and stays always-visible.
        listOf(
            PlaybackRows.BasicsEntrance.id to Route.PlaybackBasics(),
            PlaybackRows.ControlsEntrance.id to Route.PlaybackControls(),
            PlaybackRows.PlayerChromeEntrance.id to Route.PlayerChromeSettings(),
            PlaybackRows.VideoQualityEntrance.id to Route.PlaybackVideoQuality(),
            PlaybackRows.PlayerEnginesEntrance.id to Route.PlayerEngineSettings(),
            PlaybackRows.SegmentsEntrance.id to Route.MediaSegmentSettings(),
            PlaybackRows.SyncPlayCastingEntrance.id to Route.SyncPlayCastingSettings(),
            PlaybackRows.LiveTvDvrEntrance.id to Route.LiveTvDvrSettings(),
        ).forEach { (id, route) ->
            assertEquals(route, items.single { it.id == id }.route, "$id must deep-link its leaf")
            assertTrue(items.single { it.id == id }.keywords.isNotEmpty(), "$id carries no keywords")
            assertFalse(items.single { it.id == id }.isAdvanced)
        }
    }

    @Test
    fun `advanced video list keeps its 28-row order with the residuals in place`() {
        val items = PlaybackAdvancedVideoSearchItems
        assertEquals(28, items.size)
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
                PlaybackRows.SpatialAudio.id,
                PlaybackRows.SpatialAudioOnlyHeadphones.id,
                PlaybackRows.FrameRateMatching.id,
                PlaybackRows.OfflinePlayback.id,
                PlaybackRows.PlaybackMode.id,
                PlaybackRows.StreamingQuality.id,
                // The issue #186 pair: the spec-backed performance-profile
                // ceiling beside Streaming Quality, then its residual
                // Re-detect action row.
                PlaybackRows.VideoPerformanceProfile.id,
                PlaybackRows.RedetectPerformance.id,
                PlaybackRows.HdrFormatPreference.id,
                PlaybackRows.ForceTranscodeAboveHeight.id,
                PlaybackRows.PreferredTranscodeVideoCodec.id,
                PlaybackRows.PreferredTranscodeAudioCodec.id,
                PlaybackRows.CustomDeviceProfileJson.id,
                PlaybackRows.HomeNetworkQuality.id,
                PlaybackRows.SpeedTest.id,
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
        // The issue #186 binding: the profile row carries the spec-declared
        // ss_* faces; the residual Re-detect row carries its hand faces.
        val profile = items.single { it.id == PlaybackRows.VideoPerformanceProfile.id }
        assertEquals("ss_performance_profile_title", profile.titleRes.key)
        assertEquals("ss_performance_profile_subtitle", profile.subtitleRes.key)
        assertEquals(listOf("performance", "low end", "medium", "high end", "smooth", "stutter", "profile", "auto", "1080p", "bitrate", "device"), profile.keywords)
        val redetect = items.single { it.id == PlaybackRows.RedetectPerformance.id }
        assertEquals("settings_redetect_performance", redetect.titleRes.key)
        assertEquals("settings_redetect_performance_subtitle", redetect.subtitleRes.key)
    }

    @Test
    fun `external engine row derives from the playback spec`() {
        val items = ExternalEngineSearchItems
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(PlaybackRows.ExternalPlayerApp.id, item.id)
        // One-key family: the row's search title folds to the screen title —
        // the spec's titleKey names the settings_ key.
        assertEquals("settings_external_player_app", item.titleRes.key)
        assertEquals(Route.PlayerEngineSettings(), item.route)
        assertFalse(item.isAdvanced)
        assertEquals(PlatformKind.entries.toSet(), item.platforms)
    }

    @Test
    fun `live tv list keeps its order - dvr residuals then the spec-backed segment rows`() {
        val items = LiveTvSearchItems
        assertEquals(12, items.size)
        // ADR-0017: the DVR residuals deep-link the Live TV & DVR leaf, the
        // segment rows the Skipping & segments leaf.
        assertEquals(Route.LiveTvDvrSettings(), items.first().route)
        assertEquals(Route.MediaSegmentSettings(), items.last().route)
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
                PlaybackRows.SkipPromptDuration.id,
                PlaybackRows.NextUpPreroll.id,
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
    fun `appearance theme list keeps its 21-row order with the two residuals in place`() {
        val items = AppearanceThemeSearchItems
        assertEquals(21, items.size)
        assertEquals(AppearanceRows.DateFormat.id, items.first().id)
        assertEquals(AppearanceRows.ScheduledEnd.id, items.last().id)
        assertEquals(
            listOf(AppearanceRows.LibraryViewMode.id, AppearanceRows.NavLabels.id),
            items.filter { it.id in setOf(AppearanceRows.LibraryViewMode.id, AppearanceRows.NavLabels.id) }.map { it.id },
            "the two residuals keep their catalog positions",
        )
        // ADR-0017: the whole group deep-links the Theme & display leaf
        // (both the spec-kind rows and the two hand-routed residuals).
        assertTrue(items.all { it.route == Route.ThemeSettings() }, "a theme row lost the leaf route")
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
        // The TV UI scale picker (the density multiplier over the whole TV
        // composition) rides right beside it under the same TV-only,
        // not-advanced, Android-tagged grammar.
        val tvUiScale = items.single { it.id == AppearanceRows.TvUiScale.id }
        assertFalse(tvUiScale.isAdvanced)
        assertEquals(setOf(PlatformKind.ANDROID), tvUiScale.platforms)
        assertEquals(
            listOf(AppearanceRows.LayoutMode.id, AppearanceRows.ScreenFit.id, AppearanceRows.TvUiScale.id),
            items.filter {
                it.id in setOf(AppearanceRows.LayoutMode.id, AppearanceRows.ScreenFit.id, AppearanceRows.TvUiScale.id)
            }.map { it.id },
            "the form-factor fork trio keeps their catalog positions",
        )
    }

    @Test
    fun `appearance library views list keeps its 22-row order and deep-links the leaf`() {
        // ADR-0017's split of the former "Library & Cards" group: the
        // browsing half (episode lists, badges, logos, trailers, the
        // haptics / share-media / hide-search-history spec-backed trio) is
        // the Library views leaf's declaration, in the former group's
        // relative order; the detail-screen rows moved to the Detail screen
        // leaf's list. The home-discovery watch-state quartet lives in
        // HomeCardsSearchItems.
        val items = AppearanceLibraryViewsSearchItems
        assertEquals(22, items.size)
        assertEquals(
            listOf(
                AppearanceRows.HideEpisodeThumbnails.id,
                AppearanceRows.QualityBadges.id,
                AppearanceRows.NewBadges.id,
                AppearanceRows.CompactEpisodeList.id,
                AppearanceRows.ShowEpisodeStreamBadges.id,
                AppearanceRows.SkipSpecials.id,
                AppearanceRows.ShowMissingEpisodes.id,
                AppearanceRows.PreferLogos.id,
                AppearanceRows.ShowTagline.id,
                AppearanceRows.PlayThemeMusic.id,
                AppearanceRows.HapticsEnabled.id,
                AppearanceRows.ShowShareMedia.id,
                AppearanceRows.ShowExternalLinks.id,
                AppearanceRows.ShowReviews.id,
                AppearanceRows.AwardsBadges.id,
                AppearanceRows.PreferLocalTrailers.id,
                AppearanceRows.TrailerPreviewOnFocus.id,
                AppearanceRows.ShowPromotedActionsLandscape.id,
                AppearanceRows.ShowDownloadProgress.id,
                AppearanceRows.ShowUpcomingStrip.id,
                AppearanceRows.ShowMoreInCollection.id,
                AppearanceRows.HideSearchHistory.id,
            ),
            items.map { it.id },
        )
        // Every row deep-links the Library views leaf — the spec-kind rows
        // via their re-pointed route kinds, the residuals via their hand
        // routes.
        assertTrue(items.all { it.route == Route.LibraryDisplaySettings() }, "a library-views row lost the leaf route")
    }

    @Test
    fun `appearance detail list keeps its 17-row visible order and deep-links the leaf`() {
        // ADR-0017's split of the former "Library & Cards" group, narrowed
        // by the phase-5 reclassification: the visible detail block — the
        // filmography pair, the display toggles, the section-order editor
        // and the play/menu picks. The density/limit tuning moved to the
        // advanced "Density & limits" list below.
        val items = AppearanceDetailSearchItems
        assertEquals(17, items.size)
        assertEquals(
            listOf(
                AppearanceRows.ShowFilmographyRoles.id,
                AppearanceRows.ShowFrequentCostars.id,
                AppearanceRows.BackdropParallax.id,
                AppearanceRows.OverviewCollapseLines.id,
                AppearanceRows.HideWatchedEpisodes.id,
                AppearanceRows.ConfirmMarkPlayed.id,
                AppearanceRows.ShowStudioChips.id,
                AppearanceRows.ShowGenreChips.id,
                AppearanceRows.ShowTagChips.id,
                AppearanceRows.ShowSkipChips.id,
                AppearanceRows.SeasonTabsArtwork.id,
                AppearanceRows.ShowTrailerBadges.id,
                AppearanceRows.PersistSectionHides.id,
                // The pass-2 detail-QoL picks that stay visible.
                AppearanceRows.SectionOrder.id,
                AppearanceRows.SmartPlayPriority.id,
                AppearanceRows.StreamFallbackLanguage.id,
                AppearanceRows.PromotedRailSource.id,
            ),
            items.map { it.id },
        )
        // Every visible row deep-links the Detail screen leaf and stays
        // unconditionally visible (the shipped always-on set).
        assertTrue(items.all { it.route == Route.DetailDisplaySettings() }, "a detail row lost the leaf route")
        assertTrue(items.none { it.isAdvanced }, "the visible detail set grew an advanced row")
    }

    @Test
    fun `appearance detail tuning list is wholly advanced and deep-links the leaf`() {
        // The phase-5 reclassification: the tuning-shaped detail rows moved
        // to the advanced "Density & limits" group — same leaf routes,
        // advanced faces (the search hits auto-arm the gate).
        val items = AppearanceDetailTuningSearchItems
        assertEquals(13, items.size)
        assertEquals(
            listOf(
                AppearanceRows.CastItemSize.id,
                AppearanceRows.CastSeeAllLimit.id,
                AppearanceRows.PeopleGridDensity.id,
                AppearanceRows.SimilarLimit.id,
                AppearanceRows.UpcomingLimit.id,
                AppearanceRows.StaggerStyle.id,
                AppearanceRows.BackdropResolution.id,
                AppearanceRows.ChapterCardSize.id,
                AppearanceRows.ReviewsPreviewLines.id,
                AppearanceRows.FilmographySort.id,
                AppearanceRows.FranchiseBoxLimit.id,
                AppearanceRows.FranchiseMemberLimit.id,
                AppearanceRows.EpisodeOverviewPreview.id,
            ),
            items.map { it.id },
        )
        assertTrue(items.all { it.route == Route.DetailDisplaySettings() }, "a tuning row lost the leaf route")
        assertTrue(items.all { it.isAdvanced }, "the tuning rows' search faces must carry the advanced flag")
    }

    @Test
    fun `appearance performance and eye care lists derive wholly from specs`() {
        // The master level row moved to the hub's level group; the Theme
        // leaf's Performance group keeps only the Reduce Motion toggle.
        assertEquals(1, AppearancePerformanceSearchItems.size)
        assertEquals(2, AppearanceEyeCareSearchItems.size)
        assertTrue(AppearancePerformanceSearchItems.all { it.isAdvanced })
        assertTrue(AppearanceEyeCareSearchItems.all { it.isAdvanced })
        // ADR-0017: both expert groups render on the Theme & display leaf.
        assertEquals(Route.ThemeSettings(), AppearancePerformanceSearchItems.single { it.id == AppearanceRows.ReduceMotion.id }.route)
        assertEquals(Route.ThemeSettings(), AppearanceEyeCareSearchItems.single { it.id == AppearanceRows.BlueLightStrength.id }.route)
    }

    @Test
    fun `the performance hub lists derive wholly from specs and deep-link the hub route`() {
        // Sizes, in group order: 1 master + 3 motion + 5 images + 3 ambient,
        // then the playback-domain buffer quartet.
        assertEquals(1, PerformanceLevelSearchItems.size)
        assertEquals(3, PerformanceMotionSearchItems.size)
        assertEquals(5, PerformanceImageSearchItems.size)
        assertEquals(3, PerformanceAmbientSearchItems.size)
        assertEquals(4, PlaybackBufferSearchItems.size)

        // Every hub row deep-links Route.PerformanceSettings (the
        // performance_settings route kind both domains' specs now carry) and
        // keeps its spec advanced base — except the master row, whose fused
        // Always gate is an ADMISSION override, not a search flag.
        listOf(
            PerformanceLevelSearchItems,
            PerformanceMotionSearchItems,
            PerformanceImageSearchItems,
            PerformanceAmbientSearchItems,
            PlaybackBufferSearchItems,
        ).forEach { items ->
            assertTrue(items.all { it.route == Route.PerformanceSettings() }, "a hub row lost the hub route")
            assertTrue(items.all { it.isAdvanced }, "a hub row lost its advanced flag")
        }
        // The master row keeps the historical search id and its ss_* faces.
        val master = PerformanceLevelSearchItems.single()
        assertEquals("performance_mode", master.id)
        assertEquals("ss_performance_mode_title", master.titleRes.key)
        assertEquals("ss_performance_mode_subtitle", master.subtitleRes.key)
        // Spot-check a tri-state binding and a buffer binding.
        val animations = PerformanceMotionSearchItems.single { it.id == "perf_animations" }
        assertEquals("ss_perf_animations_title", animations.titleRes.key)
        val initialBuffer = PlaybackBufferSearchItems.single { it.id == "exo_initial_buffer_ms" }
        assertEquals("ss_exo_initial_buffer_ms_title", initialBuffer.titleRes.key)
    }

    @Test
    fun `the appearance hub list is the four entrance rows in leaf order`() {
        val items = AppearanceHubSearchItems
        assertEquals(4, items.size)
        assertEquals(
            listOf(
                AppearanceRows.ThemeEntrance.id,
                AppearanceRows.LibraryViewsEntrance.id,
                AppearanceRows.DetailDisplayEntrance.id,
                AppearanceRows.NavigationEntrance.id,
            ),
            items.map { it.id },
        )
        // Every entrance deep-links the leaf it opens, carries hand search
        // faces (residual launcher rows) and stays always-visible.
        listOf(
            AppearanceRows.ThemeEntrance.id to Route.ThemeSettings(),
            AppearanceRows.LibraryViewsEntrance.id to Route.LibraryDisplaySettings(),
            AppearanceRows.DetailDisplayEntrance.id to Route.DetailDisplaySettings(),
            AppearanceRows.NavigationEntrance.id to Route.NavigationDisplaySettings(),
        ).forEach { (id, route) ->
            assertEquals(route, items.single { it.id == id }.route, "$id must deep-link its leaf")
            assertTrue(items.single { it.id == id }.keywords.isNotEmpty(), "$id carries no keywords")
            assertFalse(items.single { it.id == id }.isAdvanced)
        }
    }

    @Test
    fun `appearance navigation is a mixed projection and the newsletter list stays residual`() {
        // The newsletter group is all-residual: the fused rows still project
        // their hand search faces directly — and route the HUB (the declared
        // hub exception: the group renders on Route.AppearanceSettings below
        // the entrances). The navigation group is mixed: its six residuals
        // keep hand faces, and the pass-2 global-chrome seven (backdrop dim,
        // haptics intensity, image quality, list density, press effect, flat
        // cards, overscroll) project their spec faces — both deep-linking the
        // Navigation & haptics leaf.
        assertEquals(13, AppearanceNavigationSearchItems.size)
        assertEquals(3, AppearanceNewsletterSearchItems.size)
        assertTrue(AppearanceNavigationSearchItems.all { it.route == Route.NavigationDisplaySettings() }, "a navigation row lost the leaf route")
        assertTrue(AppearanceNewsletterSearchItems.all { it.route == Route.AppearanceSettings() }, "a newsletter row lost the hub route")
        assertTrue(AppearanceNewsletterSearchItems.all { it.isAdvanced })
    }

    // ── Home ────────────────────────────────────────────────────────────

    @Test
    fun `home lists keep their sizes and derive their routes from the spec kinds`() {
        // ADR-0017's home split. The hub: three entrance rows + the advanced
        // Fine-tuning group (the former display group's tuning subset, still
        // routed home_settings). The display-main leaf: the mainstream
        // remainder (28). The Rows & layout leaf: Next Up (6) + layout (5) +
        // the curation quartet (4). The Cards & badges leaf: the dozen
        // toggles + the card-chrome trio (15).
        assertEquals(3, HomeHubSearchItems.size)
        assertEquals(8, HomeTuningSearchItems.size)
        assertEquals(28, HomeDisplayMainSearchItems.size)
        assertEquals(HomeRows.HomeMode.id, HomeDisplayMainSearchItems.first().id)
        assertEquals(HomeRows.ContinueWatchingClick.id, HomeDisplayMainSearchItems.last().id)
        assertEquals(6, HomeNextUpSearchItems.size)
        assertEquals(5, HomeLayoutSearchItems.size)
        assertEquals(4, HomeCurationSearchItems.size)
        assertEquals(15, HomeCardsSearchItems.size)

        // The drill-in rows resolve their routes through the spec route kinds.
        assertIs<Route.PinnedHomeSections>(HomeLayoutSearchItems.single { it.id == HomeRows.PinnedHomeSections.id }.route)
        assertIs<Route.HomeLayoutPresets>(HomeLayoutSearchItems.single { it.id == HomeRows.HomeLayoutPresets.id }.route)
        assertIs<Route.LibraryHomeSections>(HomeLayoutSearchItems.single { it.id == HomeRows.ConfigureLibraries.id }.route)
        assertIs<Route.DiscoverRows>(HomeLayoutSearchItems.single { it.id == HomeRows.DiscoverRows.id }.route)
        assertIs<Route.NextUpExcluded>(HomeCurationSearchItems.single { it.id == HomeRows.NextUpHidden.id }.route)
        assertIs<Route.MyMediaHidden>(HomeCurationSearchItems.single { it.id == HomeRows.HiddenLibraries.id }.route)
        // The leaves deep-link through the re-pointed spec route kinds; the
        // tuning rows keep the hub kind.
        assertIs<Route.HomeSettings>(HomeTuningSearchItems.first().route)
        assertIs<Route.HomeDisplaySettings>(HomeDisplayMainSearchItems.first().route)
        assertIs<Route.HomeRowsSettings>(HomeNextUpSearchItems.first().route)
        assertIs<Route.HomeCardsSettings>(HomeCardsSearchItems.first().route)
        // …and the advanced tier: the Fine-tuning eight carry their advanced
        // faces (the phase-5 spec-flag flip — the search hits auto-arm the
        // gate), every other home row renders unconditionally.
        assertTrue(HomeHubSearchItems.none { it.isAdvanced })
        assertTrue(HomeTuningSearchItems.all { it.isAdvanced }, "the tuning rows' search faces must carry the advanced flag")
        assertTrue(HomeDisplayMainSearchItems.none { it.isAdvanced })
        assertTrue(HomeNextUpSearchItems.none { it.isAdvanced })
        assertTrue(HomeLayoutSearchItems.none { it.isAdvanced })
        assertTrue(HomeCurationSearchItems.none { it.isAdvanced })
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
        // The dream rows deep-link into the Ambient screen (ADR-0017: the TV
        // dream + desktop idle groups as one gated screen)…
        assertEquals(Route.AmbientSettings(), items.single { it.id == SystemRows.ScreensaverKenBurns.id }.route)
        assertEquals(Route.AmbientSettings(), items.single { it.id == SystemRows.IdleAmbientEnabled.id }.route)
        // …the desktop-integration rows deep-link into the Integrations
        // screen (ADR-0017 moved them off the root).
        assertEquals(Route.Integrations(), items.single { it.id == SystemRows.DiscordPresenceEnabled.id }.route)
        assertEquals(Route.Integrations(), items.single { it.id == SystemRows.HooksEnabled.id }.route)
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
        assertEquals(PlaybackBasicsSearchItems.map { it.id }, SettingsScreenGroups.playbackBasics.itemIds)
        assertEquals(PlaybackControlsSearchItems.map { it.id }, SettingsScreenGroups.playbackControls.itemIds)
        assertEquals(PlaybackPlayerChromeSearchItems.map { it.id }, SettingsScreenGroups.playbackPlayerChrome.itemIds)
        assertEquals(PlaybackAdvancedVideoSearchItems.map { it.id }, SettingsScreenGroups.playbackAdvancedVideo.itemIds)
        assertEquals(AppearanceThemeSearchItems.map { it.id }, SettingsScreenGroups.appearanceTheme.itemIds)
        assertEquals(HomeDisplayMainSearchItems.map { it.id }, SettingsScreenGroups.homeDisplayMain.itemIds)
        assertEquals(SystemSearchItems.map { it.id }, SystemSearchItems.map { it.id })
        // …and the catalog is still the flat concatenation of the groups
        // (the matcher tie-break order) — each group's derived card item
        // ([SettingsSearchItemGroup.cardItem]) riding right after its rows.
        assertEquals(
            SettingsScreenGroups.all.flatMap { it.items + listOfNotNull(it.cardItem) }.map { it.id },
            SettingsSearchCatalog.items.map { it.id },
        )
    }
}
