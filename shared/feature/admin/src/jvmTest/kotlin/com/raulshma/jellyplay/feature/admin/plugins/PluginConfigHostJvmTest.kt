package com.raulshma.jellyplay.feature.admin.plugins

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Pins the desktop half of the plugin-config host gate: desktop has no
 * WebView host, so [pluginConfigSupported] is false — the invariant that makes
 * PluginDetailScreen hide its Configure affordances and keeps the fallback
 * route ([PluginConfigHost]'s static "unavailable" composable, screen-verified
 * like all Compose surfaces) unreachable in normal navigation.
 *
 * The androidMain actual (true, plus the WebView screen and its JS bridge) is
 * outside the jvmTest source set and cannot be pinned here.
 */
class PluginConfigHostJvmTest {

    @Test
    fun `desktop cannot host the plugin-config webview`() {
        assertFalse(pluginConfigSupported)
    }
}
