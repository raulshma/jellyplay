package com.raulshma.jellyplay.core.model

actual val currentPlatform: PlatformKind = PlatformKind.DESKTOP

/**
 * Mirrors `DesktopMpvPlayerEngineFactory.create`'s branches: only MPV builds
 * a real desktop engine; Exo/VLC ride mpv as a stand-in there and EXTERNAL
 * gets a no-op (see the factory KDoc). Those fallbacks stay as a playback
 * safety net, but the settings surface must not offer what the factory does
 * not ship.
 */
actual val platformEngineSupport: PlatformEngineSupport = PlatformEngineSupport(
    engines = listOf(PlayerType.MPV),
    default = PlayerType.MPV,
)
