package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_home
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_anti_spoiler_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_anti_spoiler_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hidden_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hidden_libraries_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_new_badges
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_new_badges_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_quality_badges
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_quality_badges_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_classic_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_clock_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_configure_libraries_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_continue_watching_click_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discover_rows_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_discover_rows_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hero_section_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_top_header_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_top_header_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hide_watched_items_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_backdrop_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_layout_presets_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_home_mode_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hidden_libraries_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_hidden_libraries_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_merge_continue_next_up_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_hidden_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_hidden_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_max_days_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_next_up_rewatching_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_pinned_home_sections_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_settings_in_home_search_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_external_ratings_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_unwatched_badge_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_show_watched_checkmark_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_unhide_cw_title

/**
 * The home domain's fused row declarations — the feature-side single home of
 * every home-hub row's presentation, ordering, and capability (the
 * [AppearanceRows] template). Each [SettingsRow] replaces the trio the domain
 * used to declare per row: the `SettingsSearchBinding` entry, the
 * `SettingsRowRecord` entry, and the `HomeSettingsIds` holder constant (all
 * retired).
 *
 * The SEMANTICS stay two-homed by design: every row is backed by a
 * [HomeDiscoveryPreferenceSpecs] spec entry — the keywords, advanced flag,
 * platform rule and route kind are declared once next to the store — so the
 * rows carry screen faces only. The one exception is `unhide_cw`
 * ([RowAdmission.ContentGated]): it renders only while hidden
 * continue-watching items exist, a content-state condition the admission
 * flags do not carry, so it counts in no total and the screen adds the +1
 * explicitly (the shipped shape). The projection fails fast at catalog init
 * on any row-spec drift; search results, catalog order, group membership and
 * per-gate visibility are byte-identical to the retired declarations.
 */
internal object HomeRows {

    // -- The "Home Display" group's rows (the display toggles, the home-wave
    //    H7/H10/H17/H18/H19/H21 features, and the CW/Next-Up curation trio),
    //    in catalog order (every row spec-backed). --

    val HomeMode = SettingsRow(
        id = "home_mode",
        icon = Tabler.Outline.Home,
        titleRes = Res.string.settings_home_mode,
        searchTitleRes = Res.string.ss_home_mode_title,
        searchSubtitleRes = Res.string.ss_home_mode_subtitle,
    )

    val HeroSection = SettingsRow(
        id = "hero_section",
        icon = Tabler.Outline.LayersLinked,
        titleRes = Res.string.settings_show_hero_section,
        searchTitleRes = Res.string.ss_hero_section_title,
        searchSubtitleRes = Res.string.ss_hero_section_subtitle,
    )

    val HomeBackdrop = SettingsRow(
        id = "home_backdrop",
        icon = Tabler.Outline.Photo,
        titleRes = Res.string.settings_home_backdrop,
        searchTitleRes = Res.string.ss_home_backdrop_title,
        searchSubtitleRes = Res.string.ss_home_backdrop_subtitle,
    )

    /**
     * H7 video hero billboard (touch/default form factors): muted looping
     * ThemeVideo → trailer-embed fallback on dwell. Default off. The search
     * title folds to the screen title (the one-key family: the spec's
     * titleKey names the `settings_*` resource — no ss_* twin).
     */
    val HeroVideo = SettingsRow(
        id = "home_hero_video",
        icon = Tabler.Outline.Video,
        titleRes = Res.string.settings_home_hero_video,
        searchSubtitleRes = Res.string.settings_home_hero_video_subtitle,
    )

    /** H7's TV form-factor twin — same clip pipeline, separate switch. */
    val HeroVideoTv = SettingsRow(
        id = "home_hero_video_tv",
        icon = Tabler.Outline.DeviceTv,
        titleRes = Res.string.settings_home_hero_video_tv,
        searchSubtitleRes = Res.string.settings_home_hero_video_tv_subtitle,
    )

    /**
     * H10 focus-driven ambient backdrop (Android TV): the focused card
     * repaints the ambient backdrop. Default off.
     */
    val FocusBackdrop = SettingsRow(
        id = "home_focus_backdrop",
        icon = Tabler.Outline.Focus,
        titleRes = Res.string.settings_home_focus_backdrop,
        searchSubtitleRes = Res.string.settings_home_focus_backdrop_subtitle,
    )

    /** H17 personal stats card ("Your library week") between hero and the first row. Default off. */
    val StatsCard = SettingsRow(
        id = "home_stats_card",
        icon = Tabler.Outline.ChartBar,
        titleRes = Res.string.settings_home_stats_card,
        searchSubtitleRes = Res.string.settings_home_stats_card_subtitle,
    )

    /** H18 genre quick-access rail (chips above the first row). Default off. */
    val GenreRail = SettingsRow(
        id = "home_genre_rail",
        icon = Tabler.Outline.Category,
        titleRes = Res.string.settings_home_genre_rail,
        searchSubtitleRes = Res.string.settings_home_genre_rail_subtitle,
    )

    /** H19 watch-party / presence chips ("Alex is watching Dune — join"). Default off; admin-gated. */
    val PresenceChips = SettingsRow(
        id = "home_presence_chips",
        icon = Tabler.Outline.Users,
        titleRes = Res.string.settings_home_presence_chips,
        searchSubtitleRes = Res.string.settings_home_presence_chips_subtitle,
    )

    /** H21 new-section announcement toast ("New on Home: … — Customize"). Default on. */
    val NewSectionAnnouncements = SettingsRow(
        id = "home_new_section_announcements",
        icon = Tabler.Outline.Speakerphone,
        titleRes = Res.string.settings_new_section_announcements,
        searchSubtitleRes = Res.string.settings_new_section_announcements_subtitle,
    )

    val ClockHome = SettingsRow(
        id = "clock_home",
        icon = Tabler.Outline.Clock,
        titleRes = Res.string.settings_show_clock_home,
        searchTitleRes = Res.string.ss_clock_home_title,
        searchSubtitleRes = Res.string.ss_clock_home_subtitle,
    )

    val HideTopHeader = SettingsRow(
        id = "hide_top_header",
        icon = Tabler.Outline.ArrowBarToDown,
        titleRes = Res.string.settings_hide_top_header_on_scroll,
        searchTitleRes = Res.string.ss_hide_top_header_title,
        searchSubtitleRes = Res.string.ss_hide_top_header_subtitle,
    )

    val SettingsInHomeSearch = SettingsRow(
        id = "settings_in_home_search",
        icon = Tabler.Outline.Adjustments,
        titleRes = Res.string.settings_show_settings_in_home_search,
        searchTitleRes = Res.string.ss_settings_in_home_search_title,
        searchSubtitleRes = Res.string.ss_settings_in_home_search_subtitle,
    )

    val ContinueWatchingClick = SettingsRow(
        id = "continue_watching_click",
        icon = Tabler.Outline.PlayerPlay,
        titleRes = Res.string.settings_continue_watching_tap,
        subtitleRes = Res.string.settings_continue_watching_tap_subtitle,
        searchTitleRes = Res.string.ss_continue_watching_click_title,
        searchSubtitleRes = Res.string.ss_continue_watching_click_subtitle,
    )

    val ClassicRows = SettingsRow(
        id = "classic_rows",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_classic_rows,
        searchTitleRes = Res.string.ss_classic_rows_title,
        searchSubtitleRes = Res.string.ss_classic_rows_subtitle,
    )

    val NextUpHidden = SettingsRow(
        id = "next_up_hidden",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_next_up_hidden,
        subtitleRes = Res.string.settings_next_up_hidden_brief,
        searchTitleRes = Res.string.ss_next_up_hidden_title,
        searchSubtitleRes = Res.string.ss_next_up_hidden_subtitle,
    )

    val UnhideCw = SettingsRow(
        id = "unhide_cw",
        icon = Tabler.Outline.Eye,
        titleRes = Res.string.settings_unhide_continue_watching,
        searchTitleRes = Res.string.ss_unhide_cw_title,
        searchSubtitleRes = Res.string.ss_unhide_cw_subtitle,
        gate = RowAdmission.ContentGated,
    )

    /**
     * H5's restore surface: libraries hidden from the My Media tiles row (the
     * per-library config sheet's hide action), with per-library and bulk
     * restore behind the Route.MyMediaHidden drill-in.
     */
    val HiddenLibraries = SettingsRow(
        id = "hidden_libraries",
        icon = Tabler.Outline.Folders,
        titleRes = Res.string.settings_hidden_libraries,
        subtitleRes = Res.string.settings_hidden_libraries_brief,
        searchTitleRes = Res.string.ss_hidden_libraries_title,
        searchSubtitleRes = Res.string.ss_hidden_libraries_subtitle,
    )

    // -- The "Continue Watching & Next Up" group's three rows, in catalog order. --

    val MergeContinueNextUp = SettingsRow(
        id = "merge_continue_next_up",
        icon = Tabler.Outline.LayersLinked,
        titleRes = Res.string.settings_merge_continue_next_up,
        searchTitleRes = Res.string.ss_merge_continue_next_up_title,
        searchSubtitleRes = Res.string.ss_merge_continue_next_up_subtitle,
    )

    val NextUpMaxDays = SettingsRow(
        id = "next_up_max_days",
        icon = Tabler.Outline.CalendarTime,
        titleRes = Res.string.settings_next_up_time_window,
        subtitleRes = Res.string.settings_next_up_time_window_subtitle,
        searchTitleRes = Res.string.ss_next_up_max_days_title,
        searchSubtitleRes = Res.string.ss_next_up_max_days_subtitle,
    )

    val NextUpRewatching = SettingsRow(
        id = "next_up_rewatching",
        icon = Tabler.Outline.History,
        titleRes = Res.string.settings_rewatching_next_up,
        searchTitleRes = Res.string.ss_next_up_rewatching_title,
        searchSubtitleRes = Res.string.ss_next_up_rewatching_subtitle,
    )

    /**
     * H6 active-session-aware Continue Watching: don't offer "continue" for an
     * item currently playing on another device. Default off.
     */
    val ExcludeActiveSessions = SettingsRow(
        id = "home_cw_exclude_active_sessions",
        icon = Tabler.Outline.Ban,
        titleRes = Res.string.settings_exclude_active_sessions,
        searchSubtitleRes = Res.string.settings_exclude_active_sessions_subtitle,
    )

    // -- The "Home Screen Layout" group's four drill-in rows + the H20
    //    auto-push toggle, in catalog order. --

    val PinnedHomeSections = SettingsRow(
        id = "pinned_home_sections",
        icon = Tabler.Outline.Pinned,
        titleRes = Res.string.settings_pinned_home_sections,
        subtitleRes = Res.string.settings_pinned_home_sections_brief,
        searchTitleRes = Res.string.ss_pinned_home_sections_title,
        searchSubtitleRes = Res.string.ss_pinned_home_sections_subtitle,
    )

    val HomeLayoutPresets = SettingsRow(
        id = "home_layout_presets",
        icon = Tabler.Outline.Bookmarks,
        titleRes = Res.string.settings_home_layout_presets,
        subtitleRes = Res.string.settings_home_layout_presets_brief,
        searchTitleRes = Res.string.ss_home_layout_presets_title,
        searchSubtitleRes = Res.string.ss_home_layout_presets_subtitle,
    )

    val ConfigureLibraries = SettingsRow(
        id = "configure_libraries",
        icon = Tabler.Outline.Folders,
        titleRes = Res.string.settings_configure_libraries,
        subtitleRes = Res.string.settings_configure_libraries_desc,
        searchTitleRes = Res.string.ss_configure_libraries_title,
        searchSubtitleRes = Res.string.ss_configure_libraries_subtitle,
    )

    val DiscoverRows = SettingsRow(
        id = "discover_rows",
        icon = Tabler.Outline.Compass,
        titleRes = Res.string.settings_discover_rows,
        subtitleRes = Res.string.settings_discover_rows_helper,
        searchTitleRes = Res.string.ss_discover_rows_title,
        searchSubtitleRes = Res.string.ss_discover_rows_subtitle,
    )

    /** H20 layout DisplayPreferences auto-push (after any layout-writing command, debounced). Default off. */
    val LayoutAutopush = SettingsRow(
        id = "home_layout_autopush",
        icon = Tabler.Outline.CloudUpload,
        titleRes = Res.string.settings_home_layout_autopush,
        searchSubtitleRes = Res.string.settings_home_layout_autopush_subtitle,
    )

    // -- The "Cards" group's rows: the PS-4 moved card-display quartet plus
    //    the home-wave badge/spoiler/trickplay toggles, in catalog order. --

    val ShowUnwatchedBadge = SettingsRow(
        id = "show_unwatched_badge",
        icon = Tabler.Outline.Folder,
        titleRes = Res.string.settings_show_unwatched_badge,
        subtitleRes = Res.string.settings_show_unwatched_badge_subtitle,
        searchTitleRes = Res.string.ss_show_unwatched_badge_title,
        searchSubtitleRes = Res.string.ss_show_unwatched_badge_subtitle,
    )

    val ShowWatchedCheckmark = SettingsRow(
        id = "show_watched_checkmark",
        icon = Tabler.Outline.CircleCheck,
        titleRes = Res.string.settings_show_watched_checkmark,
        subtitleRes = Res.string.settings_show_watched_checkmark_subtitle,
        searchTitleRes = Res.string.ss_show_watched_checkmark_title,
        searchSubtitleRes = Res.string.ss_show_watched_checkmark_subtitle,
    )

    val HideWatchedItems = SettingsRow(
        id = "hide_watched_items",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_hide_watched_items,
        subtitleRes = Res.string.settings_hide_watched_items_subtitle,
        searchTitleRes = Res.string.ss_hide_watched_items_title,
        searchSubtitleRes = Res.string.ss_hide_watched_items_subtitle,
    )

    val ShowExternalRatings = SettingsRow(
        id = "show_external_ratings",
        icon = Tabler.Outline.Star,
        titleRes = Res.string.settings_show_external_ratings,
        subtitleRes = Res.string.settings_show_external_ratings_subtitle,
        searchTitleRes = Res.string.ss_show_external_ratings_title,
        searchSubtitleRes = Res.string.ss_show_external_ratings_subtitle,
    )

    /** H11 quality badges (4K / HDR / DV / Atmos) on poster + wide cards. Default on. */
    val ShowQualityBadges = SettingsRow(
        id = "show_quality_badges",
        icon = Tabler.Outline.Badge4k,
        titleRes = Res.string.settings_show_quality_badges,
        subtitleRes = Res.string.settings_show_quality_badges_subtitle,
        searchSubtitleRes = Res.string.settings_show_quality_badges_subtitle,
    )

    /** H12 "NEW" badges for items added since the last home visit. Default on. */
    val ShowNewBadges = SettingsRow(
        id = "show_new_badges",
        icon = Tabler.Outline.Sparkles,
        titleRes = Res.string.settings_show_new_badges,
        subtitleRes = Res.string.settings_show_new_badges_subtitle,
        searchSubtitleRes = Res.string.settings_show_new_badges_subtitle,
    )

    /**
     * H13 trickplay preview on wide (resume/next-up) cards — TV-only v1: the
     * row renders only on the TV form factor ([RowAdmission.Tv]; the spec's
     * ANDROID_ONLY tag keeps the search hit off desktop). Default on.
     */
    val TrickplayPreview = SettingsRow(
        id = "home_trickplay_preview",
        icon = Tabler.Outline.Movie,
        titleRes = Res.string.settings_home_trickplay_preview,
        searchSubtitleRes = Res.string.settings_home_trickplay_preview_subtitle,
        gate = RowAdmission.Tv,
    )

    /** H15 anti-spoiler home master switch — force-hides watch-state spoilers across home. Default off. */
    val AntiSpoilerHome = SettingsRow(
        id = "anti_spoiler_home",
        icon = Tabler.Outline.EyeOff,
        titleRes = Res.string.settings_anti_spoiler_home,
        subtitleRes = Res.string.settings_anti_spoiler_home_subtitle,
        searchSubtitleRes = Res.string.settings_anti_spoiler_home_subtitle,
    )

    /**
     * Every fused home row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = HomeDisplayRows + HomeNextUpRows + HomeLayoutRows + HomeCardsRows
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = mapOf(
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_SETTINGS to Route.HomeSettings(),
    HomeDiscoveryPreferenceSpecs.ROUTE_NEXT_UP_EXCLUDED to Route.NextUpExcluded,
    HomeDiscoveryPreferenceSpecs.ROUTE_MY_MEDIA_HIDDEN to Route.MyMediaHidden,
    HomeDiscoveryPreferenceSpecs.ROUTE_PINNED_HOME_SECTIONS to Route.PinnedHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_HOME_LAYOUT_PRESETS to Route.HomeLayoutPresets(),
    HomeDiscoveryPreferenceSpecs.ROUTE_LIBRARY_HOME_SECTIONS to Route.LibraryHomeSections(),
    HomeDiscoveryPreferenceSpecs.ROUTE_DISCOVER_ROWS to Route.DiscoverRows(),
)

private val homeSpecEntries: List<PreferenceSearchSpec> = HomeDiscoveryPreferenceSpecs.searchEntries

private val homeCategory = CoreUiRes.string.ss_cat_home

internal val HomeDisplayRows: List<SettingsRow> = listOf(
    HomeRows.HomeMode,
    HomeRows.HeroSection,
    HomeRows.HeroVideo,
    HomeRows.HeroVideoTv,
    HomeRows.HomeBackdrop,
    HomeRows.FocusBackdrop,
    HomeRows.ClockHome,
    HomeRows.HideTopHeader,
    HomeRows.SettingsInHomeSearch,
    HomeRows.StatsCard,
    HomeRows.GenreRail,
    HomeRows.PresenceChips,
    HomeRows.NewSectionAnnouncements,
    HomeRows.ContinueWatchingClick,
    HomeRows.ClassicRows,
    HomeRows.NextUpHidden,
    HomeRows.HiddenLibraries,
    HomeRows.UnhideCw,
)

internal val HomeNextUpRows: List<SettingsRow> = listOf(
    HomeRows.MergeContinueNextUp,
    HomeRows.NextUpMaxDays,
    HomeRows.NextUpRewatching,
    HomeRows.ExcludeActiveSessions,
)

internal val HomeLayoutRows: List<SettingsRow> = listOf(
    HomeRows.PinnedHomeSections,
    HomeRows.HomeLayoutPresets,
    HomeRows.ConfigureLibraries,
    HomeRows.DiscoverRows,
    HomeRows.LayoutAutopush,
)

internal val HomeCardsRows: List<SettingsRow> = listOf(
    HomeRows.ShowUnwatchedBadge,
    HomeRows.ShowWatchedCheckmark,
    HomeRows.HideWatchedItems,
    HomeRows.ShowExternalRatings,
    HomeRows.ShowQualityBadges,
    HomeRows.ShowNewBadges,
    HomeRows.TrickplayPreview,
    HomeRows.AntiSpoilerHome,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val HomeDisplayGroup =
    HomeDisplayRows.asRowGroup("home.display", specEntriesFor(HomeDisplayRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeNextUpGroup =
    HomeNextUpRows.asRowGroup("home.nextUp", specEntriesFor(HomeNextUpRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeLayoutGroup =
    HomeLayoutRows.asRowGroup("home.layout", specEntriesFor(HomeLayoutRows, homeSpecEntries), searchRoutes, homeCategory)

internal val HomeCardsGroup =
    HomeCardsRows.asRowGroup("home.cards", specEntriesFor(HomeCardsRows, homeSpecEntries), searchRoutes, homeCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val HomeDisplaySearchItems: List<SettingsSearchItem> = HomeDisplayGroup.items

internal val HomeNextUpSearchItems: List<SettingsSearchItem> = HomeNextUpGroup.items

internal val HomeLayoutSearchItems: List<SettingsSearchItem> = HomeLayoutGroup.items

internal val HomeCardsSearchItems: List<SettingsSearchItem> = HomeCardsGroup.items

// -- The domain's root-screen entrance declaration --

/** The home domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_home` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val HomeEntrance = SettingsEntranceSectionRow(
    key = "item_home",
    rowId = "home",
    icon = Tabler.Outline.Home,
    titleRes = Res.string.settings_home_title,
)
