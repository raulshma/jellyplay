package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.homeSectionIcon
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_display
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_rewatching_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_classic_rows_on
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

/**
 * The declared Home settings screen groups in LazyColumn order — the
 * derivation source the deep-link scroll resolver consumes (see
 * HighlightScroll.kt). All four groups always compose (no advanced gate on
 * this screen — the dedicated hub IS the discoverability fix), so the
 * adjustment lambda is the identity. Internal so the contract test can pin
 * the derivation against it.
 */
internal val homeScreenGroups: List<Set<String>> = listOf(
    SettingsScreenGroups.homeDisplay.itemIdSet,
    SettingsScreenGroups.homeNextUp.itemIdSet,
    SettingsScreenGroups.homeLayout.itemIdSet,
    SettingsScreenGroups.homeCards.itemIdSet,
)

/**
 * The display group's `SettingsItemList(total = …)` row count, derived by
 * [rowTotalFor] from the [SettingsScreenGroups.homeDisplay] declaration
 * (nine always-rendered config rows — the declared `unhide_cw` admission is
 * deliberately absent, so the strict derivation excludes it) plus the
 * conditional unhide action row, which renders only while hidden
 * continue-watching items exist — a content-state condition with no
 * admission vocabulary (the storage cache-used info row shape: the screen
 * adds the +1 explicitly).
 */
internal fun homeDisplayScreenRowTotal(hiddenCwItems: Int): Int =
    rowTotalFor(SettingsScreenGroups.homeDisplay, RowAdmissionFlags()) + if (hiddenCwItems > 0) 1 else 0

/**
 * The cards group's `SettingsItemList(total = …)` row count: the four
 * card-display toggles moved off AppearanceSettingsScreen's "Library & Cards"
 * group, every one declared [RowAdmission.Always] (the shipped always-on
 * behavior — no advanced gate on this hub), so the strict derivation counts
 * them all under the no-flag input.
 */
internal fun homeCardsScreenRowTotal(): Int =
    rowTotalFor(SettingsScreenGroups.homeCards, RowAdmissionFlags())

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions = SettingsNavActions(),
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_title),
        onBack = onBack,
        focusTag = "home_settings_init",
        highlightSettingId = highlightSettingId,
        highlightGroups = homeScreenGroups,
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_defaults_cd),
            dialogTitle = stringResource(Res.string.settings_reset_home_title),
            dialogMessage = stringResource(Res.string.settings_reset_home_message),
            onReset = { viewModel.resetCategory(PreferenceResetCategory.HOME_DISCOVERY) },
        ),
        pickerHost = true,
    ) { activePicker ->
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
                    // Derived from the declared display group (the derivation
                    // source the row total below reads — one declaration, no
                    // parallel id list): the nine always-rendered config rows
                    // in catalog order, plus the unhide row while hidden
                    // continue-watching items exist.
                    val displayItems = remember(preferences.hiddenCwItemIds) {
                        SettingsScreenGroups.homeDisplay.itemIds.filter { it != HomeRows.UnhideCw.id } +
                            if (preferences.hiddenCwItemIds.isNotEmpty()) listOf(HomeRows.UnhideCw.id) else emptyList()
                    }
                    SettingsItemList(total = homeDisplayScreenRowTotal(preferences.hiddenCwItemIds.size)) {
                    displayItems.forEach { item ->
                        when (item) {
                            HomeRows.HomeMode.id -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Home,
                                    title = rowTitle(HomeRows.HomeMode),
                                    subtitle = if (preferences.homeMode == HomeMode.VIDEO) stringResource(Res.string.settings_home_mode_video) else stringResource(Res.string.settings_home_mode_music),
                                    trailingText = preferences.homeMode.name,
                                    highlighted = highlightSettingId == HomeRows.HomeMode.id,
                                    onClick = {
                                        val next = if (preferences.homeMode == HomeMode.VIDEO) HomeMode.MUSIC else HomeMode.VIDEO
                                        viewModel.edit { it.homeDiscovery.setHomeMode(next) }
                                    },
                                )
                            }
                            HomeRows.HeroSection.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.LayersLinked,
                                    title = rowTitle(HomeRows.HeroSection),
                                    subtitle = if (preferences.homeHeroEnabled) stringResource(Res.string.settings_show_hero_on) else stringResource(Res.string.settings_show_hero_off),
                                    checked = preferences.homeHeroEnabled,
                                    highlighted = highlightSettingId == HomeRows.HeroSection.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroEnabled(it) } },
                                )
                            }
                            HomeRows.HomeBackdrop.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Background,
                                    title = rowTitle(HomeRows.HomeBackdrop),
                                    subtitle = if (preferences.homeBackdropEnabled) stringResource(Res.string.settings_home_backdrop_on) else stringResource(Res.string.settings_home_backdrop_off),
                                    checked = preferences.homeBackdropEnabled,
                                    highlighted = highlightSettingId == HomeRows.HomeBackdrop.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeBackdropEnabled(it) } },
                                )
                            }
                            HomeRows.ClockHome.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Clock,
                                    title = rowTitle(HomeRows.ClockHome),
                                    subtitle = if (preferences.showClockOnHome) stringResource(Res.string.settings_show_clock_on) else stringResource(Res.string.settings_show_clock_off),
                                    checked = preferences.showClockOnHome,
                                    highlighted = highlightSettingId == HomeRows.ClockHome.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowClockOnHome(it) } },
                                )
                            }
                            HomeRows.HideTopHeader.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.ArrowBarToDown,
                                    title = rowTitle(HomeRows.HideTopHeader),
                                    subtitle = if (preferences.hideTopHeaderOnScroll) stringResource(Res.string.settings_hide_top_header_on_scroll_on) else stringResource(Res.string.settings_hide_top_header_on_scroll_off),
                                    checked = preferences.hideTopHeaderOnScroll,
                                    highlighted = highlightSettingId == HomeRows.HideTopHeader.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideTopHeaderOnScroll(it) } },
                                )
                            }
                            HomeRows.SettingsInHomeSearch.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Adjustments,
                                    title = rowTitle(HomeRows.SettingsInHomeSearch),
                                    subtitle = if (preferences.showSettingsInHomeSearch) stringResource(Res.string.settings_show_settings_in_home_search_on) else stringResource(Res.string.settings_show_settings_in_home_search_off),
                                    checked = preferences.showSettingsInHomeSearch,
                                    highlighted = highlightSettingId == HomeRows.SettingsInHomeSearch.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowSettingsInHomeSearch(it) } },
                                )
                            }
                            HomeRows.ContinueWatchingClick.id -> {
                                val cwTitle = rowTitle(HomeRows.ContinueWatchingClick)
                                SettingListItem(
                                    icon = Tabler.Outline.PlayerPlay,
                                    title = cwTitle,
                                    subtitle = rowSubtitle(HomeRows.ContinueWatchingClick),
                                    trailingText = preferences.continueWatchingClickBehavior.displayName,
                                    highlighted = highlightSettingId == HomeRows.ContinueWatchingClick.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = cwTitle,
                                            items = ContinueWatchingClickBehavior.entries,
                                            label = { it.displayName },
                                            isSelected = { it == preferences.continueWatchingClickBehavior },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setContinueWatchingClickBehavior(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.ClassicRows.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.History,
                                    title = rowTitle(HomeRows.ClassicRows),
                                    subtitle = if (preferences.classicRows) stringResource(Res.string.settings_classic_rows_on) else stringResource(Res.string.settings_classic_rows_off),
                                    checked = preferences.classicRows,
                                    highlighted = highlightSettingId == HomeRows.ClassicRows.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setClassicRows(it) } },
                                )
                            }
                            HomeRows.NextUpHidden.id -> {
                                SettingListItem(
                                    icon = Tabler.Outline.EyeOff,
                                    title = rowTitle(HomeRows.NextUpHidden),
                                    subtitle = rowSubtitle(HomeRows.NextUpHidden),
                                    trailingText = if (preferences.nextUpExcludedSeriesIds.isEmpty()) "" else "${preferences.nextUpExcludedSeriesIds.size}",
                                    highlighted = highlightSettingId == HomeRows.NextUpHidden.id,
                                    onClick = { navActions.onNavigate(Route.NextUpExcluded) },
                                )
                            }
                            HomeRows.UnhideCw.id -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Eye,
                                    title = rowTitle(HomeRows.UnhideCw),
                                    subtitle = stringResource(Res.string.settings_unhide_continue_watching_subtitle, preferences.hiddenCwItemIds.size),
                                    highlighted = highlightSettingId == HomeRows.UnhideCw.id,
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
                    val nextUpTotal = rowTotalFor(SettingsScreenGroups.homeNextUp, RowAdmissionFlags())
                    SettingToggleItem(
                        icon = Tabler.Outline.LayersLinked,
                        title = rowTitle(HomeRows.MergeContinueNextUp),
                        subtitle = if (preferences.mergeContinueWatchingAndNextUp) stringResource(Res.string.settings_merge_continue_next_up_on) else stringResource(Res.string.settings_merge_continue_next_up_off),
                        checked = preferences.mergeContinueWatchingAndNextUp,
                        highlighted = highlightSettingId == HomeRows.MergeContinueNextUp.id,
                        index = 0, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setMergeContinueWatchingAndNextUp(it) } },
                    )

                    val nextUpTitle = rowTitle(HomeRows.NextUpMaxDays)
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
                        subtitle = rowSubtitle(HomeRows.NextUpMaxDays),
                        trailingText = dayLabels[preferences.nextUpMaxDays] ?: formatIntPattern(xDaysFormat, preferences.nextUpMaxDays),
                        highlighted = highlightSettingId == HomeRows.NextUpMaxDays.id,
                        index = 1, count = nextUpTotal,
                        onClick = {
                            activePicker.value = PickerState.List(
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
                        title = rowTitle(HomeRows.NextUpRewatching),
                        subtitle = if (preferences.nextUpRewatching) stringResource(Res.string.settings_rewatching_next_up_on) else stringResource(Res.string.settings_rewatching_next_up_off),
                        checked = preferences.nextUpRewatching,
                        highlighted = highlightSettingId == HomeRows.NextUpRewatching.id,
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
                        title = rowTitle(HomeRows.PinnedHomeSections),
                        subtitle = rowSubtitle(HomeRows.PinnedHomeSections),
                        trailingText = if (preferences.pinnedHomeSections.isEmpty()) "" else "${preferences.pinnedHomeSections.size}",
                        highlighted = highlightSettingId == HomeRows.PinnedHomeSections.id,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.PinnedHomeSections(if (highlightSettingId == HomeRows.PinnedHomeSections.id) PINNED_ADD_HIGHLIGHT_ID else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Bookmarks,
                        title = rowTitle(HomeRows.HomeLayoutPresets),
                        subtitle = rowSubtitle(HomeRows.HomeLayoutPresets),
                        trailingText = if (preferences.homeLayoutPresets.isEmpty()) "" else "${preferences.homeLayoutPresets.size}",
                        highlighted = highlightSettingId == HomeRows.HomeLayoutPresets.id,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.HomeLayoutPresets(if (highlightSettingId == HomeRows.HomeLayoutPresets.id) PRESET_LIST_HIGHLIGHT_ID else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Folders,
                        title = rowTitle(HomeRows.ConfigureLibraries),
                        subtitle = rowSubtitle(HomeRows.ConfigureLibraries),
                        trailingText = "",
                        highlighted = highlightSettingId == HomeRows.ConfigureLibraries.id,
                        index = 0, count = 1,
                        onClick = { navActions.onNavigate(Route.LibraryHomeSections(if (highlightSettingId == HomeRows.ConfigureLibraries.id) "configure_libraries" else null)) },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.Compass,
                        title = rowTitle(HomeRows.DiscoverRows),
                        subtitle = rowSubtitle(HomeRows.DiscoverRows),
                        trailingText = if (preferences.discoverRows.isEmpty()) "" else "${preferences.discoverRows.size}",
                        highlighted = highlightSettingId == HomeRows.DiscoverRows.id,
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

            item {
                // The card-display toggles moved off AppearanceSettingsScreen's
                // "Library & Cards" group (PS-4): home-discovery knobs, now
                // grouped beside the rest of the home hub.
                SettingsGroup(
                    icon = Tabler.Outline.Photo,
                    title = stringResource(Res.string.settings_home_cards),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.showUnwatchedBadge) parts.add(rowTitle(HomeRows.ShowUnwatchedBadge))
                        if (preferences.showWatchedCheckmark) parts.add(rowTitle(HomeRows.ShowWatchedCheckmark))
                        if (preferences.hideWatchedItems) parts.add(rowTitle(HomeRows.HideWatchedItems))
                        if (preferences.showExternalRatings) parts.add(rowTitle(HomeRows.ShowExternalRatings))
                        parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_off) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeCards.itemIdSet,
                ) {
                    // Derived by rowTotalFor from the cards declaration (every
                    // row always renders — no gate on this hub).
                    SettingsItemList(total = homeCardsScreenRowTotal()) {
                        SettingToggleItem(
                            icon = rowIcon(HomeRows.ShowUnwatchedBadge),
                            title = rowTitle(HomeRows.ShowUnwatchedBadge),
                            subtitle = rowSubtitle(HomeRows.ShowUnwatchedBadge),
                            checked = preferences.showUnwatchedBadge,
                            highlighted = highlightSettingId == HomeRows.ShowUnwatchedBadge.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowUnwatchedBadge(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.ShowWatchedCheckmark),
                            title = rowTitle(HomeRows.ShowWatchedCheckmark),
                            subtitle = rowSubtitle(HomeRows.ShowWatchedCheckmark),
                            checked = preferences.showWatchedCheckmark,
                            highlighted = highlightSettingId == HomeRows.ShowWatchedCheckmark.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowWatchedCheckmark(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.HideWatchedItems),
                            title = rowTitle(HomeRows.HideWatchedItems),
                            subtitle = rowSubtitle(HomeRows.HideWatchedItems),
                            checked = preferences.hideWatchedItems,
                            highlighted = highlightSettingId == HomeRows.HideWatchedItems.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideWatchedItems(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.ShowExternalRatings),
                            title = rowTitle(HomeRows.ShowExternalRatings),
                            subtitle = rowSubtitle(HomeRows.ShowExternalRatings),
                            checked = preferences.showExternalRatings,
                            highlighted = highlightSettingId == HomeRows.ShowExternalRatings.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowExternalRatings(it) } },
                        )
                    }
                }
            }
    }
}
