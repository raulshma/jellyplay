package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Pins for [ExoErrorTaxonomy] — the ONE Media3 `PlaybackException` code →
 * [EngineError] table. The raw `EXO_ERROR_*` ints are the stable public
 * `androidx.media3.common.PlaybackException.ERROR_CODE_*` values of the media3
 * line the app builds against (verified via `javap -constants` on the
 * resolved media3-common artifact); pinning them here means a typo'd or
 * silently-renumbered constant cannot re-classify a real ExoPlayer error
 * without failing this suite (androidMain itself is not unit-testable).
 */
class ExoErrorTaxonomyTest {

    private val throwable = RuntimeException("underlying")

    @Test
    fun rawCodes_pinTheMedia3Constants() {
        assertEquals(
            listOf(
                2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 3002,
            ),
            listOf(
                ExoErrorTaxonomy.EXO_ERROR_IO_UNSPECIFIED,
                ExoErrorTaxonomy.EXO_ERROR_IO_NETWORK_CONNECTION_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_IO_NETWORK_CONNECTION_TIMEOUT,
                ExoErrorTaxonomy.EXO_ERROR_IO_INVALID_HTTP_CONTENT_TYPE,
                ExoErrorTaxonomy.EXO_ERROR_IO_BAD_HTTP_STATUS,
                ExoErrorTaxonomy.EXO_ERROR_IO_FILE_NOT_FOUND,
                ExoErrorTaxonomy.EXO_ERROR_IO_NO_PERMISSION,
                ExoErrorTaxonomy.EXO_ERROR_IO_CLEARTEXT_NOT_PERMITTED,
                ExoErrorTaxonomy.EXO_ERROR_IO_READ_POSITION_OUT_OF_RANGE,
                ExoErrorTaxonomy.EXO_ERROR_PARSING_MANIFEST_MALFORMED,
            ),
        )
        assertEquals(
            listOf(4001, 4002, 4003, 4004, 4005, 5001, 5002),
            listOf(
                ExoErrorTaxonomy.EXO_ERROR_DECODER_INIT_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_DECODER_QUERY_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_DECODING_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                ExoErrorTaxonomy.EXO_ERROR_DECODING_FORMAT_UNSUPPORTED,
                ExoErrorTaxonomy.EXO_ERROR_AUDIO_TRACK_INIT_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_AUDIO_TRACK_WRITE_FAILED,
            ),
        )
        assertEquals(
            listOf(6000, 6001, 6002, 6004, 6005, 6006, 6007),
            listOf(
                ExoErrorTaxonomy.EXO_ERROR_DRM_UNSPECIFIED,
                ExoErrorTaxonomy.EXO_ERROR_DRM_SCHEME_UNSUPPORTED,
                ExoErrorTaxonomy.EXO_ERROR_DRM_PROVISIONING_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_DRM_LICENSE_ACQUISITION_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_DRM_DISALLOWED_OPERATION,
                ExoErrorTaxonomy.EXO_ERROR_DRM_SYSTEM_ERROR,
                ExoErrorTaxonomy.EXO_ERROR_DRM_DEVICE_REVOKED,
            ),
        )
        assertEquals(
            listOf(7000, 7001),
            listOf(
                ExoErrorTaxonomy.EXO_ERROR_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
                ExoErrorTaxonomy.EXO_ERROR_VIDEO_FRAME_PROCESSING_FAILED,
            ),
        )
    }

    @Test
    fun ioAndManifestFamily_isRetryableNetwork() {
        val family = listOf(
            ExoErrorTaxonomy.EXO_ERROR_IO_UNSPECIFIED,
            ExoErrorTaxonomy.EXO_ERROR_IO_NETWORK_CONNECTION_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_IO_NETWORK_CONNECTION_TIMEOUT,
            ExoErrorTaxonomy.EXO_ERROR_IO_INVALID_HTTP_CONTENT_TYPE,
            ExoErrorTaxonomy.EXO_ERROR_IO_BAD_HTTP_STATUS,
            ExoErrorTaxonomy.EXO_ERROR_IO_FILE_NOT_FOUND,
            ExoErrorTaxonomy.EXO_ERROR_IO_NO_PERMISSION,
            ExoErrorTaxonomy.EXO_ERROR_IO_CLEARTEXT_NOT_PERMITTED,
            ExoErrorTaxonomy.EXO_ERROR_IO_READ_POSITION_OUT_OF_RANGE,
            ExoErrorTaxonomy.EXO_ERROR_PARSING_MANIFEST_MALFORMED,
        )
        family.forEach { code ->
            val error = ExoErrorTaxonomy.fromCode(code, message = null, cause = throwable)
            assertIs<EngineError.Network>(error, "code $code")
            assertSame(throwable, error.cause, "code $code")
            assertTrue(error.retryable, "code $code")
        }
    }

    @Test
    fun decoderFamily_isFatalDecoder() {
        val family = listOf(
            ExoErrorTaxonomy.EXO_ERROR_DECODER_INIT_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_DECODER_QUERY_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_DECODING_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            ExoErrorTaxonomy.EXO_ERROR_DECODING_FORMAT_UNSUPPORTED,
            ExoErrorTaxonomy.EXO_ERROR_AUDIO_TRACK_INIT_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_AUDIO_TRACK_WRITE_FAILED,
        )
        family.forEach { code ->
            val error = ExoErrorTaxonomy.fromCode(code, message = null, cause = throwable)
            assertIs<EngineError.Decoder>(error, "code $code")
            assertNull(error.codec, "code $code")
            assertSame(throwable, error.cause, "code $code")
            assertFalse(error.retryable, "code $code")
        }
    }

    @Test
    fun drmFamily_isFatalDrm() {
        val family = listOf(
            ExoErrorTaxonomy.EXO_ERROR_DRM_UNSPECIFIED,
            ExoErrorTaxonomy.EXO_ERROR_DRM_SCHEME_UNSUPPORTED,
            ExoErrorTaxonomy.EXO_ERROR_DRM_PROVISIONING_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_DRM_LICENSE_ACQUISITION_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_DRM_DISALLOWED_OPERATION,
            ExoErrorTaxonomy.EXO_ERROR_DRM_SYSTEM_ERROR,
            ExoErrorTaxonomy.EXO_ERROR_DRM_DEVICE_REVOKED,
        )
        family.forEach { code ->
            val error = ExoErrorTaxonomy.fromCode(code, message = null, cause = throwable)
            assertIs<EngineError.Drm>(error, "code $code")
            assertNull(error.scheme, "code $code")
            assertSame(throwable, error.cause, "code $code")
            assertFalse(error.retryable, "code $code")
        }
    }

    @Test
    fun drmContentError6003_staysUnknown() {
        // 6003 (DRM_CONTENT_ERROR) was never in the pre-extraction bucket —
        // pin that it did not silently join the DRM arm.
        val error = ExoErrorTaxonomy.fromCode(6003, message = "drm content", cause = null)
        assertIs<EngineError.Unknown>(error)
    }

    @Test
    fun videoFrameProcessingFamily_isRetryableRender() {
        val family = listOf(
            ExoErrorTaxonomy.EXO_ERROR_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
            ExoErrorTaxonomy.EXO_ERROR_VIDEO_FRAME_PROCESSING_FAILED,
        )
        family.forEach { code ->
            val error = ExoErrorTaxonomy.fromCode(code, message = null, cause = throwable)
            assertIs<EngineError.Render>(error, "code $code")
            assertSame(throwable, error.cause, "code $code")
            assertTrue(error.retryable, "code $code")
        }
    }

    @Test
    fun unmappedCode_isUnknownWithMessageAndCause() {
        val error = ExoErrorTaxonomy.fromCode(
            code = 1000, // ERROR_CODE_UNSPECIFIED — never in a bucket
            message = "Source error",
            cause = throwable,
        )
        assertIs<EngineError.Unknown>(error)
        assertEquals("Source error", error.raw)
        assertSame(throwable, error.cause)
        assertFalse(error.retryable)
    }

    @Test
    fun unmappedCode_nullMessage_rendersPlaceholder() {
        val error = ExoErrorTaxonomy.fromCode(code = 4242, message = null, cause = null)
        assertIs<EngineError.Unknown>(error)
        assertEquals("Unknown playback error", error.raw)
        assertNull(error.cause)
    }
}
