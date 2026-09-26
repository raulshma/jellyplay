package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.accentOptions
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.settingsGroupContainerColor
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_high
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_standard
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_moved_info
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_grid
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_masonry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_thumb
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_overridden_variant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
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
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val THEME_HIGHLIGHT_IDS = setOf(AppearanceSettingsIds.THEME_MODE, AppearanceSettingsIds.THEME_SCHEDULER)

/**
 * The declared appearance screen groups in LazyColumn order — the derivation
 * source the deep-link scroll resolver consumes (see HighlightScroll.kt), so
 * the scroll target can never drift from the UI. Indices 0-2 always render;
 * 3-5 (performance, eye care, newsletter) only compose when advanced settings
 * are shown. (The home display / Next Up / home-layout rows moved to
 * HomeSettingsScreen.)
 */
private val appearanceScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.appearanceTheme.itemIdSet,
    SettingsScreenGroups.appearanceNavigation.itemIdSet,
    SettingsScreenGroups.appearanceLibrary.itemIdSet,
    SettingsScreenGroups.appearancePerformance.itemIdSet,
    SettingsScreenGroups.appearanceEyeCare.itemIdSet,
    SettingsScreenGroups.appearanceNewsletter.itemIdSet,
)

/**
 * [resolveHighlightScrollIndex]'s [rememberHighlightScrollIndex adjustForAdvanced]
 * for this screen's LazyColumn: with advanced hidden the three trailing
 * expert groups don't compose, so their ids cannot scroll (`-1`). Pure (and
 * internal) so the contract test can pin the derivation against it.
 */
internal fun appearanceAdjustForAdvanced(showAdvanced: Boolean): (Int) -> Int =
    if (showAdvanced) {
        { it }
    } else {
        { raw -> if (raw >= 3) -1 else raw }
    }

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

    val scrollState = rememberLazyListState()
    val scrollIndex = rememberHighlightScrollIndex(
        highlightSettingId,
        appearanceScreenGroups,
        appearanceAdjustForAdvanced(showAdvanced),
    )

    HighlightScrollEffect(scrollState, scrollIndex)

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
        CenteredBringIntoView {
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
                            // kotlinx wall-clock read replaces the
                            // JVM-only java.util.Calendar (same hour-of-day
                            // semantics in the system zone).
                            val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
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
                        showAdvanced,
                        isDarkActive,
                        isAndroid12,
                    ) {
                        buildList {
                            val variant = ThemeVariant.fromId(preferences.themeVariant)
                            val isStandard = variant == ThemeVariant.STANDARD
                            add(AppearanceSettingsIds.THEME_MODE)
                            add(AppearanceSettingsIds.THEME_STYLE)
                            if (variant.accentOptions() != null) {
                                add(AppearanceSettingsIds.STYLE_ACCENT)
                            }
                            if (isStandard) {
                                add(AppearanceSettingsIds.ACCENT_COLOR)
                                add(AppearanceSettingsIds.COLOR_STYLE)
                                if (isAndroid12) add(AppearanceSettingsIds.DYNAMIC_THEMING)
                            }
                            if (isDarkActive && variant.allowsOled) add(AppearanceSettingsIds.OLED_MODE)
                            if (showAdvanced) {
                                add(AppearanceSettingsIds.CONTRAST)
                                add(AppearanceSettingsIds.LIBRARY_VIEW_MODE)
                                add(AppearanceSettingsIds.THEME_MUSIC)
                                add(AppearanceSettingsIds.NAV_LABELS)
                                add(AppearanceSettingsIds.DATE_FORMAT)
                                add(AppearanceSettingsIds.FONT_SCALE)
                                add(AppearanceSettingsIds.COLOR_BLIND_MODE)
                                add(AppearanceSettingsIds.HAND_MODE)
                                if (preferences.themeMode == ThemeMode.SCHEDULED) {
                                    add(AppearanceSettingsIds.SCHEDULED_START)
                                    add(AppearanceSettingsIds.SCHEDULED_END)
                                }
                            }
                        }
                    }
                    SettingsItemList(total = appearanceItems.size) {
                    appearanceItems.forEach { item ->
                        when (item) {
                            AppearanceSettingsIds.THEME_MODE -> {
                                val themeTitle = rowTitle(AppearanceSettingsIds.THEME_MODE)
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
                            AppearanceSettingsIds.THEME_STYLE -> {
                                val styleTitle = rowTitle(AppearanceSettingsIds.THEME_STYLE)
                                val styleSubtitle = stringResource(Res.string.settings_theme_style_subtitle)
                                SettingListItem(
                                    icon = Tabler.Outline.Palette,
                                    title = styleTitle,
                                    subtitle = styleSubtitle,
                                    trailingText = ThemeVariant.fromId(preferences.themeVariant).displayName,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.THEME_STYLE,
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
                            AppearanceSettingsIds.STYLE_ACCENT -> {
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
                            AppearanceSettingsIds.ACCENT_COLOR -> {
                                ConsumeSettingsItemIndex()
                                com.raulshma.jellyplay.core.ui.components.AccentColorPicker(
                                    selectedSwatch = preferences.accentColorSwatch,
                                    onSwatchSelected = { viewModel.edit { scope -> scope.appearance.setAccentColorSwatch(it) } },
                                )
                            }
                            AppearanceSettingsIds.COLOR_STYLE -> {
                                ConsumeSettingsItemIndex()
                                com.raulshma.jellyplay.core.ui.components.ColorStylePicker(
                                    selectedStyle = preferences.colorStyle,
                                    onStyleSelected = { viewModel.edit { scope -> scope.appearance.setColorStyle(it) } },
                                )
                            }
                            AppearanceSettingsIds.DYNAMIC_THEMING -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Video,
                                    title = rowTitle(AppearanceSettingsIds.DYNAMIC_THEMING),
                                    subtitle = stringResource(Res.string.settings_dynamic_theming_subtitle),
                                    checked = preferences.dynamicTheming,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.DYNAMIC_THEMING,
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setDynamicTheming(it) } },
                                )
                            }
                            AppearanceSettingsIds.OLED_MODE -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.BrightnessHalf,
                                    title = rowTitle(AppearanceSettingsIds.OLED_MODE),
                                    subtitle = stringResource(Res.string.settings_oled_mode_subtitle),
                                    checked = preferences.oledMode,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.OLED_MODE,
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setOledMode(it) } },
                                )
                            }
                            AppearanceSettingsIds.CONTRAST -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = rowTitle(AppearanceSettingsIds.CONTRAST),
                                    subtitle = when (preferences.contrastLevel) {
                                        ContrastLevel.DEFAULT -> stringResource(Res.string.settings_contrast_standard)
                                        ContrastLevel.MEDIUM -> stringResource(Res.string.settings_contrast_medium)
                                        ContrastLevel.HIGH -> stringResource(Res.string.settings_contrast_high)
                                    },
                                    trailingText = preferences.contrastLevel.name,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.CONTRAST,
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
                            AppearanceSettingsIds.LIBRARY_VIEW_MODE -> {
                                SettingListItem(
                                    icon = Tabler.Outline.LayoutGrid,
                                    title = rowTitle(AppearanceSettingsIds.LIBRARY_VIEW_MODE),
                                    subtitle = when (preferences.libraryViewMode) {
                                        LibraryViewMode.GRID -> stringResource(Res.string.settings_library_view_grid)
                                        LibraryViewMode.LIST -> stringResource(Res.string.settings_library_view_list)
                                        LibraryViewMode.THUMB -> stringResource(Res.string.settings_library_view_thumb)
                                        LibraryViewMode.MASONRY -> stringResource(Res.string.settings_library_view_masonry)
                                    },
                                    trailingText = preferences.libraryViewMode.name,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.LIBRARY_VIEW_MODE,
                                    onClick = {
                                        viewModel.edit { it.library.setLibraryViewMode(preferences.libraryViewMode.next) }
                                    },
                                )
                            }
                            AppearanceSettingsIds.THEME_MUSIC -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Music,
                                    title = rowTitle(AppearanceSettingsIds.THEME_MUSIC),
                                    subtitle = if (preferences.backdropThemeMusicEnabled) stringResource(Res.string.settings_backdrop_theme_music_on) else stringResource(Res.string.settings_backdrop_theme_music_off),
                                    checked = preferences.backdropThemeMusicEnabled,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.THEME_MUSIC,
                                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBackdropThemeMusicEnabled(it) } },
                                )
                            }
                            AppearanceSettingsIds.NAV_LABELS -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = rowTitle(AppearanceSettingsIds.NAV_LABELS),
                                    subtitle = if (preferences.navBarShowLabels) stringResource(Res.string.settings_nav_labels_on) else stringResource(Res.string.settings_nav_labels_off),
                                    checked = preferences.navBarShowLabels,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.NAV_LABELS,
                                    onCheckedChange = { viewModel.edit { scope -> scope.navigation.setNavBarShowLabels(it) } },
                                )
                            }
                            AppearanceSettingsIds.DATE_FORMAT -> {
                                val dateFormatTitle = rowTitle(AppearanceSettingsIds.DATE_FORMAT)
                                SettingListItem(
                                    icon = Tabler.Outline.Calendar,
                                    title = dateFormatTitle,
                                    subtitle = stringResource(Res.string.settings_date_format_subtitle),
                                    trailingText = preferences.dateFormatPreference.displayName,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.DATE_FORMAT,
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
                            AppearanceSettingsIds.FONT_SCALE -> {
                                val fontSizeTitle = rowTitle(AppearanceSettingsIds.FONT_SCALE)
                                SettingListItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = fontSizeTitle,
                                    subtitle = stringResource(Res.string.settings_font_size_app_subtitle),
                                    trailingText = preferences.appFontScale.displayName,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.FONT_SCALE,
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
                            AppearanceSettingsIds.SCHEDULED_START -> {
                                val nightStartsTitle = rowTitle(AppearanceSettingsIds.SCHEDULED_START)
                                SettingListItem(
                                    icon = Tabler.Outline.Sun,
                                    title = nightStartsTitle,
                                    subtitle = stringResource(Res.string.settings_night_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeStartHour}:00",
                                    highlighted = highlightSettingId == AppearanceSettingsIds.SCHEDULED_START,
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
                            AppearanceSettingsIds.SCHEDULED_END -> {
                                val morningStartsTitle = rowTitle(AppearanceSettingsIds.SCHEDULED_END)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = morningStartsTitle,
                                    subtitle = stringResource(Res.string.settings_morning_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeEndHour}:00",
                                    highlighted = highlightSettingId == AppearanceSettingsIds.SCHEDULED_END,
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
                            AppearanceSettingsIds.COLOR_BLIND_MODE -> {
                                val colorBlindTitle = rowTitle(AppearanceSettingsIds.COLOR_BLIND_MODE)
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = colorBlindTitle,
                                    subtitle = stringResource(Res.string.settings_color_blind_mode_subtitle),
                                    trailingText = preferences.colorBlindMode.displayName,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.COLOR_BLIND_MODE,
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
                            AppearanceSettingsIds.HAND_MODE -> {
                                val handednessTitle = rowTitle(AppearanceSettingsIds.HAND_MODE)
                                SettingListItem(
                                    icon = Tabler.Outline.HandClick,
                                    title = handednessTitle,
                                    subtitle = stringResource(Res.string.settings_handedness_subtitle),
                                    trailingText = preferences.handMode.displayName,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.HAND_MODE,
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
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceLibrary.itemIdSet,
                ) {
                    // The declared library rows plus the confirm-library-reset
                    // action row (a screen-local row with no search entry) —
                    // the admission total beside SettingsScreenGroups.
                    SettingsItemList(total = appearanceLibraryScreenRowTotal()) {

                    SettingToggleItem(
                        icon = Tabler.Outline.Folder,
                        title = rowTitle(AppearanceSettingsIds.SHOW_UNWATCHED_BADGE),
                        subtitle = stringResource(Res.string.settings_show_unwatched_badge_subtitle),
                        checked = preferences.showUnwatchedBadge,
                        highlighted = highlightSettingId == AppearanceSettingsIds.SHOW_UNWATCHED_BADGE,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowUnwatchedBadge(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = rowTitle(AppearanceSettingsIds.SHOW_WATCHED_CHECKMARK),
                        subtitle = stringResource(Res.string.settings_show_watched_checkmark_subtitle),
                        checked = preferences.showWatchedCheckmark,
                        highlighted = highlightSettingId == AppearanceSettingsIds.SHOW_WATCHED_CHECKMARK,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowWatchedCheckmark(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = rowTitle(AppearanceSettingsIds.HIDE_WATCHED_ITEMS),
                        subtitle = stringResource(Res.string.settings_hide_watched_items_subtitle),
                        checked = preferences.hideWatchedItems,
                        highlighted = highlightSettingId == AppearanceSettingsIds.HIDE_WATCHED_ITEMS,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideWatchedItems(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PhotoOff,
                        title = rowTitle(AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS),
                        subtitle = stringResource(Res.string.settings_hide_episode_thumbnails_subtitle),
                        checked = preferences.hideEpisodeThumbnails,
                        highlighted = highlightSettingId == AppearanceSettingsIds.HIDE_EPISODE_THUMBNAILS,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setHideEpisodeThumbnails(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.List,
                        title = rowTitle(AppearanceSettingsIds.COMPACT_EPISODE_LIST),
                        subtitle = stringResource(Res.string.settings_compact_episode_list_subtitle),
                        checked = preferences.compactEpisodeList,
                        highlighted = highlightSettingId == AppearanceSettingsIds.COMPACT_EPISODE_LIST,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setCompactEpisodeList(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.AlertTriangle,
                        title = stringResource(Res.string.settings_confirm_library_reset),
                        subtitle = stringResource(Res.string.settings_confirm_library_reset_subtitle),
                        checked = preferences.confirmLibraryReset,
                        highlighted = highlightSettingId == "confirm_library_reset",
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setConfirmLibraryReset(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = rowTitle(AppearanceSettingsIds.SKIP_SPECIALS),
                        subtitle = stringResource(Res.string.settings_skip_special_episodes_subtitle),
                        checked = preferences.skipSpecials,
                        highlighted = highlightSettingId == AppearanceSettingsIds.SKIP_SPECIALS,
                        onCheckedChange = { viewModel.edit { scope -> scope.library.setSkipSpecials(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.DeviceMobileVibration,
                        title = rowTitle(AppearanceSettingsIds.HAPTICS_ENABLED),
                        subtitle = stringResource(Res.string.settings_haptic_feedback_subtitle),
                        checked = preferences.hapticsEnabled,
                        highlighted = highlightSettingId == AppearanceSettingsIds.HAPTICS_ENABLED,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setHapticsEnabled(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Share,
                        title = rowTitle(AppearanceSettingsIds.SHOW_SHARE_MEDIA),
                        subtitle = stringResource(Res.string.settings_show_share_media_subtitle),
                        checked = preferences.showShareMediaOption,
                        highlighted = highlightSettingId == AppearanceSettingsIds.SHOW_SHARE_MEDIA,
                        onCheckedChange = { viewModel.edit { scope -> scope.experimental.setShowShareMediaOption(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = rowTitle(AppearanceSettingsIds.HIDE_SEARCH_HISTORY),
                        subtitle = stringResource(Res.string.settings_hide_search_history_subtitle),
                        checked = preferences.hideSearchHistory,
                        highlighted = highlightSettingId == AppearanceSettingsIds.HIDE_SEARCH_HISTORY,
                        onCheckedChange = { viewModel.edit { scope -> scope.experimental.setHideSearchHistory(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Star,
                        title = rowTitle(AppearanceSettingsIds.SHOW_EXTERNAL_RATINGS),
                        subtitle = stringResource(Res.string.settings_show_external_ratings_subtitle),
                        checked = preferences.showExternalRatings,
                        highlighted = highlightSettingId == AppearanceSettingsIds.SHOW_EXTERNAL_RATINGS,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowExternalRatings(it) } },
                    )
                    }
                }
            }

            // Pointer for the moved home settings: the former Home Screen
            // Layout group (and the home display rows) now live on the
            // dedicated Home Screen settings page.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth16)
                        .background(settingsGroupContainerColor())
                        .focusIndicator()
                        .clickable { navActions.onNavigate(Route.HomeSettings()) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Tabler.Outline.InfoCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(Res.string.settings_home_moved_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Tabler.Outline.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
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
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearancePerformance.itemIdSet,
                ) {
                    val perfTotal = SettingsScreenGroups.appearancePerformance.items.size
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = rowTitle(AppearanceSettingsIds.PERFORMANCE_MODE),
                        subtitle = stringResource(Res.string.settings_performance_mode_subtitle),
                        checked = preferences.performanceMode,
                        highlighted = highlightSettingId == AppearanceSettingsIds.PERFORMANCE_MODE,
                        index = 0, count = perfTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setPerformanceMode(it) } },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Activity,
                        title = rowTitle(AppearanceSettingsIds.REDUCE_MOTION),
                        subtitle = stringResource(Res.string.settings_reduce_motion_subtitle),
                        checked = preferences.reduceMotionEnabled,
                        highlighted = highlightSettingId == AppearanceSettingsIds.REDUCE_MOTION,
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
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceEyeCare.itemIdSet,
                ) {
                    val eyeCareTotal = SettingsScreenGroups.appearanceEyeCare.items.size
                    SettingToggleItem(
                        icon = Tabler.Outline.Moon,
                        title = rowTitle(AppearanceSettingsIds.BLUE_LIGHT_FILTER),
                        subtitle = stringResource(Res.string.settings_blue_light_filter_subtitle),
                        checked = preferences.blueLightFilterEnabled,
                        highlighted = highlightSettingId == AppearanceSettingsIds.BLUE_LIGHT_FILTER,
                        index = 0, count = eyeCareTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBlueLightFilterEnabled(it) } },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Adjustments,
                        title = rowTitle(AppearanceSettingsIds.BLUE_LIGHT_STRENGTH),
                        subtitle = stringResource(Res.string.settings_blue_light_filter_strength_subtitle),
                        trailingText = "${(preferences.blueLightFilterStrength * 100).toInt()}%",
                        highlighted = highlightSettingId == AppearanceSettingsIds.BLUE_LIGHT_STRENGTH,
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
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceNewsletter.itemIdSet,
                ) {
                    val newsletterSections = rememberReorderableOrderedList(
                        storedOrder = preferences.newsletterSectionOrder,
                        onPersist = { order -> viewModel.edit { it.notification.setNewsletterSectionOrder(order) } },
                    )

                    // The two static declared rows (enable + delivery day) plus
                    // the runtime-reorderable section rows (the declared
                    // newsletter_sections id renders as those rows).
                    SettingsItemList(
                        total = newsletterSections.items.size +
                            SettingsScreenGroups.appearanceNewsletter.items.count { it.id != "newsletter_sections" },
                    ) {

                    SettingToggleItem(
                        icon = Tabler.Outline.Mail,
                        title = rowTitle(AppearanceSettingsIds.NEWSLETTER_ENABLED),
                        subtitle = stringResource(Res.string.settings_enable_newsletter_subtitle),
                        checked = preferences.newsletterEnabled,
                                    highlighted = highlightSettingId == AppearanceSettingsIds.NEWSLETTER_ENABLED,
                                    onCheckedChange = { viewModel.edit { scope -> scope.notification.setNewsletterEnabled(it) } }
                    )

                    // The persisted newsletterDayOfWeek values are the legacy
                    // java.util.Calendar day numbers (SUNDAY=1 … SATURDAY=7) —
                    // the literals preserve that wire format now that the JVM
                    // type is gone.
                    val daysOfWeek = listOf(
                        CALENDAR_MONDAY to stringResource(Res.string.settings_day_monday),
                        CALENDAR_TUESDAY to stringResource(Res.string.settings_day_tuesday),
                        CALENDAR_WEDNESDAY to stringResource(Res.string.settings_day_wednesday),
                        CALENDAR_THURSDAY to stringResource(Res.string.settings_day_thursday),
                        CALENDAR_FRIDAY to stringResource(Res.string.settings_day_friday),
                        CALENDAR_SATURDAY to stringResource(Res.string.settings_day_saturday),
                        CALENDAR_SUNDAY to stringResource(Res.string.settings_day_sunday),
                    )
                    val dayLabel = daysOfWeek.find { it.first == preferences.newsletterDayOfWeek }?.second ?: stringResource(Res.string.settings_day_saturday)

                    SettingListItem(
                        icon = Tabler.Outline.Calendar,
                        title = rowTitle(AppearanceSettingsIds.NEWSLETTER_DELIVERY_DAY),
                        subtitle = stringResource(Res.string.settings_newsletter_delivery_day_subtitle),
                        trailingText = dayLabel,
                        highlighted = highlightSettingId == AppearanceSettingsIds.NEWSLETTER_DELIVERY_DAY,
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
                    // Eight always-on advanced theme rows plus the two rows in
                    // each of the three advanced-only groups (performance, eye
                    // care, newsletter) are hidden while Advanced is off.
                    HiddenSettingsHint(
                        hiddenCount = 14,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
        }
    }

    if (showBlueLightStrengthSheet) {
        SettingsSliderSheet(
            title = rowTitle(AppearanceSettingsIds.BLUE_LIGHT_STRENGTH),
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

// Legacy java.util.Calendar day-of-week numbers (the persisted
// `newsletterDayOfWeek` vocabulary): SUNDAY=1, MONDAY=2 … SATURDAY=7.
private const val CALENDAR_SUNDAY = 1
private const val CALENDAR_MONDAY = 2
private const val CALENDAR_TUESDAY = 3
private const val CALENDAR_WEDNESDAY = 4
private const val CALENDAR_THURSDAY = 5
private const val CALENDAR_FRIDAY = 6
private const val CALENDAR_SATURDAY = 7
