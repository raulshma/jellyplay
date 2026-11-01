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
import com.raulshma.jellyplay.core.model.HomeCardScale
import com.raulshma.jellyplay.core.model.HomeCwProgressStyle
import com.raulshma.jellyplay.core.model.HomeDiscoveryPreferenceSpecsDefaults
import com.raulshma.jellyplay.core.model.HomeDiscoverPattern
import com.raulshma.jellyplay.core.model.HomeHeroStyle
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeRefreshCadence
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.NewsletterBannerWindow
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_header_shortcuts
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_header_shortcuts_subtitle
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_small
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_large
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_thick
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_progress_style_percent
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_no_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_no_unplayed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_played_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_always
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_weekends
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_eager
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_relaxed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_manual
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_uniform
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_compact
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_stats_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_genre_rail
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_newsletter_banner
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tiles_columns_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_latest
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_recent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_upcoming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_none

/**
 * The display group's `SettingsItemList(total = …)` row count, derived by
 * [rowTotalFor] from the [SettingsScreenGroups.homeDisplay] declaration
 * (the always-rendered config rows — the declared `unhide_cw` admission is
 * deliberately absent, so the strict derivation excludes it) plus the
 * conditional unhide action row, which renders only while hidden
 * continue-watching items exist — a content-state condition with no
 * admission vocabulary (the storage cache-used info row shape: the screen
 * adds the +1 explicitly). The hero-slideshow interval row rides its
 * declared WhenOn(home_hero_style) gate; the screen feeds "style is
 * slideshow" as that parent's on-state.
 */
internal fun homeDisplayScreenRowTotal(hiddenCwItems: Int, heroSlideshowOn: Boolean): Int =
    rowTotalFor(
        SettingsScreenGroups.homeDisplay,
        RowAdmissionFlags(parentsOn = rowParentsOn(HomeRows.HomeHeroStyle.id to heroSlideshowOn)),
    ) + if (hiddenCwItems > 0) 1 else 0

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

    // The screen's ONE group declaration, in LazyColumn order — the shell
    // derives the deep-link scroll resolution from it. All four groups always
    // compose (no advanced gate on this screen — the dedicated hub IS the
    // discoverability fix), so the scroll adjust is the identity and there is
    // no hidden hint. Slots stop at GROUP grain: the row emission inside each
    // slot is the hand-written body, unchanged (ADR-0009).
    val homeGroups = listOf(
        SettingsScreenGroups.homeDisplay.asScreenGroup { activePicker ->
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
                    // catalog order, the hero-slideshow interval only while
                    // its WhenOn parent (style = slideshow) is on, plus the
                    // unhide row while hidden continue-watching items exist.
                    val heroSlideshowOn = preferences.homeHeroStyle == HomeHeroStyle.SLIDESHOW
                    val displayItems = remember(preferences.hiddenCwItemIds, heroSlideshowOn) {
                        SettingsScreenGroups.homeDisplay.itemIds
                            .filter { it != HomeRows.UnhideCw.id }
                            .filter { heroSlideshowOn || it != HomeRows.HomeHeroSlideshowInterval.id } +
                            if (preferences.hiddenCwItemIds.isNotEmpty()) listOf(HomeRows.UnhideCw.id) else emptyList()
                    }
                    SettingsItemList(total = homeDisplayScreenRowTotal(preferences.hiddenCwItemIds.size, heroSlideshowOn)) {
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
                            // The hero's render style — rides beside the
                            // hero toggle (the master switch it qualifies).
                            HomeRows.HomeHeroStyle.id -> {
                                val heroStyleTitle = rowTitle(HomeRows.HomeHeroStyle)
                                val heroStyleLabels = HomeHeroStyle.entries.associateWith { stringResource(homeHeroStyleLabelRes(it)) }
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeHeroStyle),
                                    title = heroStyleTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeHeroStyle),
                                    trailingText = heroStyleLabels[preferences.homeHeroStyle]
                                        ?: preferences.homeHeroStyle.name,
                                    highlighted = highlightSettingId == HomeRows.HomeHeroStyle.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = heroStyleTitle,
                                            items = HomeHeroStyle.entries,
                                            label = { heroStyleLabels.getValue(it) },
                                            isSelected = { it == preferences.homeHeroStyle },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroStyle(it) } },
                                        )
                                    },
                                )
                            }
                            // The slideshow style's per-slide dwell — emitted
                            // only while its WhenOn(home_hero_style) parent is
                            // on (style = slideshow); the displayItems
                            // derivation above filters it, matching the
                            // declared admission the total reads.
                            HomeRows.HomeHeroSlideshowInterval.id -> {
                                val slideshowIntervalTitle = rowTitle(HomeRows.HomeHeroSlideshowInterval)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeHeroSlideshowInterval),
                                    title = slideshowIntervalTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeHeroSlideshowInterval),
                                    trailingText = "${preferences.homeHeroSlideshowIntervalSec}s",
                                    highlighted = highlightSettingId == HomeRows.HomeHeroSlideshowInterval.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = slideshowIntervalTitle,
                                            items = listOf(3, 5, 8, 10, 15, 20, 30, 60),
                                            label = { "${it}s" },
                                            isSelected = { it == preferences.homeHeroSlideshowIntervalSec },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroSlideshowIntervalSec(it) } },
                                        )
                                    },
                                )
                            }
                            // The carousel hero's idle dwell (the SLIDESHOW
                            // style's own pacing takes precedence while that
                            // style is selected).
                            HomeRows.HomeHeroIdleRotation.id -> {
                                val idleRotationTitle = rowTitle(HomeRows.HomeHeroIdleRotation)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeHeroIdleRotation),
                                    title = idleRotationTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeHeroIdleRotation),
                                    trailingText = "${preferences.homeHeroIdleRotationMs / 1000}s",
                                    highlighted = highlightSettingId == HomeRows.HomeHeroIdleRotation.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = idleRotationTitle,
                                            items = listOf(3, 5, 8, 10, 15, 20, 30),
                                            label = { "${it}s" },
                                            isSelected = { it * 1000 == preferences.homeHeroIdleRotationMs },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroIdleRotationMs(it * 1000) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HeroScrollDefer.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HeroScrollDefer),
                                    title = rowTitle(HomeRows.HeroScrollDefer),
                                    subtitle = rowSubtitle(HomeRows.HeroScrollDefer),
                                    checked = preferences.heroScrollDefer,
                                    highlighted = highlightSettingId == HomeRows.HeroScrollDefer.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHeroScrollDefer(it) } },
                                )
                            }
                            HomeRows.HomeHeroRandomStart.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeHeroRandomStart),
                                    title = rowTitle(HomeRows.HomeHeroRandomStart),
                                    subtitle = rowSubtitle(HomeRows.HomeHeroRandomStart),
                                    checked = preferences.homeHeroRandomStart,
                                    highlighted = highlightSettingId == HomeRows.HomeHeroRandomStart.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroRandomStart(it) } },
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
                            // The stats card's visible metrics — single-select
                            // presets over the CSV set (the picker vocabulary
                            // is single-select; the presets cover the sane
                            // subsets).
                            HomeRows.HomeStatsMetrics.id -> {
                                val statsMetricsTitle = rowTitle(HomeRows.HomeStatsMetrics)
                                val allMetrics = HomeDiscoveryPreferenceSpecsDefaults.STATS_METRICS_CANONICAL
                                val metricPresets = listOf(
                                    allMetrics to stringResource(Res.string.settings_stats_metric_all),
                                    setOf("played", "unplayed") to stringResource(Res.string.settings_stats_metric_no_minutes),
                                    setOf("played", "minutes") to stringResource(Res.string.settings_stats_metric_no_unplayed),
                                    setOf("played") to stringResource(Res.string.settings_stats_metric_played_only),
                                )
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeStatsMetrics),
                                    title = statsMetricsTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeStatsMetrics),
                                    trailingText = metricPresets
                                        .find { it.first == preferences.homeStatsMetrics }
                                        ?.second
                                        ?: stringResource(Res.string.settings_custom),
                                    highlighted = highlightSettingId == HomeRows.HomeStatsMetrics.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = statsMetricsTitle,
                                            items = metricPresets,
                                            label = { it.second },
                                            isSelected = { it.first == preferences.homeStatsMetrics },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeStatsMetrics(it.first) } },
                                        )
                                    },
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
                            HomeRows.HomeClockShowDate.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeClockShowDate),
                                    title = rowTitle(HomeRows.HomeClockShowDate),
                                    subtitle = rowSubtitle(HomeRows.HomeClockShowDate),
                                    checked = preferences.homeClockShowDate,
                                    highlighted = highlightSettingId == HomeRows.HomeClockShowDate.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeClockShowDate(it) } },
                                )
                            }
                            HomeRows.HomeOpaqueDock.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeOpaqueDock),
                                    title = rowTitle(HomeRows.HomeOpaqueDock),
                                    subtitle = rowSubtitle(HomeRows.HomeOpaqueDock),
                                    checked = preferences.homeOpaqueDock,
                                    highlighted = highlightSettingId == HomeRows.HomeOpaqueDock.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeOpaqueDock(it) } },
                                )
                            }
                            HomeRows.HomeGreeting.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeGreeting),
                                    title = rowTitle(HomeRows.HomeGreeting),
                                    subtitle = rowSubtitle(HomeRows.HomeGreeting),
                                    checked = preferences.homeGreetingEnabled,
                                    highlighted = highlightSettingId == HomeRows.HomeGreeting.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeGreetingEnabled(it) } },
                                )
                            }
                            HomeRows.HomeShowModeSwitch.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeShowModeSwitch),
                                    title = rowTitle(HomeRows.HomeShowModeSwitch),
                                    subtitle = rowSubtitle(HomeRows.HomeShowModeSwitch),
                                    checked = preferences.homeShowModeSwitch,
                                    highlighted = highlightSettingId == HomeRows.HomeShowModeSwitch.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeShowModeSwitch(it) } },
                                )
                            }
                            HomeRows.HomeAlwaysShowUserSwitcher.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeAlwaysShowUserSwitcher),
                                    title = rowTitle(HomeRows.HomeAlwaysShowUserSwitcher),
                                    subtitle = rowSubtitle(HomeRows.HomeAlwaysShowUserSwitcher),
                                    checked = preferences.homeAlwaysShowUserSwitcher,
                                    highlighted = highlightSettingId == HomeRows.HomeAlwaysShowUserSwitcher.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeAlwaysShowUserSwitcher(it) } },
                                )
                            }
                            HomeRows.HomeNewsletterBanner.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeNewsletterBanner),
                                    title = rowTitle(HomeRows.HomeNewsletterBanner),
                                    subtitle = rowSubtitle(HomeRows.HomeNewsletterBanner),
                                    checked = preferences.homeNewsletterBannerEnabled,
                                    highlighted = highlightSettingId == HomeRows.HomeNewsletterBanner.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeNewsletterBannerEnabled(it) } },
                                )
                            }
                            HomeRows.HomeNewsletterWindow.id -> {
                                val newsletterWindowTitle = rowTitle(HomeRows.HomeNewsletterWindow)
                                val newsletterWindowLabels = mapOf(
                                    NewsletterBannerWindow.DEFAULT to stringResource(Res.string.settings_newsletter_window_default),
                                    NewsletterBannerWindow.ALWAYS to stringResource(Res.string.settings_newsletter_window_always),
                                    NewsletterBannerWindow.WEEKENDS to stringResource(Res.string.settings_newsletter_window_weekends),
                                )
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeNewsletterWindow),
                                    title = newsletterWindowTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeNewsletterWindow),
                                    trailingText = newsletterWindowLabels[preferences.homeNewsletterWindow]
                                        ?: preferences.homeNewsletterWindow.name,
                                    highlighted = highlightSettingId == HomeRows.HomeNewsletterWindow.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = newsletterWindowTitle,
                                            items = NewsletterBannerWindow.entries,
                                            label = { newsletterWindowLabels.getValue(it) },
                                            isSelected = { it == preferences.homeNewsletterWindow },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeNewsletterWindow(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeHideSeeAll.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeHideSeeAll),
                                    title = rowTitle(HomeRows.HomeHideSeeAll),
                                    subtitle = rowSubtitle(HomeRows.HomeHideSeeAll),
                                    checked = preferences.homeHideSeeAll,
                                    highlighted = highlightSettingId == HomeRows.HomeHideSeeAll.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHideSeeAll(it) } },
                                )
                            }
                            HomeRows.HomeCardScale.id -> {
                                val cardScaleTitle = rowTitle(HomeRows.HomeCardScale)
                                val cardScaleLabels = mapOf(
                                    HomeCardScale.SMALL to stringResource(Res.string.settings_card_scale_small),
                                    HomeCardScale.DEFAULT to stringResource(Res.string.settings_card_scale_default),
                                    HomeCardScale.LARGE to stringResource(Res.string.settings_card_scale_large),
                                )
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeCardScale),
                                    title = cardScaleTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeCardScale),
                                    trailingText = cardScaleLabels[preferences.homeCardScale]
                                        ?: preferences.homeCardScale.name,
                                    highlighted = highlightSettingId == HomeRows.HomeCardScale.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = cardScaleTitle,
                                            items = HomeCardScale.entries,
                                            label = { cardScaleLabels.getValue(it) },
                                            isSelected = { it == preferences.homeCardScale },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeCardScale(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeCardCornerRadius.id -> {
                                val cardRadiusTitle = rowTitle(HomeRows.HomeCardCornerRadius)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeCardCornerRadius),
                                    title = cardRadiusTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeCardCornerRadius),
                                    trailingText = "${preferences.homeCardCornerRadius}dp",
                                    highlighted = highlightSettingId == HomeRows.HomeCardCornerRadius.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = cardRadiusTitle,
                                            items = listOf(4, 8, 10, 12, 14, 16, 20, 24),
                                            label = { "${it}dp" },
                                            isSelected = { it == preferences.homeCardCornerRadius },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeCardCornerRadius(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeRowsAnimations.id -> {
                                SettingToggleItem(
                                    icon = rowIcon(HomeRows.HomeRowsAnimations),
                                    title = rowTitle(HomeRows.HomeRowsAnimations),
                                    subtitle = rowSubtitle(HomeRows.HomeRowsAnimations),
                                    checked = preferences.homeRowsAnimations,
                                    highlighted = highlightSettingId == HomeRows.HomeRowsAnimations.id,
                                    onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeRowsAnimations(it) } },
                                )
                            }
                            HomeRows.HomeRefreshCadence.id -> {
                                val cadenceTitle = rowTitle(HomeRows.HomeRefreshCadence)
                                val cadenceLabels = mapOf(
                                    HomeRefreshCadence.DEFAULT to stringResource(Res.string.settings_refresh_cadence_default),
                                    HomeRefreshCadence.EAGER to stringResource(Res.string.settings_refresh_cadence_eager),
                                    HomeRefreshCadence.RELAXED to stringResource(Res.string.settings_refresh_cadence_relaxed),
                                    HomeRefreshCadence.MANUAL to stringResource(Res.string.settings_refresh_cadence_manual),
                                )
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeRefreshCadence),
                                    title = cadenceTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeRefreshCadence),
                                    trailingText = cadenceLabels[preferences.homeRefreshCadence]
                                        ?: preferences.homeRefreshCadence.name,
                                    highlighted = highlightSettingId == HomeRows.HomeRefreshCadence.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = cadenceTitle,
                                            items = HomeRefreshCadence.entries,
                                            label = { cadenceLabels.getValue(it) },
                                            isSelected = { it == preferences.homeRefreshCadence },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeRefreshCadence(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeDiscoverPattern.id -> {
                                val discoverPatternTitle = rowTitle(HomeRows.HomeDiscoverPattern)
                                val discoverPatternLabels = mapOf(
                                    HomeDiscoverPattern.DEFAULT to stringResource(Res.string.settings_discover_pattern_default),
                                    HomeDiscoverPattern.UNIFORM to stringResource(Res.string.settings_discover_pattern_uniform),
                                    HomeDiscoverPattern.COMPACT to stringResource(Res.string.settings_discover_pattern_compact),
                                )
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeDiscoverPattern),
                                    title = discoverPatternTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeDiscoverPattern),
                                    trailingText = discoverPatternLabels[preferences.homeDiscoverPattern]
                                        ?: preferences.homeDiscoverPattern.name,
                                    highlighted = highlightSettingId == HomeRows.HomeDiscoverPattern.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = discoverPatternTitle,
                                            items = HomeDiscoverPattern.entries,
                                            label = { discoverPatternLabels.getValue(it) },
                                            isSelected = { it == preferences.homeDiscoverPattern },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeDiscoverPattern(it) } },
                                        )
                                    },
                                )
                            }
                            // The pre-section block's order — single-select
                            // presets over the orderable items (the picker
                            // vocabulary is single-select; the presets are the
                            // sane arrangements).
                            HomeRows.HomePreSectionOrder.id -> {
                                val preSectionTitle = rowTitle(HomeRows.HomePreSectionOrder)
                                val statsLabel = stringResource(Res.string.settings_pre_section_stats_card)
                                val genreLabel = stringResource(Res.string.settings_pre_section_genre_rail)
                                val newsletterLabel = stringResource(Res.string.settings_pre_section_newsletter_banner)
                                val orderPresets = listOf(
                                    HomeDiscoveryPreferenceSpecsDefaults.PRE_SECTION_ORDER_CANONICAL,
                                    listOf("newsletter_banner", "stats_card", "genre_rail"),
                                    listOf("genre_rail", "stats_card", "newsletter_banner"),
                                )
                                fun orderLabel(tokens: List<String>): String = tokens.map { token ->
                                    when (token) {
                                        "stats_card" -> statsLabel
                                        "genre_rail" -> genreLabel
                                        else -> newsletterLabel
                                    }
                                }.joinToString(" → ")
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomePreSectionOrder),
                                    title = preSectionTitle,
                                    subtitle = rowSubtitle(HomeRows.HomePreSectionOrder),
                                    trailingText = orderPresets
                                        .find { it == preferences.homePreSectionOrder }
                                        ?.let { orderLabel(it) }
                                        ?: stringResource(Res.string.settings_custom),
                                    highlighted = highlightSettingId == HomeRows.HomePreSectionOrder.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = preSectionTitle,
                                            items = orderPresets,
                                            label = { orderLabel(it) },
                                            isSelected = { it == preferences.homePreSectionOrder },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomePreSectionOrder(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeSearchResultsHeight.id -> {
                                val searchHeightTitle = rowTitle(HomeRows.HomeSearchResultsHeight)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeSearchResultsHeight),
                                    title = searchHeightTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeSearchResultsHeight),
                                    trailingText = "${preferences.homeSearchResultsHeightDp}dp",
                                    highlighted = highlightSettingId == HomeRows.HomeSearchResultsHeight.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = searchHeightTitle,
                                            items = listOf(280, 320, 400, 480, 560, 640),
                                            label = { "${it}dp" },
                                            isSelected = { it == preferences.homeSearchResultsHeightDp },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeSearchResultsHeightDp(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeMyMediaColumns.id -> {
                                val tilesColumnsTitle = rowTitle(HomeRows.HomeMyMediaColumns)
                                val autoLabel = stringResource(Res.string.settings_tiles_columns_auto)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeMyMediaColumns),
                                    title = tilesColumnsTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeMyMediaColumns),
                                    trailingText = if (preferences.homeMyMediaColumns == 0) autoLabel else "${preferences.homeMyMediaColumns}",
                                    highlighted = highlightSettingId == HomeRows.HomeMyMediaColumns.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = tilesColumnsTitle,
                                            items = listOf(0, 2, 3, 4, 5, 6),
                                            label = { if (it == 0) autoLabel else "$it" },
                                            isSelected = { it == preferences.homeMyMediaColumns },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeMyMediaColumns(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomeGenreRailLimit.id -> {
                                val genreLimitTitle = rowTitle(HomeRows.HomeGenreRailLimit)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomeGenreRailLimit),
                                    title = genreLimitTitle,
                                    subtitle = rowSubtitle(HomeRows.HomeGenreRailLimit),
                                    trailingText = "${preferences.homeGenreRailLimit}",
                                    highlighted = highlightSettingId == HomeRows.HomeGenreRailLimit.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = genreLimitTitle,
                                            items = listOf(6, 12, 18, 24, 30, 36),
                                            label = { "$it" },
                                            isSelected = { it == preferences.homeGenreRailLimit },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeGenreRailLimit(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomePresenceChipsLimit.id -> {
                                val presenceLimitTitle = rowTitle(HomeRows.HomePresenceChipsLimit)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomePresenceChipsLimit),
                                    title = presenceLimitTitle,
                                    subtitle = rowSubtitle(HomeRows.HomePresenceChipsLimit),
                                    trailingText = "${preferences.homePresenceChipsLimit}",
                                    highlighted = highlightSettingId == HomeRows.HomePresenceChipsLimit.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = presenceLimitTitle,
                                            items = listOf(2, 3, 4, 5, 6, 7, 8),
                                            label = { "$it" },
                                            isSelected = { it == preferences.homePresenceChipsLimit },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomePresenceChipsLimit(it) } },
                                        )
                                    },
                                )
                            }
                            HomeRows.HomePresencePollSeconds.id -> {
                                val pollSecondsTitle = rowTitle(HomeRows.HomePresencePollSeconds)
                                SettingListItem(
                                    icon = rowIcon(HomeRows.HomePresencePollSeconds),
                                    title = pollSecondsTitle,
                                    subtitle = rowSubtitle(HomeRows.HomePresencePollSeconds),
                                    trailingText = "${preferences.homePresencePollSeconds}s",
                                    highlighted = highlightSettingId == HomeRows.HomePresencePollSeconds.id,
                                    onClick = {
                                        activePicker.value = PickerState.List(
                                            title = pollSecondsTitle,
                                            items = listOf(15, 30, 60, 90, 120, 180, 300),
                                            label = { "${it}s" },
                                            isSelected = { it == preferences.homePresencePollSeconds },
                                            onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomePresencePollSeconds(it) } },
                                        )
                                    },
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
        },
        SettingsScreenGroups.homeNextUp.asScreenGroup { activePicker ->
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

                    val cwProgressStyleTitle = rowTitle(HomeRows.HomeCwProgressStyle)
                    val progressStyleLabels = mapOf(
                        HomeCwProgressStyle.DEFAULT to stringResource(Res.string.settings_progress_style_default),
                        HomeCwProgressStyle.THICK to stringResource(Res.string.settings_progress_style_thick),
                        HomeCwProgressStyle.PERCENT to stringResource(Res.string.settings_progress_style_percent),
                    )
                    SettingListItem(
                        icon = rowIcon(HomeRows.HomeCwProgressStyle),
                        title = cwProgressStyleTitle,
                        subtitle = rowSubtitle(HomeRows.HomeCwProgressStyle),
                        trailingText = progressStyleLabels[preferences.homeCwProgressStyle]
                            ?: preferences.homeCwProgressStyle.name,
                        highlighted = highlightSettingId == HomeRows.HomeCwProgressStyle.id,
                        index = 3, count = nextUpTotal,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = cwProgressStyleTitle,
                                items = HomeCwProgressStyle.entries,
                                label = { progressStyleLabels.getValue(it) },
                                isSelected = { it == preferences.homeCwProgressStyle },
                                onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeCwProgressStyle(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = rowIcon(HomeRows.HomeAskContinueRemember),
                        title = rowTitle(HomeRows.HomeAskContinueRemember),
                        subtitle = rowSubtitle(HomeRows.HomeAskContinueRemember),
                        checked = preferences.homeAskContinueRemember,
                        highlighted = highlightSettingId == HomeRows.HomeAskContinueRemember.id,
                        index = 4, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeAskContinueRemember(it) } },
                    )

                    SettingToggleItem(
                        icon = Tabler.Outline.Ban,
                        title = rowTitle(HomeRows.ExcludeActiveSessions),
                        subtitle = if (preferences.excludeActiveSessions) stringResource(Res.string.settings_exclude_active_sessions_on) else stringResource(Res.string.settings_exclude_active_sessions_off),
                        checked = preferences.excludeActiveSessions,
                        highlighted = highlightSettingId == HomeRows.ExcludeActiveSessions.id,
                        index = 5, count = nextUpTotal,
                        onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setExcludeActiveSessions(it) } },
                    )
                }
        },
        SettingsScreenGroups.homeLayout.asScreenGroup { activePicker ->
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

                    // The header dock's shortcuts — the sibling customization
                    // surface. The editor opens on the header tab; the
                    // Navigation settings row opens the same editor on the
                    // menu tab. A plain action row (the upload/restore
                    // precedent — no spec-backed declaration).
                    SettingListItem(
                        icon = Tabler.Outline.Pin,
                        title = stringResource(Res.string.settings_home_header_shortcuts),
                        subtitle = stringResource(Res.string.settings_home_header_shortcuts_subtitle),
                        trailingText = "",
                        highlighted = false,
                        index = 0, count = 1,
                        onClick = {
                            navActions.onNavigate(
                                Route.ShortcutsCustomize(com.raulshma.jellyplay.core.model.ShortcutSurface.HEADER),
                            )
                        },
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
        },
        SettingsScreenGroups.homeCards.asScreenGroup { activePicker ->
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

                        // The NEW badge's per-section gate — single-select
                        // presets over the badge-capable rows (the picker
                        // vocabulary is single-select).
                        run {
                            val badgeSectionsTitle = rowTitle(HomeRows.HomeNewBadgeSections)
                            val badgeSectionPresets = listOf(
                                setOf(HomeSectionType.LATEST_MEDIA, HomeSectionType.RECENTLY_ADDED, HomeSectionType.UPCOMING) to
                                    stringResource(Res.string.settings_new_badge_sections_all),
                                setOf(HomeSectionType.LATEST_MEDIA) to
                                    stringResource(Res.string.settings_new_badge_sections_latest),
                                setOf(HomeSectionType.RECENTLY_ADDED) to
                                    stringResource(Res.string.settings_new_badge_sections_recent),
                                setOf(HomeSectionType.UPCOMING) to
                                    stringResource(Res.string.settings_new_badge_sections_upcoming),
                                emptySet<HomeSectionType>() to
                                    stringResource(Res.string.settings_new_badge_sections_none),
                            )
                            SettingListItem(
                                icon = rowIcon(HomeRows.HomeNewBadgeSections),
                                title = badgeSectionsTitle,
                                subtitle = rowSubtitle(HomeRows.HomeNewBadgeSections),
                                trailingText = badgeSectionPresets
                                    .find { it.first == preferences.homeNewBadgeSections }
                                    ?.second
                                    ?: stringResource(Res.string.settings_custom),
                                highlighted = highlightSettingId == HomeRows.HomeNewBadgeSections.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = badgeSectionsTitle,
                                        items = badgeSectionPresets,
                                        label = { it.second },
                                        isSelected = { it.first == preferences.homeNewBadgeSections },
                                        onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeNewBadgeSections(it.first) } },
                                    )
                                },
                            )
                        }

                        val badgeDaysTitle = rowTitle(HomeRows.HomeNewBadgeDays)
                        val badgeDaysFormat = stringResource(Res.string.settings_x_days)
                        SettingListItem(
                            icon = rowIcon(HomeRows.HomeNewBadgeDays),
                            title = badgeDaysTitle,
                            subtitle = rowSubtitle(HomeRows.HomeNewBadgeDays),
                            trailingText = formatIntPattern(badgeDaysFormat, preferences.homeNewBadgeDays),
                            highlighted = highlightSettingId == HomeRows.HomeNewBadgeDays.id,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = badgeDaysTitle,
                                    items = listOf(1, 3, 7, 14, 21, 30),
                                    label = { formatIntPattern(badgeDaysFormat, it) },
                                    isSelected = { it == preferences.homeNewBadgeDays },
                                    onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeNewBadgeDays(it) } },
                                )
                            },
                        )

                        SettingToggleItem(
                            icon = rowIcon(HomeRows.HomeRowUnwatchedCounters),
                            title = rowTitle(HomeRows.HomeRowUnwatchedCounters),
                            subtitle = rowSubtitle(HomeRows.HomeRowUnwatchedCounters),
                            checked = preferences.homeRowUnwatchedCounters,
                            highlighted = highlightSettingId == HomeRows.HomeRowUnwatchedCounters.id,
                            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeRowUnwatchedCounters(it) } },
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
                            // The peek's hold time rides the TV-only toggle.
                            val peekHoldTitle = rowTitle(HomeRows.HomeTrickplayPeekMs)
                            SettingListItem(
                                icon = rowIcon(HomeRows.HomeTrickplayPeekMs),
                                title = peekHoldTitle,
                                subtitle = rowSubtitle(HomeRows.HomeTrickplayPeekMs),
                                trailingText = "${preferences.homeTrickplayPeekMs / 1000.0}s",
                                highlighted = highlightSettingId == HomeRows.HomeTrickplayPeekMs.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = peekHoldTitle,
                                        items = listOf(1000, 1500, 2000, 2500, 3000, 4000, 5000),
                                        label = { "${it / 1000.0}s" },
                                        isSelected = { it == preferences.homeTrickplayPeekMs },
                                        onSelect = { viewModel.edit { scope -> scope.homeDiscovery.setHomeTrickplayPeekMs(it) } },
                                    )
                                },
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
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_title),
        onBack = onBack,
        focusTag = "home_settings_init",
        highlightSettingId = highlightSettingId,
        groups = homeGroups,
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_defaults_cd),
            dialogTitle = stringResource(Res.string.settings_reset_home_title),
            dialogMessage = stringResource(Res.string.settings_reset_home_message),
            onReset = { viewModel.resetCategory(PreferenceResetCategory.HOME_DISCOVERY) },
        ),
        pickerHost = true,
    )

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
