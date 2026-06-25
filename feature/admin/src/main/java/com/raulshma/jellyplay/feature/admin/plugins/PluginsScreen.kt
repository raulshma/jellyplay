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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColor
import com.raulshma.jellyplay.core.ui.tv.TvGrabInitialFocus
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
    val backgroundColor = rememberScreenBackgroundColor()
    val adaptiveInfo = LocalAdaptiveInfo.current

    // TV focus-on-launch: focus the tab row once data arrives so D-pad input lands on content,
    // not the navigation drawer.
    val contentFocusRequester = remember { FocusRequester() }
    TvGrabInitialFocus(
        focusRequester = contentFocusRequester,
        itemCount = if (state.isLoading && selectedTab == 0) 0 else 1,
        tag = "plugins_init",
    )

    JellyPlayScreenScaffold(
        title = "Plugins",
        onBack = onBack,
        backgroundColor = backgroundColor,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Tabler.Outline.Refresh, contentDescription = "Refresh")
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
                    text = { Text("Installed") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        viewModel.selectTab(1)
                    },
                    text = { Text("Catalog") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        viewModel.selectTab(2)
                    },
                    text = { Text("Repositories") },
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
                            onUninstall = { viewModel.uninstallPlugin(it) },
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
                            isCatalogLoading = state.isCatalogLoading,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
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
                    text = "Plugin Management - Alpha",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = "This feature is experimental. Use at your own risk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}
