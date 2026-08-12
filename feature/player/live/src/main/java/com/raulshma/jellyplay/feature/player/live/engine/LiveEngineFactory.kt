package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Constructs the live engine, injecting the app-scoped dependencies
 * ([appContext], the streaming OkHttpClient) the engine itself shouldn't own
 * and the transcode-fallback callback the ViewModel supplies.
 *
 * v1 ships a single ExoPlayer-based live engine — every call resolves to
 * [ExoLiveEngine]. The previous `preferred: PlayerType` parameter and the
 * speculative `when` arms / stub `MpvLiveEngine` existed only to imply a switch
 * that never happens; they were removed. Revisit if a real second live adapter
 * lands — at that point restore the dispatch (and the [LivePlayerEngine] seam)
 * together, not before.
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
        config: LiveEngineConfig,
        onTranscodeFallbackNeeded: () -> Unit,
    ): LivePlayerEngine = ExoLiveEngine(appContext, config, streamingClient, onTranscodeFallbackNeeded)
}
