package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import com.raulshma.jellyplay.core.model.PlayerType
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Selects the live engine for the user's preferred [PlayerType]. See
 * `docs/superpowers/specs/2026-07-19-live-tv-player-design.md` engine-switching
 * section: LIBVLC and EXTERNAL fall back to Exo for live; MPV is honored only
 * if a real implementation exists (currently it does not — see [MpvLiveEngine]).
 *
 * Injected via Hilt. The streaming OkHttpClient is the same `@Named("streaming")`
 * instance used by the VOD `PlayerEngineFactory`.
 */
@Singleton
class LiveEngineFactory @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    @Named("streaming") private val streamingClient: OkHttpClient,
) {
    fun create(
        preferred: PlayerType,
        config: LiveEngineConfig,
    ): LivePlayerEngine = when (preferred) {
        PlayerType.EXO_PLAYER -> ExoLiveEngine(appContext, config, streamingClient)
        // v1: no MPV live implementation. Fall back to Exo so the user pref
        // never breaks live playback. Revisit when MpvLiveEngine is real.
        PlayerType.MPV, PlayerType.LIBVLC, PlayerType.EXTERNAL ->
            ExoLiveEngine(appContext, config, streamingClient)
    }
}
