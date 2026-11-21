package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.AppearanceScreenPreferences
import com.raulshma.jellyplay.core.model.NavigationCustomizationPreferences
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Palette
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_defaults_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_message
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_appearance_title
import com.raulshma.jellyplay.core.ui.navigation.Route

/**
 * The appearance hub's ONE ordered slot declaration (ADR-0009 amendment): the
 * entrance group, then the advanced-gated Newsletter group — the glossary's
 * DECLARED hub exception (the newsletter rows render on the hub below the
 * entrances instead of on a leaf). The screen attaches its hand-written
 * bodies via [withContent]; the contract test pins the slot/group parity
 * against this same declaration.
 */
internal val AppearanceHubSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.appearanceHub.asScreenGroup(),
    SettingsScreenGroups.appearanceNewsletter.asScreenGroup(advancedOnly = true),
)

/**
 * The appearance hub's entrance group body (ADR-0017's appearance split): the
 * four composite entrance rows in [AppearanceRows]'s declared order, each
 * deep-linking the leaf screen it opens (the playback hub's drill-in-row
 * shape — the route lives on the row). Each subtitle is the leaf's live
 * current-value summary — the per-domain `*SummaryParts` folds over
 * [AppearanceScreenPreferences] (ADR-0017 decision 3, pinned by
 * SettingsSummariesTest), resolved part-by-part at composition. The group
 * stays open — the entrances are the screen's primary body — and a
 * deep-linked entrance id highlights its row (the catalog routes entrance ids
 * to their leaf, so this only fires for a hand-built link).
 */
@Composable
private fun AppearanceHubSection(
    preferences: AppearanceScreenPreferences,
    navPreferences: NavigationCustomizationPreferences,
    highlightSettingId: String?,
    navActions: SettingsNavActions,
) {
    SettingsGroup(
        icon = Tabler.Outline.Palette,
        title = stringResource(Res.string.settings_appearance_sections),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = true,
    ) {
        SettingsItemList(total = SettingsScreenGroups.appearanceHub.items.size) {
            // (row, summary parts) in the hub's declared order — the row's
            // declared route IS the destination (never reconstructed here).
            // The order IS [AppearanceHubRows]'s declaration; each row's live
            // summary is selected by row id, so a row added to the
            // declaration surfaces here (or the selection fails loudly)
            // instead of dropping off a hand-maintained parallel list.
            val summaries = mapOf(
                AppearanceRows.ThemeEntrance.id to appearanceThemeSummaryParts(preferences),
                AppearanceRows.LibraryViewsEntrance.id to appearanceLibraryViewsSummaryParts(preferences),
                AppearanceRows.DetailDisplayEntrance.id to appearanceDetailSummaryParts(preferences),
                AppearanceRows.NavigationEntrance.id to appearanceNavigationSummaryParts(navPreferences),
            )
            val entrances = AppearanceHubRows.map { row -> row to summaries.getValue(row.id) }
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
 * The appearance settings HUB (ADR-0017's domain hub split): one group of the
 * four composite entrance rows ([AppearanceHubSection]) — Theme & display /
 * Library views / Detail screen / Navigation & haptics — plus the
 * advanced-gated Newsletter group rendered below them (the glossary's
 * declared hub exception). The domain's settings rows live on the leaves
 * ([Route.ThemeSettings] et al. — their spec route kinds re-pointed, so
 * search/deep-link/highlight resolution lands on the owning leaf); this
 * screen keeps the domain root's faces: the settings root's Appearance
 * entrance, the search pane's quick category, and the domain-wide reset
 * action (the reset spans the categories the domain owns, exactly as on the
 * former combined screen — it crosses every leaf, so it stays HERE). The
 * hub's advanced tier is exactly the newsletter trio; a stale deep-link into
 * a moved row id resolves to -1 here (no highlight) and lands on the owning
 * leaf through the catalog instead.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** The entrances navigate through the shared nav facade. */
    navActions: SettingsNavActions,
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navPreferences by viewModel.navigationCustomizationPreferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()

    val hubGroups = AppearanceHubSlots.withContent(
        bodies = listOf(
            { _ ->
                AppearanceHubSection(
                    preferences = preferences,
                    navPreferences = navPreferences,
                    highlightSettingId = highlightSettingId,
                    navActions = navActions,
                )
            },
            { _ ->
                AppearanceNewsletterGroup(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_title),
        onBack = onBack,
        focusTag = "appearance_init",
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
            dialogTitle = stringResource(Res.string.settings_reset_appearance_title),
            dialogMessage = stringResource(Res.string.settings_reset_appearance_message),
            onReset = { viewModel.resetCategory(PreferenceResetCategory.APPEARANCE) },
        ),
    )
}
