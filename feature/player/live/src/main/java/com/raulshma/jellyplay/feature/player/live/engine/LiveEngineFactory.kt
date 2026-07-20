package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import com.raulshma.jellyplay.core.model.PlayerType
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Selects the live engine for the user's preferred [PlayerType].
 *
 * v1 only ships an ExoPlayer-based live engine — every [PlayerType] resolves
 * to [ExoLiveEngine]. The previous `when` arms and a stub `MpvLiveEngine`
 * (every override threw) existed only as speculative scaffolding for an MPV
 * live path that was never built; both were removed to stop implying a
 * switch that never happens. Revisit if a real MPV-Live implementation
 * lands — at that point restore the `when` and the engine class together.
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
    ): LivePlayerEngine = ExoLiveEngine(appContext, config, streamingClient)
}
