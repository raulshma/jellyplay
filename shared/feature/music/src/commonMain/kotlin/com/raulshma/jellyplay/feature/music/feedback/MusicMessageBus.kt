package com.raulshma.jellyplay.feature.music.feedback

/**
 * One-shot error-feedback seam for the music home refresh failure (messages
 * are already-resolved [String]s at the call site). Android's actual is
 * app-provided: it bridges to the app-wide Koin-owned UserMessageBus, which
 * lives in :shared:core:ui since the cutover dissolved the legacy
 * core:ui module; desktop registers a buffering relay the shell's snackbar host
 * collects (DesktopMusicMessageBus).
 */
interface MusicMessageBus {
    fun error(message: String)
}
