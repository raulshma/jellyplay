package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.data.repository.HomeLayoutRestoreFetch
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.DateFormatPreference
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatDate
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.components.homeSectionIcon
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_configure_libraries
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_rows
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_exclude_active_sessions_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_display
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_autopush_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_presets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restore_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_restored
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_server_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_server_undecodable
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_sync_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_upload
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_upload_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_layout_uploaded
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_screen_layout
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_sections_visible
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_merge_continue_next_up_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements_on
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
 * (the always-rendered config rows — the declared `unhide_cw` admission is
 * deliberately absent, so the strict derivation excludes it) plus the
 * conditional unhide action row, which renders only while hidden
 * continue-watching items exist — a content-state condition with no
 * admission vocabulary (the storage cache-used info row shape: the screen
 * adds the +1 explicitly).
 */
internal fun homeDisplayScreenRowTotal(hiddenCwItems: Int): Int =
    rowTotalFor(SettingsScreenGroups.homeDisplay, RowAdmissionFlags()) + if (hiddenCwItems > 0) 1 else 0

/**
 * The cards group's `SettingsItemList(total = …)` row count: the
 * card-display quartet plus the badge/spoiler/trickplay toggles.
 * Every row is declared [RowAdmission.Always] except the TV-only trickplay
 * row ([RowAdmission.Tv]), so the strict derivation counts all rows on TV and
 * all but the trickplay row off it.
 */
internal fun homeCardsScreenRowTotal(isTv: Boolean): Int =
    rowTotalFor(SettingsScreenGroups.homeCards, RowAdmissionFlags(isTv = isTv))

/**
 * The restore confirmation dialog's content, pure for tests: the
 * timestamp-diff signal (the server write stamp) plus BOTH layouts'
 * enabled-section counts, so the dialog can show "server copy saved <date> —
 * N sections" against "this device — M sections" without any formatting
 * logic of its own.
 */
internal data class HomeLayoutRestorePreview(
    val serverSavedAtEpochMillis: Long?,
    val serverSectionCount: Int,
    val localSectionCount: Int,
)

internal fun homeLayoutRestorePreview(
    offer: HomeLayoutRestoreFetch.Offer,
    localEnabledSectionCount: Int,
): HomeLayoutRestorePreview = HomeLayoutRestorePreview(
    serverSavedAtEpochMillis = offer.savedAtEpochMillis,
    serverSectionCount = offer.config.enabledHomeSectionTypes.size,
    localSectionCount = localEnabledSectionCount,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions = SettingsNavActions(),
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    // Manual sync actions: one-shot toast texts pre-resolved at composable
    // scope (the message bus takes plain Strings — the presets screen's
    // convention) plus the restore-confirmation dialog flag.
    val bus = LocalUserMessageBus.current
    val uploadedText = stringResource(Res.string.settings_home_layout_uploaded)
    val restoredText = stringResource(Res.string.settings_home_layout_restored)
    val syncFailedText = stringResource(Res.string.settings_home_layout_sync_failed)
    val serverEmptyText = stringResource(Res.string.settings_home_layout_server_empty)
    val serverUndecodableText = stringResource(Res.string.settings_home_layout_server_undecodable)
    var showRestoreConfirm by remember { mutableStateOf(false) }

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
                    // parallel id list): the always-rendered config rows in
                    // catalog order, plus the unhide row while hidden
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
                            HomeRows.HeroVideo.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Video,
                                    title = rowTitle(HomeRows.HeroVideo),
                                    subtitle = if (preferences.homeHeroVideoEnabled) stringResource(Res.string.settings_home_hero_video_on) else stringResource(Res.string.settings_home_hero_video_off),
                                    checked = preferences.homeHeroVideoEnabled,
                                    highlighted = highlightSettingId == HomeRows.HeroVideo.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHeroVideoEnabled(it) } },
                                )
                            }
                            HomeRows.HeroVideoTv.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.DeviceTv,
                                    title = rowTitle(HomeRows.HeroVideoTv),
                                    subtitle = if (preferences.homeHeroVideoTvEnabled) stringResource(Res.string.settings_home_hero_video_tv_on) else stringResource(Res.string.settings_home_hero_video_tv_off),
                                    checked = preferences.homeHeroVideoTvEnabled,
                                    highlighted = highlightSettingId == HomeRows.HeroVideoTv.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHeroVideoTvEnabled(it) } },
                                )
                            }
                            HomeRows.FocusBackdrop.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Focus,
                                    title = rowTitle(HomeRows.FocusBackdrop),
                                    subtitle = if (preferences.homeFocusBackdrop) stringResource(Res.string.settings_home_focus_backdrop_on) else stringResource(Res.string.settings_home_focus_backdrop_off),
                                    checked = preferences.homeFocusBackdrop,
                                    highlighted = highlightSettingId == HomeRows.FocusBackdrop.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setFocusBackdrop(it) } },
                                )
                            }
                            HomeRows.StatsCard.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.ChartBar,
                                    title = rowTitle(HomeRows.StatsCard),
                                    subtitle = if (preferences.homeStatsCard) stringResource(Res.string.settings_home_stats_card_on) else stringResource(Res.string.settings_home_stats_card_off),
                                    checked = preferences.homeStatsCard,
                                    highlighted = highlightSettingId == HomeRows.StatsCard.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setStatsCard(it) } },
                                )
                            }
                            HomeRows.GenreRail.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Category,
                                    title = rowTitle(HomeRows.GenreRail),
                                    subtitle = if (preferences.homeGenreRail) stringResource(Res.string.settings_home_genre_rail_on) else stringResource(Res.string.settings_home_genre_rail_off),
                                    checked = preferences.homeGenreRail,
                                    highlighted = highlightSettingId == HomeRows.GenreRail.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setGenreRail(it) } },
                                )
                            }
                            HomeRows.PresenceChips.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Users,
                                    title = rowTitle(HomeRows.PresenceChips),
                                    subtitle = if (preferences.homePresenceChips) stringResource(Res.string.settings_home_presence_chips_on) else stringResource(Res.string.settings_home_presence_chips_off),
                                    checked = preferences.homePresenceChips,
                                    highlighted = highlightSettingId == HomeRows.PresenceChips.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setPresenceChips(it) } },
                                )
                            }
                            HomeRows.NewSectionAnnouncements.id -> {
                                SettingToggleItem(
                                    icon = Tabler.Outline.Speakerphone,
                                    title = rowTitle(HomeRows.NewSectionAnnouncements),
                                    subtitle = if (preferences.homeNewSectionAnnouncements) stringResource(Res.string.settings_new_section_announcements_on) else stringResource(Res.string.settings_new_section_announcements_off),
                                    checked = preferences.homeNewSectionAnnouncements,
                                    highlighted = highlightSettingId == HomeRows.NewSectionAnnouncements.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setNewSectionAnnouncements(it) } },
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
                            HomeRows.HiddenLibraries.id -> {
                                SettingListItem(
                                    icon = Tabler.Outline.Folders,
                                    title = rowTitle(HomeRows.HiddenLibraries),
                                    subtitle = rowSubtitle(HomeRows.HiddenLibraries),
                                    trailingText = if (preferences.myMediaHiddenLibraryIds.isEmpty()) "" else "${preferences.myMediaHiddenLibraryIds.size}",
                                    highlighted = highlightSettingId == HomeRows.HiddenLibraries.id,
                                    onClick = { navActions.onNavigate(Route.MyMediaHidden) },
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

                    SettingToggleItem(
                        icon = Tabler.Outline.Ban,
                        title = rowTitle(HomeRows.ExcludeActiveSessions),
                        subtitle = if (preferences.excludeActiveSessions) stringResource(Res.string.settings_exclude_active_sessions_on) else stringResource(Res.string.settings_exclude_active_sessions_off),
                        checked = preferences.excludeActiveSessions,
                        highlighted = highlightSettingId == HomeRows.ExcludeActiveSessions.id,
                        index = 3, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setExcludeActiveSessions(it) } },
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

                    SettingToggleItem(
                        icon = Tabler.Outline.CloudUpload,
                        title = rowTitle(HomeRows.LayoutAutopush),
                        subtitle = if (preferences.homeLayoutAutopush) stringResource(Res.string.settings_home_layout_autopush_on) else stringResource(Res.string.settings_home_layout_autopush_off),
                        checked = preferences.homeLayoutAutopush,
                        highlighted = highlightSettingId == HomeRows.LayoutAutopush.id,
                        index = 0, count = 1,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setLayoutAutopush(it) } },
                    )

                    // Manual sync actions — work regardless of the
                    // autopush pref (the manual and automatic pushes serialize
                    // through the exact same codec inside the controller).
                    SettingListItem(
                        icon = Tabler.Outline.Upload,
                        title = stringResource(Res.string.settings_home_layout_upload),
                        subtitle = stringResource(Res.string.settings_home_layout_upload_subtitle),
                        trailingText = "",
                        highlighted = false,
                        index = 0, count = 1,
                        onClick = {
                            viewModel.uploadLayoutToServer { result ->
                                if (result.isSuccess) bus.info(uploadedText) else bus.info(syncFailedText)
                            }
                        },
                    )

                    SettingListItem(
                        icon = Tabler.Outline.CloudDownload,
                        title = stringResource(Res.string.settings_home_layout_restore),
                        subtitle = stringResource(Res.string.settings_home_layout_restore_subtitle),
                        trailingText = "",
                        highlighted = false,
                        index = 0, count = 1,
                        onClick = {
                            viewModel.fetchServerLayout { result ->
                                when (result.getOrNull()) {
                                    is HomeLayoutRestoreFetch.Offer -> showRestoreConfirm = true
                                    HomeLayoutRestoreFetch.Empty -> bus.info(serverEmptyText)
                                    HomeLayoutRestoreFetch.Undecodable -> bus.info(serverUndecodableText)
                                    null -> bus.info(syncFailedText)
                                }
                            }
                        },
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
                // The "Library & Cards" card-display toggles — home-discovery
                // knobs grouped beside the rest of the home hub — plus the
                // badge/spoiler/trickplay toggles (the trickplay row is
                // TV-only, gated by its declared RowAdmission.Tv).
                SettingsGroup(
                    icon = Tabler.Outline.Photo,
                    title = stringResource(Res.string.settings_home_cards),
                    summary = {
                        val parts = mutableListOf<String>()
                        if (preferences.showUnwatchedBadge) parts.add(rowTitle(HomeRows.ShowUnwatchedBadge))
                        if (preferences.showWatchedCheckmark) parts.add(rowTitle(HomeRows.ShowWatchedCheckmark))
                        if (preferences.hideWatchedItems) parts.add(rowTitle(HomeRows.HideWatchedItems))
                        if (preferences.showExternalRatings) parts.add(rowTitle(HomeRows.ShowExternalRatings))
                        if (preferences.showQualityBadges) parts.add(rowTitle(HomeRows.ShowQualityBadges))
                        if (preferences.showNewBadges) parts.add(rowTitle(HomeRows.ShowNewBadges))
                        if (isTv && preferences.homeTrickplayPreview) parts.add(rowTitle(HomeRows.TrickplayPreview))
                        if (preferences.antiSpoilerHome) parts.add(rowTitle(HomeRows.AntiSpoilerHome))
                        parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_off) }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeCards.itemIdSet,
                ) {
                    // Derived by rowTotalFor from the cards declaration (every
                    // row always renders except the TV-gated trickplay row).
                    SettingsItemList(total = homeCardsScreenRowTotal(isTv)) {
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

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.ShowQualityBadges),
                            title = rowTitle(HomeRows.ShowQualityBadges),
                            subtitle = rowSubtitle(HomeRows.ShowQualityBadges),
                            checked = preferences.showQualityBadges,
                            highlighted = highlightSettingId == HomeRows.ShowQualityBadges.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowQualityBadges(it) } },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.ShowNewBadges),
                            title = rowTitle(HomeRows.ShowNewBadges),
                            subtitle = rowSubtitle(HomeRows.ShowNewBadges),
                            checked = preferences.showNewBadges,
                            highlighted = highlightSettingId == HomeRows.ShowNewBadges.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowNewBadges(it) } },
                        )

                        if (SettingsScreenGroups.homeCards.rowAdmitted(HomeRows.TrickplayPreview.id, RowAdmissionFlags(isTv = isTv))) {
                            SettingToggleItem(
                                icon = rowIcon(HomeRows.TrickplayPreview),
                                title = rowTitle(HomeRows.TrickplayPreview),
                                subtitle = if (preferences.homeTrickplayPreview) stringResource(Res.string.settings_home_trickplay_preview_on) else stringResource(Res.string.settings_home_trickplay_preview_off),
                                checked = preferences.homeTrickplayPreview,
                                highlighted = highlightSettingId == HomeRows.TrickplayPreview.id,
                                onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setTrickplayPreview(it) } },
                            )
                        }

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.AntiSpoilerHome),
                            title = rowTitle(HomeRows.AntiSpoilerHome),
                            subtitle = rowSubtitle(HomeRows.AntiSpoilerHome),
                            checked = preferences.antiSpoilerHome,
                            highlighted = highlightSettingId == HomeRows.AntiSpoilerHome.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setAntiSpoilerHome(it) } },
                        )
                    }
                }
            }
    }

    // Restore confirmation: the timestamp-diff dialog (server write stamp
    // vs both layouts' enabled-section counts). Shown only while a decoded
    // offer is pending — fetchServerLayout retained it on the VM. The apply
    // routes through the store's applyLayout — the SAME command the local
    // preset-restore path uses (no second write path).
    val pendingOffer = viewModel.pendingRestoreOffer
    if (showRestoreConfirm && pendingOffer != null) {
        val restoreTitle = stringResource(Res.string.settings_home_layout_restore_title)
        val restoreMessageTemplate = stringResource(Res.string.settings_home_layout_restore_message)
        val cancelText = stringResource(CoreUiRes.string.core_cancel)
        // Client-clock sourced stamp (the transport carries no server
        // timestamp) — a "how old is the server copy" signal, not a guarantee.
        val savedAtLabel = remember(pendingOffer.savedAtEpochMillis) {
            pendingOffer.savedAtEpochMillis?.let { stamp ->
                runCatching { formatDate(stamp, DateFormatPreference.LONG) }.getOrDefault("")
            } ?: ""
        }
        val preview = homeLayoutRestorePreview(pendingOffer, preferences.enabledHomeSectionTypes.size)
        val message = restoreMessageTemplate
            .replace("%1\$s", savedAtLabel)
            .replace("%2\$s", preview.serverSectionCount.toString())
            .replace("%3\$s", preview.localSectionCount.toString())
        ConfirmDialog(
            title = restoreTitle,
            message = message,
            confirmText = stringResource(Res.string.settings_home_layout_restore),
            dismissText = cancelText,
            onConfirm = {
                viewModel.edit { it.homeDiscovery.applyLayout(pendingOffer.config) }
                viewModel.clearPendingRestoreOffer()
                showRestoreConfirm = false
                bus.info(restoredText)
            },
            onDismiss = {
                viewModel.clearPendingRestoreOffer()
                showRestoreConfirm = false
            },
            tone = ConfirmTone.NEUTRAL,
        )
    }
}
