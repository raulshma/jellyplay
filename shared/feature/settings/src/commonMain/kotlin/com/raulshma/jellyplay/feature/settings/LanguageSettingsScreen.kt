package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.TrackSelectionPreset
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.ui.draw.clip
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_audio_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_display_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_display_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_font_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_forced_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_forced_subtitles_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_forced_subtitles_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_font_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_font_size_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_subtitle_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_subtitle_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hdr_subtitle_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_high_contrast_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_high_contrast_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_high_contrast_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_lang_system_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_language_subs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_open_subtitle_tester
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_open_subtitle_tester_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pgs_direct_play
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pgs_direct_play_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pgs_direct_play_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_background
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_background_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_edge_style
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_edge_style_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_font_size
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_language
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_language_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_no_offset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_position_bottom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_sync_offset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_text_color
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_text_color_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_vertical_position
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitle_vertical_position_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_subtitles_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_audio_order
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_audio_order_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_custom_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_dubbed_all_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_dubbed_shows_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_manual_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_subbed_all_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_preset_subbed_shows_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules_count
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_rules_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_selection
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_selection_preset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_selection_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_subtitle_order
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_track_subtitle_order_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ui_label

/**
 * Residual custom dialog tag. Most language/subtitle pickers flow through the
 * shared `PickerState` dispatcher; only the subtitle-background sheet stays
 * here because it mixes a colour list with an opacity slider — a shape
 * `PickerState` has no variant for.
 */
sealed class LanguageSettingsDialog {
    object None : LanguageSettingsDialog()
    object SubtitleBgColorPicker : LanguageSettingsDialog()
}

// App display languages. MUST stay in lockstep with `resourceConfigurations` in
// app/build.gradle.kts — that list is the source of truth for which locales have
// shipped values-<locale>/strings.xml. Advertising a locale here without translations
// causes a silent fallback to English, so never add an entry whose
// tag isn't also in resourceConfigurations.
internal val appLanguages = listOf(
    null to "System Default",
    "en" to "English",
    "de" to "Deutsch",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "pt" to "Português",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
)

private val appLanguageNameByCode: Map<String?, String> = appLanguages.associate { it.first to it.second }

/**
 * The declared screen groups in LazyColumn order — the derivation source the
 * deep-link scroll resolver consumes (see HighlightScroll.kt): the leading
 * language trio, then the track-selection group, then the subtitle
 * rows. Both always groups compose (the advanced rows hide inside the
 * subtitle group), so no advanced adjustment applies.
 */
private val languageScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.languageGeneral.itemIdSet,
    SettingsScreenGroups.languageTrackSelection.itemIdSet,
    SettingsScreenGroups.languageSubtitles.itemIdSet,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    onOpenSubtitleTester: () -> Unit = {},
    highlightSettingId: String? = null,
    viewModel: LanguageSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    // The declared row admissions the emission `if`s read — one gate per id,
    // declared beside the group items
    // (SettingsScreenGroups.languageSubtitles.rowAdmitted). The always rows
    // need no gate; the advanced structural block around the style rows
    // carries their declared Advanced gate; the HDR font-size row reads its
    // full All(Advanced, WhenOn) declaration.
    val subtitleRowFlags = RowAdmissionFlags(
        showAdvanced = showAdvanced,
        parentsOn = rowParentsOn(LanguageRows.HdrSubtitleStyle.id to preferences.hdrSubtitleStyleEnabled),
    )
    var activeDialog by remember { mutableStateOf<LanguageSettingsDialog>(LanguageSettingsDialog.None) }

    val langs = languages

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_language_subs_title),
        onBack = onBack,
        focusTag = "language_init",
        highlightSettingId = highlightSettingId,
        highlightGroups = languageScreenGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        pickerHost = true,
    ) { activePicker ->
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Language,
                    title = stringResource(Res.string.settings_language),
                    summary = {
                        val parts = mutableListOf<String>()
                        val appLangLabel = appLanguages.firstOrNull { it.first == preferences.appLanguage }?.second
                            ?: preferences.appLanguage ?: stringResource(Res.string.settings_lang_system_default)
                        parts.add(stringResource(Res.string.settings_ui_label, appLangLabel))
                        parts.add(stringResource(Res.string.settings_audio_label, preferences.preferredAudioLanguage ?: stringResource(Res.string.settings_lang_default)))
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val appLangLabel = appLanguages.firstOrNull { it.first == preferences.appLanguage }?.second
                        ?: preferences.appLanguage ?: stringResource(Res.string.settings_lang_system_default)
                    val appLangFallback = stringResource(Res.string.settings_lang_system_default)
                    val audioLangTitle = rowTitle(LanguageRows.AudioLanguage)
                    val langDefaultFallback = stringResource(Res.string.settings_lang_default)
                    val subtitleLangTitle = rowTitle(LanguageRows.SubtitleLanguage)
                    val displayLanguageTitle = rowTitle(LanguageRows.AppLanguage)
                    // The per-app display-language override only applies where the
                    // AppLocaleSetter seam is real (desktop's is a no-op), so the
                    // row vanishes there and the remaining rows re-index — the
                    // group's declared AppLocaleOverride Platform gate, which
                    // both the row total and the emission `if` read.
                    val showAppLocaleRow = settingsCapabilities.supportsAppLocaleOverride
                    val languageRowFlags = RowAdmissionFlags(supportsAppLocaleOverride = showAppLocaleRow)
                    // The row total derives from the declared leading trio via
                    // rowTotalFor (full per-id admission coverage).
                    SettingsItemList(total = rowTotalFor(SettingsScreenGroups.languageGeneral, languageRowFlags)) {
                    if (SettingsScreenGroups.languageGeneral.rowAdmitted(LanguageRows.AppLanguage.id, languageRowFlags)) {
                        SettingListItem(
                            icon = Tabler.Outline.Language,
                            title = rowTitle(LanguageRows.AppLanguage),
                            subtitle = stringResource(Res.string.settings_display_language_subtitle),
                            trailingText = appLangLabel,
                            highlighted = highlightSettingId == LanguageRows.AppLanguage.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = displayLanguageTitle,
                                    items = appLanguages.map { it.first },
                                    label = { code -> appLanguageNameByCode[code] ?: code ?: appLangFallback },
                                    isSelected = { it == preferences.appLanguage },
                                    onSelect = { viewModel.setAppLanguage(it) },
                                )
                            },
                        )
                    }
                    SettingListItem(
                        icon = Tabler.Outline.Language,
                        title = audioLangTitle,
                        subtitle = stringResource(Res.string.settings_audio_language_subtitle),
                        trailingText = preferences.preferredAudioLanguage ?: stringResource(Res.string.settings_lang_default),
                        highlighted = highlightSettingId == LanguageRows.AudioLanguage.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = audioLangTitle,
                                items = langs.map { it.first },
                                label = { code -> languageNameByCode[code] ?: code ?: langDefaultFallback },
                                isSelected = { it == preferences.preferredAudioLanguage },
                                onSelect = { language ->
                                    viewModel.edit { scope -> scope.subtitle.setPreferredAudioLanguage(language) }
                                },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = subtitleLangTitle,
                        subtitle = stringResource(Res.string.settings_subtitle_language_subtitle),
                        trailingText = preferences.preferredSubtitleLanguage ?: stringResource(Res.string.settings_lang_default),
                        highlighted = highlightSettingId == LanguageRows.SubtitleLanguage.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = subtitleLangTitle,
                                items = langs.map { it.first },
                                label = { code -> languageNameByCode[code] ?: code ?: langDefaultFallback },
                                isSelected = { it == preferences.preferredSubtitleLanguage },
                                onSelect = { language ->
                                    viewModel.edit { scope -> scope.subtitle.setPreferredSubtitleLanguage(language) }
                                },
                            )
                        },
                    )
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.AdjustmentsHorizontal,
                    title = stringResource(Res.string.settings_track_selection),
                    summary = { stringResource(Res.string.settings_track_selection_summary) },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    val rules = preferences.languageRules
                    var showAudioOrderEditor by remember { mutableStateOf(false) }
                    var showSubtitleOrderEditor by remember { mutableStateOf(false) }
                    var showRulesEditor by remember { mutableStateOf(false) }
                    val langDefaultFallback = stringResource(Res.string.settings_lang_default)
                    // Derived by rowTotalFor from the track-selection group
                    // declaration (every row declares Always, so the strict
                    // count fails loudly if a gated row ever lands here
                    // without an admission).
                    SettingsItemList(total = rowTotalFor(SettingsScreenGroups.languageTrackSelection, RowAdmissionFlags())) {
                        // Preset picker — the headline knob; descriptions are
                        // resolved here so the non-composable picker lambdas
                        // only carry strings.
                        val presetDescriptions = mapOf(
                            TrackSelectionPreset.MANUAL to stringResource(Res.string.settings_track_preset_manual_desc),
                            TrackSelectionPreset.SUBBED_SHOWS to stringResource(Res.string.settings_track_preset_subbed_shows_desc),
                            TrackSelectionPreset.DUBBED_SHOWS to stringResource(Res.string.settings_track_preset_dubbed_shows_desc),
                            TrackSelectionPreset.SUBBED_ALL to stringResource(Res.string.settings_track_preset_subbed_all_desc),
                            TrackSelectionPreset.DUBBED_ALL to stringResource(Res.string.settings_track_preset_dubbed_all_desc),
                            TrackSelectionPreset.CUSTOM to stringResource(Res.string.settings_track_preset_custom_desc),
                        )
                        val presetTitle = rowTitle(TrackSelectionRows.TrackSelectionPreset)
                        SettingListItem(
                            icon = Tabler.Outline.AdjustmentsHorizontal,
                            title = presetTitle,
                            subtitle = presetDescriptions[rules.preset].orEmpty(),
                            trailingText = rules.preset.displayName,
                            highlighted = highlightSettingId == TrackSelectionRows.TrackSelectionPreset.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = presetTitle,
                                    items = TrackSelectionPreset.entries,
                                    label = { it.displayName },
                                    subtitle = { presetDescriptions[it].orEmpty() },
                                    isSelected = { it == rules.preset },
                                    onSelect = { preset ->
                                        viewModel.edit { scope ->
                                            scope.subtitle.setTrackSelectionRules(rules.copy(preset = preset))
                                        }
                                    },
                                )
                            },
                        )
                        // Ordered audio/subtitle editors: up/down
                        // reordering in a dedicated sheet; entries feed the
                        // rule engine's global ordered language rung.
                        SettingListItem(
                            icon = Tabler.Outline.ListNumbers,
                            title = rowTitle(TrackSelectionRows.TrackAudioLanguages),
                            subtitle = stringResource(Res.string.settings_track_audio_order_subtitle),
                            trailingText = rules.audioLanguages.joinToString(", ") { languageNameByCode[it] ?: it }
                                .ifEmpty { langDefaultFallback },
                            highlighted = highlightSettingId == TrackSelectionRows.TrackAudioLanguages.id,
                            onClick = { showAudioOrderEditor = true },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ArrowsHorizontal,
                            title = rowTitle(TrackSelectionRows.TrackSubtitleLanguages),
                            subtitle = stringResource(Res.string.settings_track_subtitle_order_subtitle),
                            trailingText = rules.subtitleLanguages.joinToString(", ") { languageNameByCode[it] ?: it }
                                .ifEmpty { langDefaultFallback },
                            highlighted = highlightSettingId == TrackSelectionRows.TrackSubtitleLanguages.id,
                            onClick = { showSubtitleOrderEditor = true },
                        )
                        // Advanced rules: applies-to / title-pattern /
                        // language / mode cards, capped at 10.
                        SettingListItem(
                            icon = Tabler.Outline.Filter,
                            title = rowTitle(TrackSelectionRows.TrackRules),
                            subtitle = stringResource(Res.string.settings_track_rules_subtitle),
                            trailingText = stringResource(Res.string.settings_track_rules_count, rules.rules.size),
                            highlighted = highlightSettingId == TrackSelectionRows.TrackRules.id,
                            onClick = { showRulesEditor = true },
                        )
                    }
                    if (showAudioOrderEditor) {
                        OrderedLanguagesEditorSheet(
                            title = rowTitle(TrackSelectionRows.TrackAudioLanguages),
                            ordered = preferences.languageRules.audioLanguages,
                            onDismiss = { showAudioOrderEditor = false },
                            onChange = { ordered ->
                                viewModel.edit { scope ->
                                    scope.subtitle.setTrackSelectionRules(
                                        preferences.languageRules.copy(audioLanguages = ordered),
                                    )
                                }
                            },
                        )
                    }
                    if (showSubtitleOrderEditor) {
                        OrderedLanguagesEditorSheet(
                            title = rowTitle(TrackSelectionRows.TrackSubtitleLanguages),
                            ordered = preferences.languageRules.subtitleLanguages,
                            onDismiss = { showSubtitleOrderEditor = false },
                            onChange = { ordered ->
                                viewModel.edit { scope ->
                                    scope.subtitle.setTrackSelectionRules(
                                        preferences.languageRules.copy(subtitleLanguages = ordered),
                                    )
                                }
                            },
                        )
                    }
                    if (showRulesEditor) {
                        TrackRulesEditorSheet(
                            rules = preferences.languageRules,
                            onDismiss = { showRulesEditor = false },
                            onChange = { updated ->
                                viewModel.edit { scope ->
                                    scope.subtitle.setTrackSelectionRules(updated)
                                }
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Subtitles,
                    title = stringResource(Res.string.settings_subtitles),
                    summary = { stringResource(Res.string.settings_subtitles_summary, preferences.subtitleStyle.fontSize) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.languageSubtitles.itemIdSet,
                ) {
                    SubtitleStylePreview(
                        style = preferences.subtitleStyle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // Derived by rowTotalFor from the subtitles group
                    // declaration (every id's declared [RowAdmission]): 4
                    // always (the tester/font-size/forced-only trio plus
                    // high-contrast subtitles), the style rows behind their
                    // declared Advanced gate and the HDR font-size row behind
                    // the HDR-style toggle.
                    SettingsItemList(
                        total = rowTotalFor(SettingsScreenGroups.languageSubtitles, subtitleRowFlags),
                    ) {
                    SettingListItem(
                        icon = Tabler.Outline.Eye,
                        title = rowTitle(LanguageRows.SubtitleTester),
                        subtitle = stringResource(Res.string.settings_open_subtitle_tester_subtitle),
                        highlighted = highlightSettingId == LanguageRows.SubtitleTester.id,
                        onClick = onOpenSubtitleTester,
                    )
                    val fontSizeTitle = stringResource(Res.string.settings_subtitle_font_size)
                    SettingListItem(
                        icon = Tabler.Outline.Typography,
                        title = rowTitle(LanguageRows.SubtitleFontSize),
                        subtitle = stringResource(Res.string.settings_font_size_subtitle),
                        trailingText = "${preferences.subtitleStyle.fontSize}sp",
                        highlighted = highlightSettingId == LanguageRows.SubtitleFontSize.id,
                        onClick = {
                            val sizes = listOf(14, 18, 22, 24, 28, 32, 36, 40)
                            activePicker.value = pickerChip(
                                title = fontSizeTitle,
                                values = sizes,
                                current = preferences.subtitleStyle.fontSize,
                                label = { "${it}sp" },
                                onSelect = { size ->
                                    val current = preferences.subtitleStyle
                                    viewModel.edit { scope ->
                                        scope.subtitle.setSubtitleStyle(current.copy(fontSize = size))
                                    }
                                },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.TextSize,
                        title = rowTitle(LanguageRows.SubtitleForcedOnly),
                        subtitle = if (preferences.subtitlesForcedOnly) stringResource(Res.string.settings_forced_subtitles_on) else stringResource(Res.string.settings_forced_subtitles_off),
                        checked = preferences.subtitlesForcedOnly,
                        highlighted = highlightSettingId == LanguageRows.SubtitleForcedOnly.id,
                        onCheckedChange = { enabled ->
                            viewModel.edit { scope -> scope.subtitle.setSubtitlesForcedOnly(enabled) }
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = rowTitle(LanguageRows.HighContrastSubtitles),
                        subtitle = if (preferences.highContrastSubtitles) stringResource(Res.string.settings_high_contrast_on) else stringResource(Res.string.settings_high_contrast_off),
                        checked = preferences.highContrastSubtitles,
                        highlighted = highlightSettingId == LanguageRows.HighContrastSubtitles.id,
                        onCheckedChange = { enabled ->
                            viewModel.edit { scope -> scope.subtitle.setHighContrastSubtitles(enabled) }
                        },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Photo,
                            title = rowTitle(LanguageRows.PgsDirectPlay),
                            subtitle = if (preferences.pgsSubtitleDirectPlay) stringResource(Res.string.settings_pgs_direct_play_on) else stringResource(Res.string.settings_pgs_direct_play_off),
                            checked = preferences.pgsSubtitleDirectPlay,
                            highlighted = highlightSettingId == LanguageRows.PgsDirectPlay.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.playback.setPgsSubtitleDirectPlay(enabled) }
                            },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Sun,
                            title = rowTitle(LanguageRows.HdrSubtitleStyle),
                            subtitle = if (preferences.hdrSubtitleStyleEnabled) stringResource(Res.string.settings_hdr_subtitle_on) else stringResource(Res.string.settings_hdr_subtitle_off),
                            checked = preferences.hdrSubtitleStyleEnabled,
                            highlighted = highlightSettingId == LanguageRows.HdrSubtitleStyle.id,
                            onCheckedChange = { enabled ->
                                viewModel.edit { scope -> scope.subtitle.setHdrSubtitleStyleEnabled(enabled) }
                            },
                        )
                        if (SettingsScreenGroups.languageSubtitles.rowAdmitted(LanguageRows.HdrSubtitleFontSize.id, subtitleRowFlags)) {
                            SettingListItem(
                                icon = Tabler.Outline.Typography,
                                title = rowTitle(LanguageRows.HdrSubtitleFontSize),
                                subtitle = stringResource(Res.string.settings_hdr_font_size_subtitle),
                                trailingText = "${preferences.hdrSubtitleStyle.fontSize}sp",
                                highlighted = highlightSettingId == LanguageRows.HdrSubtitleFontSize.id,
                                onClick = {
                                    val current = preferences.hdrSubtitleStyle.fontSize
                                    val next = if (current >= 40) 16 else current + 2
                                    val style = preferences.hdrSubtitleStyle
                                    viewModel.edit { scope ->
                                        scope.subtitle.setHdrSubtitleStyle(style.copy(fontSize = next))
                                    }
                                },
                            )
                        }
                        val textColorTitle = rowTitle(LanguageRows.SubtitleColor)
                        SettingListItem(
                            icon = Tabler.Outline.Palette,
                            title = rowTitle(LanguageRows.SubtitleColor),
                            subtitle = stringResource(Res.string.settings_subtitle_text_color_subtitle),
                            trailingText = preferences.subtitleStyle.fontColor.name,
                            highlighted = highlightSettingId == LanguageRows.SubtitleColor.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = textColorTitle,
                                    items = SubtitleColor.entries,
                                    label = { it.name },
                                    isSelected = { it == preferences.subtitleStyle.fontColor },
                                    onSelect = { color ->
                                        val current = preferences.subtitleStyle
                                        viewModel.edit { scope ->
                                            scope.subtitle.setSubtitleStyle(current.copy(fontColor = color))
                                        }
                                    },
                                )
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Background,
                            title = rowTitle(LanguageRows.SubtitleBackground),
                            subtitle = stringResource(Res.string.settings_subtitle_background_subtitle),
                            trailingText = preferences.subtitleStyle.backgroundColor.name,
                            highlighted = highlightSettingId == LanguageRows.SubtitleBackground.id,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleBgColorPicker },
                        )
                        val edgeStyleTitle = rowTitle(LanguageRows.SubtitleEdgeStyle)
                        SettingListItem(
                            icon = Tabler.Outline.BorderAll,
                            title = rowTitle(LanguageRows.SubtitleEdgeStyle),
                            subtitle = stringResource(Res.string.settings_subtitle_edge_style_subtitle),
                            trailingText = preferences.subtitleStyle.edgeType.name,
                            highlighted = highlightSettingId == LanguageRows.SubtitleEdgeStyle.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = edgeStyleTitle,
                                    items = SubtitleEdgeType.entries,
                                    label = { it.name },
                                    isSelected = { it == preferences.subtitleStyle.edgeType },
                                    onSelect = { edge ->
                                        val current = preferences.subtitleStyle
                                        viewModel.edit { scope ->
                                            scope.subtitle.setSubtitleStyle(current.copy(edgeType = edge))
                                        }
                                    },
                                )
                            },
                        )
                        val syncOffsetTitle = rowTitle(LanguageRows.SubtitleSyncOffset)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = rowTitle(LanguageRows.SubtitleSyncOffset),
                            subtitle = if (preferences.subtitleStyle.offsetMs == 0L) stringResource(Res.string.settings_subtitle_no_offset) else "${preferences.subtitleStyle.offsetMs}ms",
                            trailingText = "${preferences.subtitleStyle.offsetMs}ms",
                            highlighted = highlightSettingId == LanguageRows.SubtitleSyncOffset.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = syncOffsetTitle,
                                    value = preferences.subtitleStyle.offsetMs.toFloat(),
                                    valueRange = -5000f..5000f,
                                    steps = 99,
                                    valueLabel = { "${it.toLong()}ms" },
                                    rangeStartLabel = "-5s",
                                    rangeEndLabel = "+5s",
                                    onConfirm = { offset ->
                                        val current = preferences.subtitleStyle
                                        viewModel.edit { scope ->
                                            scope.subtitle.setSubtitleStyle(current.copy(offsetMs = offset.toLong()))
                                        }
                                    },
                                )
                            },
                        )
                        val verticalPositionTitle = rowTitle(LanguageRows.SubtitleVerticalPosition)
                        val subtitlePositionBottomLabel = stringResource(Res.string.settings_subtitle_position_bottom)
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarDown,
                            title = rowTitle(LanguageRows.SubtitleVerticalPosition),
                            subtitle = stringResource(Res.string.settings_subtitle_vertical_position_subtitle),
                            trailingText = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                            highlighted = highlightSettingId == LanguageRows.SubtitleVerticalPosition.id,
                            onClick = {
                                activePicker.value = PickerState.Slider(
                                    title = verticalPositionTitle,
                                    value = preferences.subtitleStyle.verticalPosition,
                                    valueRange = 0f..0.4f,
                                    steps = 7,
                                    valueLabel = { "${(it * 100).toInt()}%" },
                                    rangeStartLabel = subtitlePositionBottomLabel,
                                    rangeEndLabel = "40%",
                                    onConfirm = { position ->
                                        val current = preferences.subtitleStyle
                                        viewModel.edit { scope ->
                                            scope.subtitle.setSubtitleStyle(current.copy(verticalPosition = position))
                                        }
                                    },
                                )
                            },
                        )
                    }
                    }
                }
            }

            if (!showAdvanced) {
                item {
                    HiddenSettingsHint(
                        hiddenCount = 5,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleBgColorPicker) {
        var bgOpacity by remember { mutableStateOf(preferences.subtitleStyle.backgroundOpacity) }
        TvSafeSheet(onDismissRequest = { activeDialog = LanguageSettingsDialog.None }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                SheetHeader(title = rowTitle(LanguageRows.SubtitleBackground), icon = Tabler.Outline.Palette)
                LazyColumn(
                    // KMP replacement for the Android-only LocalConfiguration.screenHeightDp:
                    // the window container height in dp (shared/core/ui WindowSizeClass pattern).
                    modifier = Modifier.heightIn(
                        max = with(LocalDensity.current) {
                            LocalWindowInfo.current.containerSize.height.toDp() * 0.35f
                        },
                    ),
                ) {
                    itemsIndexed(SubtitleColor.entries, key = { _, color -> color.name }, contentType = { _, _ -> "color" }) { index, color ->
                        val selected = color == preferences.subtitleStyle.backgroundColor
                        val shape = expressiveListShape(
                            index, SubtitleColor.entries.size,
                        )
                        val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                )
                                .then(tvFocusState.focusModifier)
                                .tvFocusIndicator(tvFocusState, shape)
                                .clickable {
                                    val current = preferences.subtitleStyle
                                    viewModel.edit { scope ->
                                        scope.subtitle.setSubtitleStyle(
                                            current.copy(backgroundColor = color),
                                        )
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                color.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Opacity: ${(bgOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = bgOpacity,
                    onValueChange = { bgOpacity = it },
                    valueRange = 0f..1f,
                    steps = 9,
                )
            }
        }
    }
}
