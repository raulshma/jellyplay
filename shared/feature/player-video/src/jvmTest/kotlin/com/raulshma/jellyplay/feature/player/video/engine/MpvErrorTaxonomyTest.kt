package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plain-value pins for [MpvErrorTaxonomy] — the ONE mpv → [EngineError]
 * table both engine adapters (Android's string-code edge, the desktop's
 * int-code edge) re-point onto. The classification arms are the union of the
 * two former hand-mirrored `mapMpvError` tables; the single recorded
 * divergence between them (the Unknown arm's diagnostic text) is preserved
 * by the declared [MpvErrorTaxonomy.fromCode.unknownDetail] parameter and
 * pinned here too.
 */
class MpvErrorTaxonomyTest {

    // ── fromCode: the numeric table ────────────────────────────────────────

    @Test
    fun fromCode_loadingFailed_isRetryableNetwork() {
        val error = MpvErrorTaxonomy.fromCode(MpvErrorTaxonomy.MPV_ERROR_LOADING_FAILED, unknownDetail = "ignored")

        assertIs<EngineError.Network>(error)
        assertTrue(error.retryable)
    }

    @Test
    fun fromCode_initFormatFamily_isFatalDecoder() {
        val family = listOf(
            MpvErrorTaxonomy.MPV_ERROR_AO_INIT_FAILED,
            MpvErrorTaxonomy.MPV_ERROR_VO_INIT_FAILED,
            MpvErrorTaxonomy.MPV_ERROR_NOTHING_TO_PLAY,
            MpvErrorTaxonomy.MPV_ERROR_UNKNOWN_FORMAT,
            MpvErrorTaxonomy.MPV_ERROR_UNSUPPORTED,
            MpvErrorTaxonomy.MPV_ERROR_NOT_IMPLEMENTED,
        )
        // The constants are the stable libmpv ABI — pin the values so a
        // typo'd table cannot silently re-classify a real mpv error.
        assertEquals(listOf(-13, -14, -15, -16, -17, -18, -19), listOf(MpvErrorTaxonomy.MPV_ERROR_LOADING_FAILED) + family)

        family.forEach { code ->
            val error = MpvErrorTaxonomy.fromCode(code, unknownDetail = "ignored")
            assertIs<EngineError.Decoder>(error, "code $code")
            assertNull(error.codec, "code $code")
            assertFalse(error.retryable, "code $code")
        }
    }

    @Test
    fun fromCode_unknownCode_isNonRetryableUnknownRenderingTheDeclaredDetail() {
        listOf(-20, -1, 0, 5, Int.MAX_VALUE, Int.MIN_VALUE).forEach { code ->
            val error = MpvErrorTaxonomy.fromCode(code, unknownDetail = "detail-$code")
            assertIs<EngineError.Unknown>(error, "code $code")
            assertEquals("Playback error (mpv): detail-$code", error.raw, "code $code")
            assertFalse(error.retryable, "code $code")
        }
    }

    // ── fromCodeString: the Android binding's string normalization ────────

    @Test
    fun fromCodeString_nullOrBlank_isUnknownPlaceholder() {
        listOf(null, "", "   ").forEach { code ->
            val error = MpvErrorTaxonomy.fromCodeString(code)
            assertIs<EngineError.Unknown>(error, "code `$code`")
            assertEquals("Playback error (mpv): unknown", error.raw, "code `$code`")
        }
    }

    @Test
    fun fromCodeString_numericStrings_routeThroughTheNumericTable() {
        assertIs<EngineError.Network>(MpvErrorTaxonomy.fromCodeString("-13"))
        assertIs<EngineError.Decoder>(MpvErrorTaxonomy.fromCodeString("-17"))
        // Unknown keeps the ORIGINAL string as its raw text, byte-identical
        // to the pre-extraction Android arm (not the re-rendered int).
        val unknown = MpvErrorTaxonomy.fromCodeString("-20")
        assertIs<EngineError.Unknown>(unknown)
        assertEquals("Playback error (mpv): -20", unknown.raw)
    }

    @Test
    fun fromCodeString_descriptiveNetworkKeywords_areRetryableNetwork() {
        listOf(
            "loading_failed",
            "Network error",
            "connection reset",
            "timeout while reading",
            "unsupported protocol",
            "HTTP 503",
            "stream closed by server",
        ).forEach { code ->
            val error = MpvErrorTaxonomy.fromCodeString(code)
            assertIs<EngineError.Network>(error, "code `$code`")
            assertTrue(error.retryable, "code `$code`")
        }
    }

    @Test
    fun fromCodeString_descriptiveDecoderKeywords_areFatalDecoder() {
        listOf(
            "ao_init_failed",
            "vo_init_failed",
            "unknown format",
            "unsupported codec",
            "decoder error",
            "codec not found",
        ).forEach { code ->
            val error = MpvErrorTaxonomy.fromCodeString(code)
            assertIs<EngineError.Decoder>(error, "code `$code`")
            assertFalse(error.retryable, "code `$code`")
        }
    }

    @Test
    fun fromCodeString_networkKeywordsWinOnKeywordOverlap() {
        // "connection lost: decoder error" carries one keyword from each
        // family — the Network arm is checked first (pre-extraction order).
        assertIs<EngineError.Network>(MpvErrorTaxonomy.fromCodeString("connection lost: decoder error"))
    }

    @Test
    fun fromCodeString_unrecognizedText_isUnknownWithTheRawStringPreserved() {
        val error = MpvErrorTaxonomy.fromCodeString("something completely different")

        assertIs<EngineError.Unknown>(error)
        assertEquals("Playback error (mpv): something completely different", error.raw)
        assertFalse(error.retryable)
    }
}
