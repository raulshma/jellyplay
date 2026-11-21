package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowBarToDown
import com.composables.icons.tabler.outline.Background
import com.composables.icons.tabler.outline.ChartBar
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Category
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Focus
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.LayersLinked
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Speakerphone
import com.composables.icons.tabler.outline.Adjustments
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.Video
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.HomeDiscoveryPreferenceSpecsDefaults
import com.raulshma.jellyplay.core.model.HomeDiscoverPattern
import com.raulshma.jellyplay.core.model.HomeHeroStyle
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.NewsletterBannerWindow
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_compact
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_discover_pattern_uniform
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_hide_top_header_on_scroll_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_display
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_entry_display
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_focus_backdrop_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_genre_rail_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_hero_video_tv_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_music
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_mode_video
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_always
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_window_weekends
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_presence_chips_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_stats_card_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_section_announcements_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_genre_rail
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_newsletter_banner
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_pre_section_stats_card
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_clock_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_hero_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_show_settings_in_home_search_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_no_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_no_unplayed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_stats_metric_played_only
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_tiles_columns_auto

/**
 * The Home display leaf's ONE ordered slot declaration (ADR-0009 amendment;
 * ADR-0017's home split): the mainstream presentation group — the former
 * `home.display` group's mainstream remainder. No advanced tier (every row
 * renders unconditionally except the slideshow-interval row's WhenOn parent).
 * The contract test pins the slot/group parity against this same declaration.
 */
internal val HomeDisplaySettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.homeDisplayMain.asScreenGroup(),
)

/**
 * The Home display LEAF (ADR-0017's home split): the mainstream
 * home-presentation rows behind the hub's Home display entrance — home mode,
 * the hero/backdrop cluster (with its gates), the clock/dock/chrome toggles,
 * the stats-card and genre-rail/presence features and their presentation
 * picks. The advanced tier (the Fine-tuning group) stayed on the hub — the
 * glossary's declared hub exception — so this leaf carries no advanced
 * toggle and no hidden hint.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeDisplaySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_entry_display),
        onBack = onBack,
        focusTag = "home_display_init",
        highlightSettingId = highlightSettingId,
        groups = HomeDisplaySettingsScreenSlots.withContent(
            bodies = listOf(
                { activePicker ->
                    HomeDisplayMainGroupBody(
                        preferences = preferences,
                        highlightSettingId = highlightSettingId,
                        viewModel = viewModel,
                        activePicker = activePicker,
                    )
                },
            ),
        ),
        pickerHost = true,
    )
}

/**
 * The `home.displayMain` group body: the mainstream presentation rows in the
 * declared catalog order, the hero-slideshow interval only while its
 * WhenOn(parent = style = slideshow) gate admits it — the same derivation the
 * total reads ([homeDisplayMainScreenRowTotal]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeDisplayMainGroupBody(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    viewModel: HomeSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
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
        // Derived from the declared display-main group (the derivation source
        // the row total below reads — one declaration, no parallel id list):
        // the always-rendered rows in catalog order, the hero-slideshow
        // interval only while its WhenOn parent (style = slideshow) is on.
        val heroSlideshowOn = preferences.homeHeroStyle == HomeHeroStyle.SLIDESHOW
        val displayItems = remember(preferences.homeHeroStyle) {
            SettingsScreenGroups.homeDisplayMain.itemIds
                .filter { heroSlideshowOn || it != HomeRows.HomeHeroSlideshowInterval.id }
        }
        SettingsItemList(total = homeDisplayMainScreenRowTotal(heroSlideshowOn)) {
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
                }
            }
        }
    }
}
