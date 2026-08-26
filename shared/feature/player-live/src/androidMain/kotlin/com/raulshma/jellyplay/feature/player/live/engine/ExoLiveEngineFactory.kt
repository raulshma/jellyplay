package com.raulshma.jellyplay.feature.player.live.engine

import android.content.Context
import okhttp3.OkHttpClient

/**
 * Constructs the live engine, holding the app-scoped dependencies
 * ([appContext], the streaming OkHttpClient) the engine itself shouldn't own.
 *
 * Player-live conveyor: the legacy Hilt-owned `LiveEngineFactory` class
 * became the commonMain `LiveEngineFactory` seam; this androidMain class is
 * its Android implementation (Koin-owned via `androidPlayerLiveModule`,
 * subtitle-tester's context-param pattern). The streaming OkHttpClient is
 * the same `NetworkQualifiers.streamingHttpClient` instance the rest of the
 * app uses (the VOD `PlayerEngineFactory` included).
 *
 * v1 ships a single ExoPlayer-based live engine — every call resolves to
 * [ExoLiveEngine]. Revisit if a real second live adapter lands.
 *
 * Test coverage delta (player-live conveyor): the legacy Robolectric
 * `LiveEngineFactoryTest` (which only asserted `create()` returns a non-null
 * engine) was NOT ported — the AGP 9 KMP library plugin exposes no
 * androidMain unit-test compilation, and this factory is a one-line
 * constructor pass-through whose observable behavior (an ExoLiveEngine
 * driving a real Media3 player) is not meaningfully assertable on the JVM
 * (same delta class as the subtitle-tester conveyor).
 */
class ExoLiveEngineFactory(
    private val appContext: Context,
    private val streamingClient: OkHttpClient,
) : LiveEngineFactory {
    override fun create(
        config: LiveEngineConfig,
        onTranscodeFallbackNeeded: () -> Unit,
    ): LivePlayerEngine = ExoLiveEngine(appContext, config, streamingClient, onTranscodeFallbackNeeded)
}
