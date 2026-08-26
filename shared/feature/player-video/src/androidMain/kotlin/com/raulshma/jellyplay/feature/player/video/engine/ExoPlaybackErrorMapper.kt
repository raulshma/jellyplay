package com.raulshma.jellyplay.feature.player.video.engine

import androidx.media3.common.PlaybackException

/**
 * Deep module: maps a Media3 [PlaybackException] to the engine-agnostic
 * [EngineError] sealed type.
 *
 * Previously this mapping lived as a private top-level function inside
 * `ExoPlayerEngine.kt`, making it unreachable by the live engine
 * (`ExoLiveEngine`, which currently surfaces a raw `String?`) without
 * duplicating the ~25-code table. Promoting it to the shared engine-contract
 * module (next to [EngineError]) gives the mapping a single home: any
 * Media3-based engine — ExoPlayer today, the live engine when it adopts
 * structured errors — maps codes through one table instead of forking it.
 *
 * Bucketing (drives the UI affordance — Retry vs Switch-engine vs OK):
 *   - IO / network / manifest → [EngineError.Network] (retryable)
 *   - decoder / codec         → [EngineError.Decoder] (switch engine)
 *   - DRM                     → [EngineError.Drm]     (not retryable)
 *   - video frame processing  → [EngineError.Render]  (reattach + retry)
 *   - anything else           → [EngineError.Unknown] (not retryable)
 */
fun PlaybackException.toEngineError(): EngineError {
    val raw = message ?: "Unknown playback error"
    val cause = cause
    return when (errorCode) {
        // Network / IO failures — always retryable.
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> EngineError.Network(cause)

        // Decoder / codec failures — not retryable on the same engine.
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> EngineError.Decoder(codec = null, cause = cause)

        // DRM — not retryable.
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION,
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
        -> EngineError.Drm(scheme = null, cause = cause)

        // Render surface failures — retryable (re-attach surface + retry).
        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED,
        PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED,
        -> EngineError.Render(cause = cause)

        else -> EngineError.Unknown(raw, cause)
    }
}
