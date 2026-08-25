package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Pure mapping table for the wasmJs `HtmlVideoEngine` — everything that turns
 * DOM `<video>` facts (event names, `MediaError` codes, seconds-valued
 * properties) into [MediaEngine] contract values, with zero DOM types so
 * `commonTest` can pin every row without a browser (the wasmJs browser test
 * lane is opt-in repo-wide; jvmTest is the gate).
 *
 * Counterpart of the mpv error mapping in
 * `apps/desktop/.../MpvDesktopEngine.mapMpvError` and the Android ExoPlayer
 * error_code switch — same [EngineError] taxonomy, different native surface.
 * Lives in this module (not `apps/web`) because the contract's own types are
 * the mapping's vocabulary, and so a future non-browser wasm runtime could
 * reuse the table.
 */
object WebPlaybackMappings {

    // ── HTMLMediaElement MediaError.code constants (HTML spec §4.8.10.3). ──
    // Declared locally as Int: the DOM's MediaError exposes them as property
    // getters on an instance we may not hold, and commonTest cannot see DOM
    // types at all. Values are frozen by the HTML specification.

    /** Fetch aborted by the user/browser. Not a taxonomy-grade failure. */
    const val MEDIA_ERR_ABORTED: Int = 1

    /** Network failure while fetching media data. */
    const val MEDIA_ERR_NETWORK: Int = 2

    /** Media was fetched but could not be decoded. */
    const val MEDIA_ERR_DECODE: Int = 3

    /** src could not be fetched OR its container/codec is unsupported. */
    const val MEDIA_ERR_SRC_NOT_SUPPORTED: Int = 4

    // ── DOM media event names (the subset the engine listens to). ──────────

    const val EVENT_LOADSTART = "loadstart"
    const val EVENT_WAITING = "waiting"
    const val EVENT_LOADEDDATA = "loadeddata"
    const val EVENT_CANPLAY = "canplay"
    const val EVENT_CANPLAYTHROUGH = "canplaythrough"
    const val EVENT_PLAYING = "playing"
    const val EVENT_PLAY = "play"
    const val EVENT_PAUSE = "pause"
    const val EVENT_ENDED = "ended"
    const val EVENT_ERROR = "error"
    const val EVENT_DURATIONCHANGE = "durationchange"
    const val EVENT_TIMEUPDATE = "timeupdate"
    const val EVENT_PROGRESS = "progress"
    const val EVENT_RATECHANGE = "ratechange"
    const val EVENT_VOLUMECHANGE = "volumechange"
    const val EVENT_LOADEDMETADATA = "loadedmetadata"

    /**
     * DOM media event → [EnginePlaybackState]. `null` for events that carry
     * no state transition (`timeupdate`/`progress`/`durationchange`/… are
     * scalars handled separately; `play`/`pause` only seed `isPlaying`).
     *
     * Mirrors the ExoPlayer/mpv mapping: fetch/stall → BUFFERING, first
     * renderable data → READY, natural end → ENDED, fatal element error →
     * ERROR. `canplaythrough` is bucketed with `canplay` (it is a strictly
     * stronger READY signal; treating it separately would only churn).
     */
    fun playbackStateForEvent(eventType: String): EnginePlaybackState? =
        when (eventType) {
            EVENT_LOADSTART, EVENT_WAITING -> EnginePlaybackState.BUFFERING
            EVENT_LOADEDDATA, EVENT_CANPLAY, EVENT_CANPLAYTHROUGH, EVENT_PLAYING ->
                EnginePlaybackState.READY
            EVENT_ENDED -> EnginePlaybackState.ENDED
            EVENT_ERROR -> EnginePlaybackState.ERROR
            else -> null
        }

    /**
     * MediaError.code → [EngineError] taxonomy:
     *  - NETWORK (2) → [EngineError.Network] (always retryable)
     *  - DECODE (3) and SRC_NOT_SUPPORTED (4) → [EngineError.Decoder] — an
     *    unsupported container/codec is the browser-native "decoder failed";
     *    not retryable on the same engine, the UI should offer transcoding
     *  - ABORTED (1) and unknown codes → [EngineError.Unknown] carrying
     *    [rawMessage] when the element supplied one
     *
     * [rawMessage] only surfaces on the Unknown branches — Network/Decoder
     * carry no message slot the engine fills today (kotlinx-browser's
     * MediaError binding exposes `code` only, so the caller passes null).
     */
    fun engineErrorForMediaErrorCode(code: Int, rawMessage: String? = null): EngineError =
        when (code) {
            MEDIA_ERR_NETWORK -> EngineError.Network(null)
            MEDIA_ERR_DECODE, MEDIA_ERR_SRC_NOT_SUPPORTED ->
                EngineError.Decoder(codec = null, cause = null)
            MEDIA_ERR_ABORTED ->
                EngineError.Unknown(rawMessage ?: "Playback aborted (MEDIA_ERR_ABORTED)")
            else -> EngineError.Unknown(rawMessage ?: "Playback error (media code $code)")
        }

    /**
     * `<video>` seconds → contract milliseconds. The DOM reports `duration`
     * as `NaN` before metadata and position values are plain doubles;
     * NaN/±∞/non-positive all fold to `0` so callers never need their own
     * guard.
     */
    fun secondsToMs(seconds: Double): Long =
        if (seconds.isNaN() || seconds.isInfinite() || seconds <= 0.0) {
            0L
        } else {
            (seconds * 1000.0).toLong()
        }

    /**
     * Seek target clamp: [0, duration] when a positive duration is known,
     * else a plain floor at 0. Mirrors `MpvDesktopEngine.seekTo`'s
     * optimistic-position clamp without the DOM read.
     */
    fun clampSeekMs(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

    /**
     * Buffered-position computation from a decoded `video.buffered` range
     * list (each entry the END of one TimeRange, in ms). The tail end is the
     * absolute media time the buffer reaches — the same quantity ExoPlayer
     * publishes as `bufferedPosition` — clamped to [0, duration] so a stale
     * range can't outlive a shorter item. Empty range list → 0.
     */
    fun bufferedTailMs(rangeEndsMs: List<Long>, durationMs: Long): Long {
        if (rangeEndsMs.isEmpty()) return 0L
        val tail = rangeEndsMs.max()
        return if (durationMs > 0) tail.coerceIn(0L, durationMs) else tail.coerceAtLeast(0L)
    }

    /**
     * Contract volume clamp (0f..1f). The DOM `volume` property uses the same
     * 0..1 scale as the contract — unlike mpv's percent — so no conversion
     * exists, only the clamp both `MpvDesktopEngine` and the contract's
     * RemotePlayableEngine docs prescribe.
     */
    fun clampVolume(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * `WxH` stats label from `videoWidth`/`videoHeight`, or `null` before the
     * first frame renders (both are 0 until then) — the null keeps
     * `EngineVideoStats.videoResolution` empty exactly like mpv pre-`video-params`.
     */
    fun resolutionLabel(width: Int, height: Int): String? =
        if (width > 0 && height > 0) "${width}x$height" else null

    /**
     * Whether a [SubtitleSource] can be wired as a `<track>` child: browsers
     * only render WebVTT through the track element (SRT/ASS are fetched but
     * never displayed). True when the MIME type, URL path extension, or codec
     * says VTT. Non-VTT sources are a documented v1 cut — see
     * `HtmlVideoEngine` KDoc.
     */
    fun isWebVttTrack(mimeType: String?, url: String, codec: String?): Boolean {
        if (mimeType != null && mimeType.contains("vtt", ignoreCase = true)) return true
        val path = url.substringBefore('?')
        if (path.endsWith(".vtt", ignoreCase = true)) return true
        if (codec.equals("vtt", ignoreCase = true)) return true
        return false
    }
}
