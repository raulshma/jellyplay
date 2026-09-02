package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_plugin_config_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop actual of the [PluginConfigHost] seam: no WebView host exists on
 * desktop, so the plugin-config route renders a static fallback. The route is
 * normally unreachable here — [pluginConfigSupported] is false and the
 * Configure affordances are hidden — this screen only backstops direct
 * navigation.
 */
@Composable
actual fun PluginConfigHost(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.admin_plugin_config_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

actual val pluginConfigSupported: Boolean = false
