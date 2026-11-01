package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.DetailBackdropResolution
import com.raulshma.jellyplay.core.model.DetailChapterCardSize
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.DetailStaggerStyle
import com.raulshma.jellyplay.core.model.FilmographySort
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.model.PromotedRailSource
import com.raulshma.jellyplay.core.model.SmartPlayPriority
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_comfort_eye_care
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm_library_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_friday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_monday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_saturday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_sunday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_thursday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_tuesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_wednesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_airing_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_cast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_chapters
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_franchise
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_media_info
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_overview
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_reviews
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_seasons
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_similar
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_detail_section_up_next
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_sections_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_section_order_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_standard_experience
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_all_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_share_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_skip_specials
import org.jetbrains.compose.resources.StringResource

/**
 * The detail section-order editor's rows: the persisted keys ARE the detail
 * feature's section-kind names (the `detail_section_order` CSV's vocabulary),
 * declared here as (key, label) descriptors — the NAV_ITEMS pattern. The
 * consumption side reconciles the stored permutation against its own known
 * list, so this UI vocabulary drifting from the feature's enum degrades to
 * "those tokens ignored" — never a broken detail body. The pinned chrome
 * sections (header / action row / book reading card) are deliberately absent:
 * they are not reorderable.
 */
private data class DetailSectionOrderDescriptor(
    val key: String,
    val labelRes: StringResource,
)

private val DETAIL_SECTION_ORDER_SECTIONS: List<DetailSectionOrderDescriptor> = listOf(
    DetailSectionOrderDescriptor("MEDIA_INFO", Res.string.settings_detail_section_media_info),
    DetailSectionOrderDescriptor("OVERVIEW", Res.string.settings_detail_section_overview),
    DetailSectionOrderDescriptor("CHAPTERS_OR_TOC", Res.string.settings_detail_section_chapters),
    DetailSectionOrderDescriptor("UP_NEXT", Res.string.settings_detail_section_up_next),
    DetailSectionOrderDescriptor("UPCOMING_EPISODES", Res.string.settings_detail_section_airing_next),
    DetailSectionOrderDescriptor("SEASONS", Res.string.settings_detail_section_seasons),
    DetailSectionOrderDescriptor("MORE_IN_COLLECTION", Res.string.settings_detail_section_franchise),
    DetailSectionOrderDescriptor("CAST", Res.string.settings_detail_section_cast),
    DetailSectionOrderDescriptor("MORE_LIKE_THIS", Res.string.settings_detail_section_similar),
    DetailSectionOrderDescriptor("TMDB_REVIEWS", Res.string.settings_detail_section_reviews),
)

/**
 * The stream-fallback language picker's common 3-letter codes (ISO 639-2/B
 * where distinct): the same vocabulary the store setter validates
 * (lowercased 3-letter tokens only, else the "eng" default).
 */
internal val STREAM_FALLBACK_LANGUAGE_CODES: List<String> = listOf(
    "und", "eng", "jpn", "ger", "fre", "spa", "ita", "por", "rus", "kor", "chi", "fra",
)

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

@Composable
private fun appearancePerformanceSummary(preferences: AppearanceScreenPreferences): String {
    val parts = mutableListOf<String>()
    if (preferences.performanceMode) parts.add(stringResource(Res.string.settings_performance_mode))
    if (preferences.reduceMotionEnabled) parts.add(stringResource(Res.string.settings_reduced_motion))
    return parts.joinToString(", ").ifEmpty { stringResource(Res.string.settings_standard_experience) }
}

@Composable
private fun appearanceEyeCareSummary(preferences: AppearanceScreenPreferences): String =
    if (preferences.blueLightFilterEnabled) {
        stringResource(Res.string.settings_blue_light_filter_summary, (preferences.blueLightFilterStrength * 100).toInt())
    } else {
        stringResource(Res.string.settings_off)
    }

@Composable
private fun appearanceNewsletterSummary(preferences: AppearanceScreenPreferences): String {
    val enabled = preferences.enabledNewsletterSections
    return if (preferences.newsletterEnabled) {
        stringResource(Res.string.settings_newsletter_sections_enabled, enabled.size, NewsletterSectionType.entries.size)
    } else {
        stringResource(Res.string.settings_disabled)
    }
}

// Legacy java.util.Calendar day-of-week numbers (the persisted
// `newsletterDayOfWeek` vocabulary): SUNDAY=1, MONDAY=2 … SATURDAY=7.
private const val CALENDAR_SUNDAY = 1
private const val CALENDAR_MONDAY = 2
private const val CALENDAR_TUESDAY = 3
private const val CALENDAR_WEDNESDAY = 4
private const val CALENDAR_THURSDAY = 5
private const val CALENDAR_FRIDAY = 6
private const val CALENDAR_SATURDAY = 7

/** The appearance screen's "Library & Cards" group: commonly-used display toggles, shown regardless of Advanced mode. */
@Composable
internal fun AppearanceLibraryGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.LayoutGrid,
        title = stringResource(Res.string.settings_library_cards),
        summary = { appearanceLibrarySummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceLibrary.itemIdSet,
    ) {
        // The declared library rows plus the confirm-library-reset
        // action row (a screen-local row with no search entry) —
        // the admission total beside SettingsScreenGroups.
        SettingsItemList(total = appearanceLibraryScreenRowTotal()) {

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
            icon = Tabler.Outline.Users,
            title = rowTitle(AppearanceRows.ShowFilmographyRoles),
            subtitle = rowSubtitle(AppearanceRows.ShowFilmographyRoles),
            checked = preferences.showFilmographyRoles,
            highlighted = highlightSettingId == AppearanceRows.ShowFilmographyRoles.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowFilmographyRoles(it) } },
        )

        SettingToggleItem(
            icon = Tabler.Outline.User,
            title = rowTitle(AppearanceRows.ShowFrequentCostars),
            subtitle = rowSubtitle(AppearanceRows.ShowFrequentCostars),
            checked = preferences.showFrequentCostars,
            highlighted = highlightSettingId == AppearanceRows.ShowFrequentCostars.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setShowFrequentCostars(it) } },
        )

        // ── The detail-screen QoL knobs: 10 toggles + 6 pickers (the two
        // DetailDensity rows and the four limit/lines rows). ────────────────

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.HideWatchedEpisodes),
            title = rowTitle(AppearanceRows.HideWatchedEpisodes),
            subtitle = rowSubtitle(AppearanceRows.HideWatchedEpisodes),
            checked = preferences.hideWatchedEpisodes,
            highlighted = highlightSettingId == AppearanceRows.HideWatchedEpisodes.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailHideWatchedEpisodes(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ConfirmMarkPlayed),
            title = rowTitle(AppearanceRows.ConfirmMarkPlayed),
            subtitle = rowSubtitle(AppearanceRows.ConfirmMarkPlayed),
            checked = preferences.confirmMarkPlayed,
            highlighted = highlightSettingId == AppearanceRows.ConfirmMarkPlayed.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailConfirmMarkPlayed(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.BackdropParallax),
            title = rowTitle(AppearanceRows.BackdropParallax),
            subtitle = rowSubtitle(AppearanceRows.BackdropParallax),
            checked = preferences.backdropParallax,
            highlighted = highlightSettingId == AppearanceRows.BackdropParallax.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailBackdropParallax(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowStudioChips),
            title = rowTitle(AppearanceRows.ShowStudioChips),
            subtitle = rowSubtitle(AppearanceRows.ShowStudioChips),
            checked = preferences.showStudioChips,
            highlighted = highlightSettingId == AppearanceRows.ShowStudioChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowStudioChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowGenreChips),
            title = rowTitle(AppearanceRows.ShowGenreChips),
            subtitle = rowSubtitle(AppearanceRows.ShowGenreChips),
            checked = preferences.showGenreChips,
            highlighted = highlightSettingId == AppearanceRows.ShowGenreChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowGenreChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowTagChips),
            title = rowTitle(AppearanceRows.ShowTagChips),
            subtitle = rowSubtitle(AppearanceRows.ShowTagChips),
            checked = preferences.showTagChips,
            highlighted = highlightSettingId == AppearanceRows.ShowTagChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowTagChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowSkipChips),
            title = rowTitle(AppearanceRows.ShowSkipChips),
            subtitle = rowSubtitle(AppearanceRows.ShowSkipChips),
            checked = preferences.showSkipChips,
            highlighted = highlightSettingId == AppearanceRows.ShowSkipChips.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowSkipChips(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ShowTrailerBadges),
            title = rowTitle(AppearanceRows.ShowTrailerBadges),
            subtitle = rowSubtitle(AppearanceRows.ShowTrailerBadges),
            checked = preferences.showTrailerBadges,
            highlighted = highlightSettingId == AppearanceRows.ShowTrailerBadges.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailShowTrailerBadges(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.PersistSectionHides),
            title = rowTitle(AppearanceRows.PersistSectionHides),
            subtitle = rowSubtitle(AppearanceRows.PersistSectionHides),
            checked = preferences.persistSectionHides,
            highlighted = highlightSettingId == AppearanceRows.PersistSectionHides.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailPersistSectionHides(it) } },
        )

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.SeasonTabsArtwork),
            title = rowTitle(AppearanceRows.SeasonTabsArtwork),
            subtitle = rowSubtitle(AppearanceRows.SeasonTabsArtwork),
            checked = preferences.seasonTabsArtwork,
            highlighted = highlightSettingId == AppearanceRows.SeasonTabsArtwork.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailSeasonTabsArtwork(it) } },
        )

        // The two DetailDensity pickers (cast rail geometry + Cast & Crew
        // grid density) and the four bounded int pickers, following the
        // theme group's enum-picker shape.
        val castItemSizeTitle = rowTitle(AppearanceRows.CastItemSize)
        SettingListItem(
            icon = rowIcon(AppearanceRows.CastItemSize),
            title = castItemSizeTitle,
            subtitle = rowSubtitle(AppearanceRows.CastItemSize),
            trailingText = preferences.castItemSize.displayName,
            highlighted = highlightSettingId == AppearanceRows.CastItemSize.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = castItemSizeTitle,
                    items = DetailDensity.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.castItemSize },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailCastItemSize(it) } },
                )
            },
        )

        val peopleGridDensityTitle = rowTitle(AppearanceRows.PeopleGridDensity)
        SettingListItem(
            icon = rowIcon(AppearanceRows.PeopleGridDensity),
            title = peopleGridDensityTitle,
            subtitle = rowSubtitle(AppearanceRows.PeopleGridDensity),
            trailingText = preferences.peopleGridDensity.displayName,
            highlighted = highlightSettingId == AppearanceRows.PeopleGridDensity.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = peopleGridDensityTitle,
                    items = DetailDensity.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.peopleGridDensity },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailPeopleGridDensity(it) } },
                )
            },
        )

        val castSeeAllLimitTitle = rowTitle(AppearanceRows.CastSeeAllLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.CastSeeAllLimit),
            title = castSeeAllLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.CastSeeAllLimit),
            trailingText = preferences.castSeeAllLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.CastSeeAllLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = castSeeAllLimitTitle,
                    // The store setter clamps to 6..24 (the row's declared
                    // bounds) — the picker offers exactly that range.
                    items = (6..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.castSeeAllLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailCastSeeAllLimit(it) } },
                )
            },
        )

        val overviewCollapseLinesTitle = rowTitle(AppearanceRows.OverviewCollapseLines)
        SettingListItem(
            icon = rowIcon(AppearanceRows.OverviewCollapseLines),
            title = overviewCollapseLinesTitle,
            subtitle = rowSubtitle(AppearanceRows.OverviewCollapseLines),
            trailingText = preferences.overviewCollapseLines.toString(),
            highlighted = highlightSettingId == AppearanceRows.OverviewCollapseLines.id,
            onClick = {
                // The shipped picker vocabulary: 3/4/5/6/8 (the sparse step
                // at the top; the setter clamps to the row's full range).
                activePicker.value = PickerState.List(
                    title = overviewCollapseLinesTitle,
                    items = listOf(3, 4, 5, 6, 8),
                    label = { it.toString() },
                    isSelected = { it == preferences.overviewCollapseLines },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailOverviewCollapseLines(it) } },
                )
            },
        )

        val similarLimitTitle = rowTitle(AppearanceRows.SimilarLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.SimilarLimit),
            title = similarLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.SimilarLimit),
            trailingText = preferences.similarItemsLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.SimilarLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = similarLimitTitle,
                    // The store setter clamps to 6..24 (the row's declared bounds).
                    items = (6..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.similarItemsLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailSimilarLimit(it) } },
                )
            },
        )

        val upcomingLimitTitle = rowTitle(AppearanceRows.UpcomingLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.UpcomingLimit),
            title = upcomingLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.UpcomingLimit),
            trailingText = preferences.upcomingEpisodesLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.UpcomingLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = upcomingLimitTitle,
                    // The store setter clamps to 4..24 (the row's declared bounds).
                    items = (4..24).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.upcomingEpisodesLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailUpcomingLimit(it) } },
                )
            },
        )

        // ── The detail QoL knobs, wave 2: 1 toggle + 6 enum pickers + 3
        // bounded-int pickers + 1 validated language picker + the section-
        // order header + its reorderable rows. ─────────────────────────────

        SettingToggleItem(
            icon = rowIcon(AppearanceRows.EpisodeOverviewPreview),
            title = rowTitle(AppearanceRows.EpisodeOverviewPreview),
            subtitle = rowSubtitle(AppearanceRows.EpisodeOverviewPreview),
            checked = preferences.episodeOverviewPreview,
            highlighted = highlightSettingId == AppearanceRows.EpisodeOverviewPreview.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setDetailEpisodeOverviewPreview(it) } },
        )

        val staggerStyleTitle = rowTitle(AppearanceRows.StaggerStyle)
        SettingListItem(
            icon = rowIcon(AppearanceRows.StaggerStyle),
            title = staggerStyleTitle,
            subtitle = rowSubtitle(AppearanceRows.StaggerStyle),
            trailingText = preferences.staggerStyle.displayName,
            highlighted = highlightSettingId == AppearanceRows.StaggerStyle.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = staggerStyleTitle,
                    items = DetailStaggerStyle.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.staggerStyle },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailStaggerStyle(it) } },
                )
            },
        )

        val backdropResolutionTitle = rowTitle(AppearanceRows.BackdropResolution)
        SettingListItem(
            icon = rowIcon(AppearanceRows.BackdropResolution),
            title = backdropResolutionTitle,
            subtitle = rowSubtitle(AppearanceRows.BackdropResolution),
            trailingText = preferences.backdropResolution.displayName,
            highlighted = highlightSettingId == AppearanceRows.BackdropResolution.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = backdropResolutionTitle,
                    items = DetailBackdropResolution.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.backdropResolution },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailBackdropResolution(it) } },
                )
            },
        )

        val chapterCardSizeTitle = rowTitle(AppearanceRows.ChapterCardSize)
        SettingListItem(
            icon = rowIcon(AppearanceRows.ChapterCardSize),
            title = chapterCardSizeTitle,
            subtitle = rowSubtitle(AppearanceRows.ChapterCardSize),
            trailingText = preferences.chapterCardSize.displayName,
            highlighted = highlightSettingId == AppearanceRows.ChapterCardSize.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = chapterCardSizeTitle,
                    items = DetailChapterCardSize.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.chapterCardSize },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailChapterCardSize(it) } },
                )
            },
        )

        val reviewsPreviewLinesTitle = rowTitle(AppearanceRows.ReviewsPreviewLines)
        SettingListItem(
            icon = rowIcon(AppearanceRows.ReviewsPreviewLines),
            title = reviewsPreviewLinesTitle,
            subtitle = rowSubtitle(AppearanceRows.ReviewsPreviewLines),
            trailingText = preferences.reviewsPreviewLines.toString(),
            highlighted = highlightSettingId == AppearanceRows.ReviewsPreviewLines.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = reviewsPreviewLinesTitle,
                    // The store setter clamps to 2..12 (the row's declared bounds).
                    items = (2..12).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.reviewsPreviewLines },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailReviewsPreviewLines(it) } },
                )
            },
        )

        val filmographySortTitle = rowTitle(AppearanceRows.FilmographySort)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FilmographySort),
            title = filmographySortTitle,
            subtitle = rowSubtitle(AppearanceRows.FilmographySort),
            trailingText = preferences.filmographySort.displayName,
            highlighted = highlightSettingId == AppearanceRows.FilmographySort.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = filmographySortTitle,
                    items = FilmographySort.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.filmographySort },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFilmographySort(it) } },
                )
            },
        )

        val franchiseBoxLimitTitle = rowTitle(AppearanceRows.FranchiseBoxLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FranchiseBoxLimit),
            title = franchiseBoxLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.FranchiseBoxLimit),
            trailingText = preferences.franchiseBoxLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.FranchiseBoxLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = franchiseBoxLimitTitle,
                    // The store setter clamps to 1..5 (the row's declared bounds).
                    items = (1..5).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.franchiseBoxLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFranchiseBoxLimit(it) } },
                )
            },
        )

        val franchiseMemberLimitTitle = rowTitle(AppearanceRows.FranchiseMemberLimit)
        SettingListItem(
            icon = rowIcon(AppearanceRows.FranchiseMemberLimit),
            title = franchiseMemberLimitTitle,
            subtitle = rowSubtitle(AppearanceRows.FranchiseMemberLimit),
            trailingText = preferences.franchiseMemberLimit.toString(),
            highlighted = highlightSettingId == AppearanceRows.FranchiseMemberLimit.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = franchiseMemberLimitTitle,
                    // The store setter clamps to 10..100 (the row's declared bounds).
                    items = (10..100).toList(),
                    label = { it.toString() },
                    isSelected = { it == preferences.franchiseMemberLimit },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailFranchiseMemberLimit(it) } },
                )
            },
        )

        val smartPlayPriorityTitle = rowTitle(AppearanceRows.SmartPlayPriority)
        SettingListItem(
            icon = rowIcon(AppearanceRows.SmartPlayPriority),
            title = smartPlayPriorityTitle,
            subtitle = rowSubtitle(AppearanceRows.SmartPlayPriority),
            trailingText = preferences.smartPlayPriority.displayName,
            highlighted = highlightSettingId == AppearanceRows.SmartPlayPriority.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = smartPlayPriorityTitle,
                    items = SmartPlayPriority.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.smartPlayPriority },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailSmartPlayPriority(it) } },
                )
            },
        )

        val streamFallbackLanguageTitle = rowTitle(AppearanceRows.StreamFallbackLanguage)
        SettingListItem(
            icon = rowIcon(AppearanceRows.StreamFallbackLanguage),
            title = streamFallbackLanguageTitle,
            subtitle = rowSubtitle(AppearanceRows.StreamFallbackLanguage),
            trailingText = preferences.streamFallbackLanguage.uppercase(),
            highlighted = highlightSettingId == AppearanceRows.StreamFallbackLanguage.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = streamFallbackLanguageTitle,
                    // The common 3-letter codes (the store setter lowercases
                    // and accepts only 3-letter tokens, else resets to "eng").
                    items = STREAM_FALLBACK_LANGUAGE_CODES,
                    label = { it.uppercase() },
                    isSelected = { it == preferences.streamFallbackLanguage },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailStreamFallbackLanguage(it) } },
                )
            },
        )

        val promotedRailSourceTitle = rowTitle(AppearanceRows.PromotedRailSource)
        SettingListItem(
            icon = rowIcon(AppearanceRows.PromotedRailSource),
            title = promotedRailSourceTitle,
            subtitle = rowSubtitle(AppearanceRows.PromotedRailSource),
            trailingText = preferences.promotedRailSource.displayName,
            highlighted = highlightSettingId == AppearanceRows.PromotedRailSource.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = promotedRailSourceTitle,
                    items = PromotedRailSource.entries,
                    label = { it.displayName },
                    isSelected = { it == preferences.promotedRailSource },
                    onSelect = { viewModel.edit { scope -> scope.library.setDetailPromotedRailSource(it) } },
                )
            },
        )

        // ── Detail Section Order: the declared header row + the reorderable
        // content-section rows (the nav-customization group's reorder
        // grammar: a shared reorderable holder mirrors the stored order,
        // reconciled against the known sections — unknown dropped, missing
        // appended canonically — and persists the joined CSV). The stored
        // vocabulary is the detail feature's section-kind names; the
        // consumption side reconciles, so a settings-side drift can never
        // strand a stale stored value. ──────────────────────────────────────
        val sectionOrderTitle = rowTitle(AppearanceRows.SectionOrder)
        val sectionOrderCustomLabel = stringResource(Res.string.settings_section_order_custom)
        val sectionOrderDefaultLabel = stringResource(Res.string.settings_section_order_default)
        val sectionOrderLabels = DETAIL_SECTION_ORDER_SECTIONS.associate { it.key to stringResource(it.labelRes) }
        SettingListItem(
            icon = rowIcon(AppearanceRows.SectionOrder),
            title = sectionOrderTitle,
            subtitle = rowSubtitle(AppearanceRows.SectionOrder),
            trailingText = if (preferences.sectionOrder.isEmpty()) sectionOrderDefaultLabel else sectionOrderCustomLabel,
            highlighted = highlightSettingId == AppearanceRows.SectionOrder.id,
            onClick = {
                activePicker.value = PickerState.List(
                    title = sectionOrderTitle,
                    items = listOf(sectionOrderDefaultLabel, sectionOrderCustomLabel),
                    label = { it },
                    isSelected = { it == (if (preferences.sectionOrder.isEmpty()) sectionOrderDefaultLabel else sectionOrderCustomLabel) },
                    onSelect = { selected ->
                        // Picking "Default" clears the stored permutation;
                        // "Custom" seeds the editor's order from the current
                        // known order (kept canonical until a row is dragged).
                        viewModel.edit { scope ->
                            scope.library.setDetailSectionOrder(
                                if (selected == sectionOrderDefaultLabel) {
                                    emptyList()
                                } else {
                                    preferences.sectionOrder.ifEmpty {
                                        DETAIL_SECTION_ORDER_SECTIONS.map { it.key }
                                    }
                                },
                            )
                        }
                    },
                )
            },
        )

        val sectionOrderEditor = rememberReorderableOrderedList(
            storedOrder = preferences.sectionOrder,
            knownOrder = DETAIL_SECTION_ORDER_SECTIONS.map { it.key },
            onPersist = { order -> viewModel.edit { scope -> scope.library.setDetailSectionOrder(order) } },
        )

        sectionOrderEditor.items.forEachIndexed { index, key ->
            val descriptor = DETAIL_SECTION_ORDER_SECTIONS.first { it.key == key }
            SettingReorderableItem(
                icon = Tabler.Outline.LayoutList,
                title = sectionOrderLabels.getValue(key),
                index = index,
                count = sectionOrderEditor.items.size,
                modifier = Modifier.onSizeChanged { sectionOrderEditor.recordHeight(key, it.height) },
                onDrag = { delta -> sectionOrderEditor.onDrag(key, delta) },
                onDragStart = { sectionOrderEditor.onDragStart(key) },
                onDragEnd = sectionOrderEditor::onDragEnd,
            )
        }

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

/** The advanced-gated `appearance.performance` group. */
@Composable
internal fun AppearancePerformanceGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.Bolt,
        title = stringResource(Res.string.settings_performance),
        summary = { appearancePerformanceSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearancePerformance.itemIdSet,
    ) {
        // Derived by rowTotalFor from the performance declaration (both rows
        // ride the advanced toggle — the structural gate around this group).
        val perfTotal = rowTotalFor(SettingsScreenGroups.appearancePerformance, RowAdmissionFlags(showAdvanced = true))
        SettingToggleItem(
            icon = Tabler.Outline.Gauge,
            title = rowTitle(AppearanceRows.PerformanceMode),
            subtitle = rowSubtitle(AppearanceRows.PerformanceMode),
            checked = preferences.performanceMode,
            highlighted = highlightSettingId == AppearanceRows.PerformanceMode.id,
            index = 0, count = perfTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setPerformanceMode(it) } },
        )
        SettingToggleItem(
            icon = Tabler.Outline.Activity,
            title = rowTitle(AppearanceRows.ReduceMotion),
            subtitle = rowSubtitle(AppearanceRows.ReduceMotion),
            checked = preferences.reduceMotionEnabled,
            highlighted = highlightSettingId == AppearanceRows.ReduceMotion.id,
            index = 1, count = perfTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setReduceMotionEnabled(it) } },
        )
    }
}

/** The advanced-gated `appearance.eyeCare` group. */
@Composable
internal fun AppearanceEyeCareGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    onShowStrengthSheet: () -> Unit,
) {
    SettingsGroup(
        icon = Tabler.Outline.Eye,
        title = stringResource(Res.string.settings_comfort_eye_care),
        summary = { appearanceEyeCareSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceEyeCare.itemIdSet,
    ) {
        // Derived by rowTotalFor from the eye-care declaration (both rows
        // ride the advanced toggle — the structural gate around this group).
        val eyeCareTotal = rowTotalFor(SettingsScreenGroups.appearanceEyeCare, RowAdmissionFlags(showAdvanced = true))
        SettingToggleItem(
            icon = Tabler.Outline.Moon,
            title = rowTitle(AppearanceRows.BlueLightFilter),
            subtitle = rowSubtitle(AppearanceRows.BlueLightFilter),
            checked = preferences.blueLightFilterEnabled,
            highlighted = highlightSettingId == AppearanceRows.BlueLightFilter.id,
            index = 0, count = eyeCareTotal,
            onCheckedChange = { viewModel.edit { scope -> scope.appearance.setBlueLightFilterEnabled(it) } },
        )
        SettingListItem(
            icon = Tabler.Outline.Adjustments,
            title = rowTitle(AppearanceRows.BlueLightStrength),
            subtitle = rowSubtitle(AppearanceRows.BlueLightStrength),
            trailingText = "${(preferences.blueLightFilterStrength * 100).toInt()}%",
            highlighted = highlightSettingId == AppearanceRows.BlueLightStrength.id,
            index = 1, count = eyeCareTotal,
            onClick = onShowStrengthSheet,
        )
    }
}

/** The advanced-gated `appearance.newsletter` group (enable + delivery day + the reorderable per-section rows). */
@Composable
internal fun AppearanceNewsletterGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
) {
    SettingsGroup(
        icon = Tabler.Outline.Mail,
        title = stringResource(Res.string.settings_newsletter_config),
        summary = { appearanceNewsletterSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearanceNewsletter.itemIdSet,
    ) {
        val newsletterSections = rememberReorderableOrderedList(
            storedOrder = preferences.newsletterSectionOrder,
            onPersist = { order -> viewModel.edit { it.notification.setNewsletterSectionOrder(order) } },
        )

        // The two static declared rows (enable + delivery day) plus
        // the runtime-reorderable section rows: the declared
        // newsletter_sections row renders AS those rows, so its admitted
        // slot is replaced by newsletterSections.items.size.
        SettingsItemList(
            total = rowTotalFor(SettingsScreenGroups.appearanceNewsletter, RowAdmissionFlags(showAdvanced = true)) -
                1 + newsletterSections.items.size,
        ) {

        SettingToggleItem(
            icon = Tabler.Outline.Mail,
            title = rowTitle(AppearanceRows.NewsletterEnabled),
            subtitle = rowSubtitle(AppearanceRows.NewsletterEnabled),
            checked = preferences.newsletterEnabled,
                        highlighted = highlightSettingId == AppearanceRows.NewsletterEnabled.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.notification.setNewsletterEnabled(it) } }
        )

        // The persisted newsletterDayOfWeek values use java.util.Calendar day
        // numbers (SUNDAY=1 … SATURDAY=7); the literals preserve that wire
        // format.
        val daysOfWeek = listOf(
            CALENDAR_MONDAY to stringResource(Res.string.settings_day_monday),
            CALENDAR_TUESDAY to stringResource(Res.string.settings_day_tuesday),
            CALENDAR_WEDNESDAY to stringResource(Res.string.settings_day_wednesday),
            CALENDAR_THURSDAY to stringResource(Res.string.settings_day_thursday),
            CALENDAR_FRIDAY to stringResource(Res.string.settings_day_friday),
            CALENDAR_SATURDAY to stringResource(Res.string.settings_day_saturday),
            CALENDAR_SUNDAY to stringResource(Res.string.settings_day_sunday),
        )
        val dayLabel = daysOfWeek.find { it.first == preferences.newsletterDayOfWeek }?.second ?: stringResource(Res.string.settings_day_saturday)

        SettingListItem(
            icon = Tabler.Outline.Calendar,
            title = rowTitle(AppearanceRows.NewsletterDeliveryDay),
            subtitle = rowSubtitle(AppearanceRows.NewsletterDeliveryDay),
            trailingText = dayLabel,
            highlighted = highlightSettingId == AppearanceRows.NewsletterDeliveryDay.id,
            onClick = {
                val currentIdx = daysOfWeek.indexOfFirst { it.first == preferences.newsletterDayOfWeek }
                val nextIdx = (currentIdx + 1) % daysOfWeek.size
                viewModel.edit { it.notification.setNewsletterDayOfWeek(daysOfWeek[nextIdx].first) }
            }
        )

        if (preferences.newsletterEnabled) {
            newsletterSections.items.forEachIndexed { index, sectionType ->
                val enabled = sectionType in preferences.enabledNewsletterSections

                SettingReorderableToggleItem(
                    icon = newsletterSectionIcon(sectionType),
                    title = stringResource(sectionType.labelRes),
                    subtitle = stringResource(sectionType.descriptionRes),
                    checked = enabled,
                    index = index + 2,
                    count = newsletterSections.items.size + 2,
                    modifier = Modifier.onSizeChanged { newsletterSections.recordHeight(sectionType, it.height) },
                    onCheckedChange = { checked ->
                        val current = preferences.enabledNewsletterSections.toMutableSet()
                        if (checked) current.add(sectionType) else current.remove(sectionType)
                        viewModel.edit { it.notification.setEnabledNewsletterSections(current) }
                    },
                    onDrag = { delta -> newsletterSections.onDrag(sectionType, delta) },
                    onDragStart = { newsletterSections.onDragStart(sectionType) },
                    onDragEnd = newsletterSections::onDragEnd,
                )
            }
        }
        }
    }
}
