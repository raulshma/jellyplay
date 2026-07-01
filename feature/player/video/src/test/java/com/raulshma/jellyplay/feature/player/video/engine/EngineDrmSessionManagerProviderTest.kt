package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pins the DRM extension point on [EngineConfig] (recommendation #5).
 *
 * DRM content isn't shipped yet; these tests guard that the hook exists, stays
 * opt-in (default `null`), and round-trips a supplied provider — so the day DRM
 * lands it plugs in here instead of being hard-coded into [ExoPlayerEngine].
 */
class EngineDrmSessionManagerProviderTest {

    @Test
    fun config_defaultsToNoDrm() {
        assertNull(EngineConfig().drmSessionManagerProvider)
    }

    @Test
    fun provider_canReturnNullForClearContent() {
        val provider = EngineDrmSessionManagerProvider { null }
        assertNull(provider.provide())
    }

    @Test
    fun config_roundTripsProvider() {
        val provider = EngineDrmSessionManagerProvider { null }
        val config = EngineConfig(drmSessionManagerProvider = provider)
        assertSame(provider, config.drmSessionManagerProvider)
        // NoOpEngine/MPV/VLC ignore the field; ExoPlayerEngine consumes it.
        assertNotNull(NoOpEngine().also { it.updateConfig(config) })
    }

    @Test
    fun noOpEngine_ignoresDrmConfigWithoutThrowing() {
        val engine = NoOpEngine()
        // Engines without DRM support must silently ignore the provider.
        engine.updateConfig(EngineConfig(drmSessionManagerProvider = EngineDrmSessionManagerProvider { null }))
        engine.load(PlaybackRequest(uri = "", title = ""))
        // Still idle — DRM config did not engage any playback.
        assertSame(EnginePlaybackState.IDLE, engine.playbackState.value)
    }
}
