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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Activity
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Help
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.License
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Server
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
import com.raulshma.jellyplay.feature.settings.R

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicensesClick: () -> Unit,
    onCheckForUpdates: () -> Unit,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val context = LocalContext.current

    val backgroundColor = com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor()

    val logsSubject = stringResource(R.string.settings_logs_subject, viewModel.appVersion)
    val sendLogsChooserTitle = stringResource(R.string.settings_send_logs_chooser)

    val focusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = focusRequester,
        itemCount = 1,
        tag = "about_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.settings_about),
        onBack = onBack,
        backgroundColor = backgroundColor,
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
                    text = stringResource(R.string.settings_jellyplay),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_version_value, viewModel.appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))

            SettingsGroup(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(R.string.settings_app_info),
                initiallyExpanded = true,
            ) {
                SettingInfoItem(
                    icon = Tabler.Outline.Activity,
                    title = stringResource(R.string.settings_version_label),
                    subtitle = stringResource(R.string.settings_version_with_build, viewModel.appVersion, viewModel.buildType),
                )
                SettingInfoItem(
                    icon = Tabler.Outline.DeviceMobile,
                    title = stringResource(R.string.settings_min_android),
                    subtitle = viewModel.minSdkInfo,
                )
                SettingInfoItem(
                    icon = Tabler.Outline.DeviceMobile,
                    title = stringResource(R.string.settings_target_android),
                    subtitle = viewModel.targetSdkInfo,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (viewModel.serverVersion != null) {
                SettingsGroup(
                    icon = Tabler.Outline.Server,
                    title = stringResource(R.string.settings_server_info),
                    initiallyExpanded = true,
                ) {
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(R.string.settings_server_name_label),
                        subtitle = viewModel.serverName ?: stringResource(R.string.settings_unknown),
                        copyableValue = viewModel.serverName,
                    )
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(R.string.settings_server_version_label),
                        subtitle = viewModel.serverVersion ?: stringResource(R.string.settings_unknown),
                        copyableValue = viewModel.serverVersion,
                    )
                    SettingInfoItem(
                        icon = Tabler.Outline.Server,
                        title = stringResource(R.string.settings_server_address_label_info),
                        subtitle = viewModel.serverAddress ?: stringResource(R.string.settings_unknown),
                        copyableValue = viewModel.serverAddress,
                    )
                }

                Spacer(Modifier.height(16.dp))
            }

            SettingsGroup(
                icon = Tabler.Outline.ExternalLink,
                title = stringResource(R.string.settings_links),
                initiallyExpanded = true,
            ) {
                SettingListItem(
                    icon = Tabler.Outline.InfoCircle,
                    title = stringResource(R.string.settings_changelog),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/raulshma/jellyplay/releases"),
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.Help,
                    title = stringResource(R.string.settings_help_faq),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/raulshma/jellyplay/wiki"),
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.InfoCircle,
                    title = stringResource(R.string.settings_github),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/raulshma/jellyplay"),
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.License,
                    title = stringResource(R.string.settings_license),
                    subtitle = "",
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.gnu.org/licenses/gpl-3.0.en.html"),
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                )
                SettingListItem(
                    icon = Tabler.Outline.ExternalLink,
                    title = stringResource(R.string.settings_open_source_licenses),
                    subtitle = "",
                    onClick = onLicensesClick,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(
                icon = Tabler.Outline.Activity,
                title = stringResource(R.string.settings_diagnostics),
                initiallyExpanded = true,
            ) {
                SettingListItem(
                    icon = if (viewModel.isCollectingLogs) Tabler.Outline.Refresh else Tabler.Outline.Download,
                    title = if (viewModel.isCollectingLogs) stringResource(R.string.settings_collecting_logs) else stringResource(R.string.settings_send_app_logs),
                    subtitle = "",
                    onClick = {
                        if (!viewModel.isCollectingLogs) {
                            viewModel.sendAppLogs { uri ->
                                if (uri != null) {
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, logsSubject)
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(shareIntent, sendLogsChooserTitle),
                                    )
                                }
                            }
                        }
                    },
                )
                SettingToggleItem(
                    icon = Tabler.Outline.Refresh,
                    title = stringResource(R.string.settings_check_updates_automatically),
                    subtitle = stringResource(R.string.settings_check_updates_subtitle),
                    checked = viewModel.selfUpdateCheckEnabled,
                    onCheckedChange = { viewModel.updateSelfUpdateCheckPref(it) },
                )
                SettingToggleItem(
                    icon = Tabler.Outline.Download,
                    title = stringResource(R.string.settings_download_updates_automatically),
                    subtitle = stringResource(R.string.settings_download_updates_subtitle),
                    checked = viewModel.selfUpdateDownloadEnabled,
                    enabled = viewModel.selfUpdateCheckEnabled,
                    onCheckedChange = { viewModel.updateSelfUpdateDownloadPref(it) },
                )
                if (viewModel.selfUpdateCheckEnabled) {
                    SettingListItem(
                        icon = Tabler.Outline.Refresh,
                        title = stringResource(R.string.settings_check_for_updates),
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
                }
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroup(
                icon = Tabler.Outline.Heart,
                title = stringResource(R.string.settings_acknowledgements),
                initiallyExpanded = true,
            ) {
                Text(
                    text = stringResource(R.string.settings_built_with_jellyfin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
