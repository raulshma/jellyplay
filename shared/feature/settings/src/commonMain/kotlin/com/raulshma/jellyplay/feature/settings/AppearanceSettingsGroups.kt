package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.NewsletterSectionType
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.reorder.rememberReorderableOrderedList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_blue_light_filter_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_comfort_eye_care
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_friday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_monday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_saturday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_sunday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_thursday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_tuesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_day_wednesday
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_disabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_config
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_newsletter_sections_enabled
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_customize
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_customize_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reduced_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_standard_experience

/**
 * The appearance domain's shared group bodies (ADR-0017's split): the
 * Performance (Reduce Motion + the master-level quick picker / hub deep-link)
 * and Eye care groups — hosted on the Theme & display leaf — and the
 * Newsletter group, hosted on the HUB (the glossary's declared hub
 * exception). The theme group body lives beside its leaf
 * ([ThemeSettingsScreen]); the library-views and detail-screen bodies beside
 * theirs ([LibraryDisplaySettingsScreen]/[DetailDisplaySettingsScreen]).
 */

@Composable
private fun appearancePerformanceSummary(preferences: AppearanceScreenPreferences): String {
    // The master level's face (Standard falls through to the level-less
    // "standard experience" default); reduced motion stays its own term.
    val level = preferences.performanceLevel
        .takeIf { it != PerformanceLevel.STANDARD }
        ?.localizedDisplayName()
    val reducedMotion = if (preferences.reduceMotionEnabled) stringResource(Res.string.settings_reduced_motion) else null
    return listOfNotNull(level, reducedMotion)
        .joinToString(", ")
        .ifEmpty { stringResource(Res.string.settings_standard_experience) }
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

/**
 * The advanced-gated `appearance.performance` group: the master level quick
 * picker + the hub Customize deep-link (both screen-local — the catalog row
 * for the master dial is the Performance hub's) beside the declared Reduce
 * Motion toggle. Hosted on the Theme & display leaf (ADR-0017 — the motion
 * concern's home, kept advanced).
 */
@Composable
internal fun AppearancePerformanceGroup(
    preferences: AppearanceScreenPreferences,
    highlightSettingId: String?,
    viewModel: AppearanceSettingsViewModel,
    onOpenPerformanceHub: () -> Unit,
    activePicker: MutableState<PickerState<*>?>,
) {
    val performanceLevel by viewModel.performanceLevel.collectAsStateWithLifecycle()
    SettingsGroup(
        icon = Tabler.Outline.Bolt,
        title = stringResource(Res.string.settings_performance),
        summary = { appearancePerformanceSummary(preferences) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.appearancePerformance.itemIdSet,
    ) {
        // The declared Reduce Motion row plus the TWO screen-local rows (the
        // level quick picker and the Customize deep-link) — the explicit +2.
        val perfTotal =
            rowTotalFor(SettingsScreenGroups.appearancePerformance, RowAdmissionFlags(showAdvanced = true)) + 2
        // The master level quick picker — the repurposed old performance-mode
        // toggle's face. The full dial (per-level descriptions + the granular
        // overrides) lives in the Performance hub; this picker writes the
        // SAME setPerformanceLevel command, so the two faces can never
        // disagree. The search id "performance_mode" stays stable (the hub's
        // level group owns the catalog row).
        val levelTitle = rowTitle(PerformanceRows.PerformanceLevel)
        // The picker's faces resolve outside its plain (non-composable) lambda.
        val levelLabels = PerformanceLevel.entries.associateWith { performanceLevelLabel(it) }
        SettingListItem(
            icon = rowIcon(PerformanceRows.PerformanceLevel),
            title = levelTitle,
            subtitle = performanceLevelLabel(performanceLevel),
            trailingText = performanceLevelLabel(performanceLevel),
            highlighted = highlightSettingId == PerformanceRows.PerformanceLevel.id,
            index = 0, count = perfTotal,
            onClick = {
                activePicker.value = PickerState.List(
                    title = levelTitle,
                    items = PerformanceLevel.entries,
                    label = { levelLabels.getValue(it) },
                    isSelected = { it == performanceLevel },
                    onSelect = { viewModel.edit { scope -> scope.appearance.setPerformanceLevel(it) } },
                )
            },
        )
        // The Customize deep-link: every granular performance row lives in
        // the hub — this screen surfaces only the quick dial.
        SettingListItem(
            icon = Tabler.Outline.Settings,
            title = stringResource(Res.string.settings_perf_customize),
            subtitle = stringResource(Res.string.settings_perf_customize_subtitle),
            trailingText = "",
            highlighted = false,
            index = 1, count = perfTotal,
            onClick = onOpenPerformanceHub,
        )
        SettingToggleItem(
            icon = rowIcon(AppearanceRows.ReduceMotion),
            title = rowTitle(AppearanceRows.ReduceMotion),
            subtitle = rowSubtitle(AppearanceRows.ReduceMotion),
            checked = preferences.reduceMotionEnabled,
            highlighted = highlightSettingId == AppearanceRows.ReduceMotion.id,
            index = 2, count = perfTotal,
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
