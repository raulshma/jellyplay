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
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Slideshow
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.HomeRefreshCadence
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_home_tuning
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_default
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_eager
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_manual
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_refresh_cadence_relaxed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_home_title

/**
 * The home hub's ONE ordered slot declaration (ADR-0009 amendment): the
 * entrance group, then the advanced-gated Fine-tuning group — the glossary's
 * declared hub exception (the appearance hub's newsletter precedent). The
 * screen attaches its hand-written bodies via [withContent]; the contract
 * test pins the slot/group parity against this same declaration. The tuning
 * rows carry their own declared Advanced gates (the phase-5 spec-flag flip
 * closed the derivation anomaly), so the default declared-admission hook
 * derives the honest 8-row delta — no content-count hook needed.
 */
internal val HomeSettingsScreenSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.homeHub.asScreenGroup(),
    SettingsScreenGroups.homeTuning.asScreenGroup(advancedOnly = true),
)

/**
 * The home hub's entrance group body (ADR-0017's home split): the three
 * composite entrance rows in [HomeRows]'s declared order, each deep-linking
 * the leaf screen it opens (the appearance hub's drill-in-row shape — the
 * route lives on the row). Each subtitle is the leaf's live current-value
 * summary — the per-domain `*SummaryParts` folds over [HomeScreenPreferences]
 * (ADR-0017 decision 3, pinned by SettingsSummariesTest), resolved
 * part-by-part at composition. The group stays open — the entrances are the
 * screen's primary body — and a deep-linked entrance id highlights its row
 * (the catalog routes entrance ids to their leaf, so this only fires for a
 * hand-built link).
 */
@Composable
private fun HomeHubSection(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    navActions: SettingsNavActions,
) {
    SettingsGroup(
        icon = Tabler.Outline.Home,
        title = stringResource(Res.string.settings_home_title),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = true,
    ) {
        SettingsItemList(total = SettingsScreenGroups.homeHub.items.size) {
            // (row, summary parts) in the hub's declared order — the row's
            // declared route IS the destination (never reconstructed here).
            // The order IS [HomeHubRows]'s declaration; each row's live
            // summary is selected by row id, so a row added to the
            // declaration surfaces here (or the selection fails loudly)
            // instead of dropping off a hand-maintained parallel list.
            val summaries = mapOf(
                HomeRows.DisplayEntrance.id to homeDisplaySummaryParts(preferences),
                HomeRows.RowsLayoutEntrance.id to homeRowsLayoutSummaryParts(preferences),
                HomeRows.CardsEntrance.id to homeCardsSummaryParts(preferences),
            )
            val entrances = HomeHubRows.map { row -> row to summaries.getValue(row.id) }
            entrances.forEach { (row, summary) ->
                SettingListItem(
                    icon = rowIcon(row),
                    title = rowTitle(row),
                    subtitle = joinSummaryTokens(summary.map { it.resolveAtComposition() }),
                    highlighted = highlightSettingId == row.id,
                    onClick = { navActions.onNavigate(requireNotNull(row.route) { "hub entrance ${row.id} declares no route" }) },
                )
            }
        }
    }
}

/**
 * The home hub's advanced-gated Fine-tuning group (ADR-0017's home split):
 * the tuning/fine-tune subset of the former combined display group, moved
 * verbatim off it. The shell only composes the slot behind the advanced
 * toggle, so the total reads the advanced leg directly (every row also
 * carries its declared Advanced gate — the spec-side flag and the whole-group
 * gate agree).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeTuningGroupBody(
    preferences: HomeScreenPreferences,
    highlightSettingId: String?,
    viewModel: HomeSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = Tabler.Outline.Slideshow,
        title = stringResource(Res.string.settings_home_tuning),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.homeTuning.itemIdSet,
    ) {
        // The whole declared tuning set — the slot only composes behind the
        // advanced toggle (the shell's whole-group gate), so the advanced leg
        // IS the composed state.
        SettingsItemList(total = homeTuningScreenRowTotal()) {
            // The hero pacing/behavior trio, then the presence cadence/limit
            // pair, then the feed refresh cadence and the two layout-density
            // knobs — [HomeTuningRows]'s declared order.
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
            SettingToggleItem(
                icon = rowIcon(HomeRows.HeroScrollDefer),
                title = rowTitle(HomeRows.HeroScrollDefer),
                subtitle = rowSubtitle(HomeRows.HeroScrollDefer),
                checked = preferences.heroScrollDefer,
                highlighted = highlightSettingId == HomeRows.HeroScrollDefer.id,
                onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHeroScrollDefer(it) } },
            )
            SettingToggleItem(
                icon = rowIcon(HomeRows.HomeHeroRandomStart),
                title = rowTitle(HomeRows.HomeHeroRandomStart),
                subtitle = rowSubtitle(HomeRows.HomeHeroRandomStart),
                checked = preferences.homeHeroRandomStart,
                highlighted = highlightSettingId == HomeRows.HomeHeroRandomStart.id,
                onCheckedChange = { viewModel.edit { scope -> scope.homeDiscovery.setHomeHeroRandomStart(it) } },
            )
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
    }
}

/**
 * The home settings HUB (ADR-0017's domain hub split): one group of the three
 * composite entrance rows ([HomeHubSection]) — Home display / Rows & layout /
 * Cards & badges — plus the advanced-gated Fine-tuning group rendered below
 * them (the glossary's declared hub exception). The domain's settings rows
 * live on the leaves ([Route.HomeDisplaySettings] et al. — their spec route
 * kinds re-pointed, so search/deep-link/highlight resolution lands on the
 * owning leaf); this screen keeps the domain root's faces: the settings
 * root's Home entrance, the search pane's quick category, and the
 * domain-wide reset action (the reset spans the categories the domain owns —
 * it crosses every leaf, so it stays HERE). The hub's advanced tier is
 * exactly the Fine-tuning group; a stale deep-link into a moved row id
 * resolves to -1 here (no highlight) and lands on the owning leaf through
 * the catalog instead.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions,
    highlightSettingId: String? = null,
    viewModel: HomeSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()

    val hubGroups = HomeSettingsScreenSlots.withContent(
        bodies = listOf(
            { _ ->
                HomeHubSection(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    navActions = navActions,
                )
            },
            { activePicker ->
                HomeTuningGroupBody(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    activePicker = activePicker,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_home_title),
        onBack = onBack,
        focusTag = "home_settings_init",
        highlightSettingId = highlightSettingId,
        groups = hubGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            rowFlags = RowAdmissionFlags(showAdvanced = false, isTv = LocalTvMode.current),
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_defaults_cd),
            dialogTitle = stringResource(Res.string.settings_reset_home_title),
            dialogMessage = stringResource(Res.string.settings_reset_home_message),
            onReset = { viewModel.resetCategory(PreferenceResetCategory.HOME_DISCOVERY) },
        ),
        pickerHost = true,
    )
}
