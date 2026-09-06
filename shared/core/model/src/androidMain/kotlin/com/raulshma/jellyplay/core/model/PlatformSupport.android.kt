package com.raulshma.jellyplay.core.model

actual val currentPlatform: PlatformKind = PlatformKind.ANDROID

/**
 * Mirrors `AndroidPlayerEngineFactory.create`'s branches — all four are
 * selectable. EXTERNAL's branch builds a [NoOpEngine] because playback is
 * handed to an external app (progress reported out-of-band); the engine
 * object is the mechanism, not a degradation.
 */
actual val platformEngineSupport: PlatformEngineSupport = PlatformEngineSupport(
    engines = PlayerType.entries.toList(),
    default = PlayerType.EXO_PLAYER,
)
