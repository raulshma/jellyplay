package com.raulshma.jellyplay.feature.admin.plugins.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PluginInfo
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_no_plugins_installed

@Composable
fun InstalledPluginsTab(
    plugins: List<PluginInfo>,
    onEnable: (PluginInfo) -> Unit,
    onDisable: (PluginInfo) -> Unit,
    onUninstall: (String) -> Unit,
    onPluginClick: (pluginId: String, pluginName: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    if (plugins.isEmpty()) {
        Text(
            stringResource(Res.string.admin_no_plugins_installed),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val isTv = LocalTvMode.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .then(if (isTv) Modifier.tvFocusRestorer() else Modifier),
        contentPadding = contentPadding,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        items(plugins, key = { it.id }) { plugin ->
            PluginListItem(
                plugin = plugin,
                onEnable = onEnable,
                onDisable = onDisable,
                onUninstall = onUninstall,
                onClick = onPluginClick,
            )
        }
    }
}
