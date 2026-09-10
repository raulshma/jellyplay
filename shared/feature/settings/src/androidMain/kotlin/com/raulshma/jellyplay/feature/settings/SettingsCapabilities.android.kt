package com.raulshma.jellyplay.feature.settings

import android.os.Build

internal actual val settingsCapabilities: SettingsCapabilities = SettingsCapabilities(
    supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    supportsNotifications = true,
    supportsAppLocaleOverride = true,
    supportsAudioCache = true,
    supportsScreenOrientation = true,
    supportsTouchGestures = true,
    supportsBiometric = true,
    supportsSystemNotificationSettings = true,
    supportsLogSharing = true,
)
