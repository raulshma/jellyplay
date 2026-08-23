package com.raulshma.jellyplay.feature.settings

/**
 * Whether this platform can derive a wallpaper-based (Material You) color
 * scheme. Android 12+ says yes (the Build.VERSION_CODES.S gate the
 * pre-migration screen inlined); desktop has no dynamic color, so the
 * dynamic-theming row stays hidden there.
 */
internal expect val supportsDynamicColor: Boolean
