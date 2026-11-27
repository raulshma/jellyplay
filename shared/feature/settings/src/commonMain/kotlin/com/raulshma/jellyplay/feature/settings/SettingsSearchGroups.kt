package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.navigation.withHighlightSettingId
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource

/**
 * One screen group of settings rows: the declaration-list identity a
 * per-screen `*SearchItems` list is decorated with at the aggregation site
 * (decision Q11a — no field is added to `SettingsSearchItem`, which lives in
 * core/ui and must not widen).
 *
 * A group is the single declaration of that screen region's facts. The
 * owning screen derives the parallel id lists from it — the deep-link scroll
 * group list, the `initiallyExpanded`
 * expand sets, and the `SettingsItemList(total = …)` row totals — so the
 * catalog and the UI cannot drift apart (a row can be declared but in no
 * screen group, so a deep-link neither scrolls nor expands).
 *
 * A single screen group may be fed by more than one adjacent declaration
 * list (the playback engine group spans the MPV/VLC/ExoPlayer lists), and a
 * declaration list may be split along the screen-group line when a screen
 * group renders only a subset of it (the playback player vs advanced-video
 * split). One declared id maps to exactly one group — pinned by
 * `SettingsCatalogScreenContractTest`.
 *
 * **Fused-row rollout state:** every domain but the experimental screen has
 * converted — its groups derive items AND admissions from the domain's fused
 * rows in one act ([List.asRowGroup], in the domain's `*SettingsRows.kt`
 * file), so the decoration here only NAMES the derived group. The
 * experimental screen keeps the spec-direct binding derivation (its five
 * rows' search faces derive from
 * [com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs]
 * through [SettingsSearchBinding]) and decorates with [asSearchGroup] — no
 * admissions, no records.
 */
internal class SettingsSearchItemGroup(
    /** Stable group name, e.g. `"playback.engine"`. Unique across [SettingsScreenGroups.all]. */
    val id: String,
    /** The declared items, in catalog order. */
    val items: List<SettingsSearchItem>,
    /**
     * The per-id declared row admissions — the ONE declaration of every gate
     * a gated row's count AND its emission `if` read (the string-keyed
     * `when` arms / inline capability checks this replaces).
     */
    val admissions: Map<String, RowAdmission> = emptyMap(),
    /**
     * The group-card search face (ADR-0017's group-grain rule): the title the
     * group's rendered card carries on its hosting screen, turning the card
     * itself into ONE searchable item — today declared only on the
     * advancedOnly groups (the "hidden" labeled cards the power-user gate
     * hides whole). Null on every group that renders no labeled card face.
     * The [cardItem] derivation makes the search hit land on the group's
     * first member row (expand + scroll + glow through the existing
     * row-id highlight path); the faces reuse the screen-emission strings —
     * no catalog twin keys.
     */
    val cardTitleRes: StringResource? = null,
    /**
     * The card hit's subtitle resource — the hosting screen's title, so the
     * two same-labeled "Fine-tuning" cards disambiguate in the hit list.
     * Required beside [cardTitleRes] (the search item's subtitle slot is
     * non-nullable) and, like it, reuses an existing screen string.
     */
    val cardSubtitleRes: StringResource? = null,
    /** Fuzzy-match keywords for the card hit — required (the catalog ratchet). */
    val cardKeywords: List<String> = emptyList(),
) {
    /** The declared ids, in catalog order — the derivation source for scroll lists. */
    val itemIds: List<String> = items.map { it.id }

    /** [itemIds] as a set — the derivation source for expand checks. */
    val itemIdSet: Set<String> = itemIds.toSet()

    /**
     * The group's card search hit, derived once at group init — null on
     * every group without a [cardTitleRes]. GROUP-grain: the id IS the
     * group's id (never a row id — the collision leg below dies loudly), it
     * enters no [itemIds]/admissions (the screens' scroll, expand and count
     * derivations stay row-id-shaped), and the hit lands on the group's
     * FIRST member row id baked into its route — zero new landing machinery.
     * The group derives honestly or dies at init, in
     * [SettingsSearchBinding] culture.
     */
    val cardItem: SettingsSearchItem? = deriveCardItem()

    private fun deriveCardItem(): SettingsSearchItem? {
        if (cardTitleRes == null) {
            require(cardSubtitleRes == null && cardKeywords.isEmpty()) {
                "settings group \"$id\" declares card faces without a card title"
            }
            return null
        }
        require(cardSubtitleRes != null) {
            "settings group \"$id\" declares a card title without a card subtitle"
        }
        require(cardKeywords.isNotEmpty()) {
            "settings group \"$id\" declares a card face without keywords (the catalog ratchet requires them)"
        }
        require(items.isNotEmpty()) {
            "settings group \"$id\" declares a card face with no member rows"
        }
        require(id !in itemIdSet) {
            "settings group \"$id\" declares a card face whose card id collides with a member row id"
        }
        val routeClasses = items.map { it.route::class }.toSet()
        require(routeClasses.size == 1) {
            "settings group \"$id\" declares a card face whose member rows do not share one " +
                "hosting route: " + items.map { "${it.id}→${it.route::class.simpleName}" }
        }
        val platforms = items.map { it.platforms }.toSet()
        require(platforms.size == 1) {
            "settings group \"$id\" declares a card face whose member rows disagree on platforms"
        }
        val categories = items.map { it.categoryRes }.toSet()
        require(categories.size == 1) {
            "settings group \"$id\" declares a card face whose member rows disagree on category"
        }
        val first = items.first()
        // Faces from the members' shared facts (category/platform/icon/route
        // class), isAdvanced from the declared slot shape: card faces are
        // declared ONLY on advancedOnly groups — the advancedOnly flag the
        // hosting screen's asScreenGroup slot carries — so the hit always
        // auto-arms the power-user gate, exactly like its member rows' hits.
        return SettingsSearchItem(
            id = id,
            titleRes = cardTitleRes,
            subtitleRes = cardSubtitleRes,
            categoryRes = first.categoryRes,
            keywords = cardKeywords,
            route = first.route.withHighlightSettingId(first.id),
            icon = first.icon,
            isAdvanced = true,
            platforms = first.platforms,
        )
    }

    /** The declared admission of one row id — null where no gate is declared. */
    fun admissionOf(id: String): RowAdmission? = admissions[id]

    /**
     * The emission-side predicate: the row's declared admission under
     * [flags], true where no gate is declared (the screen's structural
     * blocks — advanced sections, parent-toggle blocks — carry their own
     * gates around the call sites).
     */
    fun rowAdmitted(id: String, flags: RowAdmissionFlags): Boolean =
        admissions[id]?.admitted(flags) ?: true
}

/** Decorates a declaration list as the named screen group its screen renders. */
internal fun List<SettingsSearchItem>.asSearchGroup(
    id: String,
    admissions: Map<String, RowAdmission> = emptyMap(),
): SettingsSearchItemGroup = SettingsSearchItemGroup(id, this, admissions)

/**
 * The landing row id a catalog id deep-links to: a group-card search item's
 * id lands on its group's FIRST member row id (the [SettingsSearchItemGroup.cardItem]
 * route's baked highlight — the expand + scroll + glow path is the existing
 * row-id machinery), every row id lands on itself. Null when the id is no
 * card id — the callers fold with identity.
 */
internal fun groupCardLandingRowId(itemId: String): String? =
    SettingsScreenGroups.all.firstOrNull { group -> group.cardItem?.id == itemId }
        ?.itemIds?.firstOrNull()

/**
 * The aggregation decoration: every domain's declaration becomes the named
 * group of the screen region that renders its rows — either by pointing at
 * the domain's derived group val (the fused domains) or by decorating the
 * spec-direct derivation (the experimental screen).
 * [SettingsSearchCatalog.items] is the flat concatenation of [all] — the
 * curated flat order the search matcher tie-breaks on — so decorating and
 * aggregating are the same act, and a declaration that lands in no group
 * simply cannot reach the catalog.
 */
internal object SettingsScreenGroups {

    // ── Main settings screen (navigation-type ids; their rows are the
    //    deep-link targets on other screens, so they have none here) ────
    val account = AccountGroup
    val integrations = IntegrationsGroup
    val activityInsights = ActivityInsightsGroup

    /**
     * The documented split of the system declaration: the leading navigation
     * pair (admin dashboard, setup wizard) renders as the main screen's System
     * group, while the `screensaver_*` rows render as that screen's on-screen
     * screensaver group. The list itself stays whole — the partition happens
     * in SystemSettingsRows.kt, at the group derivation (the Live TV
     * media-segment precedent: a prefix split, no second declaration list).
     */
    val systemCore = SystemCoreGroup
    val systemScreensaver = SystemScreensaverGroup

    /**
     * the desktop idle "Ready to play" ambient screen rows — their own
     * screen group, hosted on the Ambient screen beside the TV dream group
     * (ADR-0017), so neither group's row totals entangle.
     */
    val systemIdleAmbient = SystemIdleAmbientGroup

    /**
     * the desktop Discord Rich Presence toggle (feature 4.2) — its own
     * capability-gated group, hosted on the Integrations screen (ADR-0017).
     */
    val systemDiscordPresence = SystemDiscordPresenceGroup

    /**
     * the desktop playback-event shell-hook rows (feature 4.3) — the master
     * toggle plus the five mpv-shim-named commands, one group, hosted on the
     * Integrations screen (ADR-0017).
     */
    val systemHooks = SystemHooksGroup

    /**
     * the JellyPlay companion-plugin settings-sync rows — the
     * opt-in toggle + sync-now action, hosted on the plugin's sync screen
     * ([Route.JellyPlaySync]).
     */
    val jellyplaySync = JellyPlaySyncGroup

    /**
     * the JellyPlay companion-plugin per-feature switches — one toggle row
     * per user-facing capability, hosted on the plugin screen
     * ([Route.JellyPlayPluginSettings], ADR-0017: the root's inline plugin
     * section became one entrance + this screen). The whole group hides when
     * the plugin is UNAVAILABLE/UNKNOWN; `transcodes` additionally only for
     * admins.
     */
    val jellyplayFeatures = JellyPlayFeaturesGroup

    // ── The home domain (ADR-0017's hub + three leaves) ────────────────
    // The former `home.display` group's id retired with the split (the
    // phase-2/3 pattern): its mainstream remainder derives as
    // `home.displayMain`, its tuning subset as the hub's advanced-gated
    // `home.tuning` (the glossary's declared hub exception, the appearance
    // newsletter precedent), and its hidden/restore trio as the new
    // `home.curation`. `home.nextUp`/`home.layout`/`home.cards` moved whole
    // (the cards group gaining its card-chrome siblings from the display
    // remainder).

    /** The hub screen's one group: the three composite entrance rows. */
    val homeHub = HomeHubGroup
    /** Derives from [HomeTuningRows]: the eight tuning rows — the hub's advanced-gated Fine-tuning group. */
    val homeTuning = HomeTuningGroup
    /** Derives from [HomeDisplayMainRows]: the mainstream presentation rows — the Home display leaf. */
    val homeDisplayMain = HomeDisplayMainGroup
    val homeNextUp = HomeNextUpGroup
    val homeLayout = HomeLayoutGroup
    /** Derives from [HomeCurationRows]: the rows-style toggle + the hidden/exclusion restore trio — a Rows & layout leaf group. */
    val homeCuration = HomeCurationGroup
    /** Derives from [HomeCardsRows]: the badge/spoiler/trickplay toggles + the card-chrome trio — the Cards & badges leaf. */
    val homeCards = HomeCardsGroup

    // ── The appearance domain (ADR-0017's hub + four leaves) ────────────
    // The appearance domain converted to the fused rows
    // (AppearanceSettingsRows.kt): the hub's entrance group, the
    // former "Library & Cards" group's split (library views + detail
    // screen), and the theme/performance/eye-care/navigation/newsletter
    // groups all derive items AND admissions from the row lists in one act
    // ([List.asRowGroup]). The hub keeps the advanced-gated newsletter group
    // on the hub screen — the glossary's declared hub exception.

    /** The hub screen's one group: the four composite entrance rows. */
    val appearanceHub = AppearanceHubGroup
    /** Derives from [AppearanceThemeRows]: 17 spec-backed rows + 2 residuals — the Theme & display leaf's main group. */
    val appearanceTheme = AppearanceThemeGroup
    /** Derives from [AppearanceNavigationRows]: spec-less residual rows + the spec-backed global-chrome seven — the Navigation & haptics leaf. */
    val appearanceNavigation = AppearanceNavigationGroup
    /** Derives from [AppearanceLibraryViewsRows]: the library/card browsing rows — the Library views leaf. */
    val appearanceLibraryViews = AppearanceLibraryViewsGroup
    /** Derives from [AppearanceDetailRows]: the detail behavior/display rows — the Detail screen leaf's visible group. */
    val appearanceDetail = AppearanceDetailGroup
    /** Derives from [AppearanceDetailTuningRows]: the density/limit tuning rows — the Detail leaf's advanced trailing group. */
    val appearanceDetailTuning = AppearanceDetailTuningGroup
    /** Derives from [AppearancePerformanceRows]: the Reduce Motion row rides the advanced toggle — a Theme & display leaf group. */
    val appearancePerformance = AppearancePerformanceGroup
    /** Derives from [AppearanceEyeCareRows]: both rows ride the advanced toggle — a Theme & display leaf group. */
    val appearanceEyeCare = AppearanceEyeCareGroup
    /** Derives from [AppearanceNewsletterRows]: three advanced residual rows — rendered on the HUB (the declared hub exception). */
    val appearanceNewsletter = AppearanceNewsletterGroup

    // ── PerformanceSettingsScreen (the Performance hub) ────────────────
    // The hub's five groups derive from the fused rows in
    // PerformanceSettingsRows.kt: the always-visible master level group and
    // the four expert groups (motion, images, ambient, playback buffers —
    // the last spanning the playback domain's spec declarations). The master
    // row keeps the historical `performance_mode` search id and lives HERE
    // (single group membership), not in the appearance screen's group — the
    // appearance screen keeps a quick picker + a Customize deep-link beside
    // its Reduce Motion row.

    /** Derives from [PerformanceLevelRows]: the master dial ([RowAdmission.Always] — always visible). */
    val performanceLevel = PerformanceLevelGroup
    /** Derives from [PerformanceMotionRows]: the three motion/transition overrides, advanced-gated. */
    val performanceMotion = PerformanceMotionGroup
    /** Derives from [PerformanceImageRows]: the five image rows, advanced-gated. */
    val performanceImages = PerformanceImageGroup
    /** Derives from [PerformanceAmbientRows]: the three ambient rows, advanced-gated. */
    val performanceAmbient = PerformanceAmbientGroup
    /** Derives from [PlaybackBufferRows]: the ExoPlayer buffer quartet (playback-domain specs), advanced-gated. */
    val playbackBuffers = PlaybackBufferGroup

    // ── The playback domain (ADR-0017's hub + eight leaves) ─────────────
    // The hub's entrance group, then the former `playback.player` group's
    // three leaves, then the domain groups that moved whole onto their leaf
    // screens (advanced video, engines, segments) or the SyncPlay/casting
    // pair's shared leaf.

    /** The hub screen's one group: the eight composite entrance rows. */
    val playbackHub = PlaybackHubGroup

    /** Derives from [PlaybackBasicsRows]: the mainstream playback-behavior leaf. */
    val playbackBasics = PlaybackBasicsGroup

    /** Derives from [PlaybackControlsRows]: the orientation/gesture/input leaf. */
    val playbackControls = PlaybackControlsGroup

    /** Derives from [PlaybackPlayerChromeRows]: the advanced video_* customization cluster. */
    val playbackPlayerChrome = PlaybackPlayerChromeGroup

    val playbackAdvancedVideo = PlaybackAdvancedVideoGroup

    /**
     * One screen group fed by four adjacent engine branch lists —
     * the screen renders a single "Engine Config" group whose rows depend
     * on the preferred player. The desktop-gated mpv audio-device rows
     * declare their [RowAdmission.Platform] gate on their fused rows — the
     * same declaration `playbackEngineScreenRowTotal` and the screen's
     * emission `if`s read.
     */
    val playbackEngine = PlaybackEngineGroup

    val playbackSyncPlay = PlaybackSyncPlayGroup
    val playbackCasting = PlaybackCastingGroup

    /**
     * The documented split of the Live TV declaration: the DVR rows derive
     * from the declaration list, while the `media_segment_*` ids render in
     * their own hand-built group enumerated from `MediaSegmentType.entries`
     * (the accepted exception pinned in `SettingsCatalogScreenContractTest`),
     * plus the group's skip-on-seek toggle — a hand-built row like the
     * per-type rows, so it rides the same partition. The list itself stays
     * whole — the partition happens in PlaybackSettingsRows.kt, at the group
     * derivation.
     */
    val playbackDvr = PlaybackDvrGroup
    val playbackMediaSegments = PlaybackMediaSegmentsGroup

    // ── AudioSettingsScreen ─────────────────────────────────────────────
    val audio = AudioGroup
    /** Derives from [AudioFineTuningRows]: the gain/buffer fine-tuning — the audio screen's advanced trailing group. */
    val audioFineTuning = AudioFineTuningGroup

    /**
     * The nested audio-cache group — the whole group only exists where
     * `settingsCapabilities.supportsAudioCache` holds, and every item in it
     * derives its platform tag from that same flag.
     */
    val audioCache = AudioCacheGroup

    // ── Single-group screens ────────────────────────────────────────────

    /**
     * The documented split of the language declaration: the leading language
     * trio renders in the screen's "Language" group, every remaining row in
     * its "Subtitles" group. The list itself stays whole — the partition
     * happens in LanguageSettingsRows.kt, at the group derivation (the Live
     * TV precedent); unlike the media-segment prefix split there is no id
     * shape to key on, so the split line is the leading-trio boundary, pinned
     * by `SettingsCatalogScreenContractTest`.
     */
    val languageGeneral = LanguageGeneralGroup
    val languageSubtitles = LanguageSubtitlesGroup

    /**
     * The language screen's "Track Selection" group — its own
     * declaration list so the general/subtitles split line above is
     * untouched. Every row always renders ([RowAdmission.Always]).
     */
    val languageTrackSelection = LanguageTrackSelectionGroup

    val notifications = NotificationGroup

    // ── StorageSettingsScreen ───────────────────────────────────────────
    val storageCache = StorageCacheGroup
    val storageNetwork = StorageNetworkGroup
    /** Derives from [StorageNetworkTuningRows]: the diagnostics fine-tuning — the storage screen's advanced trailing group. */
    val storageNetworkTuning = StorageNetworkTuningGroup
    val storageDownloads = StorageDownloadsGroup

    val security = SecurityGroup
    val backup = BackupGroup
    val about = AboutGroup
    val experimental = ExperimentalSettingsSearchItems.asSearchGroup("experimental")

    /**
     * Every group, in the curated flat catalog order (the concatenation
     * [SettingsSearchCatalog.items] is built from — the tie-break order the
     * search matcher relies on).
     */
    val all: List<SettingsSearchItemGroup> = listOf(
        account,
        integrations,
        activityInsights,
        systemCore,
        systemScreensaver,
        systemIdleAmbient,
        systemDiscordPresence,
        systemHooks,
        jellyplaySync,
        jellyplayFeatures,
        homeHub,
        homeTuning,
        homeDisplayMain,
        homeNextUp,
        homeLayout,
        homeCuration,
        homeCards,
        appearanceHub,
        appearanceTheme,
        appearanceNavigation,
        appearanceLibraryViews,
        appearanceDetail,
        appearanceDetailTuning,
        appearancePerformance,
        appearanceEyeCare,
        appearanceNewsletter,
        performanceLevel,
        performanceMotion,
        performanceImages,
        performanceAmbient,
        playbackBuffers,
        playbackHub,
        playbackBasics,
        playbackControls,
        playbackPlayerChrome,
        playbackAdvancedVideo,
        playbackEngine,
        playbackSyncPlay,
        playbackCasting,
        playbackDvr,
        playbackMediaSegments,
        audio,
        audioCache,
        audioFineTuning,
        languageGeneral,
        languageTrackSelection,
        languageSubtitles,
        notifications,
        storageCache,
        storageNetwork,
        storageNetworkTuning,
        storageDownloads,
        security,
        backup,
        about,
        experimental,
    )

    /** The id → group map — single-valued by the contract test's pinning. */
    val groupOfId: Map<String, SettingsSearchItemGroup> =
        all.flatMap { group -> group.itemIds.map { it to group } }.toMap()

    /** Prefix shared by every media-segment id in [LiveTvSearchItems]. */
    internal const val MEDIA_SEGMENT_ID_PREFIX = "media_segment_"

    /** Prefix shared by every screensaver (dream) id in the system declaration. */
    internal const val SCREENSAVER_ID_PREFIX = "screensaver_"

    /** Prefix shared by every idle-ambient id in the system declaration. */
    internal const val IDLE_AMBIENT_ID_PREFIX = "idle_ambient_"

    /** Prefix shared by every Discord-presence id in the system declaration. */
    internal const val DISCORD_PRESENCE_ID_PREFIX = "discord_"

    /** Prefix shared by every shell-hook id in the system declaration. */
    internal const val HOOKS_ID_PREFIX = "hooks_"
}
