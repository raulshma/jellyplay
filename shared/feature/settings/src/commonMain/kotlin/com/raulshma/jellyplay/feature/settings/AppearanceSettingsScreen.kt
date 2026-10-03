package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.LayoutMode
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_grid
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_masonry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_layout_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_layout_mode_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_prefer_logos_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_missing_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_missing_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_share_media_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_special_episodes_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_standard_experience
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_style_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_all_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_share_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_skip_specials
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

private val THEME_HIGHLIGHT_IDS = setOf(AppearanceRows.ThemeMode.id, AppearanceRows.ThemeScheduler.id)

/**
 * The declared appearance screen groups in LazyColumn order — the derivation
 * source the deep-link scroll resolver consumes (see HighlightScroll.kt), so
 * the scroll target can never drift from the UI. Indices 0-2 always render;
 * 3-5 (performance, eye care, newsletter) only compose when advanced settings
 * are shown. (The home display / Next Up / home-layout rows AND the
 * home-discovery card-display quartet moved to HomeSettingsScreen.)
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

// ── Named group summaries (the collapsed-header one-liners) ─────────────

@Composable
private fun appearanceThemeSummary(preferences: AppearanceScreenPreferences): String {
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
    return parts.joinToString(", ")
}

@Composable
private fun appearanceLibrarySummary(preferences: AppearanceScreenPreferences): String {
    // The group's remaining rows (the home-discovery card-display quartet
    // moved to HomeSettingsScreen — PS-4, so its terms left the summary).
    val hideThumbnails = if (preferences.hideEpisodeThumbnails) stringResource(Res.string.settings_summary_hide_thumbnails) else null
    val skipSpecials = if (preferences.skipSpecials) stringResource(Res.string.settings_summary_skip_specials) else null
    val shareOpt = if (preferences.showShareMediaOption) stringResource(Res.string.settings_summary_share_button) else null
    return listOfNotNull(hideThumbnails, skipSpecials, shareOpt)
        .joinToString(", ")
        .ifEmpty { stringResource(Res.string.settings_summary_all_hidden) }
}

@Composable
private fun appearancePerformanceSummary(preferences: AppearanceScreenPreferences): String {
    val parts = mutableListOf<String>()
    if (preferences.performanceMode) parts.add(stringResource(Res.string.settings_performance_mode))
    if (preferences.reduceMotionEnabled) parts.add(stringResource(Res.string.settings_reduced_motion))
    return parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_standard_experience) }
}

@Composable
private fun appearanceEyeCareSummary(preferences: AppearanceScreenPreferences): String =
    if (preferences.blueLightFilterEnabled) {
        stringResource(Res.string.settings_blue_light_filter_summary, (preferences.blueLightFilterStrength * 100).toInt())
    } else {
        stringResource(Res.string.settings_off)
    }

@Composable
private fun appearanceNewsletterSummary(preferences: AppearanceScreenPreferences): String {
    val enabled = preferences.enabledNewsletterSections
    return if (preferences.newsletterEnabled) {
        stringResource(Res.string.settings_newsletter_sections_enabled, enabled.size, NewsletterSectionType.entries.size)
    } else {
        stringResource(Res.string.settings_disabled)
    }
}

/**
 * The appearance screen's theme group total, derived from the fused rows'
 * gates: the rows' own [SettingsRow.gate]s (derived into the group by
 * [asRowGroup]) count via [rowTotalFor]; the [RowAdmission.ContentGated] rows
 * the flags vocabulary cannot express (the theme-state rows declared
 * ContentGated on [AppearanceRows]) add their explicit terms — exactly the
 * gates the emission `if`s below read, so the total can never drift from the
 * emitted rows (the [homeDisplayScreenRowTotal] unhide-row precedent). Pure
 * (and internal) so the contract test pins every term.
 */
internal fun appearanceThemeScreenRowTotal(
    variant: ThemeVariant,
    isDarkActive: Boolean,
    isAndroid12: Boolean,
    isTv: Boolean,
    showAdvanced: Boolean,
    themeMode: ThemeMode,
): Int {
    val declared = rowTotalFor(
        SettingsScreenGroups.appearanceTheme,
        RowAdmissionFlags(showAdvanced = showAdvanced, isTv = isTv),
    )
    var content = 0
    if (variant.accentOptions() != null) content += 1 // AppearanceRows.StyleAccent (the per-variant accent picker row)
    if (variant == ThemeVariant.STANDARD) {
        content += 2 // AppearanceRows.AccentColor + ColorStyle
        if (isAndroid12) content += 1 // AppearanceRows.DynamicTheming (the platform capability)
    }
    if (isDarkActive && variant.allowsOled) content += 1 // AppearanceRows.OledMode
    if (showAdvanced && themeMode == ThemeMode.SCHEDULED) content += 2 // AppearanceRows.ScheduledStart/End
    return declared + content
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()

    var showBlueLightStrengthSheet by remember { mutableStateOf(false) }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_title),
        onBack = onBack,
        focusTag = "appearance_init",
        highlightSettingId = highlightSettingId,
        highlightGroups = appearanceScreenGroups,
        adjustForAdvanced = appearanceAdjustForAdvanced(showAdvanced),
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_defaults_cd),
            dialogTitle = stringResource(Res.string.settings_reset_appearance_title),
            dialogMessage = stringResource(Res.string.settings_reset_appearance_message),
            onReset = { viewModel.resetCategory(PreferenceResetCategory.APPEARANCE) },
        ),
        pickerHost = true,
    ) { activePicker ->
        item {
            AppearanceThemeGroup(
                preferences = preferences,
                showAdvanced = showAdvanced,
                isTv = LocalTvMode.current,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                activePicker = activePicker,
            )
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
            AppearanceLibraryGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
            )
        }

        // Remaining groups are expert-level; keep them behind the Advanced gate.
        if (showAdvanced) {
            item {
                AppearancePerformanceGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                )
            }

            item {
                AppearanceEyeCareGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    onShowStrengthSheet = { showBlueLightStrengthSheet = true },
                )
            }

            item {
                AppearanceNewsletterGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                )
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

    if (showBlueLightStrengthSheet) {
        SettingsSliderSheet(
            title = rowTitle(AppearanceRows.BlueLightStrength),
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

}

/** The `appearance.theme` group: theme mode/style/accent, dynamic color, display and player-adjacent appearance rows. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppearanceThemeGroup(
    preferences: AppearanceScreenPreferences,
    showAdvanced: Boolean,
    isTv: Boolean,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.Palette,
        title = stringResource(Res.string.settings_theme),
        summary = { appearanceThemeSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = true,
    ) {
        val isAndroid12 = settingsCapabilities.supportsDynamicColor
        val isDarkActive = when (preferences.themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
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

        // Derived by [appearanceThemeScreenRowTotal] from the theme group
        // declaration (the advanced rows' declared admissions) plus the
        // content-gated rows' explicit terms — the same gates the emission
        // `if`s below read.
        SettingsItemList(
            total = appearanceThemeScreenRowTotal(
                variant = ThemeVariant.fromId(preferences.themeVariant),
                isDarkActive = isDarkActive,
                isAndroid12 = isAndroid12,
                isTv = isTv,
                showAdvanced = showAdvanced,
                themeMode = preferences.themeMode,
            ),
        ) {
            val themeTitle = rowTitle(AppearanceRows.ThemeMode)
            val themeFollowSystem = stringResource(Res.string.settings_theme_follow_system)
            val themeAlwaysLight = stringResource(Res.string.settings_theme_always_light)
            val themeAlwaysDark = stringResource(Res.string.settings_theme_always_dark)
            val themeVariant = ThemeVariant.fromId(preferences.themeVariant)
            // Aurora/Synthwave force dark, so the light/dark choice is inert.
            val isDarkLocked = themeVariant.isDarkLocked
            SettingListItem(
                icon = rowIcon(AppearanceRows.ThemeMode),
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
                        activePicker.value = PickerState.List(
                            title = themeTitle,
                            items = ThemeMode.entries,
                            label = { themeLabels[it] ?: it.name },
                            isSelected = { it == preferences.themeMode },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setThemeMode(it) } },
                        )
                    }
                },
            )
            val styleTitle = rowTitle(AppearanceRows.ThemeStyle)
            val styleSubtitle = stringResource(Res.string.settings_theme_style_subtitle)
            SettingListItem(
                icon = rowIcon(AppearanceRows.ThemeStyle),
                title = styleTitle,
                subtitle = styleSubtitle,
                trailingText = themeVariant.displayName,
                highlighted = highlightSettingId == AppearanceRows.ThemeStyle.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = styleTitle,
                        items = ThemeVariant.entries,
                        label = { it.displayName },
                        isSelected = { it == themeVariant },
                        onSelect = { viewModel.edit { scope -> scope.appearance.setThemeVariant(it.name.lowercase()) } },
                    )
                },
            )
            // Content-gated rows (the rows declared RowAdmission.ContentGated
            // on AppearanceRows): variant/theme-state gates the
            // admission vocabulary does not carry.
            if (themeVariant.accentOptions() != null) {
                ConsumeSettingsItemIndex()
                com.raulshma.jellyplay.core.ui.components.VariantAccentPicker(
                    variant = themeVariant,
                    isDark = effectiveDarkForSwatches,
                    selectedAccent = accentIdFor(themeVariant, preferences) ?: "",
                    onAccentSelected = { accent ->
                        viewModel.edit { it.appearance.setVariantAccent(preferences.themeVariant, accent) }
                    },
                )
            }
            if (themeVariant == ThemeVariant.STANDARD) {
                ConsumeSettingsItemIndex()
                com.raulshma.jellyplay.core.ui.components.AccentColorPicker(
                    selectedSwatch = preferences.accentColorSwatch,
                    onSwatchSelected = { viewModel.edit { scope -> scope.appearance.setAccentColorSwatch(it) } },
                )
                ConsumeSettingsItemIndex()
                com.raulshma.jellyplay.core.ui.components.ColorStylePicker(
                    selectedStyle = preferences.colorStyle,
                    onStyleSelected = { viewModel.edit { scope -> scope.appearance.setColorStyle(it) } },
                )
                if (isAndroid12) {
                    SettingToggleItem(
                        icon = rowIcon(AppearanceRows.DynamicTheming),
                        title = rowTitle(AppearanceRows.DynamicTheming),
                        subtitle = stringResource(Res.string.settings_dynamic_theming_subtitle),
                        checked = preferences.dynamicTheming,
                        highlighted = highlightSettingId == AppearanceRows.DynamicTheming.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.appearance.setDynamicTheming(it) } },
                    )
                }
            }
            if (isDarkActive && themeVariant.allowsOled) {
                SettingToggleItem(
                    icon = rowIcon(AppearanceRows.OledMode),
                    title = rowTitle(AppearanceRows.OledMode),
                    subtitle = stringResource(Res.string.settings_oled_mode_subtitle),
                    checked = preferences.oledMode,
                    highlighted = highlightSettingId == AppearanceRows.OledMode.id,
                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setOledMode(it) } },
                )
            }
            if (showAdvanced) {
                SettingListItem(
                    icon = rowIcon(AppearanceRows.Contrast),
                    title = rowTitle(AppearanceRows.Contrast),
                    subtitle = when (preferences.contrastLevel) {
                        ContrastLevel.DEFAULT -> stringResource(Res.string.settings_contrast_standard)
                        ContrastLevel.MEDIUM -> stringResource(Res.string.settings_contrast_medium)
                        ContrastLevel.HIGH -> stringResource(Res.string.settings_contrast_high)
                    },
                    trailingText = preferences.contrastLevel.name,
                    highlighted = highlightSettingId == AppearanceRows.Contrast.id,
                    onClick = {
                        val next = when (preferences.contrastLevel) {
                            ContrastLevel.DEFAULT -> ContrastLevel.MEDIUM
                            ContrastLevel.MEDIUM -> ContrastLevel.HIGH
                            ContrastLevel.HIGH -> ContrastLevel.DEFAULT
                        }
                        viewModel.edit { it.appearance.setContrastLevel(next) }
                    },
                )
                SettingListItem(
                    icon = rowIcon(AppearanceRows.LibraryViewMode),
                    title = rowTitle(AppearanceRows.LibraryViewMode),
                    subtitle = when (preferences.libraryViewMode) {
                        LibraryViewMode.GRID -> stringResource(Res.string.settings_library_view_grid)
                        LibraryViewMode.LIST -> stringResource(Res.string.settings_library_view_list)
                        LibraryViewMode.THUMB -> stringResource(Res.string.settings_library_view_thumb)
                        LibraryViewMode.MASONRY -> stringResource(Res.string.settings_library_view_masonry)
                    },
                    trailingText = preferences.libraryViewMode.name,
                    highlighted = highlightSettingId == AppearanceRows.LibraryViewMode.id,
                    onClick = {
                        viewModel.edit { it.library.setLibraryViewMode(preferences.libraryViewMode.next) }
                    },
                )
                // Layout override is touch/desktop-only: the TV
                // branch bypasses the width-class fork entirely.
                if (!isTv) {
                    val layoutTitle = rowTitle(AppearanceRows.LayoutMode)
                    SettingListItem(
                        icon = rowIcon(AppearanceRows.LayoutMode),
                        title = layoutTitle,
                        subtitle = stringResource(Res.string.settings_layout_mode_subtitle),
                        trailingText = preferences.layoutMode.displayName,
                        highlighted = highlightSettingId == AppearanceRows.LayoutMode.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = layoutTitle,
                                items = LayoutMode.entries,
                                label = { it.displayName },
                                isSelected = { it == preferences.layoutMode },
                                onSelect = { viewModel.edit { scope -> scope.appearance.setLayoutMode(it) } },
                            )
                        },
                    )
                }
                SettingToggleItem(
                    icon = rowIcon(AppearanceRows.ThemeMusic),
                    title = rowTitle(AppearanceRows.ThemeMusic),
                    subtitle = if (preferences.backdropThemeMusicEnabled) stringResource(Res.string.settings_backdrop_theme_music_on) else stringResource(Res.string.settings_backdrop_theme_music_off),
                    checked = preferences.backdropThemeMusicEnabled,
                    highlighted = highlightSettingId == AppearanceRows.ThemeMusic.id,
                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBackdropThemeMusicEnabled(it) } },
                )
                SettingToggleItem(
                    icon = rowIcon(AppearanceRows.NavLabels),
                    title = rowTitle(AppearanceRows.NavLabels),
                    subtitle = if (preferences.navBarShowLabels) stringResource(Res.string.settings_nav_labels_on) else stringResource(Res.string.settings_nav_labels_off),
                    checked = preferences.navBarShowLabels,
                    highlighted = highlightSettingId == AppearanceRows.NavLabels.id,
                    onCheckedChange = { viewModel.edit { scope -> scope.navigation.setNavBarShowLabels(it) } },
                )
                val dateFormatTitle = rowTitle(AppearanceRows.DateFormat)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.DateFormat),
                    title = dateFormatTitle,
                    subtitle = stringResource(Res.string.settings_date_format_subtitle),
                    trailingText = preferences.dateFormatPreference.displayName,
                    highlighted = highlightSettingId == AppearanceRows.DateFormat.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = dateFormatTitle,
                            items = DateFormatPreference.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.dateFormatPreference },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setDateFormatPreference(it) } },
                        )
                    },
                )
                val fontSizeTitle = rowTitle(AppearanceRows.FontScale)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.FontScale),
                    title = fontSizeTitle,
                    subtitle = stringResource(Res.string.settings_font_size_app_subtitle),
                    trailingText = preferences.appFontScale.displayName,
                    highlighted = highlightSettingId == AppearanceRows.FontScale.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = fontSizeTitle,
                            items = AppFontScale.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.appFontScale },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setAppFontScale(it) } },
                        )
                    },
                )
                val colorBlindTitle = rowTitle(AppearanceRows.ColorBlindMode)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.ColorBlindMode),
                    title = colorBlindTitle,
                    subtitle = stringResource(Res.string.settings_color_blind_mode_subtitle),
                    trailingText = preferences.colorBlindMode.displayName,
                    highlighted = highlightSettingId == AppearanceRows.ColorBlindMode.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = colorBlindTitle,
                            items = com.raulshma.jellyplay.core.model.ColorBlindMode.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.colorBlindMode },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setColorBlindMode(it) } },
                        )
                    },
                )
                val handednessTitle = rowTitle(AppearanceRows.HandMode)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.HandMode),
                    title = handednessTitle,
                    subtitle = stringResource(Res.string.settings_handedness_subtitle),
                    trailingText = preferences.handMode.displayName,
                    highlighted = highlightSettingId == AppearanceRows.HandMode.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = handednessTitle,
                            items = com.raulshma.jellyplay.core.model.HandMode.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.handMode },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setHandMode(it) } },
                        )
                    },
                )
                if (preferences.themeMode == ThemeMode.SCHEDULED) {
                    val nightStartsTitle = rowTitle(AppearanceRows.ScheduledStart)
                    SettingListItem(
                        icon = Tabler.Outline.Sun,
                        title = nightStartsTitle,
                        subtitle = stringResource(Res.string.settings_night_starts_at_subtitle),
                        trailingText = "${preferences.scheduledThemeStartHour}:00",
                        highlighted = highlightSettingId == AppearanceRows.ScheduledStart.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = nightStartsTitle,
                                items = (0..23).toList(),
                                label = { "$it:00" },
                                isSelected = { it == preferences.scheduledThemeStartHour },
                                onSelect = { viewModel.edit { scope -> scope.appearance.setScheduledThemeStartHour(it) } },
                            )
                        },
                    )
                    val morningStartsTitle = rowTitle(AppearanceRows.ScheduledEnd)
                    SettingListItem(
                        icon = Tabler.Outline.Moon,
                        title = morningStartsTitle,
                        subtitle = stringResource(Res.string.settings_morning_starts_at_subtitle),
                        trailingText = "${preferences.scheduledThemeEndHour}:00",
                        highlighted = highlightSettingId == AppearanceRows.ScheduledEnd.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = morningStartsTitle,
                                items = (0..23).toList(),
                                label = { "$it:00" },
                                isSelected = { it == preferences.scheduledThemeEndHour },
                                onSelect = { viewModel.edit { scope -> scope.appearance.setScheduledThemeEndHour(it) } },
                            )
                        },
                    )
                }
            }
        }
    }
}

/** The appearance screen's "Library & Cards" group: commonly-used display toggles, shown regardless of Advanced mode. */
@Composable
private fun AppearanceLibraryGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.LayoutGrid,
        title = stringResource(Res.string.settings_library_cards),
        summary = { appearanceLibrarySummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceLibrary.itemIdSet,
    ) {
        // The declared library rows plus the confirm-library-reset
        // action row (a screen-local row with no search entry) —
        // the admission total beside SettingsScreenGroups.
        SettingsItemList(total = appearanceLibraryScreenRowTotal()) {

        SettingToggleItem(
            icon = Tabler.Outline.PhotoOff,
            title = rowTitle(AppearanceRows.HideEpisodeThumbnails),
            subtitle = stringResource(Res.string.settings_hide_episode_thumbnails_subtitle),
            checked = preferences.hideEpisodeThumbnails,
            highlighted = highlightSettingId == AppearanceRows.HideEpisodeThumbnails.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setHideEpisodeThumbnails(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.List,
            title = rowTitle(AppearanceRows.CompactEpisodeList),
            subtitle = stringResource(Res.string.settings_compact_episode_list_subtitle),
            checked = preferences.compactEpisodeList,
            highlighted = highlightSettingId == AppearanceRows.CompactEpisodeList.id,
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
            title = rowTitle(AppearanceRows.SkipSpecials),
            subtitle = stringResource(Res.string.settings_skip_special_episodes_subtitle),
            checked = preferences.skipSpecials,
            highlighted = highlightSettingId == AppearanceRows.SkipSpecials.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setSkipSpecials(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Eye,
            title = rowTitle(AppearanceRows.ShowMissingEpisodes),
            subtitle = stringResource(Res.string.settings_show_missing_episodes_subtitle),
            checked = preferences.showMissingEpisodes,
            highlighted = highlightSettingId == AppearanceRows.ShowMissingEpisodes.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowMissingEpisodes(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Photo,
            title = rowTitle(AppearanceRows.PreferLogos),
            subtitle = stringResource(Res.string.settings_prefer_logos_subtitle),
            checked = preferences.preferLogos,
            highlighted = highlightSettingId == AppearanceRows.PreferLogos.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setPreferLogos(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.DeviceMobileVibration,
            title = rowTitle(AppearanceRows.HapticsEnabled),
            subtitle = stringResource(Res.string.settings_haptic_feedback_subtitle),
            checked = preferences.hapticsEnabled,
            highlighted = highlightSettingId == AppearanceRows.HapticsEnabled.id,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setHapticsEnabled(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Share,
            title = rowTitle(AppearanceRows.ShowShareMedia),
            subtitle = stringResource(Res.string.settings_show_share_media_subtitle),
            checked = preferences.showShareMediaOption,
            highlighted = highlightSettingId == AppearanceRows.ShowShareMedia.id,
            onCheckedChange = { viewModel.edit { scope -> scope.experimental.setShowShareMediaOption(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.EyeOff,
            title = rowTitle(AppearanceRows.HideSearchHistory),
            subtitle = stringResource(Res.string.settings_hide_search_history_subtitle),
            checked = preferences.hideSearchHistory,
            highlighted = highlightSettingId == AppearanceRows.HideSearchHistory.id,
            onCheckedChange = { viewModel.edit { scope -> scope.experimental.setHideSearchHistory(it) } },
        )
        }
    }
}

/** The advanced-gated `appearance.performance` group. */
@Composable
private fun AppearancePerformanceGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.Bolt,
        title = stringResource(Res.string.settings_performance),
        summary = { appearancePerformanceSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearancePerformance.itemIdSet,
    ) {
        // Derived by rowTotalFor from the performance declaration (both rows
        // ride the advanced toggle — the structural gate around this group).
        val perfTotal = rowTotalFor(SettingsScreenGroups.appearancePerformance, RowAdmissionFlags(showAdvanced = true))
        SettingToggleItem(
            icon = Tabler.Outline.Gauge,
            title = rowTitle(AppearanceRows.PerformanceMode),
            subtitle = stringResource(Res.string.settings_performance_mode_subtitle),
            checked = preferences.performanceMode,
            highlighted = highlightSettingId == AppearanceRows.PerformanceMode.id,
            index = 0, count = perfTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setPerformanceMode(it) } },
        )
        SettingToggleItem(
            icon = Tabler.Outline.Activity,
            title = rowTitle(AppearanceRows.ReduceMotion),
            subtitle = stringResource(Res.string.settings_reduce_motion_subtitle),
            checked = preferences.reduceMotionEnabled,
            highlighted = highlightSettingId == AppearanceRows.ReduceMotion.id,
            index = 1, count = perfTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setReduceMotionEnabled(it) } },
        )
    }
}

/** The advanced-gated `appearance.eyeCare` group. */
@Composable
private fun AppearanceEyeCareGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    onShowStrengthSheet: () -> Unit,
) {
    SettingsGroup(
        icon = Tabler.Outline.Eye,
        title = stringResource(Res.string.settings_comfort_eye_care),
        summary = { appearanceEyeCareSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceEyeCare.itemIdSet,
    ) {
        // Derived by rowTotalFor from the eye-care declaration (both rows
        // ride the advanced toggle — the structural gate around this group).
        val eyeCareTotal = rowTotalFor(SettingsScreenGroups.appearanceEyeCare, RowAdmissionFlags(showAdvanced = true))
        SettingToggleItem(
            icon = Tabler.Outline.Moon,
            title = rowTitle(AppearanceRows.BlueLightFilter),
            subtitle = stringResource(Res.string.settings_blue_light_filter_subtitle),
            checked = preferences.blueLightFilterEnabled,
            highlighted = highlightSettingId == AppearanceRows.BlueLightFilter.id,
            index = 0, count = eyeCareTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBlueLightFilterEnabled(it) } },
        )
        SettingListItem(
            icon = Tabler.Outline.Adjustments,
            title = rowTitle(AppearanceRows.BlueLightStrength),
            subtitle = stringResource(Res.string.settings_blue_light_filter_strength_subtitle),
            trailingText = "${(preferences.blueLightFilterStrength * 100).toInt()}%",
            highlighted = highlightSettingId == AppearanceRows.BlueLightStrength.id,
            index = 1, count = eyeCareTotal,
            onClick = onShowStrengthSheet,
        )
    }
}

/** The advanced-gated `appearance.newsletter` group (enable + delivery day + the reorderable per-section rows). */
@Composable
private fun AppearanceNewsletterGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.Mail,
        title = stringResource(Res.string.settings_newsletter_config),
        summary = { appearanceNewsletterSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceNewsletter.itemIdSet,
    ) {
        val newsletterSections = rememberReorderableOrderedList(
            storedOrder = preferences.newsletterSectionOrder,
            onPersist = { order -> viewModel.edit { it.notification.setNewsletterSectionOrder(order) } },
        )

        // The two static declared rows (enable + delivery day) plus
        // the runtime-reorderable section rows: the declared
        // newsletter_sections row renders AS those rows, so its admitted
        // slot is replaced by newsletterSections.items.size.
        SettingsItemList(
            total = rowTotalFor(SettingsScreenGroups.appearanceNewsletter, RowAdmissionFlags(showAdvanced = true)) -
                1 + newsletterSections.items.size,
        ) {

        SettingToggleItem(
            icon = Tabler.Outline.Mail,
            title = rowTitle(AppearanceRows.NewsletterEnabled),
            subtitle = stringResource(Res.string.settings_enable_newsletter_subtitle),
            checked = preferences.newsletterEnabled,
                        highlighted = highlightSettingId == AppearanceRows.NewsletterEnabled.id,
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
            title = rowTitle(AppearanceRows.NewsletterDeliveryDay),
            subtitle = stringResource(Res.string.settings_newsletter_delivery_day_subtitle),
            trailingText = dayLabel,
            highlighted = highlightSettingId == AppearanceRows.NewsletterDeliveryDay.id,
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

// Legacy java.util.Calendar day-of-week numbers (the persisted
// `newsletterDayOfWeek` vocabulary): SUNDAY=1, MONDAY=2 … SATURDAY=7.
private const val CALENDAR_SUNDAY = 1
private const val CALENDAR_MONDAY = 2
private const val CALENDAR_TUESDAY = 3
private const val CALENDAR_WEDNESDAY = 4
private const val CALENDAR_THURSDAY = 5
private const val CALENDAR_FRIDAY = 6
private const val CALENDAR_SATURDAY = 7
