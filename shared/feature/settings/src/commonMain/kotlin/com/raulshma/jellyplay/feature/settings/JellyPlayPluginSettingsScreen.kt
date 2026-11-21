package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_plugin_entry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The JellyPlay companion-plugin's settings screen (ADR-0017): the root
 * screen's former inline plugin section as ONE screen behind the root's one
 * JellyPlay-plugin entrance. Hosts everything the inline section held — the
 * four plugin-surface entries (sync / messages / my ratings / your watching)
 * and the per-feature switches — unchanged (IA-only); the sync toggle and
 * sync-now action stay on the sync screen they always lived on
 * ([Route.JellyPlaySync]).
 *
 * The whole screen stays gated on the plugin probe exactly as the inline
 * section was: UNAVAILABLE composes nothing (graceful-absent), a refused
 * contract renders the mismatch banner, UNKNOWN probes once. The screen is
 * reachable in practice only while the entrance row composes (probe
 * AVAILABLE); a stale deep-link into a now-unavailable plugin lands on the
 * graceful-absent body.
 */
@Composable
fun JellyPlayPluginSettingsScreen(
    onBack: () -> Unit,
    navActions: SettingsNavActions,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val onNavigate = navActions.onNavigate

    val pluginGroups = listOf(
        SettingsScreenGroups.jellyplayFeatures.asScreenGroup { _ ->
            SettingsJellyPlayPluginSection(
                viewModel = viewModel,
                highlightSettingId = highlightSettingId,
                onOpenSync = { onNavigate(Route.JellyPlaySync) },
                onOpenMessages = { onNavigate(Route.JellyPlayMessages) },
                onOpenUserRatings = { onNavigate(Route.JellyPlayUserRatings) },
                onOpenYourWatching = { onNavigate(Route.JellyPlayYourWatching) },
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_jellyplay_plugin_entry),
        onBack = onBack,
        focusTag = "jellyplay_plugin_init",
        highlightSettingId = highlightSettingId,
        groups = pluginGroups,
    )
}
