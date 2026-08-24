package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.runtime.Composable

/**
 * Android actual of the [PluginConfigHost] seam: the full WebView config
 * screen, unchanged from the legacy :feature:admin (it moved to this
 * module's androidMain verbatim, Hilt stripped, Koin-owned ViewModel).
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
