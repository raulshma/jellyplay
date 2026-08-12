package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Puzzle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.feature.admin.plugins.isTrustedRepository

@Composable
fun PluginCatalogCard(
    packageInfo: PluginPackage,
    isInstalled: Boolean,
    onInstall: (name: String, guid: String?, version: String?, repoUrl: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val installFocusState = rememberTvFocusState(focusedScale = 1.05f)
    var showTrustDialog by remember { mutableStateOf(false) }

    val latestVer = packageInfo.latestVersion
    val needsTrustConfirm = latestVer != null && !isTrustedRepository(latestVer.repositoryUrl)

    // Centralized install entry point: shows the third-party disclaimer first when
    // the package isn't from the official repo (mirrors jellyfin-web `onInstall`).
    fun requestInstall() {
        if (needsTrustConfirm) showTrustDialog = true
        else latestVer?.let {
            onInstall(packageInfo.name, packageInfo.guid, it.version, it.repositoryUrl)
        }
    }

    if (showTrustDialog && latestVer != null) {
        ConfirmDialog(
            title = stringResource(R.string.admin_install_third_party_title),
            message = stringResource(R.string.admin_install_third_party_body, "\"${packageInfo.name}\""),
            confirmText = stringResource(R.string.admin_continue),
            dismissText = stringResource(R.string.admin_cancel),
            tone = ConfirmTone.WARNING,
            icon = Tabler.Outline.AlertTriangle,
            onConfirm = {
                showTrustDialog = false
                onInstall(packageInfo.name, packageInfo.guid, latestVer.version, latestVer.repositoryUrl)
            },
            onDismiss = { showTrustDialog = false },
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PluginImage(
                    imageUrl = packageInfo.imageUrl,
                    isInstalled = isInstalled,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = packageInfo.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (packageInfo.owner.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.admin_plugin_by_author, packageInfo.owner),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (packageInfo.category.isNotBlank()) {
                            Text(
                                text = packageInfo.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (packageInfo.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = packageInfo.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (latestVer != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.admin_latest_version, latestVer.version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isInstalled) {
                        Text(
                            text = stringResource(R.string.admin_installed),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { requestInstall() },
                            modifier = Modifier
                                .then(installFocusState.focusModifier)
                                .tvFocusIndicator(installFocusState, ShapeCache.smooth12),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 4.dp,
                            ),
                        ) {
                            Icon(
                                Tabler.Outline.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.admin_install), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders the plugin's image (catalog package image) or a fallback puzzle icon.
 * Uses Coil's AsyncImage with the app's standard image pipeline.
 */
@Composable
internal fun PluginImage(
    imageUrl: String?,
    isInstalled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            // Route through the central MediaImage pipeline so the plugin image
            // gets the same crossfade + explicit decode size as the rest of the app
            // (avoids the bare-String AsyncImage over-decode at 40 dp).
            MediaImage(
                url = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                size = coil3.size.Size(80, 80),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isInstalled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Puzzle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isInstalled) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
