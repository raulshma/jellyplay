package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context
import com.raulshma.jellyplay.core.model.PlayerType
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Hilt-singleton [PlayerEngineFactory] (recommendation #7): it is
 * directly constructible (so `@Inject constructor` is valid and unit-testable
 * without a Hilt component), and its `create`/`resetBandwidthMeter` contracts
 * hold. Real-engine construction needs Android, so EXTERNAL (→ [NoOpEngine])
 * is used to exercise the factory without instantiating ExoPlayer/MPV/VLC.
 */
class PlayerEngineFactoryTest {

    private val context: Context = mockk(relaxed = true)
    private val streamingOkHttpClient: OkHttpClient = mockk(relaxed = true)
    private val factory = PlayerEngineFactory(context, streamingOkHttpClient)

    @Test
    fun create_external_returnsNoOpEngine() {
        val engine = factory.create(PlayerType.EXTERNAL)
        assertTrue(engine is MediaEngine)
        assertTrue(engine is NoOpEngine)
        assertTrue(engine.capabilities === EngineCapabilityMatrix.EXTERNAL)
    }

    @Test
    fun resetBandwidthMeter_isCallableAndDoesNotThrow() {
        // Escape hatch retained for test isolation / diagnostics reset.
        factory.resetBandwidthMeter()
    }
}
