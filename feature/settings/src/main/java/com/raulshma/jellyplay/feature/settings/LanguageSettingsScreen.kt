package com.raulshma.jellyplay.feature.settings

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
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

sealed class LanguageSettingsDialog {
    object None : LanguageSettingsDialog()
    object AudioLanguagePicker : LanguageSettingsDialog()
    object SubtitleLanguagePicker : LanguageSettingsDialog()
    object SubtitleFontPicker : LanguageSettingsDialog()
    object SubtitleColorPicker : LanguageSettingsDialog()
    object SubtitleBgColorPicker : LanguageSettingsDialog()
    object SubtitleEdgePicker : LanguageSettingsDialog()
    object SubtitleOffsetPicker : LanguageSettingsDialog()
    object SubtitlePositionPicker : LanguageSettingsDialog()
    object AppLanguagePicker : LanguageSettingsDialog()
}

internal val appLanguages = listOf(
    null to "System Default",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "de" to "Deutsch",
    "it" to "Italiano",
    "pt" to "Português",
    "ru" to "Русский",
    "ja" to "日本語",
    "zh" to "中文",
    "ko" to "한국어",
    "ar" to "العربية",
    "hi" to "हिन्दी",
    "tr" to "Türkçe",
    "pl" to "Polski",
    "nl" to "Nederlands",
    "sv" to "Svenska",
    "cs" to "Čeština",
    "da" to "Dansk",
    "fi" to "Suomi",
    "nb" to "Norsk Bokmål",
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    var activeDialog by remember { mutableStateOf<LanguageSettingsDialog>(LanguageSettingsDialog.None) }
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

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
            in listOf("app_language", "audio_language", "subtitle_language") -> 0
            in listOf("subtitle_font_size", "subtitle_forced_only", "high_contrast_subtitles", "pgs_direct_play", "hdr_subtitle_style", "hdr_subtitle_font_size", "subtitle_color", "subtitle_background", "subtitle_edge_style", "subtitle_sync_offset", "subtitle_vertical_position") -> 1
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
        title = stringResource(R.string.settings_language_subs_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
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
                    title = stringResource(R.string.settings_language),
                    summary = {
                        val parts = mutableListOf<String>()
                        val appLangLabel = appLanguages.firstOrNull { it.first == preferences.appLanguage }?.second
                            ?: preferences.appLanguage ?: stringResource(R.string.settings_lang_system_default)
                        parts.add(stringResource(R.string.settings_ui_label, appLangLabel))
                        parts.add(stringResource(R.string.settings_audio_label, preferences.preferredAudioLanguage ?: stringResource(R.string.settings_lang_default)))
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val appLangLabel = appLanguages.firstOrNull { it.first == preferences.appLanguage }?.second
                        ?: preferences.appLanguage ?: stringResource(R.string.settings_lang_system_default)
                    SettingListItem(
                        icon = Tabler.Outline.Language,
                        title = stringResource(R.string.settings_display_language),
                        subtitle = stringResource(R.string.settings_display_language_subtitle),
                        trailingText = appLangLabel,
                        highlighted = highlightSettingId == "app_language",
                        index = 0, count = 3,
                        onClick = { activeDialog = LanguageSettingsDialog.AppLanguagePicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Language,
                        title = stringResource(R.string.settings_audio_language),
                        subtitle = stringResource(R.string.settings_audio_language_subtitle),
                        trailingText = preferences.preferredAudioLanguage ?: stringResource(R.string.settings_lang_default),
                        highlighted = highlightSettingId == "audio_language",
                        index = 1, count = 3,
                        onClick = { activeDialog = LanguageSettingsDialog.AudioLanguagePicker },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Subtitles,
                        title = stringResource(R.string.settings_subtitle_language),
                        subtitle = stringResource(R.string.settings_subtitle_language_subtitle),
                        trailingText = preferences.preferredSubtitleLanguage ?: stringResource(R.string.settings_lang_default),
                        highlighted = highlightSettingId == "subtitle_language",
                        index = 2, count = 3,
                        onClick = { activeDialog = LanguageSettingsDialog.SubtitleLanguagePicker },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Subtitles,
                    title = stringResource(R.string.settings_subtitles),
                    summary = { stringResource(R.string.settings_subtitles_summary, preferences.subtitleStyle.fontSize) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in listOf("subtitle_font_size", "subtitle_forced_only", "high_contrast_subtitles", "pgs_direct_play", "hdr_subtitle_style", "hdr_subtitle_font_size", "subtitle_color", "subtitle_background", "subtitle_edge_style", "subtitle_sync_offset", "subtitle_vertical_position"),
                ) {
                    var subIdx = 0
                    val subTotal = when {
                        !showAdvanced -> 3
                        preferences.hdrSubtitleStyleEnabled -> 11
                        else -> 10
                    }
                    SettingListItem(
                        icon = Tabler.Outline.Typography,
                        title = stringResource(R.string.settings_font_size),
                        subtitle = stringResource(R.string.settings_font_size_subtitle),
                        trailingText = "${preferences.subtitleStyle.fontSize}sp",
                        highlighted = highlightSettingId == "subtitle_font_size",
                        index = subIdx++, count = subTotal,
                        onClick = { activeDialog = LanguageSettingsDialog.SubtitleFontPicker },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.TextSize,
                        title = stringResource(R.string.settings_forced_subtitles),
                        subtitle = if (preferences.subtitlesForcedOnly) stringResource(R.string.settings_forced_subtitles_on) else stringResource(R.string.settings_forced_subtitles_off),
                        checked = preferences.subtitlesForcedOnly,
                        highlighted = highlightSettingId == "subtitle_forced_only",
                        index = subIdx++, count = subTotal,
                        onCheckedChange = { viewModel.setSubtitlesForcedOnly(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Eye,
                        title = stringResource(R.string.settings_high_contrast_subtitles),
                        subtitle = if (preferences.highContrastSubtitles) stringResource(R.string.settings_high_contrast_on) else stringResource(R.string.settings_high_contrast_off),
                        checked = preferences.highContrastSubtitles,
                        highlighted = highlightSettingId == "high_contrast_subtitles",
                        index = subIdx++, count = subTotal,
                        onCheckedChange = { viewModel.setHighContrastSubtitles(it) },
                    )
                    if (showAdvanced) {
                        SettingToggleItem(
                            icon = Tabler.Outline.Photo,
                            title = stringResource(R.string.settings_pgs_direct_play),
                            subtitle = if (preferences.pgsSubtitleDirectPlay) stringResource(R.string.settings_pgs_direct_play_on) else stringResource(R.string.settings_pgs_direct_play_off),
                            checked = preferences.pgsSubtitleDirectPlay,
                            highlighted = highlightSettingId == "pgs_direct_play",
                            index = subIdx++, count = subTotal,
                            onCheckedChange = { viewModel.setPgsSubtitleDirectPlay(it) },
                        )
                        SettingToggleItem(
                            icon = Tabler.Outline.Sun,
                            title = stringResource(R.string.settings_hdr_subtitle_style),
                            subtitle = if (preferences.hdrSubtitleStyleEnabled) stringResource(R.string.settings_hdr_subtitle_on) else stringResource(R.string.settings_hdr_subtitle_off),
                            checked = preferences.hdrSubtitleStyleEnabled,
                            highlighted = highlightSettingId == "hdr_subtitle_style",
                            index = subIdx++, count = subTotal,
                            onCheckedChange = { viewModel.setHdrSubtitleStyleEnabled(it) },
                        )
                        if (preferences.hdrSubtitleStyleEnabled) {
                            SettingListItem(
                                icon = Tabler.Outline.Typography,
                                title = "HDR Font Size",
                                subtitle = "Subtitle text size for HDR content",
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
                        SettingListItem(
                            icon = Tabler.Outline.Palette,
                            title = "Text Color",
                            subtitle = "Subtitle text color",
                            trailingText = preferences.subtitleStyle.fontColor.name,
                            highlighted = highlightSettingId == "subtitle_color",
                            index = subIdx++, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleColorPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Background,
                            title = "Background",
                            subtitle = "Subtitle background color and opacity",
                            trailingText = preferences.subtitleStyle.backgroundColor.name,
                            highlighted = highlightSettingId == "subtitle_background",
                            index = subIdx++, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleBgColorPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.BorderAll,
                            title = "Edge Style",
                            subtitle = "Subtitle outline style",
                            trailingText = preferences.subtitleStyle.edgeType.name,
                            highlighted = highlightSettingId == "subtitle_edge_style",
                            index = subIdx++, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleEdgePicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Clock,
                            title = "Sync Offset",
                            subtitle = if (preferences.subtitleStyle.offsetMs == 0L) "No offset" else "${preferences.subtitleStyle.offsetMs}ms",
                            trailingText = "${preferences.subtitleStyle.offsetMs}ms",
                            highlighted = highlightSettingId == "subtitle_sync_offset",
                            index = subIdx++, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitleOffsetPicker },
                        )
                        SettingListItem(
                            icon = Tabler.Outline.ArrowBarDown,
                            title = "Vertical Position",
                            subtitle = "Subtitle vertical position on screen",
                            trailingText = "${(preferences.subtitleStyle.verticalPosition * 100).toInt()}%",
                            highlighted = highlightSettingId == "subtitle_vertical_position",
                            index = subIdx, count = subTotal,
                            onClick = { activeDialog = LanguageSettingsDialog.SubtitlePositionPicker },
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

    if (activeDialog is LanguageSettingsDialog.AppLanguagePicker) {
        SettingsListPickerSheet(
            title = "Display Language",
            items = appLanguages.map { it.first },
            label = { code -> appLanguages.find { it.first == code }?.second ?: code ?: "System Default" },
            isSelected = { it == preferences.appLanguage },
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = {
                viewModel.setAppLanguage(it)
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.AudioLanguagePicker) {
        SettingsListPickerSheet(
            title = "Audio Language",
            items = langs.map { it.first },
            label = { code -> langs.find { it.first == code }?.second ?: code ?: "Default" },
            isSelected = { it == preferences.preferredAudioLanguage },
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = {
                viewModel.setPreferredAudioLanguage(it)
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleLanguagePicker) {
        SettingsListPickerSheet(
            title = "Subtitle Language",
            items = langs.map { it.first },
            label = { code -> langs.find { it.first == code }?.second ?: code ?: "Default" },
            isSelected = { it == preferences.preferredSubtitleLanguage },
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = {
                viewModel.setPreferredSubtitleLanguage(it)
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleFontPicker) {
        val sizes = listOf(14, 18, 22, 24, 28, 32, 36, 40)
        SettingsChipPickerSheet(
            title = "Subtitle Font Size",
            options = sizes.map { "${it}sp" },
            selectedIndex = sizes.indexOf(preferences.subtitleStyle.fontSize),
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = { index ->
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontSize = sizes[index]))
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleColorPicker) {
        SettingsListPickerSheet(
            title = "Subtitle Text Color",
            items = SubtitleColor.entries,
            label = { it.name },
            isSelected = { it == preferences.subtitleStyle.fontColor },
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(fontColor = it))
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleBgColorPicker) {
        var bgOpacity by remember { mutableStateOf(preferences.subtitleStyle.backgroundOpacity) }
        AdaptiveSheet(onDismissRequest = { activeDialog = LanguageSettingsDialog.None }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    "Subtitle Background",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.35f),
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

    if (activeDialog is LanguageSettingsDialog.SubtitleEdgePicker) {
        SettingsListPickerSheet(
            title = "Subtitle Edge Style",
            items = SubtitleEdgeType.entries,
            label = { it.name },
            isSelected = { it == preferences.subtitleStyle.edgeType },
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onSelect = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(edgeType = it))
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitleOffsetPicker) {
        SettingsSliderSheet(
            title = "Subtitle Sync Offset",
            value = preferences.subtitleStyle.offsetMs.toFloat(),
            valueRange = -5000f..5000f,
            steps = 99,
            valueLabel = { "${it.toLong()}ms" },
            rangeStartLabel = "-5s",
            rangeEndLabel = "+5s",
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onConfirm = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(offsetMs = it.toLong()))
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }

    if (activeDialog is LanguageSettingsDialog.SubtitlePositionPicker) {
        SettingsSliderSheet(
            title = "Subtitle Vertical Position",
            value = preferences.subtitleStyle.verticalPosition,
            valueRange = 0f..0.4f,
            steps = 7,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "Bottom",
            rangeEndLabel = "40%",
            onDismiss = { activeDialog = LanguageSettingsDialog.None },
            onConfirm = {
                viewModel.setSubtitleStyle(preferences.subtitleStyle.copy(verticalPosition = it))
                activeDialog = LanguageSettingsDialog.None
            },
        )
    }
}
