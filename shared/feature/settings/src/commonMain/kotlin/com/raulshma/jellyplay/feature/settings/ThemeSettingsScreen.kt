package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.raulshma.jellyplay.core.model.TvOverscan
import com.raulshma.jellyplay.core.model.TvUiScale
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_entry_theme
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_artwork_dynamic
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backdrop_theme_music_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_strength
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_color_blind_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_high
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_accent_suffix
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_contrast_standard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_date_format
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dynamic_theming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_app
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_handedness
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_grid
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_list
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_masonry
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_layout_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_view_thumb
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_morning_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_labels_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_nav_labels_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_night_starts_at
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_oled_token
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_overridden_variant
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduce_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_nav_labels
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_style_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_always_dark
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_always_light
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_follow_system
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_scheduled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_scheduled_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_theme_style
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val THEME_HIGHLIGHT_IDS = setOf(AppearanceRows.ThemeMode.id, AppearanceRows.ThemeScheduler.id)

/**
 * The Theme & display leaf's ONE ordered slot declaration (ADR-0009
 * amendment; the derivation-only shape — the content slots stay empty, the
 * screen attaches its hand-written bodies via [withContent]): the theme
 * group, then the advanced-gated Performance (Reduce Motion + the master
 * level quick picker / hub deep-link) and Eye care groups — the former
 * combined appearance screen's first, fourth and fifth slots (ADR-0017's
 * split; the Newsletter group stayed on the hub). The contract test pins the
 * slot/group parity against this same declaration.
 */
internal val ThemeSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.appearanceTheme.asScreenGroup(),
    SettingsScreenGroups.appearancePerformance.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.appearanceEyeCare.asScreenGroup(advancedOnly = true),
)

/**
 * The Theme & display leaf's group bodies, mapped onto the ONE ordered
 * declaration ([ThemeSettingsScreenSlots], in LazyColumn order) with each
 * slot's hand-written body attached inline — the bodies close over the
 * screen's state (preferences, view model, the highlight id) and the shell
 * only reads the derived shape (ADR-0009: per-row emission stays
 * hand-written).
 */
private fun themeScreenGroups(
    preferences: AppearanceScreenPreferences,
    showAdvanced: Boolean,
    isTv: Boolean,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    onOpenPerformanceHub: () -> Unit,
    showBlueLightStrengthSheet: () -> Unit,
): List<PreferenceScreenGroup> =
    ThemeSettingsScreenSlots.withContent(
        bodies = listOf(
    { activePicker ->
        AppearanceThemeGroup(
            preferences = preferences,
            showAdvanced = showAdvanced,
            isTv = isTv,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            activePicker = activePicker,
        )
    },
    { activePicker ->
        AppearancePerformanceGroup(
            preferences = preferences,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            onOpenPerformanceHub = onOpenPerformanceHub,
            activePicker = activePicker,
        )
    },
    { _ ->
        AppearanceEyeCareGroup(
            preferences = preferences,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            onShowStrengthSheet = showBlueLightStrengthSheet,
        )
    },
        ),
    )

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
        // The mode's localized row face (the same four resources the row's
        // subtitle when-map and the hub entrance fold resolve) — never the
        // raw enum name.
        parts.add(
            when (preferences.themeMode) {
                ThemeMode.SYSTEM -> stringResource(Res.string.settings_theme_follow_system)
                ThemeMode.LIGHT -> stringResource(Res.string.settings_theme_always_light)
                ThemeMode.DARK -> stringResource(Res.string.settings_theme_always_dark)
                ThemeMode.SCHEDULED -> stringResource(Res.string.settings_theme_scheduled_label)
            },
        )
        val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
        parts.add(stringResource(Res.string.settings_accent_suffix, accentName))
        parts.add(preferences.colorStyle.displayName)
        if (preferences.dynamicTheming) parts.add(stringResource(Res.string.settings_artwork_dynamic))
    }
    if (preferences.oledMode) parts.add(stringResource(Res.string.settings_oled_token))
    if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add(
        when (preferences.contrastLevel) {
            ContrastLevel.MEDIUM -> stringResource(Res.string.settings_contrast_medium)
            ContrastLevel.HIGH -> stringResource(Res.string.settings_contrast_high)
            ContrastLevel.DEFAULT -> stringResource(Res.string.settings_contrast_standard)
        },
    )
    return parts.joinToString(", ")
}

/**
 * The Theme & display leaf's theme group total, derived from the fused rows'
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

/**
 * The Theme & display LEAF (ADR-0017's appearance split): the whole theme
 * group behind the hub's Theme & display entrance, plus the advanced-gated
 * Performance (Reduce Motion — the motion concern's home, kept advanced
 * alongside its master-level quick picker and hub deep-link) and Eye care
 * groups. The advanced rows ride the same advanced toggle they always did —
 * the leaf carries the toggle and the derived hidden-settings hint, exactly
 * the former combined screen's shape narrowed to these groups.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** The Performance group's "Customize…" row: deep-links into the Performance hub. */
    onOpenPerformanceHub: () -> Unit = {},
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()

    var showBlueLightStrengthSheet by remember { mutableStateOf(false) }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_entry_theme),
        onBack = onBack,
        focusTag = "appearance_theme_init",
        highlightSettingId = highlightSettingId,
        groups = themeScreenGroups(
            preferences = preferences,
            showAdvanced = showAdvanced,
            isTv = LocalTvMode.current,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            onOpenPerformanceHub = onOpenPerformanceHub,
            showBlueLightStrengthSheet = { showBlueLightStrengthSheet = true },
        ),
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            rowFlags = RowAdmissionFlags(showAdvanced = false, isTv = LocalTvMode.current),
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
    )

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
internal fun AppearanceThemeGroup(
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
            val themeScheduledLabel = stringResource(Res.string.settings_theme_scheduled_label)
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
                            ThemeMode.SCHEDULED to themeScheduledLabel,
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
            SettingListItem(
                icon = rowIcon(AppearanceRows.ThemeStyle),
                title = styleTitle,
                subtitle = rowSubtitle(AppearanceRows.ThemeStyle),
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
                        subtitle = rowSubtitle(AppearanceRows.DynamicTheming),
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
                    subtitle = rowSubtitle(AppearanceRows.OledMode),
                    checked = preferences.oledMode,
                    highlighted = highlightSettingId == AppearanceRows.OledMode.id,
                    onCheckedChange = { viewModel.edit { scope -> scope.appearance.setOledMode(it) } },
                )
            }
            // The TV overscan calibration: TV-only (the layout override's
            // form-factor twin — the fork the row's RowAdmission.Tv gate
            // declares), never behind the advanced toggle so TV users can
            // always reach it.
            if (isTv) {
                val screenFitTitle = rowTitle(AppearanceRows.ScreenFit)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.ScreenFit),
                    title = screenFitTitle,
                    subtitle = rowSubtitle(AppearanceRows.ScreenFit),
                    trailingText = preferences.tvOverscan.displayName,
                    highlighted = highlightSettingId == AppearanceRows.ScreenFit.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = screenFitTitle,
                            items = TvOverscan.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.tvOverscan },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setTvOverscan(it) } },
                        )
                    },
                )
                // The TV UI scale (the density multiplier): the overscan
                // calibration's sibling — same TV-only, always-visible,
                // D-pad-picker treatment.
                val uiScaleTitle = rowTitle(AppearanceRows.TvUiScale)
                SettingListItem(
                    icon = rowIcon(AppearanceRows.TvUiScale),
                    title = uiScaleTitle,
                    subtitle = rowSubtitle(AppearanceRows.TvUiScale),
                    trailingText = preferences.tvUiScale.displayName,
                    highlighted = highlightSettingId == AppearanceRows.TvUiScale.id,
                    onClick = {
                        activePicker.value = PickerState.List(
                            title = uiScaleTitle,
                            items = TvUiScale.entries,
                            label = { it.displayName },
                            isSelected = { it == preferences.tvUiScale },
                            onSelect = { viewModel.edit { scope -> scope.appearance.setTvUiScale(it) } },
                        )
                    },
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
                        subtitle = rowSubtitle(AppearanceRows.LayoutMode),
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
                    subtitle = rowSubtitle(AppearanceRows.DateFormat),
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
                    subtitle = rowSubtitle(AppearanceRows.FontScale),
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
                    subtitle = rowSubtitle(AppearanceRows.ColorBlindMode),
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
                    subtitle = rowSubtitle(AppearanceRows.HandMode),
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
                        subtitle = rowSubtitle(AppearanceRows.ScheduledStart),
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
                        subtitle = rowSubtitle(AppearanceRows.ScheduledEnd),
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
