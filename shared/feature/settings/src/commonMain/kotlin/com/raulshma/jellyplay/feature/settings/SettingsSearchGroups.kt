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
 */
internal class SettingsSearchItemGroup(
    /** Stable group name, e.g. `"playback.engine"`. Unique across [SettingsScreenGroups.all]. */
    val id: String,
    /** The declared items, in catalog order. */
    val items: List<SettingsSearchItem>,
) {
    /** The declared ids, in catalog order — the derivation source for scroll lists. */
    val itemIds: List<String> = items.map { it.id }

    /** [itemIds] as a set — the derivation source for expand checks. */
    val itemIdSet: Set<String> = itemIds.toSet()
}

/** Decorates a declaration list as the named screen group its screen renders. */
internal fun List<SettingsSearchItem>.asSearchGroup(id: String): SettingsSearchItemGroup =
    SettingsSearchItemGroup(id, this)

/**
 * The aggregation decoration: every per-screen `*SearchItems` declaration
 * list becomes the named group of the screen region that renders its rows.
 * [SettingsSearchCatalog.items] is the flat concatenation of [all] — the
 * curated flat order the search matcher tie-breaks on — so decorating and
 * aggregating are the same act, and a declaration that lands in no group
 * simply cannot reach the catalog.
 */
internal object SettingsScreenGroups {

    // ── Main settings screen (navigation-type ids; their rows are the
    //    deep-link targets on other screens, so they have none here) ────
    val account = AccountSearchItems.asSearchGroup("account")
    val integrations = IntegrationsSearchItems.asSearchGroup("integrations")
    val activityInsights = ActivityInsightsSearchItems.asSearchGroup("activityInsights")

    /**
     * The documented split of [SystemSearchItems]: the leading navigation pair
     * (admin dashboard, setup wizard) renders as the main screen's System
     * group, while the `screensaver_*` rows render as that screen's on-screen
     * screensaver group. The list itself stays whole — the partition happens
     * here, at the aggregation decoration (the Live TV media-segment
     * precedent: a prefix split, no second declaration list).
     */
    val systemCore = SystemSearchItems.filter { !it.id.startsWith(SCREENSAVER_ID_PREFIX) }
        .asSearchGroup("system.core")
    val systemScreensaver = SystemSearchItems.filter { it.id.startsWith(SCREENSAVER_ID_PREFIX) }
        .asSearchGroup("system.screensaver")

    // ── AppearanceSettingsScreen ────────────────────────────────────────
    val appearanceTheme = AppearanceThemeSearchItems.asSearchGroup("appearance.theme")
    val appearanceNavigation = AppearanceNavigationSearchItems.asSearchGroup("appearance.navigation")
    val appearanceLibrary = AppearanceLibrarySearchItems.asSearchGroup("appearance.library")
    val appearanceHomeLayout = AppearanceHomeLayoutSearchItems.asSearchGroup("appearance.homeLayout")
    val appearancePerformance = AppearancePerformanceSearchItems.asSearchGroup("appearance.performance")
    val appearanceEyeCare = AppearanceEyeCareSearchItems.asSearchGroup("appearance.eyeCare")
    val appearanceNewsletter = AppearanceNewsletterSearchItems.asSearchGroup("appearance.newsletter")

    // ── PlaybackSettingsScreen ──────────────────────────────────────────
    val playbackPlayer = PlaybackSettingsSearchItems.asSearchGroup("playback.player")
    val playbackAdvancedVideo = PlaybackAdvancedVideoSearchItems.asSearchGroup("playback.advancedVideo")

    /**
     * One screen group fed by three adjacent engine declaration lists —
     * the screen renders a single "Engine Config" group whose rows depend
     * on the preferred player.
     */
    val playbackEngine = (MpvEngineSearchItems + VlcEngineSearchItems + ExoPlayerEngineSearchItems)
        .asSearchGroup("playback.engine")

    val playbackSyncPlay = SyncPlaySearchItems.asSearchGroup("playback.syncPlay")
    val playbackCasting = CastingSearchItems.asSearchGroup("playback.casting")

    /**
     * The documented split of [LiveTvSearchItems]: the DVR rows derive from
     * the declaration list, while the `media_segment_*` ids render in their
     * own hand-built group enumerated from `MediaSegmentType.entries` (the
     * accepted exception pinned in `SettingsCatalogScreenContractTest`).
     * The list itself stays whole — the partition happens here, at the
     * aggregation decoration.
     */
    val playbackDvr = LiveTvSearchItems.filter { !it.id.startsWith(MEDIA_SEGMENT_ID_PREFIX) }
        .asSearchGroup("playback.dvr")
    val playbackMediaSegments = LiveTvSearchItems.filter { it.id.startsWith(MEDIA_SEGMENT_ID_PREFIX) }
        .asSearchGroup("playback.mediaSegments")

    // ── AudioSettingsScreen ─────────────────────────────────────────────
    val audio = AudioSettingsSearchItems.asSearchGroup("audio")

    /**
     * The nested audio-cache group — the whole group only exists where
     * `settingsCapabilities.supportsAudioCache` holds, and every item in it
     * derives its platform tag from that same flag.
     */
    val audioCache = AudioCacheSearchItems.asSearchGroup("audio.cache")

    // ── Single-group screens ────────────────────────────────────────────

    /**
     * The documented split of [LanguageSettingsSearchItems]: the leading
     * language trio renders in the screen's "Language" group, every remaining
     * row in its "Subtitles" group. The list itself stays whole — the
     * partition happens here, at the aggregation decoration (the Live TV
     * precedent); unlike the media-segment prefix split there is no id shape
     * to key on, so the split line is the leading-trio size, pinned by
     * `SettingsCatalogScreenContractTest`.
     */
    val languageGeneral = LanguageSettingsSearchItems.take(LANGUAGE_GENERAL_GROUP_SIZE)
        .asSearchGroup("language.general")
    val languageSubtitles = LanguageSettingsSearchItems.drop(LANGUAGE_GENERAL_GROUP_SIZE)
        .asSearchGroup("language.subtitles")

    val notifications = NotificationSettingsSearchItems.asSearchGroup("notifications")

    // ── StorageSettingsScreen ───────────────────────────────────────────
    val storageCache = StorageCacheSearchItems.asSearchGroup("storage.cache")
    val storageNetwork = StorageNetworkSearchItems.asSearchGroup("storage.network")
    val storageDownloads = StorageDownloadsSearchItems.asSearchGroup("storage.downloads")

    val security = SecuritySettingsSearchItems.asSearchGroup("security")
    val backup = BackupSettingsSearchItems.asSearchGroup("backup")
    val about = AboutSearchItems.asSearchGroup("about")
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
        appearanceTheme,
        appearanceNavigation,
        appearanceLibrary,
        appearanceHomeLayout,
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

    /** Prefix shared by every screensaver (dream) id in [SystemSearchItems]. */
    internal const val SCREENSAVER_ID_PREFIX = "screensaver_"

    /**
     * The leading trio of [LanguageSettingsSearchItems] (app / audio /
     * subtitle language) that renders in the screen's "Language" group — the
     * split line of the [languageGeneral]/[languageSubtitles] decoration.
     */
    internal const val LANGUAGE_GENERAL_GROUP_SIZE = 3
}
