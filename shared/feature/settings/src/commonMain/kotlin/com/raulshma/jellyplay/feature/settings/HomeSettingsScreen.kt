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
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_watching_tap_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows_helper
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_watched_items_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_cards
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_next_up_hidden_brief
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pinned_home_sections_brief
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_external_ratings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_section
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_unwatched_badge_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_watched_checkmark_subtitle
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
                        SettingsScreenGroups.homeDisplay.itemIds.filter { it != HomeSettingsIds.UNHIDE_CW } +
                            if (preferences.hiddenCwItemIds.isNotEmpty()) listOf(HomeSettingsIds.UNHIDE_CW) else emptyList()
                    }
                    SettingsItemList(total = homeDisplayScreenRowTotal(preferences.hiddenCwItemIds.size)) {
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
                            HomeSettingsIds.CLASSIC_ROWS -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.History,
                                    title = rowTitle(HomeSettingsIds.CLASSIC_ROWS),
                                    subtitle = if (preferences.classicRows) stringResource(Res.string.settings_classic_rows_on) else stringResource(Res.string.settings_classic_rows_off),
                                    checked = preferences.classicRows,
                                    highlighted = highlightSettingId == HomeSettingsIds.CLASSIC_ROWS,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setClassicRows(it) } },
                                )
                            }
                            HomeSettingsIds.NEXT_UP_HIDDEN -> {
                                SettingListItem(
                                    icon = Tabler.Outline.EyeOff,
                                    title = rowTitle(HomeSettingsIds.NEXT_UP_HIDDEN),
                                    subtitle = stringResource(Res.string.settings_next_up_hidden_brief),
                                    trailingText = if (preferences.nextUpExcludedSeriesIds.isEmpty()) "" else "${preferences.nextUpExcludedSeriesIds.size}",
                                    highlighted = highlightSettingId == HomeSettingsIds.NEXT_UP_HIDDEN,
                                    onClick = { navActions.onNavigate(Route.NextUpExcluded) },
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

            item {
                // The card-display toggles moved off AppearanceSettingsScreen's
                // "Library & Cards" group (PS-4): home-discovery knobs, now
                // grouped beside the rest of the home hub.
                SettingsGroup(
                    icon = Tabler.Outline.Photo,
                    title = stringResource(Res.string.settings_home_cards),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.showUnwatchedBadge) parts.add(rowTitle(HomeSettingsIds.SHOW_UNWATCHED_BADGE))
                        if (preferences.showWatchedCheckmark) parts.add(rowTitle(HomeSettingsIds.SHOW_WATCHED_CHECKMARK))
                        if (preferences.hideWatchedItems) parts.add(rowTitle(HomeSettingsIds.HIDE_WATCHED_ITEMS))
                        if (preferences.showExternalRatings) parts.add(rowTitle(HomeSettingsIds.SHOW_EXTERNAL_RATINGS))
                        parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_off) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeCards.itemIdSet,
                ) {
                    // Derived by rowTotalFor from the cards declaration (every
                    // row always renders — no gate on this hub).
                    SettingsItemList(total = homeCardsScreenRowTotal()) {
                        SettingToggleItem(
                            icon = rowIcon(HomeSettingsIds.SHOW_UNWATCHED_BADGE),
                            title = rowTitle(HomeSettingsIds.SHOW_UNWATCHED_BADGE),
                            subtitle = stringResource(Res.string.settings_show_unwatched_badge_subtitle),
                            checked = preferences.showUnwatchedBadge,
                            highlighted = highlightSettingId == HomeSettingsIds.SHOW_UNWATCHED_BADGE,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowUnwatchedBadge(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeSettingsIds.SHOW_WATCHED_CHECKMARK),
                            title = rowTitle(HomeSettingsIds.SHOW_WATCHED_CHECKMARK),
                            subtitle = stringResource(Res.string.settings_show_watched_checkmark_subtitle),
                            checked = preferences.showWatchedCheckmark,
                            highlighted = highlightSettingId == HomeSettingsIds.SHOW_WATCHED_CHECKMARK,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowWatchedCheckmark(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeSettingsIds.HIDE_WATCHED_ITEMS),
                            title = rowTitle(HomeSettingsIds.HIDE_WATCHED_ITEMS),
                            subtitle = stringResource(Res.string.settings_hide_watched_items_subtitle),
                            checked = preferences.hideWatchedItems,
                            highlighted = highlightSettingId == HomeSettingsIds.HIDE_WATCHED_ITEMS,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHideWatchedItems(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeSettingsIds.SHOW_EXTERNAL_RATINGS),
                            title = rowTitle(HomeSettingsIds.SHOW_EXTERNAL_RATINGS),
                            subtitle = stringResource(Res.string.settings_show_external_ratings_subtitle),
                            checked = preferences.showExternalRatings,
                            highlighted = highlightSettingId == HomeSettingsIds.SHOW_EXTERNAL_RATINGS,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowExternalRatings(it) } },
                        )
                    }
                }
            }
    }
}
