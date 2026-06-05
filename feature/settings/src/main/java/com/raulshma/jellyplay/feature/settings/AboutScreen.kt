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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.ExternalLink
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.License
import com.composables.icons.tabler.outline.Server
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

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

    JellyPlayScreenScaffold(
        title = "About",
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
                ),
        ) {
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "JellyPlay",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version ${viewModel.appVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))

            SettingsGroupHeader("App Info")
            SettingsInfoRow("Version", "${viewModel.appVersion} (${viewModel.buildType})")
            SettingsInfoRow("Min Android", "Android 9 (API 28)")
            SettingsInfoRow("Target Android", "Android 15 (API 37)")

            Spacer(Modifier.height(16.dp))

            if (viewModel.serverVersion != null) {
                SettingsGroupHeader("Server Info")
                SettingsInfoRow("Server Name", viewModel.serverName ?: "Unknown")
                SettingsInfoRow("Server Version", viewModel.serverVersion ?: "Unknown")
                SettingsInfoRow("Server Address", viewModel.serverAddress ?: "Unknown")

                Spacer(Modifier.height(16.dp))
            }

            SettingsGroupHeader("Links")
            SettingsClickableRow(
                icon = { Icon(Tabler.Outline.InfoCircle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                title = "GitHub",
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
                title = "License (GPL-3.0)",
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
                title = "Open Source Licenses",
                onClick = onLicensesClick,
            )

            Spacer(Modifier.height(16.dp))

            SettingsGroupHeader("Acknowledgements")
            Text(
                text = "Built with Jellyfin SDK",
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
