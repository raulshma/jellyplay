package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Structured playback-error taxonomy surfaced by [MediaEngine]s. Replaces the
 * bare `errorFlow: Flow<String>` string channel so the UI can distinguish
 * recoverable errors (network drop, surface init, start-up timeout) from fatal
 * ones (unsupported codec, DRM failure) and offer the right affordance (Retry
 * vs. Switch-engine/Transcode vs. OK).
 *
 * Each subtype declares its own [retryable] verdict. The mapping from each
 * engine's native error surface to this taxonomy happens at the `MediaEngine`
 * boundary.
 */
sealed interface EngineError {
    val message: String
    val retryable: Boolean
    val cause: Throwable?

    /** A network failure (timeout, DNS, connection reset). Always retryable. */
    data class Network(override val cause: Throwable?) : EngineError {
        override val message: String = "Network error"
        override val retryable: Boolean = true
    }

    /**
     * The media's codec is unsupported or the decoder failed. Not retryable on
     * the same engine — the UI should offer switching engine or transcoding.
     */
    data class Decoder(val codec: String?, override val cause: Throwable?) : EngineError {
        override val message: String = if (codec != null) "Decoder error ($codec)" else "Decoder error"
        override val retryable: Boolean = false
    }

    /** DRM initialization or license failure. Not retryable. */
    data class Drm(val scheme: String?, override val cause: Throwable?) : EngineError {
        override val message: String = if (scheme != null) "DRM error ($scheme)" else "DRM error"
        override val retryable: Boolean = false
    }

    /**
     * The media source returned an error. Retryable only for HTTP 5xx (server
     * errors); 4xx (client errors) are not retried. `httpStatus == null` covers
     * non-HTTP sources (file, smb) where retryability is unknown → not retried.
     */
    data class Source(val httpStatus: Int?, override val cause: Throwable?) : EngineError {
        override val message: String =
            if (httpStatus != null) "Source error (HTTP $httpStatus)" else "Source error"
        override val retryable: Boolean get() = httpStatus in 500..599
    }

    /** Rendering surface initialization or output failure. Retryable. */
    data class Render(override val cause: Throwable?) : EngineError {
        override val message: String = "Render error"
        override val retryable: Boolean = true
    }

    /**
     * Playback failed to start within the initial buffering window (the engine
     * never reached READY). Distinct from [Network] — the request may have
     * succeeded but the decoder/surface pipeline stalled. Retryable: a retry
     * may succeed, or the UI can offer switching engine / transcoding.
     */
    data class Timeout(override val cause: Throwable? = null) : EngineError {
        override val message: String = "Playback failed to start. Try a different player engine."
        override val retryable: Boolean = true
    }

    /** An unmapped error. Carries the engine's raw string for diagnostics. Not retryable. */
    data class Unknown(val raw: String, override val cause: Throwable? = null) : EngineError {
        override val message: String = raw
        override val retryable: Boolean = false
    }
}
