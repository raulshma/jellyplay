package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.components.supportsDynamicColor

internal actual val settingsCapabilities: SettingsCapabilities = SettingsCapabilities(
    supportsDynamicColor = supportsDynamicColor,
    supportsNotifications = true,
    supportsAppLocaleOverride = true,
    supportsAudioCache = true,
    supportsScreenOrientation = true,
    supportsTouchGestures = true,
    supportsAppLock = true,
    supportsBiometric = true,
    supportsSystemNotificationSettings = true,
    supportsLogSharing = true,
    supportsAudioDeviceSelection = false,
    supportsMpvRenderProfiles = false,
    supportsVolumeMemory = false,
    supportsIdleAmbientScreen = false,
    supportsDiscordPresence = false,
    supportsShellHooks = false,
    supportsPip = true,
)
