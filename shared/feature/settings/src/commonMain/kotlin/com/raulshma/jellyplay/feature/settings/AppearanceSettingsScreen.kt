package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.accentOptions
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.ui.components.homeSectionIcon
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.ConsumeSettingsItemIndex
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_artwork_dynamic
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_strength_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_color_blind_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_color_blind_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_comfort_eye_care
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_compact_episode_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_compact_episode_list_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_high
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_standard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_friday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_monday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_saturday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_sunday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_thursday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_tuesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_wednesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_newsletter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_enable_newsletter_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_haptic_feedback
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_haptic_feedback_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_episode_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_episode_thumbnails_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_search_history
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_search_history_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_screen_layout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_sections_visible
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_grid
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_masonry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_thumb
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_labels_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_labels_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_activity_log
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_activity_log_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_continue_watching_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_curated_picks
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_curated_picks_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_delivery_day
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_delivery_day_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_library_stats
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_library_stats_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_next_up_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_recently_added
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_recently_added_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_sections_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_overridden_variant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_standard_experience
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_style_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_all_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_watched
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_share_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_skip_specials
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_unwatched_badges
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_watched_checkmarks
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_always_dark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_always_light
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_follow_system
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_scheduled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_days

private val THEME_HIGHLIGHT_IDS = setOf("theme_mode", "theme_scheduler")
private val APPEARANCE_LIBRARY_GROUP_IDS = setOf("show_unwatched_badge", "show_watched_checkmark", "hide_watched_items", "hide_episode_thumbnails", "skip_specials", "show_share_media", "show_external_ratings")
private val PERFORMANCE_GROUP_IDS = setOf("performance_mode", "reduce_motion")
private val BLUE_LIGHT_GROUP_IDS = setOf("blue_light_filter", "blue_light_strength")
private val NEWSLETTER_GROUP_IDS = setOf("newsletter_enabled", "newsletter_delivery_day", "newsletter_sections")

/**
 * The persisted accent id for a themed variant, or null when the variant has
 * no accent (standard uses the global swatch; monochrome is fixed). Single
 * source for both the group summary and the style_accent picker.
 */
private fun accentIdFor(variant: ThemeVariant, preferences: AppearanceScreenPreferences): String? = when (variant) {
    ThemeVariant.SYNTHWAVE -> preferences.synthwaveAccent
    ThemeVariant.SOOTHING -> preferences.soothingAccent
    ThemeVariant.VIVID -> preferences.vividAccent
    ThemeVariant.AURORA -> preferences.auroraAccent
    ThemeVariant.SAKURA -> preferences.sakuraAccent
    ThemeVariant.VECTOR_POP -> preferences.vectorPopAccent
    ThemeVariant.STANDARD, ThemeVariant.MONOCHROME -> null
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions = SettingsNavActions(),
    highlightSettingId: String? = null,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "appearance_init",
    )

    val homeLayoutGroup = remember { listOf("pinned_home_sections", "home_layout_presets", "configure_libraries", "home_section_layout") }
    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId, showAdvanced) {
        val themeGroup = listOf(
            "theme_mode", "theme_scheduler", "theme_style", "style_accent",
            "dynamic_theming", "oled_mode", "contrast", "library_view_mode", "home_mode", "hero_section", "home_backdrop",
            "clock_home", "settings_in_home_search", "continue_watching_click", "unhide_cw", "merge_continue_next_up", "next_up_max_days",
            "next_up_rewatching", "theme_music", "nav_labels", "date_format", "font_scale", "color_blind_mode",
            "hand_mode", "scheduled_start", "scheduled_end",
        )
        val libraryGroup = listOf(
            "show_unwatched_badge", "show_watched_checkmark", "hide_watched_items", "hide_episode_thumbnails",
            "skip_specials", "haptics_enabled", "show_share_media", "hide_search_history", "show_external_ratings",
        )
        val performanceGroup = listOf("performance_mode", "reduce_motion")
        val eyeCareGroup = listOf("blue_light_filter", "blue_light_strength")
        val newsletterGroup = listOf("newsletter_enabled", "newsletter_delivery_day")
        val navBarGroup = listOf("nav_bar_customization", "nav_hide_on_scroll")
        // Index 0 = Theme, 1 = Navigation customization, 2 = Library & Cards, 3 = Home Screen Layout.
        // Performance/Eye Care/Newsletter only exist when advanced is on
        // and occupy indices 4/5/6 respectively.
        when (highlightSettingId) {
            in themeGroup -> 0
            in navBarGroup -> 1
            in libraryGroup -> 2
            in homeLayoutGroup -> 3
            in performanceGroup -> if (showAdvanced) 4 else -1
            in eyeCareGroup -> if (showAdvanced) 5 else -1
            in newsletterGroup -> if (showAdvanced) 6 else -1
            else -> -1
        }
    }

    // Phase 1 (coarse): scroll the containing group into the LazyColumn's composition window so the
    // target item is actually composed — items in off-screen groups (later sections) are otherwise
    // never mounted and their bringIntoViewRequester has no target. Phase 2 (centering) is then
    // performed by the highlighted item itself via CenterBringIntoViewSpec.
    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }
    var showBlueLightStrengthSheet by remember { mutableStateOf(false) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_appearance_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.focusIndicator(CircleShape),
            ) {
                Icon(
                    Tabler.Outline.Refresh,
                    contentDescription = stringResource(Res.string.settings_reset_defaults_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides CenterBringIntoViewSpec
        ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvFocusRestorer()
                .focusRequester(focusRequester),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Palette,
                    title = stringResource(Res.string.settings_theme),
                    summary = {
                        val variant = ThemeVariant.fromId(preferences.themeVariant)
                        val parts = mutableListOf<String>()
                        if (variant != ThemeVariant.STANDARD) {
                            val accentId = accentIdFor(variant, preferences)
                            if (accentId != null) {
                                val accentLabel = variant.accentOptions()
                                    ?.find { it.id == accentId.lowercase() }?.label
                                    ?: accentId.lowercase().replaceFirstChar { it.uppercase() }
                                parts.add(stringResource(Res.string.settings_style_summary, variant.displayName, accentLabel))
                            } else {
                                parts.add(variant.displayName)
                            }
                        } else {
                            parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                            val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
                            parts.add("$accentName accent")
                            parts.add(preferences.colorStyle.displayName)
                            if (preferences.dynamicTheming) parts.add(stringResource(Res.string.settings_artwork_dynamic))
                        }
                        if (preferences.oledMode) parts.add("OLED")
                        if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add("${preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }} contrast")
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val isAndroid12 = settingsCapabilities.supportsDynamicColor
                    val isDarkActive = when (preferences.themeMode) {
                        ThemeMode.DARK -> true
                        ThemeMode.LIGHT -> false
                        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                        ThemeMode.SCHEDULED -> {
                            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                            val start = preferences.scheduledThemeStartHour
                            val end = preferences.scheduledThemeEndHour
                            if (start <= end) hour in start until end else hour >= start || hour < end
                        }
                    }

                    // Accent swatches must preview the shade the applied scheme
                    // actually uses — the effective dark flag (themeMode plus the
                    // dark-locked variants), not the raw system setting.
                    val effectiveDarkForSwatches = isDarkActive ||
                        ThemeVariant.fromId(preferences.themeVariant).isDarkLocked

                    val appearanceItems = remember(
                        preferences.themeVariant,
                        preferences.themeMode,
                        preferences.hiddenCwItemIds,
                        showAdvanced,
                        isDarkActive,
                        isAndroid12,
                    ) {
                        buildList {
                            val variant = ThemeVariant.fromId(preferences.themeVariant)
                            val isStandard = variant == ThemeVariant.STANDARD
                            add("theme_mode")
                            add("theme_style")
                            if (variant.accentOptions() != null) {
                                add("style_accent")
                            }
                            if (isStandard) {
                                add("accent_color")
                                add("color_style")
                                if (isAndroid12) add("dynamic_theming")
                            }
                            if (isDarkActive && variant.allowsOled) add("oled_mode")
                            if (showAdvanced) {
                                add("contrast")
                                add("library_view_mode")
                                add("home_mode")
                                add("hero_section")
                                add("home_backdrop")
                                add("clock_home")
                                add("hide_top_header")
                                add("settings_in_home_search")
                                add("continue_watching_click")
                                if (preferences.hiddenCwItemIds.isNotEmpty()) {
                                    add("unhide_cw")
                                }
                                add("merge_continue_next_up")
                                add("next_up_max_days")
                                add("next_up_rewatching")
                                add("theme_music")
                                add("nav_labels")
                                add("date_format")
                                add("font_scale")
                                add("color_blind_mode")
                                add("hand_mode")
                                if (preferences.themeMode == ThemeMode.SCHEDULED) {
                                    add("scheduled_start")
                                    add("scheduled_end")
                                }
                            }
                        }
                    }
                    SettingsItemList(total = appearanceItems.size) {
                    appearanceItems.forEach { item ->
                        when (item) {
                            "theme_mode" -> {
                                val themeTitle = stringResource(Res.string.settings_theme_mode)
                                val themeFollowSystem = stringResource(Res.string.settings_theme_follow_system)
                                val themeAlwaysLight = stringResource(Res.string.settings_theme_always_light)
                                val themeAlwaysDark = stringResource(Res.string.settings_theme_always_dark)
                                val themeVariant = ThemeVariant.fromId(preferences.themeVariant)
                                // Aurora/Synthwave force dark, so the light/dark choice is inert.
                                val isDarkLocked = themeVariant.isDarkLocked
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = themeTitle,
                                    subtitle = if (isDarkLocked) {
                                        stringResource(Res.string.settings_overridden_variant, themeVariant.displayName)
                                    } else {
                                        when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> themeFollowSystem
                                            ThemeMode.LIGHT -> themeAlwaysLight
                                            ThemeMode.DARK -> themeAlwaysDark
                                            ThemeMode.SCHEDULED -> stringResource(Res.string.settings_theme_scheduled, preferences.scheduledThemeStartHour, preferences.scheduledThemeEndHour)
                                        }
                                    },
                                    trailingText = if (isDarkLocked) "-" else preferences.themeMode.name,
                                    highlighted = highlightSettingId in THEME_HIGHLIGHT_IDS,
                                    onClick = {
                                        if (!isDarkLocked) {
                                            val themeLabels = mapOf(
                                                ThemeMode.SYSTEM to themeFollowSystem,
                                                ThemeMode.LIGHT to themeAlwaysLight,
                                                ThemeMode.DARK to themeAlwaysDark,
                                                ThemeMode.SCHEDULED to "Scheduled",
                                            )
                                            activePicker = PickerState.List(
                                                title = themeTitle,
                                                items = ThemeMode.entries,
                                                label = { themeLabels[it] ?: it.name },
                                                isSelected = { it == preferences.themeMode },
                                                onSelect = { viewModel.edit { scope -> scope.appearance.setThemeMode(it) } },
                                            )
                                        }
                                    },
                                )
                            }
                            "theme_style" -> {
                                val styleTitle = stringResource(Res.string.settings_theme_style)
                                val styleSubtitle = stringResource(Res.string.settings_theme_style_subtitle)
                                SettingListItem(
                                    icon = Tabler.Outline.Palette,
                                    title = styleTitle,
                                    subtitle = styleSubtitle,
                                    trailingText = ThemeVariant.fromId(preferences.themeVariant).displayName,
                                    highlighted = highlightSettingId == "theme_style",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = styleTitle,
                                            items = ThemeVariant.entries,
                                            label = { it.displayName },
                                            isSelected = { it == ThemeVariant.fromId(preferences.themeVariant) },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setThemeVariant(it.name.lowercase()) } },
                                        )
                                    },
                                )
                            }
                            "style_accent" -> {
                                ConsumeSettingsItemIndex()
                                val styleVariant = ThemeVariant.fromId(preferences.themeVariant)
                                com.raulshma.jellyplay.core.ui.components.VariantAccentPicker(
                                    variant = styleVariant,
                                    isDark = effectiveDarkForSwatches,
                                    selectedAccent = accentIdFor(styleVariant, preferences) ?: "",
                                    onAccentSelected = { accent ->
                                        viewModel.edit { it.appearance.setVariantAccent(preferences.themeVariant, accent) }
                                    },
                                )
                            }
                            "accent_color" -> {
                                ConsumeSettingsItemIndex()
                                com.raulshma.jellyplay.core.ui.components.AccentColorPicker(
                                    selectedSwatch = preferences.accentColorSwatch,
                                    onSwatchSelected = { viewModel.edit { scope -> scope.appearance.setAccentColorSwatch(it) } },
                                )
                            }
                            "color_style" -> {
                                ConsumeSettingsItemIndex()
                                com.raulshma.jellyplay.core.ui.components.ColorStylePicker(
                                    selectedStyle = preferences.colorStyle,
                                    onStyleSelected = { viewModel.edit { scope -> scope.appearance.setColorStyle(it) } },
                                )
                            }
                            "dynamic_theming" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Video,
                                    title = stringResource(Res.string.settings_dynamic_theming),
                                    subtitle = stringResource(Res.string.settings_dynamic_theming_subtitle),
                                    checked = preferences.dynamicTheming,
                                    highlighted = highlightSettingId == "dynamic_theming",
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setDynamicTheming(it) } },
                                )
                            }
                            "oled_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.BrightnessHalf,
                                    title = stringResource(Res.string.settings_oled_mode),
                                    subtitle = stringResource(Res.string.settings_oled_mode_subtitle),
                                    checked = preferences.oledMode,
                                    highlighted = highlightSettingId == "oled_mode",
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setOledMode(it) } },
                                )
                            }
                            "contrast" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = stringResource(Res.string.settings_contrast),
                                    subtitle = when (preferences.contrastLevel) {
                                        ContrastLevel.DEFAULT -> stringResource(Res.string.settings_contrast_standard)
                                        ContrastLevel.MEDIUM -> stringResource(Res.string.settings_contrast_medium)
                                        ContrastLevel.HIGH -> stringResource(Res.string.settings_contrast_high)
                                    },
                                    trailingText = preferences.contrastLevel.name,
                                    highlighted = highlightSettingId == "contrast",
                                    onClick = {
                                        val next = when (preferences.contrastLevel) {
                                            ContrastLevel.DEFAULT -> ContrastLevel.MEDIUM
                                            ContrastLevel.MEDIUM -> ContrastLevel.HIGH
                                            ContrastLevel.HIGH -> ContrastLevel.DEFAULT
                                        }
                                        viewModel.edit { it.appearance.setContrastLevel(next) }
                                    },
                                )
                            }
                            "library_view_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.LayoutGrid,
                                    title = stringResource(Res.string.settings_library_view_mode),
                                    subtitle = when (preferences.libraryViewMode) {
                                        LibraryViewMode.GRID -> stringResource(Res.string.settings_library_view_grid)
                                        LibraryViewMode.LIST -> stringResource(Res.string.settings_library_view_list)
                                        LibraryViewMode.THUMB -> stringResource(Res.string.settings_library_view_thumb)
                                        LibraryViewMode.MASONRY -> stringResource(Res.string.settings_library_view_masonry)
                                    },
                                    trailingText = preferences.libraryViewMode.name,
                                    highlighted = highlightSettingId == "library_view_mode",
                                    onClick = {
                                        viewModel.edit { it.library.setLibraryViewMode(preferences.libraryViewMode.next) }
                                    },
                                )
                            }
                            "home_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = stringResource(Res.string.settings_home_mode),
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) stringResource(Res.string.settings_home_mode_video) else stringResource(Res.string.settings_home_mode_music),
                                    trailingText = preferences.homeMode.name,
                                    highlighted = highlightSettingId == "home_mode",
                                    onClick = {
                                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                        viewModel.edit { it.homeDiscovery.setHomeMode(next) }
                                    },
                                )
                            }
                            "hero_section" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = stringResource(Res.string.settings_show_hero_section),
                                    subtitle = if (preferences.homeHeroEnabled) stringResource(Res.string.settings_show_hero_on) else stringResource(Res.string.settings_show_hero_off),
                                    checked = preferences.homeHeroEnabled,
                                    highlighted = highlightSettingId == "hero_section",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroEnabled(it) } },
                                )
                            }
                            "home_backdrop" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Background,
                                    title = stringResource(Res.string.settings_home_backdrop),
                                    subtitle = if (preferences.homeBackdropEnabled) stringResource(Res.string.settings_home_backdrop_on) else stringResource(Res.string.settings_home_backdrop_off),
                                    checked = preferences.homeBackdropEnabled,
                                    highlighted = highlightSettingId == "home_backdrop",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeBackdropEnabled(it) } },
                                )
                            }
                            "clock_home" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Clock,
                                    title = stringResource(Res.string.settings_show_clock_home),
                                    subtitle = if (preferences.showClockOnHome) stringResource(Res.string.settings_show_clock_on) else stringResource(Res.string.settings_show_clock_off),
                                    checked = preferences.showClockOnHome,
                                    highlighted = highlightSettingId == "clock_home",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowClockOnHome(it) } },
                                )
                            }
                            "hide_top_header" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.ArrowBarToDown,
                                    title = stringResource(Res.string.settings_hide_top_header_on_scroll),
                                    subtitle = if (preferences.hideTopHeaderOnScroll) stringResource(Res.string.settings_hide_top_header_on_scroll_on) else stringResource(Res.string.settings_hide_top_header_on_scroll_off),
                                    checked = preferences.hideTopHeaderOnScroll,
                                    highlighted = highlightSettingId == "hide_top_header",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideTopHeaderOnScroll(it) } },
                                )
                            }
                            "settings_in_home_search" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = stringResource(Res.string.settings_show_settings_in_home_search),
                                    subtitle = if (preferences.showSettingsInHomeSearch) stringResource(Res.string.settings_show_settings_in_home_search_on) else stringResource(Res.string.settings_show_settings_in_home_search_off),
                                    checked = preferences.showSettingsInHomeSearch,
                                    highlighted = highlightSettingId == "settings_in_home_search",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowSettingsInHomeSearch(it) } },
                                )
                            }
                            "continue_watching_click" -> {
                                val cwTitle = stringResource(Res.string.settings_continue_watching_tap)
                                SettingListItem(
                                    icon = Tabler.Outline.PlayerPlay,
                                    title = cwTitle,
                                    subtitle = stringResource(Res.string.settings_continue_watching_tap_subtitle),
                                    trailingText = preferences.continueWatchingClickBehavior.displayName,
                                    highlighted = highlightSettingId == "continue_watching_click",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = cwTitle,
                                            items = com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.continueWatchingClickBehavior },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setContinueWatchingClickBehavior(it) } },
                                        )
                                    },
                                )
                            }
                            "unhide_cw" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = stringResource(Res.string.settings_unhide_continue_watching),
                                    subtitle = stringResource(Res.string.settings_unhide_continue_watching_subtitle, preferences.hiddenCwItemIds.size),
                                    highlighted = highlightSettingId == "unhide_cw",
                                    onClick = { viewModel.edit { it.homeDiscovery.unhideAllCwItems() } },
                                )
                            }
                            "merge_continue_next_up" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = stringResource(Res.string.settings_merge_continue_next_up),
                                    subtitle = if (preferences.mergeContinueWatchingAndNextUp) stringResource(Res.string.settings_merge_continue_next_up_on) else stringResource(Res.string.settings_merge_continue_next_up_off),
                                    checked = preferences.mergeContinueWatchingAndNextUp,
                                    highlighted = highlightSettingId == "merge_continue_next_up",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setMergeContinueWatchingAndNextUp(it) } },
                                )
                            }
                            "next_up_max_days" -> {
                                val nextUpTitle = stringResource(Res.string.settings_next_up_time_window)
                                val unlimitedLabel = stringResource(Res.string.settings_unlimited)
                                val xDaysFormat = stringResource(Res.string.settings_x_days)
                                val dayLabels = mapOf(
                                    0 to unlimitedLabel,
                                    7 to xDaysFormat.format(7),
                                    14 to xDaysFormat.format(14),
                                    30 to xDaysFormat.format(30),
                                    60 to xDaysFormat.format(60),
                                    90 to xDaysFormat.format(90),
                                )
                                SettingListItem(
                                    icon = Tabler.Outline.CalendarTime,
                                    title = nextUpTitle,
                                    subtitle = stringResource(Res.string.settings_next_up_time_window_subtitle),
                                    trailingText = dayLabels[preferences.nextUpMaxDays] ?: xDaysFormat.format(preferences.nextUpMaxDays),
                                    highlighted = highlightSettingId == "next_up_max_days",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = nextUpTitle,
                                            items = listOf(0, 7, 14, 30, 60, 90),
                                            label = { dayLabels[it] ?: xDaysFormat.format(it) },
                                            isSelected = { it == preferences.nextUpMaxDays },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpMaxDays(it) } },
                                        )
                                    },
                                )
                            }
                            "next_up_rewatching" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.History,
                                    title = stringResource(Res.string.settings_rewatching_next_up),
                                    subtitle = if (preferences.nextUpRewatching) stringResource(Res.string.settings_rewatching_next_up_on) else stringResource(Res.string.settings_rewatching_next_up_off),
                                    checked = preferences.nextUpRewatching,
                                    highlighted = highlightSettingId == "next_up_rewatching",
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpRewatching(it) } },
                                )
                            }
                            "theme_music" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Music,
                                    title = stringResource(Res.string.settings_backdrop_theme_music),
                                    subtitle = if (preferences.backdropThemeMusicEnabled) stringResource(Res.string.settings_backdrop_theme_music_on) else stringResource(Res.string.settings_backdrop_theme_music_off),
                                    checked = preferences.backdropThemeMusicEnabled,
                                    highlighted = highlightSettingId == "theme_music",
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBackdropThemeMusicEnabled(it) } },
                                )
                            }
                            "nav_labels" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = stringResource(Res.string.settings_show_nav_labels),
                                    subtitle = if (preferences.navBarShowLabels) stringResource(Res.string.settings_nav_labels_on) else stringResource(Res.string.settings_nav_labels_off),
                                    checked = preferences.navBarShowLabels,
                                    highlighted = highlightSettingId == "nav_labels",
                                    onCheckedChange = { viewModel.edit { scope -> scope.navigation.setNavBarShowLabels(it) } },
                                )
                            }
                            "date_format" -> {
                                val dateFormatTitle = stringResource(Res.string.settings_date_format)
                                SettingListItem(
                                    icon = Tabler.Outline.Calendar,
                                    title = dateFormatTitle,
                                    subtitle = stringResource(Res.string.settings_date_format_subtitle),
                                    trailingText = preferences.dateFormatPreference.displayName,
                                    highlighted = highlightSettingId == "date_format",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = dateFormatTitle,
                                            items = DateFormatPreference.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.dateFormatPreference },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setDateFormatPreference(it) } },
                                        )
                                    },
                                )
                            }
                            "font_scale" -> {
                                val fontSizeTitle = stringResource(Res.string.settings_font_size_app)
                                SettingListItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = fontSizeTitle,
                                    subtitle = stringResource(Res.string.settings_font_size_app_subtitle),
                                    trailingText = preferences.appFontScale.displayName,
                                    highlighted = highlightSettingId == "font_scale",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = fontSizeTitle,
                                            items = AppFontScale.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.appFontScale },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setAppFontScale(it) } },
                                        )
                                    },
                                )
                            }
                            "scheduled_start" -> {
                                val nightStartsTitle = stringResource(Res.string.settings_night_starts_at)
                                SettingListItem(
                                    icon = Tabler.Outline.Sun,
                                    title = nightStartsTitle,
                                    subtitle = stringResource(Res.string.settings_night_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeStartHour}:00",
                                    highlighted = highlightSettingId == "scheduled_start",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = nightStartsTitle,
                                            items = (0..23).toList(),
                                            label = { "$it:00" },
                                            isSelected = { it == preferences.scheduledThemeStartHour },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setScheduledThemeStartHour(it) } },
                                        )
                                    },
                                )
                            }
                            "scheduled_end" -> {
                                val morningStartsTitle = stringResource(Res.string.settings_morning_starts_at)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = morningStartsTitle,
                                    subtitle = stringResource(Res.string.settings_morning_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeEndHour}:00",
                                    highlighted = highlightSettingId == "scheduled_end",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = morningStartsTitle,
                                            items = (0..23).toList(),
                                            label = { "$it:00" },
                                            isSelected = { it == preferences.scheduledThemeEndHour },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setScheduledThemeEndHour(it) } },
                                        )
                                    },
                                )
                            }
                            "color_blind_mode" -> {
                                val colorBlindTitle = stringResource(Res.string.settings_color_blind_mode)
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = colorBlindTitle,
                                    subtitle = stringResource(Res.string.settings_color_blind_mode_subtitle),
                                    trailingText = preferences.colorBlindMode.displayName,
                                    highlighted = highlightSettingId == "color_blind_mode",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = colorBlindTitle,
                                            items = com.raulshma.jellyplay.core.model.ColorBlindMode.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.colorBlindMode },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setColorBlindMode(it) } },
                                        )
                                    },
                                )
                            }
                            "hand_mode" -> {
                                val handednessTitle = stringResource(Res.string.settings_handedness)
                                SettingListItem(
                                    icon = Tabler.Outline.HandClick,
                                    title = handednessTitle,
                                    subtitle = stringResource(Res.string.settings_handedness_subtitle),
                                    trailingText = preferences.handMode.displayName,
                                    highlighted = highlightSettingId == "hand_mode",
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = handednessTitle,
                                            items = com.raulshma.jellyplay.core.model.HandMode.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.handMode },
                                            onSelect = { viewModel.edit { scope -> scope.appearance.setHandMode(it) } },
                                        )
                                    },
                                )
                            }
                        }
                    }
                    }
                }
            }

            // Floating navigation bar customization (enable/disable items, reorder,
            // hide-on-scroll).
            item {
                val navPrefs by viewModel.navigationCustomizationPreferences.collectAsStateWithLifecycle()
                NavigationCustomizationGroup(
                    preferences = navPrefs,
                    viewModel = viewModel,
                )
            }

            // Library & Cards: commonly-used display toggles, shown regardless of Advanced mode.
            item {
                SettingsGroup(
                    icon = Tabler.Outline.LayoutGrid,
                    title = stringResource(Res.string.settings_library_cards),
                    summary = {
                        val unwatched = if (preferences.showUnwatchedBadge) stringResource(Res.string.settings_summary_unwatched_badges) else null
                        val checkmarks = if (preferences.showWatchedCheckmark) stringResource(Res.string.settings_summary_watched_checkmarks) else null
                        val hideWatched = if (preferences.hideWatchedItems) stringResource(Res.string.settings_summary_hide_watched) else null
                        val hideThumbnails = if (preferences.hideEpisodeThumbnails) stringResource(Res.string.settings_summary_hide_thumbnails) else null
                        val skipSpecials = if (preferences.skipSpecials) stringResource(Res.string.settings_summary_skip_specials) else null
                        val shareOpt = if (preferences.showShareMediaOption) stringResource(Res.string.settings_summary_share_button) else null
                        val ratingsOpt = if (preferences.showExternalRatings) stringResource(Res.string.settings_summary_external_ratings) else null
                        listOfNotNull(unwatched, checkmarks, hideWatched, hideThumbnails, skipSpecials, shareOpt, ratingsOpt).joinToString(", ").ifEmpty { stringResource(Res.string.settings_summary_all_hidden) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in APPEARANCE_LIBRARY_GROUP_IDS,
                ) {
                    val cardTotal = 8
                    var cardIdx = 0

                    SettingToggleItem(
                        icon = Tabler.Outline.Folder,
                        title = stringResource(Res.string.settings_show_unwatched_badge),
                        subtitle = stringResource(Res.string.settings_show_unwatched_badge_subtitle),
                        checked = preferences.showUnwatchedBadge,
                        highlighted = highlightSettingId == "show_unwatched_badge",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowUnwatchedBadge(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = stringResource(Res.string.settings_show_watched_checkmark),
                        subtitle = stringResource(Res.string.settings_show_watched_checkmark_subtitle),
                        checked = preferences.showWatchedCheckmark,
                        highlighted = highlightSettingId == "show_watched_checkmark",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowWatchedCheckmark(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = stringResource(Res.string.settings_hide_watched_items),
                        subtitle = stringResource(Res.string.settings_hide_watched_items_subtitle),
                        checked = preferences.hideWatchedItems,
                        highlighted = highlightSettingId == "hide_watched_items",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideWatchedItems(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PhotoOff,
                        title = stringResource(Res.string.settings_hide_episode_thumbnails),
                        subtitle = stringResource(Res.string.settings_hide_episode_thumbnails_subtitle),
                        checked = preferences.hideEpisodeThumbnails,
                        highlighted = highlightSettingId == "hide_episode_thumbnails",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setHideEpisodeThumbnails(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.List,
                        title = stringResource(Res.string.settings_compact_episode_list),
                        subtitle = stringResource(Res.string.settings_compact_episode_list_subtitle),
                        checked = preferences.compactEpisodeList,
                        highlighted = highlightSettingId == "compact_episode_list",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setCompactEpisodeList(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = stringResource(Res.string.settings_confirm_library_reset),
                        subtitle = stringResource(Res.string.settings_confirm_library_reset_subtitle),
                        checked = preferences.confirmLibraryReset,
                        highlighted = highlightSettingId == "confirm_library_reset",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setConfirmLibraryReset(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = stringResource(Res.string.settings_skip_special_episodes),
                        subtitle = stringResource(Res.string.settings_skip_special_episodes_subtitle),
                        checked = preferences.skipSpecials,
                        highlighted = highlightSettingId == "skip_specials",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setSkipSpecials(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.DeviceMobileVibration,
                        title = stringResource(Res.string.settings_haptic_feedback),
                        subtitle = stringResource(Res.string.settings_haptic_feedback_subtitle),
                        checked = preferences.hapticsEnabled,
                        highlighted = highlightSettingId == "haptics_enabled",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setHapticsEnabled(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Share,
                        title = stringResource(Res.string.settings_show_share_media),
                        subtitle = stringResource(Res.string.settings_show_share_media_subtitle),
                        checked = preferences.showShareMediaOption,
                        highlighted = highlightSettingId == "show_share_media",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.experimental.setShowShareMediaOption(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = stringResource(Res.string.settings_hide_search_history),
                        subtitle = stringResource(Res.string.settings_hide_search_history_subtitle),
                        checked = preferences.hideSearchHistory,
                        highlighted = highlightSettingId == "hide_search_history",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.experimental.setHideSearchHistory(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Star,
                        title = stringResource(Res.string.settings_show_external_ratings),
                        subtitle = stringResource(Res.string.settings_show_external_ratings_subtitle),
                        checked = preferences.showExternalRatings,
                        highlighted = highlightSettingId == "show_external_ratings",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowExternalRatings(it) } },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Home,
                    title = stringResource(Res.string.settings_home_screen_layout),
                    summary = {
                        val enabled = preferences.enabledHomeSectionTypes
                        stringResource(Res.string.settings_home_sections_visible, enabled.size, HomeSectionType.CONFIGURABLE.size)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in homeLayoutGroup,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Pinned,
                        title = stringResource(Res.string.settings_pinned_home_sections),
                        subtitle = stringResource(Res.string.settings_pinned_home_sections_brief),
                        trailingText = if (preferences.pinnedHomeSections.isEmpty()) "" else "${preferences.pinnedHomeSections.size}",
                        highlighted = highlightSettingId == "pinned_home_sections",
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.PinnedHomeSections(if (highlightSettingId == "pinned_home_sections") "pinned_add" else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Bookmarks,
                        title = stringResource(Res.string.settings_home_layout_presets),
                        subtitle = stringResource(Res.string.settings_home_layout_presets_brief),
                        trailingText = if (preferences.homeLayoutPresets.isEmpty()) "" else "${preferences.homeLayoutPresets.size}",
                        highlighted = highlightSettingId == "home_layout_presets",
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.HomeLayoutPresets(if (highlightSettingId == "home_layout_presets") "preset_list" else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Folders,
                        title = stringResource(Res.string.settings_configure_libraries),
                        subtitle = stringResource(Res.string.settings_configure_libraries_desc),
                        trailingText = "",
                        highlighted = highlightSettingId == "configure_libraries",
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.LibraryHomeSections(if (highlightSettingId == "configure_libraries") "configure_libraries" else null)) },
                    )

                    val homeSections = rememberReorderableOrderedList(
                        storedOrder = preferences.homeSectionOrder,
                        onPersist = { order -> viewModel.edit { it.homeDiscovery.setHomeSectionOrder(order) } },
                    )

                    homeSections.items.forEachIndexed { index, sectionType ->
                        val enabled = sectionType in preferences.enabledHomeSectionTypes
                        SettingReorderableToggleItem(
                            icon = homeSectionIcon(sectionType),
                            title = sectionType.displayName,
                            subtitle = sectionType.description,
                            checked = enabled,
                            index = index,
                            count = homeSections.items.size,
                            modifier = Modifier.onSizeChanged { homeSections.recordHeight(sectionType, it.height) },
                            onCheckedChange = { checked ->
                                viewModel.edit { it.homeDiscovery.setSectionVisible(sectionType, checked) }
                            },
                            onDrag = { delta -> homeSections.onDrag(sectionType, delta) },
                            onDragStart = { homeSections.onDragStart(sectionType) },
                            onDragEnd = homeSections::onDragEnd,
                        )
                    }
                }
            }

            // Remaining groups are expert-level; keep them behind the Advanced gate.
            if (showAdvanced) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(Res.string.settings_performance),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.performanceMode) parts.add(stringResource(Res.string.settings_performance_mode))
                        if (preferences.reduceMotionEnabled) parts.add(stringResource(Res.string.settings_reduced_motion))
                        parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_standard_experience) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PERFORMANCE_GROUP_IDS,
                ) {
                    val perfTotal = 2
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(Res.string.settings_performance_mode),
                        subtitle = stringResource(Res.string.settings_performance_mode_subtitle),
                        checked = preferences.performanceMode,
                        highlighted = highlightSettingId == "performance_mode",
                        index = 0, count = perfTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setPerformanceMode(it) } },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Activity,
                        title = stringResource(Res.string.settings_reduce_motion),
                        subtitle = stringResource(Res.string.settings_reduce_motion_subtitle),
                        checked = preferences.reduceMotionEnabled,
                        highlighted = highlightSettingId == "reduce_motion",
                        index = 1, count = perfTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setReduceMotionEnabled(it) } },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Eye,
                    title = stringResource(Res.string.settings_comfort_eye_care),
                    summary = {
                        if (preferences.blueLightFilterEnabled) {
                            stringResource(Res.string.settings_blue_light_filter_summary, (preferences.blueLightFilterStrength * 100).toInt())
                        } else {
                            stringResource(Res.string.settings_off)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in BLUE_LIGHT_GROUP_IDS,
                ) {
                    val eyeCareTotal = 2
                    SettingToggleItem(
                        icon = Tabler.Outline.Moon,
                        title = stringResource(Res.string.settings_blue_light_filter),
                        subtitle = stringResource(Res.string.settings_blue_light_filter_subtitle),
                        checked = preferences.blueLightFilterEnabled,
                        highlighted = highlightSettingId == "blue_light_filter",
                        index = 0, count = eyeCareTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBlueLightFilterEnabled(it) } },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Adjustments,
                        title = stringResource(Res.string.settings_blue_light_filter_strength),
                        subtitle = stringResource(Res.string.settings_blue_light_filter_strength_subtitle),
                        trailingText = "${(preferences.blueLightFilterStrength * 100).toInt()}%",
                        highlighted = highlightSettingId == "blue_light_strength",
                        index = 1, count = eyeCareTotal,
                        onClick = { showBlueLightStrengthSheet = true },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Mail,
                    title = stringResource(Res.string.settings_newsletter_config),
                    summary = {
                        val enabled = preferences.enabledNewsletterSections
                        if (preferences.newsletterEnabled) {
                            stringResource(Res.string.settings_newsletter_sections_enabled, enabled.size, NewsletterSectionType.entries.size)
                        } else {
                            stringResource(Res.string.settings_disabled)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in NEWSLETTER_GROUP_IDS,
                ) {
                    val newsletterSections = rememberReorderableOrderedList(
                        storedOrder = preferences.newsletterSectionOrder,
                        onPersist = { order -> viewModel.edit { it.notification.setNewsletterSectionOrder(order) } },
                    )

                    SettingsItemList(total = newsletterSections.items.size + 2) {

                    SettingToggleItem(
                        icon = Tabler.Outline.Mail,
                        title = stringResource(Res.string.settings_enable_newsletter),
                        subtitle = stringResource(Res.string.settings_enable_newsletter_subtitle),
                        checked = preferences.newsletterEnabled,
                                    highlighted = highlightSettingId == "newsletter_enabled",
                                    onCheckedChange = { viewModel.edit { scope -> scope.notification.setNewsletterEnabled(it) } }
                    )

                    val daysOfWeek = listOf(
                        java.util.Calendar.MONDAY to stringResource(Res.string.settings_day_monday),
                        java.util.Calendar.TUESDAY to stringResource(Res.string.settings_day_tuesday),
                        java.util.Calendar.WEDNESDAY to stringResource(Res.string.settings_day_wednesday),
                        java.util.Calendar.THURSDAY to stringResource(Res.string.settings_day_thursday),
                        java.util.Calendar.FRIDAY to stringResource(Res.string.settings_day_friday),
                        java.util.Calendar.SATURDAY to stringResource(Res.string.settings_day_saturday),
                        java.util.Calendar.SUNDAY to stringResource(Res.string.settings_day_sunday),
                    )
                    val dayLabel = daysOfWeek.find { it.first == preferences.newsletterDayOfWeek }?.second ?: stringResource(Res.string.settings_day_saturday)

                    SettingListItem(
                        icon = Tabler.Outline.Calendar,
                        title = stringResource(Res.string.settings_newsletter_delivery_day),
                        subtitle = stringResource(Res.string.settings_newsletter_delivery_day_subtitle),
                        trailingText = dayLabel,
                        highlighted = highlightSettingId == "newsletter_delivery_day",
                        onClick = {
                            val currentIdx = daysOfWeek.indexOfFirst { it.first == preferences.newsletterDayOfWeek }
                            val nextIdx = (currentIdx + 1) % daysOfWeek.size
                            viewModel.edit { it.notification.setNewsletterDayOfWeek(daysOfWeek[nextIdx].first) }
                        }
                    )

                    if (preferences.newsletterEnabled) {
                        newsletterSections.items.forEachIndexed { index, sectionType ->
                            val enabled = sectionType in preferences.enabledNewsletterSections

                            SettingReorderableToggleItem(
                                icon = newsletterSectionIcon(sectionType),
                                title = stringResource(sectionType.labelRes),
                                subtitle = stringResource(sectionType.descriptionRes),
                                checked = enabled,
                                index = index + 2,
                                count = newsletterSections.items.size + 2,
                                modifier = Modifier.onSizeChanged { newsletterSections.recordHeight(sectionType, it.height) },
                                onCheckedChange = { checked ->
                                    val current = preferences.enabledNewsletterSections.toMutableSet()
                                    if (checked) current.add(sectionType) else current.remove(sectionType)
                                    viewModel.edit { it.notification.setEnabledNewsletterSections(current) }
                                },
                                onDrag = { delta -> newsletterSections.onDrag(sectionType, delta) },
                                onDragStart = { newsletterSections.onDragStart(sectionType) },
                                onDragEnd = newsletterSections::onDragEnd,
                            )
                        }
                    }
                    }
                }
            }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 7,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (showBlueLightStrengthSheet) {
        SettingsSliderSheet(
            title = stringResource(Res.string.settings_blue_light_filter_strength),
            value = preferences.blueLightFilterStrength,
            valueRange = 0.1f..1f,
            steps = 8,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "10%",
            rangeEndLabel = "100%",
            onDismiss = { showBlueLightStrengthSheet = false },
            onConfirm = {
                viewModel.edit { scope -> scope.appearance.setBlueLightFilterStrength(it) }
                showBlueLightStrengthSheet = false
            },
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_reset_appearance_title),
            message = stringResource(Res.string.settings_reset_appearance_message),
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                viewModel.resetCategory(PreferenceResetCategory.APPEARANCE)
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
            dismissText = stringResource(Res.string.settings_cancel),
        )
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
