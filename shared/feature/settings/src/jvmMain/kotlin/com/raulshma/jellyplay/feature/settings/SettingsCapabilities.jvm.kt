package com.raulshma.jellyplay.feature.settings

/**
 * Desktop's visibility half. Every false mirrors a no-op/null desktop seam
 * actual — the flag hides the row, the seam stays for the behavior it still
 * carries. The flag↔seam equalities are pinned in `DesktopPlatformActualsTest`.
 *
 * `supportsAudioDeviceSelection` / `supportsMpvRenderProfiles` /
 * `supportsVolumeMemory` are the three TRUEs: the desktop mpv stack backs the
 * audio-device rows (DesktopAudioDeviceEnumerator over libmpv), the render
 * rows (Anime4K extraction + the HWND-embed `vo=gpu-next` HDR
 * path) and the per-content-type volume-memory toggle (the app owns
 * mpv's volume scalar only on this platform).
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
    supportsAudioDeviceSelection = true,
    supportsMpvRenderProfiles = true,
    supportsVolumeMemory = true,
    supportsIdleAmbientScreen = true,
)
