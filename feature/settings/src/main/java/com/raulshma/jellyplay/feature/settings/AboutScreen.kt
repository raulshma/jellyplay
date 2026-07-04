package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Help
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.License
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Server
import com.composables.icons.tabler.outline.Bell
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.settings.R

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onLicensesClick: () -> Unit,
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

            SettingsGroupHeader(stringResource(R.string.settings_app_info))
            SettingsInfoRow(stringResource(R.string.settings_version_label), stringResource(R.string.settings_version_with_build, viewModel.appVersion, viewModel.buildType))
            SettingsInfoRow(stringResource(R.string.settings_min_android), viewModel.minSdkInfo)
            SettingsInfoRow(stringResource(R.string.settings_target_android), viewModel.targetSdkInfo)

            Spacer(Modifier.height(16.dp))

            if (viewModel.serverVersion != null) {
                SettingsGroupHeader(stringResource(R.string.settings_server_info))
                SettingsInfoRow(stringResource(R.string.settings_server_name_label), viewModel.serverName ?: stringResource(R.string.settings_unknown))
                SettingsInfoRow(stringResource(R.string.settings_server_version_label), viewModel.serverVersion ?: stringResource(R.string.settings_unknown))
                SettingsInfoRow(stringResource(R.string.settings_server_address_label_info), viewModel.serverAddress ?: stringResource(R.string.settings_unknown))

                Spacer(Modifier.height(16.dp))
            }

            SettingsGroupHeader(stringResource(R.string.settings_links))
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.InfoCircle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.settings_changelog),
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
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.Help, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.settings_help_faq),
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
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.InfoCircle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.settings_github),
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
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.License, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.settings_license),
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
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.ExternalLink, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.settings_open_source_licenses),
                onClick = onLicensesClick,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroupHeader(stringResource(R.string.settings_diagnostics))
            SettingsClickableRow(
                icon = {
                    Icon(
                        if (viewModel.isCollectingLogs) Tabler.Outline.Refresh else Tabler.Outline.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                title = if (viewModel.isCollectingLogs) stringResource(R.string.settings_collecting_logs) else stringResource(R.string.settings_send_app_logs),
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
            if (viewModel.selfUpdateCheckEnabled) {
                SettingsClickableRow(
                    icon = {
                        Icon(
                            if (viewModel.isCheckingUpdate) Tabler.Outline.Refresh else Tabler.Outline.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    title = when {
                        viewModel.isCheckingUpdate -> stringResource(R.string.settings_checking_for_updates)
                        viewModel.updateInfo?.isUpdateAvailable == true ->
                            stringResource(R.string.settings_update_available, viewModel.updateInfo!!.latestVersion)
                        viewModel.updateInfo != null -> stringResource(R.string.settings_up_to_date, viewModel.updateInfo!!.latestVersion)
                        else -> stringResource(R.string.settings_check_for_updates)
                    },
                    onClick = {
                        val info = viewModel.updateInfo
                        if (info?.isUpdateAvailable == true && info.downloadUrl.isNotBlank()) {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(info.downloadUrl),
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        } else if (!viewModel.isCheckingUpdate) {
                            viewModel.checkForUpdate()
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsGroupHeader(stringResource(R.string.settings_acknowledgements))
            Text(
                text = stringResource(R.string.settings_built_with_jellyfin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsClickableRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth12)
            .focusIndicator()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Tabler.Outline.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
