package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.runtime.Composable

/**
 * Android actual of the [PluginConfigHost] seam: the full WebView config
 * screen with a Koin-owned ViewModel.
 */
@Composable
actual fun PluginConfigHost(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
) {
    PluginConfigScreen(
        pluginId = pluginId,
        pluginName = pluginName,
        onBack = onBack,
    )
}

actual val pluginConfigSupported: Boolean = true
