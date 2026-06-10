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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.raulshma.jellyplay.core.model.ContrastLevel
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val showAdvanced = preferences.showAdvancedSettings
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val scrollState = rememberLazyListState()
    val scrollIndex = remember(highlightSettingId) {
        when (highlightSettingId) {
            in listOf("theme_mode", "synthwave_mode", "soothing_mode", "dynamic_theming", "oled_mode", "contrast", "library_view_mode", "home_mode", "hero_section", "nav_labels") -> 0
            in listOf("show_unwatched_badge", "show_watched_checkmark", "hide_watched_items", "show_share_media", "show_external_ratings") -> 1
            in listOf("performance_mode", "reduce_motion") -> 2
            else -> -1
        }
    }

    LaunchedEffect(scrollIndex) {
        if (scrollIndex >= 0) {
            try {
                scrollState.animateScrollToItem(scrollIndex)
            } catch (_: Exception) {}
        }
    }

    JellyPlayScreenScaffold(
        title = "Appearance",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            AdvancedSettingsToggleButton(
                showAdvanced = showAdvanced,
                onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = adaptiveInfo.contentPadding(isTv),
                end = adaptiveInfo.contentPadding(isTv),
                bottom = adaptiveInfo.bottomPadding(isTv),
            ),
        ) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.Palette,
                    title = "Theme",
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.synthwaveMode) {
                            parts.add("Synthwave (${preferences.synthwaveAccent.lowercase().replaceFirstChar { it.uppercase() }})")
                        } else if (preferences.soothingMode) {
                            parts.add("Soothing (${preferences.soothingAccent.lowercase().replaceFirstChar { it.uppercase() }})")
                        } else {
                            parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                            val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
                            parts.add("$accentName accent")
                            parts.add(preferences.colorStyle.displayName)
                            if (preferences.dynamicTheming) parts.add("Artwork dynamic")
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
                    }

                    val appearanceItems = buildList {
                        add("theme_mode")
                        add("synthwave_mode")
                        if (preferences.synthwaveMode) {
                            add("synthwave_accent")
                        }
                        add("soothing_mode")
                        if (preferences.soothingMode) {
                            add("soothing_accent")
                        }
                        if (!preferences.synthwaveMode && !preferences.soothingMode) {
                            add("accent_color")
                            add("color_style")
                            if (isAndroid12) add("dynamic_theming")
                        }
                        if (isDarkActive && !preferences.synthwaveMode && !preferences.soothingMode) add("oled_mode")
                        if (showAdvanced) {
                            add("contrast")
                            add("library_view_mode")
                            add("home_mode")
                            add("hero_section")
                            add("nav_labels")
                        }
                    }
                    val totalCount = appearanceItems.size
                    var currentIdx = 0

                    appearanceItems.forEach { item ->
                        when (item) {
                            "theme_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = "Theme Mode",
                                    subtitle = if (preferences.synthwaveMode) {
                                        "Overridden by Synthwave Mode"
                                    } else if (preferences.soothingMode) {
                                        "Overridden by Soothing Mode"
                                    } else {
                                        when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> "Follow system setting"
                                            ThemeMode.LIGHT -> "Always light"
                                            ThemeMode.DARK -> "Always dark"
                                        }
                                    },
                                    trailingText = if (preferences.synthwaveMode) "-" else if (preferences.soothingMode) "-" else preferences.themeMode.name,
                                    highlighted = highlightSettingId == "theme_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        if (!preferences.synthwaveMode && !preferences.soothingMode) {
                                            val next = when (preferences.themeMode) {
                                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                                ThemeMode.LIGHT -> ThemeMode.DARK
                                                ThemeMode.DARK -> ThemeMode.SYSTEM
                                            }
                                            viewModel.setThemeMode(next)
                                        }
                                    },
                                )
                            }
                            "synthwave_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Palette,
                                    title = "Synthwave Mode",
                                    subtitle = "Apply retro-futuristic neon theme with sharp corners",
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
                                    title = "Soothing Mode",
                                    subtitle = "Apply a calm, Facebook-inspired theme with soft rounded corners",
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
                                    title = "Dynamic Theming",
                                    subtitle = "Colors extracted from artwork",
                                    checked = preferences.dynamicTheming,
                                    highlighted = highlightSettingId == "dynamic_theming",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setDynamicTheming(it) },
                                )
                            }
                            "oled_mode" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.BrightnessHalf,
                                    title = "OLED Mode",
                                    subtitle = "Pure black backgrounds for AMOLED displays",
                                    checked = preferences.oledMode,
                                    highlighted = highlightSettingId == "oled_mode",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setOledMode(it) },
                                )
                            }
                            "contrast" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = "Contrast",
                                    subtitle = when (preferences.contrastLevel) {
                                        ContrastLevel.DEFAULT -> "Standard contrast"
                                        ContrastLevel.MEDIUM -> "Medium contrast"
                                        ContrastLevel.HIGH -> "High contrast"
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
                                    title = "Library View Mode",
                                    subtitle = when (preferences.libraryViewMode) {
                                        LibraryViewMode.GRID -> "Display items in a grid layout"
                                        LibraryViewMode.LIST -> "Display items in a list layout"
                                    },
                                    trailingText = preferences.libraryViewMode.name,
                                    highlighted = highlightSettingId == "library_view_mode",
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = when (preferences.libraryViewMode) {
                                            LibraryViewMode.GRID -> LibraryViewMode.LIST
                                            LibraryViewMode.LIST -> LibraryViewMode.GRID
                                        }
                                        viewModel.setLibraryViewMode(next)
                                    },
                                )
                            }
                            "home_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = "Home Mode",
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
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
                                    title = "Show Hero Section",
                                    subtitle = if (preferences.homeHeroEnabled) "Featured content banner on home" else "Compact home layout",
                                    checked = preferences.homeHeroEnabled,
                                    highlighted = highlightSettingId == "hero_section",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setHomeHeroEnabled(it) },
                                )
                            }
                            "nav_labels" -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.TextSize,
                                    title = "Show Navigation Labels",
                                    subtitle = if (preferences.navBarShowLabels) "Icons and text" else "Icons only",
                                    checked = preferences.navBarShowLabels,
                                    highlighted = highlightSettingId == "nav_labels",
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setNavBarShowLabels(it) },
                                )
                            }
                        }
                    }
                }
            }

            if (showAdvanced) {
            item {
                SettingsGroup(
                    icon = Tabler.Outline.LayoutGrid,
                    title = "Library & Cards",
                    summary = {
                        val unwatched = if (preferences.showUnwatchedBadge) "Unwatched badges" else null
                        val checkmarks = if (preferences.showWatchedCheckmark) "Watched checkmarks" else null
                        val hideWatched = if (preferences.hideWatchedItems) "Hide watched" else null
                        val shareOpt = if (preferences.showShareMediaOption) "Share button" else null
                        val ratingsOpt = if (preferences.showExternalRatings) "External ratings" else null
                        listOfNotNull(unwatched, checkmarks, hideWatched, shareOpt, ratingsOpt).joinToString(", ").ifEmpty { "All badges/checkmarks hidden" }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in listOf("show_unwatched_badge", "show_watched_checkmark", "hide_watched_items", "show_share_media", "show_external_ratings"),
                ) {
                    val cardTotal = 5
                    var cardIdx = 0

                    SettingToggleItem(
                        icon = Tabler.Outline.Folder,
                        title = "Show Unwatched Badge",
                        subtitle = "Overlay a badge on items that are unwatched",
                        checked = preferences.showUnwatchedBadge,
                        highlighted = highlightSettingId == "show_unwatched_badge",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowUnwatchedBadge(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.CircleCheck,
                        title = "Show Watched Checkmark",
                        subtitle = "Overlay a checkmark badge on card views",
                        checked = preferences.showWatchedCheckmark,
                        highlighted = highlightSettingId == "show_watched_checkmark",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowWatchedCheckmark(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.EyeOff,
                        title = "Hide Watched Items",
                        subtitle = "Default-filter out watched media from libraries",
                        checked = preferences.hideWatchedItems,
                        highlighted = highlightSettingId == "hide_watched_items",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setHideWatchedItems(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Share,
                        title = "Show Share Media Option",
                        subtitle = "Show share option button on details pages",
                        checked = preferences.showShareMediaOption,
                        highlighted = highlightSettingId == "show_share_media",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowShareMediaOption(it) },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Star,
                        title = "Show External Ratings",
                        subtitle = "Display critic rating scores (IMDb/TMDB) on details pages",
                        checked = preferences.showExternalRatings,
                        highlighted = highlightSettingId == "show_external_ratings",
                        index = cardIdx++, count = cardTotal,
                        onCheckedChange = { viewModel.setShowExternalRatings(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = "Performance",
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.performanceMode) parts.add("Performance Mode")
                        if (preferences.reduceMotionEnabled) parts.add("Reduced motion")
                        parts.joinToString(", ").ifEmpty { "Standard experience" }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in listOf("performance_mode", "reduce_motion"),
                ) {
                    val perfTotal = 2
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Performance Mode",
                        subtitle = "Reduces animations and effects for better performance on lower-end devices",
                        checked = preferences.performanceMode,
                        highlighted = highlightSettingId == "performance_mode",
                        index = 0, count = perfTotal,
                        onCheckedChange = { viewModel.setPerformanceMode(it) },
                    )
                    SettingToggleItem(
                        icon = Tabler.Outline.Activity,
                        title = "Reduce Motion",
                        subtitle = "Disable heavy parallax and card animations",
                        checked = preferences.reduceMotionEnabled,
                        highlighted = highlightSettingId == "reduce_motion",
                        index = 1, count = perfTotal,
                        onCheckedChange = { viewModel.setReduceMotionEnabled(it) },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Home,
                    title = "Home Screen Layout",
                    summary = {
                        val enabled = preferences.enabledHomeSectionTypes
                        "${enabled.size} of ${HomeSectionType.CONFIGURABLE.size} sections visible"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
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

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Mail,
                    title = "Newsletter Layout & Config",
                    summary = {
                        val enabled = preferences.enabledNewsletterSections
                        if (preferences.newsletterEnabled) {
                            "${enabled.size} of ${NewsletterSectionType.entries.size} sections enabled"
                        } else {
                            "Disabled"
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
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
                        title = "Enable Newsletter",
                        subtitle = "Enable periodic newsletter digest",
                        checked = preferences.newsletterEnabled,
                        index = 0,
                        count = totalCount,
                        onCheckedChange = { viewModel.setNewsletterEnabled(it) }
                    )

                    val daysOfWeek = listOf(
                        java.util.Calendar.MONDAY to "Monday",
                        java.util.Calendar.TUESDAY to "Tuesday",
                        java.util.Calendar.WEDNESDAY to "Wednesday",
                        java.util.Calendar.THURSDAY to "Thursday",
                        java.util.Calendar.FRIDAY to "Friday",
                        java.util.Calendar.SATURDAY to "Saturday",
                        java.util.Calendar.SUNDAY to "Sunday",
                    )
                    val dayLabel = daysOfWeek.find { it.first == preferences.newsletterDayOfWeek }?.second ?: "Saturday"

                    SettingListItem(
                        icon = Tabler.Outline.Calendar,
                        title = "Newsletter Delivery Day",
                        subtitle = "Day of the week to receive the newsletter",
                        trailingText = dayLabel,
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
                                NewsletterSectionType.RECENTLY_ADDED -> "Recently Added"
                                NewsletterSectionType.ACTIVITY_DIGEST -> "Activity Log"
                                NewsletterSectionType.LIBRARY_STATS -> "Library Stats"
                                NewsletterSectionType.CONTINUE_WATCHING -> "Continue Watching"
                                NewsletterSectionType.NEXT_UP -> "Next Up"
                                NewsletterSectionType.CURATED_PICKS -> "Curated Picks"
                            }
                            val sectionDesc = when (sectionType) {
                                NewsletterSectionType.RECENTLY_ADDED -> "Newest media items added to server"
                                NewsletterSectionType.ACTIVITY_DIGEST -> "Recent server activity logs"
                                NewsletterSectionType.LIBRARY_STATS -> "Overview statistics of your libraries"
                                NewsletterSectionType.CONTINUE_WATCHING -> "In-progress items to resume"
                                NewsletterSectionType.NEXT_UP -> "Next episodes in series"
                                NewsletterSectionType.CURATED_PICKS -> "Special recommendations for you"
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
                        hiddenCount = 9,
                        onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
                    )
                }
            }
        }
    }
}
