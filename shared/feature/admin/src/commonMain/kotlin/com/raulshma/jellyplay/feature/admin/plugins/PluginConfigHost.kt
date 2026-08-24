package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.runtime.Composable

/**
 * Platform seam for the plugin-configuration WebView screen (admin conveyor).
 * The screen itself is Android-only — it drives `android.webkit.WebView`, the
 * `pluginBridge.js` asset, and a `JavascriptInterface` bridge — so it lives in
 * this module's androidMain verbatim. The common navigation graph reaches it
 * through this expect instead: the Android actual delegates to
 * [PluginConfigScreen]; desktop has no WebView host, so its actual renders a
 * static "not available" fallback ([admin_plugin_config_unavailable]) and the
 * route is normally unreachable there because the Configure affordances gate
 * on [pluginConfigSupported].
 *
 * The argument list mirrors the pre-migration `PluginConfigScreen(pluginId,
 * pluginName, onBack)` call site in AdminNavigation byte-for-byte.
 */
@Composable
expect fun PluginConfigHost(
    pluginId: String,
    pluginName: String,
    onBack: () -> Unit,
)

/**
 * Whether this platform can host the plugin-config WebView. Android = true
 * (the screen exists); desktop = false — PluginDetailScreen hides its
 * Configure card when this is false so the fallback route stays unreachable.
 */
expect val pluginConfigSupported: Boolean
