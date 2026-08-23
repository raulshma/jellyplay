package com.raulshma.jellyplay.feature.music.feedback

/**
 * One-shot error-feedback seam for the music home refresh failure (messages
 * are already-resolved [String]s at the call site). Android's actual is
 * app-provided: it bridges to the app-wide Hilt-owned UserMessageBus, which
 * still lives in the legacy Android-only :core:ui shim until its own conveyor
 * move; desktop registers a no-op (voice-search seam pattern).
 */
interface MusicMessageBus {
    fun error(message: String)
}
