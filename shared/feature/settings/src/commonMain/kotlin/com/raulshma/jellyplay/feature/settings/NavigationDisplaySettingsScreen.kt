package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_appearance_entry_navigation
import org.jetbrains.compose.resources.stringResource

/**
 * The Navigation & haptics LEAF (ADR-0017's appearance split): the floating
 * navigation-bar customization group behind the hub's Navigation & haptics
 * entrance — the bar's item customization/reorder + hide-on-scroll toggle and
 * the global interface/interaction rows (start tab, 24h clock, press
 * animations, pull-to-refresh, backdrop dim, haptics intensity, image
 * quality, list density, press effect, flat cards, overscroll) plus the
 * user-shortcuts editor drill-in. The group body ([NavigationCustomizationGroup])
 * is unchanged from the former combined appearance screen.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavigationDisplaySettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** The group's "Customize shortcuts" row: opens the shortcuts editor. */
    onCustomizeShortcuts: () -> Unit = {},
    /** The group's master-haptics link row: opens the Library views leaf on the switch. */
    onOpenHapticsSwitch: () -> Unit = {},
    viewModel: AppearanceSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navPrefs by viewModel.navigationCustomizationPreferences.collectAsStateWithLifecycle()

    val navigationGroups = listOf(
        SettingsScreenGroups.appearanceNavigation.asScreenGroup { activePicker ->
            // Floating navigation bar customization (enable/disable items,
            // reorder, hide-on-scroll) + the global interface/interaction rows.
            NavigationCustomizationGroup(
                preferences = navPrefs,
                viewModel = viewModel,
                onCustomizeShortcuts = onCustomizeShortcuts,
                onOpenHapticsSwitch = onOpenHapticsSwitch,
                screen = preferences,
                activePicker = activePicker,
                highlightSettingId = highlightSettingId,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_appearance_entry_navigation),
        onBack = onBack,
        focusTag = "appearance_navigation_init",
        highlightSettingId = highlightSettingId,
        groups = navigationGroups,
        pickerHost = true,
    )
}
