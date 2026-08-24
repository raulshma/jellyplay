package com.raulshma.jellyplay.feature.onboarding

/**
 * Whether this platform can derive a wallpaper-based (Material You) color
 * scheme. Android 12+ says yes (the Build.VERSION_CODES.S gate the
 * pre-migration AppearanceStep inlined); desktop has no dynamic color, so
 * the dynamic-theming row stays hidden there (same shape as the settings
 * module's AppearancePlatform seam).
 */
internal expect val supportsDynamicColor: Boolean
