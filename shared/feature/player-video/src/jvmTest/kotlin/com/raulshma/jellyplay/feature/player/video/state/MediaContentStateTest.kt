package com.raulshma.jellyplay.feature.player.video.state

import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * Pins the HDR / frame-rate derivation on [MediaContentState].
 *
 * The `VideoPlayerUiState.hdrType` / `videoFrameRate` accessors delegate to
 * this slice (`get() = media.hdrType`), so this is the single derivation
 * home; the pin keeps the stream-scanning contract (first VIDEO stream,
 * SDR-vs-HDR folding) from silently changing under the delegated readers.
 */
class MediaContentStateTest {

    @Test
    fun hdrType_nullWhenNoVideoStream() {
        val state = MediaContentState(mediaStreams = emptyList())
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_nullWhenVideoRangeIsSdr() {
        val state = MediaContentState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "SDR")),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_nullWhenVideoRangeAbsent() {
        val state = MediaContentState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = null)),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_returnsRangeForHdr10() {
        val state = MediaContentState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "HDR10")),
        )
        assertEquals(state.hdrType, "HDR10")
    }

    @Test
    fun hdrType_caseInsensitiveSdrReturnsNull() {
        val state = MediaContentState(
            mediaStreams = listOf(MediaStream(index = 0, type = StreamType.VIDEO, videoRange = "sdr")),
        )
        assertNull(state.hdrType)
    }

    @Test
    fun hdrType_usesFirstVideoStream() {
        val state = MediaContentState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.AUDIO),
                MediaStream(index = 1, type = StreamType.VIDEO, videoRange = "HLG"),
                MediaStream(index = 2, type = StreamType.VIDEO, videoRange = "SDR"),
            ),
        )
        assertEquals(state.hdrType, "HLG")
    }

    @Test
    fun videoFrameRate_nullWhenNoVideoStream() {
        val state = MediaContentState(mediaStreams = emptyList())
        assertNull(state.videoFrameRate)
    }

    @Test
    fun videoFrameRate_returnsFirstVideoStreamRate() {
        val state = MediaContentState(
            mediaStreams = listOf(
                MediaStream(index = 0, type = StreamType.VIDEO, realFrameRate = 23.976f),
                MediaStream(index = 1, type = StreamType.VIDEO, realFrameRate = 60f),
            ),
        )
        assertEquals(23.976f, state.videoFrameRate!!, 0.0001f)
    }
}
