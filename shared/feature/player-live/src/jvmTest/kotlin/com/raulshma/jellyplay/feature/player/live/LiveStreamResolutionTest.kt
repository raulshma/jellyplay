package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveStreamOption
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins every arm of [resolveLiveStreamResolution] — the live-stream
 * fallback's capability ladder (direct stream → transcode URL →
 * liveStreamId last resort → no playable method) plus the play-method fold
 * that reflects the URL the ladder BUILT, not the server's verdict (the
 * onPlayerError -> transcode fallback stays eligible). The builder lambda
 * stands in for the ViewModel's `playbackRepository.getStreamUrl` call, so
 * these tests also pin exactly which arguments cross that seam and that the
 * degenerate source never builds a URL at all.
 */
class LiveStreamResolutionTest {

    private var builtUrls = mutableListOf<Pair<String, String?>>()

    private fun builder(): (String, String?) -> String = { mediaSourceId, liveStreamId ->
        builtUrls.add(mediaSourceId to liveStreamId)
        "https://server/Videos/$mediaSourceId/stream"
    }

    private fun source(
        supportsDirectPlay: Boolean = false,
        supportsDirectStream: Boolean = false,
        supportsTranscoding: Boolean = false,
        transcodeUrl: String? = null,
        liveStreamId: String? = null,
    ) = MediaSource(
        id = "src-1",
        name = "Source 1",
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
        transcodeUrl = transcodeUrl,
        liveStreamId = liveStreamId,
    )

    @Test
    fun `direct stream flag resolves via the direct arm`() {
        val resolution = resolveLiveStreamResolution(
            source = source(supportsDirectStream = true, liveStreamId = "live-1"),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.Via.DIRECT_STREAM, (resolution as LiveStreamResolution.Resolved).via)
        assertEquals("https://server/Videos/src-1/stream", resolution.url)
        // Direct-stream URLs still carry the server-issued liveStreamId so
        // the tuner session is opened.
        assertEquals("live-1", resolution.liveStreamId)
        // No supportsDirectPlay flag -> the built URL is a direct stream.
        assertEquals(PlayMethod.DIRECT_STREAM, resolution.playMethod)
        assertEquals(listOf<Pair<String, String?>>("src-1" to "live-1"), builtUrls)
    }

    @Test
    fun `direct play flag wins the play method`() {
        val resolution = resolveLiveStreamResolution(
            source = source(supportsDirectPlay = true),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.Via.DIRECT_STREAM, (resolution as LiveStreamResolution.Resolved).via)
        assertEquals(PlayMethod.DIRECT_PLAY, resolution.playMethod)
        assertNull(resolution.liveStreamId)
    }

    @Test
    fun `transcode flag with transcodeUrl resolves via the transcode arm`() {
        val resolution = resolveLiveStreamResolution(
            source = source(supportsTranscoding = true, transcodeUrl = "/master.m3u8"),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.Via.TRANSCODE_URL, (resolution as LiveStreamResolution.Resolved).via)
        // Even on the transcode arm the URL built is the direct stream URL —
        // the pinned divergence that keeps the transcode fallback eligible.
        assertEquals("https://server/Videos/src-1/stream", resolution.url)
        assertEquals(PlayMethod.DIRECT_STREAM, resolution.playMethod)
    }

    @Test
    fun `transcode flag without a usable transcodeUrl falls through to the liveStreamId arm`() {
        // Blank transcodeUrl falls through exactly like a null one; the
        // liveStreamId then carries the resolution.
        val resolution = resolveLiveStreamResolution(
            source = source(supportsTranscoding = true, transcodeUrl = "  ", liveStreamId = "live-2"),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.Via.LIVE_STREAM_ID, (resolution as LiveStreamResolution.Resolved).via)
        assertEquals("live-2", resolution.liveStreamId)
    }

    @Test
    fun `all-false flags fall back to the liveStreamId arm`() {
        val resolution = resolveLiveStreamResolution(
            source = source(liveStreamId = "tuner-9"),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.Via.LIVE_STREAM_ID, (resolution as LiveStreamResolution.Resolved).via)
        assertEquals("tuner-9", resolution.liveStreamId)
        assertEquals(PlayMethod.DIRECT_STREAM, resolution.playMethod)
    }

    @Test
    fun `degenerate source with no flags and no liveStreamId resolves to no playable method`() {
        val resolution = resolveLiveStreamResolution(
            source = source(liveStreamId = null),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.NoPlayableMethod, resolution)
        // The degenerate source must not build a URL at all.
        assertTrue(builtUrls.isEmpty(), "NoPlayableMethod must not call the URL builder")
    }

    @Test
    fun `blank liveStreamId is no liveStreamId`() {
        val resolution = resolveLiveStreamResolution(
            source = source(liveStreamId = ""),
            buildStreamUrl = builder(),
        )
        assertEquals(LiveStreamResolution.NoPlayableMethod, resolution)
        assertTrue(builtUrls.isEmpty(), "a blank liveStreamId must not build a URL")
    }

    @Test
    fun `probe-override fires only for DIRECT_STREAM option over a transcode verdict`() {
        // The one arm that falls to the ladder despite a non-null
        // resolvePlayback: the user asked for Direct Stream and the server's
        // live-source probe failed, resolving a transcode instead.
        assertTrue(
            shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.TRANSCODE),
        )
        // AUTO/TRANSCODE options accept whatever the server picks.
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.AUTO, PlayMethod.TRANSCODE))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.TRANSCODE, PlayMethod.TRANSCODE))
        // Any non-transcode verdict is trusted regardless of the option.
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.DIRECT_STREAM))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.DIRECT_STREAM, PlayMethod.DIRECT_PLAY))
        assertFalse(shouldIgnoreServerTranscodeVerdict(LiveStreamOption.AUTO, PlayMethod.DIRECT_STREAM))
    }
}
