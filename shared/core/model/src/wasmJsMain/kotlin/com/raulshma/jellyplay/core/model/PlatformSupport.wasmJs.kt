package com.raulshma.jellyplay.core.model

actual val currentPlatform: PlatformKind = PlatformKind.WEB

/** Web playback rides the HtmlVideoEngine — no [PlayerType] engines ship. */
actual val platformEngineSupport: PlatformEngineSupport = PlatformEngineSupport(
    engines = emptyList(),
    default = PlayerType.EXO_PLAYER,
)
