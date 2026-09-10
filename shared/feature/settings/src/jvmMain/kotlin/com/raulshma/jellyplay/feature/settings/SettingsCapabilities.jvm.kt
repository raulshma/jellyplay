package com.raulshma.jellyplay.feature.settings

/**
 * Desktop's visibility half. Every false mirrors a no-op/null desktop seam
 * actual — the flag hides the row, the seam stays for the behavior it still
 * carries. The flag↔seam equalities are pinned in `DesktopPlatformActualsTest`.
 */
internal actual val settingsCapabilities: SettingsCapabilities = SettingsCapabilities(
    supportsDynamicColor = false,
    supportsNotifications = false,
    supportsAppLocaleOverride = false,
    supportsAudioCache = false,
    supportsScreenOrientation = false,
    supportsTouchGestures = false,
    supportsBiometric = false,
    supportsSystemNotificationSettings = false,
    supportsLogSharing = false,
)
