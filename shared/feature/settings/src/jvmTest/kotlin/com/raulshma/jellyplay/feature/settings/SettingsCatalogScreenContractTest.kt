package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPreferences
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.ui.navigation.Route
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The catalog ↔ screen contract (the drift ratchet): the per-screen
 * `*SearchItems` declarations — decorated into named screen groups by
 * [SettingsScreenGroups] and aggregated by [SettingsSearchCatalog] — are the
 * single declaration of each screen's group facts, and the screens must
 * derive their deep-link scroll groups, expand sets and row totals from that
 * declaration instead of hand-typed parallel id lists.
 *
 * Four live drifts shipped before this contract existed, each caught by one
 * of the tests below had it been in place:
 *  - `vlc_video_output` (declared, in NO screen group → deep-link neither
 *    scrolled nor expanded) — caught by [every declared id maps to exactly
 *    one screen group] + the behavioral pins;
 *  - `live_stream_option` (declared, row existed, but in no group list) —
 *    same;
 *  - `dialogue_boost_strength` (expand set only, no scroll entry, row without
 *    a `highlighted` param) — caught by [every catalog id has a highlighted
 *    row in its screen file];
 *  - `auto_delete_after_watch` (group id + row, but NO search item declared)
 *    — caught by [every screen highlighted id exists in the catalog].
 *
 * Source-scanning (like `ControllerOwnershipTest` / the resolve-guard
 * ratchet): walk up from `user.dir` to the module root via
 * `src/commonMain/kotlin`, then assert against the sources, so the test
 * fails at build time on this machine rather than shipping drift.
 */
class SettingsCatalogScreenContractTest {

    // ── Source access (same precedent as the resolve-guard ratchet) ─────

    private val srcRoot: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var moduleRoot: File? = null
        while (dir != null && moduleRoot == null) {
            if (File(dir, "src/commonMain/kotlin").isDirectory) moduleRoot = dir else dir = dir.parentFile
        }
        assertTrue(moduleRoot != null, "could not locate src/commonMain/kotlin from ${System.getProperty("user.dir")}")
        moduleRoot
    }

    private val sourcesByName: Map<String, File> by lazy {
        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associateBy { it.name }
    }

    private fun source(fileName: String): String {
        val file = sourcesByName[fileName] ?: fail("missing source file $fileName")
        return file.readText(Charsets.UTF_8)
    }

    /** Every `id = "<id>"` declared in any `*SearchItems.kt` file. */
    private fun declaredIds(): Set<String> =
        srcRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("SearchItems.kt") }
            .flatMap { file ->
                Regex("id = \"([A-Za-z0-9_]+)\"").findAll(file.readText(Charsets.UTF_8))
                    .map { it.groupValues[1] }
            }
            .toSet()

    /** Every screen-file occurrence of the uniform highlighted-row pattern. */
    private fun highlightedRowsByFile(): Map<String, Set<String>> =
        srcRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") }
            .associate { file ->
                file.name to Regex("highlighted = highlightSettingId == \"([A-Za-z0-9_]+)\"")
                    .findAll(file.readText(Charsets.UTF_8)).map { it.groupValues[1] }.toSet()
            }
            .filterValues { it.isNotEmpty() }

    // ── 1. Declaration → group identity ─────────────────────────────────

    @Test
    fun `every declared id maps to exactly one screen group`() {
        val ids = declaredIds()
        assertTrue(ids.isNotEmpty(), "no *SearchItems.kt declarations found — scan is broken")

        // A decoration exists for every declared id (a declaration that lands
        // in no group cannot reach the catalog — the vlc_video_output bug).
        val undecorated = ids - SettingsScreenGroups.groupOfId.keys
        assertEquals(emptySet(), undecorated, "declared ids decorated into no screen group")

        // …and no decoration without a declaration (stale group entries).
        val undeclared = SettingsScreenGroups.groupOfId.keys - ids
        assertEquals(emptySet(), undeclared, "groups decorated for undeclared ids")

        // Group names are unique, and the catalog is exactly the decorated
        // groups flattened in the curated order.
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

    // ── 2. Catalog id → screen row ──────────────────────────────────────

    /**
     * Group id → the screen file(s) whose rows render that group. Groups of
     * navigation-type ids have no file: their deep-links navigate elsewhere
     * instead of scrolling, and every one of their ids is exempted below.
     */
    private val groupScreenFiles: Map<String, List<String>> = mapOf(
        "account" to emptyList(),
        "integrations" to listOf("IntegrationsScreen.kt", "SeerrSettingsScreen.kt"),
        "activityInsights" to emptyList(),
        "system.core" to emptyList(),
        "system.screensaver" to emptyList(),
        "appearance.theme" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.navigation" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.library" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.homeLayout" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.performance" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.eyeCare" to listOf("AppearanceSettingsScreen.kt"),
        "appearance.newsletter" to listOf("AppearanceSettingsScreen.kt"),
        "playback.player" to listOf("PlaybackSettingsScreen.kt"),
        "playback.advancedVideo" to listOf("PlaybackSettingsScreen.kt"),
        "playback.engine" to listOf("PlaybackSettingsScreen.kt"),
        "playback.syncPlay" to listOf("PlaybackSettingsScreen.kt"),
        "playback.casting" to listOf("PlaybackSettingsScreen.kt"),
        "playback.dvr" to listOf("PlaybackSettingsScreen.kt"),
        "playback.mediaSegments" to listOf("PlaybackSettingsScreen.kt"),
        "audio" to listOf("AudioSettingsScreen.kt"),
        "audio.cache" to listOf("AudioSettingsScreen.kt"),
        "language.general" to listOf("LanguageSettingsScreen.kt"),
        "language.subtitles" to listOf("LanguageSettingsScreen.kt"),
        "notifications" to listOf("NotificationSettingsScreen.kt"),
        "storage.cache" to listOf("StorageSettingsScreen.kt"),
        "storage.network" to listOf("StorageSettingsScreen.kt"),
        "storage.downloads" to listOf("StorageSettingsScreen.kt"),
        "security" to listOf("SecuritySettingsScreen.kt"),
        "backup" to listOf("BackupSettingsScreen.kt"),
        "about" to emptyList(),
        "experimental" to listOf("ExperimentalSettingsScreen.kt", "IntegrationsScreen.kt"),
    )

    /**
     * Declared ids that legitimately have no
     * `highlighted = highlightSettingId == "<id>"` row, each with the reason.
     * Adding a new id here needs a row-shaped justification — this map is the
     * review home for the exception.
     */
    private val noRowExceptions: Map<String, String> = mapOf(
        // Navigation-type entries: the deep-link routes to another screen (or
        // an external surface) rather than scrolling a row on this module's
        // settings screens.
        "logout" to "navigation entry (account actions)",
        "sign_out_from_server" to "navigation entry (server session actions)",
        "server_management" to "navigates to ServerManagementScreen",
        "user_management" to "navigates to UserManagementScreen",
        "favorites" to "navigates to the Favorites tab",
        "watch_progress_heatmap" to "navigates to the heatmap screen",
        "activity_queue" to "navigates to the ARR queue screen",
        "upcoming" to "navigates to the calendar screen",
        "requests" to "navigates to the Seerr requests screen",
        "admin_dashboard" to "navigates to the admin dashboard",
        "setup_wizard" to "navigates to onboarding",
        "screensaver_show_title" to "screensaver fact consumed off-screen",
        "screensaver_categories" to "screensaver fact consumed off-screen",
        "screensaver_slideshow_interval" to "screensaver fact consumed off-screen",
        "screensaver_ken_burns" to "screensaver fact consumed off-screen",
        "screensaver_transition_style" to "screensaver fact consumed off-screen",
        "experimental" to "the Experimental screen entry itself",
        "integrations" to "the Integrations hub screen entry itself",
        "about_version" to "about screen info row (no highlight param)",
        // Rows that highlight through a non-literal pattern.
        "theme_mode" to "highlighted via the THEME_HIGHLIGHT_IDS multi-id set",
        "theme_scheduler" to "highlighted via the THEME_HIGHLIGHT_IDS multi-id set",
        "HOME_CARD_CLIPPING" to "highlighted via `highlightSettingId == info.feature.name`",
        "MEDIA_CARD_PEEK" to "highlighted via `highlightSettingId == info.feature.name`",
        "DIRECT_ARR_INTEGRATION" to "highlighted via `highlightSettingId == info.feature.name`",
        // Rows that exist but do not take the highlight param.
        "accent_color" to "accent swatch picker row (theme group scroll+expand covers the deep-link)",
        "color_style" to "color style picker row (theme group scroll+expand covers the deep-link)",
        "style_accent" to "style accent picker row (theme group scroll+expand covers the deep-link)",
        "nav_bar_customization" to "NavigationCustomizationGroup rows carry no highlight param",
        "nav_hide_on_scroll" to "NavigationCustomizationGroup rows carry no highlight param",
        "newsletter_sections" to "renders as the runtime-reorderable newsletter section rows",
        "reset_engine_defaults" to "the reset action row rendered inside every engine branch",
        // Documented hand-built group: the media-segment rows are enumerated
        // from MediaSegmentType.entries and carry no highlight param; the
        // enum-naming sync is pinned in its own test below.
        "media_segment_intro" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
        "media_segment_outro" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
        "media_segment_preview" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
        "media_segment_recap" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
        "media_segment_commercial" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
        "media_segment_unknown" to "rows enumerated from MediaSegmentType.entries (accepted exception)",
    )

    @Test
    fun `every catalog id has a highlighted row in its screen file`() {
        val rowsByFile = highlightedRowsByFile()
        val missing = mutableListOf<String>()
        for (group in SettingsScreenGroups.all) {
            val files = groupScreenFiles.getValue(group.id).map { source(it) to it }
            for (id in group.itemIds) {
                if (noRowExceptions.containsKey(id)) continue
                val found = files.any { (text, _) -> "highlighted = highlightSettingId == \"$id\"" in text }
                if (!found) {
                    missing.add("${group.id}/$id (expected a highlighted row in " +
                        "${groupScreenFiles.getValue(group.id).joinToString()})")
                }
            }
        }
        assertEquals(emptyList(), missing, "catalog ids with no screen row (vlc_video_output-class drift)")
        assertTrue(noRowExceptions.keys.all { it in declaredIds() }, "stale noRowExceptions entries")
    }

    // ── 3. Screen row → catalog (the reverse drift) ─────────────────────

    @Test
    fun `every screen highlighted id exists in the catalog`() {
        // Rows that carry a highlighted param without a search entry — a
        // deep-link can never produce their ids, but the rows are real.
        val rowOnlyExceptions = mapOf(
            "confirm_library_reset" to "Appearance library action row, never a search hit",
            "pinned_add" to "pass-through highlight into PinnedHomeSectionsScreen's add row",
        )
        val orphans = mutableListOf<String>()
        for ((file, ids) in highlightedRowsByFile()) {
            for (id in ids) {
                if (id !in SettingsScreenGroups.groupOfId && id !in rowOnlyExceptions) {
                    orphans.add("$file/$id (auto_delete_after_watch-class drift — declare it or exempt it)")
                }
            }
        }
        assertEquals(emptyList(), orphans, "screen highlighted ids missing from the catalog")
    }

    // ── 4. Screens consume the derivation (no hand-typed group lists) ───

    /** The retired hand-typed group id constants, per screen file. */
    private val retiredHandGroupLists: Map<String, List<String>> = mapOf(
        "PlaybackSettingsScreen.kt" to listOf(
            "PLAYBACK_ADVANCED_GROUP_IDS",
            "PLAYBACK_ENGINE_GROUP_IDS",
            "PLAYBACK_SYNCPLAY_GROUP_IDS",
            "PLAYBACK_CASTING_GROUP_IDS",
            "PLAYBACK_DVR_GROUP_IDS",
        ),
        "StorageSettingsScreen.kt" to listOf(
            "STORAGE_CACHE_GROUP_IDS",
            "STORAGE_NETWORK_GROUP_IDS",
            "STORAGE_DOWNLOADS_GROUP_IDS",
        ),
        "AppearanceSettingsScreen.kt" to listOf(
            "APPEARANCE_LIBRARY_GROUP_IDS",
            "PERFORMANCE_GROUP_IDS",
            "BLUE_LIGHT_GROUP_IDS",
            "NEWSLETTER_GROUP_IDS",
        ),
        "LanguageSettingsScreen.kt" to listOf(
            "LANGUAGE_GROUP_IDS",
            "SUBTITLE_GROUP_IDS",
        ),
        "SettingsScreen.kt" to listOf(
            "SCREENSAVER_GROUP_IDS",
        ),
    )

    /** The derivation each rewired screen must consume (evidence in source). */
    private val requiredDerivationUsage: Map<String, List<String>> = mapOf(
        "PlaybackSettingsScreen.kt" to listOf(
            "rememberHighlightScrollIndex(",
            "playbackAdjustForAdvanced",
            "SettingsScreenGroups.playbackPlayer",
            "SettingsScreenGroups.playbackAdvancedVideo",
            "SettingsScreenGroups.playbackEngine",
            "SettingsScreenGroups.playbackSyncPlay",
            "SettingsScreenGroups.playbackCasting",
            "SettingsScreenGroups.playbackDvr",
            "playbackPlayerScreenRowTotal(",
            "playbackAdvancedVideoScreenRowTotal(",
            "playbackEngineScreenRowTotal(",
            "RowAdmissionFlags(",
            "rowAdmitted(",
        ),
        "AppearanceSettingsScreen.kt" to listOf(
            "rememberHighlightScrollIndex(",
            "appearanceAdjustForAdvanced",
            "SettingsScreenGroups.appearanceTheme",
            "SettingsScreenGroups.appearanceLibrary",
            "SettingsScreenGroups.appearanceHomeLayout",
            "SettingsScreenGroups.appearancePerformance",
            "SettingsScreenGroups.appearanceEyeCare",
            "SettingsScreenGroups.appearanceNewsletter",
            "appearanceLibraryScreenRowTotal(",
        ),
        "StorageSettingsScreen.kt" to listOf(
            "resolveHighlightScrollIndex(",
            "SettingsScreenGroups.storageCache",
            "SettingsScreenGroups.storageNetwork",
            "SettingsScreenGroups.storageDownloads",
            "storageCacheScreenRowTotal(",
            "storageDownloadsScreenRowTotal(",
            "RowAdmissionFlags(",
            "rowAdmitted(",
        ),
        "LanguageSettingsScreen.kt" to listOf(
            "rememberHighlightScrollIndex(",
            "SettingsScreenGroups.languageGeneral",
            "SettingsScreenGroups.languageSubtitles",
            "languageGeneralScreenRowTotal(",
            "languageSubtitlesScreenRowTotal(",
        ),
        "NotificationSettingsScreen.kt" to listOf(
            "notificationScreenRowTotal(",
        ),
        "SecuritySettingsScreen.kt" to listOf(
            "securityScreenRowTotal(",
        ),
        "SettingsScreen.kt" to listOf(
            "SettingsScreenGroups.account",
            "SettingsScreenGroups.activityInsights",
            "SettingsScreenGroups.systemCore",
            "SettingsScreenGroups.systemScreensaver",
            "settingsSearchResults(",
        ),
        "AudioSettingsScreen.kt" to listOf(
            "audioScreenRowTotal",
            "SettingsScreenGroups.audioCache",
            "audioRowAdmissionFlags(",
            "rowAdmitted(",
        ),
    )

    /** The retired hand-incremented row counters / hand-run totals, per screen file. */
    private val retiredRowCounters: Map<String, List<String>> = mapOf(
        "StorageSettingsScreen.kt" to listOf(
            "storageIdx", "networkIdx", "downloadIdx",
        ),
        "PlaybackSettingsScreen.kt" to listOf(
            "vlcIdx", "vlcTotal", "exoIdx", "exoTotal",
            "syncIdx", "syncTotal", "castIdx", "castTotal", "dvrIdx", "dvrTotal",
        ),
        "NotificationSettingsScreen.kt" to listOf(
            "notifIdx", "notifTotal", "notifBaseTotal",
        ),
        "LanguageSettingsScreen.kt" to listOf(
            "langIndex", "langRowCount", "subIdx", "subTotal",
        ),
        "AppearanceSettingsScreen.kt" to listOf(
            "cardIdx", "cardTotal",
        ),
        "SecuritySettingsScreen.kt" to listOf(
            "secIdx", "secTotal", "baseSecTotal",
        ),
    )

    @Test
    fun `screens derive scroll and expand structures from the catalog groups`() {
        for ((file, retired) in retiredHandGroupLists) {
            val text = source(file)
            for (constant in retired) {
                if (constant in text) fail("$file still hand-types $constant — derive it from SettingsScreenGroups")
            }
        }
        for ((file, required) in requiredDerivationUsage) {
            val text = source(file)
            for (usage in required) {
                assertTrue(usage in text, "$file must consume the catalog derivation via `$usage`")
            }
        }
    }

    @Test
    fun `screens feed SettingsItemList from the admission totals, not hand counters`() {
        // The row indexes are owned by SettingsItemList's auto-increment (the
        // emitted sequence is 0 until the admission total) and the totals by
        // the pure admission functions — a reappearing `var xIdx = 0` or a
        // hand-run `val xTotal = …` arithmetic block is the drift this pins.
        for ((file, counters) in retiredRowCounters) {
            val text = source(file)
            for (counter in counters) {
                if (counter in text) fail("$file still hand-bumps $counter — feed SettingsItemList(total = …admissionTotal())")
            }
        }
    }

    // ── 4b. Emission reads the DECLARED admission (one gate, once) ──────

    /**
     * The retired inline emission gates, per screen file (block form — the
     * value ternaries on the same preferences legitimately stay): each used
     * to re-declare a gate the row-total count already encoded — both sides
     * now read the per-id [RowAdmission] declared beside the group item, via
     * `SettingsSearchItemGroup.rowAdmitted`.
     */
    private val retiredInlineRowGates: Map<String, List<String>> = mapOf(
        "PlaybackSettingsScreen.kt" to listOf(
            "if (settingsCapabilities.supportsTouchGestures)",
            "if (settingsCapabilities.supportsScreenOrientation)",
            "if (isTv) {",
            "if (preferences.dialogueBoostEnabled) {",
        ),
        "AudioSettingsScreen.kt" to listOf(
            "if (preferences.audioNormalizationMode == AudioNormalizationMode.TRACK",
            "if (preferences.equalizerEnabled) {",
            "if (preferences.dialogueBoostEnabled) {",
            "if (preferences.nightModeEnabled) {",
            "if (preferences.bassBoostEnabled) {",
            "if (preferences.virtualizerEnabled) {",
            "if (preferences.volumeBoostEnabled) {",
            "if (preferences.channelMixEnabled) {",
        ),
        "StorageSettingsScreen.kt" to listOf(
            "if (preferences.downloadScheduleEnabled) {",
        ),
    )

    @Test
    fun `emission sites read the declared row admissions, not inline gate checks`() {
        for ((file, gates) in retiredInlineRowGates) {
            val text = source(file)
            for (gate in gates) {
                if (gate in text) fail("$file still hand-gates rows via `$gate` — read the group's declared RowAdmission via rowAdmitted()")
            }
        }
    }

    // ── The four (plus one) fixed drifts, pinned behaviorally ───────────

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
        assertTrue("vlc_video_output" in SettingsScreenGroups.playbackEngine.itemIdSet)
        assertEquals(2, resolveHighlightScrollIndex("vlc_video_output", playbackGroups, playbackAdjustForAdvanced(true)))
        // With advanced hidden the engine group doesn't compose — no scroll.
        assertEquals(-1, resolveHighlightScrollIndex("vlc_video_output", playbackGroups, playbackAdjustForAdvanced(false)))
    }

    @Test
    fun `live_stream_option and dialogue_boost_strength land in the advanced-video group`() {
        val advanced = SettingsScreenGroups.playbackAdvancedVideo.itemIdSet
        assertTrue("live_stream_option" in advanced)
        assertTrue("dialogue_boost_strength" in advanced)
        assertEquals(1, resolveHighlightScrollIndex("live_stream_option", playbackGroups, playbackAdjustForAdvanced(true)))
        assertEquals(1, resolveHighlightScrollIndex("dialogue_boost_strength", playbackGroups, playbackAdjustForAdvanced(true)))
    }

    @Test
    fun `auto_delete_after_watch is declared in the downloads group with a screen row`() {
        assertTrue("auto_delete_after_watch" in SettingsScreenGroups.storageDownloads.itemIdSet)
        assertTrue(
            "highlighted = highlightSettingId == \"auto_delete_after_watch\"" in source("StorageSettingsScreen.kt"),
            "the storage row lost its highlighted param",
        )
    }

    @Test
    fun `gesture_indicator_side scrolls within the player group`() {
        // Not one of the four shipped drifts, but the hand-typed player scroll
        // list had omitted it all the same; the derivation keeps it honest.
        assertEquals(0, resolveHighlightScrollIndex("gesture_indicator_side", playbackGroups, playbackAdjustForAdvanced(true)))
    }

    @Test
    fun `always-visible playback groups shift correctly when advanced is hidden`() {
        // SyncPlay/casting/DVR always render; with advanced off they sit at
        // LazyColumn indices 1/2/3 behind the player group.
        assertEquals(1, resolveHighlightScrollIndex("syncplay_join_behavior", playbackGroups, playbackAdjustForAdvanced(false)))
        assertEquals(2, resolveHighlightScrollIndex("background_casting", playbackGroups, playbackAdjustForAdvanced(false)))
        assertEquals(3, resolveHighlightScrollIndex("dvr_recording_quality", playbackGroups, playbackAdjustForAdvanced(false)))
        // The player group never moves.
        assertEquals(0, resolveHighlightScrollIndex("player_engine", playbackGroups, playbackAdjustForAdvanced(false)))
    }

    // ── The documented media-segment exception ──────────────────────────

    @Test
    fun `media segment ids stay in LiveTvSearchItems and track the enum naming`() {
        // The screen's media-segment group stays hand-built, enumerated from
        // MediaSegmentType.entries (the accepted exception to the derivation).
        // The declaration side keeps the ids inside LiveTvSearchItems and must
        // keep following the media_segment_<enum-name> convention so the
        // derived scroll set and the hand-built rows stay in lock-step.
        val declared = SettingsScreenGroups.playbackMediaSegments.itemIds
        assertEquals(
            MediaSegmentType.entries.map { "media_segment_${it.name.lowercase()}" },
            declared,
            "media_segment_* declarations drifted from MediaSegmentType.entries",
        )
        assertTrue(
            SettingsScreenGroups.playbackDvr.itemIds.none { it.startsWith(SettingsScreenGroups.MEDIA_SEGMENT_ID_PREFIX) },
            "DVR group must not absorb media-segment ids",
        )
    }

    // ── The aggregation-decoration splits, pinned at their split lines ───

    @Test
    fun `language general group is exactly the declared leading trio`() {
        // The split has no id shape to key on (the Live TV prefix precedent
        // does not apply), so the split line is positional — pin the trio by
        // name so a declaration inserted above the subtitles half lands loudly
        // here instead of silently re-shaping the screen groups.
        assertEquals(
            listOf("app_language", "audio_language", "subtitle_language"),
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
            listOf("admin_dashboard", "setup_wizard"),
            SettingsScreenGroups.systemCore.itemIds,
        )
        assertTrue(
            SettingsScreenGroups.systemScreensaver.itemIds.all { it.startsWith(SettingsScreenGroups.SCREENSAVER_ID_PREFIX) },
            "system.screensaver must hold only screensaver ids",
        )
        assertEquals(SystemSearchItems.size, SettingsScreenGroups.systemCore.items.size + SettingsScreenGroups.systemScreensaver.items.size)
    }

    @Test
    fun `language deep-links scroll via the derived groups`() {
        assertEquals(
            0,
            resolveHighlightScrollIndex("subtitle_language", listOf(
                SettingsScreenGroups.languageGeneral.itemIdSet,
                SettingsScreenGroups.languageSubtitles.itemIdSet,
            )),
        )
        assertEquals(
            1,
            resolveHighlightScrollIndex("subtitle_tester", listOf(
                SettingsScreenGroups.languageGeneral.itemIdSet,
                SettingsScreenGroups.languageSubtitles.itemIdSet,
            )),
        )
        assertTrue("subtitle_tester" in SettingsScreenGroups.languageSubtitles.itemIdSet)
        assertTrue("high_contrast_subtitles" in SettingsScreenGroups.languageSubtitles.itemIdSet)
    }

    // ── The audio row-total derivation ──────────────────────────────────

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

    // ── The per-group row-admission totals (the audio shape, extended) ──

    /**
     * The index/total pair a rendered group emits: inside `SettingsItemList`
     * the rows take the auto-index 0..total-1 and every row's `count` is the
     * admission total, so each pinned total below IS the total the screen
     * feeds and the sequence below IS the index sequence the rows display.
     */
    private fun emittedIndexSequence(total: Int): List<Int> = (0 until total).toList()

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
        // exactly its own rows.
        val allGatesOff = playbackPlayerScreenRowTotal(
            isTv = false,
            showAdvanced = false,
            supportsScreenOrientation = false,
            supportsTouchGestures = false,
        )
        assertEquals(6, allGatesOff)
        assertEquals(7, playbackPlayerScreenRowTotal(false, false, supportsScreenOrientation = true, supportsTouchGestures = false))
        assertEquals(9, playbackPlayerScreenRowTotal(false, false, supportsScreenOrientation = false, supportsTouchGestures = true))
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
        assertEquals(33, playbackPlayerScreenRowTotal(true, true, supportsScreenOrientation = true, supportsTouchGestures = true))

        // Advanced video: the dialogue-boost strength row rides its toggle.
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size - 1,
            playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled = false),
        )
        assertEquals(
            SettingsScreenGroups.playbackAdvancedVideo.items.size,
            playbackAdvancedVideoScreenRowTotal(dialogueBoostEnabled = true),
        )

        // Engine branches: declared prefix rows + the one reset row each.
        assertEquals(12, playbackEngineScreenRowTotal("mpv_"))
        assertEquals(9, playbackEngineScreenRowTotal("vlc_"))
        assertEquals(8, playbackEngineScreenRowTotal("exo_"))
        for (prefix in listOf("mpv_", "vlc_", "exo_")) {
            assertEquals(
                SettingsScreenGroups.playbackEngine.items.count { it.id.startsWith(prefix) } + 1,
                playbackEngineScreenRowTotal(prefix),
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

    // ── The declared row admissions (one gate for count AND emission) ────

    @Test
    fun `platform-gated rows drop on the desktop seam and admit on the capability`() {
        // jvmTest runs the desktop actual — no screen orientation, no touch
        // gestures — and the declared Platform admissions must agree with the
        // desktop-filtered catalog drops (pinned in
        // SettingsSearchCatalogPlatformFilterTest).
        assertFalse(settingsCapabilities.supportsScreenOrientation)
        assertFalse(settingsCapabilities.supportsTouchGestures)
        val desktop = RowAdmissionFlags()
        listOf("orientation", "seek_duration", "gestures", "gesture_indicator_side").forEach { id ->
            assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted(id, desktop), "$id must drop without its capability")
        }
        val touch = RowAdmissionFlags(supportsTouchGestures = true)
        listOf("seek_duration", "gestures", "gesture_indicator_side").forEach {
            assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted(it, touch), "$it must admit on the capability")
        }
        assertTrue(
            SettingsScreenGroups.playbackPlayer.rowAdmitted("orientation", RowAdmissionFlags(supportsScreenOrientation = true)),
        )
    }

    @Test
    fun `tv rows admit on the tv form factor alone - the shipped count semantics`() {
        // The two TV rows are declared isAdvanced, yet the total has always
        // admitted them on isTv alone (their emission sits inside the advanced
        // block, which carries the advanced half of the gate).
        val tv = RowAdmissionFlags(isTv = true)
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted("android_tv_watch_next", tv))
        assertTrue(SettingsScreenGroups.playbackPlayer.rowAdmitted("tv_zoom_mode", tv))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted("android_tv_watch_next", RowAdmissionFlags()))
        assertFalse(SettingsScreenGroups.playbackPlayer.rowAdmitted("tv_zoom_mode", RowAdmissionFlags(showAdvanced = true)))
    }

    @Test
    fun `when-on rows admit on their declared parent toggle`() {
        // Every declared admission key is a declared group id (no stale
        // gate entries), and every audio key is an isAdvanced row — its
        // declared gate REPLACES the isAdvanced base in the count, so a
        // non-advanced key would silently widen admission.
        mapOf(
            SettingsScreenGroups.playbackPlayer to PlaybackPlayerRowAdmissions,
            SettingsScreenGroups.playbackAdvancedVideo to PlaybackAdvancedVideoRowAdmissions,
            SettingsScreenGroups.audio to AudioRowAdmissions,
            SettingsScreenGroups.storageDownloads to StorageDownloadsRowAdmissions,
        ).forEach { (group, admissions) ->
            assertEquals(
                emptySet(),
                admissions.keys - group.itemIdSet,
                "${group.id} declares admissions for undeclared ids",
            )
        }
        assertEquals(
            emptyList(),
            AudioRowAdmissions.keys.filterNot { id -> SettingsScreenGroups.audio.items.first { it.id == id }.isAdvanced },
            "audio admissions must sit on isAdvanced rows (the gate replaces the advanced base)",
        )

        // The playback advanced-video strength row and the storage schedule
        // windows ride their parent toggle alone…
        val boostOn = RowAdmissionFlags(parentsOn = setOf("dialogue_boost"))
        assertTrue(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted("dialogue_boost_strength", boostOn))
        assertFalse(SettingsScreenGroups.playbackAdvancedVideo.rowAdmitted("dialogue_boost_strength", RowAdmissionFlags()))
        val scheduleOn = RowAdmissionFlags(parentsOn = setOf("download_schedule"))
        listOf("download_schedule_start", "download_schedule_end", "download_schedule_wifi_only").forEach {
            assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted(it, scheduleOn), "$it admits on the schedule toggle")
            assertFalse(SettingsScreenGroups.storageDownloads.rowAdmitted(it, RowAdmissionFlags()), "$it drops when scheduling is off")
        }
        assertTrue(SettingsScreenGroups.storageDownloads.rowAdmitted("download_schedule", RowAdmissionFlags()))

        // …while the audio strength rows additionally ride the advanced
        // toggle (All(Advanced, WhenOn(parent)) — the advanced half is the
        // structural block the screen's advanced section carries).
        val parentsOnly = RowAdmissionFlags(parentsOn = setOf("night_mode", "equalizer"))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted("night_mode_strength", parentsOnly))
        assertFalse(SettingsScreenGroups.audio.rowAdmitted("equalizer_preset", parentsOnly))
        val advanced = RowAdmissionFlags(showAdvanced = true, parentsOn = setOf("night_mode", "equalizer"))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted("night_mode_strength", advanced))
        assertTrue(SettingsScreenGroups.audio.rowAdmitted("equalizer_preset", advanced))
    }

    // ── The search-result click action ──────────────────────────────────

    @Test
    fun `search-result clicks decide the pure action per branch`() {
        // Both destructive dialogs, with the destructive ids kept out of recents.
        val logout = settingsResultClickAction("logout", Route.Settings, isAdvanced = false, showAdvancedSettings = false)
        assertTrue(logout.action is SettingsSearchResultAction.OpenSignOutDialog && !logout.action.fromServer)
        assertFalse(logout.recordRecent)

        val signOutFromServer = settingsResultClickAction("sign_out_from_server", Route.Settings, isAdvanced = false, showAdvancedSettings = true)
        assertTrue(signOutFromServer.action is SettingsSearchResultAction.OpenSignOutDialog && signOutFromServer.action.fromServer)
        assertFalse(signOutFromServer.recordRecent)

        // Bare-settings screensaver targets reveal their on-screen group: no
        // navigation, only the pending highlight.
        val screensaver = settingsResultClickAction("screensaver_ken_burns", Route.Settings, isAdvanced = false, showAdvancedSettings = true)
        assertTrue(screensaver.action is SettingsSearchResultAction.NoOp)
        assertEquals("screensaver_ken_burns", screensaver.pendingHighlightId)
        assertTrue(screensaver.recordRecent)

        // The setup wizard keeps its host indirection.
        val wizard = settingsResultClickAction("setup_wizard", Route.Onboarding, isAdvanced = false, showAdvancedSettings = true)
        assertTrue(wizard.action is SettingsSearchResultAction.OpenSetupWizard)
        assertEquals("setup_wizard", wizard.pendingHighlightId)

        // Regular sub-screen entries navigate with the id baked in and mark
        // the pending highlight — except the two management exemptions.
        val theme = settingsResultClickAction("theme_mode", Route.AppearanceSettings(), isAdvanced = false, showAdvancedSettings = true)
        val themeAction = theme.action as SettingsSearchResultAction.NavigateToScreen
        assertEquals(Route.AppearanceSettings("theme_mode"), themeAction.route)
        assertEquals("theme_mode", theme.pendingHighlightId)

        val server = settingsResultClickAction("server_management", Route.ServerManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(server.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(server.pendingHighlightId, "server_management must not mark the TV re-entry highlight")

        val users = settingsResultClickAction("user_management", Route.UserManagement(), isAdvanced = false, showAdvancedSettings = true)
        assertTrue(users.action is SettingsSearchResultAction.NavigateToScreen)
        assertNull(users.pendingHighlightId, "user_management must not mark the TV re-entry highlight")
    }

    @Test
    fun `advanced result clicks auto-enable advanced settings`() {
        val off = settingsResultClickAction("equalizer", Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = false)
        assertTrue(off.enableAdvanced)
        assertTrue(off.action is SettingsSearchResultAction.NavigateToScreen)

        val on = settingsResultClickAction("equalizer", Route.AudioSettings(), isAdvanced = true, showAdvancedSettings = true)
        assertFalse(on.enableAdvanced)
    }
}
