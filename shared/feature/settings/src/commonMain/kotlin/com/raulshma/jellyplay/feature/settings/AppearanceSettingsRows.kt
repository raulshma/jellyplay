package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_accent_color_title
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_subtitle
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_style_title
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_appearance
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_color_blind_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_compact_episode_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_haptic_feedback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_episode_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_search_history
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_layout_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_bar_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_hide_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_delivery_day
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_prefer_logos
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_missing_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_filter_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_blue_light_strength_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_color_blind_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_compact_episode_list_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_contrast_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_date_format_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_date_format_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dynamic_theming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_dynamic_theming_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_font_scale_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_font_scale_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hand_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hand_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_haptics_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_haptics_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_episode_thumbnails_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_search_history_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_layout_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_layout_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_library_view_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_bar_customization_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_hide_on_scroll_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_nav_labels_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_delivery_day_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_enabled_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_newsletter_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_oled_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_performance_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_prefer_logos_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_prefer_logos_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_reduce_motion_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_end_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_scheduled_start_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_missing_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_missing_episodes_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_share_media_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_specials_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_skip_specials_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_style_accent_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_style_accent_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_music_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_music_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_scheduler_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_scheduler_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_theme_style_subtitle

/**
 * The appearance domain's fused row declarations — the feature-side single
 * home of every appearance row's presentation, ordering, and capability (the
 * converted template of the fused-catalog wave). Each [SettingsRow] replaces
 * the trio the domain used to declare per row: the `SettingsSearchBinding`
 * entry, the `SettingsRowRecord` entry, and the `AppearanceSettingsIds`
 * holder constant (all deleted). One declaration per row: id, title faces,
 * icon, route kind, capability gate ([SettingsRow.gate] — the admission the
 * group totals AND the screen emissions read), and the admission exceptions
 * ([RowAdmission.ContentGated] — the theme-state-gated rows).
 *
 * The SEMANTICS stay two-homed by design: each spec-backed row's keywords,
 * advanced flag, platform rule and route kind live on the datastore-side
 * [PreferenceSearchSpec] ([AppearancePreferenceSpecs.searchEntries], plus the
 * cross-domain experimental toggles the library group renders) — this file is
 * the presentation tier only, and the projection
 * ([List.toSearchItems]/[List.asRowGroup]) fails fast at catalog init on any
 * row↔spec drift. The residual rows (Library/Navigation/Newsletter-store
 * knobs — stores the spec tier does not cover yet) carry their full hand
 * search faces here until their stores migrate.
 *
 * Search results, catalog order, group membership, and per-gate visibility
 * are byte-identical to the retired record+binding declarations
 * (`SpecDerivedSearchItemsTest`, `SettingsSearchCatalogTest` keep pinning
 * them); `FusedRowsRatchetTest` ratchets the conversion module-wide
 * (zero records/bindings/ids-holder entries in production source, every
 * group derived).
 */
internal object AppearanceRows {

    // ── Theme group (the screen's first group; catalog order = spec order) ──

    val DateFormat = SettingsRow(
        id = "date_format",
        icon = Tabler.Outline.Calendar,
        titleRes = Res.string.settings_date_format,
        searchTitleRes = Res.string.ss_date_format_title,
        searchSubtitleRes = Res.string.ss_date_format_subtitle,
    )

    val FontScale = SettingsRow(
        id = "font_scale",
        icon = Tabler.Outline.TextSize,
        titleRes = Res.string.settings_font_size_app,
        searchTitleRes = Res.string.ss_font_scale_title,
        searchSubtitleRes = Res.string.ss_font_scale_subtitle,
    )

    val ColorBlindMode = SettingsRow(
        id = "color_blind_mode",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_color_blind_mode,
        searchTitleRes = Res.string.ss_color_blind_mode_title,
        searchSubtitleRes = Res.string.ss_color_blind_mode_subtitle,
    )

    val HandMode = SettingsRow(
        id = "hand_mode",
        icon = Tabler.Outline.HandClick,
        titleRes = Res.string.settings_handedness,
        searchTitleRes = Res.string.ss_hand_mode_title,
        searchSubtitleRes = Res.string.ss_hand_mode_subtitle,
    )

    /**
     * The theme-scheduler highlight-alias row: its screen face IS the
     * theme_mode row (a search deep-link to it highlights through
     * [THEME_HIGHLIGHT_IDS]), so it declares
     * [RowAdmission.ContentGated] — counted by no total (the shipped
     * count never contained it; the security group's `pin_for_player_lock`
     * quirk shape) and gated by no flags vocabulary.
     */
    val ThemeScheduler = SettingsRow(
        id = "theme_scheduler",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_theme_mode,
        searchTitleRes = Res.string.ss_theme_scheduler_title,
        searchSubtitleRes = Res.string.ss_theme_scheduler_subtitle,
        gate = RowAdmission.ContentGated,
    )

    val ThemeMode = SettingsRow(
        id = "theme_mode",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_theme_mode,
        searchTitleRes = Res.string.ss_theme_mode_title,
        searchSubtitleRes = Res.string.ss_theme_mode_subtitle,
    )

    /** The search hit restates the row's screen title (the fold: no search title). */
    val ThemeStyle = SettingsRow(
        id = "theme_style",
        icon = Tabler.Outline.Palette,
        titleRes = Res.string.settings_theme_style,
        searchSubtitleRes = Res.string.ss_theme_style_subtitle,
    )

    /**
     * Content-gated: the hand-built VariantAccentPicker (titled per-variant by
     * `core_ui_variant_accent_title` — no single screen title resource) whose
     * emission rides the active variant's accent support.
     */
    val StyleAccent = SettingsRow(
        id = "style_accent",
        icon = Tabler.Outline.Palette,
        searchTitleRes = Res.string.ss_style_accent_title,
        searchSubtitleRes = Res.string.ss_style_accent_subtitle,
        gate = RowAdmission.ContentGated,
    )

    /**
     * Content-gated with a capability leg: the emission rides the standard
     * variant branch AND the `supportsDynamicColor` capability (the screen's
     * `if` reads the same [settingsCapabilities] fact this row's platform tag
     * derives from — one capability fact, two faces).
     */
    val DynamicTheming = SettingsRow(
        id = "dynamic_theming",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_dynamic_theming,
        searchTitleRes = Res.string.ss_dynamic_theming_title,
        searchSubtitleRes = Res.string.ss_dynamic_theming_subtitle,
        platforms = platformsForCapability(settingsCapabilities.supportsDynamicColor),
        gate = RowAdmission.ContentGated,
    )

    /** Content-gated: active dark + the variant's OLED allowance. */
    val OledMode = SettingsRow(
        id = "oled_mode",
        icon = Tabler.Outline.BrightnessHalf,
        titleRes = Res.string.settings_oled_mode,
        searchTitleRes = Res.string.ss_oled_mode_title,
        searchSubtitleRes = Res.string.ss_oled_mode_subtitle,
        gate = RowAdmission.ContentGated,
    )

    val Contrast = SettingsRow(
        id = "contrast",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_contrast,
        searchTitleRes = Res.string.ss_contrast_title,
        searchSubtitleRes = Res.string.ss_contrast_subtitle,
    )

    // ── RESIDUAL row (LibraryStore — no spec machinery). ────────────────

    val LibraryViewMode = SettingsRow(
        id = "library_view_mode",
        icon = Tabler.Outline.LayoutGrid,
        titleRes = Res.string.settings_library_view_mode,
        searchTitleRes = Res.string.ss_library_view_mode_title,
        searchSubtitleRes = Res.string.ss_library_view_mode_subtitle,
        keywords = listOf("library", "view", "grid", "list", "layout"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
    )

    /**
     * The layout override: advanced AND touch/desktop-only — the one
     * flags-expressible form-factor fork in the group, declared as
     * [RowAdmission.All]([RowAdmission.Advanced], [RowAdmission.NotTv]) so
     * the derived total carries the fork the screen's `if (!isTv)` reads.
     */
    val LayoutMode = SettingsRow(
        id = "layout_mode",
        icon = Tabler.Outline.Devices,
        titleRes = Res.string.settings_layout_mode,
        searchTitleRes = Res.string.ss_layout_mode_title,
        searchSubtitleRes = Res.string.ss_layout_mode_subtitle,
        gate = RowAdmission.All(RowAdmission.Advanced, RowAdmission.NotTv),
    )

    val ThemeMusic = SettingsRow(
        id = "theme_music",
        icon = Tabler.Outline.Music,
        titleRes = Res.string.settings_backdrop_theme_music,
        searchTitleRes = Res.string.ss_theme_music_title,
        searchSubtitleRes = Res.string.ss_theme_music_subtitle,
    )

    // ── RESIDUAL row (NavigationStore — no spec machinery). ─────────────

    val NavLabels = SettingsRow(
        id = "nav_labels",
        icon = Tabler.Outline.TextSize,
        titleRes = Res.string.settings_show_nav_labels,
        searchTitleRes = Res.string.ss_nav_labels_title,
        searchSubtitleRes = Res.string.ss_nav_labels_subtitle,
        keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
    )

    /** The enum's core_ui_* faces (the fold: search title = screen title). Content-gated: standard-branch row. */
    val AccentColor = SettingsRow(
        id = "accent_color",
        icon = Tabler.Outline.Palette,
        titleRes = CoreUiRes.string.core_ui_accent_color_title,
        searchSubtitleRes = CoreUiRes.string.core_ui_accent_color_subtitle,
        gate = RowAdmission.ContentGated,
    )

    /** The enum's core_ui_* faces (the fold). Content-gated: standard-branch row. */
    val ColorStyle = SettingsRow(
        id = "color_style",
        icon = Tabler.Outline.Palette,
        titleRes = CoreUiRes.string.core_ui_color_style_title,
        searchSubtitleRes = CoreUiRes.string.core_ui_color_style_subtitle,
        gate = RowAdmission.ContentGated,
    )

    /** Content-gated: the SCHEDULED theme-mode pair rides the mode + advanced. */
    val ScheduledStart = SettingsRow(
        id = "scheduled_start",
        icon = Tabler.Outline.Sunrise,
        titleRes = Res.string.settings_night_starts_at,
        searchTitleRes = Res.string.ss_scheduled_start_title,
        searchSubtitleRes = Res.string.ss_scheduled_start_subtitle,
        gate = RowAdmission.ContentGated,
    )

    /** Content-gated: the SCHEDULED theme-mode pair rides the mode + advanced. */
    val ScheduledEnd = SettingsRow(
        id = "scheduled_end",
        icon = Tabler.Outline.Sunset,
        titleRes = Res.string.settings_morning_starts_at,
        searchTitleRes = Res.string.ss_scheduled_end_title,
        searchSubtitleRes = Res.string.ss_scheduled_end_subtitle,
        gate = RowAdmission.ContentGated,
    )

    // ── Navigation-customization group (spec-less residual rows) ────────

    val NavBarCustomization = SettingsRow(
        id = "nav_bar_customization",
        icon = Tabler.Outline.LayoutGrid,
        titleRes = Res.string.settings_nav_bar_title,
        searchTitleRes = Res.string.ss_nav_bar_customization_title,
        searchSubtitleRes = Res.string.ss_nav_bar_customization_subtitle,
        keywords = listOf(
            "navigation", "bar", "items", "bottom", "reorder", "hide", "show", "tabs", "home",
            "library", "search", "live tv", "browse", "shortcuts", "customize",
        ),
        route = Route.AppearanceSettings(),
    )

    /** The search hit restates the row's screen title (the fold: no search title). */
    val NavHideOnScroll = SettingsRow(
        id = "nav_hide_on_scroll",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_nav_hide_on_scroll,
        searchSubtitleRes = Res.string.ss_nav_hide_on_scroll_subtitle,
        keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
        route = Route.AppearanceSettings(),
    )

    // ── Library & Cards group ───────────────────────────────────────────

    // ── RESIDUAL row (LibraryStore — no spec machinery). ────────────────

    val HideEpisodeThumbnails = SettingsRow(
        id = "hide_episode_thumbnails",
        icon = Tabler.Outline.PhotoOff,
        titleRes = Res.string.settings_hide_episode_thumbnails,
        searchTitleRes = Res.string.ss_hide_episode_thumbnails_title,
        searchSubtitleRes = Res.string.ss_hide_episode_thumbnails_subtitle,
        keywords = listOf("hide", "episode", "thumbnail", "spoiler", "preview"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    // ── RESIDUAL row (LibraryStore — no spec machinery); the search hit restates the screen title (the fold). ──

    val CompactEpisodeList = SettingsRow(
        id = "compact_episode_list",
        icon = Tabler.Outline.List,
        titleRes = Res.string.settings_compact_episode_list,
        searchSubtitleRes = Res.string.ss_compact_episode_list_subtitle,
        keywords = listOf("episode", "list", "compact", "vertical", "layout", "rows", "dense"),
        route = Route.AppearanceSettings(),
        gate = RowAdmission.Always,
    )

    // ── RESIDUAL row (LibraryStore — no spec machinery). ────────────────

    val SkipSpecials = SettingsRow(
        id = "skip_specials",
        icon = Tabler.Outline.PlayerSkipForward,
        titleRes = Res.string.settings_skip_special_episodes,
        searchTitleRes = Res.string.ss_skip_specials_title,
        searchSubtitleRes = Res.string.ss_skip_specials_subtitle,
        keywords = listOf("skip", "special", "episode", "bonus", "exclude"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    // ── RESIDUAL row (LibraryStore — no spec machinery). ────────────────

    val ShowMissingEpisodes = SettingsRow(
        id = "show_missing_episodes",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_show_missing_episodes,
        searchTitleRes = Res.string.ss_show_missing_episodes_title,
        searchSubtitleRes = Res.string.ss_show_missing_episodes_subtitle,
        keywords = listOf("missing", "episode", "unaired", "virtual", "placeholder", "season", "show"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
        gate = RowAdmission.Always,
    )

    // ── RESIDUAL row (LibraryStore — no spec machinery). ────────────────

    val PreferLogos = SettingsRow(
        id = "prefer_logos",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_prefer_logos,
        searchTitleRes = Res.string.ss_prefer_logos_title,
        searchSubtitleRes = Res.string.ss_prefer_logos_subtitle,
        keywords = listOf("logo", "clear logo", "title", "artwork", "image", "banner", "details", "prefer"),
        route = Route.AppearanceSettings(),
        gate = RowAdmission.Always,
    )

    /**
     * Spec-backed (appearance store), yet gated [RowAdmission.Always]: the
     * "Library & Cards" group predates and outlives the advanced toggle (the
     * shipped always-on behavior — the language high-contrast shipped quirk),
     * so the row's explicit gate overrides its spec's advanced flag.
     */
    val HapticsEnabled = SettingsRow(
        id = "haptics_enabled",
        icon = Tabler.Outline.DeviceMobileVibration,
        titleRes = Res.string.settings_haptic_feedback,
        searchTitleRes = Res.string.ss_haptics_enabled_title,
        searchSubtitleRes = Res.string.ss_haptics_enabled_subtitle,
        gate = RowAdmission.Always,
    )

    /** Spec-backed (experimental store); [RowAdmission.Always] — see [HapticsEnabled]. */
    val ShowShareMedia = SettingsRow(
        id = "show_share_media",
        icon = Tabler.Outline.Share,
        titleRes = Res.string.settings_show_share_media,
        searchTitleRes = Res.string.ss_show_share_media_title,
        searchSubtitleRes = Res.string.ss_show_share_media_subtitle,
        gate = RowAdmission.Always,
    )

    /** Spec-backed (experimental store); [RowAdmission.Always] — see [HapticsEnabled]. */
    val HideSearchHistory = SettingsRow(
        id = "hide_search_history",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_hide_search_history,
        searchTitleRes = Res.string.ss_hide_search_history_title,
        searchSubtitleRes = Res.string.ss_hide_search_history_subtitle,
        gate = RowAdmission.Always,
    )

    // ── Performance group (advanced-gated) ──────────────────────────────

    val PerformanceMode = SettingsRow(
        id = "performance_mode",
        icon = Tabler.Outline.Gauge,
        titleRes = Res.string.settings_performance_mode,
        searchTitleRes = Res.string.ss_performance_mode_title,
        searchSubtitleRes = Res.string.ss_performance_mode_subtitle,
    )

    val ReduceMotion = SettingsRow(
        id = "reduce_motion",
        icon = Tabler.Outline.Activity,
        titleRes = Res.string.settings_reduce_motion,
        searchTitleRes = Res.string.ss_reduce_motion_title,
        searchSubtitleRes = Res.string.ss_reduce_motion_subtitle,
    )

    // ── Eye Care group (advanced-gated) ─────────────────────────────────

    val BlueLightFilter = SettingsRow(
        id = "blue_light_filter",
        icon = Tabler.Outline.Moon,
        titleRes = Res.string.settings_blue_light_filter,
        searchTitleRes = Res.string.ss_blue_light_filter_title,
        searchSubtitleRes = Res.string.ss_blue_light_filter_subtitle,
    )

    val BlueLightStrength = SettingsRow(
        id = "blue_light_strength",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_blue_light_filter_strength,
        searchTitleRes = Res.string.ss_blue_light_strength_title,
        searchSubtitleRes = Res.string.ss_blue_light_strength_subtitle,
    )

    // ── Newsletter group (advanced-gated, spec-less residual rows) ──────

    val NewsletterEnabled = SettingsRow(
        id = "newsletter_enabled",
        icon = Tabler.Outline.Mail,
        titleRes = Res.string.settings_enable_newsletter,
        searchTitleRes = Res.string.ss_newsletter_enabled_title,
        searchSubtitleRes = Res.string.ss_newsletter_enabled_subtitle,
        keywords = listOf("newsletter", "digest", "email", "periodic", "report"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
    )

    val NewsletterDeliveryDay = SettingsRow(
        id = "newsletter_delivery_day",
        icon = Tabler.Outline.Calendar,
        titleRes = Res.string.settings_newsletter_delivery_day,
        searchTitleRes = Res.string.ss_newsletter_delivery_day_title,
        searchSubtitleRes = Res.string.ss_newsletter_delivery_day_subtitle,
        keywords = listOf("newsletter", "delivery", "day", "schedule", "weekday", "send"),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
    )

    /**
     * No screen title: the declared row renders as the runtime-reorderable
     * per-section rows (the media-segment enum-driven shape), titled by
     * `NewsletterSectionType.labelRes`.
     */
    val NewsletterSections = SettingsRow(
        id = "newsletter_sections",
        icon = Tabler.Outline.Mail,
        searchTitleRes = Res.string.ss_newsletter_sections_title,
        searchSubtitleRes = Res.string.ss_newsletter_sections_subtitle,
        keywords = listOf(
            "newsletter", "sections", "recently added", "activity log", "library stats",
            "continue watching", "next up", "curated picks", "content", "digest",
        ),
        route = Route.AppearanceSettings(),
        isAdvanced = true,
    )

    /**
     * Every fused appearance row — the ratchet's vocabulary. A computed
     * accessor (not an initializer): the group row lists are top-level vals
     * declared later in this file, and an eager field would turn the
     * object↔file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = AppearanceThemeRows + AppearanceNavigationRows + AppearanceLibraryRows +
            AppearancePerformanceRows + AppearanceEyeCareRows + AppearanceNewsletterRows
}

// ═══════════════════════════════════════════════════════════════════════
// The spec-derived derivation inputs: the appearance screen's searchable
// semantics live on the datastore-side spec declarations — this domain's own
// ([AppearancePreferenceSpecs]) plus the cross-domain knobs the library group
// renders (the experimental share-media / hide-search-history toggles). The
// row lists below are the ordered spine — presentation faces, catalog order,
// gates. (The home-discovery watch-state quartet moved to the home cards
// group — PS-4.)
// ═══════════════════════════════════════════════════════════════════════

private val searchRoutes: Map<String, Route> = mapOf(
    AppearancePreferenceSpecs.ROUTE_APPEARANCE_SETTINGS to Route.AppearanceSettings(),
)

private val appearanceSpecEntries: List<PreferenceSearchSpec> =
    AppearancePreferenceSpecs.searchEntries +
        ExperimentalPreferenceSpecs.appearanceSearchEntries

private val appearanceCategory = CoreUiRes.string.ss_cat_appearance

/** The theme group's 19 rows, in catalog order (= the spec declarations' order). */
internal val AppearanceThemeRows: List<SettingsRow> = listOf(
    AppearanceRows.DateFormat,
    AppearanceRows.FontScale,
    AppearanceRows.ColorBlindMode,
    AppearanceRows.HandMode,
    AppearanceRows.ThemeScheduler,
    AppearanceRows.ThemeMode,
    AppearanceRows.ThemeStyle,
    AppearanceRows.StyleAccent,
    AppearanceRows.DynamicTheming,
    AppearanceRows.OledMode,
    AppearanceRows.Contrast,
    AppearanceRows.LibraryViewMode,
    AppearanceRows.LayoutMode,
    AppearanceRows.ThemeMusic,
    AppearanceRows.NavLabels,
    AppearanceRows.AccentColor,
    AppearanceRows.ColorStyle,
    AppearanceRows.ScheduledStart,
    AppearanceRows.ScheduledEnd,
)

/**
 * The navigation-customization group's rows — HAND-MAINTAINED residuals: the
 * navigation knobs live in the spec-less NavigationStore until it migrates.
 */
internal val AppearanceNavigationRows: List<SettingsRow> = listOf(
    AppearanceRows.NavBarCustomization,
    AppearanceRows.NavHideOnScroll,
)

/**
 * The "Library & Cards" group's 8 rows: the five library-content residuals +
 * the appearance haptics toggle and the experimental share-media /
 * hide-search-history toggles (spec-backed). (The home-discovery watch-state
 * quartet moved to the home cards group — PS-4.)
 */
internal val AppearanceLibraryRows: List<SettingsRow> = listOf(
    AppearanceRows.HideEpisodeThumbnails,
    AppearanceRows.CompactEpisodeList,
    AppearanceRows.SkipSpecials,
    AppearanceRows.ShowMissingEpisodes,
    AppearanceRows.PreferLogos,
    AppearanceRows.HapticsEnabled,
    AppearanceRows.ShowShareMedia,
    AppearanceRows.HideSearchHistory,
)

/** The advanced-gated "Performance" group's rows (both spec-backed). */
internal val AppearancePerformanceRows: List<SettingsRow> = listOf(
    AppearanceRows.PerformanceMode,
    AppearanceRows.ReduceMotion,
)

/** The advanced-gated "Eye Care" group's rows (both spec-backed). */
internal val AppearanceEyeCareRows: List<SettingsRow> = listOf(
    AppearanceRows.BlueLightFilter,
    AppearanceRows.BlueLightStrength,
)

/** The advanced-gated "Newsletter" group's rows — spec-less residuals. */
internal val AppearanceNewsletterRows: List<SettingsRow> = listOf(
    AppearanceRows.NewsletterEnabled,
    AppearanceRows.NewsletterDeliveryDay,
    AppearanceRows.NewsletterSections,
)

/**
 * The appearance screen's six screen groups — items AND per-row admissions
 * derive from the row lists above in one act ([List.asRowGroup]), so the
 * declaration is the single home of the groups' order, faces and gates.
 * Aggregated in [SettingsSearchCatalog] (via [SettingsScreenGroups]); the
 * projection is fail-fast at init on any row↔spec drift.
 */
internal val AppearanceThemeGroup =
    AppearanceThemeRows.asRowGroup("appearance.theme", specEntriesFor(AppearanceThemeRows, appearanceSpecEntries), searchRoutes, appearanceCategory)

internal val AppearanceNavigationGroup =
    AppearanceNavigationRows.asRowGroup("appearance.navigation", emptyList(), searchRoutes, appearanceCategory)

internal val AppearanceLibraryGroup =
    AppearanceLibraryRows.asRowGroup("appearance.library", specEntriesFor(AppearanceLibraryRows, appearanceSpecEntries), searchRoutes, appearanceCategory)

internal val AppearancePerformanceGroup =
    AppearancePerformanceRows.asRowGroup("appearance.performance", specEntriesFor(AppearancePerformanceRows, appearanceSpecEntries), searchRoutes, appearanceCategory)

internal val AppearanceEyeCareGroup =
    AppearanceEyeCareRows.asRowGroup("appearance.eyeCare", specEntriesFor(AppearanceEyeCareRows, appearanceSpecEntries), searchRoutes, appearanceCategory)

internal val AppearanceNewsletterGroup =
    AppearanceNewsletterRows.asRowGroup("appearance.newsletter", emptyList(), searchRoutes, appearanceCategory)

// The catalog projections, kept as named vals — the search/catalog-order pins
// (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these lists.

/** The catalog projection of the theme group. */
internal val AppearanceThemeSearchItems: List<SettingsSearchItem> = AppearanceThemeGroup.items

/** The catalog projection of the navigation-customization group. */
internal val AppearanceNavigationSearchItems: List<SettingsSearchItem> = AppearanceNavigationGroup.items

/** The catalog projection of the "Library & Cards" group. */
internal val AppearanceLibrarySearchItems: List<SettingsSearchItem> = AppearanceLibraryGroup.items

/** The catalog projection of the performance group. */
internal val AppearancePerformanceSearchItems: List<SettingsSearchItem> = AppearancePerformanceGroup.items

/** The catalog projection of the eye-care group. */
internal val AppearanceEyeCareSearchItems: List<SettingsSearchItem> = AppearanceEyeCareGroup.items

/** The catalog projection of the newsletter group. */
internal val AppearanceNewsletterSearchItems: List<SettingsSearchItem> = AppearanceNewsletterGroup.items

// ── The domain's root-screen entrance declaration ────────────────────────

/**
 * The appearance domain's root-screen entrance — the ONE ordered declaration
 * that drives BOTH the settings root's `item_appearance` section emission
 * (icon/title/route id) and its entrance-step index (the [section] entry
 * spliced into [SETTINGS_ENTRANCE_SECTIONS] at this domain's render
 * position). The retired hand pair — the literal entry in the sections list
 * plus the hand-written call site — could drift only by failing the
 * SettingsEntranceStepsTest pins; the declaration-driven shape cannot drift
 * at all (both faces read this object). The per-domain rollout recipe: each
 * converted domain declares one of these, splices [section] into the list,
 * consumes the faces at the call site, and adds its key to the guard test's
 * converted set (the regex scanner cannot see declaration-driven call sites).
 */
internal val AppearanceEntrance = SettingsEntranceSectionRow(
    key = "item_appearance",
    rowId = "appearance",
    icon = Tabler.Outline.Palette,
    titleRes = Res.string.settings_appearance,
)
