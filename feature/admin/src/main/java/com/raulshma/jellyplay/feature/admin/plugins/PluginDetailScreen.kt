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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
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
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.plugins.components.MarkdownText
import com.raulshma.jellyplay.feature.admin.plugins.components.PluginStatusBadge

@Composable
fun PluginDetailScreen(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
    onConfig: (pluginId: String, pluginName: String) -> Unit = { _, _ -> },
    viewModel: PluginDetailViewModel = hiltViewModel(),
) {
    viewModel.initialize(pluginId, pluginName)
    val state = viewModel.state
    val backgroundColor = rememberScreenBackgroundColor()
    val adaptiveInfo = LocalAdaptiveInfo.current
    var showUninstallDialog by remember { mutableStateOf(false) }

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
        backgroundColor = backgroundColor,
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
                    item {
                        PluginHeaderCard(
                            plugin = plugin,
                            isToggling = state.isToggling,
                            onToggle = { viewModel.toggleEnabled() },
                            onUninstall = { showUninstallDialog = true },
                        )
                    }

                    if (state.hasConfigPage) {
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
                                        "Description",
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

                    state.pluginPackage?.let { pkg ->
                        if (pkg.versions.isNotEmpty()) {
                            item {
                                Text(
                                    "Version History",
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
                                    onInstall = { viewModel.installVersion(version) },
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
                                        "Loading version history...",
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
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("Uninstall Plugin") },
            text = {
                Text("Are you sure you want to uninstall ${state.plugin?.name ?: "this plugin"}? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        viewModel.uninstall { onBack() }
                    },
                    enabled = !state.isUninstalling,
                ) {
                    if (state.isUninstalling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Uninstall", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PluginHeaderCard(
    plugin: PluginInfo,
    isToggling: Boolean,
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
                val isEnabled = plugin.status == PluginStatus.ACTIVE || plugin.status == PluginStatus.RESTART
                FilledTonalButton(
                    onClick = onToggle,
                    enabled = !isToggling,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.then(toggleFocusState.focusModifier).tvFocusIndicator(toggleFocusState, ShapeCache.smooth12),
                ) {
                    Text(if (isEnabled) "Disable" else "Enable")
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
                        Text("Uninstall", color = MaterialTheme.colorScheme.error)
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
                    "Configuration",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Edit plugin settings",
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
                            "Current",
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
                        Text("Install", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
