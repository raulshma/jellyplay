package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.Test

/**
 * Pins [VlcErrorMapper]'s contract at the libVLC → [EngineError] boundary.
 * Until libVLC exposes structured codes, every error maps to
 * [EngineError.Unknown] — the invariant that matters is that the engine's RAW
 * text is preserved verbatim (it is the only diagnostic surface the user and
 * the logs have) and that a `null` throwable message falls back to a generic
 * line instead of rendering the string "null".
 */
class VlcErrorMapperTest {

    @Test
    fun fromMessage_wrapsRawTextInUnknown() {
        val error = VlcErrorMapper.fromMessage("core input error: local file 1 failed")

        assertIs<EngineError.Unknown>(error)
        assertEquals("core input error: local file 1 failed", error.message)
        assertEquals("core input error: local file 1 failed", error.raw)
    }

    @Test
    fun fromMessage_unknownIsNotRetryable() {
        assertFalse(VlcErrorMapper.fromMessage("boom").retryable)
    }

    @Test
    fun fromMessage_withoutCause_leavesCauseNull() {
        assertNull(VlcErrorMapper.fromMessage("boom").cause)
    }

    @Test
    fun fromMessage_attachesCause_verbatim() {
        val cause = IllegalStateException("vlc died")
        val error = VlcErrorMapper.fromMessage("boom", cause)

        assertSame(cause, error.cause)
    }

    @Test
    fun fromThrowable_usesThrowableMessageAsRawText() {
        val cause = java.io.IOException("connection reset by peer")

        val error = VlcErrorMapper.fromThrowable(cause)

        assertIs<EngineError.Unknown>(error)
        assertEquals("connection reset by peer", error.message)
        assertSame(cause, error.cause)
        assertFalse(error.retryable)
    }

    @Test
    fun fromThrowable_nullMessage_fallsBackToGenericText() {
        val cause = RuntimeException() // message == null

        val error = VlcErrorMapper.fromThrowable(cause)

        assertIs<EngineError.Unknown>(error)
        assertEquals("VLC encountered an error during playback", error.message)
        assertSame(cause, error.cause)
    }
}
