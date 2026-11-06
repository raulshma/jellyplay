package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowBack
import com.composables.icons.tabler.outline.Badge4k
import com.composables.icons.tabler.outline.ColorFilter
import com.composables.icons.tabler.outline.Contrast2
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Gauge
import com.composables.icons.tabler.outline.Hourglass
import com.composables.icons.tabler.outline.History
import com.composables.icons.tabler.outline.LayersLinked
import com.composables.icons.tabler.outline.ArrowsShuffle
import com.composables.icons.tabler.outline.MoonStars
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Percentage
import com.composables.icons.tabler.outline.Photo
import com.composables.icons.tabler.outline.PhotoDown
import com.composables.icons.tabler.outline.WaveSine
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_appearance
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_playback
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_animations
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_shared_elements
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_nav_transitions
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_image_decode_cap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_image_crossfade
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_blurhash
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_quality_badges
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_image_memory_cache
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_ambient_hero_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_backdrop_blur
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_screensaver_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_exo_initial_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_exo_rebuffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_exo_target_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_exo_retain_back_buffer
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_initial_buffer_ms_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_initial_buffer_ms_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_rebuffer_ms_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_rebuffer_ms_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_retain_back_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_retain_back_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_target_buffer_mb_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_exo_target_buffer_mb_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_image_memory_cache_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_image_memory_cache_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_ambient_hero_video_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_ambient_hero_video_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_animations_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_animations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_backdrop_blur_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_backdrop_blur_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_blurhash_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_blurhash_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_image_crossfade_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_image_crossfade_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_image_decode_cap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_image_decode_cap_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_nav_transitions_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_nav_transitions_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_quality_badges_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_quality_badges_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_screensaver_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_screensaver_motion_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_shared_elements_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_perf_shared_elements_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_title

/**
 * The Performance hub's fused row declarations — the feature-side single home
 * of the master level dial and the fifteen granular rows the hub renders
 * (the eleven appearance-side performance rows
 * ([AppearancePreferenceSpecs] `perf_*` + image memory cache) and the four
 * ExoPlayer buffer rows ([PlaybackPreferenceSpecs] `exo_*`)). The two-home
 * contract holds exactly like every other converted domain: the SEMANTICS
 * (keywords, advanced flag, route kind — all
 * `ROUTE_PERFORMANCE_SETTINGS` since the hub wave) live on the datastore-side
 * [PreferenceSearchSpec] declarations, this file is the presentation tier
 * only, and the projection ([List.toSearchItems]/[List.asRowGroup]) fails
 * fast at catalog init on any row↔spec drift.
 *
 * The master level row keeps the historical `performance_mode` search id (the
 * search index and persisted deep links stay valid across the boolean→level
 * migration); its screen face is the hub's level picker. It declares
 * [RowAdmission.Always] explicitly — the spec's advanced tag is the row's
 * legacy (the old appearance-side group hid behind the advanced toggle), but
 * a dedicated hub whose whole first group collapses behind the toggle would
 * render empty at default settings; the HapticsEnabled quirk precedent
 * applies (an always-on row despite an advanced tag). The override rows keep
 * their spec advanced base: the four non-level hub groups only compose
 * behind the advanced toggle.
 */
internal object PerformanceRows {

    // ── Level group ─────────────────────────────────────────────────────

    /**
     * The master performance level ([AppearancePreferenceSpecs.PERFORMANCE_LEVEL]
     * — wire key `performance_level`, search id `performance_mode`). The
     * screen subtitle is DYNAMIC (the current level's label), so
     * [SettingsRow.subtitleRes] stays null; the static search face below is
     * the row's only subtitle resource.
     */
    val PerformanceLevel = SettingsRow(
        id = "performance_mode",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_performance_mode,
        searchTitleRes = Res.string.ss_performance_mode_title,
        searchSubtitleRes = Res.string.ss_performance_mode_subtitle,
        gate = RowAdmission.Always,
    )

    // ── Motion & transitions group ──────────────────────────────────────

    val PerfAnimations = SettingsRow(
        id = "perf_animations",
        icon = Tabler.Outline.WaveSine,
        titleRes = Res.string.settings_perf_animations,
        searchTitleRes = Res.string.ss_perf_animations_title,
        searchSubtitleRes = Res.string.ss_perf_animations_subtitle,
    )

    val PerfSharedElements = SettingsRow(
        id = "perf_shared_elements",
        icon = Tabler.Outline.LayersLinked,
        titleRes = Res.string.settings_perf_shared_elements,
        searchTitleRes = Res.string.ss_perf_shared_elements_title,
        searchSubtitleRes = Res.string.ss_perf_shared_elements_subtitle,
    )

    val PerfNavTransitions = SettingsRow(
        id = "perf_nav_transitions",
        icon = Tabler.Outline.ArrowsShuffle,
        titleRes = Res.string.settings_perf_nav_transitions,
        searchTitleRes = Res.string.ss_perf_nav_transitions_title,
        searchSubtitleRes = Res.string.ss_perf_nav_transitions_subtitle,
    )

    // ── Images group ────────────────────────────────────────────────────

    val PerfImageDecodeCap = SettingsRow(
        id = "perf_image_decode_cap",
        icon = Tabler.Outline.PhotoDown,
        titleRes = Res.string.settings_perf_image_decode_cap,
        searchTitleRes = Res.string.ss_perf_image_decode_cap_title,
        searchSubtitleRes = Res.string.ss_perf_image_decode_cap_subtitle,
    )

    val PerfImageCrossfade = SettingsRow(
        id = "perf_image_crossfade",
        icon = Tabler.Outline.ColorFilter,
        titleRes = Res.string.settings_perf_image_crossfade,
        searchTitleRes = Res.string.ss_perf_image_crossfade_title,
        searchSubtitleRes = Res.string.ss_perf_image_crossfade_subtitle,
    )

    val PerfBlurhash = SettingsRow(
        id = "perf_blurhash",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_perf_blurhash,
        searchTitleRes = Res.string.ss_perf_blurhash_title,
        searchSubtitleRes = Res.string.ss_perf_blurhash_subtitle,
    )

    val PerfQualityBadges = SettingsRow(
        id = "perf_quality_badges",
        icon = Tabler.Outline.Badge4k,
        titleRes = Res.string.settings_perf_quality_badges,
        searchTitleRes = Res.string.ss_perf_quality_badges_title,
        searchSubtitleRes = Res.string.ss_perf_quality_badges_subtitle,
    )

    val ImageMemoryCache = SettingsRow(
        id = "image_memory_cache_percent",
        icon = Tabler.Outline.Percentage,
        titleRes = Res.string.settings_perf_image_memory_cache,
        searchTitleRes = Res.string.ss_image_memory_cache_title,
        searchSubtitleRes = Res.string.ss_image_memory_cache_subtitle,
    )

    // ── Ambient effects group ───────────────────────────────────────────

    val PerfAmbientHeroVideo = SettingsRow(
        id = "perf_ambient_hero_video",
        icon = Tabler.Outline.Movie,
        titleRes = Res.string.settings_perf_ambient_hero_video,
        searchTitleRes = Res.string.ss_perf_ambient_hero_video_title,
        searchSubtitleRes = Res.string.ss_perf_ambient_hero_video_subtitle,
    )

    val PerfBackdropBlur = SettingsRow(
        id = "perf_backdrop_blur",
        icon = Tabler.Outline.Contrast2,
        titleRes = Res.string.settings_perf_backdrop_blur,
        searchTitleRes = Res.string.ss_perf_backdrop_blur_title,
        searchSubtitleRes = Res.string.ss_perf_backdrop_blur_subtitle,
    )

    val PerfScreensaverMotion = SettingsRow(
        id = "perf_screensaver_motion",
        icon = Tabler.Outline.MoonStars,
        titleRes = Res.string.settings_perf_screensaver_motion,
        searchTitleRes = Res.string.ss_perf_screensaver_motion_title,
        searchSubtitleRes = Res.string.ss_perf_screensaver_motion_subtitle,
    )

    // ── Playback buffers group ──────────────────────────────────────────

    val ExoInitialBuffer = SettingsRow(
        id = "exo_initial_buffer_ms",
        icon = Tabler.Outline.Hourglass,
        titleRes = Res.string.settings_perf_exo_initial_buffer,
        searchTitleRes = Res.string.ss_exo_initial_buffer_ms_title,
        searchSubtitleRes = Res.string.ss_exo_initial_buffer_ms_subtitle,
    )

    val ExoRebuffer = SettingsRow(
        id = "exo_rebuffer_ms",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_perf_exo_rebuffer,
        searchTitleRes = Res.string.ss_exo_rebuffer_ms_title,
        searchSubtitleRes = Res.string.ss_exo_rebuffer_ms_subtitle,
    )

    val ExoTargetBuffer = SettingsRow(
        id = "exo_target_buffer_mb",
        icon = Tabler.Outline.Database,
        titleRes = Res.string.settings_perf_exo_target_buffer,
        searchTitleRes = Res.string.ss_exo_target_buffer_mb_title,
        searchSubtitleRes = Res.string.ss_exo_target_buffer_mb_subtitle,
    )

    val ExoRetainBackBuffer = SettingsRow(
        id = "exo_retain_back_buffer",
        icon = Tabler.Outline.ArrowBack,
        titleRes = Res.string.settings_perf_exo_retain_back_buffer,
        searchTitleRes = Res.string.ss_exo_retain_back_buffer_title,
        searchSubtitleRes = Res.string.ss_exo_retain_back_buffer_subtitle,
    )

    /**
     * Every fused performance-hub row. A computed accessor (not an
     * initializer): the group row lists are top-level vals declared later in
     * this file, and an eager field would turn the object↔file-facade
     * initialization order into a cycle (the AppearanceRows.all precedent).
     */
    val all: List<SettingsRow>
        get() = PerformanceLevelRows + PerformanceMotionRows + PerformanceImageRows +
            PerformanceAmbientRows + PlaybackBufferRows
}

// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs: the hub spans two domains' spec
// declarations — the appearance-side performance rows ride
// [AppearancePreferenceSpecs.searchEntries], the buffer quartet rides
// [PlaybackPreferenceSpecs.searchEntries]. Both domains' route kinds resolve
// through ONE map: every hub row's spec now carries
// [AppearancePreferenceSpecs.ROUTE_PERFORMANCE_SETTINGS].
// ═══════════════════════════════════════════════════════════════════════

private val performanceSearchRoutes: Map<String, Route> = mapOf(
    AppearancePreferenceSpecs.ROUTE_PERFORMANCE_SETTINGS to Route.PerformanceSettings(),
)

private val performanceAppearanceSpecEntries: List<PreferenceSearchSpec> =
    AppearancePreferenceSpecs.searchEntries

private val performancePlaybackSpecEntries: List<PreferenceSearchSpec> =
    PlaybackPreferenceSpecs.searchEntries

private val performanceAppearanceCategory = CoreUiRes.string.ss_cat_appearance
private val performancePlaybackCategory = CoreUiRes.string.ss_cat_playback

/** The master level dial — the hub's always-visible first group (one row). */
internal val PerformanceLevelRows: List<SettingsRow> = listOf(
    PerformanceRows.PerformanceLevel,
)

/** The tri-state motion/transition overrides, in catalog order (= spec order). */
internal val PerformanceMotionRows: List<SettingsRow> = listOf(
    PerformanceRows.PerfAnimations,
    PerformanceRows.PerfSharedElements,
    PerformanceRows.PerfNavTransitions,
)

/** The image pipeline rows: decode cap, crossfade, BlurHash, badges, memory cache. */
internal val PerformanceImageRows: List<SettingsRow> = listOf(
    PerformanceRows.PerfImageDecodeCap,
    PerformanceRows.PerfImageCrossfade,
    PerformanceRows.PerfBlurhash,
    PerformanceRows.PerfQualityBadges,
    PerformanceRows.ImageMemoryCache,
)

/** The ambient-effect rows: hero video, backdrop blur, screensaver motion. */
internal val PerformanceAmbientRows: List<SettingsRow> = listOf(
    PerformanceRows.PerfAmbientHeroVideo,
    PerformanceRows.PerfBackdropBlur,
    PerformanceRows.PerfScreensaverMotion,
)

/** The ExoPlayer `DefaultLoadControl` buffer quartet (the playback-domain rows). */
internal val PlaybackBufferRows: List<SettingsRow> = listOf(
    PerformanceRows.ExoInitialBuffer,
    PerformanceRows.ExoRebuffer,
    PerformanceRows.ExoTargetBuffer,
    PerformanceRows.ExoRetainBackBuffer,
)

/**
 * The hub's five screen groups — items AND per-row admissions derive from the
 * row lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates. The level group carries
 * the hub's one always-visible row; the four expert groups only compose
 * behind the advanced toggle (their slots declare `advancedOnly` on the
 * screen). Aggregated in [SettingsSearchCatalog] (via [SettingsScreenGroups]);
 * the projection is fail-fast at init on any row↔spec drift.
 */
internal val PerformanceLevelGroup =
    PerformanceLevelRows.asRowGroup(
        "performance.level",
        specEntriesFor(PerformanceLevelRows, performanceAppearanceSpecEntries),
        performanceSearchRoutes,
        performanceAppearanceCategory,
    )

internal val PerformanceMotionGroup =
    PerformanceMotionRows.asRowGroup(
        "performance.motion",
        specEntriesFor(PerformanceMotionRows, performanceAppearanceSpecEntries),
        performanceSearchRoutes,
        performanceAppearanceCategory,
    )

internal val PerformanceImageGroup =
    PerformanceImageRows.asRowGroup(
        "performance.images",
        specEntriesFor(PerformanceImageRows, performanceAppearanceSpecEntries),
        performanceSearchRoutes,
        performanceAppearanceCategory,
    )

internal val PerformanceAmbientGroup =
    PerformanceAmbientRows.asRowGroup(
        "performance.ambient",
        specEntriesFor(PerformanceAmbientRows, performanceAppearanceSpecEntries),
        performanceSearchRoutes,
        performanceAppearanceCategory,
    )

internal val PlaybackBufferGroup =
    PlaybackBufferRows.asRowGroup(
        "performance.buffers",
        specEntriesFor(PlaybackBufferRows, performancePlaybackSpecEntries),
        performanceSearchRoutes,
        performancePlaybackCategory,
    )

// The catalog projections, kept as named vals — the search/catalog-order pins
// (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these lists.

/** The catalog projection of the level group (the master dial). */
internal val PerformanceLevelSearchItems: List<SettingsSearchItem> = PerformanceLevelGroup.items

/** The catalog projection of the motion group. */
internal val PerformanceMotionSearchItems: List<SettingsSearchItem> = PerformanceMotionGroup.items

/** The catalog projection of the images group. */
internal val PerformanceImageSearchItems: List<SettingsSearchItem> = PerformanceImageGroup.items

/** The catalog projection of the ambient group. */
internal val PerformanceAmbientSearchItems: List<SettingsSearchItem> = PerformanceAmbientGroup.items

/** The catalog projection of the buffers group. */
internal val PlaybackBufferSearchItems: List<SettingsSearchItem> = PlaybackBufferGroup.items

// ── The domain's root-screen entrance declaration ────────────────────────

/**
 * The performance hub's root-screen entrance — the ONE ordered declaration
 * that drives BOTH the settings root's `item_performance` section emission
 * (icon/title/route id) and its entrance-step index (the [section] entry
 * spliced into [SETTINGS_ENTRANCE_SECTIONS] at this domain's render position,
 * between appearance and playback). Same shape as [AppearanceEntrance] /
 * [PlaybackEntrance]; the root screen's call site reads these faces.
 */
internal val PerformanceEntrance = SettingsEntranceSectionRow(
    key = "item_performance",
    rowId = "performance",
    icon = Tabler.Outline.Gauge,
    titleRes = Res.string.settings_performance,
)
