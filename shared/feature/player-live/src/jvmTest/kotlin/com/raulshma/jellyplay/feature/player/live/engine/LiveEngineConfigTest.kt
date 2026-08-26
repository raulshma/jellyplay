package com.raulshma.jellyplay.feature.player.live.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveEngineConfigTest {

    @Test
    fun `default config uses tight live buffer sizes`() {
        val config = LiveEngineConfig()
        assertEquals(10_000, config.minBufferMs)
        assertEquals(30_000, config.maxBufferMs)
        assertEquals(5_000, config.rebufferMs)
        assertTrue(config.minBufferMs < config.maxBufferMs, "min must be less than max")
    }

    @Test
    fun `default play method is direct stream`() {
        val request = LivePlaybackRequest(url = "u", title = "t")
        assertEquals(LivePlayMethod.DIRECT_STREAM, request.playMethod)
    }
}
