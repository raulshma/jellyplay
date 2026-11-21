package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The catalog ↔ screen contract (the drift guard) for the single-sourced row
 * ids ("rows own their identity"): every
 * settings row id is declared ONCE — as the `val id` of its fused
 * [SettingsRow] beside the `*Rows` declarations (every domain but the
 * experimental screen), or (the one hold-out) as a `const val` in the
 * experimental screen's ids holder beside its binding-derived
 * `ExperimentalSettingsSearchItems`. The search-item declarations, the
 * screens' `highlighted` comparisons, the gates and the row-total
 * derivations all reference those single sources, so the screen-row ↔
 * catalog pairing is pinned by compilation instead: a row's comparison and
 * the catalog item literally share one declaration.
 *
 * What still needs a runtime test — and lives here — is the REFERENTIAL
 * integrity the compiler cannot see: every id must resolve to exactly one
 * catalog item (a row that highlights nothing — the `vlc_video_output` class
 * of bug), and every catalog id must come from a single source (a declaration
 * that bypasses the fused rows or the experimental holder, or an orphan
 * highlight target). On top of that sit the behavioral pins: the fixed
 * drifts' scroll behavior, the aggregation-decoration splits, the declared
 * row admissions and the per-group totals. The four known drift shapes each
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
 * row-catalog guards themselves live in `FusedRowsRatchetTest`.
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
        PerformanceRows.all,
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
        JellyPlaySyncRows.all,
        JellyPlayFeatureRows.all,
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
        // above, fails here. The card items are the second declared source:
        // a group-card hit's id IS its group's id (pinned by
        // [groupCardItems] below) — never a row id.
        val singleSourceIds = allHolderIds.toSet() + allFusedIds.toSet()
        val cardIds = SettingsScreenGroups.all.mapNotNull { it.cardItem?.id }.toSet()
        val undeclared = SettingsSearchCatalog.items.map { it.id }.filter { it !in singleSourceIds && it !in cardIds }
        assertEquals(emptyList(), undeclared, "catalog ids declared outside the single sources (fused rows + experimental holder + group cards)")

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
            SettingsScreenGroups.all.flatMap { it.items + listOfNotNull(it.cardItem) }.map { it.id },
            SettingsSearchCatalog.items.map { it.id },
            "catalog is not the flat concatenation of the decorated groups (+ their card items)",
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
     * The strict derivation's coverage guard: [rowTotalFor] counts nothing
     * for an id that declares no gate, so every group a screen feeds a
     * `SettingsItemList` total from must declare EVERY id's admission — the
     * listed exceptions are the deliberately-undeclared rows (screen-local or
     * content-gated rows the screens add explicitly, each commented at its
     * declaration). The one remaining size-derived exception
     * (storage.network) is pinned separately below — its screen feeds the
     * declaration size itself, so the per-id guard cannot apply.
     */
    @Test
    fun `groups the screens feed totals from declare every id's admission`() {
        val undeclaredExceptions = mapOf(
            "audio" to emptySet<String>(),
            // The playback hub's entrance rows and the three former
            // playback.player leaves: every declared row carries a gate the
            // strict derivation reads (the entrances declare Always).
            "playback.hub" to emptySet(),
            "playback.basics" to emptySet(),
            "playback.controls" to emptySet(),
            "playback.playerChrome" to emptySet(),
            "playback.advancedVideo" to emptySet(),
            "playback.engine" to emptySet(),
            "notifications" to emptySet(),
            "language.general" to emptySet(),
            "language.trackSelection" to emptySet(),
            "language.subtitles" to emptySet(),
            "storage.cache" to emptySet(),
            // The phase-5 reclassification: the three new advanced trailing
            // groups declare their advanced bases and only compose behind
            // the advanced structural block — every id fully covered.
            "appearance.detailTuning" to emptySet(),
            "storage.networkTuning" to emptySet(),
            "audio.fineTuning" to emptySet(),
            // The auto-arm row is DECLARED RowAdmission.ContentGated (its
            // emission rides the experimental Direct *arr flag the screen
            // reads through the VM), so the derived admissions carry no entry
            // for it — the pin list must be the literal rows, and the screen
            // adds the count term explicitly.
            "storage.downloads" to setOf(StorageRows.AutoArmFromArr.id),
            // The count quirks: pin_for_player_lock renders behind the pin
            // toggle but is not admitted into the count, and the quick-connect /
            // remote-control / remote-display rows render in their own
            // single-row groups outside the lock-group total.
            "security" to setOf(
                SecurityRows.PinForPlayerLock.id,
                SecurityRows.QuickConnectAuthorize.id,
                SecurityRows.RemoteControlEnabled.id,
                SecurityRows.RemoteDisplayContentEnabled.id,
            ),
            // ADR-0017's home split: the hub's entrance rows (declare Always)
            // and the advanced Fine-tuning group (the declared hub exception —
            // every row renders behind the shell's whole-group gate) are
            // fully covered. The display-main leaf renders every declared row
            // unconditionally except the slideshow-interval row's WhenOn
            // parent (flags-expressible). The curation group's unhide row
            // rides hidden-CW content state — no admission vocabulary; the
            // screen adds the +1 explicitly.
            "home.hub" to emptySet(),
            "home.tuning" to emptySet(),
            "home.displayMain" to emptySet(),
            "home.curation" to setOf(HomeRows.UnhideCw.id),
            "home.nextUp" to emptySet(),
            "home.layout" to emptySet(),
            // The cards group renders every declared row unconditionally —
            // declared Always per id, fully covered.
            "home.cards" to emptySet(),
            // The theme group's content-gated rows: their emission depends on
            // theme state the admission vocabulary does not carry (variant
            // accent support, standard-vs-themed branch, active-dark + OLED
            // allowance, the SCHEDULED theme mode). They are DECLARED
            // RowAdmission.ContentGated on their fused rows, so the derived
            // admissions carry no entry for them (the strict derivation shape:
            // the pin list must be the literal rows — a row switching to
            // ContentGated without being pinned here fails). The rows'
            // content terms are added explicitly by
            // appearanceThemeScreenRowTotal. theme_scheduler is the
            // highlight-alias row (its screen face is the theme_mode row via
            // THEME_HIGHLIGHT_IDS) — ContentGated and counted by no total,
            // the quirk shape. (layout_mode is not an exception: its
            // All(Advanced, NotTv) gate is flags-expressible and derives with
            // the advanced base.)
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
            // The appearance hub's entrance rows (declare Always) and the
            // former "Library & Cards" split (every declared row renders
            // unconditionally — its shipped always-on behavior): fully
            // covered; the screen-local confirm-library-reset action row and
            // the section-order editor's reorderable rows are the explicit
            // +1/+10 terms at their call sites.
            "appearance.hub" to emptySet(),
            "appearance.libraryViews" to emptySet(),
            "appearance.detail" to emptySet(),
            // The three advanced-only groups declare their advanced base and
            // only compose behind the advanced structural block.
            "appearance.performance" to emptySet(),
            "appearance.eyeCare" to emptySet(),
            // newsletter_sections' declared row renders as the runtime-
            // reorderable rows (the enum-driven media-segment shape) — the
            // screen's total replaces its admitted slot with the runtime
            // count explicitly.
            "appearance.newsletter" to emptySet(),
            // The Performance hub's five groups: the level group's master row
            // declares Always (fully covered), the four expert groups declare
            // their advanced base and only compose behind the advanced
            // structural block. The screens' screen-local cross-reference
            // rows are the explicit +1/+2 terms at their call sites.
            "performance.level" to emptySet(),
            "performance.motion" to emptySet(),
            "performance.images" to emptySet(),
            "performance.ambient" to emptySet(),
            "performance.buffers" to emptySet(),
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
        // guard above cannot apply; pin instead that the group admits ALL
        // seven rows under the default flags — the user_data_sync row keeps
        // its explicit [RowAdmission.Always] gate (the shipped quirk), so the
        // derived admissions agree with the screen's unconditional size. (The
        // phase-5 reclassification moved the four diagnostics rows to the
        // storage.networkTuning group — the eleven became seven.)
        val sizeDerivedGroups = mapOf(
            "storage.network" to 7,
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
        // ADR-0017: the touch fork split across the leaves — the seek
        // duration row landed on Basics, the gesture set on Controls.
        listOf(
            SettingsScreenGroups.playbackBasics to PlaybackRows.SeekDuration.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.Gestures.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.GestureIndicatorSide.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.DoubleTapHoldSeek.id,
        ).forEach { (group, id) ->
            assertFalse(group.rowAdmitted(id, desktop), "$id must drop without its capability")
        }
        assertFalse(
            SettingsScreenGroups.playbackControls.rowAdmitted(PlaybackRows.Orientation.id, desktop),
            "the orientation row must drop without its capability",
        )
        val touch = RowAdmissionFlags(supportsTouchGestures = true)
        listOf(
            SettingsScreenGroups.playbackBasics to PlaybackRows.SeekDuration.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.Gestures.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.GestureIndicatorSide.id,
            SettingsScreenGroups.playbackControls to PlaybackRows.DoubleTapHoldSeek.id,
        ).forEach { (group, it) ->
            assertTrue(group.rowAdmitted(it, touch), "$it must admit on the capability")
        }
        assertTrue(
            SettingsScreenGroups.playbackControls.rowAdmitted(
                PlaybackRows.Orientation.id,
                RowAdmissionFlags(supportsScreenOrientation = true),
            ),
        )
        // the volume-memory toggle is DESKTOP-backed — it admits on
        // this JVM's default seam flags (the desktop binary backs the row)
        // and drops where the capability is off (Android).
        assertTrue(
            SettingsScreenGroups.playbackBasics.rowAdmitted(
                PlaybackRows.RememberVolumePerContentType.id,
                desktop,
            ),
            "the volume-memory row admits on the desktop seam",
        )
        assertFalse(
            SettingsScreenGroups.playbackBasics.rowAdmitted(
                PlaybackRows.RememberVolumePerContentType.id,
                RowAdmissionFlags(supportsVolumeMemory = false),
            ),
            "the volume-memory row must drop without its capability",
        )
    }

    @Test
    fun `tv rows admit on the tv form factor alone - the shipped count semantics`() {
        // ADR-0017: the TV pair split — the watch-next row on Basics, the
        // overscan zoom beside the chrome knobs. The zoom row declares
        // gate = Tv alone (its total admits on isTv; its emission sits inside
        // the chrome leaf's advanced block, which carries the advanced half).
        // The watch-next row declares the FULL gate its emission reads —
        // All(Advanced, Tv) — so the basics total does not count it while
        // advanced settings are off.
        val tv = RowAdmissionFlags(isTv = true)
        val tvAdvanced = RowAdmissionFlags(isTv = true, showAdvanced = true)
        assertTrue(
            SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, tvAdvanced),
            "watch-next admits on TV with advanced settings on",
        )
        assertFalse(
            SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, tv),
            "watch-next is not counted while advanced settings are off",
        )
        assertFalse(
            SettingsScreenGroups.playbackBasics.rowAdmitted(PlaybackRows.AndroidTvWatchNext.id, RowAdmissionFlags(showAdvanced = true)),
            "watch-next stays excluded on non-TV devices",
        )
        assertTrue(SettingsScreenGroups.playbackPlayerChrome.rowAdmitted(PlaybackRows.TvZoomMode.id, tv))
        assertFalse(SettingsScreenGroups.playbackPlayerChrome.rowAdmitted(PlaybackRows.TvZoomMode.id, RowAdmissionFlags(showAdvanced = true)))
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
        val autoDownloadOn = RowAdmissionFlags(parentsOn = setOf(StorageRows.AutoDownloadMode.id))
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
            parentsOn = setOf(StorageRows.AutoDownloadMode.id, StorageRows.AutoDownloadKeepDays.id),
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
        // AUDIO_PASSTHROUGH pair dropping out of the wiring makes the five
        // per-codec passthrough rows invisible — this guard turns that drift
        // into a loud test failure instead of a blank stretch of settings
        // screen.
        val everyParentOn = playbackRowAdmissionFlags(
            isTv = false,
            showAdvanced = true,
            preferences = PlaybackPreferences(
                dialogueBoostEnabled = true,
                videoAutoplayNext = true,
                audioPassthrough = true,
                spatialAudioEnabled = true,
                pauseScreensaverMode = PauseScreensaverMode.CLOCK,
            ),
        )
        val declaredParents = listOf(
            SettingsScreenGroups.playbackHub,
            SettingsScreenGroups.playbackBasics,
            SettingsScreenGroups.playbackControls,
            SettingsScreenGroups.playbackPlayerChrome,
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

    /**
     * The playback leaves' sections in LazyColumn order, per screen
     * (ADR-0017: the former one seven-group screen became the hub + eight
     * leaves, so the deep-link scroll pins run per leaf against the same
     * [deriveAdjustForAdvanced] fold the shell computes).
     */
    private val videoQualitySections = listOf(
        SettingsScreenSection(SettingsScreenGroups.playbackAdvancedVideo.itemIdSet, advancedOnly = true),
    )

    private val videoQualityGroups = videoQualitySections.map { it.idSet }

    private val engineSections = listOf(
        SettingsScreenSection(SettingsScreenGroups.playbackEngine.itemIdSet, advancedOnly = true),
    )

    private val engineGroups = engineSections.map { it.idSet }

    private val syncPlayCastingSections = listOf(
        SettingsScreenSection(SettingsScreenGroups.playbackSyncPlay.itemIdSet),
        SettingsScreenSection(SettingsScreenGroups.playbackCasting.itemIdSet),
    )

    private val syncPlayCastingGroups = syncPlayCastingSections.map { it.idSet }

    @Test
    fun `vlc_video_output deep-links into the engine leaf`() {
        assertTrue(PlaybackRows.VlcVideoOutput.id in SettingsScreenGroups.playbackEngine.itemIdSet)
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.VlcVideoOutput.id, engineGroups, deriveAdjustForAdvanced(engineSections, true)),
        )
        // With advanced hidden the engine group doesn't compose — no scroll.
        assertEquals(
            -1,
            resolveHighlightScrollIndex(PlaybackRows.VlcVideoOutput.id, engineGroups, deriveAdjustForAdvanced(engineSections, false)),
        )
    }

    @Test
    fun `live_stream_option and dialogue_boost_strength land in the advanced-video leaf`() {
        val advanced = SettingsScreenGroups.playbackAdvancedVideo.itemIdSet
        assertTrue(PlaybackRows.LiveStreamOption.id in advanced)
        assertTrue(PlaybackRows.DialogueBoostStrength.id in advanced)
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.LiveStreamOption.id, videoQualityGroups, deriveAdjustForAdvanced(videoQualitySections, true)),
        )
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.DialogueBoostStrength.id, videoQualityGroups, deriveAdjustForAdvanced(videoQualitySections, true)),
        )
        // With advanced hidden the advanced-only group doesn't compose.
        assertEquals(
            -1,
            resolveHighlightScrollIndex(PlaybackRows.LiveStreamOption.id, videoQualityGroups, deriveAdjustForAdvanced(videoQualitySections, false)),
        )
    }

    @Test
    fun `auto_delete_after_watch is declared in the downloads group with a screen row`() {
        assertTrue(StorageRows.AutoDeleteAfterWatch.id in SettingsScreenGroups.storageDownloads.itemIdSet)
        // The row itself shares the same declaration (compile-paired).
        assertTrue(StorageRows.AutoDeleteAfterWatch.id in allFusedIds)
    }

    @Test
    fun `gesture_indicator_side scrolls within the controls leaf`() {
        // The derivation keeps this row reachable in the controls scroll
        // list.
        assertEquals(
            0,
            resolveHighlightScrollIndex(
                PlaybackRows.GestureIndicatorSide.id,
                SettingsScreenGroups.playbackControls.itemIdSet.let(::listOf),
            ),
        )
    }

    @Test
    fun `the syncplay-and-casting leaf's groups scroll in declaration order`() {
        // The leaf's two always-visible groups render in declaration order;
        // the DVR trio deep-links its own leaf.
        assertEquals(
            0,
            resolveHighlightScrollIndex(PlaybackRows.SyncplayJoinBehavior.id, syncPlayCastingGroups, deriveAdjustForAdvanced(syncPlayCastingSections, false)),
        )
        assertEquals(
            1,
            resolveHighlightScrollIndex(PlaybackRows.BackgroundCasting.id, syncPlayCastingGroups, deriveAdjustForAdvanced(syncPlayCastingSections, false)),
        )
        assertEquals(
            0,
            resolveHighlightScrollIndex(
                PlaybackRows.DvrRecordingQuality.id,
                SettingsScreenGroups.playbackDvr.itemIdSet.let(::listOf),
            ),
        )
        // The basics group never moves.
        assertEquals(
            0,
            resolveHighlightScrollIndex(
                PlaybackRows.PlayerEngine.id,
                SettingsScreenGroups.playbackBasics.itemIdSet.let(::listOf),
            ),
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
        // group's skip-on-seek toggle, skip-prompt-duration row and the
        // Next Up preroll row are the three non-enum members — hand-built
        // rows like the per-type rows.
        val enumSegmentIds = MediaSegmentType.entries.map { "media_segment_${it.name.lowercase()}" }
        assertEquals(
            enumSegmentIds + PlaybackRows.SkipSegmentsOnSeek.id + PlaybackRows.SkipPromptDuration.id +
                PlaybackRows.NextUpPreroll.id,
            SettingsScreenGroups.playbackMediaSegments.itemIds,
            "media-segment group declarations drifted from MediaSegmentType.entries + the hand-built transport rows",
        )
        assertTrue(
            SettingsScreenGroups.playbackDvr.itemIds.none {
                it.startsWith(SettingsScreenGroups.MEDIA_SEGMENT_ID_PREFIX) || it == PlaybackRows.SkipSegmentsOnSeek.id ||
                    it == PlaybackRows.SkipPromptDuration.id || it == PlaybackRows.NextUpPreroll.id
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
            LanguageSettingsSearchItems.size - LanguageGeneralRows.size,
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

        // Network: unconditionally the whole visible declaration (the screen
        // feeds `SettingsScreenGroups.storageNetwork.items.size` directly —
        // no conditional, so no admission function). The four diagnostics
        // rows moved to the advanced networkTuning group (the phase-5
        // reclassification).
        assertEquals(7, SettingsScreenGroups.storageNetwork.items.size, "the storage.network declaration changed — update this pin")
        assertEquals(
            SettingsScreenGroups.storageNetworkTuning.items.size,
            rowTotalFor(SettingsScreenGroups.storageNetworkTuning, RowAdmissionFlags(showAdvanced = true)),
            "the diagnostics group grew a non-advanced row",
        )
        assertEquals(
            0,
            rowTotalFor(SettingsScreenGroups.storageNetworkTuning, RowAdmissionFlags(showAdvanced = false)),
            "the diagnostics group must stay wholly advanced",
        )

        // Downloads: the download_schedule_* window rows only when
        // scheduling is on (their declared WhenOn gate); the auto-download
        // retention rows (check-now included) only when the policy mode is
        // not NEVER; clean-up-now only when the keep-days window is set;
        // every other record renders unconditionally (its non-advanced base).
        // The auto-arm row is ContentGated (the experimental Direct *arr
        // flag) — counted by no total, so every pin below subtracts its
        // exception list too.
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size -
                StorageDownloadScheduleWindowRows.size -
                StorageAutoDownloadRetentionRows.size -
                StorageAutoDownloadCleanUpNowRows.size -
                StorageAutoArmContentGatedRows.size,
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to false)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size -
                StorageAutoDownloadRetentionRows.size -
                StorageAutoDownloadCleanUpNowRows.size -
                StorageAutoArmContentGatedRows.size,
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - // minus every parent-gated row:
                StorageDownloadScheduleWindowRows.size - // the schedule windows
                StorageAutoDownloadRetentionRows.size - // the auto-download retention rows
                StorageAutoDownloadCleanUpNowRows.size - // the clean-up action
                StorageAutoArmContentGatedRows.size, // the ContentGated auto-arm row
            rowTotalFor(SettingsScreenGroups.storageDownloads, RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to false))),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - // minus the retention rows and the clean-up action:
                StorageAutoDownloadRetentionRows.size -
                StorageAutoDownloadCleanUpNowRows.size -
                StorageAutoArmContentGatedRows.size,
            rowTotalFor(SettingsScreenGroups.storageDownloads, RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true))),
        )
        // The auto-download retention cluster: the retention rows ride the
        // mode picker, and clean-up-now additionally rides the
        // keep-days picker being non-zero — with the schedule and
        // auto-download windows on, only the clean-up action stays gated
        // off (and the ContentGated auto-arm row is counted by no total
        // under every evaluation); turning the keep-days window on admits
        // every counted row.
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size -
                StorageAutoDownloadCleanUpNowRows.size -
                StorageAutoArmContentGatedRows.size, // minus the clean-up action (the keep-days window is still off)
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(parentsOn = rowParentsOn(StorageRows.DownloadSchedule.id to true, StorageRows.AutoDownloadMode.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.storageDownloads.items.size - StorageAutoArmContentGatedRows.size, // every counted row admits; the ContentGated auto-arm row never does
            rowTotalFor(
                SettingsScreenGroups.storageDownloads,
                RowAdmissionFlags(
                    parentsOn = rowParentsOn(
                        StorageRows.DownloadSchedule.id to true,
                        StorageRows.AutoDownloadMode.id to true,
                        StorageRows.AutoDownloadKeepDays.id to true,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `playback row totals track the leaves advanced-video and engine declarations`() {
        // Basics: the nine unconditional rows (player_engine, default_speed,
        // default_aspect, video_autoplay_next, autoplay_countdown,
        // autoplay_episode_limit, session_restore, next_episode_details, the
        // desktop volume-memory toggle) survive every gate off; each flag
        // reveals exactly its own rows.
        val allGatesOff = rowTotalFor(
            SettingsScreenGroups.playbackBasics,
            RowAdmissionFlags(
                isTv = false,
                showAdvanced = false,
                supportsScreenOrientation = false,
                supportsTouchGestures = false,
            ),
        )
        // Naive derivation: with every gate off, the non-advanced
        // declarations survive EXCEPT the Platform-gated rows (the touch
        // fork's seek-duration row — the gesture fork lives on Controls —
        // both capabilities are off here) and the WhenOn(autoplay)
        // still-watching rows (no parents on). The desktop-backed
        // volume-memory toggle (this JVM's seam) stays in.
        assertEquals(
            SettingsScreenGroups.playbackBasics.items.count { !it.isAdvanced } -
                PlaybackBasicsTouchRows.size -
                PlaybackBasicsStillWatchingRows.size,
            allGatesOff,
        )
        assertEquals(
            // the touch capability reveals exactly its own row
            SettingsScreenGroups.playbackBasics.items.count { !it.isAdvanced } -
                PlaybackBasicsStillWatchingRows.size,
            rowTotalFor(
                SettingsScreenGroups.playbackBasics,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = false,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )
        assertEquals(
            // Advanced mode reveals the advanced declarations EXCEPT the
            // parent-gated and capability-gated rows: the WhenOn(autoplay)
            // still-watching pair, the WhenOn(pause_screensaver) delay row,
            // the Tv watch-next row (no TV here) and the Platform(Pip)
            // auto-PiP row (off on this desktop JVM's seam).
            SettingsScreenGroups.playbackBasics.items.size - // minus the still-dropped exception rows:
                PlaybackBasicsStillWatchingRows.size -
                PlaybackBasicsPauseScreensaverDelayRows.size -
                PlaybackBasicsTvRows.size -
                PlaybackBasicsAutoPipRows.size,
            rowTotalFor(
                SettingsScreenGroups.playbackBasics,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = true,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )
        // On TV the watch-next row comes back (it rides isTv alone — shipped
        // semantics, also without advanced mode); every other advanced
        // declaration admits under advanced. The WhenOn(autoplay)
        // still-watching rows (no parents on), the WhenOn(pause_screensaver)
        // delay row (no parents on), and the Platform(Pip) auto-PiP row (off
        // on this desktop JVM's seam — TV does not back it) drop.
        assertEquals(
            SettingsScreenGroups.playbackBasics.items.size - // minus the still-dropped exception rows:
                PlaybackBasicsStillWatchingRows.size -
                PlaybackBasicsPauseScreensaverDelayRows.size -
                PlaybackBasicsAutoPipRows.size,
            rowTotalFor(
                SettingsScreenGroups.playbackBasics,
                RowAdmissionFlags(
                    isTv = true,
                    showAdvanced = true,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )

        // Controls: the six always rows (the Always-admitted input-bindings
        // editor row included — every platform has some input surface) plus
        // the capability forks; the four advanced rows behind the toggle.
        val controlsAllGatesOff = rowTotalFor(
            SettingsScreenGroups.playbackControls,
            RowAdmissionFlags(
                isTv = false,
                showAdvanced = false,
                supportsScreenOrientation = false,
                supportsTouchGestures = false,
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackControls.items.count { !it.isAdvanced } -
                PlaybackControlsOrientationRows.size -
                PlaybackControlsTouchRows.size,
            controlsAllGatesOff,
        )
        // With both capabilities forced on every declared row admits: the
        // six always rows plus the four advanced rows behind the toggle.
        assertEquals(
            SettingsScreenGroups.playbackControls.items.size,
            rowTotalFor(
                SettingsScreenGroups.playbackControls,
                RowAdmissionFlags(
                    isTv = false,
                    showAdvanced = true,
                    supportsScreenOrientation = true,
                    supportsTouchGestures = true,
                ),
            ),
        )

        // Chrome: every row is advanced (the group composes only behind the
        // toggle); off-TV the Tv zoom drops.
        assertEquals(
            0,
            rowTotalFor(
                SettingsScreenGroups.playbackPlayerChrome,
                RowAdmissionFlags(isTv = false, showAdvanced = false),
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayerChrome.items.size - PlaybackChromeTvRows.size,
            rowTotalFor(
                SettingsScreenGroups.playbackPlayerChrome,
                RowAdmissionFlags(isTv = false, showAdvanced = true),
            ),
        )
        assertEquals(
            SettingsScreenGroups.playbackPlayerChrome.items.size,
            rowTotalFor(
                SettingsScreenGroups.playbackPlayerChrome,
                RowAdmissionFlags(isTv = true, showAdvanced = true),
            ),
        )

        // Advanced video: the whole group only composes behind the advanced
        // toggle (its declared Advanced base — the flags carry that toggle);
        // the parent-gated rows (the dialogue-boost strength row, the
        // per-codec passthrough rows, the spatial headphones sub-knob and
        // the issue #186 Re-detect action row riding the profile row's AUTO
        // state) additionally ride their parent toggles (their declared All
        // gates) — with no parents on, they all drop.
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size -
                PlaybackAdvancedVideoParentGatedRows.size,
            rowTotalFor(SettingsScreenGroups.playbackAdvancedVideo, RowAdmissionFlags(showAdvanced = true)),
        )
        val boostOn = RowAdmissionFlags(
            showAdvanced = true,
            parentsOn = rowParentsOn(
                PlaybackRows.DialogueBoost.id to true,
                PlaybackRows.AudioPassthrough.id to true,
                PlaybackRows.SpatialAudio.id to true,
                // The Re-detect row's parent: the profile being AUTO.
                PlaybackRows.VideoPerformanceProfile.id to true,
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
            SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith("mpv_") } - // both capabilities off — the mpv branch falls back to its base row set:
                PlaybackEngineMpvAudioDeviceRows.size - // the audio-device trio
                PlaybackEngineMpvRenderRows.size + // the render-profile rows (mpv_hdr_passthrough left the render-profile gate for the plain advanced gate when passthrough went cross-host)
                1, // +1: the reset row
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
        // five toggle rows ride it; the five advanced rows
        // (respect_system_dnd, quiet_hours, max_per_check,
        // notification_libraries, notification_series — the per-series
        // management entry) additionally ride the advanced toggle; the
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
            NotificationRows.NotificationSeries.id,
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
        ) // + quiet hours/dnd/max-per-check/libraries/series
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
            SettingsScreenGroups.languageGeneral.items.size - LanguageGeneralLocaleGatedRows.size, // minus the app-language row behind its AppLocaleOverride capability (off in these flags)
            rowTotalFor(SettingsScreenGroups.languageGeneral, RowAdmissionFlags(supportsAppLocaleOverride = false)),
        )
        assertEquals(
            SettingsScreenGroups.languageGeneral.items.size,
            rowTotalFor(SettingsScreenGroups.languageGeneral, RowAdmissionFlags(supportsAppLocaleOverride = true)),
        )

        // Subtitles: the non-advanced rows plus the always-shown
        // high-contrast row (declared advanced yet never gated — the shipped
        // quirk); the style rows behind advanced; the HDR font-size row
        // behind the HDR toggle.
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.count { !it.isAdvanced } + LanguageSubtitlesAlwaysShownRows.size,
            rowTotalFor(
                SettingsScreenGroups.languageSubtitles,
                RowAdmissionFlags(showAdvanced = false, parentsOn = rowParentsOn(LanguageRows.HdrSubtitleStyle.id to true)),
            ),
        )
        assertEquals(
            SettingsScreenGroups.languageSubtitles.items.size - LanguageSubtitlesParentGatedRows.size, // minus the HDR font-size row behind its WhenOn(HDR style) parent (no parents on in these flags)
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
    fun `home leaf row totals track the split declarations`() {
        // ADR-0017's home split: the display-main leaf's count follows its
        // emitted rows — the declared rows minus the hero-slideshow interval
        // row while its WhenOn (home_hero_style = slideshow) parent is off.
        val displayRowsWithoutGatedRow = SettingsScreenGroups.homeDisplayMain.items.count {
            it.id != HomeRows.HomeHeroSlideshowInterval.id
        }
        assertEquals(displayRowsWithoutGatedRow, homeDisplayMainScreenRowTotal(heroSlideshowOn = false))
        assertEquals(
            displayRowsWithoutGatedRow + 1, // +1: the slideshow interval row, its parent on
            homeDisplayMainScreenRowTotal(heroSlideshowOn = true),
        )
        assertEquals(
            SettingsScreenGroups.homeDisplayMain.items.size - HomeDisplayMainGatedRows.size,
            homeDisplayMainScreenRowTotal(heroSlideshowOn = false),
            "the display-main declaration changed — update this pin",
        )
        assertEquals(
            SettingsScreenGroups.homeDisplayMain.itemIds.last(),
            HomeRows.ContinueWatchingClick.id,
            "the display-main declaration's catalog order drifted",
        )

        // The curation group: the declared rows (the strict derivation
        // excludes the ContentGated unhide row) plus the conditional unhide
        // row (the content-gated +1 once hidden CW items exist).
        assertEquals(
            SettingsScreenGroups.homeCuration.items.size - HomeCurationContentGatedRows.size,
            homeCurationScreenRowTotal(hiddenCwItems = 0),
        )
        assertEquals(
            SettingsScreenGroups.homeCuration.items.size - HomeCurationContentGatedRows.size + 1,
            homeCurationScreenRowTotal(hiddenCwItems = 1),
        )
        assertEquals(
            SettingsScreenGroups.homeCuration.items.size - HomeCurationContentGatedRows.size,
            homeCurationScreenRowTotal(hiddenCwItems = 0),
            "the curation declaration changed — update this pin",
        )
        // The unhide row is declared last, so the derived emission order
        // keeps it at the end.
        assertEquals(HomeRows.UnhideCw.id, SettingsScreenGroups.homeCuration.itemIds.last())
        // …and it admits for emission (rowAdmitted's ContentGated default) —
        // the screen's content-state `if` carries the gate.
        assertTrue(SettingsScreenGroups.homeCuration.rowAdmitted(HomeRows.UnhideCw.id, RowAdmissionFlags()))

        // The hub's Fine-tuning group: the whole declared tuning set — the
        // slot only composes behind the advanced toggle, so the total reads
        // the advanced leg.
        assertEquals(
            SettingsScreenGroups.homeTuning.items.size,
            homeTuningScreenRowTotal(),
        )
    }

    @Test
    fun `appearance library row totals are the declarations plus their screen-local terms`() {
        // Every declared row renders unconditionally (its declared Always —
        // the shipped always-on set). ADR-0017's split: the Library views
        // leaf adds the screen-local confirm-library-reset action row (+1);
        // the Detail screen leaf adds the section-order editor's reorderable
        // rows (+10).
        assertEquals(
            SettingsScreenGroups.appearanceLibraryViews.items.size,
            rowTotalFor(SettingsScreenGroups.appearanceLibraryViews, RowAdmissionFlags()),
        )
        assertEquals(
            SettingsScreenGroups.appearanceLibraryViews.items.size + 1,
            appearanceLibraryViewsScreenRowTotal(),
        )
        assertEquals(
            SettingsScreenGroups.appearanceDetail.items.size,
            rowTotalFor(SettingsScreenGroups.appearanceDetail, RowAdmissionFlags()),
        )
        assertEquals(
            SettingsScreenGroups.appearanceDetail.items.size + DETAIL_SECTION_ORDER_EDITOR_ROWS,
            appearanceDetailScreenRowTotal(),
        )
    }

    @Test
    fun `home cards row total is the card-display toggles with the TV-gated trickplay fork`() {
        // The card-display toggles, the badge/spoiler/trickplay cluster, the
        // QoL wave-3 badge-window/counter rows and the card-chrome trio
        // (ADR-0017's split moved scale/radius/animations off the former
        // display group): every row renders unconditionally on the Cards &
        // badges leaf except the two TV-only trickplay rows (the preview
        // toggle and the peek hold — both declared RowAdmission.Tv), so the
        // strict derivation counts all fifteen on TV and all but the
        // trickplay pair off it.
        assertEquals(15, SettingsScreenGroups.homeCards.items.size, "the cards declaration changed — update this pin")
        assertEquals(
            SettingsScreenGroups.homeCards.items.size,
            homeCardsScreenRowTotal(isTv = true),
        )
        assertEquals(
            SettingsScreenGroups.homeCards.items.size - HomeCardsTvOnlyRows.size,
            homeCardsScreenRowTotal(isTv = false),
        )
        assertTrue(
            SettingsScreenGroups.homeCards.items.all { !it.isAdvanced },
            "the cards rows must stay always-on",
        )
        // The trickplay pair is the TV fork.
        assertEquals(
            listOf(HomeRows.TrickplayPreview.id, HomeRows.HomeTrickplayPeekMs.id),
            SettingsScreenGroups.homeCards.items
                .filter { SettingsScreenGroups.homeCards.admissionOf(it.id) == RowAdmission.Tv }
                .map { it.id },
        )
    }

    @Test
    fun `appearance theme row total tracks the declaration and its content gates`() {
        val standard = ThemeVariant.STANDARD
        val themed = ThemeVariant.SYNTHWAVE // carries accent options + dark-locked
        // The declared-half derivations, over the same declarations the
        // screen reads: the always-shown pair under flags-off, and the
        // advanced rows the toggle reveals (the flags-expressible fork:
        // layout_mode in and the TV pair out on touch/desktop, inverted on
        // TV).
        val flagsOffDeclared = rowTotalFor(SettingsScreenGroups.appearanceTheme, RowAdmissionFlags())
        val advancedRevealedNonTv =
            rowTotalFor(SettingsScreenGroups.appearanceTheme, RowAdmissionFlags(showAdvanced = true)) - flagsOffDeclared
        val advancedRevealedTv =
            rowTotalFor(SettingsScreenGroups.appearanceTheme, RowAdmissionFlags(showAdvanced = true, isTv = true)) - flagsOffDeclared
        val standardBranchContent = AppearanceThemeStandardBranchRows.size
        // Standard variant, gates off: theme_mode + theme_style (the
        // flags-off declared half), plus the standard-branch accent_color +
        // color_style content term.
        assertEquals(
            flagsOffDeclared + standardBranchContent,
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
        // The advanced toggle reveals the declared advanced rows (the
        // flags-expressible fork's non-TV set: contrast, library_view_mode,
        // layout_mode, theme_music, nav_labels, date_format, font_scale,
        // color_blind_mode, hand_mode) plus the two content terms the
        // standard branch added.
        assertEquals(
            flagsOffDeclared + standardBranchContent + advancedRevealedNonTv,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = true,
                themeMode = ThemeMode.SYSTEM,
            ),
        )
        // The form-factor fork: layout_mode is touch/desktop-only, and the
        // TV-only always-visible rows ([AppearanceThemeTvOnlyRows] — no
        // advanced gate on either) appear in its place.
        assertEquals(
            flagsOffDeclared + standardBranchContent + advancedRevealedTv,
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
            flagsOffDeclared + standardBranchContent + advancedRevealedNonTv +
                AppearanceThemeScheduledPairRows.size,
            appearanceThemeScreenRowTotal(
                variant = standard,
                isDarkActive = false,
                isAndroid12 = false,
                isTv = false,
                showAdvanced = true,
                themeMode = ThemeMode.SCHEDULED,
            ),
        )
        // The declared half is exactly the declaration minus the named
        // exception sets — the content-gated rows, the theme_scheduler
        // highlight-alias, and the TV-only pair (whose Tv gate drops them
        // under the default non-TV flags) — the strict guard keeps the sets
        // in sync with the gates.
        val declared = rowTotalFor(
            SettingsScreenGroups.appearanceTheme,
            RowAdmissionFlags(showAdvanced = true),
        )
        assertEquals(
            SettingsScreenGroups.appearanceTheme.items.size -
                AppearanceThemeContentGatedRows.size -
                AppearanceThemeHighlightAliasRows.size -
                AppearanceThemeTvOnlyRows.size,
            declared,
        )
        // On TV only layout_mode drops: the TV pair admits, so the TV half
        // is the non-TV one plus the fork swap (the pair minus the one row
        // it replaces).
        assertEquals(
            declared + AppearanceThemeTvOnlyRows.size - 1,
            rowTotalFor(
                SettingsScreenGroups.appearanceTheme,
                RowAdmissionFlags(showAdvanced = true, isTv = true),
            ),
        )
    }

    @Test
    fun `appearance expert group totals derive from their advanced declarations`() {
        // The advanced-only groups only compose behind the advanced
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
        // The Detail leaf's "Density & limits" group (the phase-5
        // reclassification): same wholly-advanced base.
        assertEquals(
            SettingsScreenGroups.appearanceDetailTuning.items.size,
            rowTotalFor(SettingsScreenGroups.appearanceDetailTuning, RowAdmissionFlags(showAdvanced = true)),
        )
        assertEquals(
            0,
            rowTotalFor(SettingsScreenGroups.appearanceDetailTuning, RowAdmissionFlags(showAdvanced = false)),
            "the detail tuning group must stay wholly advanced",
        )
        // Newsletter: the declared trio minus the sections row
        // ([AppearanceNewsletterRuntimeReorderableRows] — it renders as the
        // runtime-reorderable rows the screen counts explicitly).
        val sectionCount = 4
        assertEquals(
            SettingsScreenGroups.appearanceNewsletter.items.size - AppearanceNewsletterRuntimeReorderableRows.size + sectionCount,
            rowTotalFor(SettingsScreenGroups.appearanceNewsletter, RowAdmissionFlags(showAdvanced = true)) - AppearanceNewsletterRuntimeReorderableRows.size + sectionCount,
        )
    }

    /**
     * The Theme & display leaf's sections in LazyColumn order — the same fold
     * the shell derives from the leaf's [PreferenceScreenGroup] slot
     * declaration ([ThemeSettingsScreenSlots], ADR-0017's appearance split:
     * the advanced-only expert groups are the leaf's TRAILING slots now), so
     * the trailing-shape adjust pin below runs the very
     * [deriveAdjustForAdvanced] the shell computes.
     */
    private val appearanceSections = listOf(
        SettingsScreenSection(SettingsScreenGroups.appearanceTheme.itemIdSet),
        SettingsScreenSection(SettingsScreenGroups.appearancePerformance.itemIdSet, advancedOnly = true),
        SettingsScreenSection(SettingsScreenGroups.appearanceEyeCare.itemIdSet, advancedOnly = true),
    )

    @Test
    fun `appearance trailing expert groups stay out of the derived scroll adjust when advanced is hidden`() {
        // The shipped trailing shape: with advanced hidden the two trailing
        // expert groups don't compose (their ids cannot scroll → -1) and
        // nothing trails them, so the leading theme group keeps its index;
        // with advanced shown every group composes and the adjust is the
        // identity.
        assertEquals(-1, deriveAdjustForAdvanced(appearanceSections, false)(1))
        assertEquals(-1, deriveAdjustForAdvanced(appearanceSections, false)(2))
        assertEquals(0, deriveAdjustForAdvanced(appearanceSections, false)(0))
        for (raw in appearanceSections.indices) {
            assertEquals(raw, deriveAdjustForAdvanced(appearanceSections, true)(raw))
        }
        // End-to-end over the same resolver the scaffold drives: an expert id
        // lands on its declared index when shown and scrolls nowhere when hidden.
        val appearanceGroups = appearanceSections.map { it.idSet }
        val expertId = SettingsScreenGroups.appearancePerformance.itemIds.first()
        assertEquals(
            1,
            resolveHighlightScrollIndex(expertId, appearanceGroups, deriveAdjustForAdvanced(appearanceSections, true)),
        )
        assertEquals(
            -1,
            resolveHighlightScrollIndex(expertId, appearanceGroups, deriveAdjustForAdvanced(appearanceSections, false)),
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

    // ── 7. The screen-shell declaration path ────────────────────────────

    @Test
    fun `screen-shell slot declarations derive the same hidden counts as their group declarations`() {
        // The migrated screens' hoisted slot declarations — the ONE
        // ordered declarations the screens map their hand-written bodies
        // onto ([AppearanceHubSlots]/[ThemeSettingsScreenSlots] et al.,
        // beside each screen).
        // Consumed directly here, so a screen reorder or re-flag updates
        // both sides of every pin below at once instead of stranding a stale
        // replica. The default content-count hook IS the declared-admission
        // delta, so every plain slot list derives exactly what
        // [hiddenRowsFor] over the group list derives — the ratchet's
        // absolute numbers (FusedRowsRatchetTest) hold through the shell
        // unchanged. The track-selection slot contributes zero (every row
        // declares Always), so the language declaration's completeness does
        // not move its hint. The deliberately-absent content-only slots (the
        // security screen's quick-connect and remote-control groups)
        // contribute zero by construction.
        val appearanceHubSlots = AppearanceHubSlots
        val themeLeafSlots = ThemeSettingsScreenSlots
        val languageSlots = LanguageSettingsScreenSlots
        val storageSlots = StorageSettingsScreenSlots
        val securitySlots = SecuritySettingsScreenSlots
        val notificationSlots = NotificationSettingsScreenSlots
        val backupSlots = BackupSettingsScreenSlots
        // ADR-0017: the combined playback screen became the hub + eight
        // leaves. The engines leaf keeps the one runtime-keyed declaration —
        // its slot is parameterized by the preferred player (the content-
        // count hook is the shape the flags vocabulary cannot express); the
        // shipped JVM shape is the LIBVLC branch — this JVM's desktop seam
        // backs the mpv device rows and the vlc branch is capability-free.
        val playbackHubSlots = PlaybackHubSlots
        val playbackEngineSlots = PlayerEngineSettingsScreenSlots(PlayerType.LIBVLC)
        // ADR-0017's appearance split: the hub keeps the newsletter trio (the
        // declared hub exception), and the Theme & display leaf hosts the
        // theme group plus the advanced-only Performance/Eye-care trailing
        // slots.
        val appearanceHubGroups = listOf(
            SettingsScreenGroups.appearanceHub,
            SettingsScreenGroups.appearanceNewsletter,
        )
        assertEquals(
            hiddenRowsFor(appearanceHubGroups, RowAdmissionFlags(showAdvanced = false, isTv = false)),
            hiddenRowsFor(appearanceHubSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)),
        )
        assertEquals(
            hiddenRowsFor(appearanceHubGroups, RowAdmissionFlags(showAdvanced = false, isTv = true)),
            hiddenRowsFor(appearanceHubSlots, RowAdmissionFlags(showAdvanced = false, isTv = true)),
        )
        val themeLeafGroups = listOf(
            SettingsScreenGroups.appearanceTheme,
            SettingsScreenGroups.appearancePerformance,
            SettingsScreenGroups.appearanceEyeCare,
        )
        assertEquals(
            hiddenRowsFor(themeLeafGroups, RowAdmissionFlags(showAdvanced = false, isTv = false)),
            hiddenRowsFor(themeLeafSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)),
        )
        assertEquals(
            hiddenRowsFor(themeLeafGroups, RowAdmissionFlags(showAdvanced = false, isTv = true)),
            hiddenRowsFor(themeLeafSlots, RowAdmissionFlags(showAdvanced = false, isTv = true)),
        )
        assertEquals(
            hiddenRowsFor(
                listOf(SettingsScreenGroups.languageGeneral, SettingsScreenGroups.languageSubtitles),
                RowAdmissionFlags(showAdvanced = false, supportsAppLocaleOverride = false),
            ),
            hiddenRowsFor(languageSlots, RowAdmissionFlags(showAdvanced = false, supportsAppLocaleOverride = false)),
        )
        assertEquals(
            hiddenRowsFor(
                listOf(
                    SettingsScreenGroups.storageCache,
                    SettingsScreenGroups.storageNetwork,
                    SettingsScreenGroups.storageNetworkTuning,
                ),
                RowAdmissionFlags(showAdvanced = false),
            ),
            hiddenRowsFor(storageSlots, RowAdmissionFlags(showAdvanced = false)),
        )
        // The Detail leaf's slots (ADR-0017's appearance split + the phase-5
        // reclassification): the visible detail group plus the advanced
        // "Density & limits" trailing slot — the shell count IS the thirteen.
        assertEquals(
            hiddenRowsFor(
                listOf(SettingsScreenGroups.appearanceDetail, SettingsScreenGroups.appearanceDetailTuning),
                RowAdmissionFlags(showAdvanced = false, isTv = false),
            ),
            hiddenRowsFor(DetailDisplaySettingsScreenSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)),
        )
        assertEquals(
            13,
            hiddenRowsFor(DetailDisplaySettingsScreenSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)),
            "the detail leaf hint drifted",
        )
        // ADR-0017's home + storage splits: the home hub's slots (entrances +
        // the advanced Fine-tuning exception) and the leaves' slot lists, the
        // Downloads sibling's single slot — same parity shape as the
        // appearance split. The phase-5 parity fix: the tuning rows carry
        // their declared advanced gates now, so the default declared-
        // admission hook derives the honest 8 — the group-level derivation
        // and the slot's hook agree (both 8; no content-count hook, no
        // double-count).
        val homeHubSlots = HomeSettingsScreenSlots
        assertEquals(
            8,
            hiddenRowsFor(
                listOf(SettingsScreenGroups.homeHub, SettingsScreenGroups.homeTuning),
                RowAdmissionFlags(showAdvanced = false, isTv = false),
            ),
            "the group-level tuning derivation must agree with the slot's 8",
        )
        assertEquals(8, hiddenRowsFor(homeHubSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)), "the home hub hint drifted")
        assertEquals(0, hiddenRowsFor(HomeDisplaySettingsScreenSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)), "the display leaf grew an advanced row")
        assertEquals(0, hiddenRowsFor(HomeRowsSettingsScreenSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)), "the rows leaf grew an advanced row")
        assertEquals(0, hiddenRowsFor(HomeCardsSettingsScreenSlots, RowAdmissionFlags(showAdvanced = false, isTv = false)), "the cards leaf grew an advanced row")
        assertEquals(0, hiddenRowsFor(DownloadsSettingsScreenSlots, RowAdmissionFlags(showAdvanced = false)), "the downloads screen grew an advanced row")
        assertEquals(
            hiddenRowsFor(
                listOf(SettingsScreenGroups.security),
                RowAdmissionFlags(showAdvanced = false, supportsAppLock = true, supportsBiometric = false),
            ),
            hiddenRowsFor(securitySlots, RowAdmissionFlags(showAdvanced = false, supportsAppLock = true, supportsBiometric = false)),
        )
        // The notifications screen's single slot: the parent-gated legs are
        // the interesting shape (the quiet-hours pair counts only while both
        // toggles are on, the system-settings row where its capability
        // holds) — the shell derives the exact same delta the screen's
        // hiddenHint passes as rowFlags.
        val notificationsFlags = RowAdmissionFlags(
            showAdvanced = false,
            supportsSystemNotificationSettings = true,
            parentsOn = rowParentsOn(
                NotificationRows.NotificationsEnable.id to true,
                NotificationRows.QuietHours.id to true,
            ),
        )
        assertEquals(
            hiddenRowsFor(listOf(SettingsScreenGroups.notifications), notificationsFlags),
            hiddenRowsFor(notificationSlots, notificationsFlags),
        )
        // The backup screen's single slot: no hiddenHint rides the screen
        // (it takes no advanced tier), but the slot/group parity still holds.
        assertEquals(
            hiddenRowsFor(listOf(SettingsScreenGroups.backup), RowAdmissionFlags()),
            hiddenRowsFor(backupSlots, RowAdmissionFlags()),
        )

        // Playback hub: the entrance rows declare Always — the hub declares
        // no advanced rows, so its derived hint is flat zero (the advanced
        // tiers live on the leaves now).
        val desktop = RowAdmissionFlags(showAdvanced = false)
        assertEquals(
            0,
            hiddenRowsFor(playbackHubSlots, desktop),
            "the playback hub grew an advanced row (or an entrance lost its Always gate)",
        )
        // Engines leaf: the leaf hosts ONLY the engine slot, so the shell
        // count IS the branch term — the slot's prefix hook differences the
        // preferred player's rows across the advanced leg (vlc here: the
        // branch is capability-free; the desktop seam backs the mpv device
        // rows for the mpv arm).
        assertEquals(
            rowTotalFor(SettingsScreenGroups.playbackEngine, desktop.copy(showAdvanced = true)) { it.id.startsWith("vlc_") } -
                rowTotalFor(SettingsScreenGroups.playbackEngine, desktop) { it.id.startsWith("vlc_") },
            hiddenRowsFor(playbackEngineSlots, desktop),
        )
        // …and it is honest where the frozen literal was not: the count moves
        // with the engine branch (vlc's declared row count differs from mpv's
        // desktop-gated one) instead of sitting at a hand-picked constant.
        val mpvSlots = PlayerEngineSettingsScreenSlots(PlayerType.MPV)
        assertTrue(
            hiddenRowsFor(playbackEngineSlots, desktop) != hiddenRowsFor(mpvSlots, desktop),
            "the derived playback hint must track the engine branch (the frozen 28 could not)",
        )

        // The performance hub's slots: a plain list (no runtime-keyed hook),
        // so the shell count equals the plain groups' delta exactly — and
        // equals the 15-row expert set the FusedRowsRatchetTest pins.
        val performanceSlots = PerformanceSettingsScreenSlots()
        val performanceGroups = listOf(
            SettingsScreenGroups.performanceLevel,
            SettingsScreenGroups.performanceMotion,
            SettingsScreenGroups.performanceImages,
            SettingsScreenGroups.performanceAmbient,
            SettingsScreenGroups.playbackBuffers,
        )
        assertEquals(
            hiddenRowsFor(performanceGroups, desktop),
            hiddenRowsFor(performanceSlots, desktop),
        )
        assertEquals(15, hiddenRowsFor(performanceSlots, desktop), "the hub hint drifted")
    }

    // ── 8. The search-result click action ───────────────────────────────

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

        // A moved tuning row (ADR-0017: the dream rows live on the Ambient
        // screen) navigates like any other sub-screen row — the id baked into
        // its owning screen's route, highlight marked.
        val screensaver = settingsResultClickAction(
            SystemRows.ScreensaverKenBurns.id, Route.AmbientSettings(), isAdvanced = false, showAdvancedSettings = true,
        )
        val screensaverAction = screensaver.action as SettingsSearchResultAction.NavigateToScreen
        assertEquals(Route.AmbientSettings(SystemRows.ScreensaverKenBurns.id), screensaverAction.route)
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

        // The phase-5 parity proof: a FINE-TUNING row's search hit arms the
        // gate. The home hub's refresh-cadence tuning row carries its
        // advanced face on the CATALOG item itself (the spec-flag flip), so
        // the arm decision derives from the same face the search pane
        // renders — no hand-restated flag.
        val tuningHit = SettingsSearchCatalog.items.single { it.id == HomeRows.HomeRefreshCadence.id }
        assertTrue(tuningHit.isAdvanced, "the tuning row's search face must carry the advanced flag")
        val armed = settingsResultClickAction(tuningHit.id, tuningHit.route, isAdvanced = tuningHit.isAdvanced, showAdvancedSettings = false)
        assertTrue(armed.enableAdvanced, "a tuning-row search hit must auto-arm the advanced gate")
    }

    // ── 9. The bare-Settings route totality contract ────────────────────

    /**
     * ADR-0017 emptied the root's on-screen settings surface: every former
     * inline group (the screensaver/idle-ambient tuning groups, Discord +
     * shell hooks, the plugin section) moved onto its declared screen, so a
     * search hit routes to THAT screen with the id baked in — the NoOp
     * reveal arm of `settingsResultClickAction` has no on-screen rows left.
     * The only catalog rows routed bare-`Route.Settings` are the two
     * destructive account actions, which resolve to confirm dialogs before
     * any navigation (section 8). This pin holds that totality in BOTH
     * directions: no searchable row may land on the root (a new on-screen
     * root row must declare a real route like every other row), and the two
     * dialog actions must stay the exact bare-Settings set (a route change
     * there is a reviewed semantics change, not a refactor).
     */
    @Test
    fun `the only bare-Settings routes are the two destructive account actions`() {
        val bareSettingsIds = SettingsSearchCatalog.items
            .filter { it.route == Route.Settings }
            .map { it.id }
        assertEquals(
            listOf(AccountRows.Logout.id, AccountRows.SignOutFromServer.id),
            bareSettingsIds,
            "a searchable row routes bare-Settings onto the root screen — ADR-0017 moved every " +
                "on-screen root row onto its declared screen; route the row to its owning screen " +
                "(the bare-Route.Settings arm of settingsResultClickAction is dialog-only now)",
        )
    }

    // ── 10. The sub-screen deep-link totality contract ──────────────────

    /**
     * The root-screen counterpart (section 9) generalized to every
     * sub-screen route: a search hit deep-links its route with the row id
     * baked in ([SettingsBrowsePane] navigates `Route.X(id)`), and the
     * target screen can only scroll to and expand the group that declares
     * the id ([PreferenceScreenScaffold.groups] → the
     * [rememberHighlightScrollIndex] derivation). The three pins below hold
     * the pairing total in BOTH directions, data-driven over the catalog's
     * route kinds — a future searchable domain whose screen has not migrated
     * onto the shell fails the scan until its route is bound or named.
     */

    /**
     * Route kind → the ONE ordered group declaration the sub-screen renders
     * (the [SettingsScreenGroups] members its hoisted `XScreenSlots`
     * declaration derives from — the same objects the section-7 slot pins
     * consume). A screen migrating onto the shell adds its route here; the
     * maps key on the route CLASS so the catalog's highlight-carrying route
     * copies (`withHighlightSettingId`) match the bare binding.
     */
    private val screenGroupsByRoute: Map<KClass<out Route>, List<SettingsSearchItemGroup>> = mapOf(
        // ADR-0017's appearance split: the hub hosts the entrance group plus
        // the newsletter advanced group (the declared hub exception), each
        // leaf its domain groups (the Theme leaf hosts the theme +
        // performance + eye-care trio).
        Route.AppearanceSettings::class to listOf(
            SettingsScreenGroups.appearanceHub,
            SettingsScreenGroups.appearanceNewsletter,
        ),
        Route.ThemeSettings::class to listOf(
            SettingsScreenGroups.appearanceTheme,
            SettingsScreenGroups.appearancePerformance,
            SettingsScreenGroups.appearanceEyeCare,
        ),
        Route.LibraryDisplaySettings::class to listOf(
            SettingsScreenGroups.appearanceLibraryViews,
        ),
        Route.DetailDisplaySettings::class to listOf(
            SettingsScreenGroups.appearanceDetail,
            SettingsScreenGroups.appearanceDetailTuning,
        ),
        Route.NavigationDisplaySettings::class to listOf(
            SettingsScreenGroups.appearanceNavigation,
        ),
        // ADR-0017's home split: the hub hosts the entrance group plus the
        // advanced-gated Fine-tuning group (the declared hub exception), each
        // leaf its domain groups (the Rows & layout leaf hosts the Next Up +
        // layout + curation trio).
        Route.HomeSettings::class to listOf(
            SettingsScreenGroups.homeHub,
            SettingsScreenGroups.homeTuning,
        ),
        Route.HomeDisplaySettings::class to listOf(
            SettingsScreenGroups.homeDisplayMain,
        ),
        Route.HomeRowsSettings::class to listOf(
            SettingsScreenGroups.homeNextUp,
            SettingsScreenGroups.homeLayout,
            SettingsScreenGroups.homeCuration,
        ),
        Route.HomeCardsSettings::class to listOf(
            SettingsScreenGroups.homeCards,
        ),
        Route.PerformanceSettings::class to listOf(
            SettingsScreenGroups.performanceLevel,
            SettingsScreenGroups.performanceMotion,
            SettingsScreenGroups.performanceImages,
            SettingsScreenGroups.performanceAmbient,
            SettingsScreenGroups.playbackBuffers,
        ),
        // ADR-0017's playback hub split: the hub hosts the entrance group,
        // each leaf its domain groups (SyncPlay & casting hosts the pair).
        Route.PlaybackSettings::class to listOf(
            SettingsScreenGroups.playbackHub,
        ),
        Route.PlaybackBasics::class to listOf(
            SettingsScreenGroups.playbackBasics,
        ),
        Route.PlaybackControls::class to listOf(
            SettingsScreenGroups.playbackControls,
        ),
        Route.PlayerChromeSettings::class to listOf(
            SettingsScreenGroups.playbackPlayerChrome,
        ),
        Route.PlaybackVideoQuality::class to listOf(
            SettingsScreenGroups.playbackAdvancedVideo,
        ),
        Route.PlayerEngineSettings::class to listOf(
            SettingsScreenGroups.playbackEngine,
        ),
        Route.MediaSegmentSettings::class to listOf(
            SettingsScreenGroups.playbackMediaSegments,
        ),
        Route.SyncPlayCastingSettings::class to listOf(
            SettingsScreenGroups.playbackSyncPlay,
            SettingsScreenGroups.playbackCasting,
        ),
        Route.LiveTvDvrSettings::class to listOf(
            SettingsScreenGroups.playbackDvr,
        ),
        Route.AudioSettings::class to listOf(
            SettingsScreenGroups.audio,
            SettingsScreenGroups.audioCache,
            SettingsScreenGroups.audioFineTuning,
        ),
        Route.LanguageSettings::class to listOf(
            SettingsScreenGroups.languageGeneral,
            SettingsScreenGroups.languageTrackSelection,
            SettingsScreenGroups.languageSubtitles,
        ),
        // ADR-0017's storage split: the cache + network halves stay on the
        // storage screen (plus the advanced Network diagnostics group — the
        // phase-5 reclassification); the Downloads group is the sibling
        // screen.
        Route.StorageSettings::class to listOf(
            SettingsScreenGroups.storageCache,
            SettingsScreenGroups.storageNetwork,
            SettingsScreenGroups.storageNetworkTuning,
        ),
        Route.DownloadsSettings::class to listOf(
            SettingsScreenGroups.storageDownloads,
        ),
        Route.SecuritySettings::class to listOf(SettingsScreenGroups.security),
        Route.NotificationSettings::class to listOf(SettingsScreenGroups.notifications),
        Route.BackupSettings::class to listOf(SettingsScreenGroups.backup),
        // ADR-0017: the TV dream + desktop idle-ambient groups as ONE Ambient
        // screen; the desktop-integration groups on the Integrations screen;
        // the plugin per-feature switches on the plugin screen.
        Route.AmbientSettings::class to listOf(
            SettingsScreenGroups.systemScreensaver,
            SettingsScreenGroups.systemIdleAmbient,
        ),
        Route.Integrations::class to listOf(
            SettingsScreenGroups.integrations,
            SettingsScreenGroups.systemDiscordPresence,
            SettingsScreenGroups.systemHooks,
        ),
        Route.JellyPlayPluginSettings::class to listOf(
            SettingsScreenGroups.jellyplayFeatures,
        ),
    )

    /**
     * The catalog route kinds whose target declares no screen groups — each
     * entry named. Two families plus the root: the navigation-only
     * destinations (the row is a launcher for a non-settings surface — the
     * deep link navigates and never highlights) and the not-yet-migrated
     * screens (the ADR-0009 follow-up set — migrating a screen onto the
     * shell moves its route into [screenGroupsByRoute] and deletes its
     * entry). A route kind in NEITHER map fails the scan below.
     */
    private val routesWithoutDeclaredGroups: Map<KClass<out Route>, String> = mapOf(
        // The root screen's coverage is its own family (section 9 pins
        // rootRevealGroups, the auto-expand subset of the root's groups).
        Route.Settings::class to "the root screen — pinned by the section-9 bare-Settings totality family",
        // Navigation-only destinations: the row is a launcher (the activity
        // insights / system entrances, the management screens, the tools),
        // not a highlight target.
        Route.Favorites::class to "the Favorites app surface (launcher row)",
        Route.WatchProgressHeatmap::class to "the watch-progress heatmap surface (launcher row)",
        Route.ArrQueue::class to "the ARR queue surface (launcher row)",
        Route.UpcomingCalendar::class to "the upcoming-calendar surface (launcher row)",
        Route.Requests::class to "the requests surface (launcher row)",
        Route.AdminDashboard::class to "the admin dashboard surface (launcher row)",
        Route.Onboarding::class to "the setup wizard flow (launcher row)",
        Route.ServerManagement::class to "the server management screen (section 8: must not mark the highlight)",
        Route.UserManagement::class to "the user management screen (section 8: must not mark the highlight)",
        Route.SubtitleTester::class to "the subtitle tester tool (launcher row)",
        Route.NextUpExcluded::class to "the Next Up exclusion editor (dedicated surface)",
        Route.MyMediaHidden::class to "the hidden-libraries restore editor (dedicated surface)",
        // The not-yet-migrated screens (the ADR-0009 follow-up set).
        Route.PinnedHomeSections::class to "PinnedHomeSectionsScreen (inline body) — migrate onto the shell",
        Route.HomeLayoutPresets::class to "HomeLayoutPresetsScreen (inline body) — migrate onto the shell",
        Route.LibraryHomeSections::class to "LibraryHomeSectionsScreen (inline body) — migrate onto the shell",
        Route.DiscoverRows::class to "DiscoverRowsScreen (inline body) — migrate onto the shell",
        Route.SeerrSettings::class to "SeerrSettingsScreen (inline body) — migrate onto the shell",
        Route.JellyPlaySync::class to "JellyPlaySyncScreen (bespoke body) — migrate onto the shell",
        Route.About::class to "AboutScreen (bespoke body) — migrate onto the shell",
        Route.ExperimentalSettings::class to "ExperimentalSettingsScreen (the spec-direct hold-out, inline body)",
        Route.ArrSettings::class to "ArrSettingsScreen (bespoke body) — migrate onto the shell",
    )

    /**
     * The declared ids a screen renders as its own rows while the id
     * deep-links a DIFFERENT route — the drill-in launcher shape (the home
     * screen's dedicated editors, the language screen's tester tool). Named
     * per screen; each id's actual route is covered by the route-kind scan
     * (its route's [routesWithoutDeclaredGroups] entry names the target).
     */
    private val declaredIdsRoutingElsewhere: Map<KClass<out Route>, Set<String>> = mapOf(
        // The playback hub's eight entrance rows (ADR-0017): rows the hub
        // renders that deep-link the leaf screens they open.
        Route.PlaybackSettings::class to setOf(
            PlaybackRows.BasicsEntrance.id, // → Route.PlaybackBasics
            PlaybackRows.ControlsEntrance.id, // → Route.PlaybackControls
            PlaybackRows.PlayerChromeEntrance.id, // → Route.PlayerChromeSettings
            PlaybackRows.VideoQualityEntrance.id, // → Route.PlaybackVideoQuality
            PlaybackRows.PlayerEnginesEntrance.id, // → Route.PlayerEngineSettings
            PlaybackRows.SegmentsEntrance.id, // → Route.MediaSegmentSettings
            PlaybackRows.SyncPlayCastingEntrance.id, // → Route.SyncPlayCastingSettings
            PlaybackRows.LiveTvDvrEntrance.id, // → Route.LiveTvDvrSettings
        ),
        // The appearance hub's four entrance rows (ADR-0017's appearance
        // split): rows the hub renders that deep-link the leaf screens they
        // open. (The newsletter trio is NOT exempt: it routes to and renders
        // on the hub itself.)
        Route.AppearanceSettings::class to setOf(
            AppearanceRows.ThemeEntrance.id, // → Route.ThemeSettings
            AppearanceRows.LibraryViewsEntrance.id, // → Route.LibraryDisplaySettings
            AppearanceRows.DetailDisplayEntrance.id, // → Route.DetailDisplaySettings
            AppearanceRows.NavigationEntrance.id, // → Route.NavigationDisplaySettings
        ),
        // The home hub's three entrance rows (ADR-0017's home split): rows
        // the hub renders that deep-link the leaf screens they open. (The
        // Fine-tuning group is NOT exempt: its rows route to and render on
        // the hub itself.)
        Route.HomeSettings::class to setOf(
            HomeRows.DisplayEntrance.id, // → Route.HomeDisplaySettings
            HomeRows.RowsLayoutEntrance.id, // → Route.HomeRowsSettings
            HomeRows.CardsEntrance.id, // → Route.HomeCardsSettings
        ),
        // The Rows & layout leaf renders the home drill-in launchers (the
        // former hub screen's set moved whole with the layout group) plus
        // the exclusion/hidden-libraries restore rows.
        Route.HomeRowsSettings::class to setOf(
            HomeRows.NextUpHidden.id, // → Route.NextUpExcluded
            HomeRows.HiddenLibraries.id, // → Route.MyMediaHidden
            HomeRows.PinnedHomeSections.id, // → Route.PinnedHomeSections
            HomeRows.HomeLayoutPresets.id, // → Route.HomeLayoutPresets
            HomeRows.ConfigureLibraries.id, // → Route.LibraryHomeSections
            HomeRows.DiscoverRows.id, // → Route.DiscoverRows
        ),
        // The Integrations hub's launcher trio: rows the hub renders that
        // deep-link the dedicated surfaces they open (the hub itself and the
        // subtitle drill-in route Integrations — same class, covered above).
        Route.Integrations::class to setOf(
            IntegrationsRows.SeerrSettings.id, // → Route.SeerrSettings
        ),
        Route.LanguageSettings::class to setOf(
            LanguageRows.SubtitleTester.id, // → Route.SubtitleTester
        ),
    )


    /** The data-driven scan: every catalog route kind is bound or named. */
    @Test
    fun `every catalog route is a declared screen binding or a named exemption`() {
        val routeKinds = SettingsSearchCatalog.items.map { it.route::class }.toSet()
        assertTrue(routeKinds.isNotEmpty(), "no catalog routes — the route scan is broken")
        val unaccounted = routeKinds - screenGroupsByRoute.keys - routesWithoutDeclaredGroups.keys
        assertEquals(
            emptySet(),
            unaccounted,
            "catalog routes with no declared screen groups and no named exemption — bind the screen's " +
                "groups in screenGroupsByRoute (a declaration-path screen) or name the route in " +
                "routesWithoutDeclaredGroups",
        )
        // The staleness leg: every binding and every named exemption still
        // has catalog items routed to it (a migrated-away entry or an
        // exemption whose row died is deleted, not kept). The launcher
        // routes are exempt: their group's ids route the leaves they open,
        // so no catalog item routes to the launcher itself (by design).
        val stale = (screenGroupsByRoute.keys + routesWithoutDeclaredGroups.keys) - routeKinds - launcherRoutes.keys
        assertEquals(
            emptySet(),
            stale,
            "declared/exempt routes with no catalog items routed to them — delete the stale entry",
        )
    }

    /**
     * The launcher routes (ADR-0017's domain hubs): declaration-path screens
     * whose declared entrance group's rows are composite entrances that
     * deep-link the leaves they open — so no ENTRANCE routes to the launcher
     * itself. Each entry names why the route is a launcher. A launcher MAY
     * keep one inline advanced group routed to it
     * ([launcherInlineGroups] — the glossary's declared hub exception: the
     * appearance hub's newsletter trio); a launcher without an entry there
     * is strict (nothing routes to it).
     */
    private val launcherRoutes: Map<KClass<out Route>, String> = mapOf(
        Route.PlaybackSettings::class to
            "the playback hub — its eight entrance rows route the leaves they open",
        Route.AppearanceSettings::class to
            "the appearance hub — its four entrance rows route the leaves they open; " +
            "the newsletter advanced group is the declared hub exception and stays routed here",
        Route.HomeSettings::class to
            "the home hub — its three entrance rows route the leaves they open; " +
            "the Fine-tuning advanced group is the declared hub exception and stays routed here",
    )

    /**
     * The ONE inline group a launcher keeps routed to itself (absent = the
     * strict launcher: nothing routes to it). The appearance hub's newsletter
     * advanced group is the glossary's declared hub exception — its rows are
     * real hub rows, not entrances. The home hub's Fine-tuning group is the
     * same declared shape. (Both launchers also keep their inline group's
     * GROUP-CARD hit routed here — the card ids join the forward pin's
     * allowed set through this map's groups.)
     */
    private val launcherInlineGroups: Map<KClass<out Route>, SettingsSearchItemGroup> = mapOf(
        Route.AppearanceSettings::class to SettingsScreenGroups.appearanceNewsletter,
        Route.HomeSettings::class to SettingsScreenGroups.homeTuning,
    )

    /**
     * The declared GROUP-CARD search items (the group-grain card concept):
     * each entry pins card id ↔ owning group ↔ hosting route class. A card
     * face is declared ONLY on an advancedOnly group that renders a labeled
     * card — the five "hidden card" groups — so the card ids here are exactly
     * [SettingsScreenGroups.all]'s non-null [SettingsSearchItemGroup.cardItem]s
     * (the totality leg of the test below); a sixth advancedOnly group
     * without a rendered label card declares no card face and is named in
     * the test body.
     */
    private val groupCardItems: List<GroupCardPin> = listOf(
        GroupCardPin("home.tuning", SettingsScreenGroups.homeTuning, Route.HomeSettings::class),
        GroupCardPin("appearance.detailTuning", SettingsScreenGroups.appearanceDetailTuning, Route.DetailDisplaySettings::class),
        GroupCardPin("appearance.newsletter", SettingsScreenGroups.appearanceNewsletter, Route.AppearanceSettings::class),
        GroupCardPin("audio.fineTuning", SettingsScreenGroups.audioFineTuning, Route.AudioSettings::class),
        GroupCardPin("storage.networkTuning", SettingsScreenGroups.storageNetworkTuning, Route.StorageSettings::class),
    )

    /** One declared group-card pin: the card id, its owning group, its hosting route class. */
    private data class GroupCardPin(
        val cardId: String,
        val group: SettingsSearchItemGroup,
        val hostingRoute: KClass<out Route>,
    )

    /**
     * The group-card pins, both legs. The card leg: each declared card item
     * exists, carries its group's id and title resource, isAdvanced (the
     * advancedOnly slot shape the declared cards ride — the hit auto-arms
     * the power-user gate), non-empty keywords (the catalog ratchet), and a
     * route of the declared hosting class whose BAKED highlight is the
     * group's FIRST member row id (the expand + scroll + glow path is the
     * existing row-id machinery — the card deep-links through it with zero
     * new landing code). The totality leg: the catalog's card set is EXACTLY
     * this pin list — a new card face joins here (and its hosting route's
     * forward pin above) or the aggregation fails loudly.
     */
    @Test
    fun `group card items are pinned to their groups and land on the first member row`() {
        groupCardItems.forEach { pin ->
            val card = requireNotNull(pin.group.cardItem) { "${pin.group.id} declares no card item — update this pin or drop the face" }
            assertEquals(pin.cardId, card.id, "${pin.group.id}'s card id must be the group's id")
            assertEquals(pin.group.id, card.id, "card id and owning group id drifted")
            assertEquals(pin.hostingRoute, card.route::class, "${pin.cardId} must route its hosting screen")
            assertTrue(card.isAdvanced, "${pin.cardId} must auto-arm the power-user gate (advancedOnly slot shape)")
            assertTrue(card.keywords.isNotEmpty(), "${pin.cardId} carries no keywords")
            assertEquals(
                pin.group.itemIds.first(),
                highlightOf(card.route),
                "${pin.cardId}'s baked highlight must be the group's first member row id",
            )
        }
        // Totality: the catalog's card set is exactly the pinned ids (the
        // rows+card concatenation keeps every card hit adjacent to its
        // group's rows in the curated flat order).
        val pinnedCardIds = groupCardItems.map { it.cardId }.toSet()
        assertEquals(
            groupCardItems.map { it.cardId },
            SettingsSearchCatalog.items.map { it.id }.filter { it in pinnedCardIds },
        )
        assertEquals(
            groupCardItems.map { it.cardId }.toSet(),
            SettingsScreenGroups.all.mapNotNull { it.cardItem?.id }.toSet(),
            "a card face was declared without (or removed from) this pin list",
        )
        // The landing fold and the baked route agree: the dispatch injects
        // exactly the id the card's route already carries.
        groupCardItems.forEach { pin ->
            val baked = highlightOf(requireNotNull(pin.group.cardItem).route)
            assertEquals(baked, groupCardLandingRowId(pin.cardId), "${pin.cardId}'s fold and baked landing drifted")
        }
        assertNull(groupCardLandingRowId("__no_such_card__"), "a non-card id must fold to identity (null → self)")
    }

    /** The baked highlight id of a highlightable route (null when none). */
    private fun highlightOf(route: Route): String? = when (route) {
        is Route.HomeSettings -> route.highlightSettingId
        is Route.AudioSettings -> route.highlightSettingId
        is Route.DetailDisplaySettings -> route.highlightSettingId
        is Route.StorageSettings -> route.highlightSettingId
        is Route.AppearanceSettings -> route.highlightSettingId
        else -> null
    }

    /**
     * The hub entrance rows' explicit destinations (entrance id → the route
     * class it deep-links). Spelled out here — NOT derived from the rows'
     * declared routes — so a row whose route drifts to the wrong leaf fails
     * the pin instead of redefining its own expectation. The forward pin's
     * launcher exemption is per-entrance: an id may be absent from screen R's
     * declared groups only while R is the destination this map names, so an
     * entrance routed to the wrong leaf is never silently exempted.
     */
    private val hubEntranceDestinations: Map<String, KClass<out Route>> = mapOf(
        PlaybackRows.BasicsEntrance.id to Route.PlaybackBasics::class,
        PlaybackRows.ControlsEntrance.id to Route.PlaybackControls::class,
        PlaybackRows.PlayerChromeEntrance.id to Route.PlayerChromeSettings::class,
        PlaybackRows.VideoQualityEntrance.id to Route.PlaybackVideoQuality::class,
        PlaybackRows.PlayerEnginesEntrance.id to Route.PlayerEngineSettings::class,
        PlaybackRows.SegmentsEntrance.id to Route.MediaSegmentSettings::class,
        PlaybackRows.SyncPlayCastingEntrance.id to Route.SyncPlayCastingSettings::class,
        PlaybackRows.LiveTvDvrEntrance.id to Route.LiveTvDvrSettings::class,
        AppearanceRows.ThemeEntrance.id to Route.ThemeSettings::class,
        AppearanceRows.LibraryViewsEntrance.id to Route.LibraryDisplaySettings::class,
        AppearanceRows.DetailDisplayEntrance.id to Route.DetailDisplaySettings::class,
        AppearanceRows.NavigationEntrance.id to Route.NavigationDisplaySettings::class,
        HomeRows.DisplayEntrance.id to Route.HomeDisplaySettings::class,
        HomeRows.RowsLayoutEntrance.id to Route.HomeRowsSettings::class,
        HomeRows.CardsEntrance.id to Route.HomeCardsSettings::class,
    )

    /**
     * The Rows & layout leaf's route binding must be the same group set its
     * screen renders ([HomeRowsSettingsScreenSlots]) — a hand-copied binding
     * could validate a different set than the screen emits. The slots expose
     * only their id sets, so the parity is ID-based; the hidden-row count
     * assertion beside the slot pin stays separate.
     */
    @Test
    fun `the Rows and layout route binding matches the screen's slot declaration`() {
        assertEquals(
            HomeRowsSettingsScreenSlots.map { it.idSet },
            screenGroupsByRoute.getValue(Route.HomeRowsSettings::class).map { it.itemIdSet },
            "the Route.HomeRowsSettings binding drifted from HomeRowsSettingsScreenSlots — " +
                "the route-binding check would validate groups the screen does not render",
        )
    }

    /**
     * The entrance-destination map is total over the hubs' entrance rows and
     * every entrance actually routes the destination it names — the two legs
     * the forward pin's per-entrance exemption stands on.
     */
    @Test
    fun `every hub entrance routes its explicit destination`() {
        val entranceIds = SettingsScreenGroups.playbackHub.itemIdSet +
            SettingsScreenGroups.appearanceHub.itemIdSet +
            SettingsScreenGroups.homeHub.itemIdSet
        assertEquals(
            entranceIds,
            hubEntranceDestinations.keys,
            "hub entrance ids and hubEntranceDestinations drifted — add/remove the row's entry beside its declaration",
        )
        val catalogRoutes = SettingsSearchCatalog.items.associate { it.id to it.route::class }
        hubEntranceDestinations.forEach { (id, destination) ->
            assertEquals(
                destination,
                catalogRoutes[id],
                "hub entrance $id does not route its declared destination — the deep link would land on the wrong leaf",
            )
        }
    }

    /** The forward pin: every id routed to R is covered by R's declared groups. */
    @Test
    fun `every catalog item routed to a sub-screen belongs to that screen's declared groups`() {
        // The vlc_video_output class of bug, per screen: an id whose deep
        // link navigates to screen R but that no group R declares can neither
        // scroll nor expand — the highlight lands nowhere.
        // The launcher rows (the hubs' entrances, declared in each hub's
        // group) are entitled to sit outside R's groups only while R is the
        // leaf that entrance deep-links ([hubEntranceDestinations]) — never
        // as a blanket hub-id exemption. A screen's GROUP-CARD hits are the
        // second covered set: a card id lands on its group's first member
        // row (which R's group DOES declare), so the card itself is covered
        // by its owning group's card item, not by any itemIdSet.
        screenGroupsByRoute.forEach { (routeKind, groups) ->
            val routedIds = SettingsSearchCatalog.items.filter { it.route::class == routeKind }.map { it.id }
            if (routeKind in launcherRoutes) {
                // A launcher takes no deep-link rows: every entrance routes
                // the leaf it opens. The declared inline exception (the
                // newsletter trio on the appearance hub) — plus that inline
                // group's own card item — is the ONLY set allowed to route
                // here.
                val inlineIds = launcherInlineGroups[routeKind]
                    ?.let { group -> group.itemIdSet + listOfNotNull(group.cardItem?.id) }
                    ?: emptySet()
                assertEquals(
                    inlineIds.toList(),
                    routedIds,
                    "$routeKind is a launcher — only its declared inline group (and its card item) may route to it; " +
                        "every entrance must route the leaf it opens",
                )
                return@forEach
            }
            assertTrue(routedIds.isNotEmpty(), "$routeKind binding has no catalog items — the binding is stale")
            val covered = groups.flatMap { it.itemIdSet }.toSet() +
                groups.mapNotNull { it.cardItem?.id }.toSet()
            val uncovered = routedIds.filterNot { id -> id in covered || hubEntranceDestinations[id] == routeKind }
            assertEquals(
                emptyList(),
                uncovered,
                "ids routed to $routeKind that no group of that screen declares — declare them in the " +
                    "screen's group so the deep link scrolls",
            )
        }
    }

    /** The reverse pin: every id in R's declared groups routes to R (or is a named drill-in). */
    @Test
    fun `every declared group id is a catalog item routed to its screen`() {
        // The auto_delete_after_watch class of bug (declared in a group, no
        // routed search item): a highlight target that highlights nothing.
        // The named exemptions are the drill-in launchers — rows the screen
        // renders whose id deep-links the dedicated surface they open.
        screenGroupsByRoute.forEach { (routeKind, groups) ->
            val routedIds = SettingsSearchCatalog.items
                .filter { it.route::class == routeKind }
                .map { it.id }
                .toSet()
            val exempt = declaredIdsRoutingElsewhere[routeKind] ?: emptySet()
            val stray = groups.flatMap { it.itemIdSet }.filterNot { it in routedIds || it in exempt }
            assertEquals(
                emptyList(),
                stray,
                "ids declared in $routeKind's groups that route to no catalog item of that screen — " +
                    "remove them from the group or name them in declaredIdsRoutingElsewhere",
            )
        }
    }

    /**
     * The scroll-resolution totality: EVERY catalog item routed to a screen
     * with declared groups — row AND group-card — resolves a landing group
     * on that screen's declaration, so the shell's
     * [rememberHighlightScrollIndex] never comes up empty for a searchable
     * id (the gate is armed — identity adjust — the state every search click
     * produces before the screen composes). The card items resolve on their
     * FIRST member row id (the id their routes bake), never on the card id
     * itself, which no screen's declaration carries. This is the scroll leg
     * of the forward pin above, stated on the resolver the screens run.
     */
    @Test
    fun `every catalog item resolves a scroll landing on its route's declared groups`() {
        screenGroupsByRoute.forEach { (routeKind, groups) ->
            // A strict launcher (e.g. the playback hub) legitimately routes
            // nothing here; the forward pin guards binding staleness.
            val routed = SettingsSearchCatalog.items.filter { it.route::class == routeKind }
            val idSets = groups.map { it.itemIdSet }
            routed.forEach { item ->
                // The hub launchers' entrance rows deep-link the leaf they
                // open; no leaf declares them (the shipped launcher shape —
                // the same per-entrance exemption the forward pin stands on).
                if (hubEntranceDestinations[item.id] == routeKind) return@forEach
                val landingId = groupCardLandingRowId(item.id) ?: item.id
                val index = resolveHighlightScrollIndex(landingId, idSets)
                assertTrue(
                    index >= 0,
                    "${item.id} (landing row $landingId) resolves no scroll group on $routeKind's declared groups",
                )
            }
        }
        // The card landings explicitly: first member id → hosting screen.
        SettingsScreenGroups.all.forEach { group ->
            val card = group.cardItem ?: return@forEach
            val binding = screenGroupsByRoute[card.route::class] ?: return@forEach
            val index = resolveHighlightScrollIndex(group.itemIds.first(), binding.map { it.itemIdSet })
            assertTrue(
                index >= 0,
                "${card.id}'s first member row resolves no scroll group on its hosting screen",
            )
            assertEquals(
                -1,
                resolveHighlightScrollIndex(card.id, binding.map { it.itemIdSet }),
                "${card.id}'s own id must NOT be a scroll target — the card lands on its first member",
            )
        }
    }
}

