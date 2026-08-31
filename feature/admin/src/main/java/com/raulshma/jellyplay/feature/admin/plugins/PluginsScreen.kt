package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.Refresh
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
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
import com.raulshma.jellyplay.feature.admin.R
import com.raulshma.jellyplay.feature.admin.plugins.components.InstalledPluginsTab
import com.raulshma.jellyplay.feature.admin.plugins.components.CatalogTab
import com.raulshma.jellyplay.feature.admin.plugins.components.RepositoriesTab
import com.raulshma.jellyplay.feature.admin.plugins.components.InstallationProgressBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onBack: () -> Unit,
    onPluginDetail: (pluginId: String, pluginName: String) -> Unit = { _, _ -> },
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    var selectedTab by remember { mutableIntStateOf(0) }
    val backgroundColorState = rememberScreenBackgroundColorState()
    val adaptiveInfo = LocalAdaptiveInfo.current

    // Pending uninstall confirmation. Unlike the per-plugin detail screen, the
    // list-level uninstall previously fired immediately — a destructive server
    // operation with no chance to cancel. `rememberSaveable` so an open
    // confirmation survives config change (rotation) rather than silently
    // dismissing a destructive-action dialog.
    var pendingUninstallId by rememberSaveable { mutableStateOf<String?>(null) }

    // TV focus-on-launch: focus the tab row once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (state.isLoading && selectedTab == 0) 0 else 1,
        tag = "plugins_init",
    )

    JellyPlayScreenScaffold(
        title = stringResource(R.string.admin_plugins_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
        actions = {
            val refreshFocusState = rememberTvFocusState()
            IconButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.then(refreshFocusState.focusModifier).tvFocusIndicator(refreshFocusState, CircleShape),
            ) {
                Icon(Tabler.Outline.Refresh, contentDescription = stringResource(R.string.admin_refresh))
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .focusRequester(contentFocusRequester)
                .padding(paddingValues),
        ) {
            if (state.activeInstallations.isNotEmpty()) {
                InstallationProgressBanner(
                    installations = state.activeInstallations,
                    onCancel = { viewModel.cancelInstallation(it) },
                )
            }

            AlphaBanner()

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        viewModel.selectTab(0)
                    },
                    text = { Text(stringResource(R.string.admin_installed_tab)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.selectTab(1)
                    },
                    text = { Text(stringResource(R.string.admin_catalog_tab)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.selectTab(2)
                    },
                    text = { Text(stringResource(R.string.admin_repositories_tab)) },
                )
            }

            if (state.isLoading && selectedTab == 0) {
                ScreenLoadingState()
            } else {
                val contentPadding = adaptiveInfo.contentPadding()
                val bottomPadding = adaptiveInfo.bottomPadding()
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (selectedTab) {
                        0 -> InstalledPluginsTab(
                            plugins = state.installedPlugins,
                            onEnable = { viewModel.enablePlugin(it) },
                            onDisable = { viewModel.disablePlugin(it) },
                            onUninstall = { pendingUninstallId = it },
                            onPluginClick = onPluginDetail,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = contentPadding,
                                end = contentPadding,
                                top = 8.dp,
                                bottom = bottomPadding,
                            ),
                        )
                        1 -> CatalogTab(
                            packages = state.filteredPackages,
                            allPackages = state.availablePackages,
                            installedPlugins = state.installedPlugins,
                            searchQuery = state.catalogSearchQuery,
                            statusFilter = state.catalogStatusFilter,
                            categoryFilter = state.catalogCategoryFilter,
                            isCatalogLoading = state.isCatalogLoading,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onStatusFilterChange = { viewModel.updateStatusFilter(it) },
                            onCategoryFilterChange = { viewModel.updateCategoryFilter(it) },
                            onInstall = { name, guid, version, repoUrl ->
                                viewModel.installPackage(name, guid, version, repoUrl)
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = contentPadding,
                                end = contentPadding,
                                top = 8.dp,
                                bottom = bottomPadding,
                            ),
                        )
                        2 -> RepositoriesTab(
                            repositories = state.repositories,
                            isLoading = state.isReposLoading,
                            onAddRepository = { name, url -> viewModel.addRepository(name, url) },
                            onRemoveRepository = { viewModel.removeRepository(it) },
                            onToggleRepository = { index, enabled -> viewModel.toggleRepository(index, enabled) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = contentPadding,
                                end = contentPadding,
                                top = 8.dp,
                                bottom = bottomPadding,
                            ),
                        )
                    }
                }
            }
        }
    }

    pendingUninstallId?.let { pluginId ->
        ConfirmDialog(
            title = stringResource(R.string.admin_uninstall_plugin_confirm_title),
            message = "This removes the plugin from the server. It can be reinstalled from the Catalog.",
            confirmText = stringResource(R.string.admin_uninstall),
            dismissText = stringResource(R.string.admin_cancel),
            tone = ConfirmTone.NEUTRAL,
            onConfirm = {
                viewModel.uninstallPlugin(pluginId)
                pendingUninstallId = null
            },
            onDismiss = { pendingUninstallId = null },
        )
    }
}

@Composable
private fun AlphaBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column {
                Text(
                    text = stringResource(R.string.admin_plugin_management_alpha),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = stringResource(R.string.admin_plugin_experimental),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
