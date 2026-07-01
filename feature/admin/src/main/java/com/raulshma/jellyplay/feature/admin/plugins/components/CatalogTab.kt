package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.model.PluginPackage
import com.raulshma.jellyplay.core.ui.components.ScreenLoadingState
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer

@Composable
fun CatalogTab(
    packages: List<PluginPackage>,
    allPackages: List<PluginPackage>,
    installedPlugins: List<PluginInfo>,
    searchQuery: String,
    isCatalogLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
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
            .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                placeholder = { Text("Search plugins...") },
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
        }

        if (packages.isEmpty() && searchQuery.isNotBlank()) {
            item {
                Text(
                    "No plugins found matching \"$searchQuery\"",
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
