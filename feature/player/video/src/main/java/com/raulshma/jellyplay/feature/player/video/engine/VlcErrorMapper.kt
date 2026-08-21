package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Shared error mapping for libVLC. Currently all libVLC errors surface as
 * [EngineError.Unknown] — libVLC 3.7.x does not expose structured codes that
 * map cleanly to [EngineError.Network]/[EngineError.Decoder] without JNI
 * parsing. This object is the single home so that (a) the mapping lives next
 * to [ExoPlaybackErrorMapper], (b) the contract can inject errors uniformly,
 * and (c) a future table (once VLC codes are enumerated) lands in one file.
 */
object VlcErrorMapper {
    fun fromMessage(raw: String, cause: Throwable? = null): EngineError =
        EngineError.Unknown(raw, cause)

    fun fromThrowable(cause: Throwable): EngineError =
        fromMessage(cause.message ?: "VLC encountered an error during playback", cause)
}
