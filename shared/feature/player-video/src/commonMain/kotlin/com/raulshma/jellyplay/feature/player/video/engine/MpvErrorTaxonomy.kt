package com.raulshma.jellyplay.feature.player.video.engine

/**
 * ONE pure mapping table from libmpv error codes onto the [EngineError]
 * taxonomy, shared by BOTH mpv adapters — the Android `MpvPlayerEngine`
 * (whose binding surfaces the END_FILE `error` as a string) and the desktop
 * `MpvDesktopEngine` (whose JNA binding surfaces it as an int). The two
 * engines previously carried two hand-mirrored `mapMpvError` tables whose
 * classification agreed on every code; that table now lives exactly once:
 *  - `MPV_ERROR_LOADING_FAILED` (-13) maps to retryable [EngineError.Network]
 *    (transient network drops, HTTP timeouts, server-closed transcodes);
 *  - the decoder/output/format init family (-14 … -19) maps to
 *    [EngineError.Decoder] (not retryable on the same engine);
 *  - anything else maps to [EngineError.Unknown].
 *
 * The ONE divergence between the former tables is confined to the Unknown
 * arm's diagnostic message and is preserved via the declared
 * [fromCode.unknownDetail] parameter: the desktop renders libmpv's
 * `mpv_error_string(code)` text, Android renders the raw string the binding
 * handed over. Classification stays identical either way.
 *
 * Mirrors ExoPlayer's `PlaybackException.toEngineError` split so the UI can
 * offer the right affordance (Retry vs. Switch-engine/Transcode vs. OK).
 * Consumers stay the two engine adapters and the taxonomy test; this is not
 * a stable API surface (the `PlaybackVolumePolicy` precedent).
 */
object MpvErrorTaxonomy {

    // mpv_error codes carried by the END_FILE node's `error` field — the
    // stable libmpv client-API ABI (mpv client.h). A network/source load
    // failure surfaces as LOADING_FAILED; decoder/output/format init failures
    // are fatal on the same engine.
    const val MPV_ERROR_LOADING_FAILED = -13
    const val MPV_ERROR_AO_INIT_FAILED = -14
    const val MPV_ERROR_VO_INIT_FAILED = -15
    const val MPV_ERROR_NOTHING_TO_PLAY = -16
    const val MPV_ERROR_UNKNOWN_FORMAT = -17
    const val MPV_ERROR_UNSUPPORTED = -18
    const val MPV_ERROR_NOT_IMPLEMENTED = -19

    /**
     * Numeric path — the documented END_FILE contract. [unknownDetail] is
     * rendered into the [EngineError.Unknown.raw] message ONLY (the desktop
     * passes `mpv_error_string(code)`; Android's string path passes the raw
     * code string) — a declared parameter so the classification table stays
     * one while each adapter keeps its current diagnostic text.
     */
    fun fromCode(code: Int, unknownDetail: String): EngineError = when (code) {
        // Load / source failures — transient, retryable.
        MPV_ERROR_LOADING_FAILED -> EngineError.Network(cause = null)
        // Decoder / output / format init — fatal on same engine.
        MPV_ERROR_AO_INIT_FAILED,
        MPV_ERROR_VO_INIT_FAILED,
        MPV_ERROR_NOTHING_TO_PLAY,
        MPV_ERROR_UNKNOWN_FORMAT,
        MPV_ERROR_UNSUPPORTED,
        MPV_ERROR_NOT_IMPLEMENTED,
        -> EngineError.Decoder(codec = null, cause = null)
        else -> EngineError.Unknown("Playback error (mpv): $unknownDetail")
    }

    /**
     * String normalization for bindings that surface the END_FILE `error` as
     * a STRING (the Android binding): tolerates both the numeric form
     * ("-13") and a descriptive string (e.g. "loading_failed",
     * "ao_init_failed", or a raw network message). Null / blank map to the
     * unknown placeholder exactly as the pre-extraction Android engine did.
     */
    fun fromCodeString(code: String?): EngineError {
        if (code.isNullOrBlank()) return EngineError.Unknown("Playback error (mpv): unknown")
        // Numeric mpv_error path — the documented END_FILE contract. The
        // original string (not the re-rendered int) stays the Unknown arm's
        // raw text, byte-identical to the pre-extraction engine.
        val numeric = code.toIntOrNull()
        if (numeric != null) return fromCode(numeric, unknownDetail = code)
        // Descriptive-string fallback: some bindings surface the error name or
        // a network message rather than the numeric code. Match keywords so we
        // still recover the retry affordance for transient drops; network
        // keywords win on overlap (checked first — the pre-extraction order).
        val textual = code.lowercase()
        return when {
            "loading_failed" in textual ||
                "network" in textual ||
                "connection" in textual ||
                "timeout" in textual ||
                "protocol" in textual ||
                "http" in textual ||
                "stream" in textual -> EngineError.Network(cause = null)
            "ao_init" in textual ||
                "vo_init" in textual ||
                "format" in textual ||
                "unsupported" in textual ||
                "decoder" in textual ||
                "codec" in textual -> EngineError.Decoder(codec = null, cause = null)
            else -> EngineError.Unknown("Playback error (mpv): $code")
        }
    }
}
