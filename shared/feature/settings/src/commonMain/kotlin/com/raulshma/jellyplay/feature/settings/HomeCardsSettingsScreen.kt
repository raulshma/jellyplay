package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Photo
import com.raulshma.jellyplay.core.model.HomeCardScale
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_large
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_card_scale_small
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_entry_cards
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_trickplay_preview_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_all
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_latest
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_recent
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_new_badge_sections_upcoming
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_days

/**
 * The Cards & badges leaf's ONE ordered slot declaration (ADR-0009 amendment;
 * ADR-0017's home split): the cards group — the former dozen rows plus the
 * card-chrome trio moved off the former display group. No advanced tier.
 * The contract test pins the slot/group parity against this same declaration.
 */
internal val HomeCardsSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.homeCards.asScreenGroup(),
)

/**
 * The Cards & badges LEAF (ADR-0017's home split): the card-display surface
 * behind the hub's Cards & badges entrance — the badge/ratings/spoiler
 * toggles, the TV-gated trickplay pair (the peek rides its toggle's
 * admission block) and the card-chrome trio (scale, corner radius, row
 * animations). No advanced tier.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCardsSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_entry_cards),
        onBack = onBack,
        focusTag = "home_cards_init",
        highlightSettingId = highlightSettingId,
        groups = HomeCardsSettingsScreenSlots.withContent(
            bodies = listOf(
                { activePicker ->
                    HomeCardsGroupBody(
                        preferences = preferences,
                        isTv = isTv,
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
 * The `home.cards` group body — the former hub screen's cards body with the
 * card-chrome trio appended (the declared catalog order: the dozen toggles,
 * then scale / corner radius / rows animations).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeCardsGroupBody(
    preferences: HomeScreenPreferences,
    isTv: Boolean,
    highlightSettingId: String?,
    viewModel: HomeSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
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
        // The leaf's ONLY group — it stays open on first entry (the display
        // leaf's main-group precedent); the per-row highlight checks below
        // still cover the search-result reveal.
        initiallyExpanded = true,
    ) {
        // Derived by rowTotalFor from the cards declaration (every
        // row always renders except the TV-gated trickplay pair).
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

            // The card-chrome trio — the former display group's scattered
            // card rows, rejoined with their concern (the declared catalog
            // order trails the toggles).
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
            SettingToggleItem(
                icon = rowIcon(HomeRows.HomeRowsAnimations),
                title = rowTitle(HomeRows.HomeRowsAnimations),
                subtitle = rowSubtitle(HomeRows.HomeRowsAnimations),
                checked = preferences.homeRowsAnimations,
                highlighted = highlightSettingId == HomeRows.HomeRowsAnimations.id,
                onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeRowsAnimations(it) } },
            )
        }
    }
}
