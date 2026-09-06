package com.raulshma.jellyplay.feature.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
// causes a silent fallback to English (analysis F-19), so never add an entry whose
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

private val LANGUAGE_GROUP_IDS = setOf("app_language", "audio_language", "subtitle_language")
private val SUBTITLE_GROUP_IDS = setOf("subtitle_font_size", "subtitle_forced_only", "high_contrast_subtitles", "pgs_direct_play", "hdr_subtitle_style", "hdr_subtitle_font_size", "subtitle_color", "subtitle_background", "subtitle_edge_style", "subtitle_sync_offset", "subtitle_vertical_position", "subtitle_tester")

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
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<LanguageSettingsDialog>(LanguageSettingsDialog.None) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "language_init",
    )

    val langs = languages

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            in LANGUAGE_GROUP_IDS -> 0
            in SUBTITLE_GROUP_IDS -> 1
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

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_language_subs_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        // Center a highlighted (search-navigated) setting in the viewport instead of parking it
        // at the bottom edge, which is the default BringIntoViewSpec behaviour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides
                com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
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
                    val audioLangTitle = stringResource(Res.string.settings_audio_language)
                    val langDefaultFallback = stringResource(Res.string.settings_lang_default)
                    val subtitleLangTitle = stringResource(Res.string.settings_subtitle_language)
                    val displayLanguageTitle = stringResource(Res.string.settings_display_language)
                    // The per-app display-language override only applies where the
                    // AppLocaleSetter seam is real (desktop's is a no-op), so the
                    // row vanishes there and the remaining rows re-index.
                    val showAppLocaleRow = settingsCapabilities.supportsAppLocaleOverride
                    val langRowCount = if (showAppLocaleRow) 3 else 2
                    var langIndex = 0
                    if (showAppLocaleRow) {
                        SettingListItem(
                            icon = Tabler.Outline.Language,
                            title = stringResource(Res.string.settings_display_language),
                            subtitle = stringResource(Res.string.settings_display_language_subtitle),
                            trailingText = appLangLabel,
                            highlighted = highlightSettingId == "app_language",
                            index = langIndex++, count = langRowCount,
                            onClick = {
                                activePicker = PickerState.List(
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
                        highlighted = highlightSettingId == "audio_language",
                        index = langIndex++, count = langRowCount,
                        onClick = {
                            activePicker = PickerState.List(
                                title = audioLangTitle,
                                items = langs.map { it.first },
                                label = { code -> languageNameByCode[code] ?: code ?: langDefaultFallback },
                                isSelected = { it == preferences.preferredAudioLanguage },
                                onSelect = { viewModel.setPreferredAudioLanguage(it) },
                            )
                        },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = subtitleLangTitle,
                        subtitle = stringResource(Res.string.settings_subtitle_language_subtitle),
                        trailingText = preferences.preferredSubtitleLanguage ?: stringResource(Res.string.settings_lang_default),
                        highlighted = highlightSettingId == "subtitle_language",
                        index = langIndex++, count = langRowCount,
                        onClick = {
                            activePicker = PickerState.List(
                                title = subtitleLangTitle,
                                items = langs.map { it.first },
                                label = { code -> languageNameByCode[code] ?: code ?: langDefaultFallback },
                                isSelected = { it == preferences.preferredSubtitleLanguage },
                                onSelect = { viewModel.setPreferredSubtitleLanguage(it) },
                            )
                        },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Subtitles,
                    title = stringResource(Res.string.settings_subtitles),
                    summary = { stringResource(Res.string.settings_subtitles_summary, preferences.subtitleStyle.fontSize) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SUBTITLE_GROUP_IDS,
                ) {
                    com.raulshma.jellyplay.core.ui.components.SubtitleStylePreview(
                        style = preferences.subtitleStyle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    var subIdx = 0
                    val subTotal = when {
                        !showAdvanced -> 4
                        preferences.hdrSubtitleStyleEnabled -> 12
                        else -> 11
                    }
                    SettingListItem(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(Res.string.settings_open_subtitle_tester),
                        subtitle = stringResource(Res.string.settings_open_subtitle_tester_subtitle),
                        highlighted = highlightSettingId == "subtitle_tester",
                        index = subIdx++, count = subTotal,
                        onClick = onOpenSubtitleTester,
                    )
                    val fontSizeTitle = stringResource(Res.string.settings_subtitle_font_size)
                    SettingListItem(
                        icon = Tabler.Outline.Typography,
                        title = stringResource(Res.string.settings_font_size),
                        subtitle = stringResource(Res.string.settings_font_size_subtitle),
                        trailingText = "${preferences.subtitleStyle.fontSize}sp",
                        highlighted = highlightSettingId == "subtitle_font_size",
                        index = subIdx++, count = subTotal,
                        onClick = {
                            val sizes = listOf(14, 18, 22, 24, 28, 32, 36, 40)
                            activePicker = pickerChip(
                                title = fontSizeTitle,
                                values = sizes,
                                current = preferences.subtitleStyle.fontSize,
                                label = { "${it}sp" },
                                onSelect = { size ->
                                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontSize = size))
                                },
                            )
                        },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.TextSize,
                        title = stringResource(Res.string.settings_forced_subtitles),
                        subtitle = if (preferences.subtitlesForcedOnly) stringResource(Res.string.settings_forced_subtitles_on) else stringResource(Res.string.settings_forced_subtitles_off),
                        checked = preferences.subtitlesForcedOnly,
                        highlighted = highlightSettingId == "subtitle_forced_only",
                        index = subIdx++, count = subTotal,
                        onCheckedChange = { viewModel.setSubtitlesForcedOnly(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(Res.string.settings_high_contrast_subtitles),
                        subtitle = if (preferences.highContrastSubtitles) stringResource(Res.string.settings_high_contrast_on) else stringResource(Res.string.settings_high_contrast_off),
                        checked = preferences.highContrastSubtitles,
                        highlighted = highlightSettingId == "high_contrast_subtitles",
                        index = subIdx++, count = subTotal,
                        onCheckedChange = { viewModel.setHighContrastSubtitles(it) },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Photo,
                            title = stringResource(Res.string.settings_pgs_direct_play),
                            subtitle = if (preferences.pgsSubtitleDirectPlay) stringResource(Res.string.settings_pgs_direct_play_on) else stringResource(Res.string.settings_pgs_direct_play_off),
                            checked = preferences.pgsSubtitleDirectPlay,
                            highlighted = highlightSettingId == "pgs_direct_play",
                            index = subIdx++, count = subTotal,
                            onCheckedChange = { viewModel.setPgsSubtitleDirectPlay(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Sun,
                            title = stringResource(Res.string.settings_hdr_subtitle_style),
                            subtitle = if (preferences.hdrSubtitleStyleEnabled) stringResource(Res.string.settings_hdr_subtitle_on) else stringResource(Res.string.settings_hdr_subtitle_off),
                            checked = preferences.hdrSubtitleStyleEnabled,
                            highlighted = highlightSettingId == "hdr_subtitle_style",
                            index = subIdx++, count = subTotal,
                            onCheckedChange = { viewModel.setHdrSubtitleStyleEnabled(it) },
                        )
                        if (preferences.hdrSubtitleStyleEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Typography,
                                title = stringResource(Res.string.settings_hdr_font_size),
                                subtitle = stringResource(Res.string.settings_hdr_font_size_subtitle),
                                trailingText = "${preferences.hdrSubtitleStyle.fontSize}sp",
                                highlighted = highlightSettingId == "hdr_subtitle_font_size",
                                index = subIdx++, count = subTotal,
                                onClick = {
                                    val current = preferences.hdrSubtitleStyle.fontSize
                                    val next = if (current >= 40) 16 else current + 2
                                    viewModel.setHdrSubtitleStyle(preferences.hdrSubtitleStyle.copy(fontSize = next))
                                },
                            )
                        }
                        val textColorTitle = stringResource(Res.string.settings_subtitle_text_color)
                        SettingListItem(
                            icon = Tabler.Outline.Palette,
                            title = stringResource(Res.string.settings_subtitle_text_color),
                            subtitle = stringResource(Res.string.settings_subtitle_text_color_subtitle),
                            trailingText = preferences.subtitleStyle.fontColor.name,
                            highlighted = highlightSettingId == "subtitle_color",
                            index = subIdx++, count = subTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = textColorTitle,
                                    items = SubtitleColor.entries,
                                    label = { it.name },
                                    isSelected = { it == preferences.subtitleStyle.fontColor },
                                    onSelect = {
                                        viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontColor = it))
                                    },
                                )
                            },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Background,
                            title = stringResource(Res.string.settings_subtitle_background),
                            subtitle = stringResource(Res.string.settings_subtitle_background_subtitle),
                            trailingText = preferences.subtitleStyle.backgroundColor.name,
                            highlighted = highlightSettingId == "subtitle_background",
                            index = subIdx++, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleBgColorPicker },
                        )
                        val edgeStyleTitle = stringResource(Res.string.settings_subtitle_edge_style)
                        SettingListItem(
                            icon = Tabler.Outline.BorderAll,
                            title = stringResource(Res.string.settings_subtitle_edge_style),
                            subtitle = stringResource(Res.string.settings_subtitle_edge_style_subtitle),
                            trailingText = preferences.subtitleStyle.edgeType.name,
                            highlighted = highlightSettingId == "subtitle_edge_style",
                            index = subIdx++, count = subTotal,
                            onClick = {
                                activePicker = PickerState.List(
                                    title = edgeStyleTitle,
                                    items = SubtitleEdgeType.entries,
                                    label = { it.name },
                                    isSelected = { it == preferences.subtitleStyle.edgeType },
                                    onSelect = {
                                        viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(edgeType = it))
                                    },
                                )
                            },
                        )
                        val syncOffsetTitle = stringResource(Res.string.settings_subtitle_sync_offset)
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = stringResource(Res.string.settings_subtitle_sync_offset),
                            subtitle = if (preferences.subtitleStyle.offsetMs == 0L) stringResource(Res.string.settings_subtitle_no_offset) else "${preferences.subtitleStyle.offsetMs}ms",
                            trailingText = "${preferences.subtitleStyle.offsetMs}ms",
                            highlighted = highlightSettingId == "subtitle_sync_offset",
                            index = subIdx++, count = subTotal,
                            onClick = {
                                activePicker = PickerState.Slider(
                                    title = syncOffsetTitle,
                                    value = preferences.subtitleStyle.offsetMs.toFloat(),
                                    valueRange = -5000f..5000f,
                                    steps = 99,
                                    valueLabel = { "${it.toLong()}ms" },
                                    rangeStartLabel = "-5s",
                                    rangeEndLabel = "+5s",
                                    onConfirm = {
                                        viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(offsetMs = it.toLong()))
                                    },
                                )
                            },
                        )
                        val verticalPositionTitle = stringResource(Res.string.settings_subtitle_vertical_position)
                        val subtitlePositionBottomLabel = stringResource(Res.string.settings_subtitle_position_bottom)
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarDown,
                            title = stringResource(Res.string.settings_subtitle_vertical_position),
                            subtitle = stringResource(Res.string.settings_subtitle_vertical_position_subtitle),
                            trailingText = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                            highlighted = highlightSettingId == "subtitle_vertical_position",
                            index = subIdx, count = subTotal,
                            onClick = {
                            activePicker = PickerState.Slider(
                                title = verticalPositionTitle,
                                value = preferences.subtitleStyle.verticalPosition,
                                valueRange = 0f..0.4f,
                                steps = 7,
                                valueLabel = { "${(it * 100).toInt()}%" },
                                rangeStartLabel = subtitlePositionBottomLabel,
                                rangeEndLabel = "40%",
                                onConfirm = {
                                    viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(verticalPosition = it))
                                },
                            )
                        },
                        )
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
                SheetHeader(title = stringResource(Res.string.settings_subtitle_background), icon = Tabler.Outline.Palette)
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
                                    viewModel.setSubtitleStyle(
                                        preferences.subtitleStyle.copy(backgroundColor = color),
                                    )
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

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
