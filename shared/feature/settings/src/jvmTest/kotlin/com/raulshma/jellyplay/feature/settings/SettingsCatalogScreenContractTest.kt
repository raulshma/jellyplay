package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalog ↔ screen contract (the drift ratchet), rerolled for the
 * single-sourced row ids ("rows own their identity"): every
 * settings row id is declared ONCE — as the `val id` of its fused
 * [SettingsRow] beside the `*Rows` declarations (every domain but the
 * experimental screen), or (the one hold-out) as a `const val` in the
 * experimental screen's ids holder beside its binding-derived
 * `ExperimentalSettingsSearchItems`. The search-item declarations, the
 * screens' `highlighted` comparisons, the gates and the row-total
 * derivations all reference those single sources, so the screen-row ↔
 * catalog pairing that used to need a 900-line source-tree regex scanner is
 * pinned by compilation instead: a row's comparison and the catalog item
 * literally share one declaration.
 *
 * What still needs a runtime test — and lives here — is the REFERENTIAL
 * integrity the compiler cannot see: every id must resolve to exactly one
 * catalog item (a row that highlights nothing — the `vlc_video_output` class
 * of bug), and every catalog id must come from a single source (a declaration
 * that bypasses the fused rows or the experimental holder, or an orphan
 * highlight target). On top of that sit the behavioral pins: the fixed
 * drifts' scroll behavior, the aggregation-decoration splits, the declared
 * row admissions and the per-group totals. The four shipped drifts each would
 * still fail loudly:
 *  - `vlc_video_output` (declared, in NO screen group) — caught by the
 *    vocabulary↔catalog↔group integrity test;
 *  - `live_stream_option` / `dialogue_boost_strength` (row/group drifts) —
 *    caught by the integrity test + the scroll pins;
 *  - `auto_delete_after_watch` (group id + row, NO search item) — caught by
 *    the integrity test (declared id with no catalog item).
 *
 * Deliberate value pins elsewhere (`SettingsSearchCatalogTest`,
 * `SettingsSearchCatalogPlatformFilterTest`) keep the raw id literals: they
 * guard the persisted deep-link/recents strings — a change there is a
 * reviewed schema change at the single sources, not a refactor. The
 * conversion ratchets themselves live in `FusedRowsRatchetTest`.
 */
class SettingsCatalogScreenContractTest {

    // ── The single-source vocabularies ──────────────────────────────────

    /**
     * The one remaining ids holder: the experimental screen's five rows stay
     * on the ExperimentalPreferenceSpecs + binding derivation (their per-row
     * search categories differ, so they cannot fold into a single-category
     * fused row list). Every other domain declares its ids on the fused rows.
     */
    private val idsHolders: List<Any> = listOf(
        ExperimentalSettingsIds,
    )

    /** Every fused row vocabulary — one entry per converted domain. */
    private val fusedRowVocabularies: List<List<SettingsRow>> = listOf(
        AppearanceRows.all,
        HomeRows.all,
        PlaybackRows.all,
        AudioRows.all,
        StorageRows.all,
        LanguageRows.all,
        TrackSelectionRows.all,
        NotificationRows.all,
        SecurityRows.all,
        SystemRows.all,
        AccountRows.all,
        ActivityInsightsRows.all,
        IntegrationsRows.all,
        BackupRows.all,
        AboutRows.all,
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

    private val allFusedIds: List<String> by lazy { fusedRowVocabularies.flatten().map { it.id } }

    // ── 1. Referential integrity: vocabulary ↔ catalog ↔ groups ────────

    @Test
    fun `every settings row id resolves to exactly one catalog item`() {
        // Both single-source vocabularies: the fused rows (converted
        // domains) and the experimental ids holder (the binding derivation).
        val singleSourceIds = allHolderIds + allFusedIds
        assertTrue(allHolderIds.isNotEmpty(), "no ids holder declares any id — reflection scan is broken")
        assertTrue(allFusedIds.isNotEmpty(), "no fused row vocabulary declares any id — scan broken?")
        val catalogIds = SettingsSearchCatalog.items.map { it.id }
        val missing = singleSourceIds.filter { id -> catalogIds.count { it == id } != 1 }
        assertEquals(emptyList(), missing, "row ids that resolve to no (or several) catalog items")
    }

    @Test
    fun `every catalog id comes from a single source and lands in exactly one group`() {
        // A declaration built with a raw literal (bypassing the fused rows
        // and the experimental holder), or a new vocabulary not registered
        // above, fails here.
        val singleSourceIds = allHolderIds.toSet() + allFusedIds.toSet()
        val undeclared = SettingsSearchCatalog.items.map { it.id }.filter { it !in singleSourceIds }
        assertEquals(emptyList(), undeclared, "catalog ids declared outside the single sources (fused rows + experimental holder)")

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
    }

    /**
     * The strict derivation's coverage ratchet: [rowTotalFor] counts nothing
     * for an id that declares no gate, so every group a screen feeds a
     * `SettingsItemList` total from must declare EVERY id's admission — the
     * listed exceptions are the deliberately-undeclared rows (screen-local or
     * content-gated rows the screens add explicitly, each commented at its
     * declaration). The one remaining size-derived exception
     * (storage.network) is pinned separately below — its screen feeds the
     * declaration size itself, so the per-id ratchet cannot apply.
     */
    @Test
    fun `groups the screens feed totals from declare every id's admission`() {
        val undeclaredExceptions = mapOf(
            "audio" to emptySet<String>(),
            "playback.player" to emptySet(),
            "playback.advancedVideo" to emptySet(),
            "playback.engine" to emptySet(),
            "notifications" to emptySet(),
            "language.general" to emptySet(),
            "language.trackSelection" to emptySet(),
            "language.subtitles" to emptySet(),
            "storage.cache" to emptySet(),
            "storage.downloads" to emptySet(),
            // The shipped count quirks: pin_for_player_lock renders behind the
            // pin toggle but has never been admitted into the count, and the
            // quick-connect / remote-control / remote-display rows render in
            // their own single-row groups outside the lock-group total.
            "security" to setOf(
                SecurityRows.PinForPlayerLock.id,
                SecurityRows.QuickConnectAuthorize.id,
                SecurityRows.RemoteControlEnabled.id,
                SecurityRows.RemoteDisplayContentEnabled.id,
            ),
            // The unhide row rides hidden-CW content state — no admission
            // vocabulary; the screen adds the +1 explicitly.
            "home.display" to setOf(HomeRows.UnhideCw.id),
            // The cards group (the PS-4 Appearance → HomeSettings move)
            // renders every declared row unconditionally — declared Always
            // per id, fully covered.
            "home.cards" to emptySet(),
            // The theme group's content-gated rows: their emission depends on
            // theme state the admission vocabulary does not carry (variant
            // accent support, standard-vs-themed branch, active-dark + OLED
            // allowance, the SCHEDULED theme mode). They are DECLARED
            // RowAdmission.ContentGated on their fused rows, so the derived
            // admissions carry no entry for them (the strict ratchet shape:
            // the pin list must be the literal rows — a row switching to
            // ContentGated without being pinned here fails). The rows'
            // content terms are added explicitly by
            // appearanceThemeScreenRowTotal. theme_scheduler is the
            // highlight-alias row (its screen face is the theme_mode row via
            // THEME_HIGHLIGHT_IDS) — ContentGated and counted by no total,
            // the shipped quirk shape. (layout_mode left this exception set
            // in the fused conversion: its All(Advanced, NotTv) gate is
            // flags-expressible and derives with the advanced base.)
            "appearance.theme" to setOf(
                AppearanceRows.StyleAccent.id,
                AppearanceRows.AccentColor.id,
                AppearanceRows.ColorStyle.id,
                AppearanceRows.DynamicTheming.id,
                AppearanceRows.OledMode.id,
                AppearanceRows.ScheduledStart.id,
                AppearanceRows.ScheduledEnd.id,
                AppearanceRows.ThemeScheduler.id,
            ),
            // Library & Cards renders every declared row unconditionally (its
            // shipped always-on behavior) — declared Always per id, fully
            // covered; the screen-local confirm-library-reset action row is
            // the explicit +1 at the call site.
            "appearance.library" to emptySet(),
            // The three advanced-only groups declare their advanced base and
            // only compose behind the advanced structural block.
            "appearance.performance" to emptySet(),
            "appearance.eyeCare" to emptySet(),
            // newsletter_sections' declared row renders as the runtime-
            // reorderable per-section rows (the enum-driven media-segment
            // shape) — the screen's total replaces its admitted slot with the
            // runtime count explicitly.
            "appearance.newsletter" to emptySet(),
        )
        undeclaredExceptions.forEach { (groupId, exceptions) ->
            val group = requireNotNull(SettingsScreenGroups.all.firstOrNull { it.id == groupId })
            assertEquals(
                emptySet(),
                group.itemIdSet - group.admissions.keys - exceptions,
                "$groupId admission coverage drifted (an id declares no gate)",
            )
        }

        // The one remaining size-derived exception: every declared row renders
        // unconditionally, so the screen feeds the declaration size itself —
        // no conditional, so no admission overrides. A total that IS the
        // declaration size self-tracks a new unconditional row, so the per-id
        // ratchet above cannot apply; pin instead that the group admits ALL
        // eleven rows under the default flags — the four advanced-tagged rows
        // carry an explicit [RowAdmission.Always] gate (the legacy tags the
        // screen never honored), so the derived admissions agree with the
        // screen's unconditional size.
        val sizeDerivedGroups = mapOf(
            "storage.network" to 11,
        )
        sizeDerivedGroups.forEach { (groupId, declaredCount) ->
            val group = requireNotNull(SettingsScreenGroups.all.firstOrNull { it.id == groupId })
            assertEquals(
                declaredCount,
                group.items.size,
                "$groupId declaration changed — update this pin or move the group onto the admission-derived ratchet",
            )
            assertEquals(
                declaredCount,
                rowTotalFor(group, RowAdmissionFlags()),
                "$groupId grew a gate its screen does not read — the screen feeds the declaration size",
            )
        }
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
            PlaybackRows.Orientation.id,
            PlaybackRows.SeekDuration.id,
            PlaybackRows.Gestures.id,
            PlaybackRows.GestureIndicatorSide.id,
        ).forEach { id ->
            assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(id, desktop), "$id must drop without its capability")
        }
        val touch = RowAdmissionFlags(supportsTouchGestures = true)
        listOf(
            PlaybackRows.SeekDuration.id,
            PlaybackRows.Gestures.id,
            PlaybackRows.GestureIndicatorSide.id,
        ).forEach {
            assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(it, touch), "$it must admit on the capability")
        }
        assertTrue(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackRows.Orientation.id,
                RowAdmissionFlags(supportsScreenOrientation = true),
            ),
        )
        // the volume-memory toggle is DESKTOP-backed — it admits on
        // this JVM's default seam flags (the desktop binary backs the row)
        // and drops where the capability is off (Android).
        assertTrue(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackRows.RememberVolumePerContentType.id,
                desktop,
            ),
            "the volume-memory row admits on the desktop seam",
        )
        assertFalse(
            SettingsScreenGroups.playbackPlayer.rowAdmitted(
                PlaybackRows.RememberVolumePerContentType.id,
                RowAdmissionFlags(supportsVolumeMemory = false),
            ),
            "the volume-memory row must drop without its capability",
        )
    }

    @Test
    fun `tv rows admit on the tv form factor alone - the shipped count semantics`() {
        // The two TV rows declare gate = Tv, yet the total has always
        // admitted them on isTv alone (their emission sits inside the advanced
        // block, which carries the advanced half of the gate).
        val tv = RowAdmissionFlags(isTv = true)
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, tv))
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.TvZoomMode.id, tv))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, RowAdmissionFlags()))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(PlaybackRows.TvZoomMode.id, RowAdmissionFlags(showAdvanced = true)))
    }

    @Test
    fun `when-on rows admit on their declared parent toggle`() {
        // The storage schedule windows ride their parent toggle alone…
        val scheduleOn = RowAdmissionFlags(parentsOn = setOf(StorageRows.DownloadSchedule.id))
        listOf(
            StorageRows.DownloadScheduleStart.id,
            StorageRows.DownloadScheduleEnd.id,
            StorageRows.DownloadScheduleWifiOnly.id,
        ).forEach {
            assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(it, scheduleOn), "$it admits on the schedule toggle")
            assertFalse(SettingsScreenGroups.storageDownloads.rowAdmitted(it, RowAdmissionFlags()), "$it drops when scheduling is off")
        }
        assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.DownloadSchedule.id, RowAdmissionFlags()))

        // …the auto-download retention cluster rides the auto-download toggle
        // the same way, and the "Clean up now" action additionally rides the
        // keep-days picker being non-zero (the screen feeds the > 0 state).
        val autoDownloadOn = RowAdmissionFlags(parentsOn = setOf(StorageRows.AutoDownloadNewEpisodes.id))
        listOf(
            StorageRows.AutoDownloadLookahead.id,
            StorageRows.AutoDownloadMaxPerPass.id,
            StorageRows.AutoDownloadKeepDays.id,
            StorageRows.AutoDownloadServers.id,
        ).forEach {
            assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(it, autoDownloadOn), "$it admits on the auto-download toggle")
            assertFalse(SettingsScreenGroups.storageDownloads.rowAdmitted(it, RowAdmissionFlags()), "$it drops when auto-download is off")
        }
        val keepDaysOn = RowAdmissionFlags(
            parentsOn = setOf(StorageRows.AutoDownloadNewEpisodes.id, StorageRows.AutoDownloadKeepDays.id),
        )
        assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadCleanUpNow.id, keepDaysOn))
        assertFalse(
            SettingsScreenGroups.storageDownloads.rowAdmitted(StorageRows.AutoDownloadCleanUpNow.id, autoDownloadOn),
            "clean-up-now drops while the keep-days window is off",
        )

        // …while the dialogue-boost strength row rides the advanced toggle
        // AND its parent toggle (All(Advanced, WhenOn) — the structural
        // advanced blocks around both emission sites, playback and audio,
        // carry the advanced half).
        val boostOn = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(PlaybackRows.DialogueBoost.id))
        assertTrue(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.DialogueBoostStrength.id, boostOn))
        assertFalse(
            SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(
                PlaybackRows.DialogueBoostStrength.id,
                RowAdmissionFlags(showAdvanced = true),
            ),
            "the strength row must drop while the boost toggle is off",
        )
        assertFalse(
            SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(
                PlaybackRows.DialogueBoostStrength.id,
                RowAdmissionFlags(parentsOn = setOf(PlaybackRows.DialogueBoost.id)),
            ),
            "the strength row must drop while advanced mode is off",
        )

        // …the per-codec passthrough rows ride the master passthrough toggle
        // the same way (All(Advanced, WhenOn) — no bitstreaming, no
        // per-codec allow-list to configure).
        val passthroughOn = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(PlaybackRows.AudioPassthrough.id))
        assertTrue(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.PassthroughCodecAc3.id, passthroughOn))
        assertTrue(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(PlaybackRows.PassthroughCodecTruehd.id, passthroughOn))
        assertFalse(
            SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted(
                PlaybackRows.PassthroughCodecAc3.id,
                RowAdmissionFlags(showAdvanced = true),
            ),
            "the codec rows must drop while the master passthrough toggle is off",
        )

        // …and the audio strength rows also ride the advanced toggle
        // (All(Advanced, WhenOn(parent)) — the advanced half is the
        // structural block the screen's advanced section carries).
        val parentsOnly = RowAdmissionFlags(parentsOn = setOf(AudioRows.NightMode.id, AudioRows.Equalizer.id))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted(AudioRows.NightModeStrength.id, parentsOnly))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted(AudioRows.EqualizerPreset.id, parentsOnly))
        val advanced = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(AudioRows.NightMode.id, AudioRows.Equalizer.id))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted(AudioRows.NightModeStrength.id, advanced))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted(AudioRows.EqualizerPreset.id, advanced))
    }

    @Test
    fun `every playback WhenOn parent rides the screen's admission flags`() {
        // All parent toggles on: playbackRowAdmissionFlags' parentsOn set must
        // then cover EVERY WhenOn parentId the playback groups declare. The
        // AUDIO_PASSTHROUGH pair dropping out of the wiring is exactly how the
        // five per-codec passthrough rows went permanently invisible — this
        // ratchet turns that drift into a loud test failure instead of a
        // blank stretch of settings screen.
        val everyParentOn = playbackRowAdmissionFlags(
            isTv = false,
            showAdvanced = true,
            preferences = PlaybackPreferences(
                dialogueBoostEnabled = true,
                videoAutoplayNext = true,
                audioPassthrough = true,
            ),
        )
        val declaredParents = listOf(
            SettingsScreenGroups.playbackPlayer,
            SettingsScreenGroups.playbackAdvancedVideo,
            SettingsScreenGroups.playbackEngine,
            SettingsScreenGroups.playbackMediaSegments,
            SettingsScreenGroups.playbackSyncPlay,
            SettingsScreenGroups.playbackCasting,
            SettingsScreenGroups.playbackDvr,
        ).flatMap { group -> group.admissions.values.flatMap(::whenOnParents) }
        assertTrue(declaredParents.isNotEmpty(), "the ratchet needs WhenOn declarations to guard")
        val missing = declaredParents.filter { it !in everyParentOn.parentsOn }
        assertEquals(emptyList(), missing, "WhenOn parents missing from playbackRowAdmissionFlags wiring")
    }

    /** Every WhenOn parent a gate tree carries, nested [RowAdmission.All]s included. */
    private fun whenOnParents(admission: RowAdmission): List<String> = when (admission) {
        is RowAdmission.WhenOn -> listOf(admission.parentId)
        is RowAdmission.All -> admission.gates.flatMap { whenOnParents(it) }
        else -> emptyList()
    }

    @Test
    fun `notification rows carry the declared gates the strict count reads`() {
        val admissions = SettingsScreenGroups.notifications.admissions
        // The master toggle states its unconditional gate explicitly…
        assertIs<RowAdmission.Always>(admissions[NotificationRows.NotificationsEnable.id])
        // …the five toggle rows ride it…
        listOf(
            NotificationRows.NotificationCheckFrequency.id,
            NotificationRows.NotificationSound.id,
            NotificationRows.NotificationVibrate.id,
            NotificationRows.NotificationLights.id,
            NotificationRows.NotificationNewEpisodes.id,
        ).forEach { id ->
            val gate = assertIs<RowAdmission.WhenOn>(admissions[id])
            assertEquals(NotificationRows.NotificationsEnable.id, gate.parentId)
        }
        // …the quiet-hours pair rides toggle + advanced + quiet hours…
        val quietGate = admissions[NotificationRows.QuietStart.id]!!
        val quietFlagsOff = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(NotificationRows.NotificationsEnable.id))
        val quietFlagsOn = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = setOf(NotificationRows.NotificationsEnable.id, NotificationRows.QuietHours.id),
        )
        assertFalse(quietGate.admitted(quietFlagsOff))
        assertTrue(quietGate.admitted(quietFlagsOn))
        // …and the system-settings row rides the platform-intent capability.
        val systemFlagsOff = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = setOf(NotificationRows.NotificationsEnable.id),
            supportsSystemNotificationSettings = false,
        )
        val systemFlagsOn = systemFlagsOff.copy(supportsSystemNotificationSettings = true)
        assertFalse(SettingsScreenGroups.notifications.rowAdmitted(NotificationRows.SystemNotificationSettings.id, systemFlagsOff))
        assertTrue(SettingsScreenGroups.notifications.rowAdmitted(NotificationRows.SystemNotificationSettings.id, systemFlagsOn))
        // The count is strict: only declared ids admit.
        assertEquals(SettingsScreenGroups.notifications.itemIds.toSet(), admissions.keys.toSet())
    }

    @Test
    fun `language subtitles rows are fully declared`() {
        val admissions = SettingsScreenGroups.languageSubtitles.admissions
        // Declared advanced yet shown in every mode — the shipped quirk, verbatim.
        assertIs<RowAdmission.Always>(admissions[LanguageRows.HighContrastSubtitles.id])
        // The HDR font-size row additionally rides the HDR-style toggle.
        val hdrGate = assertIs<RowAdmission.All>(admissions[LanguageRows.HdrSubtitleFontSize.id])
        val base = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf(LanguageRows.HdrSubtitleStyle.id))
        assertTrue(hdrGate.admitted(base))
        assertFalse(hdrGate.admitted(base.copy(parentsOn = emptySet())))
        // Every id declares its gate — the count is strict like notifications.
        assertEquals(SettingsScreenGroups.languageSubtitles.itemIds.toSet(), admissions.keys.toSet())
        // The trio plus high-contrast always show; the style rows ride Advanced.
        for (id in listOf(
            LanguageRows.SubtitleFontSize.id,
            LanguageRows.SubtitleForcedOnly.id,
            LanguageRows.SubtitleTester.id,
        )) {
            assertIs<RowAdmission.Always>(admissions[id])
        }
        for (id in listOf(
            LanguageRows.PgsDirectPlay.id,
            LanguageRows.HdrSubtitleStyle.id,
            LanguageRows.SubtitleColor.id,
            LanguageRows.SubtitleBackground.id,
            LanguageRows.SubtitleEdgeStyle.id,
            LanguageRows.SubtitleSyncOffset.id,
            LanguageRows.SubtitleVerticalPosition.id,
        )) {
            assertIs<RowAdmission.Advanced>(admissions[id])
        }
    }

    @Test
    fun `security rows keep the shipped count gates and the undeclared quirk`() {
        val admissions = SettingsScreenGroups.security.admissions
        val pinGateDecl = assertIs<RowAdmission.Platform>(admissions[SecurityRows.PinLock.id])
        assertEquals(RowAdmissionCapability.AppLock, pinGateDecl.capability)
        val biometricGateDecl = assertIs<RowAdmission.Platform>(admissions[SecurityRows.BiometricLock.id])
        assertEquals(RowAdmissionCapability.Biometric, biometricGateDecl.capability)
        assertIs<RowAdmission.All>(admissions[SecurityRows.AutoLockTimer.id])
        // pin_for_player_lock renders behind the pin toggle but has never been
        // admitted into the count — its deliberately-missing declaration (the
        // screen gates its desktop hiding on the capability directly).
        assertNull(admissions[SecurityRows.PinForPlayerLock.id])
        assertNull(admissions[SecurityRows.QuickConnectAuthorize.id])
        assertNull(admissions[SecurityRows.RemoteControlEnabled.id])
        // The biometric flag is the screen's gate-aware computed value; the
        // app-lock flag is the plain capability (desktop's lock gate is
        // Android-only, so every PIN row drops there).
        assertTrue(SettingsScreenGroups.security.rowAdmitted(SecurityRows.BiometricLock.id, RowAdmissionFlags(supportsBiometric = true)))
        assertFalse(SettingsScreenGroups.security.rowAdmitted(SecurityRows.BiometricLock.id, RowAdmissionFlags()))
        assertTrue(SettingsScreenGroups.security.rowAdmitted(SecurityRows.PinLock.id, RowAdmissionFlags(supportsAppLock = true)))
        assertFalse(SettingsScreenGroups.security.rowAdmitted(SecurityRows.PinLock.id, RowAdmissionFlags(supportsAppLock = false)))
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
        assertTrue(PlaybackRows.VlcVideoOutput.id in SettingsScreenGroups.playbackEngine.itemIdSet)
        assertEquals(
            2,
            resolveHighlightScrollIndex(PlaybackRows.VlcVideoOutput.id, playbackGroups, playbackAdjustForAdvanced(true)),
        )
        // With advanced hidden the engine group doesn't compose — no scroll.
        assertEquals(
            -1,
            resolveHighlightScrollIndex(PlaybackRows.VlcVideoOutput.id, playbackGroups, playbackAdjustForAdvanced(false)),
        )
    }

    @Test
    fun `live_stream_option and dialogue_boost_strength land in the advanced-video group`() {
        val advanced = SettingsScreenGroups.playbackAdvancedVideo.itemIdSet
        assertTrue(PlaybackRows.LiveStreamOption.id in advanced)
        assertTrue(PlaybackRows.DialogueBoostStrength.id in advanced)
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackRows.LiveStreamOption.id, playbackGroups, playbackAdjustForAdvanced(true)),
        )
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackRows.DialogueBoostStrength.id, playbackGroups, playbackAdjustForAdvanced(true)),
        )
    }

    @Test
    fun `auto_delete_after_watch is declared in the downloads group with a screen row`() {
        assertTrue(StorageRows.AutoDeleteAfterWatch.id in SettingsScreenGroups.storageDownloads.itemIdSet)
        // The row itself shares the same declaration (compile-paired) — the
        // source-scan that used to assert the literal is retired.
        assertTrue(StorageRows.AutoDeleteAfterWatch.id in allFusedIds)
    }

    @Test
    fun `gesture_indicator_side scrolls within the player group`() {
        // Not one of the four shipped drifts, but the hand-typed player scroll
        // list had omitted it all the same; the derivation keeps it honest.
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.GestureIndicatorSide.id, playbackGroups, playbackAdjustForAdvanced(true)),
        )
    }

    @Test
    fun `always-visible playback groups shift correctly when advanced is hidden`() {
        // SyncPlay/casting/DVR always render; with advanced off they sit at
        // LazyColumn indices 1/2/3 behind the player group.
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackRows.SyncplayJoinBehavior.id, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        assertEquals(
            2,
            resolveHighlightScrollIndex(PlaybackRows.BackgroundCasting.id, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        assertEquals(
            3,
            resolveHighlightScrollIndex(PlaybackRows.DvrRecordingQuality.id, playbackGroups, playbackAdjustForAdvanced(false)),
        )
        // The player group never moves.
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.PlayerEngine.id, playbackGroups, playbackAdjustForAdvanced(false)),
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
            enumSegmentIds + PlaybackRows.SkipSegmentsOnSeek.id,
            SettingsScreenGroups.playbackMediaSegments.itemIds,
            "media-segment group declarations drifted from MediaSegmentType.entries + the skip-on-seek toggle",
        )
        assertTrue(
            SettingsScreenGroups.playbackDvr.itemIds.none {
                it.startsWith(SettingsScreenGroups.MEDIA_SEGMENT_ID_PREFIX) || it == PlaybackRows.SkipSegmentsOnSeek.id
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
            listOf(LanguageRows.AppLanguage.id, LanguageRows.AudioLanguage.id, LanguageRows.SubtitleLanguage.id),
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
            listOf(SystemRows.AdminDashboard.id, SystemRows.SetupWizard.id),
            SettingsScreenGroups.systemCore.itemIds,
        )
        assertTrue(
            SettingsScreenGroups.systemScreensaver.itemIds.all { it.startsWith(SettingsScreenGroups.SCREENSAVER_ID_PREFIX) },
            "system.screensaver must hold only screensaver ids",
        )
        // The idle-ambient (desktop), Discord-presence (desktop) and
        // shell-hooks (desktop) rows are each split into their own group —
        // the five groups together still partition SystemSearchItems exactly.
        assertEquals(
            SystemSearchItems.size,
            SettingsScreenGroups.systemCore.items.size +
                SettingsScreenGroups.systemScreensaver.items.size +
                SettingsScreenGroups.systemIdleAmbient.items.size +
                SettingsScreenGroups.systemDiscordPresence.items.size +
                SettingsScreenGroups.systemHooks.items.size,
        )
        assertTrue(
            SettingsScreenGroups.systemIdleAmbient.itemIds.all { it.startsWith(SettingsScreenGroups.IDLE_AMBIENT_ID_PREFIX) },
            "system.idleAmbient must hold only idle-ambient ids",
        )
        assertTrue(
            SettingsScreenGroups.systemDiscordPresence.itemIds.all { it.startsWith(SettingsScreenGroups.DISCORD_PRESENCE_ID_PREFIX) },
            "system.discordPresence must hold only discord-presence ids",
        )
        assertTrue(
            SettingsScreenGroups.systemHooks.itemIds.all { it.startsWith(SettingsScreenGroups.HOOKS_ID_PREFIX) },
            "system.hooks must hold only shell-hook ids",
        )
    }

    @Test
    fun `language deep-links scroll via the derived groups`() {
        val languageGroups = listOf(
            SettingsScreenGroups.languageGeneral.itemIdSet,
            SettingsScreenGroups.languageSubtitles.itemIdSet,
        )
        assertEquals(0, resolveHighlightScrollIndex(LanguageRows.SubtitleLanguage.id, languageGroups))
        assertEquals(1, resolveHighlightScrollIndex(LanguageRows.SubtitleTester.id, languageGroups))
        assertTrue(LanguageRows.SubtitleTester.id in SettingsScreenGroups.languageSubtitles.itemIdSet)
        assertTrue(LanguageRows.HighContrastSubtitles.id in SettingsScreenGroups.languageSubtitles.itemIdSet)
    }

    // ── 6. The per-group row-total derivation ───────────────────────────

    /**
     * The index/total pair a rendered group emits: inside `SettingsItemList`
     * the rows take the auto-index 0..total-1 and every row's `count` is the
     * admission total, so each pinned total below IS the total the screen
     * feeds and the sequence below IS the index sequence the rows display.
     * Every total is read through [rowTotalFor] — the same call the screens
     * make — so the pins hold the derivation itself, not a per-screen
     * wrapper.
     */
    private fun emittedIndexSequence(total: Int): List<Int> = (0 until total).toList()

    @Test
    fun `audio row total tracks the declaration and its conditionals`() {
        // The naive derivation, over the same declarations the screen reads:
        // the five non-advanced rows always render; an advanced row renders
        // behind the advanced toggle except the seven effect-dependent rows,
        // which additionally ride their parent toggles (each mapped to its
        // parent id below — the replaygain pre-amp's normalization parent
        // counts as on in the TRACK/ALBUM modes via audioRowAdmissionFlags).
        // The screen-local dialogue-boost pair — declared with the playback
        // advanced-video declaration (both screens render it) — is added
        // explicitly: the toggle rides the equalizer block (read through the
        // preset row's declared equalizer gate), the strength row its own
        // All(Advanced, WhenOn(dialogue_boost)) gate.
        val parentGatedAudioRows = mapOf(
            AudioRows.ReplaygainPreamp.id to AudioRows.VolumeNormalization.id,
            AudioRows.EqualizerPreset.id to AudioRows.Equalizer.id,
            AudioRows.NightModeStrength.id to AudioRows.NightMode.id,
            AudioRows.BassBoostStrength.id to AudioRows.BassBoost.id,
            AudioRows.VirtualizerStrength.id to AudioRows.Virtualizer.id,
            AudioRows.VolumeBoostGain.id to AudioRows.VolumeBoost.id,
            AudioRows.ChannelMixMode.id to AudioRows.ChannelMixing.id,
        )
        fun naiveAudioTotal(showAdvanced: Boolean, preferences: AudioPreferences): Int {
            val parentsOn = audioRowAdmissionFlags(showAdvanced, preferences).parentsOn
            return SettingsScreenGroups.audio.items.count { row ->
                !row.isAdvanced ||
                    (
                        showAdvanced &&
                            (row.id !in parentGatedAudioRows || parentGatedAudioRows.getValue(row.id) in parentsOn)
                        )
            } +
                (if (showAdvanced && AudioRows.Equalizer.id in parentsOn) 1 else 0) + // +1: the dialogue-boost toggle
                (if (showAdvanced && PlaybackRows.DialogueBoost.id in parentsOn) 1 else 0) // +1: the dialogue-boost strength row
        }
        assertEquals(
            naiveAudioTotal(showAdvanced = false, preferences = AudioPreferences()),
            audioScreenRowTotal(showAdvanced = false, preferences = AudioPreferences()),
        )
        assertEquals(
            naiveAudioTotal(showAdvanced = true, preferences = AudioPreferences()),
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences()),
        )
        val allConditionalsOn = AudioPreferences(
            audioNormalizationMode = AudioNormalizationMode.TRACK,
            equalizerEnabled = true,
            dialogueBoostEnabled = true,
            nightModeEnabled = true,
            bassBoostEnabled = true,
            virtualizerEnabled = true,
            volumeBoostEnabled = true,
            channelMixEnabled = true,
        )
        assertEquals(
            naiveAudioTotal(showAdvanced = true, preferences = allConditionalsOn),
            audioScreenRowTotal(
                showAdvanced = true,
                preferences = allConditionalsOn,
            ),
        )
        // Each conditional moves the total by exactly its own row — except
        // the equalizer toggle, which also reveals the dialogue-boost row
        // (the strength row the dialogue-boost toggle itself gates).
        assertEquals(
            naiveAudioTotal(showAdvanced = true, preferences = AudioPreferences(audioNormalizationMode = AudioNormalizationMode.ALBUM)),
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(audioNormalizationMode = AudioNormalizationMode.ALBUM)),
        )
        assertEquals(
            naiveAudioTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true)),
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true)),
        )
        assertEquals(
            naiveAudioTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true, dialogueBoostEnabled = true)),
            audioScreenRowTotal(showAdvanced = true, preferences = AudioPreferences(equalizerEnabled = true, dialogueBoostEnabled = true)),
        )
    }

    @Test
    fun `audio row total counts the five non-advanced declarations unconditionally`() {
        val nonAdvanced = SettingsScreenGroups.audio.items.count { !it.isAdvanced }
        assertEquals(5, nonAdvanced, "the audio declaration's non-advanced set changed — update the totals pin")
        assertEquals(
            nonAdvanced,
            rowTotalFor(SettingsScreenGroups.audio, RowAdmissionFlags()),
        )
    }

    @Test
    fun `storage row totals track the cache and downloads declarations`() {
        // Cache: the screen-local cache-used info row (the explicit +1) plus
        // the declared rows — the two clear rows always, the five tuning rows
        // behind the advanced toggle (their declared Advanced base).
        assertEquals(
            SettingsScreenGroups.storageCache.items.count { !it.isAdvanced },
            rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = false)),
        )
        assertEquals(
            SettingsScreenGroups.storageCache.items.count { !it.isAdvanced } + 1,
            rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = false)) + 1,
        )
        assertEquals(
            SettingsScreenGroups.storageCache.items.size + 1,
            rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = true)) + 1,
        )
        assertEquals(
            SettingsScreenGroups.storageCache.items.count { !it.isAdvanced } + 1, // +1: the screen-local cache-used info row
            rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = false)) + 1,
        )
        assertEquals(
            SettingsScreenGroups.storageCache.items.size + 1, // +1: the screen-local cache-used info row
            rowTotalFor(SettingsScreenGroups.storageCache, RowAdmissionFlags(showAdvanced = true)) + 1,
        )
        assertEquals(
            emittedIndexSequence(SettingsScreenGroups.storageCache.items.count { !it.isAdvanced } + 1),
            List(SettingsScreenGroups.storageCache.items.count { !it.isAdvanced } + 1) { it },
        )

        // Network: unconditionally the whole declaration (the screen feeds
        // `SettingsScreenGroups.storageNetwork.items.size` directly — no
        // conditional, so no admission function).
        assertEquals(11, SettingsScreenGroups.storageNetwork.items.size, "the storage.network declaration changed — update this pin")

        // Downloads: the three download_schedule_* window rows only when
        // scheduling is on (their declared WhenOn gate); the four auto-download
        // retention rows only when auto-download is on; clean-up-now only when
        // the keep-days window is set; every other record renders
        // unconditionally (its non-advanced base).
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 8,
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to false)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 5,
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 8, // minus the three schedule windows, the four auto-download retention rows, and the clean-up action — every parent-gated row
            rowTotalFor(SettingsScreenGroups.storageDownloads, RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to false))),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 5, // minus the four auto-download retention rows and the clean-up action
            rowTotalFor(SettingsScreenGroups.storageDownloads, RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true))),
        )
        // The auto-download retention cluster: the four policy rows ride the
        // auto-download toggle, and clean-up-now additionally rides the
        // keep-days picker being non-zero — with the schedule and
        // auto-download windows on, only the clean-up action stays gated
        // off; turning the keep-days window on admits every declared row.
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - 1, // minus the clean-up action (the keep-days window is still off)
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true, StorageRows.AutoDownloadNewEpisodes.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size, // every declared row admits
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(
                    parentsOn = rowParentsOn(
                        StorageRows.DownloadSchedule.id to true,
                        StorageRows.AutoDownloadNewEpisodes.id to true,
                        StorageRows.AutoDownloadKeepDays.id to true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `playback row totals track the player advanced-video and engine declarations`() {
        // Player: the six unconditional rows (player_engine, default_speed,
        // default_aspect, video_autoplay_next, autoplay_countdown,
        // hold_speed_multiplier) survive every gate off; each flag reveals
        // exactly its own rows — plus the volume-memory toggle, whose
        // Platform(VolumeMemory) admission rides this JVM's desktop seam (on).
        val allGatesOff = rowTotalFor(
            SettingsScreenGroups.playbackPlayer,
            RowAdmissionFlags(
                isTv = false,
                showAdvanced = false,
                supportsScreenOrientation = false,
                supportsTouchGestures = false,
            ),
        )
        // Naive derivation: with every gate off, the non-advanced
        // declarations survive EXCEPT the five Platform-gated rows (the
        // orientation row and the four touch rows — both capabilities are
        // off here) and the two WhenOn(autoplay) still-watching rows (no
        // parents on). The Always-admitted input-bindings editor row (every
        // platform has some input surface) and the desktop-backed
        // volume-memory toggle (this JVM's seam) stay in.
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.count { !it.isAdvanced } - 5 - 2,
            allGatesOff,
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.count { !it.isAdvanced } - 4 - 2, // the orientation capability reveals exactly its own row
            rowTotalFor(
                SettingsScreenGroups.playbackPlayer,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = false,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = false,
                ),
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.count { !it.isAdvanced } - 1 - 2, // the touch capability reveals exactly its four rows; the orientation row stays hidden
            rowTotalFor(
                SettingsScreenGroups.playbackPlayer,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = false,
                    supportsScreenOrientation = false,
                    supportsTouchGestures = true,
                ),
            ),
        )
        // Every non-advanced declaration renders with caps on (isTv off: the
        // two TV rows ride isTv alone — shipped semantics, also without
        // advanced mode), EXCEPT the two still-watching rows, which ride the
        // autoplay toggle (their declared WhenOn gate — the flags here carry
        // no parentsOn).
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.count { !it.isAdvanced } - 2,
            rowTotalFor(
                SettingsScreenGroups.playbackPlayer,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = false,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.size - 5, // minus the two TV rows, the two WhenOn(autoplay) still-watching rows, and the Platform(Pip) auto-PiP row (off on this desktop JVM's seam)
            rowTotalFor(
                SettingsScreenGroups.playbackPlayer,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = true,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )
        // On TV the two TV rows come back (they ride isTv alone — shipped
        // semantics, also without advanced mode); every other advanced
        // declaration (the resume-on-headset-plug row among them) admits
        // under advanced, and the Always-admitted input-bindings editor row
        // needs no advanced. Only the two WhenOn(autoplay) still-watching
        // rows (no parents on) and the Platform(Pip) auto-PiP row (off on
        // this desktop JVM's seam — TV does not back it) drop.
        assertEquals(
            SettingsScreenGroups.playbackPlayer.items.size - 2 - 1,
            rowTotalFor(
                SettingsScreenGroups.playbackPlayer,
                RowAdmissionFlags(
                    isTv = true,
                    showAdvanced = true,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )

        // Advanced video: the whole group only composes behind the advanced
        // toggle (its declared Advanced base — the flags carry that toggle);
        // the dialogue-boost strength row AND the five per-codec passthrough
        // rows additionally ride their parent toggles (their declared All
        // gates) — with no parents on, those six drop.
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size - 6,
            rowTotalFor(SettingsScreenGroups.playbackAdvancedVideo, RowAdmissionFlags(showAdvanced = true)),
        )
        val boostOn = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = rowParentsOn(
                PlaybackRows.DialogueBoost.id to true,
                PlaybackRows.AudioPassthrough.id to true,
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size,
            rowTotalFor(SettingsScreenGroups.playbackAdvancedVideo, boostOn),
        )

        // Engine branches: declared prefix rows (the mpv branch minus the
        // desktop-gated audio-device trio and the render rows
        // where the capabilities are off) + the one reset row each. Desktop
        // JVM tests run with the capabilities ON (the desktop binary backs
        // the rows), so the default-flag call sees all of them.
        // Each branch's declared rows (prefix-counted) plus the screen-local
        // reset row (+1 — the one reset_engine_defaults item renders in
        // every engine branch).
        assertEquals(
            SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("mpv_") } + 1,
            playbackEngineScreenRowTotal("mpv_"),
        )
        assertEquals(
            SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("vlc_") } + 1,
            playbackEngineScreenRowTotal("vlc_"),
        )
        assertEquals(
            SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("exo_") } + 1,
            playbackEngineScreenRowTotal("exo_"),
        )
        assertEquals(
            SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("mpv_") } - 3 - 5 + 1, // minus the audio-device trio and the five render rows (both capabilities off); +1: the reset row
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
        assertEquals(
            emittedIndexSequence(playbackEngineScreenRowTotal("vlc_")),
            (0 until SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("vlc_") } + 1).toList(),
        )
    }

    @Test
    fun `notification row total tracks the declaration through every gate`() {
        // The naive derivation, over the same declarations the screen reads:
        // the master toggle always renders (its declared Always gate); the
        // five toggle rows ride it; the four advanced rows
        // (respect_system_dnd, quiet_hours, max_per_check,
        // notification_libraries) additionally ride the advanced toggle; the
        // quiet pair additionally rides the quiet-hours toggle; the
        // system-settings row additionally rides the platform-intent
        // capability.
        val whenOnMasterRows = listOf(
            NotificationRows.NotificationCheckFrequency.id,
            NotificationRows.NotificationSound.id,
            NotificationRows.NotificationVibrate.id,
            NotificationRows.NotificationLights.id,
            NotificationRows.NotificationNewEpisodes.id,
        )
        val advancedRows = listOf(
            NotificationRows.RespectSystemDnd.id,
            NotificationRows.QuietHours.id,
            NotificationRows.MaxPerCheck.id,
            NotificationRows.NotificationLibraries.id,
        )
        val quietPairRows = listOf(
            NotificationRows.QuietStart.id,
            NotificationRows.QuietEnd.id,
        )
        fun naiveNotificationTotal(showAdvanced: Boolean, masterOn: Boolean, quietHoursOn: Boolean, systemSettingsCap: Boolean): Int =
            1 + // the master toggle
                (if (masterOn) whenOnMasterRows.size else 0) +
                (if (masterOn && showAdvanced) advancedRows.size else 0) +
                (if (masterOn && showAdvanced && quietHoursOn) quietPairRows.size else 0) +
                (if (masterOn && showAdvanced && systemSettingsCap) 1 else 0) // +1: the platform's system-notification-settings row
        val allOn = rowTotalFor(
            SettingsScreenGroups.notifications,
            RowAdmissionFlags(
                showAdvanced = true,
                supportsSystemNotificationSettings = true,
                parentsOn = rowParentsOn(
                    NotificationRows.NotificationsEnable.id to true,
                    NotificationRows.QuietHours.id to true,
                ),
            ),
        )
        assertEquals(SettingsScreenGroups.notifications.items.size, allOn, "a declared notification id is admitted by no gate")
        val masterOff = RowAdmissionFlags(
            showAdvanced = true,
            supportsSystemNotificationSettings = true,
            parentsOn = rowParentsOn(NotificationRows.QuietHours.id to true),
        )
        assertEquals(
            naiveNotificationTotal(showAdvanced = true, masterOn = false, quietHoursOn = true, systemSettingsCap = true),
            rowTotalFor(SettingsScreenGroups.notifications, masterOff),
        ) // master toggle off: only itself
        assertEquals(
            naiveNotificationTotal(
                showAdvanced = false,
                masterOn = true,
                quietHoursOn = true,
                systemSettingsCap = settingsCapabilities.supportsSystemNotificationSettings,
            ),
            rowTotalFor(
                SettingsScreenGroups.notifications,
                RowAdmissionFlags(parentsOn = rowParentsOn(NotificationRows.NotificationsEnable.id to true, NotificationRows.QuietHours.id to true)),
            ),
        ) // + frequency/sound/vibrate/lights/new-episodes
        assertEquals(
            naiveNotificationTotal(
                showAdvanced = true,
                masterOn = true,
                quietHoursOn = false,
                systemSettingsCap = settingsCapabilities.supportsSystemNotificationSettings,
            ),
            rowTotalFor(
                SettingsScreenGroups.notifications,
                RowAdmissionFlags(
                    showAdvanced = true,
                    parentsOn = rowParentsOn(NotificationRows.NotificationsEnable.id to true),
                ),
            ),
        ) // + quiet hours/dnd/max-per-check/libraries
        assertEquals(
            naiveNotificationTotal(
                showAdvanced = true,
                masterOn = true,
                quietHoursOn = true,
                systemSettingsCap = settingsCapabilities.supportsSystemNotificationSettings,
            ),
            rowTotalFor(
                SettingsScreenGroups.notifications,
                RowAdmissionFlags(
                    showAdvanced = true,
                    parentsOn = rowParentsOn(
                        NotificationRows.NotificationsEnable.id to true,
                        NotificationRows.QuietHours.id to true,
                    ),
                ),
            ),
        ) // + quiet start/end
        assertEquals(
            naiveNotificationTotal(showAdvanced = true, masterOn = true, quietHoursOn = true, systemSettingsCap = true),
            allOn,
        ) // + the platform's system-notification-settings row
    }

    @Test
    fun `language row totals track the trio and the subtitles conditionals`() {
        // The leading trio: the app-language row only where the locale
        // override applies (its declared AppLocaleOverride Platform gate).
        assertEquals(
            SettingsScreenGroups.languageGeneral.items.size - 1, // minus the app-language row behind its AppLocaleOverride capability (off in these flags)
            rowTotalFor(SettingsScreenGroups.languageGeneral, RowAdmissionFlags(supportsAppLocaleOverride = false)),
        )
        assertEquals(
            SettingsScreenGroups.languageGeneral.items.size,
            rowTotalFor(SettingsScreenGroups.languageGeneral, RowAdmissionFlags(supportsAppLocaleOverride = true)),
        )

        // Subtitles: 4 always (tester/font-size/forced-only + the
        // always-shown high-contrast row — declared advanced yet never
        // gated); the style rows behind advanced; the HDR font-size row
        // behind the HDR toggle.
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.count { !it.isAdvanced } + 1, // +1: the always-shown high-contrast row (declared advanced yet never gated — the shipped quirk)
            rowTotalFor(
                SettingsScreenGroups.languageSubtitles,
                RowAdmissionFlags(showAdvanced = false, parentsOn = rowParentsOn(LanguageRows.HdrSubtitleStyle.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.size - 1, // minus the HDR font-size row behind its WhenOn(HDR style) parent (no parents on in these flags)
            rowTotalFor(SettingsScreenGroups.languageSubtitles, RowAdmissionFlags(showAdvanced = true)),
        )
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.size,
            rowTotalFor(
                SettingsScreenGroups.languageSubtitles,
                RowAdmissionFlags(showAdvanced = true, parentsOn = rowParentsOn(LanguageRows.HdrSubtitleStyle.id to true)),
            ),
        )

        // Track selection: every row always renders (its declared Always).
        assertEquals(4, SettingsScreenGroups.languageTrackSelection.items.size, "the track-selection declaration changed — update this pin")
        assertEquals(
            SettingsScreenGroups.languageTrackSelection.items.size,
            rowTotalFor(SettingsScreenGroups.languageTrackSelection, RowAdmissionFlags()),
        )
    }

    @Test
    fun `home display row total is the nine declared rows plus the conditional unhide row`() {
        // The retired oracle double-counted: items.size (10, including the
        // unhide row) + 1 — while the screen emits 9 rows without hidden CW
        // items and 10 with. The derivation fixes the count to the emitted
        // rows: the nine always-declared rows plus the explicit unhide +1.
        val displayRowsWithoutUnhide = SettingsScreenGroups.homeDisplay.items.count { it.id != HomeRows.UnhideCw.id }
        assertEquals(displayRowsWithoutUnhide, homeDisplayScreenRowTotal(hiddenCwItems = 0))
        assertEquals(
            displayRowsWithoutUnhide + 1, // +1: the unhide row, once hidden CW items exist
            homeDisplayScreenRowTotal(hiddenCwItems = 1),
        )
        assertEquals(
            SettingsScreenGroups.homeDisplay.items.size - 1,
            homeDisplayScreenRowTotal(hiddenCwItems = 0),
            "the display declaration changed — update this pin",
        )
        // The unhide row is declared last, so the derived emission order
        // matches the retired hand-built list.
        assertEquals(HomeRows.UnhideCw.id, SettingsScreenGroups.homeDisplay.itemIds.last())
        // …and it admits for emission (rowAdmitted's no-gate default) — the
        // screen's content-state `if` carries its gate.
        assertTrue(SettingsScreenGroups.homeDisplay.rowAdmitted(HomeRows.UnhideCw.id, RowAdmissionFlags()))
    }

    @Test
    fun `appearance library row total is the declaration plus the reset action row`() {
        // Every declared row renders unconditionally (its declared Always —
        // the shipped always-on set) and the screen-local confirm-library-
        // reset action row is the explicit +1.
        assertEquals(
            SettingsScreenGroups.appearanceLibrary.items.size,
            rowTotalFor(SettingsScreenGroups.appearanceLibrary, RowAdmissionFlags()),
        )
        assertEquals(
            SettingsScreenGroups.appearanceLibrary.items.size + 1,
            appearanceLibraryScreenRowTotal(),
        )
        // 8 declared rows (the shipped set minus the home-discovery
        // card-display quartet that moved to HomeSettingsScreen — PS-4) plus
        // the screen-local confirm-library-reset action row.
        assertEquals(
            SettingsScreenGroups.appearanceLibrary.items.size + 1, // +1: the screen-local confirm-library-reset action row
            appearanceLibraryScreenRowTotal(),
        )
    }

    @Test
    fun `home cards row total is the four moved card-display rows`() {
        // The PS-4 moved quartet: every row renders unconditionally on the
        // home hub (no advanced gate), so the strict derivation counts all
        // four under the no-flag input.
        assertEquals(4, SettingsScreenGroups.homeCards.items.size, "the cards declaration changed — update this pin")
        assertEquals(
            SettingsScreenGroups.homeCards.items.size,
            homeCardsScreenRowTotal(),
        )
        assertTrue(SettingsScreenGroups.homeCards.items.all { !it.isAdvanced }, "the cards rows must stay always-on")
    }

    @Test
    fun `appearance theme row total tracks the declaration and its content gates`() {
        val standard = ThemeVariant.STANDARD
        val themed = ThemeVariant.SYNTHWAVE // carries accent options + dark-locked
        // Standard variant, gates off: theme_mode + theme_style, plus the
        // standard-branch accent_color + color_style = 4.
        assertEquals(
            4,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = false,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // The Android-12 capability adds exactly the dynamic_theming row.
        assertEquals(
            5,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = true,
                isTv = false,
                showAdvanced = false,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // A themed variant swaps the standard branch for the accent picker.
        assertEquals(
            3,
            appearanceThemeScreenRowTotal(
                variant = themed,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = false,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // Active dark + an OLED-capable themed variant (Vivid: accent picker
        // + OLED allowance) adds exactly the oled row.
        assertEquals(
            4,
            appearanceThemeScreenRowTotal(
                variant = ThemeVariant.VIVID,
                isDarkActive = true,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = false,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // The advanced toggle reveals the nine declared advanced rows
        // (contrast, library_view_mode, layout_mode, theme_music, nav_labels,
        // date_format, font_scale, color_blind_mode, hand_mode) on
        // touch/desktop plus the two content terms the standard branch added.
        assertEquals(
            4 + 9,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = true,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // The form-factor fork swaps the rows: layout_mode is
        // touch/desktop-only, and the TV-only "Screen fit" overscan picker
        // (always visible on TV — no advanced gate) appears in its
        // place, so the TV count nets +1 over the touch/desktop one.
        assertEquals(
            4 + 8 + 1,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = true,
                showAdvanced = true,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // SCHEDULED mode reveals the start/end pair.
        assertEquals(
            4 + 9 + 2,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = true,
                themeMode = ThemeMode.SCHEDULED,
            ),
        )
        // The declared half is exactly the advanced-flag base minus the
        // content-gated exceptions and the theme_scheduler highlight-alias —
        // the strict ratchet keeps them in sync. (Fused conversion: the
        // exception set is 7 ContentGated rows + the alias. The form-factor
        // fork is a SWAP: under the default (non-TV) flags layout_mode's
        // All(Advanced, NotTv) admits and screen_fit's Tv drops; on TV it
        // swaps — either way exactly one of the pair drops, so the declared
        // half is 20 - 7 - 1 - 1 = 11 under advanced.)
        val declared = rowTotalFor(
            SettingsScreenGroups.appearanceTheme,
            RowAdmissionFlags(showAdvanced = true),
        )
        assertEquals(
            SettingsScreenGroups.appearanceTheme.items.size - 7 - 1 - 1,
            declared,
        )
        // On TV the same count: screen_fit admits and layout_mode drops.
        assertEquals(
            declared,
            rowTotalFor(
                SettingsScreenGroups.appearanceTheme,
                RowAdmissionFlags(showAdvanced = true, isTv = true),
            ),
        )
    }

    @Test
    fun `appearance expert group totals derive from their advanced declarations`() {
        // The three advanced-only groups only compose behind the advanced
        // structural block; their totals read the declared advanced base —
        // the whole declaration (the groups declare no other gates, per the
        // fully-covered exception pins above).
        assertEquals(
            SettingsScreenGroups.appearancePerformance.items.size,
            rowTotalFor(SettingsScreenGroups.appearancePerformance, RowAdmissionFlags(showAdvanced = true)),
        )
        assertEquals(
            SettingsScreenGroups.appearanceEyeCare.items.size,
            rowTotalFor(SettingsScreenGroups.appearanceEyeCare, RowAdmissionFlags(showAdvanced = true)),
        )
        // Newsletter: the declared trio minus the sections row (which renders
        // as the runtime-reorderable rows the screen counts explicitly).
        val sectionCount = 4
        assertEquals(
            SettingsScreenGroups.appearanceNewsletter.items.size - 1 + sectionCount,
            rowTotalFor(SettingsScreenGroups.appearanceNewsletter, RowAdmissionFlags(showAdvanced = true)) - 1 + sectionCount,
        )
    }

    @Test
    fun `security row total keeps the shipped count gates`() {
        // The pin row rides the app-lock capability; biometric rides the
        // platform+gate flag; the auto-lock timer rides both compounded with
        // the advanced toggle. pin_for_player_lock renders behind the pin
        // toggle but has never been admitted into the count — the preserved
        // quirk this pins (the strict derivation counts it as 0: it declares
        // no gate).
        fun securityTotal(canShowBiometric: Boolean, showAdvanced: Boolean, supportsAppLock: Boolean = true) =
            rowTotalFor(SettingsScreenGroups.security, RowAdmissionFlags(showAdvanced = showAdvanced, supportsBiometric = canShowBiometric, supportsAppLock = supportsAppLock))
        // The naive derivation, over the same declarations the screen reads:
        // exactly three of the group's rows ever enter the count — the pin
        // row behind the app-lock capability, the biometric row behind the
        // gate-aware biometric flag, and the auto-lock timer behind
        // All(Platform(AppLock), Advanced). The four ContentGated rows
        // (pin_for_player_lock, quick_connect_authorize,
        // remote_control_enabled, remote_display_content_enabled) declare no
        // admission and count nothing — the preserved shipped quirk.
        fun naiveSecurityTotal(showAdvanced: Boolean, supportsAppLock: Boolean, supportsBiometric: Boolean): Int =
            listOf(
                SecurityRows.PinLock.id to supportsAppLock, // Platform(AppLock)
                SecurityRows.BiometricLock.id to supportsBiometric, // Platform(Biometric)
                SecurityRows.AutoLockTimer.id to (supportsAppLock && showAdvanced), // All(Platform(AppLock), Advanced)
            ).count { it.second }
        assertEquals(naiveSecurityTotal(showAdvanced = false, supportsAppLock = true, supportsBiometric = false), securityTotal(canShowBiometric = false, showAdvanced = false))
        assertEquals(naiveSecurityTotal(showAdvanced = false, supportsAppLock = true, supportsBiometric = true), securityTotal(canShowBiometric = true, showAdvanced = false))
        assertEquals(naiveSecurityTotal(showAdvanced = true, supportsAppLock = true, supportsBiometric = false), securityTotal(canShowBiometric = false, showAdvanced = true))
        assertEquals(naiveSecurityTotal(showAdvanced = true, supportsAppLock = true, supportsBiometric = true), securityTotal(canShowBiometric = true, showAdvanced = true))
        // Desktop (supportsAppLock=false, and the biometric gate nulls there
        // so its computed flag is false too — the JVM actuals): no lock-group
        // row renders at all, the screen total collapses.
        assertEquals(naiveSecurityTotal(showAdvanced = false, supportsAppLock = false, supportsBiometric = false), securityTotal(canShowBiometric = false, showAdvanced = false, supportsAppLock = false))
        assertEquals(naiveSecurityTotal(showAdvanced = true, supportsAppLock = false, supportsBiometric = false), securityTotal(canShowBiometric = false, showAdvanced = true, supportsAppLock = false))
        // The biometric row rides ONLY the gate-aware biometric flag (the
        // screen's null-gate conjunct owns the no-hardware case): with the
        // flag forced true the row is admitted even with app-lock off — a
        // synthetic combination no binary ships (desktop nulls the gate,
        // Android sets both flags), so the derivation follows the flag.
        assertEquals(naiveSecurityTotal(showAdvanced = true, supportsAppLock = false, supportsBiometric = true), securityTotal(canShowBiometric = true, showAdvanced = true, supportsAppLock = false))
    }

    // ── 7. The search-result click action ───────────────────────────────

    @Test
    fun `search-result clicks decide the pure action per branch`() {
        // Both destructive dialogs, with the destructive ids kept out of recents.
        val logout = settingsResultClickAction(AccountRows.Logout.id, Route.Settings, isAdvanced = false, showAdvancedSettings = false)
        assertTrue(logout.action is SettingsSearchResultAction.OpenSignOutDialog && !logout.action.fromServer)
        assertFalse(logout.recordRecent)

        val signOutFromServer = settingsResultClickAction(
            AccountRows.SignOutFromServer.id, Route.Settings, isAdvanced = false, showAdvancedSettings = true,
        )
        assertTrue(signOutFromServer.action is SettingsSearchResultAction.OpenSignOutDialog && signOutFromServer.action.fromServer)
        assertFalse(signOutFromServer.recordRecent)

        // Bare-settings screensaver targets reveal their on-screen group: no
        // navigation, only the pending highlight.
        val screensaver = settingsResultClickAction(
            SystemRows.ScreensaverKenBurns.id, Route.Settings, isAdvanced = false, showAdvancedSettings = true,
        )
        assertTrue(screensaver.action is SettingsSearchResultAction.NoOp)
        assertEquals(SystemRows.ScreensaverKenBurns.id, screensaver.pendingHighlightId)
        assertTrue(screensaver.recordRecent)

        // The setup wizard keeps its host indirection.
        val wizard = settingsResultClickAction(SystemRows.SetupWizard.id, Route.Onboarding, isAdvanced = false, showAdvancedSettings = true)
        assertTrue(wizard.action is SettingsSearchResultAction.OpenSetupWizard)
        assertEquals(SystemRows.SetupWizard.id, wizard.pendingHighlightId)

        // Regular sub-screen entries navigate with the id baked in and mark
        // the pending highlight — except the two management exemptions.
        val theme = settingsResultClickAction(AppearanceRows.ThemeMode.id, Route.AppearanceSettings(), isAdvanced = false, showAdvancedSettings = true)
        val themeAction = theme.action as SettingsSearchResultAction.NavigateToScreen
        assertEquals(Route.AppearanceSettings(AppearanceRows.ThemeMode.id), themeAction.route)
        assertEquals(AppearanceRows.ThemeMode.id, theme.pendingHighlightId)

        val server = settingsResultClickAction(AccountRows.ServerManagement.id, Route.ServerManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(server.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(server.pendingHighlightId, "server_management must not mark the TV re-entry highlight")

        val users = settingsResultClickAction(AccountRows.UserManagement.id, Route.UserManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(users.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(users.pendingHighlightId, "user_management must not mark the TV re-entry highlight")
    }

    @Test
    fun `advanced result clicks auto-enable advanced settings`() {
        val off = settingsResultClickAction(AudioRows.Equalizer.id, Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = false)
        assertTrue(off.enableAdvanced)
        assertTrue(off.action is SettingsSearchResultAction.NavigateToScreen)

        val on = settingsResultClickAction(AudioRows.Equalizer.id, Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = true)
        assertFalse(on.enableAdvanced)
    }
}
