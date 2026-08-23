package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.type.AssRenderType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [AssClockPumpRenderersFactory] — the render clock feeding libass.
 *
 * Regression: the renderer-position `positionUs` passed to render() carries a
 * constant +10¹² µs base on Jellyfin HLS/TS transcodes (observed
 * `1_000_860_429_000` at an 860 s resume point), and the player's own position
 * cannot be queried from the playback thread. The pump therefore anchors at
 * the load's start position and integrates renderer-position DELTAS, which are
 * base-independent — the published clock must track the media timeline no
 * matter what base the stream uses.
 */
class AssClockPumpRenderersFactoryTest {

    @Test
    fun factory_appendsClockPumpRenderer() {
        val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        val factory = AssClockPumpRenderersFactory(handler, emptyBaseFactory(), startMediaTimeUs = 0L)

        val renderers = factory.createRenderers(
            plainHandler(), noOpVideoListener(), noOpAudioListener(),
            noOpTextOutput(), noOpMetadataOutput(),
        )

        assertEquals(1, renderers.size)
        assertEquals("AssClockPumpRenderer", renderers[0].name)
    }

    @Test
    fun pump_anchorsAtStartPosition_ignoresRendererPositionBase() {
        val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        // Resumed at ~14 min 20 s; renderer positions carry a +1e12 µs base.
        val renderers = AssClockPumpRenderersFactory(handler, emptyBaseFactory(), startMediaTimeUs = 860_429_000L)
            .createRenderers(
                plainHandler(), noOpVideoListener(), noOpAudioListener(),
                noOpTextOutput(), noOpMetadataOutput(),
            )

        renderers[0].render(1_000_860_429_000L, 0L)
        assertEquals(860_429_000L, handler.videoTime)

        // 40 ms of playback → +40_000 µs on the media clock.
        renderers[0].render(1_000_860_469_000L, 0L)
        assertEquals(860_469_000L, handler.videoTime)
    }

    @Test
    fun pump_backwardSeek_tracksBackward() {
        val handler = AssHandler(AssRenderType.OVERLAY_OPEN_GL)
        val renderers = AssClockPumpRenderersFactory(handler, emptyBaseFactory(), startMediaTimeUs = 0L)
            .createRenderers(
                plainHandler(), noOpVideoListener(), noOpAudioListener(),
                noOpTextOutput(), noOpMetadataOutput(),
            )

        renderers[0].render(1_000_100_000L, 0L) // anchor tick, base unknown
        renderers[0].render(1_000_160_000L, 0L) // +60 ms
        assertEquals(60_000L, handler.videoTime)

        // Seek back 40 ms → renderer position drops, media clock follows.
        renderers[0].render(1_000_120_000L, 0L)
        assertEquals(20_000L, handler.videoTime)
    }

    private fun emptyBaseFactory(): RenderersFactory = RenderersFactory { _, _, _, _, _ ->
        emptyArray<Renderer>()
    }

    private fun plainHandler(): android.os.Handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun noOpVideoListener(): VideoRendererEventListener = object : VideoRendererEventListener {}

    private fun noOpAudioListener(): AudioRendererEventListener = object : AudioRendererEventListener {}

    private fun noOpTextOutput(): TextOutput = TextOutput { }

    private fun noOpMetadataOutput(): MetadataOutput = MetadataOutput { }
}
