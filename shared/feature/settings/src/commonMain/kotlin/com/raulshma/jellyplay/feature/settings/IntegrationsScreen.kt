package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_arr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_arr_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_seerr_integration

/**
 * Top-level integrations hub. Lists every third-party service JellyPlay talks
 * to (Seerr, Radarr/Sonarr) as drill-in cards, plus — since ADR-0017 — the
 * desktop-integration groups moved off the settings root: the Discord Rich
 * Presence toggle and the playback-event shell hooks, capability-gated
 * exactly as they were on the root (hidden = structurally absent).
 *
 * The screen's ONE ordered group declaration (the launcher trio + the two
 * desktop groups) drives the deep-link highlight scroll — a search hit on a
 * `discord_*` / `hooks_*` row routes here with the id baked in and lands on
 * the owning card. The desktop groups' pickers (the shell-hook command
 * editor) flow through the scaffold's single [SettingsPickerDialog] host.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IntegrationsScreen(
    onBack: () -> Unit,
    onSeerrSettings: () -> Unit,
    onArrSettings: () -> Unit,
    onSubtitleProviderSettings: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val preferences = viewModel.preferences

    val integrationGroups = buildList {
        add(
            SettingsScreenGroups.integrations.asScreenGroup { _ ->
                // The launcher trio: Seerr, Radarr/Sonarr, the subtitle
                // provider drill-in (the hand-built rows the hub opened with;
                // the service-intro copy rides this slot above the card).
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Connect JellyPlay to your media services to enable requests, " +
                            "discover, and download-queue tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                    SettingsGroup(
                        icon = Tabler.Outline.PlugConnected,
                        title = stringResource(Res.string.settings_integrations_title),
                        initiallyExpanded = true,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        val count = 3
                        SettingListItem(
                            icon = Tabler.Outline.Puzzle,
                            title = rowTitle(IntegrationsRows.SeerrSettings),
                            subtitle = rowSubtitle(IntegrationsRows.SeerrSettings),
                            index = 0,
                            count = count,
                            highlighted = highlightSettingId == IntegrationsRows.SeerrSettings.id,
                            onClick = onSeerrSettings,
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Download,
                            title = stringResource(Res.string.settings_integrations_arr),
                            subtitle = stringResource(Res.string.settings_integrations_arr_subtitle),
                            index = 1,
                            count = count,
                            highlighted = highlightSettingId == ExperimentalSettingsIds.ARR_SETTINGS,
                            onClick = onArrSettings,
                        )
                        SettingListItem(
                            icon = Tabler.Outline.Subtitles,
                            title = rowTitle(IntegrationsRows.SubtitleProviderSettings),
                            subtitle = rowSubtitle(IntegrationsRows.SubtitleProviderSettings),
                            index = 2,
                            count = count,
                            highlighted = highlightSettingId == IntegrationsRows.SubtitleProviderSettings.id,
                            onClick = onSubtitleProviderSettings,
                        )
                    }
                }
            },
        )
        // The desktop-integration groups, moved from the settings root
        // (ADR-0017) — capability-gated like the root blocks they replace.
        if (settingsCapabilities.supportsDiscordPresence) {
            add(
                SettingsScreenGroups.systemDiscordPresence.asScreenGroup { _ ->
                    SettingsDiscordPresenceSection(
                        preferences = preferences,
                        viewModel = viewModel,
                        highlightSettingId = highlightSettingId,
                    )
                },
            )
        }
        if (settingsCapabilities.supportsShellHooks) {
            add(
                SettingsScreenGroups.systemHooks.asScreenGroup { activePicker ->
                    SettingsShellHooksSection(
                        preferences = preferences,
                        viewModel = viewModel,
                        highlightSettingId = highlightSettingId,
                        activeDialog = activePicker,
                    )
                },
            )
        }
    }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_integrations_title),
        onBack = onBack,
        focusTag = "integrations_init",
        highlightSettingId = highlightSettingId,
        groups = integrationGroups,
        pickerHost = true,
    )
}
