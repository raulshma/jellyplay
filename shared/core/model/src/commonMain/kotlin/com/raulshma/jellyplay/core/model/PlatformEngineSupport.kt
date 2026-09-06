package com.raulshma.jellyplay.core.model

/**
 * Which [PlayerType] engines the running binary actually ships, and the
 * engine to fall back to when a stored choice is unavailable.
 *
 * The per-platform actuals MUST mirror which engines that platform's
 * `PlayerEngineFactory` builds as a real, selectable engine — not every
 * `when` branch: desktop's factory rides Exo/VLC on mpv as a stand-in and
 * hands EXTERNAL to a no-op, yet only MPV is offered. The factory `when` is
 * the source of truth (compiler-forced exhaustive over [PlayerType], so an
 * enum addition cannot compile without a factory decision). Declaring
 * availability here next to the enum lets preference reads, the settings
 * picker and the search catalog all consume one table instead of
 * re-deriving it per site. The desktop actual is pinned in the settings
 * module's `DesktopPlatformActualsTest`; the Android actual is
 * `PlayerType.entries.toList()`, which auto-syncs with any entry the
 * factory `when` is forced to decide.
 */
data class PlatformEngineSupport(
    val engines: List<PlayerType>,
    val default: PlayerType,
) {
    fun isAvailable(type: PlayerType): Boolean = type in engines
}

/**
 * The shipped-engine table for the running binary.
 *
 * - Android: all four [PlayerType]s (`AndroidPlayerEngineFactory` builds real
 *   Exo/mpv/VLC engines; EXTERNAL routes to `NoOpEngine` + the external-app
 *   intent). Default EXO_PLAYER — the historical stored default.
 * - Desktop: MPV only (`DesktopMpvPlayerEngineFactory` rides every choice on
 *   mpv; Exo/VLC/EXTERNAL have no desktop engine). Default MPV.
 * - Web: empty — web playback does not route through [PlayerType] yet; the
 *   default is inert.
 */
expect val platformEngineSupport: PlatformEngineSupport
