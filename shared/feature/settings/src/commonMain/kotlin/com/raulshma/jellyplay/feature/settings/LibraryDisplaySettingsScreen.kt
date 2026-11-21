package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_entry_library_views
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_all_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_share_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_skip_specials

@Composable
private fun appearanceLibrarySummary(preferences: AppearanceScreenPreferences): String {
    // The group's remaining rows; the home-discovery card-display quartet
    // lives on HomeSettingsScreen, so its terms stay out of the summary.
    val hideThumbnails = if (preferences.hideEpisodeThumbnails) stringResource(Res.string.settings_summary_hide_thumbnails) else null
    val skipSpecials = if (preferences.skipSpecials) stringResource(Res.string.settings_summary_skip_specials) else null
    val shareOpt = if (preferences.showShareMediaOption) stringResource(Res.string.settings_summary_share_button) else null
    return listOfNotNull(hideThumbnails, skipSpecials, shareOpt)
        .joinToString(", ")
        .ifEmpty { stringResource(Res.string.settings_summary_all_hidden) }
}

/**
 * The `appearance.libraryViews` group (ADR-0017's split of the former
 * "Library & Cards" group): the library/card browsing toggles, shown
 * regardless of Advanced mode. The detail-screen rows moved to
 * [AppearanceDetailGroup] (the Detail screen leaf); the screen-local
 * confirm-library-reset action row stays beside the toggles it guards.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun AppearanceLibraryViewsGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.LayoutGrid,
        title = stringResource(Res.string.settings_library_cards),
        summary = { appearanceLibrarySummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceLibraryViews.itemIdSet,
    ) {
        // The declared library rows plus the confirm-library-reset
        // action row (a screen-local row with no search entry) —
        // the admission total beside SettingsScreenGroups.
        SettingsItemList(total = appearanceLibraryViewsScreenRowTotal()) {

        SettingToggleItem(
            icon = Tabler.Outline.PhotoOff,
            title = rowTitle(AppearanceRows.HideEpisodeThumbnails),
            subtitle = rowSubtitle(AppearanceRows.HideEpisodeThumbnails),
            checked = preferences.hideEpisodeThumbnails,
            highlighted = highlightSettingId == AppearanceRows.HideEpisodeThumbnails.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setHideEpisodeThumbnails(it) } },
        )

        // The card-badge rows: a second face on the home cards
        // group's prefs — these write the SAME HomeDiscoveryStore commands
        // (the cross-store shape the home-discovery fields in this group's
        // slice already establish), so either screen's toggle moves both.
        SettingToggleItem(
            icon = rowIcon(AppearanceRows.QualityBadges),
            title = rowTitle(AppearanceRows.QualityBadges),
            subtitle = rowSubtitle(AppearanceRows.QualityBadges),
            checked = preferences.showQualityBadges,
            highlighted = highlightSettingId == AppearanceRows.QualityBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowQualityBadges(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.NewBadges),
            title = rowTitle(AppearanceRows.NewBadges),
            subtitle = rowSubtitle(AppearanceRows.NewBadges),
            checked = preferences.showNewBadges,
            highlighted = highlightSettingId == AppearanceRows.NewBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setShowNewBadges(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.List,
            title = rowTitle(AppearanceRows.CompactEpisodeList),
            subtitle = rowSubtitle(AppearanceRows.CompactEpisodeList),
            checked = preferences.compactEpisodeList,
            highlighted = highlightSettingId == AppearanceRows.CompactEpisodeList.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setCompactEpisodeList(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Subtitles,
            title = rowTitle(AppearanceRows.ShowEpisodeStreamBadges),
            subtitle = rowSubtitle(AppearanceRows.ShowEpisodeStreamBadges),
            checked = preferences.showEpisodeStreamBadges,
            highlighted = highlightSettingId == AppearanceRows.ShowEpisodeStreamBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowEpisodeStreamBadges(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.AlertTriangle,
            title = stringResource(Res.string.settings_confirm_library_reset),
            subtitle = stringResource(Res.string.settings_confirm_library_reset_subtitle),
            checked = preferences.confirmLibraryReset,
            highlighted = highlightSettingId == "confirm_library_reset",
            onCheckedChange = { viewModel.edit { scope -> scope.library.setConfirmLibraryReset(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.PlayerSkipForward,
            title = rowTitle(AppearanceRows.SkipSpecials),
            subtitle = rowSubtitle(AppearanceRows.SkipSpecials),
            checked = preferences.skipSpecials,
            highlighted = highlightSettingId == AppearanceRows.SkipSpecials.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setSkipSpecials(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Eye,
            title = rowTitle(AppearanceRows.ShowMissingEpisodes),
            subtitle = rowSubtitle(AppearanceRows.ShowMissingEpisodes),
            checked = preferences.showMissingEpisodes,
            highlighted = highlightSettingId == AppearanceRows.ShowMissingEpisodes.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowMissingEpisodes(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Photo,
            title = rowTitle(AppearanceRows.PreferLogos),
            subtitle = rowSubtitle(AppearanceRows.PreferLogos),
            checked = preferences.preferLogos,
            highlighted = highlightSettingId == AppearanceRows.PreferLogos.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setPreferLogos(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Quote,
            title = rowTitle(AppearanceRows.ShowTagline),
            subtitle = rowSubtitle(AppearanceRows.ShowTagline),
            checked = preferences.showTagline,
            highlighted = highlightSettingId == AppearanceRows.ShowTagline.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowTagline(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Music,
            title = rowTitle(AppearanceRows.PlayThemeMusic),
            subtitle = rowSubtitle(AppearanceRows.PlayThemeMusic),
            checked = preferences.playThemeMusic,
            highlighted = highlightSettingId == AppearanceRows.PlayThemeMusic.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setPlayThemeMusic(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.DeviceMobileVibration,
            title = rowTitle(AppearanceRows.HapticsEnabled),
            subtitle = rowSubtitle(AppearanceRows.HapticsEnabled),
            checked = preferences.hapticsEnabled,
            highlighted = highlightSettingId == AppearanceRows.HapticsEnabled.id,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setHapticsEnabled(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Share,
            title = rowTitle(AppearanceRows.ShowShareMedia),
            subtitle = rowSubtitle(AppearanceRows.ShowShareMedia),
            checked = preferences.showShareMediaOption,
            highlighted = highlightSettingId == AppearanceRows.ShowShareMedia.id,
            onCheckedChange = { viewModel.edit { scope -> scope.experimental.setShowShareMediaOption(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Link,
            title = rowTitle(AppearanceRows.ShowExternalLinks),
            subtitle = rowSubtitle(AppearanceRows.ShowExternalLinks),
            checked = preferences.showExternalLinks,
            highlighted = highlightSettingId == AppearanceRows.ShowExternalLinks.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowExternalLinks(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Star,
            title = rowTitle(AppearanceRows.ShowReviews),
            subtitle = rowSubtitle(AppearanceRows.ShowReviews),
            checked = preferences.showReviews,
            highlighted = highlightSettingId == AppearanceRows.ShowReviews.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowReviews(it) } },
        )

        // Renders beside the reviews toggle — the other details-page
        // ratings/awards display knob (appearance-store-backed).
        SettingToggleItem(
            icon = rowIcon(AppearanceRows.AwardsBadges),
            title = rowTitle(AppearanceRows.AwardsBadges),
            subtitle = rowSubtitle(AppearanceRows.AwardsBadges),
            checked = preferences.detailsAwardsBadgesEnabled,
            highlighted = highlightSettingId == AppearanceRows.AwardsBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setDetailsAwardsBadgesEnabled(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Movie,
            title = rowTitle(AppearanceRows.PreferLocalTrailers),
            subtitle = rowSubtitle(AppearanceRows.PreferLocalTrailers),
            checked = preferences.preferLocalTrailers,
            highlighted = highlightSettingId == AppearanceRows.PreferLocalTrailers.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setPreferLocalTrailers(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.DeviceTv,
            title = rowTitle(AppearanceRows.TrailerPreviewOnFocus),
            subtitle = rowSubtitle(AppearanceRows.TrailerPreviewOnFocus),
            checked = preferences.trailerPreviewOnFocus,
            highlighted = highlightSettingId == AppearanceRows.TrailerPreviewOnFocus.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setTrailerPreviewOnFocus(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.PlayerPlay,
            title = rowTitle(AppearanceRows.ShowPromotedActionsLandscape),
            subtitle = rowSubtitle(AppearanceRows.ShowPromotedActionsLandscape),
            checked = preferences.showPromotedActionsLandscape,
            highlighted = highlightSettingId == AppearanceRows.ShowPromotedActionsLandscape.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowPromotedActionsLandscape(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Download,
            title = rowTitle(AppearanceRows.ShowDownloadProgress),
            subtitle = rowSubtitle(AppearanceRows.ShowDownloadProgress),
            checked = preferences.showDownloadProgress,
            highlighted = highlightSettingId == AppearanceRows.ShowDownloadProgress.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowDownloadProgress(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.CalendarEvent,
            title = rowTitle(AppearanceRows.ShowUpcomingStrip),
            subtitle = rowSubtitle(AppearanceRows.ShowUpcomingStrip),
            checked = preferences.showUpcomingStrip,
            highlighted = highlightSettingId == AppearanceRows.ShowUpcomingStrip.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowUpcomingStrip(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.Stack2,
            title = rowTitle(AppearanceRows.ShowMoreInCollection),
            subtitle = rowSubtitle(AppearanceRows.ShowMoreInCollection),
            checked = preferences.showMoreInCollection,
            highlighted = highlightSettingId == AppearanceRows.ShowMoreInCollection.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowMoreInCollection(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.EyeOff,
            title = rowTitle(AppearanceRows.HideSearchHistory),
            subtitle = rowSubtitle(AppearanceRows.HideSearchHistory),
            checked = preferences.hideSearchHistory,
            highlighted = highlightSettingId == AppearanceRows.HideSearchHistory.id,
            onCheckedChange = { viewModel.edit { scope -> scope.experimental.setHideSearchHistory(it) } },
        )
        }
    }
}

/**
 * The Library views LEAF (ADR-0017's appearance split): the library/card
 * browsing rows behind the hub's Library views entrance — the former "Library
 * & Cards" group's browsing half (episode lists, badges, logos, trailers,
 * haptics, the search-history toggle) plus the screen-local
 * confirm-library-reset action row. The rows stay always-visible exactly as
 * on the former combined screen (the leaf carries no advanced tier, so no
 * advanced toggle).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryDisplaySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val libraryViewsGroups = listOf(
        SettingsScreenGroups.appearanceLibraryViews.asScreenGroup(
            content = { _ ->
                AppearanceLibraryViewsGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_entry_library_views),
        onBack = onBack,
        focusTag = "appearance_library_views_init",
        highlightSettingId = highlightSettingId,
        groups = libraryViewsGroups,
        pickerHost = true,
    )
}
