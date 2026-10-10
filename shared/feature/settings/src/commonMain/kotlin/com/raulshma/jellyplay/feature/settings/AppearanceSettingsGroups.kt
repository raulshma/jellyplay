package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NewsletterSectionType
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
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_library_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_sections_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance_mode
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_standard_experience
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_all_hidden
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_hide_thumbnails
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_share_button
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_summary_skip_specials

@Composable
private fun appearanceLibrarySummary(preferences: AppearanceScreenPreferences): String {
    // The group's remaining rows (the home-discovery card-display quartet
    // moved to HomeSettingsScreen — PS-4, so its terms left the summary).
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

        SettingToggleItem(
            icon = Tabler.Outline.List,
            title = rowTitle(AppearanceRows.CompactEpisodeList),
            subtitle = rowSubtitle(AppearanceRows.CompactEpisodeList),
            checked = preferences.compactEpisodeList,
            highlighted = highlightSettingId == AppearanceRows.CompactEpisodeList.id,
            onCheckedChange = { viewModel.edit { scope -> scope.library.setCompactEpisodeList(it) } },
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

        // The persisted newsletterDayOfWeek values are the legacy
        // java.util.Calendar day numbers (SUNDAY=1 … SATURDAY=7) —
        // the literals preserve that wire format now that the JVM
        // type is gone.
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
