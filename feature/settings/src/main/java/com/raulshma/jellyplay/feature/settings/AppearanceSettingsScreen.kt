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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.AppFontScale
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.CenterBringIntoViewSpec
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

private val THEME_HIGHLIGHT_IDS = setOf("theme_mode", "theme_scheduler")
private val APPEARANCE_LIBRARY_GROUP_IDS = setOf("show_unwatched_badge", "show_watched_checkmark", "hide_watched_items", "hide_episode_thumbnails", "skip_specials", "show_share_media", "show_external_ratings")
private val PERFORMANCE_GROUP_IDS = setOf("performance_mode", "reduce_motion")
private val BLUE_LIGHT_GROUP_IDS = setOf("blue_light_filter", "blue_light_strength")
private val NEWSLETTER_GROUP_IDS = setOf("newsletter_enabled", "newsletter_delivery_day", "newsletter_sections")

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    onPinnedHomeSections: (String?) -> Unit = {},
    onHomeLayoutPresets: (String?) -> Unit = {},
    onConfigureLibraries: (String?) -> Unit = {},
    highlightSettingId: String? = null,
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

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
            "theme_mode", "theme_scheduler", "synthwave_mode", "soothing_mode", "monochrome_mode",
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
        // Index 0 = Theme, 1 = Navigation customization, 2 = Library & Cards, 3 = Home Screen Layout.
        // Performance/Eye Care/Newsletter only exist when advanced is on
        // and occupy indices 4/5/6 respectively.
        when (highlightSettingId) {
            in themeGroup -> 0
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
        title = stringResource(R.string.settings_appearance_title),
        onBack = onBack,
        backgroundColor = backgroundColor,
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
                    contentDescription = stringResource(R.string.settings_reset_defaults_cd),
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
                    title = stringResource(R.string.settings_theme),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.synthwaveMode) {
                            parts.add(stringResource(R.string.settings_synthwave_accent, preferences.synthwaveAccent.lowercase().replaceFirstChar { it.uppercase() }))
                        } else if (preferences.soothingMode) {
                            parts.add(stringResource(R.string.settings_soothing_accent, preferences.soothingAccent.lowercase().replaceFirstChar { it.uppercase() }))
                        } else if (preferences.monochromeMode) {
                            parts.add(stringResource(R.string.settings_monochrome_nothing))
                        } else {
                            parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                            val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
                            parts.add("$accentName accent")
                            parts.add(preferences.colorStyle.displayName)
                            if (preferences.dynamicTheming) parts.add(stringResource(R.string.settings_artwork_dynamic))
                        }
                        if (preferences.oledMode) parts.add("OLED")
                        if (preferences.contrastLevel != ContrastLevel.DEFAULT) parts.add("${preferences.contrastLevel.name.lowercase().replaceFirstChar { it.uppercase() }} contrast")
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val isAndroid12 = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
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

                    val appearanceItems = remember(
                        preferences.synthwaveMode,
                        preferences.soothingMode,
                        preferences.monochromeMode,
                        preferences.themeMode,
                        preferences.hiddenCwItemIds,
                        showAdvanced,
                        isDarkActive,
                        isAndroid12,
                    ) {
                        buildList {
                            add("theme_mode")
                            add("synthwave_mode")
                            if (preferences.synthwaveMode) {
                                add("synthwave_accent")
                            }
                            add("soothing_mode")
                            if (preferences.soothingMode) {
                                add("soothing_accent")
                            }
                            add("monochrome_mode")
                            if (!preferences.synthwaveMode && !preferences.soothingMode && !preferences.monochromeMode) {
                                add("accent_color")
                                add("color_style")
                                if (isAndroid12) add("dynamic_theming")
                            }
                            if (isDarkActive && !preferences.synthwaveMode && !preferences.soothingMode && !preferences.monochromeMode) add("oled_mode")
                            if (showAdvanced) {
                                add("contrast")
                                add("library_view_mode")
                                add("home_mode")
                                add("hero_section")
                                add("home_backdrop")
                                add("clock_home")
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
                    val totalCount = appearanceItems.size
                    var currentIdx = 0

                    appearanceItems.forEach { item ->
                        when (item) {
                            "theme_mode" -> {
                                val themeTitle = stringResource(R.string.settings_theme_mode)
                                val themeFollowSystem = stringResource(R.string.settings_theme_follow_system)
                                val themeAlwaysLight = stringResource(R.string.settings_theme_always_light)
                                val themeAlwaysDark = stringResource(R.string.settings_theme_always_dark)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = themeTitle,
                                    subtitle = if (preferences.synthwaveMode) {
                                        stringResource(R.string.settings_overridden_synthwave)
                                    } else if (preferences.soothingMode) {
                                        stringResource(R.string.settings_overridden_soothing)
                                    } else if (preferences.monochromeMode) {
                                        stringResource(R.string.settings_overridden_monochrome)
                                    } else {
                                        when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> themeFollowSystem
                                            ThemeMode.LIGHT -> themeAlwaysLight
                                            ThemeMode.DARK -> themeAlwaysDark
                                            ThemeMode.SCHEDULED -> stringResource(R.string.settings_theme_scheduled, preferences.scheduledThemeStartHour, preferences.scheduledThemeEndHour)
                                        }
                                    },
                                    trailingText = if (preferences.synthwaveMode) "-" else if (preferences.soothingMode) "-" else if (preferences.monochromeMode) "-" else preferences.themeMode.name,
                                    highlighted = highlightSettingId in THEME_HIGHLIGHT_IDS,
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        if (!preferences.synthwaveMode && !preferences.soothingMode && !preferences.monochromeMode) {
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
                                                onSelect = { viewModel.setThemeMode(it) },
                                            )
                                        }
                                    },
                                )
                            }
                            "synthwave_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Palette,
                                    title = stringResource(R.string.settings_synthwave_mode),
                                    subtitle = stringResource(R.string.settings_synthwave_mode_subtitle),
                                    checked = preferences.synthwaveMode,
                                    highlighted = highlightSettingId == "synthwave_mode",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setSynthwaveMode(it) },
                                )
                            }
                            "synthwave_accent" -> {
                                com.raulshma.jellyplay.core.ui.components.SynthwaveAccentPicker(
                                    selectedAccent = preferences.synthwaveAccent,
                                    onAccentSelected = { viewModel.setSynthwaveAccent(it) },
                                )
                                currentIdx++
                            }
                            "soothing_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Palette,
                                    title = stringResource(R.string.settings_soothing_mode),
                                    subtitle = stringResource(R.string.settings_soothing_mode_subtitle),
                                    checked = preferences.soothingMode,
                                    highlighted = highlightSettingId == "soothing_mode",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setSoothingMode(it) },
                                )
                            }
                            "soothing_accent" -> {
                                com.raulshma.jellyplay.core.ui.components.SoothingAccentPicker(
                                    selectedAccent = preferences.soothingAccent,
                                    onAccentSelected = { viewModel.setSoothingAccent(it) },
                                )
                                currentIdx++
                            }
                            "monochrome_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Palette,
                                    title = stringResource(R.string.settings_monochrome_mode),
                                    subtitle = stringResource(R.string.settings_monochrome_mode_subtitle),
                                    checked = preferences.monochromeMode,
                                    highlighted = highlightSettingId == "monochrome_mode",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setMonochromeMode(it) },
                                )
                            }
                            "accent_color" -> {
                                com.raulshma.jellyplay.core.ui.components.AccentColorPicker(
                                    selectedSwatch = preferences.accentColorSwatch,
                                    onSwatchSelected = { viewModel.setAccentColorSwatch(it) },
                                )
                                currentIdx++
                            }
                            "color_style" -> {
                                com.raulshma.jellyplay.core.ui.components.ColorStylePicker(
                                    selectedStyle = preferences.colorStyle,
                                    onStyleSelected = { viewModel.setColorStyle(it) },
                                )
                                currentIdx++
                            }
                            "dynamic_theming" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Video,
                                    title = stringResource(R.string.settings_dynamic_theming),
                                    subtitle = stringResource(R.string.settings_dynamic_theming_subtitle),
                                    checked = preferences.dynamicTheming,
                                    highlighted = highlightSettingId == "dynamic_theming",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                                )
                            }
                            "oled_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.BrightnessHalf,
                                    title = stringResource(R.string.settings_oled_mode),
                                    subtitle = stringResource(R.string.settings_oled_mode_subtitle),
                                    checked = preferences.oledMode,
                                    highlighted = highlightSettingId == "oled_mode",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setOledMode(it) },
                                )
                            }
                            "contrast" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = stringResource(R.string.settings_contrast),
                                    subtitle = when (preferences.contrastLevel) {
                                        ContrastLevel.DEFAULT -> stringResource(R.string.settings_contrast_standard)
                                        ContrastLevel.MEDIUM -> stringResource(R.string.settings_contrast_medium)
                                        ContrastLevel.HIGH -> stringResource(R.string.settings_contrast_high)
                                    },
                                    trailingText = preferences.contrastLevel.name,
                                    highlighted = highlightSettingId == "contrast",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = when (preferences.contrastLevel) {
                                            ContrastLevel.DEFAULT -> ContrastLevel.MEDIUM
                                            ContrastLevel.MEDIUM -> ContrastLevel.HIGH
                                            ContrastLevel.HIGH -> ContrastLevel.DEFAULT
                                        }
                                        viewModel.setContrastLevel(next)
                                    },
                                )
                            }
                            "library_view_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.LayoutGrid,
                                    title = stringResource(R.string.settings_library_view_mode),
                                    subtitle = when (preferences.libraryViewMode) {
                                        LibraryViewMode.GRID -> stringResource(R.string.settings_library_view_grid)
                                        LibraryViewMode.LIST -> stringResource(R.string.settings_library_view_list)
                                        LibraryViewMode.THUMB -> stringResource(R.string.settings_library_view_thumb)
                                        LibraryViewMode.MASONRY -> stringResource(R.string.settings_library_view_masonry)
                                    },
                                    trailingText = preferences.libraryViewMode.name,
                                    highlighted = highlightSettingId == "library_view_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        viewModel.setLibraryViewMode(preferences.libraryViewMode.next)
                                    },
                                )
                            }
                            "home_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = stringResource(R.string.settings_home_mode),
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) stringResource(R.string.settings_home_mode_video) else stringResource(R.string.settings_home_mode_music),
                                    trailingText = preferences.homeMode.name,
                                    highlighted = highlightSettingId == "home_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                        viewModel.setHomeMode(next)
                                    },
                                )
                            }
                            "hero_section" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = stringResource(R.string.settings_show_hero_section),
                                    subtitle = if (preferences.homeHeroEnabled) stringResource(R.string.settings_show_hero_on) else stringResource(R.string.settings_show_hero_off),
                                    checked = preferences.homeHeroEnabled,
                                    highlighted = highlightSettingId == "hero_section",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setHomeHeroEnabled(it) },
                                )
                            }
                            "home_backdrop" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Background,
                                    title = stringResource(R.string.settings_home_backdrop),
                                    subtitle = if (preferences.homeBackdropEnabled) stringResource(R.string.settings_home_backdrop_on) else stringResource(R.string.settings_home_backdrop_off),
                                    checked = preferences.homeBackdropEnabled,
                                    highlighted = highlightSettingId == "home_backdrop",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setHomeBackdropEnabled(it) },
                                )
                            }
                            "clock_home" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Clock,
                                    title = stringResource(R.string.settings_show_clock_home),
                                    subtitle = if (preferences.showClockOnHome) stringResource(R.string.settings_show_clock_on) else stringResource(R.string.settings_show_clock_off),
                                    checked = preferences.showClockOnHome,
                                    highlighted = highlightSettingId == "clock_home",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setShowClockOnHome(it) },
                                )
                            }
                            "settings_in_home_search" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = stringResource(R.string.settings_show_settings_in_home_search),
                                    subtitle = if (preferences.showSettingsInHomeSearch) stringResource(R.string.settings_show_settings_in_home_search_on) else stringResource(R.string.settings_show_settings_in_home_search_off),
                                    checked = preferences.showSettingsInHomeSearch,
                                    highlighted = highlightSettingId == "settings_in_home_search",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setShowSettingsInHomeSearch(it) },
                                )
                            }
                            "continue_watching_click" -> {
                                val cwTitle = stringResource(R.string.settings_continue_watching_tap)
                                SettingListItem(
                                    icon = Tabler.Outline.PlayerPlay,
                                    title = cwTitle,
                                    subtitle = stringResource(R.string.settings_continue_watching_tap_subtitle),
                                    trailingText = preferences.continueWatchingClickBehavior.displayName,
                                    highlighted = highlightSettingId == "continue_watching_click",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = cwTitle,
                                            items = com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.continueWatchingClickBehavior },
                                            onSelect = { viewModel.setContinueWatchingClickBehavior(it) },
                                        )
                                    },
                                )
                            }
                            "unhide_cw" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = stringResource(R.string.settings_unhide_continue_watching),
                                    subtitle = stringResource(R.string.settings_unhide_continue_watching_subtitle, preferences.hiddenCwItemIds.size),
                                    highlighted = highlightSettingId == "unhide_cw",
                                    index = currentIdx++, count = totalCount,
                                    onClick = { viewModel.unhideAllCwItems() },
                                )
                            }
                            "merge_continue_next_up" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = stringResource(R.string.settings_merge_continue_next_up),
                                    subtitle = if (preferences.mergeContinueWatchingAndNextUp) stringResource(R.string.settings_merge_continue_next_up_on) else stringResource(R.string.settings_merge_continue_next_up_off),
                                    checked = preferences.mergeContinueWatchingAndNextUp,
                                    highlighted = highlightSettingId == "merge_continue_next_up",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setMergeContinueWatchingAndNextUp(it) },
                                )
                            }
                            "next_up_max_days" -> {
                                val nextUpTitle = stringResource(R.string.settings_next_up_time_window)
                                val unlimitedLabel = stringResource(R.string.settings_unlimited)
                                val xDaysFormat = stringResource(R.string.settings_x_days)
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
                                    subtitle = stringResource(R.string.settings_next_up_time_window_subtitle),
                                    trailingText = dayLabels[preferences.nextUpMaxDays] ?: xDaysFormat.format(preferences.nextUpMaxDays),
                                    highlighted = highlightSettingId == "next_up_max_days",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = nextUpTitle,
                                            items = listOf(0, 7, 14, 30, 60, 90),
                                            label = { dayLabels[it] ?: xDaysFormat.format(it) },
                                            isSelected = { it == preferences.nextUpMaxDays },
                                            onSelect = { viewModel.setNextUpMaxDays(it) },
                                        )
                                    },
                                )
                            }
                            "next_up_rewatching" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.History,
                                    title = stringResource(R.string.settings_rewatching_next_up),
                                    subtitle = if (preferences.nextUpRewatching) stringResource(R.string.settings_rewatching_next_up_on) else stringResource(R.string.settings_rewatching_next_up_off),
                                    checked = preferences.nextUpRewatching,
                                    highlighted = highlightSettingId == "next_up_rewatching",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setNextUpRewatching(it) },
                                )
                            }
                            "theme_music" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Music,
                                    title = stringResource(R.string.settings_backdrop_theme_music),
                                    subtitle = if (preferences.backdropThemeMusicEnabled) stringResource(R.string.settings_backdrop_theme_music_on) else stringResource(R.string.settings_backdrop_theme_music_off),
                                    checked = preferences.backdropThemeMusicEnabled,
                                    highlighted = highlightSettingId == "theme_music",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setBackdropThemeMusicEnabled(it) },
                                )
                            }
                            "nav_labels" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = stringResource(R.string.settings_show_nav_labels),
                                    subtitle = if (preferences.navBarShowLabels) stringResource(R.string.settings_nav_labels_on) else stringResource(R.string.settings_nav_labels_off),
                                    checked = preferences.navBarShowLabels,
                                    highlighted = highlightSettingId == "nav_labels",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setNavBarShowLabels(it) },
                                )
                            }
                            "date_format" -> {
                                val dateFormatTitle = stringResource(R.string.settings_date_format)
                                SettingListItem(
                                    icon = Tabler.Outline.Calendar,
                                    title = dateFormatTitle,
                                    subtitle = stringResource(R.string.settings_date_format_subtitle),
                                    trailingText = preferences.dateFormatPreference.displayName,
                                    highlighted = highlightSettingId == "date_format",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = dateFormatTitle,
                                            items = DateFormatPreference.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.dateFormatPreference },
                                            onSelect = { viewModel.setDateFormatPreference(it) },
                                        )
                                    },
                                )
                            }
                            "font_scale" -> {
                                val fontSizeTitle = stringResource(R.string.settings_font_size_app)
                                SettingListItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = fontSizeTitle,
                                    subtitle = stringResource(R.string.settings_font_size_app_subtitle),
                                    trailingText = preferences.appFontScale.displayName,
                                    highlighted = highlightSettingId == "font_scale",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = fontSizeTitle,
                                            items = AppFontScale.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.appFontScale },
                                            onSelect = { viewModel.setAppFontScale(it) },
                                        )
                                    },
                                )
                            }
                            "scheduled_start" -> {
                                val nightStartsTitle = stringResource(R.string.settings_night_starts_at)
                                SettingListItem(
                                    icon = Tabler.Outline.Sun,
                                    title = nightStartsTitle,
                                    subtitle = stringResource(R.string.settings_night_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeStartHour}:00",
                                    highlighted = highlightSettingId == "scheduled_start",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = nightStartsTitle,
                                            items = (0..23).toList(),
                                            label = { "$it:00" },
                                            isSelected = { it == preferences.scheduledThemeStartHour },
                                            onSelect = { viewModel.setScheduledThemeStartHour(it) },
                                        )
                                    },
                                )
                            }
                            "scheduled_end" -> {
                                val morningStartsTitle = stringResource(R.string.settings_morning_starts_at)
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = morningStartsTitle,
                                    subtitle = stringResource(R.string.settings_morning_starts_at_subtitle),
                                    trailingText = "${preferences.scheduledThemeEndHour}:00",
                                    highlighted = highlightSettingId == "scheduled_end",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = morningStartsTitle,
                                            items = (0..23).toList(),
                                            label = { "$it:00" },
                                            isSelected = { it == preferences.scheduledThemeEndHour },
                                            onSelect = { viewModel.setScheduledThemeEndHour(it) },
                                        )
                                    },
                                )
                            }
                            "color_blind_mode" -> {
                                val colorBlindTitle = stringResource(R.string.settings_color_blind_mode)
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = colorBlindTitle,
                                    subtitle = stringResource(R.string.settings_color_blind_mode_subtitle),
                                    trailingText = preferences.colorBlindMode.displayName,
                                    highlighted = highlightSettingId == "color_blind_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = colorBlindTitle,
                                            items = com.raulshma.jellyplay.core.model.ColorBlindMode.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.colorBlindMode },
                                            onSelect = { viewModel.setColorBlindMode(it) },
                                        )
                                    },
                                )
                            }
                            "hand_mode" -> {
                                val handednessTitle = stringResource(R.string.settings_handedness)
                                SettingListItem(
                                    icon = Tabler.Outline.HandClick,
                                    title = handednessTitle,
                                    subtitle = stringResource(R.string.settings_handedness_subtitle),
                                    trailingText = preferences.handMode.displayName,
                                    highlighted = highlightSettingId == "hand_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = handednessTitle,
                                            items = com.raulshma.jellyplay.core.model.HandMode.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.handMode },
                                            onSelect = { viewModel.setHandMode(it) },
                                        )
                                    },
                                )
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
                    title = stringResource(R.string.settings_library_cards),
                    summary = {
                        val unwatched = if (preferences.showUnwatchedBadge) stringResource(R.string.settings_summary_unwatched_badges) else null
                        val checkmarks = if (preferences.showWatchedCheckmark) stringResource(R.string.settings_summary_watched_checkmarks) else null
                        val hideWatched = if (preferences.hideWatchedItems) stringResource(R.string.settings_summary_hide_watched) else null
                        val hideThumbnails = if (preferences.hideEpisodeThumbnails) stringResource(R.string.settings_summary_hide_thumbnails) else null
                        val skipSpecials = if (preferences.skipSpecials) stringResource(R.string.settings_summary_skip_specials) else null
                        val shareOpt = if (preferences.showShareMediaOption) stringResource(R.string.settings_summary_share_button) else null
                        val ratingsOpt = if (preferences.showExternalRatings) stringResource(R.string.settings_summary_external_ratings) else null
                        listOfNotNull(unwatched, checkmarks, hideWatched, hideThumbnails, skipSpecials, shareOpt, ratingsOpt).joinToString(", ").ifEmpty { stringResource(R.string.settings_summary_all_hidden) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in APPEARANCE_LIBRARY_GROUP_IDS,
                ) {
                    val cardTotal = 8
                    var cardIdx = 0

                    SettingToggleItem(
                        icon = Tabler.Outline.Folder,
                        title = stringResource(R.string.settings_show_unwatched_badge),
                        subtitle = stringResource(R.string.settings_show_unwatched_badge_subtitle),
                        checked = preferences.showUnwatchedBadge,
                        highlighted = highlightSettingId == "show_unwatched_badge",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowUnwatchedBadge(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = stringResource(R.string.settings_show_watched_checkmark),
                        subtitle = stringResource(R.string.settings_show_watched_checkmark_subtitle),
                        checked = preferences.showWatchedCheckmark,
                        highlighted = highlightSettingId == "show_watched_checkmark",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowWatchedCheckmark(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = stringResource(R.string.settings_hide_watched_items),
                        subtitle = stringResource(R.string.settings_hide_watched_items_subtitle),
                        checked = preferences.hideWatchedItems,
                        highlighted = highlightSettingId == "hide_watched_items",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setHideWatchedItems(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PhotoOff,
                        title = stringResource(R.string.settings_hide_episode_thumbnails),
                        subtitle = stringResource(R.string.settings_hide_episode_thumbnails_subtitle),
                        checked = preferences.hideEpisodeThumbnails,
                        highlighted = highlightSettingId == "hide_episode_thumbnails",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setHideEpisodeThumbnails(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.List,
                        title = stringResource(R.string.settings_compact_episode_list),
                        subtitle = stringResource(R.string.settings_compact_episode_list_subtitle),
                        checked = preferences.compactEpisodeList,
                        highlighted = highlightSettingId == "compact_episode_list",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setCompactEpisodeList(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.PlayerSkipForward,
                        title = stringResource(R.string.settings_skip_special_episodes),
                        subtitle = stringResource(R.string.settings_skip_special_episodes_subtitle),
                        checked = preferences.skipSpecials,
                        highlighted = highlightSettingId == "skip_specials",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setSkipSpecials(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.DeviceMobileVibration,
                        title = stringResource(R.string.settings_haptic_feedback),
                        subtitle = stringResource(R.string.settings_haptic_feedback_subtitle),
                        checked = preferences.hapticsEnabled,
                        highlighted = highlightSettingId == "haptics_enabled",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Share,
                        title = stringResource(R.string.settings_show_share_media),
                        subtitle = stringResource(R.string.settings_show_share_media_subtitle),
                        checked = preferences.showShareMediaOption,
                        highlighted = highlightSettingId == "show_share_media",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowShareMediaOption(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = stringResource(R.string.settings_hide_search_history),
                        subtitle = stringResource(R.string.settings_hide_search_history_subtitle),
                        checked = preferences.hideSearchHistory,
                        highlighted = highlightSettingId == "hide_search_history",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setHideSearchHistory(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Star,
                        title = stringResource(R.string.settings_show_external_ratings),
                        subtitle = stringResource(R.string.settings_show_external_ratings_subtitle),
                        checked = preferences.showExternalRatings,
                        highlighted = highlightSettingId == "show_external_ratings",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowExternalRatings(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Home,
                    title = stringResource(R.string.settings_home_screen_layout),
                    summary = {
                        val enabled = preferences.enabledHomeSectionTypes
                        stringResource(R.string.settings_home_sections_visible, enabled.size, HomeSectionType.CONFIGURABLE.size)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in homeLayoutGroup,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Pinned,
                        title = stringResource(R.string.settings_pinned_home_sections),
                        subtitle = stringResource(R.string.settings_pinned_home_sections_brief),
                        trailingText = if (preferences.pinnedHomeSections.isEmpty()) "" else "${preferences.pinnedHomeSections.size}",
                        highlighted = highlightSettingId == "pinned_home_sections",
                        index = 0, count = 1,
                        onClick = { onPinnedHomeSections(if (highlightSettingId == "pinned_home_sections") "pinned_add" else null) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Bookmarks,
                        title = stringResource(R.string.settings_home_layout_presets),
                        subtitle = stringResource(R.string.settings_home_layout_presets_brief),
                        trailingText = if (preferences.homeLayoutPresets.isEmpty()) "" else "${preferences.homeLayoutPresets.size}",
                        highlighted = highlightSettingId == "home_layout_presets",
                        index = 0, count = 1,
                        onClick = { onHomeLayoutPresets(if (highlightSettingId == "home_layout_presets") "preset_list" else null) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Folders,
                        title = stringResource(R.string.settings_configure_libraries),
                        subtitle = stringResource(R.string.settings_configure_libraries_desc),
                        trailingText = "",
                        highlighted = highlightSettingId == "configure_libraries",
                        index = 0, count = 1,
                        onClick = { onConfigureLibraries(if (highlightSettingId == "configure_libraries") "configure_libraries" else null) },
                    )

                    val homeSectionOrder = remember { mutableStateListOf<HomeSectionType>().apply { addAll(preferences.homeSectionOrder) } }
                    val itemHeights = remember { mutableStateMapOf<HomeSectionType, Int>() }
                    var draggingSection by remember { mutableStateOf<HomeSectionType?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(preferences.homeSectionOrder) {
                        if (draggingSection == null) {
                            homeSectionOrder.clear()
                            homeSectionOrder.addAll(preferences.homeSectionOrder)
                        }
                    }

                    fun persistHomeSectionOrder() {
                        val currentOrder = homeSectionOrder.toList()
                        if (currentOrder != preferences.homeSectionOrder) {
                            viewModel.setHomeSectionOrder(currentOrder)
                        }
                    }

                    fun moveSection(type: HomeSectionType, deltaY: Float) {
                        if (draggingSection != type) return
                        dragOffsetY += deltaY

                        while (true) {
                            val currentIndex = homeSectionOrder.indexOf(type)
                            if (currentIndex == -1) return

                            val draggedHeight = itemHeights[type] ?: return

                            if (dragOffsetY > 0f && currentIndex < homeSectionOrder.lastIndex) {
                                val nextType = homeSectionOrder[currentIndex + 1]
                                val nextHeight = itemHeights[nextType] ?: draggedHeight
                                val threshold = (draggedHeight + nextHeight) / 2f
                                if (dragOffsetY > threshold) {
                                    homeSectionOrder.removeAt(currentIndex)
                                    homeSectionOrder.add(currentIndex + 1, type)
                                    dragOffsetY -= nextHeight.toFloat()
                                    continue
                                }
                            }

                            if (dragOffsetY < 0f && currentIndex > 0) {
                                val prevType = homeSectionOrder[currentIndex - 1]
                                val prevHeight = itemHeights[prevType] ?: draggedHeight
                                val threshold = (draggedHeight + prevHeight) / 2f
                                if (-dragOffsetY > threshold) {
                                    homeSectionOrder.removeAt(currentIndex)
                                    homeSectionOrder.add(currentIndex - 1, type)
                                    dragOffsetY += prevHeight.toFloat()
                                    continue
                                }
                            }
                            break
                        }
                    }

                    homeSectionOrder.forEachIndexed { index, sectionType ->
                        val enabled = sectionType in preferences.enabledHomeSectionTypes
                        SettingReorderableToggleItem(
                            icon = when (sectionType) {
                                HomeSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
                                HomeSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
                                HomeSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
                                HomeSectionType.LATEST_MEDIA -> Tabler.Outline.LayersLinked
                                else -> Tabler.Outline.Folder
                            },
                            title = sectionType.displayName,
                            subtitle = sectionType.description,
                            checked = enabled,
                            index = index,
                            count = homeSectionOrder.size,
                            modifier = Modifier.onSizeChanged { itemHeights[sectionType] = it.height },
                            onCheckedChange = { checked ->
                                val current = preferences.enabledHomeSectionTypes.toMutableSet()
                                if (checked) current.add(sectionType) else current.remove(sectionType)
                                viewModel.setEnabledHomeSectionTypes(current)
                            },
                            onDrag = { delta -> moveSection(sectionType, delta) },
                            onDragStart = { draggingSection = sectionType; dragOffsetY = 0f },
                            onDragEnd = { draggingSection = null; persistHomeSectionOrder() },
                        )
                    }
                }
            }

            // Remaining groups are expert-level; keep them behind the Advanced gate.
            if (showAdvanced) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = stringResource(R.string.settings_performance),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.performanceMode) parts.add(stringResource(R.string.settings_performance_mode))
                        if (preferences.reduceMotionEnabled) parts.add(stringResource(R.string.settings_reduced_motion))
                        parts.joinToString(", ").ifEmpty { stringResource(R.string.settings_standard_experience) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in PERFORMANCE_GROUP_IDS,
                ) {
                    val perfTotal = 2
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = stringResource(R.string.settings_performance_mode),
                        subtitle = stringResource(R.string.settings_performance_mode_subtitle),
                        checked = preferences.performanceMode,
                        highlighted = highlightSettingId == "performance_mode",
                        index = 0, count = perfTotal,
                        onCheckedChange = { viewModel.setPerformanceMode(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Activity,
                        title = stringResource(R.string.settings_reduce_motion),
                        subtitle = stringResource(R.string.settings_reduce_motion_subtitle),
                        checked = preferences.reduceMotionEnabled,
                        highlighted = highlightSettingId == "reduce_motion",
                        index = 1, count = perfTotal,
                        onCheckedChange = { viewModel.setReduceMotionEnabled(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Eye,
                    title = stringResource(R.string.settings_comfort_eye_care),
                    summary = {
                        if (preferences.blueLightFilterEnabled) {
                            stringResource(R.string.settings_blue_light_filter_summary, (preferences.blueLightFilterStrength * 100).toInt())
                        } else {
                            stringResource(R.string.settings_off)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in BLUE_LIGHT_GROUP_IDS,
                ) {
                    val eyeCareTotal = 2
                    SettingToggleItem(
                        icon = Tabler.Outline.Moon,
                        title = stringResource(R.string.settings_blue_light_filter),
                        subtitle = stringResource(R.string.settings_blue_light_filter_subtitle),
                        checked = preferences.blueLightFilterEnabled,
                        highlighted = highlightSettingId == "blue_light_filter",
                        index = 0, count = eyeCareTotal,
                        onCheckedChange = { viewModel.setBlueLightFilterEnabled(it) },
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Adjustments,
                        title = stringResource(R.string.settings_blue_light_filter_strength),
                        subtitle = stringResource(R.string.settings_blue_light_filter_strength_subtitle),
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
                    title = stringResource(R.string.settings_newsletter_config),
                    summary = {
                        val enabled = preferences.enabledNewsletterSections
                        if (preferences.newsletterEnabled) {
                            stringResource(R.string.settings_newsletter_sections_enabled, enabled.size, NewsletterSectionType.entries.size)
                        } else {
                            stringResource(R.string.settings_disabled)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in NEWSLETTER_GROUP_IDS,
                ) {
                    val newsletterSections = remember { mutableStateListOf<NewsletterSectionType>().apply { addAll(preferences.newsletterSectionOrder) } }
                    val itemHeights = remember { mutableStateMapOf<NewsletterSectionType, Int>() }
                    var draggingSection by remember { mutableStateOf<NewsletterSectionType?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(preferences.newsletterSectionOrder) {
                        if (draggingSection == null) {
                            newsletterSections.clear()
                            newsletterSections.addAll(preferences.newsletterSectionOrder)
                        }
                    }

                    fun persistNewsletterSectionOrder() {
                        val currentOrder = newsletterSections.toList()
                        if (currentOrder != preferences.newsletterSectionOrder) {
                            viewModel.setNewsletterSectionOrder(currentOrder)
                        }
                    }

                    fun moveSection(type: NewsletterSectionType, deltaY: Float) {
                        if (draggingSection != type) return
                        dragOffsetY += deltaY

                        while (true) {
                            val currentIndex = newsletterSections.indexOf(type)
                            if (currentIndex == -1) return

                            val draggedHeight = itemHeights[type] ?: return

                            if (dragOffsetY > 0f && currentIndex < newsletterSections.lastIndex) {
                                val nextType = newsletterSections[currentIndex + 1]
                                val nextHeight = itemHeights[nextType] ?: draggedHeight
                                val threshold = (draggedHeight + nextHeight) / 2f
                                if (dragOffsetY > threshold) {
                                    newsletterSections.removeAt(currentIndex)
                                    newsletterSections.add(currentIndex + 1, type)
                                    dragOffsetY -= nextHeight.toFloat()
                                    continue
                                }
                            }

                            if (dragOffsetY < 0f && currentIndex > 0) {
                                val prevType = newsletterSections[currentIndex - 1]
                                val prevHeight = itemHeights[prevType] ?: draggedHeight
                                val threshold = (draggedHeight + prevHeight) / 2f
                                if (-dragOffsetY > threshold) {
                                    newsletterSections.removeAt(currentIndex)
                                    newsletterSections.add(currentIndex - 1, type)
                                    dragOffsetY += prevHeight.toFloat()
                                    continue
                                }
                            }
                            break
                        }
                    }

                    val totalCount = newsletterSections.size + 2

                    SettingToggleItem(
                        icon = Tabler.Outline.Mail,
                        title = stringResource(R.string.settings_enable_newsletter),
                        subtitle = stringResource(R.string.settings_enable_newsletter_subtitle),
                        checked = preferences.newsletterEnabled,
                        highlighted = highlightSettingId == "newsletter_enabled",
                        index = 0,
                        count = totalCount,
                        onCheckedChange = { viewModel.setNewsletterEnabled(it) }
                    )

                    val daysOfWeek = listOf(
                        java.util.Calendar.MONDAY to stringResource(R.string.settings_day_monday),
                        java.util.Calendar.TUESDAY to stringResource(R.string.settings_day_tuesday),
                        java.util.Calendar.WEDNESDAY to stringResource(R.string.settings_day_wednesday),
                        java.util.Calendar.THURSDAY to stringResource(R.string.settings_day_thursday),
                        java.util.Calendar.FRIDAY to stringResource(R.string.settings_day_friday),
                        java.util.Calendar.SATURDAY to stringResource(R.string.settings_day_saturday),
                        java.util.Calendar.SUNDAY to stringResource(R.string.settings_day_sunday),
                    )
                    val dayLabel = daysOfWeek.find { it.first == preferences.newsletterDayOfWeek }?.second ?: stringResource(R.string.settings_day_saturday)

                    SettingListItem(
                        icon = Tabler.Outline.Calendar,
                        title = stringResource(R.string.settings_newsletter_delivery_day),
                        subtitle = stringResource(R.string.settings_newsletter_delivery_day_subtitle),
                        trailingText = dayLabel,
                        highlighted = highlightSettingId == "newsletter_delivery_day",
                        index = 1,
                        count = totalCount,
                        onClick = {
                            val currentIdx = daysOfWeek.indexOfFirst { it.first == preferences.newsletterDayOfWeek }
                            val nextIdx = (currentIdx + 1) % daysOfWeek.size
                            viewModel.setNewsletterDayOfWeek(daysOfWeek[nextIdx].first)
                        }
                    )

                    if (preferences.newsletterEnabled) {
                        newsletterSections.forEachIndexed { index, sectionType ->
                            val enabled = sectionType in preferences.enabledNewsletterSections
                            val displayName = when (sectionType) {
                                NewsletterSectionType.RECENTLY_ADDED -> stringResource(R.string.settings_newsletter_recently_added)
                                NewsletterSectionType.ACTIVITY_DIGEST -> stringResource(R.string.settings_newsletter_activity_log)
                                NewsletterSectionType.LIBRARY_STATS -> stringResource(R.string.settings_newsletter_library_stats)
                                NewsletterSectionType.CONTINUE_WATCHING -> stringResource(R.string.settings_newsletter_continue_watching)
                                NewsletterSectionType.NEXT_UP -> stringResource(R.string.settings_newsletter_next_up)
                                NewsletterSectionType.CURATED_PICKS -> stringResource(R.string.settings_newsletter_curated_picks)
                            }
                            val sectionDesc = when (sectionType) {
                                NewsletterSectionType.RECENTLY_ADDED -> stringResource(R.string.settings_newsletter_recently_added_desc)
                                NewsletterSectionType.ACTIVITY_DIGEST -> stringResource(R.string.settings_newsletter_activity_log_desc)
                                NewsletterSectionType.LIBRARY_STATS -> stringResource(R.string.settings_newsletter_library_stats_desc)
                                NewsletterSectionType.CONTINUE_WATCHING -> stringResource(R.string.settings_newsletter_continue_watching_desc)
                                NewsletterSectionType.NEXT_UP -> stringResource(R.string.settings_newsletter_next_up_desc)
                                NewsletterSectionType.CURATED_PICKS -> stringResource(R.string.settings_newsletter_curated_picks_desc)
                            }

                            SettingReorderableToggleItem(
                                icon = when (sectionType) {
                                    NewsletterSectionType.CONTINUE_WATCHING -> Tabler.Outline.PlayerPlay
                                    NewsletterSectionType.NEXT_UP -> Tabler.Outline.PlayerSkipForward
                                    NewsletterSectionType.RECENTLY_ADDED -> Tabler.Outline.Clock
                                    NewsletterSectionType.LIBRARY_STATS -> Tabler.Outline.LayersLinked
                                    NewsletterSectionType.CURATED_PICKS -> Tabler.Outline.Wand
                                    NewsletterSectionType.ACTIVITY_DIGEST -> Tabler.Outline.Folder
                                },
                                title = displayName,
                                subtitle = sectionDesc,
                                checked = enabled,
                                index = index + 2,
                                count = totalCount,
                                modifier = Modifier.onSizeChanged { itemHeights[sectionType] = it.height },
                                onCheckedChange = { checked ->
                                    val current = preferences.enabledNewsletterSections.toMutableSet()
                                    if (checked) current.add(sectionType) else current.remove(sectionType)
                                    viewModel.setEnabledNewsletterSections(current)
                                },
                                onDrag = { delta -> moveSection(sectionType, delta) },
                                onDragStart = { draggingSection = sectionType; dragOffsetY = 0f },
                                onDragEnd = { draggingSection = null; persistNewsletterSectionOrder() },
                            )
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
            title = stringResource(R.string.settings_blue_light_filter_strength),
            value = preferences.blueLightFilterStrength,
            valueRange = 0.1f..1f,
            steps = 8,
            valueLabel = { "${(it * 100).toInt()}%" },
            rangeStartLabel = "10%",
            rangeEndLabel = "100%",
            onDismiss = { showBlueLightStrengthSheet = false },
            onConfirm = {
                viewModel.setBlueLightFilterStrength(it)
                showBlueLightStrengthSheet = false
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_appearance_title)) },
            text = { Text(stringResource(R.string.settings_reset_appearance_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetCategory(PreferenceResetCategory.APPEARANCE)
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.settings_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
