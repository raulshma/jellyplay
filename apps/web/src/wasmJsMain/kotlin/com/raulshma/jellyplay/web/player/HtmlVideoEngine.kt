package com.raulshma.jellyplay.web.player

import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.EngineConfig
import com.raulshma.jellyplay.feature.player.video.engine.EngineError
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.EnginePositionTicker
import com.raulshma.jellyplay.feature.player.video.engine.EngineVideoStats
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.engine.MediaTrack
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackRequest
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleEvent
import com.raulshma.jellyplay.feature.player.video.engine.SubtitleSource
import com.raulshma.jellyplay.feature.player.video.engine.TimedCue
import com.raulshma.jellyplay.feature.player.video.engine.WebPlaybackMappings
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import org.w3c.dom.HTMLTrackElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event

/**
 * Web (wasmJs) playback backend: a browser `<video>` element implementing the
 * common [MediaEngine] contract (plan §Phase W). Structure mirrors
 * `MpvDesktopEngine` — MutableStateFlow per contract flow, tryEmit-only
 * SharedFlows for errors/subtitle events, scalar mirrors for the synchronous
 * getters, one `engineScope` — with the DOM event pump replacing mpv's event
 * thread (the JS event loop is single-threaded, so no cross-thread
 * synchronization exists by construction).
 *
 * Event → state/error mapping is table-driven through [WebPlaybackMappings]
 * (pure common Kotlin, unit-tested via jvmTest):
 *  - `loadstart`/`waiting` → BUFFERING
 *  - `loadeddata`/`canplay`/`canplaythrough`/`playing` → READY
 *  - `play`/`pause` → `isPlaying` seed only
 *  - `ended` → ENDED; `error` → ERROR + [EngineError]
 *  - `durationchange`/`timeupdate`/`progress`/`ratechange`/`volumechange` →
 *    duration/position/buffered/speed/volume scalar + flow updates
 *
 * mpv lessons carried over: `isPlaying` is seeded at transition events (the
 * `playing` event, not just play/pause ticks); replay from ENDED is
 * seek(0) + play; `durationMs` falls back to `PlaybackRequest.serverDurationMs`
 * until the DOM reports a real duration; `load()` resets per-item state so the
 * previous item's duration/buffer never leaks into this item's BUFFERING
 * window.
 *
 * Video output: the element is created DETACHED and hidden
 * (`display: none`) — the ComposeViewport canvas owns the page, so the engine
 * never inserts itself into the DOM. Call [attachTo] to parent it into a host
 * (a later player UI will supply a dedicated layer); [detach] unpaints it
 * again. Nothing is wired into the shell UI in this phase.
 *
 * Deliberate v1 cuts (each with its unblock path):
 *  - **No HLS on Chromium.** A plain `<video>` only plays HLS where the
 *    browser demuxes it natively (Safari). Chromium needs MSE via hls.js in
 *    a later slice. Direct-play MP4/WebM works everywhere.
 *  - **No HTTP headers.** `<video src>` cannot carry request headers, so the
 *    Jellyfin stream URL must embed `api_key` (it does —
 *    `core/network PlaybackUrlBuilders.buildStreamUrl` appends
 *    `&api_key=`); `PlaybackRequest.headers`/`authToken` are dropped.
 *  - **No track selection.** The element exposes no container-level
 *    audio/subtitle enumeration without MSE; `availableTracks` stays empty
 *    and [selectTrack] is a no-op (audio track switching rides the URL).
 *  - **External subtitles: WebVTT only.** `<track>` renders VTT exclusively;
 *    non-VTT [SubtitleSource]s (SRT/ASS extraction URLs) are skipped.
 *    Track elements appended after the media is loaded only activate on the
 *    next `load()` (HTML spec re-runs the track algorithm there).
 *    **CORS caveat (reviewer catch):** track fetches inherit the media
 *    element's `crossOrigin`, which this engine leaves unset ("no CORS"
 *    mode) — setting it to "anonymous" would flip the VIDEO request to CORS
 *    mode and break servers without CORS headers. Consequence: cross-origin
 *    subtitle URLs fail silently (no cues rendered). In practice subtitles
 *    need same-origin serving (reverse proxy in front of both page and
 *    Jellyfin — see docs/jellyfin-cors.md) until a fetch-and-blob track
 *    loader replaces the plain `<track src>` wiring.
 *  - **`PlaybackRequest.mimeType` dropped.** `<video src>` carries no type
 *    hint; the browser sniffs the container (accepted cost of direct play).
 *  - **No audio effects / delays / passthrough / subtitle styling.** A bare
 *    `<video>` exposes none of them — every capability flag is false and the
 *    corresponding contract calls no-op, per the EngineCapabilityMatrix
 *    behaviour contract.
 *  - **No cues.** `currentCues`/`liveSubtitleCue` stay empty/null
 *    (`supportsCues = false`) until a TextTrack interop slice lands.
 *  - **No `PlayerType`/matrix entry.** Adding an `HTML_VIDEO` enum value
 *    ripples through exhaustive `when`s and persisted settings across the
 *    Android app; the engine ships with a literal [displayName] and its own
 *    all-false capability constant until the web shell grows a UI that needs
 *    the matrix.
 */
class HtmlVideoEngine : MediaEngine {

    override val displayName: String = "HTML Video"

    /**
     * All-false profile (explicit per row, same convention as
     * `EngineCapabilityMatrix`): a bare `<video>` has no PiP hooks here, no
     * delay/passthrough/effect controls, no cue text surface in v1, and no
     * zoom-safe subtitle path (captions ride the element box).
     */
    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsPip = false,
        supportsMiniMode = false,
        supportsCues = false,
        supportsAudioDelay = false,
        supportsSubtitleDelay = false,
        supportsAudioPassthrough = false,
        supportsSubtitleStyle = false,
        supportsSubtitleVerticalPosition = false,
        supportsDialogueBoost = false,
        supportsNightMode = false,
        supportsAudioNormalization = false,
        supportsChannelMixing = false,
        supportsVideoFilters = false,
        supportsLiveQualitySwitch = false,
        supportsBandwidthEstimate = false,
        supportsAssOverride = false,
        supportsAssStyleOverride = false,
        supportsFontFamily = false,
        supportsFreeFormColors = false,
        supportsBorderStyles = false,
        supportsSecondarySubtitles = false,
        supportsScreenshot = false,
    )

    // ── State surface (same shape as MpvDesktopEngine) ──────────────────────

    private val _playbackState = MutableStateFlow(EnginePlaybackState.IDLE)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _availableTracks = MutableStateFlow<List<MediaTrack>>(emptyList())
    override val availableTracks: StateFlow<List<MediaTrack>> = _availableTracks.asStateFlow()

    private val _currentCues = MutableStateFlow<List<TimedCue>>(emptyList())
    override val currentCues: StateFlow<List<TimedCue>> = _currentCues.asStateFlow()

    private val _liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> = _liveSubtitleCue.asStateFlow()

    private val _errorFlow = MutableSharedFlow<EngineError>(extraBufferCapacity = 8)
    override val errorFlow: Flow<EngineError> = _errorFlow.asSharedFlow()

    private val _subtitleEvents = MutableSharedFlow<SubtitleEvent>(extraBufferCapacity = 8)
    override val subtitleEvents: Flow<SubtitleEvent> = _subtitleEvents.asSharedFlow()

    private val _bufferedPositionMs = MutableStateFlow(0L)
    override val bufferedPositionMs: StateFlow<Long> = _bufferedPositionMs.asStateFlow()

    private val _videoStats = MutableStateFlow(EngineVideoStats())
    override val videoStats: StateFlow<EngineVideoStats> = _videoStats.asStateFlow()

    private val _pollingIntervalMs = MutableStateFlow(DEFAULT_POLLING_INTERVAL_MS)
    override val pollingIntervalMs: StateFlow<Long> = _pollingIntervalMs.asStateFlow()
    override fun setPollingIntervalMs(ms: Long) { _pollingIntervalMs.value = ms }

    private val _videoStatsEnabled = MutableStateFlow(false)
    override val videoStatsEnabled: StateFlow<Boolean> = _videoStatsEnabled.asStateFlow()
    override fun setVideoStatsEnabled(enabled: Boolean) { _videoStatsEnabled.value = enabled }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val audioSessionId: Int = -1 // no Android audio session on wasm

    private var positionMs: Long = 0L
    private var durationValue: Long = 0L
    /** Server-reported runtime fallback while the DOM duration is unknown. */
    private var serverDurationMs: Long = 0L
    override val currentPositionMs: Long get() = positionMs
    override val durationMs: Long get() = if (durationValue > 0) durationValue else serverDurationMs

    private var speedValue: Float = 1f
    override val playbackSpeed: Float get() = speedValue

    private var volumeValue: Float = 1f
    override val volume: Float get() = volumeValue

    override val underlyingPlayer: Any? get() = if (released) null else video

    override val positionFlow: Flow<Long> = callbackFlow {
        trySend(currentPositionMs)
        val ticker = EnginePositionTicker(
            scope = engineScope,
            pollingIntervalMs = _pollingIntervalMs,
            isPlayingFlow = isPlaying,
            isCurrentlyPlaying = { isPlaying.value },
            onActive = { trySend(currentPositionMs) },
        ).launch()
        awaitClose { ticker.cancel() }
    }.conflate()

    // ── Element + DOM wiring ────────────────────────────────────────────────

    private val video: HTMLVideoElement = document.createElement("video") as HTMLVideoElement

    /** The DOM parent the element is currently rendered in, if attached. */
    private var attachedParent: Element? = null

    private var released = false
    private var pendingStartMs: Long = 0L
    private var currentConfig: EngineConfig = EngineConfig()

    private val domEventHandler: (Event) -> Unit = { event -> handleDomEvent(event.type) }

    private val handledEventTypes = listOf(
        WebPlaybackMappings.EVENT_LOADSTART,
        WebPlaybackMappings.EVENT_WAITING,
        WebPlaybackMappings.EVENT_LOADEDDATA,
        WebPlaybackMappings.EVENT_CANPLAY,
        WebPlaybackMappings.EVENT_CANPLAYTHROUGH,
        WebPlaybackMappings.EVENT_PLAYING,
        WebPlaybackMappings.EVENT_PLAY,
        WebPlaybackMappings.EVENT_PAUSE,
        WebPlaybackMappings.EVENT_ENDED,
        WebPlaybackMappings.EVENT_ERROR,
        WebPlaybackMappings.EVENT_DURATIONCHANGE,
        WebPlaybackMappings.EVENT_TIMEUPDATE,
        WebPlaybackMappings.EVENT_PROGRESS,
        WebPlaybackMappings.EVENT_RATECHANGE,
        WebPlaybackMappings.EVENT_VOLUMECHANGE,
        WebPlaybackMappings.EVENT_LOADEDMETADATA,
    )

    init {
        // Detached + hidden: the canvas owns the page until a player UI
        // attaches the element to its own host layer.
        video.style.display = "none"
        video.preload = "auto"
        handledEventTypes.forEach { video.addEventListener(it, domEventHandler) }

        // Stats projector (mpv pattern): high-churn reads only while the
        // stats overlay is open.
        engineScope.launch {
            while (isActive) {
                if (videoStatsEnabled.value && !released) projectVideoStats()
                delay(VIDEO_STATS_POLL_MS)
            }
        }
    }

    /** Parents the video element into [parent] and paints it once media loads. */
    fun attachTo(parent: Element) {
        if (released) return
        attachedParent = parent
        if (video.parentNode != parent) {
            video.parentNode?.removeChild(video)
            parent.appendChild(video)
        }
        updateVisibility()
    }

    /** Unparents the video element and hides it again. Playback continues. */
    fun detach() {
        attachedParent = null
        video.parentNode?.removeChild(video)
        updateVisibility()
    }

    /** Visible only while attached to a host AND an item is loaded. */
    private fun updateVisibility() {
        video.style.display =
            if (attachedParent != null && _playbackState.value != EnginePlaybackState.IDLE) "block" else "none"
    }

    // ── DOM event dispatch (mapping table in WebPlaybackMappings) ──────────

    private fun handleDomEvent(type: String) {
        when (type) {
            WebPlaybackMappings.EVENT_PLAY -> _isPlaying.value = true
            WebPlaybackMappings.EVENT_PAUSE -> _isPlaying.value = false
            WebPlaybackMappings.EVENT_PLAYING -> _isPlaying.value = true
            WebPlaybackMappings.EVENT_ENDED -> {
                _isPlaying.value = false
                _liveSubtitleCue.value = null
            }
            WebPlaybackMappings.EVENT_ERROR -> {
                // kotlinx-browser's wasmJs MediaError exposes only `code` —
                // the spec's legacy `message` string is not in the binding,
                // so the mapper's built-in raw strings carry the diagnostic.
                val mediaError = video.error ?: return
                _errorFlow.tryEmit(
                    WebPlaybackMappings.engineErrorForMediaErrorCode(mediaError.code.toInt()),
                )
            }
            WebPlaybackMappings.EVENT_LOADEDMETADATA -> {
                if (pendingStartMs > 0) {
                    // startPositionMs arrives as currentTime on the first
                    // metadata; seeking earlier throws per spec.
                    video.currentTime = pendingStartMs / 1000.0
                    pendingStartMs = 0L
                }
            }
            WebPlaybackMappings.EVENT_DURATIONCHANGE ->
                if (hasMetadataOrBetter()) {
                    durationValue = WebPlaybackMappings.secondsToMs(video.duration)
                }
            WebPlaybackMappings.EVENT_TIMEUPDATE ->
                if (hasMetadataOrBetter()) {
                    positionMs = WebPlaybackMappings.secondsToMs(video.currentTime)
                }
            WebPlaybackMappings.EVENT_PROGRESS ->
                if (hasMetadataOrBetter()) {
                    _bufferedPositionMs.value =
                        WebPlaybackMappings.bufferedTailMs(bufferedRangeEndsMs(), durationMs)
                }
            WebPlaybackMappings.EVENT_RATECHANGE ->
                speedValue = video.playbackRate.toFloat()
            WebPlaybackMappings.EVENT_VOLUMECHANGE -> {
                volumeValue = WebPlaybackMappings.clampVolume(video.volume.toFloat())
            }
        }

        // State transition LAST, so the handlers above can read the
        // pre-transition state; suppressed while ENDED (mpv's eof-reached
        // guard: stale canplay/waiting after the last frame must not un-END
        // the item — replay re-enters via play()'s explicit BUFFERING seed).
        WebPlaybackMappings.playbackStateForEvent(type)?.let { mapped ->
            val suppressed = _playbackState.value == EnginePlaybackState.ENDED &&
                (mapped == EnginePlaybackState.BUFFERING || mapped == EnginePlaybackState.READY)
            if (!suppressed) setPlaybackState(mapped)
        }
    }

    private fun setPlaybackState(state: EnginePlaybackState) {
        _playbackState.value = state
        updateVisibility()
    }

    /**
     * Stale-event guard for the src-swap race (reviewer catch): load() resets
     * the scalars synchronously, but timeupdate/progress/durationchange tasks
     * already queued for the OLD src still run afterward (single JS thread ≠
     * cancelled tasks). Per spec the readyState drops to HAVE_NOTHING when
     * load() drops the resource, and the new item only emits these events
     * once metadata exists — so gating on it keeps the old item's
     * position/buffer/duration out of the new item's BUFFERING window.
     */
    private fun hasMetadataOrBetter(): Boolean = video.readyState.toInt() > 0

    private fun bufferedRangeEndsMs(): List<Long> {
        val buffered = video.buffered
        return buildList {
            for (index in 0 until buffered.length) {
                add(WebPlaybackMappings.secondsToMs(buffered.end(index)))
            }
        }
    }

    // ── MediaEngine: source loading & teardown ─────────────────────────────

    override fun load(request: PlaybackRequest) {
        if (released) return
        // Per-item reset (Android/mpv pattern): the previous item's
        // duration/buffer must not leak into this item's BUFFERING window.
        serverDurationMs = request.serverDurationMs
        durationValue = 0L
        positionMs = 0L
        pendingStartMs = request.startPositionMs.coerceAtLeast(0L)
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
        _availableTracks.value = emptyList()
        _liveSubtitleCue.value = null
        _isPlaying.value = false

        // Subtitles: rebuild <track> children. WebVTT only (browsers render
        // nothing else through track elements) — see class KDoc.
        removeTrackElements()
        request.externalSubtitles
            .filter { WebPlaybackMappings.isWebVttTrack(it.mimeType, it.url, it.codec) }
            .forEach(::appendTrackElement)

        // request.headers/authToken are unusable on a media element (class
        // KDoc): the URL must embed api_key, which buildStreamUrl guarantees.
        video.src = request.uri
        video.playbackRate = speedValue.toDouble()
        video.load()
        setPlaybackState(EnginePlaybackState.BUFFERING)
    }

    override fun release() {
        if (released) return
        stop()
        released = true
        engineScope.cancel()
        handledEventTypes.forEach { video.removeEventListener(it, domEventHandler) }
        video.parentNode?.removeChild(video)
        attachedParent = null
    }

    // ── MediaEngine: transport control ─────────────────────────────────────

    override fun play() {
        if (released) return
        if (_playbackState.value == EnginePlaybackState.ENDED) {
            // Replay from ENDED = seek(0) + play (mpv keep-open lesson): a
            // paused-at-EOF element restarts nothing by itself. The seek
            // flips the ended condition, then BUFFERING re-arms the state
            // mapping suppressed while ENDED.
            video.currentTime = 0.0
            setPlaybackState(EnginePlaybackState.BUFFERING)
        }
        // Autoplay-policy rejections (NotAllowedError before a user gesture)
        // are a browser precondition, not an engine fault: swallow them —
        // isPlaying simply stays false until a gesture-driven retry. The js()
        // body exists because the typed Promise.catch on wasmJs has no
        // JsAny-typed Unit to return from its handler lambda.
        playIgnoringAutoplayRejection(video)
    }

    override fun pause() {
        if (released) return
        video.pause()
    }

    override fun stop() {
        if (released) return
        video.pause()
        // The spec's reset recipe: drop the src, re-run load() → emptied,
        // decoder released. Stop semantics match MpvDesktopEngine: the engine
        // stays usable for the next load() from a clean IDLE.
        video.removeAttribute("src")
        removeTrackElements()
        video.load()
        pendingStartMs = 0L
        positionMs = 0L
        durationValue = 0L
        setPlaybackState(EnginePlaybackState.IDLE)
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _liveSubtitleCue.value = null
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        val clamped = WebPlaybackMappings.clampSeekMs(positionMs, durationMs)
        if (_playbackState.value == EnginePlaybackState.ENDED && clamped < durationMs) {
            // Scrubbing back off the last frame exits ENDED (mpv's eof-reached
            // flip on seek): READY-but-paused. Without this, the next play()
            // would take the replay path and discard the position the user
            // just chose; seeking exactly to the end keeps ENDED.
            setPlaybackState(EnginePlaybackState.READY)
        }
        video.currentTime = clamped / 1000.0
        this.positionMs = clamped
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (released) return
        video.playbackRate = speed.toDouble()
        speedValue = speed
    }

    // ── MediaEngine: volume/mute (RemotePlayableEngine; <video>.volume is
    //    already the contract's 0..1 float — no percent conversion) ────────

    override fun setVolume(value: Float) {
        if (released) return
        val clamped = WebPlaybackMappings.clampVolume(value)
        volumeValue = clamped
        video.volume = clamped.toDouble()
    }

    override fun increaseVolume(delta: Float) = setVolume(volumeValue + delta)

    override fun decreaseVolume(delta: Float) = setVolume(volumeValue - delta)

    override fun setMuted(muted: Boolean) {
        if (released) return
        video.muted = muted
    }

    // ── MediaEngine: tracks & subtitles ────────────────────────────────────

    /** No container-level enumeration without MSE — v1 cut, see class KDoc. */
    override fun selectTrack(type: TrackType, index: Int) {
        // Deliberate no-op: availableTracks is empty by construction.
    }

    /** Server-side concern (PlaybackInfo maxBitrate), same reasoning as mpv. */
    override fun setMaxVideoBitrate(bps: Int?) {
        // Deliberate no-op: the engine plays the URL it is handed.
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        if (released) return
        if (!WebPlaybackMappings.isWebVttTrack(source.mimeType, source.url, source.codec)) return
        appendTrackElement(source)
        // Note: a <track> appended after the resource loaded only activates
        // on the next load() (HTML spec) — v1 cut, documented in class KDoc.
    }

    private fun appendTrackElement(source: SubtitleSource) {
        val track = document.createElement("track") as HTMLTrackElement
        track.kind = "subtitles"
        track.src = source.url
        track.label = source.label
        source.language?.let { track.srclang = it }
        track.default = source.isDefault
        video.appendChild(track)
    }

    private fun removeTrackElements() {
        val children = video.querySelectorAll("track")
        // Collect first: removing mid-iteration mutates the live NodeList.
        // (?.let, not addNotNull — that extension does not resolve inside
        // buildList on the wasmJs stdlib.)
        val tracks = buildList {
            for (index in 0 until children.length) {
                (children.item(index) as? HTMLTrackElement)?.let { add(it) }
            }
        }
        tracks.forEach { track -> track.parentNode?.removeChild(track) }
    }

    // ── MediaEngine: config & styling (capability-false no-ops) ────────────

    override fun updateConfig(config: EngineConfig) {
        if (currentConfig == config) return
        currentConfig = config
        // No EngineConfig field is actionable on a bare <video> (audio/
        // subtitle delays, effects, passthrough and subtitle styling have no
        // DOM API here) — the capability-false no-op the matrix KDoc
        // prescribes. Stored so a future config-driven slice sees the latest
        // value.
    }

    override fun applySubtitleStyle(style: SubtitleStyle) {
        // Deliberate no-op (supportsSubtitleStyle = false).
    }

    // ── MediaEngine: aspect ratio (CSS object-fit on the element) ──────────

    override fun setAspectRatio(ratio: AspectRatio) {
        if (released) return
        video.style.objectFit = when (ratio) {
            AspectRatio.FILL -> "fill"
            AspectRatio.CROP -> "cover"
            // AUTO/FIT letterbox inside the element box; the fixed RATIO_*
            // entries need a sized wrapper element to force an arbitrary
            // ratio (a bare <video> only has contain/cover/fill) — v2.
            else -> "contain"
        }
    }

    // ── Stats projection ────────────────────────────────────────────────────

    private fun projectVideoStats() {
        _videoStats.value = EngineVideoStats(
            // The browser pipeline is the only decoder there is; no codec
            // identity is exposed without MSE.
            videoDecoder = "browser",
            videoResolution = WebPlaybackMappings.resolutionLabel(
                video.videoWidth,
                video.videoHeight,
            ),
            bufferedPositionMs = _bufferedPositionMs.value,
        )
    }

    private companion object {
        private const val DEFAULT_POLLING_INTERVAL_MS = 1000L
        private const val VIDEO_STATS_POLL_MS = 1000L
    }
}

/**
 * `video.play()` with the rejection swallowed (autoplay-policy NotAllowedError
 * before a user gesture is a browser precondition, not an engine fault). The
 * `js()` body exists because the typed `Promise.catch` on wasmJs has no
 * JsAny-typed Unit to return from its handler lambda, and `js()` calls must be
 * a single expression inside a top-level function body in Kotlin/Wasm.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun playIgnoringAutoplayRejection(video: HTMLVideoElement) {
    js("video.play().catch(function (e) { })")
}
