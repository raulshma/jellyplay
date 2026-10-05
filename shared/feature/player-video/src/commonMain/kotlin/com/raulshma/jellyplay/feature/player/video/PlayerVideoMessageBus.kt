package com.raulshma.jellyplay.feature.player.video

/**
 * One-shot user-feedback seam for the video player: the exact
 * member set [VideoPlayerViewModel] and [SubtitleManager] call on the legacy
 * Android-only `UserMessageBus`. Messages are already-resolved [String]s at
 * the call sites; resource-backed messages go through the
 * [PlayerVideoMessage] seal so no legacy `UiText`/`R` machinery leaks into
 * common code (LiveTvUserMessage precedent, livetv conveyor).
 *
 * The androidMain adapter bridges the app-wide Hilt-owned legacy
 * `UserMessageBus`; the jvmMain actual still drops messages (no desktop host
 * renders them yet — the music seam's relay, DesktopMusicMessageBus, shows
 * the shape a future host would collect).
 */
interface PlayerVideoMessageBus {

    /** Informational, non-blocking feedback (dynamic/server-supplied text). */
    fun info(message: String)

    /** Recoverable error feedback (dynamic/server-supplied text). */
    fun error(message: String)

    /** Informational feedback backed by a localizable resource message. */
    fun info(message: PlayerVideoMessage)
}

/**
 * Resource-backed message seal for the [PlayerVideoMessageBus] calls whose
 * text lives in a string table (the SmartDownloadDeleted entry reuses a
 * same-name entry in the shared `core:ui` table; the session-notice entries
 * live in this module's compose-resources — strings stay byte-identical to
 * the former hardcoded literals). The androidMain adapter resolves the
 * entries; the desktop stub drops them.
 */
sealed interface PlayerVideoMessage {

    /** A finished download was auto-removed on the watched threshold. */
    data object SmartDownloadDeleted : PlayerVideoMessage

    /** A playback-mode/quality reload resolved to a transcode (brief re-buffer). */
    data object TranscodeSwitched : PlayerVideoMessage

    /** A forced-direct-play reload found no playable direct method — falling back to transcode. */
    data object DirectPlayUnavailable : PlayerVideoMessage

    /** The VLC engine was selected while a TLS client certificate is active (documented-unsupported there). */
    data object VlcClientCertUnsupported : PlayerVideoMessage
}
