package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Help
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.License
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Server
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_about
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_acknowledgements
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_app_info
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_built_with_jellyfin
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_changelog
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_for_updates
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_updates_automatically
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_check_updates_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_12_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_24_hours
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_3_days
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_1_week
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_update_dismiss_never
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_collecting_logs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_diagnostics
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_updates_automatically
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_download_updates_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_github
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_help_faq
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_license
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_links
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_logs_subject
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_min_android
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_open_source_licenses
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_send_app_logs
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_send_logs_chooser
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_address_label_info
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_info
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_name_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_version_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_target_android
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_unknown
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_version_label
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_version_value
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_version_with_build

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicensesClick: () -> Unit,
    onCheckForUpdates: () -> Unit,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val platformIntents = rememberPlatformIntents()

    val backgroundColorState = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState()

    val logsSubject = stringResource(Res.string.settings_logs_subject, viewModel.appVersion)
    val sendLogsChooserTitle = stringResource(Res.string.settings_send_logs_chooser)

    val focusRequester = remember { FocusRequester() }
    var activePicker by remember { mutableStateOf<PickerState<*>?>(null) }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "about_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.settings_about),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = adaptiveInfo.contentPadding(isTv),
                    end = adaptiveInfo.contentPadding(isTv),
                    bottom = adaptiveInfo.bottomPadding(isTv),
                )
                .tvFocusRestorer()
                .focusRequester(focusRequester),
        ) {
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.settings_jellyplay),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.settings_version_value, viewModel.appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))

            SettingsGroup(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(Res.string.settings_app_info),
                initiallyExpanded = true,
            ) {
                SettingInfoItem(
                    icon = Tabler.Outline.Activity,
                    title = stringResource(Res.string.settings_version_label),
                    subtitle = stringResource(Res.string.settings_version_with_build, viewModel.appVersion, viewModel.buildType),
                )
                SettingInfoItem(
                    icon = Tabler.Outline.DeviceMobile,
                    title = stringResource(Res.string.settings_min_android),
                    subtitle = viewModel.minSdkInfo,
                )
                SettingInfoItem(
                    icon = Tabler.Outline.DeviceMobile,
                    title = stringResource(Res.string.settings_target_android),
                    subtitle = viewModel.targetSdkInfo,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (viewModel.serverVersion != null) {
                SettingsGroup(
                    icon = Tabler.Outline.Server,
                    title = stringResource(Res.string.settings_server_info),
                    initiallyExpanded = true,
                ) {
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(Res.string.settings_server_name_label),
                        subtitle = viewModel.serverName ?: stringResource(Res.string.settings_unknown),
                        copyableValue = viewModel.serverName,
                    )
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(Res.string.settings_server_version_label),
                        subtitle = viewModel.serverVersion ?: stringResource(Res.string.settings_unknown),
                        copyableValue = viewModel.serverVersion,
                    )
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(Res.string.settings_server_address_label_info),
                        subtitle = viewModel.serverAddress ?: stringResource(Res.string.settings_unknown),
                        copyableValue = viewModel.serverAddress,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            SettingsGroup(
                icon = Tabler.Outline.ExternalLink,
                title = stringResource(Res.string.settings_links),
                initiallyExpanded = true,
            ) {
                SettingListItem(
                    icon = Tabler.Outline.InfoCircle,
                    title = stringResource(Res.string.settings_changelog),
                    subtitle = "",
                    onClick = {
                        platformIntents.openUrl("https://github.com/raulshma/jellyplay/releases")
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.Help,
                    title = stringResource(Res.string.settings_help_faq),
                    subtitle = "",
                    onClick = {
                        platformIntents.openUrl("https://github.com/raulshma/jellyplay/wiki")
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.InfoCircle,
                    title = stringResource(Res.string.settings_github),
                    subtitle = "",
                    onClick = {
                        platformIntents.openUrl("https://github.com/raulshma/jellyplay")
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.License,
                    title = stringResource(Res.string.settings_license),
                    subtitle = "",
                    onClick = {
                        platformIntents.openUrl("https://www.gnu.org/licenses/gpl-3.0.en.html")
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.ExternalLink,
                    title = stringResource(Res.string.settings_open_source_licenses),
                    subtitle = "",
                    onClick = onLicensesClick,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(
                icon = Tabler.Outline.Activity,
                title = stringResource(Res.string.settings_diagnostics),
                initiallyExpanded = true,
            ) {
                // Desktop's LogCollector seam returns null — no log bundle to
                // share, so the row stays off the surface there.
                if (settingsCapabilities.supportsLogSharing) {
                    SettingListItem(
                        icon = if (viewModel.isCollectingLogs) Tabler.Outline.Refresh else Tabler.Outline.Download,
                        title = if (viewModel.isCollectingLogs) stringResource(Res.string.settings_collecting_logs) else stringResource(Res.string.settings_send_app_logs),
                        subtitle = "",
                        onClick = {
                            if (!viewModel.isCollectingLogs) {
                                viewModel.sendAppLogs { uri ->
                                    if (uri != null) {
                                        platformIntents.shareLogFile(logsSubject, sendLogsChooserTitle, uri)
                                    }
                                }
                            }
                        },
                    )
                }
                SettingToggleItem(
                    icon = Tabler.Outline.Refresh,
                    title = stringResource(Res.string.settings_check_updates_automatically),
                    subtitle = stringResource(Res.string.settings_check_updates_subtitle),
                    checked = viewModel.selfUpdateCheckEnabled,
                    onCheckedChange = { viewModel.updateSelfUpdateCheckPref(it) },
                )
                SettingToggleItem(
                    icon = Tabler.Outline.Download,
                    title = stringResource(Res.string.settings_download_updates_automatically),
                    subtitle = stringResource(Res.string.settings_download_updates_subtitle),
                    checked = viewModel.selfUpdateDownloadEnabled,
                    enabled = viewModel.selfUpdateCheckEnabled,
                    onCheckedChange = { viewModel.updateSelfUpdateDownloadPref(it) },
                )
                if (viewModel.selfUpdateCheckEnabled) {
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = stringResource(Res.string.settings_check_for_updates),
                        subtitle = "",
                        onClick = {
                            // Result (update available or "up to date" with
                            // release notes) is shown in the app-wide update
                            // sheet driven by the app shell's UpdateCoordinator,
                            // not inline here — avoids the stale "(v)" status
                            // that drifted from a local copy of the version
                            // string.
                            onCheckForUpdates()
                        },
                    )
                    val dismissTitle = stringResource(Res.string.settings_update_dismiss_title)
                    val dismissSubtitle = stringResource(Res.string.settings_update_dismiss_subtitle)
                    val dismissPeriodLabels = mapOf(
                        UpdateDismissPeriod.HOURS_12 to stringResource(Res.string.settings_update_dismiss_12_hours),
                        UpdateDismissPeriod.HOURS_24 to stringResource(Res.string.settings_update_dismiss_24_hours),
                        UpdateDismissPeriod.DAYS_3 to stringResource(Res.string.settings_update_dismiss_3_days),
                        UpdateDismissPeriod.WEEK_1 to stringResource(Res.string.settings_update_dismiss_1_week),
                        UpdateDismissPeriod.NEVER to stringResource(Res.string.settings_update_dismiss_never),
                    )
                    SettingListItem(
                        icon = Tabler.Outline.Clock,
                        title = dismissTitle,
                        subtitle = dismissSubtitle,
                        trailingText = dismissPeriodLabels.getValue(viewModel.updateDismissPeriod),
                        onClick = {
                            activePicker = PickerState.List(
                                title = dismissTitle,
                                items = UpdateDismissPeriod.entries.toList(),
                                label = { period -> dismissPeriodLabels.getValue(period) },
                                isSelected = { it == viewModel.updateDismissPeriod },
                                onSelect = { viewModel.updateDismissPeriodPref(it) },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(
                icon = Tabler.Outline.Heart,
                title = stringResource(Res.string.settings_acknowledgements),
                initiallyExpanded = true,
            ) {
                Text(
                    text = stringResource(Res.string.settings_built_with_jellyfin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    SettingsPickerDialog(
        state = activePicker,
        onDismiss = { activePicker = null },
    )
}
