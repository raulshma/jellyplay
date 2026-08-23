package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.Test

class EngineErrorTest {

    @Test
    fun network_isRetryable() {
        val cause = RuntimeException("timeout")
        val error = EngineError.Network(cause)
        assertTrue(error.retryable)
        assertEquals("Network error", error.message)
        assertSame(cause, error.cause)
    }

    @Test
    fun network_acceptsNullCause() {
        val error = EngineError.Network(null)
        assertTrue(error.retryable)
        assertNull(error.cause)
    }

    @Test
    fun decoder_isNotRetryableAndCarriesCodec() {
        val error = EngineError.Decoder(codec = "hevc", cause = null)
        assertFalse(error.retryable)
        assertEquals("hevc", error.codec)
    }

    @Test
    fun drm_isNotRetryableAndCarriesScheme() {
        val error = EngineError.Drm(scheme = "Widevine", cause = null)
        assertFalse(error.retryable)
        assertEquals("Widevine", error.scheme)
    }

    @Test
    fun source_5xxIsRetryable() {
        val retryableCodes = listOf(500, 502, 503, 504)
        retryableCodes.forEach { code ->
            val error = EngineError.Source(httpStatus = code, cause = null)
            assertTrue(error.retryable, "HTTP $code must be retryable")
        }
    }

    @Test
    fun source_4xxIsNotRetryable() {
        val nonRetryableCodes = listOf(400, 401, 403, 404, 410, 429)
        nonRetryableCodes.forEach { code ->
            val error = EngineError.Source(httpStatus = code, cause = null)
            assertFalse(error.retryable, "HTTP $code must not be retryable")
        }
    }

    @Test
    fun source_nullHttpStatusIsNotRetryable() {
        val error = EngineError.Source(httpStatus = null, cause = null)
        assertFalse(error.retryable)
    }

    @Test
    fun render_isRetryable() {
        val cause = RuntimeException("surface init failed")
        val error = EngineError.Render(cause)
        assertTrue(error.retryable)
        assertSame(cause, error.cause)
    }

    @Test
    fun timeout_isRetryable() {
        val error = EngineError.Timeout()
        assertTrue(error.retryable)
        assertEquals("Playback failed to start. Try a different player engine.", error.message)
    }

    @Test
    fun unknown_isNotRetryableAndCarriesRaw() {
        val error = EngineError.Unknown(raw = "mpv error -7")
        assertFalse(error.retryable)
        assertEquals("mpv error -7", error.raw)
    }

    @Test
    fun unknown_acceptsCause() {
        val cause = IllegalStateException()
        val error = EngineError.Unknown(raw = "boom", cause = cause)
        assertSame(cause, error.cause)
    }

    @Test
    fun exhaustiveness_allSubtypesCovered() {
        // Guards the sealed hierarchy: if a new subtype is added, this test
        // forces the author to decide its retryability explicitly.
        val samples: List<EngineError> = listOf(
            EngineError.Network(null),
            EngineError.Decoder(null, null),
            EngineError.Drm(null, null),
            EngineError.Source(null, null),
            EngineError.Render(null),
            EngineError.Unknown(""),
        )
        assertEquals(6, samples.distinctBy { it::class }.size)
    }
}
