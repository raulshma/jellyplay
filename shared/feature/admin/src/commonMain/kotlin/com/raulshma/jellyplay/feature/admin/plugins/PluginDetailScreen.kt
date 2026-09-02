package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Puzzle
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginStatus
import com.raulshma.jellyplay.core.model.PluginVersionInfo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.components.MarkdownText
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_bundled
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cancel
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_configuration
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_continue
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_current
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_description
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_details
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_developer
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_disable
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_dismiss
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_edit_plugin_settings
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_enable
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_install
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_install_third_party_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_install_third_party_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_loading_version_history
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_repository
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_status
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_uninstall
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_uninstall_plugin_body
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_uninstall_plugin_title
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_unknown
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_version
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_version_history
import com.raulshma.jellyplay.feature.admin.plugins.components.PluginStatusBadge

@Composable
fun PluginDetailScreen(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
    onConfig: (pluginId: String, pluginName: String) -> Unit = { _, _ -> },
    viewModel: PluginDetailViewModel = koinViewModel(),
) {
    viewModel.initialize(pluginId, pluginName)
    val state = viewModel.state
    val backgroundColorState = rememberScreenBackgroundColorState()
    val adaptiveInfo = LocalAdaptiveInfo.current
    var showUninstallDialog by remember { mutableStateOf(false) }
    // Pending version install awaiting third-party trust confirmation.
    var pendingTrustInstall by remember { mutableStateOf<PluginVersionInfo?>(null) }

    // TV focus-on-launch: focus the first card once content arrives so D-pad input lands on
    // content, not the navigation drawer.
    val listFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = listFocusRequester,
        itemCount = if (state.isLoading || state.plugin == null) 0 else 1,
        tag = "plugin_detail_init",
    )

    JellyPlayScreenScaffold(
        title = state.plugin?.name ?: pluginName,
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) {
        if (state.isLoading) {
            ScreenLoadingState()
        } else {
            val contentPadding = adaptiveInfo.contentPadding()
            val bottomPadding = adaptiveInfo.bottomPadding()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRestorer()
                    .focusRequester(listFocusRequester),
                contentPadding = PaddingValues(
                    start = contentPadding,
                    end = contentPadding,
                    top = 8.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.plugin?.let { plugin ->
                    if (state.error != null) {
                        item { ErrorBanner(message = state.error, onDismiss = { viewModel.clearError() }) }
                    }

                    item {
                        PluginHeaderCard(
                            plugin = plugin,
                            isToggling = state.isToggling,
                            isEnabledOverride = state.isEnabledOverride,
                            onToggle = { viewModel.toggleEnabled() },
                            onUninstall = { showUninstallDialog = true },
                        )
                    }

                    if (state.hasConfigPage && pluginConfigSupported) {
                        item {
                            ConfigActionCard(
                                pluginId = plugin.id,
                                pluginName = plugin.name,
                                onConfig = onConfig,
                            )
                        }
                    }

                    if (plugin.description.isNotBlank()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                shape = ShapeCache.smooth16,
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(Res.string.admin_description),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        plugin.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Metadata table (Status / Version / Developer / Repository),
                    // mirroring jellyfin-web's PluginDetailsTable.
                    item {
                        PluginDetailsMetadataCard(
                            plugin = plugin,
                            packageName = state.pluginPackage?.name,
                            owner = state.pluginPackage?.owner,
                            repositoryUrl = state.pluginPackage?.versions
                                ?.firstOrNull { it.version == plugin.version }?.repositoryUrl,
                        )
                    }

                    state.pluginPackage?.let { pkg ->
                        if (pkg.versions.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(Res.string.admin_version_history),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }
                            items(pkg.versions, key = { it.version + it.repositoryName }) { version ->
                                val isCurrentVersion = version.version == plugin.version
                                VersionHistoryItem(
                                    version = version,
                                    isCurrentVersion = isCurrentVersion,
                                    isInstalling = state.installingVersion == version.version,
                                    onInstall = {
                                        // Gate third-party installs behind a trust disclaimer
                                        // (jellyfin-web onInstall / TRUSTED_REPO_URL).
                                        if (isTrustedRepository(version.repositoryUrl)) {
                                            viewModel.installVersion(version)
                                        } else {
                                            pendingTrustInstall = version
                                        }
                                    },
                                )
                            }
                        }
                    } ?: run {
                        if (state.isLoadingVersions) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, start = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Text(
                                        stringResource(Res.string.admin_loading_version_history),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUninstallDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.admin_uninstall_plugin_title),
            message = stringResource(Res.string.admin_uninstall_plugin_body, state.plugin?.name ?: ""),
            confirmText = stringResource(Res.string.admin_uninstall),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            confirmLoading = state.isUninstalling,
            onConfirm = {
                showUninstallDialog = false
                viewModel.uninstall { onBack() }
            },
            onDismiss = { showUninstallDialog = false },
        )
    }

    // Third-party install disclaimer (mirrors jellyfin-web MessagePluginInstallDisclaimer).
    pendingTrustInstall?.let { version ->
        ConfirmDialog(
            title = stringResource(Res.string.admin_install_third_party_title),
            message = stringResource(Res.string.admin_install_third_party_body, "version ${version.version}"),
            confirmText = stringResource(Res.string.admin_continue),
            dismissText = stringResource(Res.string.admin_cancel),
            tone = ConfirmTone.WARNING,
            icon = Tabler.Outline.AlertTriangle,
            onConfirm = {
                val v = version
                pendingTrustInstall = null
                viewModel.installVersion(v)
            },
            onDismiss = { pendingTrustInstall = null },
        )
    }
}

@Composable
private fun PluginHeaderCard(
    plugin: PluginInfo,
    isToggling: Boolean,
    isEnabledOverride: Boolean?,
    onToggle: () -> Unit,
    onUninstall: () -> Unit,
) {
    val toggleFocusState = rememberTvFocusState()
    val uninstallFocusState = rememberTvFocusState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.Puzzle,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Version ${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PluginStatusBadge(status = plugin.status)
                Spacer(Modifier.weight(1f))
                if (isToggling) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                }
                val statusEnabled = plugin.status == PluginStatus.ACTIVE || plugin.status == PluginStatus.RESTART
                // Use the optimistic override while a toggle is in flight so the
                // label reflects the requested state immediately.
                val isEnabled = isEnabledOverride ?: statusEnabled
                FilledTonalButton(
                    onClick = onToggle,
                    enabled = !isToggling,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.then(toggleFocusState.focusModifier).tvFocusIndicator(toggleFocusState, ShapeCache.smooth12),
                ) {
                    Text(if (isEnabled) stringResource(Res.string.admin_disable) else stringResource(Res.string.admin_enable))
                }
                if (plugin.canUninstall) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onUninstall,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.then(uninstallFocusState.focusModifier).tvFocusIndicator(uninstallFocusState, ShapeCache.smooth12),
                    ) {
                        Icon(
                            Tabler.Outline.Trash,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.admin_uninstall), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigActionCard(
    pluginId: String,
    pluginName: String,
    onConfig: (String, String) -> Unit,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "configCardScale",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onConfig(pluginId, pluginName) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.admin_configuration),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(Res.string.admin_edit_plugin_settings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Tabler.Outline.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionHistoryItem(
    version: PluginVersionInfo,
    isCurrentVersion: Boolean,
    isInstalling: Boolean,
    onInstall: () -> Unit,
) {
    val focusState = rememberTvFocusState()
    val installFocusState = rememberTvFocusState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isCurrentVersion) focusState.focusModifier else Modifier)
            .then(if (!isCurrentVersion) Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentVersion) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "v${version.version}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrentVersion) {
                        Text(
                            stringResource(Res.string.admin_current),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (version.changelog.isNullOrBlank().not()) {
                    Spacer(Modifier.height(2.dp))
                    MarkdownText(
                        text = version.changelog!!,
                        modifier = Modifier.heightIn(max = 80.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (version.repositoryName.isNotBlank()) {
                        Text(
                            version.repositoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (version.targetAbi.isNullOrBlank().not()) {
                        Text(
                            version.targetAbi!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isCurrentVersion) {
                FilledTonalButton(
                    onClick = onInstall,
                    enabled = !isInstalling,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.then(installFocusState.focusModifier).tvFocusIndicator(installFocusState, ShapeCache.smooth12),
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Tabler.Outline.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.admin_install), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Inline error banner for failed enable/disable/install/uninstall operations. */
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.admin_dismiss)) }
        }
    }
}

/**
 * Status / Version / Developer / Repository details card, mirroring
 * jellyfin-web's PluginDetailsTable component.
 */
@Composable
private fun PluginDetailsMetadataCard(
    plugin: PluginInfo,
    packageName: String?,
    owner: String?,
    repositoryUrl: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(Res.string.admin_details),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            val developer = when {
                !plugin.canUninstall -> stringResource(Res.string.admin_bundled)
                !owner.isNullOrBlank() -> owner
                else -> stringResource(Res.string.admin_unknown)
            }
            val repository = when {
                !plugin.canUninstall -> stringResource(Res.string.admin_bundled)
                !repositoryUrl.isNullOrBlank() -> repositoryUrl
                else -> stringResource(Res.string.admin_unknown)
            }
            MetadataRow(stringResource(Res.string.admin_status), plugin.status.name.lowercase().replaceFirstChar { it.uppercase() })
            MetadataRow(stringResource(Res.string.admin_version), plugin.version.ifBlank { "—" })
            MetadataRow(stringResource(Res.string.admin_developer), developer)
            MetadataRow(stringResource(Res.string.admin_repository), repository)
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
        )
    }
}
