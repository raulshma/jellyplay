package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.homeSectionIcon
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.CenteredBringIntoView
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_display
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_screen_layout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_sections_visible
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_time_window_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_home
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unhide_continue_watching_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_days
import androidx.compose.foundation.shape.CircleShape

/**
 * The declared Home settings screen groups in LazyColumn order — the
 * derivation source the deep-link scroll resolver consumes (see
 * HighlightScroll.kt). All three groups always compose (no advanced gate on
 * this screen — the dedicated hub IS the discoverability fix), so the
 * adjustment lambda is the identity. Internal so the contract test can pin
 * the derivation against it.
 */
internal val homeScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.homeDisplay.itemIdSet,
    SettingsScreenGroups.homeNextUp.itemIdSet,
    SettingsScreenGroups.homeLayout.itemIdSet,
)

/** The display group's declared rows plus the conditional unhide action row. */
internal fun homeDisplayScreenRowTotal(hiddenCwItems: Int): Int =
    SettingsScreenGroups.homeDisplay.items.size + if (hiddenCwItems > 0) 1 else 0

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions = SettingsNavActions(),
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "home_settings_init",
    )

    val scrollState = rememberLazyListState()
    val scrollIndex = rememberHighlightScrollIndex(highlightSettingId, homeScreenGroups)
    HighlightScrollEffect(scrollState, scrollIndex)

    var showResetDialog by remember { mutableStateOf(false) }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_home_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
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
                    icon = Tabler.Outline.Home,
                    title = stringResource(Res.string.settings_home_display),
                    summary = {
                        val modeLabel = if (preferences.homeMode == HomeMode.VIDEO) {
                            stringResource(Res.string.settings_home_mode_video)
                        } else {
                            stringResource(Res.string.settings_home_mode_music)
                        }
                        val parts = mutableListOf(modeLabel)
                        if (preferences.homeHeroEnabled) parts.add(stringResource(Res.string.settings_show_hero_on))
                        if (preferences.showClockOnHome) parts.add(stringResource(Res.string.settings_show_clock_on))
                        if (preferences.showSettingsInHomeSearch) {
                            parts.add(stringResource(Res.string.settings_show_settings_in_home_search_on))
                        }
                        parts.joinToString(", ")
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = true,
                ) {
                    val displayItems = remember(preferences.hiddenCwItemIds) {
                        buildList {
                            add(HomeSettingsIds.HOME_MODE)
                            add(HomeSettingsIds.HERO_SECTION)
                            add(HomeSettingsIds.HOME_BACKDROP)
                            add(HomeSettingsIds.CLOCK_HOME)
                            add(HomeSettingsIds.HIDE_TOP_HEADER)
                            add(HomeSettingsIds.SETTINGS_IN_HOME_SEARCH)
                            add(HomeSettingsIds.CONTINUE_WATCHING_CLICK)
                            if (preferences.hiddenCwItemIds.isNotEmpty()) {
                                add(HomeSettingsIds.UNHIDE_CW)
                            }
                        }
                    }
                    SettingsItemList(total = displayItems.size) {
                    displayItems.forEach { item ->
                        when (item) {
                            HomeSettingsIds.HOME_MODE -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = rowTitle(HomeSettingsIds.HOME_MODE),
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) stringResource(Res.string.settings_home_mode_video) else stringResource(Res.string.settings_home_mode_music),
                                    trailingText = preferences.homeMode.name,
                                    highlighted = highlightSettingId == HomeSettingsIds.HOME_MODE,
                                    onClick = {
                                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                        viewModel.edit { it.homeDiscovery.setHomeMode(next) }
                                    },
                                )
                            }
                            HomeSettingsIds.HERO_SECTION -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = rowTitle(HomeSettingsIds.HERO_SECTION),
                                    subtitle = if (preferences.homeHeroEnabled) stringResource(Res.string.settings_show_hero_on) else stringResource(Res.string.settings_show_hero_off),
                                    checked = preferences.homeHeroEnabled,
                                    highlighted = highlightSettingId == HomeSettingsIds.HERO_SECTION,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroEnabled(it) } },
                                )
                            }
                            HomeSettingsIds.HOME_BACKDROP -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Background,
                                    title = rowTitle(HomeSettingsIds.HOME_BACKDROP),
                                    subtitle = if (preferences.homeBackdropEnabled) stringResource(Res.string.settings_home_backdrop_on) else stringResource(Res.string.settings_home_backdrop_off),
                                    checked = preferences.homeBackdropEnabled,
                                    highlighted = highlightSettingId == HomeSettingsIds.HOME_BACKDROP,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeBackdropEnabled(it) } },
                                )
                            }
                            HomeSettingsIds.CLOCK_HOME -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Clock,
                                    title = rowTitle(HomeSettingsIds.CLOCK_HOME),
                                    subtitle = if (preferences.showClockOnHome) stringResource(Res.string.settings_show_clock_on) else stringResource(Res.string.settings_show_clock_off),
                                    checked = preferences.showClockOnHome,
                                    highlighted = highlightSettingId == HomeSettingsIds.CLOCK_HOME,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowClockOnHome(it) } },
                                )
                            }
                            HomeSettingsIds.HIDE_TOP_HEADER -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.ArrowBarToDown,
                                    title = rowTitle(HomeSettingsIds.HIDE_TOP_HEADER),
                                    subtitle = if (preferences.hideTopHeaderOnScroll) stringResource(Res.string.settings_hide_top_header_on_scroll_on) else stringResource(Res.string.settings_hide_top_header_on_scroll_off),
                                    checked = preferences.hideTopHeaderOnScroll,
                                    highlighted = highlightSettingId == HomeSettingsIds.HIDE_TOP_HEADER,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideTopHeaderOnScroll(it) } },
                                )
                            }
                            HomeSettingsIds.SETTINGS_IN_HOME_SEARCH -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = rowTitle(HomeSettingsIds.SETTINGS_IN_HOME_SEARCH),
                                    subtitle = if (preferences.showSettingsInHomeSearch) stringResource(Res.string.settings_show_settings_in_home_search_on) else stringResource(Res.string.settings_show_settings_in_home_search_off),
                                    checked = preferences.showSettingsInHomeSearch,
                                    highlighted = highlightSettingId == HomeSettingsIds.SETTINGS_IN_HOME_SEARCH,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowSettingsInHomeSearch(it) } },
                                )
                            }
                            HomeSettingsIds.CONTINUE_WATCHING_CLICK -> {
                                val cwTitle = rowTitle(HomeSettingsIds.CONTINUE_WATCHING_CLICK)
                                SettingListItem(
                                    icon = Tabler.Outline.PlayerPlay,
                                    title = cwTitle,
                                    subtitle = stringResource(Res.string.settings_continue_watching_tap_subtitle),
                                    trailingText = preferences.continueWatchingClickBehavior.displayName,
                                    highlighted = highlightSettingId == HomeSettingsIds.CONTINUE_WATCHING_CLICK,
                                    onClick = {
                                        activePicker = PickerState.List(
                                            title = cwTitle,
                                            items = ContinueWatchingClickBehavior.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.continueWatchingClickBehavior },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setContinueWatchingClickBehavior(it) } },
                                        )
                                    },
                                )
                            }
                            HomeSettingsIds.UNHIDE_CW -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = rowTitle(HomeSettingsIds.UNHIDE_CW),
                                    subtitle = stringResource(Res.string.settings_unhide_continue_watching_subtitle, preferences.hiddenCwItemIds.size),
                                    highlighted = highlightSettingId == HomeSettingsIds.UNHIDE_CW,
                                    onClick = { viewModel.edit { it.homeDiscovery.unhideAllCwItems() } },
                                )
                            }
                        }
                    }
                    }
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.CalendarTime,
                    title = stringResource(Res.string.settings_continue_next_up),
                    summary = {
                        if (preferences.mergeContinueWatchingAndNextUp) {
                            stringResource(Res.string.settings_merge_continue_next_up_on)
                        } else {
                            stringResource(Res.string.settings_merge_continue_next_up_off)
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeNextUp.itemIdSet,
                ) {
                    val nextUpTotal = SettingsScreenGroups.homeNextUp.items.size
                    SettingToggleItem(
                        icon = Tabler.Outline.LayersLinked,
                        title = rowTitle(HomeSettingsIds.MERGE_CONTINUE_NEXT_UP),
                        subtitle = if (preferences.mergeContinueWatchingAndNextUp) stringResource(Res.string.settings_merge_continue_next_up_on) else stringResource(Res.string.settings_merge_continue_next_up_off),
                        checked = preferences.mergeContinueWatchingAndNextUp,
                        highlighted = highlightSettingId == HomeSettingsIds.MERGE_CONTINUE_NEXT_UP,
                        index = 0, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setMergeContinueWatchingAndNextUp(it) } },
                    )

                    val nextUpTitle = rowTitle(HomeSettingsIds.NEXT_UP_MAX_DAYS)
                    val unlimitedLabel = stringResource(Res.string.settings_unlimited)
                    val xDaysFormat = stringResource(Res.string.settings_x_days)
                    val dayLabels = mapOf(
                        0 to unlimitedLabel,
                        7 to formatIntPattern(xDaysFormat, 7),
                        14 to formatIntPattern(xDaysFormat, 14),
                        30 to formatIntPattern(xDaysFormat, 30),
                        60 to formatIntPattern(xDaysFormat, 60),
                        90 to formatIntPattern(xDaysFormat, 90),
                    )
                    SettingListItem(
                        icon = Tabler.Outline.CalendarTime,
                        title = nextUpTitle,
                        subtitle = stringResource(Res.string.settings_next_up_time_window_subtitle),
                        trailingText = dayLabels[preferences.nextUpMaxDays] ?: formatIntPattern(xDaysFormat, preferences.nextUpMaxDays),
                        highlighted = highlightSettingId == HomeSettingsIds.NEXT_UP_MAX_DAYS,
                        index = 1, count = nextUpTotal,
                        onClick = {
                            activePicker = PickerState.List(
                                title = nextUpTitle,
                                items = listOf(0, 7, 14, 30, 60, 90),
                                label = { dayLabels[it] ?: formatIntPattern(xDaysFormat, it) },
                                isSelected = { it == preferences.nextUpMaxDays },
                                onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpMaxDays(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.History,
                        title = rowTitle(HomeSettingsIds.NEXT_UP_REWATCHING),
                        subtitle = if (preferences.nextUpRewatching) stringResource(Res.string.settings_rewatching_next_up_on) else stringResource(Res.string.settings_rewatching_next_up_off),
                        checked = preferences.nextUpRewatching,
                        highlighted = highlightSettingId == HomeSettingsIds.NEXT_UP_REWATCHING,
                        index = 2, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setNextUpRewatching(it) } },
                    )
                }
            }

            item {
                SettingsGroup(
                    icon = Tabler.Outline.LayoutGrid,
                    title = stringResource(Res.string.settings_home_screen_layout),
                    summary = {
                        val enabled = preferences.enabledHomeSectionTypes
                        stringResource(Res.string.settings_home_sections_visible, enabled.size, HomeSectionType.CONFIGURABLE.size)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeLayout.itemIdSet,
                ) {
                    SettingListItem(
                        icon = Tabler.Outline.Pinned,
                        title = rowTitle(HomeSettingsIds.PINNED_HOME_SECTIONS),
                        subtitle = stringResource(Res.string.settings_pinned_home_sections_brief),
                        trailingText = if (preferences.pinnedHomeSections.isEmpty()) "" else "${preferences.pinnedHomeSections.size}",
                        highlighted = highlightSettingId == HomeSettingsIds.PINNED_HOME_SECTIONS,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.PinnedHomeSections(if (highlightSettingId == HomeSettingsIds.PINNED_HOME_SECTIONS) PINNED_ADD_HIGHLIGHT_ID else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Bookmarks,
                        title = rowTitle(HomeSettingsIds.HOME_LAYOUT_PRESETS),
                        subtitle = stringResource(Res.string.settings_home_layout_presets_brief),
                        trailingText = if (preferences.homeLayoutPresets.isEmpty()) "" else "${preferences.homeLayoutPresets.size}",
                        highlighted = highlightSettingId == HomeSettingsIds.HOME_LAYOUT_PRESETS,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.HomeLayoutPresets(if (highlightSettingId == HomeSettingsIds.HOME_LAYOUT_PRESETS) PRESET_LIST_HIGHLIGHT_ID else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Folders,
                        title = rowTitle(HomeSettingsIds.CONFIGURE_LIBRARIES),
                        subtitle = stringResource(Res.string.settings_configure_libraries_desc),
                        trailingText = "",
                        highlighted = highlightSettingId == HomeSettingsIds.CONFIGURE_LIBRARIES,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.LibraryHomeSections(if (highlightSettingId == HomeSettingsIds.CONFIGURE_LIBRARIES) "configure_libraries" else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Compass,
                        title = rowTitle(HomeSettingsIds.DISCOVER_ROWS),
                        subtitle = stringResource(Res.string.settings_discover_rows_helper),
                        trailingText = if (preferences.discoverRows.isEmpty()) "" else "${preferences.discoverRows.size}",
                        highlighted = highlightSettingId == HomeSettingsIds.DISCOVER_ROWS,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.DiscoverRows()) },
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
        }
        }
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_reset_home_title),
            message = stringResource(Res.string.settings_reset_home_message),
            confirmText = stringResource(Res.string.settings_reset),
            onConfirm = {
                viewModel.resetCategory(PreferenceResetCategory.HOME_DISCOVERY)
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
