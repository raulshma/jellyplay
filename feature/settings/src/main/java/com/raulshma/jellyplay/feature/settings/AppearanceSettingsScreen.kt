package com.raulshma.jellyplay.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val preferences = viewModel.preferences
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    JellyPlayScreenScaffold(
        title = "Appearance",
        onBack = onBack,
        backgroundColor = backgroundColor,
    ) { innerPadding ->
        LazyColumn(
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
                        parts.add(preferences.themeMode.name.lowercase().replaceFirstChar { it.uppercase() })
                        val accentName = preferences.accentColorSwatch.lowercase().replaceFirstChar { it.uppercase() }
                        parts.add("$accentName accent")
                        parts.add(preferences.colorStyle.displayName)
                        if (preferences.dynamicTheming) parts.add("Artwork dynamic")
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
                        add("accent_color")
                        add("color_style")
                        if (isAndroid12) add("dynamic_theming")
                        if (isDarkActive) add("oled_mode")
                        add("contrast")
                        add("home_mode")
                        add("hero_section")
                        add("nav_labels")
                    }
                    val totalCount = appearanceItems.size
                    var currentIdx = 0

                    appearanceItems.forEach { item ->
                        when (item) {
                            "theme_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Moon,
                                    title = "Theme Mode",
                                    subtitle = when (preferences.themeMode) {
                                        ThemeMode.SYSTEM -> "Follow system setting"
                                        ThemeMode.LIGHT -> "Always light"
                                        ThemeMode.DARK -> "Always dark"
                                    },
                                    trailingText = preferences.themeMode.name,
                                    index = currentIdx++, count = totalCount,
                                    onClick = {
                                        val next = when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                            ThemeMode.LIGHT -> ThemeMode.DARK
                                            ThemeMode.DARK -> ThemeMode.SYSTEM
                                        }
                                        viewModel.setThemeMode(next)
                                    },
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
                                    title = "Dynamic Theming",
                                    subtitle = "Colors extracted from artwork",
                                    checked = preferences.dynamicTheming,
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
                            "home_mode" -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = "Home Mode",
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) "Video-focused home screen" else "Music-focused home screen",
                                    trailingText = preferences.homeMode.name,
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
                                    index = currentIdx++, count = totalCount,
                                    onCheckedChange = { viewModel.setNavBarShowLabels(it) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.Bolt,
                    title = "Performance",
                    summary = {
                        if (preferences.performanceMode) "Reduced animations and effects" else "Standard experience"
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    SettingToggleItem(
                        icon = Tabler.Outline.Gauge,
                        title = "Performance Mode",
                        subtitle = "Reduces animations and effects for better performance on lower-end devices",
                        checked = preferences.performanceMode,
                        index = 0, count = 1,
                        onCheckedChange = { viewModel.setPerformanceMode(it) },
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
        }
    }
}
