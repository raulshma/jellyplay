package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [toEngineError] — the Media3 PlaybackException → EngineError mapping
 * that drives the player UI's Retry / Switch-engine / OK affordances.
 *
 * Previously this mapping was a private function inside ExoPlayerEngine.kt,
 * unreachable for direct testing; promoting it to the shared engine-contract
 * module gives each error-code bucket a direct assertion.
 */
class ExoPlaybackErrorMapperTest {

    private fun exception(code: Int, cause: Throwable? = null) =
        PlaybackException("err", cause, code)

    @Test
    fun `IO network codes map to retryable Network`() {
        listOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        ).forEach { code ->
            val mapped = exception(code).toEngineError()
            assertEquals(code.toString(), EngineError.Network::class, mapped::class)
            assertTrue(code.toString(), mapped.retryable)
        }
    }

    @Test
    fun `decoder codes map to non-retryable Decoder`() {
        listOf(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        ).forEach { code ->
            val mapped = exception(code).toEngineError()
            assertEquals(code.toString(), EngineError.Decoder::class, mapped::class)
            assertFalse(code.toString(), mapped.retryable)
        }
    }

    @Test
    fun `DRM codes map to non-retryable Drm`() {
        listOf(
            PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
            PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
        ).forEach { code ->
            val mapped = exception(code).toEngineError()
            assertEquals(code.toString(), EngineError.Drm::class, mapped::class)
            assertFalse(code.toString(), mapped.retryable)
        }
    }

    @Test
    fun `video frame processor codes map to retryable Render`() {
        listOf(
            PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
            PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
        ).forEach { code ->
            val mapped = exception(code).toEngineError()
            assertEquals(code.toString(), EngineError.Render::class, mapped::class)
            assertTrue(code.toString(), mapped.retryable)
        }
    }

    @Test
    fun `unknown code maps to non-retryable Unknown`() {
        val mapped = exception(PlaybackException.ERROR_CODE_UNSPECIFIED).toEngineError()

        assertEquals(EngineError.Unknown::class, mapped::class)
        assertFalse(mapped.retryable)
    }

    @Test
    fun `cause is propagated`() {
        val cause = RuntimeException("root")
        val mapped = exception(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, cause).toEngineError()

        assertEquals(cause, mapped.cause)
    }
}
