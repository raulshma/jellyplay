package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_plugins_found
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_search_plugins
import com.raulshma.jellyplay.feature.admin.plugins.PluginCategory
import com.raulshma.jellyplay.feature.admin.plugins.PluginStatusFilter

@Composable
fun CatalogTab(
    packages: List<PluginPackage>,
    allPackages: List<PluginPackage>,
    installedPlugins: List<PluginInfo>,
    searchQuery: String,
    statusFilter: PluginStatusFilter,
    categoryFilter: PluginCategory,
    isCatalogLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (PluginStatusFilter) -> Unit,
    onCategoryFilterChange: (PluginCategory) -> Unit,
    onInstall: (name: String, guid: String?, version: String?, repoUrl: String?) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    if (isCatalogLoading && allPackages.isEmpty()) {
        ScreenLoadingState()
        return
    }

    val installedGuids = installedPlugins.map { it.id }.toSet()
    val isTv = LocalTvMode.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(Res.string.admin_search_plugins)) },
                    leadingIcon = {
                        Icon(
                            Tabler.Outline.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    singleLine = true,
                    shape = ShapeCache.smooth16,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )

                // Status filter chips (All / Installed / Available).
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PluginStatusFilter.entries, key = { it.name }) { status ->
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { onStatusFilterChange(status) },
                            label = { Text(status.displayName) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }

                // Category filter chips.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PluginCategory.entries, key = { it.name }) { category ->
                        FilterChip(
                            selected = categoryFilter == category,
                            onClick = { onCategoryFilterChange(category) },
                            label = { Text(category.displayName) },
                            shape = CircleShape,
                        )
                    }
                }
            }
        }

        if (packages.isEmpty()) {
            item {
                Text(
                    stringResource(Res.string.admin_no_plugins_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }

        items(packages, key = { it.guid }) { pkg ->
            PluginCatalogCard(
                packageInfo = pkg,
                isInstalled = pkg.guid in installedGuids,
                onInstall = onInstall,
            )
        }
    }
}
