package com.raulshma.jellyplay.feature.player.live.engine

/**
 * Constructs the live engine for the shared ViewModel (player-live
 * conveyor). The legacy Hilt-owned class became this commonMain SAM seam:
 * the platform side owns the app-scoped dependencies (Android: the
 * application context + the named `streaming` OkHttpClient — see
 * androidMain's `ExoLiveEngineFactory` and `androidPlayerLiveModule`), so
 * [LiveTvPlayerViewModel][com.raulshma.jellyplay.feature.player.live.LiveTvPlayerViewModel]
 * stays constructible in commonMain and its jvmTest.
 *
 * v1 ships a single ExoPlayer-based live engine — every Android resolution
 * builds an [ExoLiveEngine]-shaped instance (previously the `preferred:
 * PlayerType` parameter and the speculative `when` arms / stub `MpvLiveEngine`
 * existed only to imply a switch that never happens; they were removed at
 * the legacy cleanup, not re-added here). Revisit if a real second live
 * adapter lands — at that point restore the dispatch (and the seam split)
 * together, not before.
 */
fun interface LiveEngineFactory {
    fun create(
        config: LiveEngineConfig,
        onTranscodeFallbackNeeded: () -> Unit,
    ): LivePlayerEngine
}
