package com.raulshma.jellyplay.feature.settings

/**
 * Web's visibility half: every flag false mirrors a no-op/null wasm seam
 * actual — the flag hides the row, the seam stays for the behavior it still
 * carries (the same flag⟺seam-null-ness rule the desktop actual documents;
 * pinned for desktop in DesktopPlatformActualsTest).
 */
internal actual val settingsCapabilities: SettingsCapabilities = SettingsCapabilities(
    supportsDynamicColor = false,
    supportsNotifications = false,
    supportsAppLocaleOverride = false,
    supportsAudioCache = false,
    supportsScreenOrientation = false,
    // Conservative, matching desktop: the gesture surface (double-tap seek)
    // rides the native player engines; the web engine story is not this
    // batch's scope, so the row stays hidden rather than over-promise.
    supportsTouchGestures = false,
    supportsBiometric = false,
    supportsSystemNotificationSettings = false,
    supportsLogSharing = false,
)
