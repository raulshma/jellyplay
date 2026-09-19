package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.PlayMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [selectPlaybackMethod] — the play-method selection ladder moved
 * verbatim out of [PlaybackRepositoryImpl.resolvePlayback] — so every branch
 * (live / direct-play / direct-stream / transcode, and the no-playable-method
 * null) plus the URL-source pairing each method forces can never drift
 * silently. The facade tests (PlaybackRepositoryImplTest) pin the end-to-end
 * URL choreography; what is pinned HERE is the decision table itself,
 * including the branches the facade suite never exercises.
 */
class PlaybackMethodSelectionTest {

    private fun source(
        liveStreamId: String? = null,
        requiresOpening: Boolean = false,
        supportsDirectPlay: Boolean = false,
        supportsDirectStream: Boolean = false,
        supportsTranscoding: Boolean = false,
    ) = MediaSource(
        id = "source-1",
        name = "main",
        liveStreamId = liveStreamId,
        requiresOpening = requiresOpening,
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
    )

    // ── non-live ladder: precedence direct play > direct stream > transcode ──

    @Test
    fun `non-live with everything supported picks Direct Play over the static stream URL`() {
        val selection = selectPlaybackMethod(
            source(supportsDirectPlay = true, supportsDirectStream = true, supportsTranscoding = true),
        )

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_PLAY, PlaybackUrlSource.STATIC_STREAM), selection)
    }

    @Test
    fun `non-live direct stream resolves through the transcode URL`() {
        // The SDK surfaces no DirectStreamUrl, so the direct-stream URL IS
        // the server-baked transcode path.
        val selection = selectPlaybackMethod(source(supportsDirectStream = true, supportsTranscoding = true))

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.TRANSCODE), selection)
    }

    @Test
    fun `non-live with only transcoding supported transcodes`() {
        val selection = selectPlaybackMethod(source(supportsTranscoding = true))

        assertEquals(PlaybackMethodSelection(PlayMethod.TRANSCODE, PlaybackUrlSource.TRANSCODE), selection)
    }

    @Test
    fun `no playable method offered yields null`() {
        assertNull(selectPlaybackMethod(source()))
    }

    // ── live ladder ──────────────────────────────────────────────────────

    @Test
    fun `live with direct stream supported reports Direct Stream on the live stream URL`() {
        val selection = selectPlaybackMethod(
            source(liveStreamId = "live-1", supportsDirectStream = true, supportsTranscoding = true),
        )

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.LIVE_STREAM), selection)
    }

    @Test
    fun `live with direct play but no direct stream uses the live URL yet reports Transcode when possible`() {
        // Declared divergence: direct play makes the LIVE_STREAM URL eligible
        // (base path) but never wins the reported method — the method ladder
        // reads only directStream/transcoding.
        val selection = selectPlaybackMethod(
            source(liveStreamId = "live-1", supportsDirectPlay = true, supportsTranscoding = true),
        )

        assertEquals(PlaybackMethodSelection(PlayMethod.TRANSCODE, PlaybackUrlSource.LIVE_STREAM), selection)
    }

    @Test
    fun `live with only direct play still claims Direct Stream on the live URL`() {
        // The historical fallback arm: a live source offering nothing else
        // reports DIRECT_STREAM while riding the client-built live URL.
        val selection = selectPlaybackMethod(source(liveStreamId = "live-1", supportsDirectPlay = true))

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.LIVE_STREAM), selection)
    }

    @Test
    fun `live with no direct method falls back to the transcode path`() {
        val selection = selectPlaybackMethod(source(liveStreamId = "live-1", supportsTranscoding = true))

        assertEquals(PlaybackMethodSelection(PlayMethod.TRANSCODE, PlaybackUrlSource.TRANSCODE), selection)
    }

    @Test
    fun `live offering nothing claims Direct Stream over the transcode URL`() {
        // Totality edge of the live branch: not even transcode is offered,
        // yet the source is live — the method falls back to DIRECT_STREAM
        // while the only URL available is the transcode path.
        val selection = selectPlaybackMethod(source(liveStreamId = "live-1"))

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.TRANSCODE), selection)
    }

    @Test
    fun `requiresOpening alone routes the source through the live branch`() {
        // Live TV without a pre-issued liveStreamId: the server just demands
        // the stream be opened — same live routing.
        val selection = selectPlaybackMethod(source(requiresOpening = true, supportsDirectStream = true))

        assertEquals(PlaybackMethodSelection(PlayMethod.DIRECT_STREAM, PlaybackUrlSource.LIVE_STREAM), selection)
    }
}
