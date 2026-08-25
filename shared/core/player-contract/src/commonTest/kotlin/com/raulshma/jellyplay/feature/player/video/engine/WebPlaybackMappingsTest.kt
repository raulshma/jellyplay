package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Pins every row of the [WebPlaybackMappings] table — the pure half of
 * `HtmlVideoEngine`. Runs via jvmTest (the wasmJs browser lane is opt-in
 * repo-wide), which is exactly why the mapping lives in commonMain with no
 * DOM types.
 */
class WebPlaybackMappingsTest {

    // ── Event → EnginePlaybackState ────────────────────────────────────────

    @Test
    fun stateEvent_fetchAndStallMapToBuffering() {
        assertEquals(
            EnginePlaybackState.BUFFERING,
            WebPlaybackMappings.playbackStateForEvent(WebPlaybackMappings.EVENT_LOADSTART),
            "loadstart must map to BUFFERING",
        )
        assertEquals(
            EnginePlaybackState.BUFFERING,
            WebPlaybackMappings.playbackStateForEvent(WebPlaybackMappings.EVENT_WAITING),
            "waiting must map to BUFFERING",
        )
    }

    @Test
    fun stateEvent_renderableDataMapsToReady() {
        listOf(
            WebPlaybackMappings.EVENT_LOADEDDATA,
            WebPlaybackMappings.EVENT_CANPLAY,
            WebPlaybackMappings.EVENT_CANPLAYTHROUGH,
            WebPlaybackMappings.EVENT_PLAYING,
        ).forEach { event ->
            assertEquals(
                EnginePlaybackState.READY,
                WebPlaybackMappings.playbackStateForEvent(event),
                "$event must map to READY",
            )
        }
    }

    @Test
    fun stateEvent_endedAndErrorMapToTerminalStates() {
        assertEquals(
            EnginePlaybackState.ENDED,
            WebPlaybackMappings.playbackStateForEvent(WebPlaybackMappings.EVENT_ENDED),
            "ended must map to ENDED",
        )
        assertEquals(
            EnginePlaybackState.ERROR,
            WebPlaybackMappings.playbackStateForEvent(WebPlaybackMappings.EVENT_ERROR),
            "error must map to ERROR",
        )
    }

    @Test
    fun stateEvent_scalarAndTransportEventsCarryNoState() {
        listOf(
            WebPlaybackMappings.EVENT_PLAY,
            WebPlaybackMappings.EVENT_PAUSE,
            WebPlaybackMappings.EVENT_TIMEUPDATE,
            WebPlaybackMappings.EVENT_PROGRESS,
            WebPlaybackMappings.EVENT_DURATIONCHANGE,
            WebPlaybackMappings.EVENT_RATECHANGE,
            WebPlaybackMappings.EVENT_VOLUMECHANGE,
            WebPlaybackMappings.EVENT_LOADEDMETADATA,
            "seeking",
            "seeked",
            "stalled",
            "emptied",
            "suspend",
        ).forEach { event ->
            assertNull(
                WebPlaybackMappings.playbackStateForEvent(event),
                "$event must not map to a playback state",
            )
        }
    }

    // ── MediaError.code → EngineError ──────────────────────────────────────

    @Test
    fun errorMapping_networkCodeBecomesRetryableNetwork() {
        val error = WebPlaybackMappings.engineErrorForMediaErrorCode(WebPlaybackMappings.MEDIA_ERR_NETWORK)
        assertTrue(error is EngineError.Network, "MEDIA_ERR_NETWORK must map to Network")
        assertTrue(error.retryable, "Network must stay retryable")
    }

    @Test
    fun errorMapping_decodeAndUnsupportedCodecBecomeDecoder() {
        val decode = WebPlaybackMappings.engineErrorForMediaErrorCode(WebPlaybackMappings.MEDIA_ERR_DECODE)
        assertTrue(decode is EngineError.Decoder, "MEDIA_ERR_DECODE must map to Decoder")
        assertFalse(decode.retryable, "Decoder must not be retryable")

        val unsupported =
            WebPlaybackMappings.engineErrorForMediaErrorCode(WebPlaybackMappings.MEDIA_ERR_SRC_NOT_SUPPORTED)
        assertTrue(unsupported is EngineError.Decoder, "MEDIA_ERR_SRC_NOT_SUPPORTED must map to Decoder")
        assertFalse(unsupported.retryable, "Decoder must not be retryable")
    }

    @Test
    fun errorMapping_abortedFallsBackToUnknownWithDefaultRaw() {
        val error = WebPlaybackMappings.engineErrorForMediaErrorCode(WebPlaybackMappings.MEDIA_ERR_ABORTED)
        assertTrue(error is EngineError.Unknown, "MEDIA_ERR_ABORTED must map to Unknown")
        assertEquals(
            "Playback aborted (MEDIA_ERR_ABORTED)",
            error.message,
            "aborted without rawMessage must use the default raw string",
        )
        val withRaw = WebPlaybackMappings.engineErrorForMediaErrorCode(
            WebPlaybackMappings.MEDIA_ERR_ABORTED,
            rawMessage = "The play() request was interrupted",
        )
        assertEquals(
            "The play() request was interrupted",
            withRaw.message,
            "rawMessage must be carried into Unknown",
        )
    }

    @Test
    fun errorMapping_unknownCodeBecomesUnknownWithCodeInMessage() {
        val error = WebPlaybackMappings.engineErrorForMediaErrorCode(0)
        assertTrue(error is EngineError.Unknown, "code 0 must map to Unknown")
        assertEquals(
            "Playback error (media code 0)",
            error.message,
            "unknown codes must surface their numeric code",
        )
        assertFalse(error.retryable, "Unknown must not be retryable")
    }

    // ── seconds → ms ────────────────────────────────────────────────────────

    @Test
    fun secondsToMs_convertsAndTruncates() {
        assertEquals(90_500L, WebPlaybackMappings.secondsToMs(90.5), "90.5s must become 90500ms")
        assertEquals(1L, WebPlaybackMappings.secondsToMs(0.0019), "sub-millisecond values truncate to 1ms")
    }

    @Test
    fun secondsToMs_nanInfinityAndNonPositiveFoldToZero() {
        assertEquals(0L, WebPlaybackMappings.secondsToMs(Double.NaN), "NaN (pre-metadata duration) must fold to 0")
        assertEquals(0L, WebPlaybackMappings.secondsToMs(Double.POSITIVE_INFINITY), "+Infinity must fold to 0")
        assertEquals(0L, WebPlaybackMappings.secondsToMs(Double.NEGATIVE_INFINITY), "-Infinity must fold to 0")
        assertEquals(0L, WebPlaybackMappings.secondsToMs(0.0), "0s must fold to 0")
        assertEquals(0L, WebPlaybackMappings.secondsToMs(-12.0), "negative positions must fold to 0")
    }

    // ── seek clamping ───────────────────────────────────────────────────────

    @Test
    fun clampSeek_inRangePassthrough() {
        assertEquals(42_000L, WebPlaybackMappings.clampSeekMs(42_000L, 90_000L), "in-range seeks pass through")
        assertEquals(0L, WebPlaybackMappings.clampSeekMs(0L, 90_000L), "seek to start passes through")
    }

    @Test
    fun clampSeek_clampsToDurationWhenKnown() {
        assertEquals(90_000L, WebPlaybackMappings.clampSeekMs(120_000L, 90_000L), "seeks past duration clamp to duration")
        assertEquals(0L, WebPlaybackMappings.clampSeekMs(-5_000L, 90_000L), "negative seeks clamp to 0")
    }

    @Test
    fun clampSeek_unknownDurationOnlyFloorsAtZero() {
        assertEquals(120_000L, WebPlaybackMappings.clampSeekMs(120_000L, 0L), "no duration → no ceiling")
        assertEquals(0L, WebPlaybackMappings.clampSeekMs(-1L, 0L), "no duration → still floored at 0")
    }

    // ── buffered tail ───────────────────────────────────────────────────────

    @Test
    fun bufferedTail_emptyRangesYieldZero() {
        assertEquals(0L, WebPlaybackMappings.bufferedTailMs(emptyList(), 90_000L), "no ranges → 0")
    }

    @Test
    fun bufferedTail_takesLargestRangeEnd() {
        val ranges = listOf(30_000L, 61_000L, 45_000L)
        assertEquals(
            61_000L,
            WebPlaybackMappings.bufferedTailMs(ranges, 90_000L),
            "tail must be the maximum range end, not the last",
        )
    }

    @Test
    fun bufferedTail_clampsToDurationAndFloorsAtZero() {
        assertEquals(
            90_000L,
            WebPlaybackMappings.bufferedTailMs(listOf(120_000L), 90_000L),
            "stale ranges beyond a shorter duration clamp to duration",
        )
        assertEquals(
            120_000L,
            WebPlaybackMappings.bufferedTailMs(listOf(120_000L), 0L),
            "unknown duration → no clamp ceiling",
        )
        assertEquals(
            0L,
            WebPlaybackMappings.bufferedTailMs(listOf(-5L), 90_000L),
            "negative range ends floor to 0",
        )
    }

    // ── volume clamp ────────────────────────────────────────────────────────

    @Test
    fun clampVolume_passesThroughContractRange() {
        assertEquals(0f, WebPlaybackMappings.clampVolume(0f), "0 must pass through")
        assertEquals(0.5f, WebPlaybackMappings.clampVolume(0.5f), "mid volume must pass through")
        assertEquals(1f, WebPlaybackMappings.clampVolume(1f), "1 must pass through")
    }

    @Test
    fun clampVolume_clampsOutsideContractRange() {
        assertEquals(0f, WebPlaybackMappings.clampVolume(-0.3f), "below-range clamps to 0")
        assertEquals(1f, WebPlaybackMappings.clampVolume(1.7f), "above-range clamps to 1")
    }

    // ── resolution label ────────────────────────────────────────────────────

    @Test
    fun resolutionLabel_formatsPositiveDimensions() {
        assertEquals("1920x1080", WebPlaybackMappings.resolutionLabel(1920, 1080), "WxH format")
        assertEquals("3840x2160", WebPlaybackMappings.resolutionLabel(3840, 2160), "WxH format (4K)")
    }

    @Test
    fun resolutionLabel_nullBeforeFirstFrame() {
        assertNull(WebPlaybackMappings.resolutionLabel(0, 0), "pre-frame 0x0 must be null")
        assertNull(WebPlaybackMappings.resolutionLabel(1920, 0), "half-known dimensions must be null")
        assertNull(WebPlaybackMappings.resolutionLabel(0, 1080), "half-known dimensions must be null")
        assertNull(WebPlaybackMappings.resolutionLabel(-1, -1), "invalid dimensions must be null")
    }

    // ── WebVTT track eligibility ────────────────────────────────────────────

    @Test
    fun isWebVttTrack_acceptsVttMimeUrlAndCodec() {
        assertTrue(
            WebPlaybackMappings.isWebVttTrack("text/vtt", "https://s/x", null),
            "text/vtt MIME type qualifies",
        )
        assertTrue(
            WebPlaybackMappings.isWebVttTrack(null, "https://s/Videos/i/1/Subtitles/2/Stream.vtt?api_key=k", null),
            ".vtt URL extension (with query string) qualifies",
        )
        assertTrue(
            WebPlaybackMappings.isWebVttTrack(null, "https://s/x", "vtt"),
            "vtt codec qualifies",
        )
    }

    @Test
    fun isWebVttTrack_rejectsNonVttSources() {
        assertFalse(
            WebPlaybackMappings.isWebVttTrack("application/x-subrip", "https://s/x", "srt"),
            "SRT sources must be rejected",
        )
        assertFalse(
            WebPlaybackMappings.isWebVttTrack(null, "https://s/Videos/i/1/Subtitles/2/Stream.ass?api_key=k", "ass"),
            "ASS sources must be rejected",
        )
        assertFalse(
            WebPlaybackMappings.isWebVttTrack(null, "https://s/subtitles.vtt.d/other", null),
            "extension not at path end must be rejected",
        )
        assertFalse(
            WebPlaybackMappings.isWebVttTrack(null, "https://s/x", null),
            "no signal anywhere must be rejected",
        )
    }
}
