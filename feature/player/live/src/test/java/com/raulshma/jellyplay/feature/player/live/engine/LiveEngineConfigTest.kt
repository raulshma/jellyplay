package com.raulshma.jellyplay.feature.player.live.engine

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveEngineConfigTest {

    @Test
    fun `default config uses tight live buffer sizes`() {
        val config = LiveEngineConfig()
        assertEquals(10_000, config.minBufferMs)
        assertEquals(30_000, config.maxBufferMs)
        assertEquals(5_000, config.rebufferMs)
        assertTrue("min must be less than max", config.minBufferMs < config.maxBufferMs)
    }

    @Test
    fun `default request mime is HLS`() {
        val request = LivePlaybackRequest(url = "https://example/Videos/x/stream", title = "x")
        assertEquals(MimeTypes.APPLICATION_M3U8, request.mimeType)
    }

    @Test
    fun `default play method is direct stream`() {
        val request = LivePlaybackRequest(url = "u", title = "t")
        assertEquals(LivePlayMethod.DIRECT_STREAM, request.playMethod)
    }
}
