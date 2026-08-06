package com.raulshma.jellyplay.feature.player.video.engine

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [MediaEngineContractTest] specimen for [ExoPlayerEngine].
 *
 * The constructor is pure DI (no native init; the player is built lazily in
 * `load`), so the engine constructs cleanly under Robolectric. This phase runs
 * only the Level-0 invariants — actually building/feeding a media3 `ExoPlayer`
 * under Robolectric is out of scope. The matrix identity + displayName checks
 * stay active and lock the EXO_PLAYER projection.
 */
@RunWith(RobolectricTestRunner::class)
class ExoPlayerEngineContractTest : MediaEngineContractTest() {

    override fun createEngine(): MediaEngine =
        ExoPlayerEngine(
            context = ApplicationProvider.getApplicationContext(),
            streamingOkHttpClient = mockk<OkHttpClient>(),
            bandwidthMeter = null,
            fontProvider = FontProvider(ApplicationProvider.getApplicationContext()),
        )

    override fun expectedCapabilityMatrix(): EngineCapabilities = EngineCapabilityMatrix.EXO_PLAYER
    override fun expectedDisplayName(): String = "ExoPlayer"

    // createSurfaceView inflates a media3 PlayerView, which needs its layout
    // resource resolved — unavailable under Robolectric (Resources$NotFound).
    // The View-returning Level-0 test therefore skips for this specimen.
    override fun supportsViewCreation(): Boolean = false
}
