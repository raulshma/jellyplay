package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalog ↔ screen contract (the drift ratchet), rerolled for the
 * single-sourced row ids (candidate C2 "rows own their identity"): every
 * settings row id is declared ONCE, as a `const val` in its screen's `*Ids`
 * holder beside the `*SearchItems` declarations. The search-item
 * declarations, the screens' `highlighted` comparisons, the admissions keys
 * and the row-total derivations all reference those constants, so the
 * screen-row ↔ catalog pairing that used to need a 900-line source-tree
 * regex scanner is pinned by compilation instead: a row's comparison and the
 * catalog item literally share one declaration.
 *
 * What still needs a runtime test — and lives here — is the REFERENTIAL
 * integrity the compiler cannot see: every holder constant must resolve to
 * exactly one catalog item (a row that highlights nothing — the
 * `vlc_video_output` class of bug), and every catalog id must come from a
 * holder (a declaration that bypasses the single source, or a holder
 * constant no declaration uses — an orphan highlight target). On top of
 * that sit the behavioral pins: the fixed drifts' scroll behavior, the
 * aggregation-decoration splits, the declared row admissions and the
 * per-group totals. The four shipped drifts each would still fail loudly:
 *  - `vlc_video_output` (declared, in NO screen group) — caught by the
 *    holder↔catalog↔group integrity test;
 *  - `live_stream_option` / `dialogue_boost_strength` (row/group drifts) —
 *    caught by the integrity test + the scroll pins;
 *  - `auto_delete_after_watch` (group id + row, NO search item) — caught by
 *    the integrity test (holder id with no catalog item).
 *
 * Deliberate value pins elsewhere (`SettingsSearchCatalogTest`,
 * `SettingsSearchCatalogPlatformFilterTest`) keep the raw id literals: they
 * guard the persisted deep-link/recents strings — a change there is a
 * reviewed schema change at the holders, not a refactor.
 */
class SettingsCatalogScreenContractTest {

    // ── The ids holders (the single-source registry) ────────────────────

    /**
     * Every per-screen ids holder. Registering a new holder is one line —
     * and omitting it fails loudly: its ids are in the catalog but in no
     * registered holder, so the reverse coverage check below trips.
     */
    private val idsHolders: List<Any> = listOf(
        AboutScreenIds,
        AppearanceSettingsIds,
        AudioSettingsIds,
        BackupSettingsIds,
        ExperimentalSettingsIds,
        IntegrationsScreenIds,
        LanguageSettingsIds,
        NotificationSettingsIds,
        PlaybackSettingsIds,
        SecuritySettingsIds,
        SettingsScreenIds,
        StorageSettingsIds,
        TrackSelectionIds,
    )

    /** Every id a holder declares, via the holders' public const fields. */
    private fun holderIds(holder: Any): List<String> =
        holder::class.java.fields
            .filter { it.type == String::class.java }
            .map { field ->
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                    field.get(null) as String
                } else {
                    field.get(holder) as String
                }
            }

    private val allHolderIds: List<String> by lazy { idsHolders.flatMap { holderIds(it) } }

    // ── 1. Referential integrity: holders ↔ catalog ↔ groups ───────────

    @Test
    fun `every settings row id resolves to exactly one catalog item`() {
        assertTrue(allHolderIds.isNotEmpty(), "no ids holder declares any id — reflection scan is broken")
        val catalogIds = SettingsSearchCatalog.items.map { it.id }
        val missing = allHolderIds.filter { id -> catalogIds.count { it == id } != 1 }
        assertEquals(emptyList(), missing, "row ids that resolve to no (or several) catalog items")
    }

    @Test
    fun `every catalog id comes from an ids holder and lands in exactly one group`() {
        // A declaration built with a raw literal (bypassing the holders), or
        // a new holder not registered above, fails here.
        val undeclared = SettingsSearchCatalog.items.map { it.id }.filter { it !in allHolderIds }
        assertEquals(emptyList(), undeclared, "catalog ids declared outside the ids holders")

        // …and each lands in exactly one screen group (a declaration that
        // lands in no group cannot reach the catalog, but a group decoration
        // referencing it twice would double-count the screen's rows).
        val groupCounts = SettingsScreenGroups.all.flatMap { it.itemIds }.groupBy { it }
        val multi = groupCounts.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), multi, "ids decorated into more than one screen group")
        assertEquals(
            SettingsScreenGroups.all.map { it.id }.toSet().size,
            SettingsScreenGroups.all.size,
            "duplicate group names",
        )
        assertEquals(
            SettingsScreenGroups.all.flatMap { it.items }.map { it.id },
            SettingsSearchCatalog.items.map { it.id },
            "catalog is not the flat concatenation of the decorated groups",
        )
    }

    // ── 2. The declared row admissions (one gate for count AND emission) ──

    @Test
    fun `every declared admission key is a declared group id`() {
        SettingsScreenGroups.all.forEach { group ->
            assertEquals(
                emptySet(),
                group.admissions.keys - group.itemIdSet,
                "${group.id} declares admissions for undeclared ids",
            )
        }
        // The audio gates REPLACE the isAdvanced base in the count, so every
        // audio key must sit on an isAdvanced row (a non-advanced key would
        // silently widen admission).
        assertEquals(
            emptyList(),
            AudioRowAdmissions.keys.filterNot { id -> SettingsScreenGroups.audio.items.first { it.id == id }.isAdvanced },
            "audio admissions must sit on isAdvanced rows (the gate replaces the advanced base)",
        )
    }

    @Test
    fun `platform-gated rows drop on the desktop seam and admit on the capability`() {
        // jvmTest runs the desktop actual — no screen orientation, no touch
        // gestures — and the declared Platform admissions must agree with the
        // desktop-filtered catalog drops (pinned in
        // SettingsSearchCatalogPlatformFilterTest).
        assertFalse(settingsCapabilities.supportsScreenOrientation)
        assertFalse(settingsCapabilities.supportsTouchGestures)
        val desktop = RowAdmissionFlags()
        listOf(
            PlaybackSettingsIds.ORIENTATION,
            PlaybackSettingsIds.SEEK_DURATION,
            PlaybackSettingsIds.GESTURES,
            PlaybackSettingsIds.GESTURE_INDICATOR_SIDE,
        ).forEach { id ->
            assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(id, desktop), "$id must drop without its capability")
        }
        val touch = RowAdmissionFlags(supportsTouchGestures = true)
        listOf(
            PlaybackSettingsIds.SEEK_DURATION,
            PlaybackSettingsIds.GESTURES,
            PlaybackSettingsIds.GESTURE_INDICATOR_SIDE,
        ).forEach {
            assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(it, touch), "$it must admit on the capability")
        }
        assertTrue(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackSettingsIds.ORIENTATION,
                RowAdmissionFlags(supportsScreenOrientation = true),
            ),
        )
        // the volume-memory toggle is DESKTOP-backed — it admits on
        // this JVM's default seam flags (the desktop binary backs the row)
        // and drops where the capability is off (Android).
        assertTrue(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE,
                desktop,
            ),
            "the volume-memory row admits on the desktop seam",
        )
        assertFalse(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackSettingsIds.REMEMBER_VOLUME_PER_CONTENT_TYPE,
                RowAdmissionFlags(supportsVolumeMemory = false),
            ),
            "the volume-memory row must drop without its capability",
        )
    }

    @Test
    fun `tv rows admit on the tv form factor alone - the shipped count semantics`() {
        // The two TV rows are declared isAdvanced, yet the total has always
        // admitted them on isTv alone (their emission sits inside the advanced
        // block, which carries the advanced half of the gate).
        val tv = RowAdmissionFlags(isTv = true)
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT, tv))
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackSettingsIds.TV_ZOOM_MODE, tv))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackSettingsIds.ANDROID_TV_WATCH_NEXT, RowAdmissionFlags()))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackSettingsIds.TV_ZOOM_MODE, RowAdmissionFlags(showAdvanced = true)))
    }

    @Test
    fun `when-on rows admit on their declared parent toggle`() {
        // The playback advanced-video strength row and the storage schedule
        // windows ride their parent toggle alone…
        val boostOn = RowAdmissionFlags(parentsOn = setOf(PlaybackSettingsIds.DIALOGUE_BOOST))
        assertTrue(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH, boostOn))
        assertFalse(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH, RowAdmissionFlags()))
        val scheduleOn = RowAdmissionFlags(parentsOn = setOf(StorageSettingsIds.DOWNLOAD_SCHEDULE))
        listOf(
            StorageSettingsIds.DOWNLOAD_SCHEDULE_START,
            StorageSettingsIds.DOWNLOAD_SCHEDULE_END,
            StorageSettingsIds.DOWNLOAD_SCHEDULE_WIFI_ONLY,
        ).forEach {
            assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(it, scheduleOn), "$it admits on the schedule toggle")
            assertFalse(SettingsScreenGroups.storageDownloads.rowAdmitted(it, RowAdmissionFlags()), "$it drops when scheduling is off")
        }
        assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(StorageSettingsIds.DOWNLOAD_SCHEDULE, RowAdmissionFlags()))

        // …while the audio strength rows additionally ride the advanced
        // toggle (All(Advanced, WhenOn(parent)) — the advanced half is the
        // structural block the screen's advanced section carries).
        val parentsOnly = RowAdmissionFlags(parentsOn = setOf(AudioSettingsIds.NIGHT_MODE, AudioSettingsIds.EQUALIZER))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted(AudioSettingsIds.NIGHT_MODE_STRENGTH, parentsOnly))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted(AudioSettingsIds.EQUALIZER_PRESET, parentsOnly))
        val advanced = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(AudioSettingsIds.NIGHT_MODE, AudioSettingsIds.EQUALIZER))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted(AudioSettingsIds.NIGHT_MODE_STRENGTH, advanced))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted(AudioSettingsIds.EQUALIZER_PRESET, advanced))
    }

    @Test
    fun `notification rows carry the declared gates the strict count reads`() {
        val admissions = SettingsScreenGroups.notifications.admissions
        // The master toggle states its unconditional gate explicitly…
        assertIs<RowAdmission.Always>(admissions[NotificationSettingsIds.NOTIFICATIONS_ENABLE])
        // …the four toggle rows ride it…
        listOf(
            NotificationSettingsIds.NOTIFICATION_CHECK_FREQUENCY,
            NotificationSettingsIds.NOTIFICATION_SOUND,
            NotificationSettingsIds.NOTIFICATION_VIBRATE,
            NotificationSettingsIds.NOTIFICATION_LIGHTS,
        ).forEach { id ->
            val gate = assertIs<RowAdmission.WhenOn>(admissions[id])
            assertEquals(NotificationSettingsIds.NOTIFICATIONS_ENABLE, gate.parentId)
        }
        // …the quiet-hours pair rides toggle + advanced + quiet hours…
        val quietGate = admissions[NotificationSettingsIds.QUIET_START]!!
        val quietFlagsOff = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(NotificationSettingsIds.NOTIFICATIONS_ENABLE))
        val quietFlagsOn = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = setOf(NotificationSettingsIds.NOTIFICATIONS_ENABLE, NotificationSettingsIds.QUIET_HOURS),
        )
        assertFalse(quietGate.admitted(quietFlagsOff))
        assertTrue(quietGate.admitted(quietFlagsOn))
        // …and the system-settings row rides the platform-intent capability.
        val systemFlagsOff = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = setOf(NotificationSettingsIds.NOTIFICATIONS_ENABLE),
            supportsSystemNotificationSettings = false,
        )
        val systemFlagsOn = systemFlagsOff.copy(supportsSystemNotificationSettings = true)
        assertFalse(SettingsScreenGroups.notifications.rowAdmitted(NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS, systemFlagsOff))
        assertTrue(SettingsScreenGroups.notifications.rowAdmitted(NotificationSettingsIds.SYSTEM_NOTIFICATION_SETTINGS, systemFlagsOn))
        // The count is strict: only declared ids admit.
        assertEquals(SettingsScreenGroups.notifications.itemIds.toSet(), admissions.keys.toSet())
    }

    @Test
    fun `language subtitles rows are fully declared`() {
        val admissions = SettingsScreenGroups.languageSubtitles.admissions
        // Declared advanced yet shown in every mode — the shipped quirk, verbatim.
        assertIs<RowAdmission.Always>(admissions[LanguageSettingsIds.HIGH_CONTRAST_SUBTITLES])
        // The HDR font-size row additionally rides the HDR-style toggle.
        val hdrGate = assertIs<RowAdmission.All>(admissions[LanguageSettingsIds.HDR_SUBTITLE_FONT_SIZE])
        val base = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(LanguageSettingsIds.HDR_SUBTITLE_STYLE))
        assertTrue(hdrGate.admitted(base))
        assertFalse(hdrGate.admitted(base.copy(parentsOn = emptySet())))
        // Every id declares its gate — the count is strict like notifications.
        assertEquals(SettingsScreenGroups.languageSubtitles.itemIds.toSet(), admissions.keys.toSet())
        // The trio plus high-contrast always show; the style rows ride Advanced.
        for (id in listOf(
            LanguageSettingsIds.SUBTITLE_FONT_SIZE,
            LanguageSettingsIds.SUBTITLE_FORCED_ONLY,
            LanguageSettingsIds.SUBTITLE_TESTER,
        )) {
            assertIs<RowAdmission.Always>(admissions[id])
        }
        for (id in listOf(
            LanguageSettingsIds.PGS_DIRECT_PLAY,
            LanguageSettingsIds.HDR_SUBTITLE_STYLE,
            LanguageSettingsIds.SUBTITLE_COLOR,
            LanguageSettingsIds.SUBTITLE_BACKGROUND,
            LanguageSettingsIds.SUBTITLE_EDGE_STYLE,
            LanguageSettingsIds.SUBTITLE_SYNC_OFFSET,
            LanguageSettingsIds.SUBTITLE_VERTICAL_POSITION,
        )) {
            assertIs<RowAdmission.Advanced>(admissions[id])
        }
    }

    @Test
    fun `security rows keep the shipped count gates and the undeclared quirk`() {
        val admissions = SettingsScreenGroups.security.admissions
        assertIs<RowAdmission.Always>(admissions[SecuritySettingsIds.PIN_LOCK])
        val biometricGateDecl = assertIs<RowAdmission.Platform>(admissions[SecuritySettingsIds.BIOMETRIC_LOCK])
        assertEquals(RowAdmissionCapability.Biometric, biometricGateDecl.capability)
        assertIs<RowAdmission.Advanced>(admissions[SecuritySettingsIds.AUTO_LOCK_TIMER])
        // pin_for_player_lock renders behind the pin toggle but has never been
        // admitted into the count — its deliberately-missing declaration.
        assertNull(admissions[SecuritySettingsIds.PIN_FOR_PLAYER_LOCK])
        assertNull(admissions[SecuritySettingsIds.QUICK_CONNECT_AUTHORIZE])
        assertNull(admissions[SecuritySettingsIds.REMOTE_CONTROL_ENABLED])
        // The biometric flag is the screen's gate-aware computed value.
        assertTrue(SettingsScreenGroups.security.rowAdmitted(SecuritySettingsIds.BIOMETRIC_LOCK, RowAdmissionFlags(supportsBiometric = true)))
        assertFalse(SettingsScreenGroups.security.rowAdmitted(SecuritySettingsIds.BIOMETRIC_LOCK, RowAdmissionFlags()))
    }

    // ── 3. The fixed drifts, pinned behaviorally ────────────────────────

    /** The playback screen groups in LazyColumn order, from the declaration. */
    private val playbackGroups = listOf(
        SettingsScreenGroups.playbackPlayer.itemIdSet,
        SettingsScreenGroups.playbackAdvancedVideo.itemIdSet,
        SettingsScreenGroups.playbackEngine.itemIdSet,
        SettingsScreenGroups.playbackMediaSegments.itemIdSet,
        SettingsScreenGroups.playbackSyncPlay.itemIdSet,
        SettingsScreenGroups.playbackCasting.itemIdSet,
        SettingsScreenGroups.playbackDvr.itemIdSet,
    )

    @Test
    fun `vlc_video_output deep-links into the engine group`() {
        assertTrue(PlaybackSettingsIds.VLC_VIDEO_OUTPUT in SettingsScreenGroups.playbackEngine.itemIdSet)
        assertEquals(
            2,
            resolveHighlightScrollIndex(PlaybackSettingsIds.VLC_VIDEO_OUTPUT, playbackGroups, playbackAdjustForAdvanced(true)),
        )
        // With advanced hidden the engine group doesn't compose — no scroll.
        assertEquals(
            -1,
            resolveHighlightScrollIndex(PlaybackSettingsIds.VLC_VIDEO_OUTPUT, playbackGroups, playbackAdjustForAdvanced(false)),
        )
    }

    @Test
    fun `live_stream_option and dialogue_boost_strength land in the advanced-video group`() {
        val advanced = SettingsScreenGroups.playbackAdvancedVideo.itemIdSet
        assertTrue(PlaybackSettingsIds.LIVE_STREAM_OPTION in advanced)
        assertTrue(PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH in advanced)
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackSettingsIds.LIVE_STREAM_OPTION, playbackGroups, playbackAdjustForAdvanced(true)),
        )
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackSettingsIds.DIALOGUE_BOOST_STRENGTH, playbackGroups, playbackAdjustForAdvanced(true)),
        )
    }

    @Test
    fun `auto_delete_after_watch is declared in the downloads group with a screen row`() {
        assertTrue(StorageSettingsIds.AUTO_DELETE_AFTER_WATCH in SettingsScreenGroups.storageDownloads.itemIdSet)
        // The row itself shares the same constant (compile-paired) — the
        // source-scan that used to assert the literal is retired.
        assertTrue(allHolderIds.contains(StorageSettingsIds.AUTO_DELETE_AFTER_WATCH))
    }

    @Test
    fun `gesture_indicator_side scrolls within the player group`() {
        // Not one of the four shipped drifts, but the hand-typed player scroll
        // list had omitted it all the same; the derivation keeps it honest.
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackSettingsIds.GESTURE_INDICATOR_SIDE, playbackGroups, playbackAdjustForAdvanced(true)),
        )
    }

    @Test
    fun `always-visible playback groups shift correctly when advanced is hidden`() {
        // SyncPlay/casting/DVR always render; with advanced off they sit at
        // LazyColumn indices 1/2/3 behind the player group.
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackSettingsIds.SYNCPLAY_JOIN_BEHAVIOR, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        assertEquals(
            2,
            resolveHighlightScrollIndex(PlaybackSettingsIds.BACKGROUND_CASTING, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        assertEquals(
            3,
            resolveHighlightScrollIndex(PlaybackSettingsIds.DVR_RECORDING_QUALITY, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        // The player group never moves.
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackSettingsIds.PLAYER_ENGINE, playbackGroups, playbackAdjustForAdvanced(false)),
        )
    }

    // ── 4. The documented media-segment exception ───────────────────────

    @Test
    fun `media segment ids stay in LiveTvSearchItems and track the enum naming`() {
        // The screen's media-segment group stays hand-built, enumerated from
        // MediaSegmentType.entries (the accepted exception to the derivation).
        // The declaration side keeps the ids inside LiveTvSearchItems and must
        // keep following the media_segment_<enum-name> convention so the
        // derived scroll set and the hand-built rows stay in lock-step. The
        // group's skip-on-seek toggle is the one non-enum member — a
        // hand-built row like the per-type rows.
        val enumSegmentIds = MediaSegmentType.entries.map { "media_segment_${it.name.lowercase()}" }
        assertEquals(
            enumSegmentIds + PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK,
            SettingsScreenGroups.playbackMediaSegments.itemIds,
            "media-segment group declarations drifted from MediaSegmentType.entries + the skip-on-seek toggle",
        )
        assertTrue(
            SettingsScreenGroups.playbackDvr.itemIds.none {
                it.startsWith(SettingsScreenGroups.MEDIA_SEGMENT_ID_PREFIX) || it == PlaybackSettingsIds.SKIP_SEGMENTS_ON_SEEK
            },
            "DVR group must not absorb media-segment ids",
        )
    }

    // ── 5. The aggregation-decoration splits, pinned at their split lines ──

    @Test
    fun `language general group is exactly the declared leading trio`() {
        // The split has no id shape to key on (the Live TV prefix precedent
        // does not apply), so the split line is positional — pin the trio by
        // name so a declaration inserted above the subtitles half lands loudly
        // here instead of silently re-shaping the screen groups.
        assertEquals(
            listOf(LanguageSettingsIds.APP_LANGUAGE, LanguageSettingsIds.AUDIO_LANGUAGE, LanguageSettingsIds.SUBTITLE_LANGUAGE),
            SettingsScreenGroups.languageGeneral.itemIds,
            "language.general split line drifted",
        )
        assertEquals(
            LanguageSettingsSearchItems.size - 3,
            SettingsScreenGroups.languageSubtitles.itemIds.size,
            "language.subtitles must absorb every non-trio declaration",
        )
    }

    @Test
    fun `system screensaver split keeps the navigation pair in the core group`() {
        assertEquals(
            listOf(SettingsScreenIds.ADMIN_DASHBOARD, SettingsScreenIds.SETUP_WIZARD),
            SettingsScreenGroups.systemCore.itemIds,
        )
        assertTrue(
            SettingsScreenGroups.systemScreensaver.itemIds.all { it.startsWith(SettingsScreenGroups.SCREENSAVER_ID_PREFIX) },
            "system.screensaver must hold only screensaver ids",
        )
        // The idle-ambient (desktop) rows are split into their own group —
        // the three groups together still partition SystemSearchItems exactly.
        assertEquals(SystemSearchItems.size, SettingsScreenGroups.systemCore.items.size + SettingsScreenGroups.systemScreensaver.items.size + SettingsScreenGroups.systemIdleAmbient.items.size)
        assertTrue(
            SettingsScreenGroups.systemIdleAmbient.itemIds.all { it.startsWith(SettingsScreenGroups.IDLE_AMBIENT_ID_PREFIX) },
            "system.idleAmbient must hold only idle-ambient ids",
        )
    }

    @Test
    fun `language deep-links scroll via the derived groups`() {
        val languageGroups = listOf(
            SettingsScreenGroups.languageGeneral.itemIdSet,
            SettingsScreenGroups.languageSubtitles.itemIdSet,
        )
        assertEquals(0, resolveHighlightScrollIndex(LanguageSettingsIds.SUBTITLE_LANGUAGE, languageGroups))
        assertEquals(1, resolveHighlightScrollIndex(LanguageSettingsIds.SUBTITLE_TESTER, languageGroups))
        assertTrue(LanguageSettingsIds.SUBTITLE_TESTER in SettingsScreenGroups.languageSubtitles.itemIdSet)
        assertTrue(LanguageSettingsIds.HIGH_CONTRAST_SUBTITLES in SettingsScreenGroups.languageSubtitles.itemIdSet)
    }

    // ── 6. The per-group row-admission totals ───────────────────────────

    /**
     * The index/total pair a rendered group emits: inside `SettingsItemList`
     * the rows take the auto-index 0..total-1 and every row's `count` is the
     * admission total, so each pinned total below IS the total the screen
     * feeds and the sequence below IS the index sequence the rows display.
     */
    private fun emittedIndexSequence(total: Int): List<Int> = (0 until total).toList()

    @Test
    fun `audio row total tracks the declaration and its conditionals`() {
        // The retired arithmetic oracle: 5 base rows; +7 advanced always-on;
        // +1 replaygain pre-amp only under TRACK/ALBUM normalization; +2 for
        // preset + dialogue toggle under equalizerEnabled; +1 dialogue
        // strength under dialogueBoostEnabled; one toggle each for night
        // mode/bass/virtualizer/volume boost with one gated strength row
        // behind each; +5 for reverb/auto-eq/channel-mix/balance/pitch; +1
        // channel-mix mode under channelMixEnabled. The dialogue pair
        // derives from the playback advanced-video declaration (both screens
        // render it).
        assertEquals(5, audioScreenRowTotal(showAdvanced = false, preferences = AudioPreferences()))
        assertEquals(22, audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences()))
        assertEquals(
            31,
            audioScreenRowTotal(
                showAdvanced = true,
                preferences = AudioPreferences(
                    audioNormalizationMode = AudioNormalizationMode.TRACK,
                    equalizerEnabled = true,
                    dialogueBoostEnabled = true,
                    nightModeEnabled = true,
                    bassBoostEnabled = true,
                    virtualizerEnabled = true,
                    volumeBoostEnabled = true,
                    channelMixEnabled = true,
                ),
            ),
        )
        // Each conditional moves the total by exactly its own row — except
        // the equalizer toggle, which also reveals the dialogue-boost row
        // (the strength row the dialogue-boost toggle itself gates).
        assertEquals(
            23,
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(audioNormalizationMode = AudioNormalizationMode.ALBUM)),
        )
        assertEquals(
            24,
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true)),
        )
        assertEquals(
            25,
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true, dialogueBoostEnabled = true)),
        )
    }

    @Test
    fun `audio row total counts the five non-advanced declarations unconditionally`() {
        val nonAdvanced = SettingsScreenGroups.audio.items.count { !it.isAdvanced }
        assertEquals(5, nonAdvanced, "the audio declaration's non-advanced set changed — update the totals pin")
        assertEquals(
            nonAdvanced,
            audioScreenRowTotal(showAdvanced = false, preferences = AudioPreferences()),
        )
    }

    @Test
    fun `storage row totals track the cache and downloads declarations`() {
        // Cache: the screen-local cache-used info row + the declared
        // non-advanced rows (clear_cache, clear_image_cache); the five
        // advanced declarations only behind the advanced toggle.
        assertEquals(
            SettingsScreenGroups.storageCache.items.count { !it.isAdvanced } + 1,
            storageCacheScreenRowTotal(showAdvanced = false),
        )
        assertEquals(
            SettingsScreenGroups.storageCache.items.size + 1,
            storageCacheScreenRowTotal(showAdvanced = true),
        )
        assertEquals(3, storageCacheScreenRowTotal(showAdvanced = false))
        assertEquals(8, storageCacheScreenRowTotal(showAdvanced = true))
        assertEquals(emittedIndexSequence(3), List(3) { it })

        // Network: unconditionally the whole declaration (the screen feeds
        // `SettingsScreenGroups.storageNetwork.items.size` directly — no
        // conditional, so no admission function).
        assertEquals(11, SettingsScreenGroups.storageNetwork.items.size, "the storage.network declaration changed — update this pin")

        // Downloads: the three download_schedule_* window rows only when
        // scheduling is on; the toggle itself always renders.
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 3,
            storageDownloadsScreenRowTotal(downloadScheduleEnabled = false),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size,
            storageDownloadsScreenRowTotal(downloadScheduleEnabled = true),
        )
        assertEquals(7, storageDownloadsScreenRowTotal(downloadScheduleEnabled = false))
        assertEquals(10, storageDownloadsScreenRowTotal(downloadScheduleEnabled = true))
    }

    @Test
    fun `playback row totals track the player advanced-video and engine declarations`() {
        // Player: the six unconditional rows (player_engine, default_speed,
        // default_aspect, video_autoplay_next, autoplay_countdown,
        // hold_speed_multiplier) survive every gate off; each flag reveals
        // exactly its own rows — plus the volume-memory toggle, whose
        // Platform(VolumeMemory) admission rides this JVM's desktop seam (on).
        val allGatesOff = playbackPlayerScreenRowTotal(
            isTv = false,
            showAdvanced = false,
            supportsScreenOrientation = false,
            supportsTouchGestures = false,
        )
        assertEquals(7, allGatesOff)
        assertEquals(8, playbackPlayerScreenRowTotal(false, false, supportsScreenOrientation = true, supportsTouchGestures = false))
        assertEquals(10, playbackPlayerScreenRowTotal(false, false, supportsScreenOrientation = false, supportsTouchGestures = true))
        // Every non-advanced declaration renders with caps on (isTv off: the
        // two TV rows ride isTv alone — shipped semantics, also without
        // advanced mode).
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.count { !it.isAdvanced },
            playbackPlayerScreenRowTotal(false, false, supportsScreenOrientation = true, supportsTouchGestures = true),
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.size - 2, // minus the two TV rows
            playbackPlayerScreenRowTotal(false, true, supportsScreenOrientation = true, supportsTouchGestures = true),
        )
        assertEquals(34, playbackPlayerScreenRowTotal(true, true, supportsScreenOrientation = true, supportsTouchGestures = true))

        // Advanced video: the dialogue-boost strength row rides its toggle.
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size - 1,
            playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled = false),
        )
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size,
            playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled = true),
        )

        // Engine branches: declared prefix rows (the mpv branch minus the
        // desktop-gated audio-device trio and the render rows
        // where the capabilities are off) + the one reset row each. Desktop
        // JVM tests run with the capabilities ON (the desktop binary backs
        // the rows), so the default-flag call sees all of them.
        assertEquals(20, playbackEngineScreenRowTotal("mpv_"))
        assertEquals(9, playbackEngineScreenRowTotal("vlc_"))
        assertEquals(8, playbackEngineScreenRowTotal("exo_"))
        assertEquals(
            12,
            playbackEngineScreenRowTotal(
                "mpv_",
                supportsAudioDeviceSelection = false,
                supportsMpvRenderProfiles = false,
            ),
            "capabilities off: the mpv branch falls back to the base row set",
        )
        for (prefix in listOf("mpv_", "vlc_", "exo_")) {
            assertEquals(
                SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith(prefix) } + 1,
                playbackEngineScreenRowTotal(
                    prefix,
                    supportsAudioDeviceSelection = true,
                    supportsMpvRenderProfiles = true,
                ),
                "the $prefix branch's declared-row set changed — update this pin",
            )
        }

        // Always-visible trailing groups: the sequence/invariant shape.
        assertEquals(emittedIndexSequence(playbackEngineScreenRowTotal("vlc_")), (0 until 9).toList())
    }

    @Test
    fun `notification row total tracks the declaration through every gate`() {
        val allOn = notificationScreenRowTotal(
            enabled = true,
            showAdvanced = true,
            quietHoursEnabled = true,
            canOpenSystemNotificationSettings = true,
        )
        assertEquals(SettingsScreenGroups.notifications.items.size, allOn, "a declared notification id is admitted by no gate")
        assertEquals(1, notificationScreenRowTotal(false, true, true, true)) // master toggle off: only itself
        assertEquals(5, notificationScreenRowTotal(true, false, true, true)) // + frequency/sound/vibrate/lights
        assertEquals(9, notificationScreenRowTotal(true, true, false, false)) // + quiet hours/dnd/max-per-check/libraries
        assertEquals(11, notificationScreenRowTotal(true, true, true, false)) // + quiet start/end
        assertEquals(12, allOn) // + the platform's system-notification-settings row
    }

    @Test
    fun `language row totals track the trio and the subtitles conditionals`() {
        // The leading trio: the app-language row only where the locale
        // override applies.
        assertEquals(2, languageGeneralScreenRowTotal(showsAppLocaleRow = false))
        assertEquals(
            SettingsScreenGroups.languageGeneral.items.size,
            languageGeneralScreenRowTotal(showsAppLocaleRow = true),
        )

        // Subtitles: 4 always (tester/font-size/forced-only + the
        // always-shown high-contrast row — declared advanced yet never
        // gated); the style rows behind advanced; the HDR font-size row
        // behind the HDR toggle.
        assertEquals(4, languageSubtitlesScreenRowTotal(showAdvanced = false, hdrSubtitleStyleEnabled = true))
        assertEquals(11, languageSubtitlesScreenRowTotal(showAdvanced = true, hdrSubtitleStyleEnabled = false))
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.size,
            languageSubtitlesScreenRowTotal(showAdvanced = true, hdrSubtitleStyleEnabled = true),
        )
    }

    @Test
    fun `appearance library row total is the declaration plus the reset action row`() {
        assertEquals(
            SettingsScreenGroups.appearanceLibrary.items.size + 1,
            appearanceLibraryScreenRowTotal(),
        )
        assertEquals(11, appearanceLibraryScreenRowTotal())
    }

    @Test
    fun `security row total keeps the shipped count gates`() {
        // The pin row always; biometric rides the platform+gate flag; the
        // auto-lock timer rides advanced. pin_for_player_lock renders behind
        // the pin toggle but has never been admitted into the count — the
        // preserved quirk this pins.
        assertEquals(1, securityScreenRowTotal(canShowBiometric = false, showAdvanced = false))
        assertEquals(2, securityScreenRowTotal(canShowBiometric = true, showAdvanced = false))
        assertEquals(2, securityScreenRowTotal(canShowBiometric = false, showAdvanced = true))
        assertEquals(3, securityScreenRowTotal(canShowBiometric = true, showAdvanced = true))
    }

    // ── 7. The search-result click action ───────────────────────────────

    @Test
    fun `search-result clicks decide the pure action per branch`() {
        // Both destructive dialogs, with the destructive ids kept out of recents.
        val logout = settingsResultClickAction(SettingsScreenIds.LOGOUT, Route.Settings, isAdvanced = false, showAdvancedSettings = false)
        assertTrue(logout.action is SettingsSearchResultAction.OpenSignOutDialog && !logout.action.fromServer)
        assertFalse(logout.recordRecent)

        val signOutFromServer = settingsResultClickAction(
            SettingsScreenIds.SIGN_OUT_FROM_SERVER, Route.Settings, isAdvanced = false, showAdvancedSettings = true,
        )
        assertTrue(signOutFromServer.action is SettingsSearchResultAction.OpenSignOutDialog && signOutFromServer.action.fromServer)
        assertFalse(signOutFromServer.recordRecent)

        // Bare-settings screensaver targets reveal their on-screen group: no
        // navigation, only the pending highlight.
        val screensaver = settingsResultClickAction(
            SettingsScreenIds.SCREENSAVER_KEN_BURNS, Route.Settings, isAdvanced = false, showAdvancedSettings = true,
        )
        assertTrue(screensaver.action is SettingsSearchResultAction.NoOp)
        assertEquals(SettingsScreenIds.SCREENSAVER_KEN_BURNS, screensaver.pendingHighlightId)
        assertTrue(screensaver.recordRecent)

        // The setup wizard keeps its host indirection.
        val wizard = settingsResultClickAction(SettingsScreenIds.SETUP_WIZARD, Route.Onboarding, isAdvanced = false, showAdvancedSettings = true)
        assertTrue(wizard.action is SettingsSearchResultAction.OpenSetupWizard)
        assertEquals(SettingsScreenIds.SETUP_WIZARD, wizard.pendingHighlightId)

        // Regular sub-screen entries navigate with the id baked in and mark
        // the pending highlight — except the two management exemptions.
        val theme = settingsResultClickAction(AppearanceSettingsIds.THEME_MODE, Route.AppearanceSettings(), isAdvanced = false, showAdvancedSettings = true)
        val themeAction = theme.action as SettingsSearchResultAction.NavigateToScreen
        assertEquals(Route.AppearanceSettings(AppearanceSettingsIds.THEME_MODE), themeAction.route)
        assertEquals(AppearanceSettingsIds.THEME_MODE, theme.pendingHighlightId)

        val server = settingsResultClickAction(SettingsScreenIds.SERVER_MANAGEMENT, Route.ServerManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(server.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(server.pendingHighlightId, "server_management must not mark the TV re-entry highlight")

        val users = settingsResultClickAction(SettingsScreenIds.USER_MANAGEMENT, Route.UserManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(users.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(users.pendingHighlightId, "user_management must not mark the TV re-entry highlight")
    }

    @Test
    fun `advanced result clicks auto-enable advanced settings`() {
        val off = settingsResultClickAction(AudioSettingsIds.EQUALIZER, Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = false)
        assertTrue(off.enableAdvanced)
        assertTrue(off.action is SettingsSearchResultAction.NavigateToScreen)

        val on = settingsResultClickAction(AudioSettingsIds.EQUALIZER, Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = true)
        assertFalse(on.enableAdvanced)
    }
}
