package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * One screen group of settings rows: the declaration-list identity a
 * per-screen `*SearchItems` list is decorated with at the aggregation site
 * (decision Q11a — no field is added to `SettingsSearchItem`, which lives in
 * core/ui and must not widen).
 *
 * A group is the single declaration of that screen region's facts. The
 * owning screen derives everything that used to be hand-typed parallel id
 * lists from it — the deep-link scroll group list, the `initiallyExpanded`
 * expand sets, and the `SettingsItemList(total = …)` row totals — so the
 * catalog and the UI can no longer drift apart (the `vlc_video_output`
 * class of bug: declared, but in no screen group, so a deep-link neither
 * scrolled nor expanded).
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
) {
    /** The declared ids, in catalog order — the derivation source for scroll lists. */
    val itemIds: List<String> = items.map { it.id }

    /** [itemIds] as a set — the derivation source for expand checks. */
    val itemIdSet: Set<String> = itemIds.toSet()

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
     * screen group (rendered by a capability-gated block beside the TV dream
     * group, so neither group's row totals entangle).
     */
    val systemIdleAmbient = SystemIdleAmbientGroup

    /**
     * the desktop Discord Rich Presence toggle (feature 4.2) — its own
     * capability-gated group beside the idle-ambient one, same shape.
     */
    val systemDiscordPresence = SystemDiscordPresenceGroup

    /**
     * the desktop playback-event shell-hook rows (feature 4.3) — the master
     * toggle plus the five mpv-shim-named commands, one group.
     */
    val systemHooks = SystemHooksGroup

    /**
     * the JellyPlay companion-plugin settings-sync rows (ADR 0010) — the
     * opt-in toggle + sync-now action, one capability-gated on-screen group
     * (emitted only where the plugin's capabilities probe reports AVAILABLE).
     */
    val jellyplaySync = JellyPlaySyncGroup

    /**
     * the JellyPlay companion-plugin per-feature switches ("Server plugin",
     * ADR 0010) — one toggle row per user-facing capability, emitted only
     * where the probe exposes it (the whole group hides when the plugin is
     * UNAVAILABLE/UNKNOWN; `transcodes` additionally only for admins).
     */
    val jellyplayFeatures = JellyPlayFeaturesGroup

    // ── HomeSettingsScreen ─────────────────────────────────────────────
    /**
     * The home config hub's four screen groups — the rows moved off
     * Appearance (the former advanced-gated home display rows, the Next Up
     * behavior rows, the former `appearance.homeLayout` drill-in group, and
     * the "Library & Cards" card-display quartet, PS-4). Ids carried over
     * verbatim, so persisted deep-links/recents keep resolving — only the
     * owning screen changed.
     */
    val homeDisplay = HomeDisplayGroup
    val homeNextUp = HomeNextUpGroup
    val homeLayout = HomeLayoutGroup
    val homeCards = HomeCardsGroup

    // ── AppearanceSettingsScreen ────────────────────────────────────────
    // The appearance domain converted to the fused rows
    // (AppearanceSettingsRows.kt): the six groups derive items AND
    // admissions from the row lists in one act ([List.asRowGroup]) — the
    // per-domain admissions maps this shape replaced delete as each domain
    // converts (the appearance trio — record + binding + admissions map — is
    // retired; see [AppearanceThemeGroup]).

    /** Derives from [AppearanceThemeRows]: 17 spec-backed rows + 2 residuals. */
    val appearanceTheme = AppearanceThemeGroup
    /** Derives from [AppearanceNavigationRows]: spec-less residual rows. */
    val appearanceNavigation = AppearanceNavigationGroup
    /** Derives from [AppearanceLibraryRows]: 3 spec-backed rows + 5 residuals, every row [RowAdmission.Always]. */
    val appearanceLibrary = AppearanceLibraryGroup
    /** Derives from [AppearancePerformanceRows]: both rows ride the advanced toggle. */
    val appearancePerformance = AppearancePerformanceGroup
    /** Derives from [AppearanceEyeCareRows]: both rows ride the advanced toggle. */
    val appearanceEyeCare = AppearanceEyeCareGroup
    /** Derives from [AppearanceNewsletterRows]: three advanced residual rows. */
    val appearanceNewsletter = AppearanceNewsletterGroup

    // ── PlaybackSettingsScreen ──────────────────────────────────────────
    val playbackPlayer = PlaybackPlayerGroup
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
        homeDisplay,
        homeNextUp,
        homeLayout,
        homeCards,
        appearanceTheme,
        appearanceNavigation,
        appearanceLibrary,
        appearancePerformance,
        appearanceEyeCare,
        appearanceNewsletter,
        playbackPlayer,
        playbackAdvancedVideo,
        playbackEngine,
        playbackSyncPlay,
        playbackCasting,
        playbackDvr,
        playbackMediaSegments,
        audio,
        audioCache,
        languageGeneral,
        languageTrackSelection,
        languageSubtitles,
        notifications,
        storageCache,
        storageNetwork,
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
