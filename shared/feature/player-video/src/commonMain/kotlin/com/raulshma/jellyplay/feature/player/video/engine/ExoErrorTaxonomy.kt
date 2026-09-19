package com.raulshma.jellyplay.feature.player.video.engine

/**
 * ONE pure mapping table from Media3 `PlaybackException` error codes onto the
 * [EngineError] taxonomy — the `MpvErrorTaxonomy` pattern applied to the Exo
 * side. The table previously lived only inside androidMain's
 * `ExoPlaybackErrorMapper`; it now lives here (commonMain, over plain `Int`
 * codes) so it is jvmTest-pinnable, and the androidMain mapper is a thin
 * extension that reads `PlaybackException.errorCode` and delegates.
 *
 * Media3 constants stay OUT of commonMain: each `EXO_ERROR_*` below is the
 * raw `Int` of the named `androidx.media3.common.PlaybackException`
 * `ERROR_CODE_*` constant (the stable public error-code ints of the media3
 * line the app builds against, verified via `javap -constants` — pinned by
 * [ExoErrorTaxonomyTest] so a typo'd table cannot silently re-classify a real
 * ExoPlayer error).
 *
 * Bucketing (drives the UI affordance — Retry vs Switch-engine vs OK):
 *   - IO / network / manifest → [EngineError.Network] (retryable)
 *   - decoder / codec         → [EngineError.Decoder] (switch engine)
 *   - DRM                     → [EngineError.Drm]     (not retryable)
 *   - video frame processing  → [EngineError.Render]  (reattach + retry)
 *   - anything else           → [EngineError.Unknown] (not retryable)
 */
object ExoErrorTaxonomy {

    // IO / source family (media3: 2xxx + the malformed-manifest parsing code).
    const val EXO_ERROR_IO_UNSPECIFIED = 2000 // PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    const val EXO_ERROR_IO_NETWORK_CONNECTION_FAILED = 2001 // …IO_NETWORK_CONNECTION_FAILED
    const val EXO_ERROR_IO_NETWORK_CONNECTION_TIMEOUT = 2002 // …IO_NETWORK_CONNECTION_TIMEOUT
    const val EXO_ERROR_IO_INVALID_HTTP_CONTENT_TYPE = 2003 // …IO_INVALID_HTTP_CONTENT_TYPE
    const val EXO_ERROR_IO_BAD_HTTP_STATUS = 2004 // …IO_BAD_HTTP_STATUS
    const val EXO_ERROR_IO_FILE_NOT_FOUND = 2005 // …IO_FILE_NOT_FOUND
    const val EXO_ERROR_IO_NO_PERMISSION = 2006 // …IO_NO_PERMISSION
    const val EXO_ERROR_IO_CLEARTEXT_NOT_PERMITTED = 2007 // …IO_CLEARTEXT_NOT_PERMITTED
    const val EXO_ERROR_IO_READ_POSITION_OUT_OF_RANGE = 2008 // …IO_READ_POSITION_OUT_OF_RANGE
    const val EXO_ERROR_PARSING_MANIFEST_MALFORMED = 3002 // …PARSING_MANIFEST_MALFORMED

    // Decoder / codec family (media3: 4xxx + the audio-track output codes).
    const val EXO_ERROR_DECODER_INIT_FAILED = 4001 // …DECODER_INIT_FAILED
    const val EXO_ERROR_DECODER_QUERY_FAILED = 4002 // …DECODER_QUERY_FAILED
    const val EXO_ERROR_DECODING_FAILED = 4003 // …DECODING_FAILED
    const val EXO_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004 // …DECODING_FORMAT_EXCEEDS_CAPABILITIES
    const val EXO_ERROR_DECODING_FORMAT_UNSUPPORTED = 4005 // …DECODING_FORMAT_UNSUPPORTED
    const val EXO_ERROR_AUDIO_TRACK_INIT_FAILED = 5001 // …AUDIO_TRACK_INIT_FAILED
    const val EXO_ERROR_AUDIO_TRACK_WRITE_FAILED = 5002 // …AUDIO_TRACK_WRITE_FAILED

    // DRM family (media3: 6xxx; note 6003 CONTENT_ERROR was never in the
    // bucket and stays on the Unknown arm).
    const val EXO_ERROR_DRM_UNSPECIFIED = 6000 // …DRM_UNSPECIFIED
    const val EXO_ERROR_DRM_SCHEME_UNSUPPORTED = 6001 // …DRM_SCHEME_UNSUPPORTED
    const val EXO_ERROR_DRM_PROVISIONING_FAILED = 6002 // …DRM_PROVISIONING_FAILED
    const val EXO_ERROR_DRM_LICENSE_ACQUISITION_FAILED = 6004 // …DRM_LICENSE_ACQUISITION_FAILED
    const val EXO_ERROR_DRM_DISALLOWED_OPERATION = 6005 // …DRM_DISALLOWED_OPERATION
    const val EXO_ERROR_DRM_SYSTEM_ERROR = 6006 // …DRM_SYSTEM_ERROR
    const val EXO_ERROR_DRM_DEVICE_REVOKED = 6007 // …DRM_DEVICE_REVOKED

    // Video frame processing family (media3: 7xxx).
    const val EXO_ERROR_VIDEO_FRAME_PROCESSOR_INIT_FAILED = 7000 // …VIDEO_FRAME_PROCESSOR_INIT_FAILED
    const val EXO_ERROR_VIDEO_FRAME_PROCESSING_FAILED = 7001 // …VIDEO_FRAME_PROCESSING_FAILED

    /**
     * Maps a Media3 error code (the `PlaybackException.errorCode` int) to the
     * engine-agnostic [EngineError]. [message] is the exception's message
     * (null renders the "Unknown playback error" placeholder on the Unknown
     * arm only) and [cause] is threaded into whichever arm carries one.
     */
    fun fromCode(code: Int, message: String?, cause: Throwable?): EngineError = when (code) {
        // Network / IO failures — always retryable.
        EXO_ERROR_IO_UNSPECIFIED,
        EXO_ERROR_IO_NETWORK_CONNECTION_FAILED,
        EXO_ERROR_IO_NETWORK_CONNECTION_TIMEOUT,
        EXO_ERROR_IO_INVALID_HTTP_CONTENT_TYPE,
        EXO_ERROR_IO_BAD_HTTP_STATUS,
        EXO_ERROR_IO_FILE_NOT_FOUND,
        EXO_ERROR_IO_NO_PERMISSION,
        EXO_ERROR_IO_CLEARTEXT_NOT_PERMITTED,
        EXO_ERROR_IO_READ_POSITION_OUT_OF_RANGE,
        EXO_ERROR_PARSING_MANIFEST_MALFORMED,
        -> EngineError.Network(cause)

        // Decoder / codec failures — not retryable on the same engine.
        EXO_ERROR_DECODER_INIT_FAILED,
        EXO_ERROR_DECODER_QUERY_FAILED,
        EXO_ERROR_DECODING_FAILED,
        EXO_ERROR_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        EXO_ERROR_DECODING_FORMAT_UNSUPPORTED,
        EXO_ERROR_AUDIO_TRACK_INIT_FAILED,
        EXO_ERROR_AUDIO_TRACK_WRITE_FAILED,
        -> EngineError.Decoder(codec = null, cause = cause)

        // DRM — not retryable.
        EXO_ERROR_DRM_UNSPECIFIED,
        EXO_ERROR_DRM_SCHEME_UNSUPPORTED,
        EXO_ERROR_DRM_PROVISIONING_FAILED,
        EXO_ERROR_DRM_LICENSE_ACQUISITION_FAILED,
        EXO_ERROR_DRM_DISALLOWED_OPERATION,
        EXO_ERROR_DRM_SYSTEM_ERROR,
        EXO_ERROR_DRM_DEVICE_REVOKED,
        -> EngineError.Drm(scheme = null, cause = cause)

        // Render surface failures — retryable (re-attach surface + retry).
        EXO_ERROR_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
        EXO_ERROR_VIDEO_FRAME_PROCESSING_FAILED,
        -> EngineError.Render(cause = cause)

        else -> EngineError.Unknown(message ?: "Unknown playback error", cause)
    }
}
