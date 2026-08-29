package com.raulshma.jellyplay.feature.player.video

/**
 * One-shot user-feedback seam for the video player (wave 8C): the exact
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
 * Resource-backed message seal for the one [PlayerVideoMessageBus] call whose
 * text lives in the legacy `core:ui` string table (the resource itself is not
 * duplicated into this module's compose-resources — strings stay
 * byte-identical). The androidMain adapter resolves the entry; the desktop
 * stub drops it.
 */
sealed interface PlayerVideoMessage {

    /** A finished download was auto-removed on the watched threshold. */
    data object SmartDownloadDeleted : PlayerVideoMessage
}
