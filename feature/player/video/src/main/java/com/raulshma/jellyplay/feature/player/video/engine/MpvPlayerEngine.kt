package com.raulshma.jellyplay.feature.player.video.engine

import android.content.Context

import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import com.raulshma.jellyplay.core.data.playback.DialogueBoostHelper
import com.raulshma.jellyplay.core.data.playback.EqualizerHelper
import com.raulshma.jellyplay.core.data.playback.MediaStreamVolume
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvAudioOutput
import com.raulshma.jellyplay.core.model.MpvDemuxerMaxBytes
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.MpvFrameDrop
import com.raulshma.jellyplay.core.model.MpvHwdec
import com.raulshma.jellyplay.core.model.MpvScaler
import com.raulshma.jellyplay.core.model.MpvSkipLoopFilter
import com.raulshma.jellyplay.core.model.MpvVideoOutput
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.model.parseMpvConfigOptions
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class MpvPlayerEngine(
    private val context: Context,
    private val fontProvider: FontProvider,
) : ReloadablePlayerEngine(context) {

    companion object {
        private const val TAG = "MpvPlayerEngine"
        private const val DEMUXER_MAX_BYTES_LOW = 32 * 1024 * 1024L
        private const val DEMUXER_MAX_BYTES_NORMAL = 64 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_LOW = 16 * 1024 * 1024L
        private const val DEMUXER_MAX_BACK_BYTES_NORMAL = 32 * 1024 * 1024L
        // mpv_end_file_reason — see mpv client.h handleEndFile().
        private const val MPV_END_FILE_REASON_EOF = 0
        private const val MPV_END_FILE_REASON_STOP = 1
        private const val MPV_END_FILE_REASON_QUIT = 2
        private const val MPV_END_FILE_REASON_ERROR = 3
        private const val MPV_END_FILE_REASON_REDIRECT = 4
        // mpv_error codes carried by the END_FILE node's `error` field — see
        // mpv client.h. A network/source load failure surfaces as
        // MPV_ERROR_LOADING_FAILED; decoder/output/format init failures are
        // fatal on the same engine. Used by [mapMpvError].
        private const val MPV_ERROR_LOADING_FAILED = -13
        private const val MPV_ERROR_AO_INIT_FAILED = -14
        private const val MPV_ERROR_VO_INIT_FAILED = -15
        private const val MPV_ERROR_NOTHING_TO_PLAY = -16
        private const val MPV_ERROR_UNKNOWN_FORMAT = -17
        private const val MPV_ERROR_UNSUPPORTED = -18
        private const val MPV_ERROR_NOT_IMPLEMENTED = -19
        // Prefix/text filter for which verbose (below WARN) mpv messages are
        // surfaced in debug builds. Covers the subtitle/font/render pipeline
        // (sub/ass/libass/vtt/srt) plus the demux/vo/decode paths that feed it,
        // so a no-render bug can be traced end-to-end without logcat drowning.
        private val MPV_SUBTITLE_LOG_PATTERN =
            Regex("(?i)(sub|subtitle|libass|webvtt|vtt|srt|ssa|ass|ffmpeg|http|stream|vo/|demux|cplayer|vd)")
        private val REDACT_API_KEY = Regex("(?i)(api_key=)[^&\\s]+")
        private val REDACT_API_KEY_ENCODED = Regex("(?i)(api_key%3D)[^&\\s]+")
        private val REDACT_EMBY_TOKEN = Regex("(?i)(X-Emby-Token:\\s*)[^,\\s]+")
    }

    private val isLowRamDevice by lazy { EngineDeviceProfile.isLowRamDevice(context) }

    override val capabilities = EngineCapabilityMatrix.MPV
    override val zoomSafeSubtitleStrategy = ZoomSafeSubtitleStrategy.COMPOSE_CUE
    override val displayName: String = PlayerType.MPV.displayName

    // The currently-displayed subtitle line, exposed to the screen for the
    // zoom-safe Compose overlay (zoomSafeSubtitleStrategy = COMPOSE_CUE).
    // Distinct from the accumulated [currentCues] history: this is the single
    // live line, cleared on blank/track-switch/stop. Driven by mpv's `sub-text`
    // property (ASS override tags already stripped by mpv).
    private val _liveSubtitleCue = MutableStateFlow<CharSequence?>(null)
    override val liveSubtitleCue: StateFlow<CharSequence?> = _liveSubtitleCue.asStateFlow()

    private var mpvView: PlayerMPVView? = null
    private var pendingRequest: PlaybackRequest? = null
    @Volatile private var pendingSubtitles: List<SubtitleSource> = emptyList()
    /**
     * Maps the `title` (== the [SubtitleSource.label] passed to `sub-add`) of a
     * side-loaded subtitle to the caller-supplied [SubtitleSource.id] —
     * `"offline:${index}"` for downloaded sidecars, `"external:${index}"` for
     * remote non-manifest subs. mpv's `sub-add` takes no id argument, so without
     * this registry [buildTracks] would emit the synthetic `"mpv_sub_${id}"` and
     * lose the stable id that the offline-subtitle restore path keys on.
     * ExoPlayer instead propagates the `MediaItem.SubtitleConfiguration.id` into
     * the track `format.id`; this registry keeps mpv's exposed [MediaTrack.id]
     * consistent with that so a persisted/pending offline selection resolves on
     * both engines.
     *
     * Keyed by label because that is the exact `title` arg echoed back in mpv's
     * `track-list`, matching the label-keyed dedup in [existingSubLabels].
     * Rebuilt per item in [load] and cleared in [release] so entries never bleed
     * across items. Reference-swapped (never mutated in place) so [buildTracks]
     * reads it race-free from the main thread.
     */
    @Volatile private var sideLoadedSubtitleIds: Map<String, String> = emptyMap()
    // Android audio session id generated via AudioManager and pushed into
    // mpv's audiotrack/aaudio outputs so Android AudioEffects (dialogue
    // boost, night mode) can bind to mpv's output. Previously read back
    // the string property "audio-device-id" as an int, which always
    // threw and returned 0 — leaving the effect chain unbound.
    @Volatile private var generatedAudioSessionId: Int = 0

    // Observer-driven cached playback state. These are populated by mpv
    // property observers (postInitOptions) instead of via per-tick
    // getProperty JNI calls on the main thread — the old polling approach
    // issued 3–15 synchronous JNI reads/second on the main looper and was a
    // primary source of UI jank during mpv playback
    @Volatile private var cachedPositionMs: Long = 0L
    @Volatile private var cachedDurationMs: Long = 0L
    @Volatile private var cachedBufferedPositionMs: Long = 0L
    // Most recent sub-start (media-time seconds) reported by mpv; pairs with
    // the next sub-text emission to stamp a TimedCue start. -1 = no current sub.
    @Volatile private var cachedSubStartSec: Double = -1.0
    // Server-reported total runtime, used as a fallback when the mpv demuxer
    // cannot resolve a duration for HLS/transcoded streams (where `duration`
    // is frequently 0/partial). Set from PlaybackRequest in load().
    @Volatile private var serverDurationMs: Long = 0L

    // mpv handles its own internal EQ via af filters; this helper exists
    // solely to host the dialogue-boost overlay (see DialogueBoostHelper
    // kdoc) on the engine's audio session. User EQ settings never flow
    // through it — the helper stays at FLAT base levels with only the
    // boost offsets overlaid.
    private val equalizerHelper = EqualizerHelper()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper)
    private val nightMode = NightModeHelper()

    private var wasPlayingBeforeActivityPause = false

    // Set true by [release] before the async native destroy. Observer callbacks
    // read this at entry and bail, so a callback that races in during the
    // teardown window (after removeObserver but before mpv_terminate_destroy
    // finishes on the release thread) cannot touch torn-down state. Matches
    // mpvkt's `player.isExiting` guard.
    @Volatile private var released = false

    // Coalesces the burst of immediate refreshTracks() calls that fire when a
    // track changes: a single subtitle pick triggers `select-${type}` plus the
    // `sid`/`aid`/`track-list` observers within ~50 ms, each previously posting
    // its own synchronous getPropertyNode("track-list") JNI read (plus the
    // debug-only logSubtitleRenderState reads) onto the main looper. That burst
    // was a primary cause of the MPV playback ANR. See [TrackRefreshCoalescer];
    // delayed refreshes (late-arriving track enumeration for HLS/transcoded
    // streams) keep their own mainHandler.postDelayed slot so they are not
    // cancelled by an intervening immediate refresh.
    private val trackRefresh = TrackRefreshCoalescer(
        scopeProvider = { engineScope },
        onRefresh = { performCoalescedRefresh() },
    )

    /**
     * The coalesced refresh body. Runs on engineScope = Dispatchers.Main so
     * buildTracks() stays main-threaded to serialise against
     * mpv_terminate_destroy (see refreshTracks). The coalescer already collapsed
     * the observer burst into this single read.
     */
    private fun performCoalescedRefresh() {
        if (released) return
        val tracks = try {
            buildTracks()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh MPV tracks (coalesced)", e); return
        }
        publishTracks(tracks, "coalesced")
    }

    /**
     * Dedicated background thread for native mpv teardown. [release] is invoked
     * from the Compose `onDispose` on the main thread; `BaseMPVView.destroy()`
     * runs `mpv_terminate_destroy()` which synchronously tears down the GPU
     * context, demuxer, network threads, and libass — blocking for hundreds of
     * ms to seconds. Routing stop+destroy onto this thread keeps the main 
     * looper responsive on
     * player close. Created lazily so non-mpv engines pay nothing.
     */
    private val releaseThread: HandlerThread by lazy {
        HandlerThread("MpvRelease", android.os.Process.THREAD_PRIORITY_BACKGROUND)
            .also { it.start() }
    }
    private val releaseHandler: Handler by lazy { Handler(releaseThread.looper) }

    override fun onActivityPause() {
        wasPlayingBeforeActivityPause = _isPlaying.value
        pause()
    }

    override fun onActivityResume() {
        if (wasPlayingBeforeActivityPause) {
            wasPlayingBeforeActivityPause = false
            play()
        }
    }

    private inner class PlayerMPVView(
        ctx: Context,
    ) : BaseMPVView(ctx, null) {

        private val observer = object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "demuxer-cache-time" -> cachedBufferedPositionMs = value * 1000L
                }
            }
            override fun eventProperty(property: String, value: Double) {
                when (property) {
                    // time-pos MUST be observed as DOUBLE: as INT64, mpv emits
                    // a property change only when the whole-second value
                    // changes (1 update/sec), so currentPositionMs quantizes
                    // to 1000ms steps. SyncPlay's correction loop compares it
                    // against a continuously-advancing server clock and read
                    // the 0..1000ms quantization gap as drift, SkipToSync-
                    // seeking (and pulsing "Syncing") on most 2s correction
                    // ticks — the endless syncing/synced cycle on mpv. DOUBLE
                    // updates per frame, giving ms precision.
                    "time-pos" -> cachedPositionMs = (value * 1000L).toLong().coerceAtLeast(0L)
                    "duration" -> cachedDurationMs = (value * 1000L).toLong().coerceAtLeast(0L)
                    "sub-start" -> cachedSubStartSec = value
                    "demuxer-cache-duration" -> {
                        // demuxer-cache-duration is relative to the current
                        // position; the ticker folds it into a downstream
                        // buffered value via updateBufferPosition(). Cache the
                        // raw seconds here (no JNI) so the getter path stays
                        // off the main-thread read loop.
                        cachedBufferedPositionMs = cachedPositionMs + (value * 1000L).toLong()
                    }
                }
            }
            override fun eventProperty(property: String, value: Boolean) {
                if (released) return
                if (property == "pause") {
                    _isPlaying.value = !value
                }
                if (property == "paused-for-cache") {
                    _playbackState.value = if (value) EnginePlaybackState.BUFFERING else EnginePlaybackState.READY
                }
                if (property == "sub-visibility") {
                    Log.d(TAG, "MPV subtitle visibility changed to $value")
                }
                if (property == "eof-reached" && value) {
                    // With keep-open=yes, natural EOF pauses on the last frame
                    // and does NOT emit END_FILE — so this observer is the
                    // authoritative end-of-content signal. The raw END_FILE
                    // handler must not be treated as EOF (it fires for network
                    // drops, transcode aborts, redirects, stop — which is why
                    // playback used to stop a few seconds in on transcoded
                    // streams).
                    Log.d(TAG, "MPV eof-reached=true → ENDED")
                    _playbackState.value = EnginePlaybackState.ENDED
                }
            }
            override fun eventProperty(property: String, value: String) {
                if (property == "sid" || property == "aid") {
                    Log.d(TAG, "MPV $property changed to ${redactSensitive(value)}")
                    if (property == "sid") {
                        // Subtitle track switch: reset accumulated cues so lines
                        // from the prior track don't bleed into the preview.
                        _currentCues.value = emptyList()
                        // Clear the live overlay line so a stale caption from the
                        // previous track doesn't linger while zoomed.
                        _liveSubtitleCue.value = null
                    }
                    refreshTracks("property:$property")
                } else if (property == "sub-text") {
                    accumulateMpvSubText(value)
                    // Mirror the live line into the overlay flow. mpv fires
                    // sub-text only on a line change and emits "" when the line
                    // clears — surface both so the Compose overlay updates/clears
                    // in lockstep with native rendering.
                    _liveSubtitleCue.value = value.takeIf { it.isNotBlank() }
                }
            }
            override fun eventProperty(property: String, value: MPVNode) {
                if (property == "track-list") {
                    refreshTracks("property:track-list")
                }
            }
            override fun event(eventId: Int, data: MPVNode) {
                if (released) return
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        Log.d(TAG, "MPV start file; adding ${pendingSubtitles.size} Jellyfin subtitle source(s)")
                        addPendingSubtitles(mpv)
                        _playbackState.value = EnginePlaybackState.BUFFERING
                        // Surface side-loaded subs + early track entries. mpv's
                        // track-list observer does not reliably fire for
                        // externally added (sub-add) tracks or for the demuxer
                        // entries of an HLS/transcoded stream that resolve
                        // slightly after start-file, so re-poll explicitly.
                        refreshTracks("start-file", delayMs = 200)
                        refreshTracks("start-file-late", delayMs = 800)
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "MPV file loaded")
                        _playbackState.value = EnginePlaybackState.READY
                        refreshTracks("file-loaded")
                        // For HLS/transcoded streams the demuxer populates audio
                        // track-list entries asynchronously after FILE_LOADED;
                        // a single immediate read races ahead of that and yields
                        // an empty audio picker. Re-poll after a short delay so
                        // late-arriving audio/subtitle tracks are enumerated.
                        refreshTracks("file-loaded-late", delayMs = 500)
                    }
                    MPV.mpvEvent.MPV_EVENT_END_FILE -> {
                        // END_FILE is NOT end-of-content with keep-open=yes:
                        // true EOF keeps the file open and flips eof-reached
                        // (handled above). END_FILE here means mpv closed the
                        // demuxer mid-stream — a transcode session abort,
                        // network drop, HTTP redirect, or stop command. The
                        // data node carries {reason, error}; only act on it
                        // to avoid the previous bug where every END_FILE was
                        // treated as completion → playback stopped after a few
                        // seconds on transcoded/flaky HLS streams.
                        handleEndFile(data)
                    }
                }
            }
        }

        private val logObserver = object : MPV.LogObserver {
            override fun logMessage(prefix: String, level: Int, text: String) {
                logMpvMessage(prefix, level, text)
            }
        }

        override fun initOptions() {
            val configDir = java.io.File(context.filesDir, "mpv")
            mpv.setOptionString("config", "yes")
            mpv.setOptionString("config-dir", configDir.absolutePath)

            val fontsDir = fontProvider.provideFontsDir()
            mpv.setOptionString("sub-fonts-dir", fontsDir.absolutePath)
            // Force the libass font provider off. On some devices libass's
            // fontconfig provider fails to initialize ("can't find selected font
            // provider" — observed on Adreno 509 / Nokia 6.1 Plus under app
            // isolation, with OR without a FONTCONFIG_FILE env override), and
            // EVERY subtitle then rasterizes to an empty bitmap. With "none",
            // libass resolves fonts solely from sub-fonts-dir (the bundled
            // subfont.ttf + any user-installed .ttf) plus the ASS `sub-font`
            // default set below. System fontconfig aliasing is lost, but that is
            // strictly better than no subtitles at all, and ASS tracks usually
            // embed their own fonts (mkv attachments), which libass loads via
            // the demuxer regardless of provider.
            mpv.setOptionString("sub-font-provider", "none")
            // Default the requested family to the bundled fallback's own family
            // so libass matches it exactly under the none provider. Overridden
            // per-style in applySubtitleStyleProperties when the user picks a
            // font, and ASS tracks ignore sub-font unless sub-ass-override=force.
            fontProvider.bundledFallbackFamilyName()?.let { mpv.setOptionString("sub-font", it) }

            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()

            val hwdecValue = mpvCfg.hwdecOverride?.key ?: decoderModeToHwdec(currentConfig.decoderMode)
            mpv.setOptionString("hwdec", hwdecValue)
            mpv.setOptionString("hwdec-codecs", "all")

            val aoValue = buildString {
                append(mpvCfg.audioOutput.key)
                mpvCfg.audioFallback?.let { append(",").append(it.key) }
            }
            mpv.setOptionString("ao", aoValue)
            // gpu-context / opengl-es are NOT set: the is.xyz.mpv BaseMPVView
            // binding (io.github.abdallahmehiz:mpv-android-lib) creates its own
            // GLES context internally, so these options are redundant and can
            // race with the binding's own context setup. mpvkt — the reference
            // app for this exact binding — sets neither.
            // Size subtitles against the video frame, not the OS window. With
            // "yes" (window-relative), rotating to portrait grows the window
            // height ~2x and blows the captions up, while the video itself is
            // letterboxed; "no" keeps captions proportional to the video, so
            // they stay correct and consistent across rotation — matching how
            // ExoPlayer (fixed SP) and VLC (video-relative freetype) behave.
            //
            mpv.setOptionString("sub-scale-with-window", "no")
            mpv.setOptionString("sub-auto", "fuzzy")
            mpv.setOptionString("sub-visibility", "yes")
            mpv.setOptionString("sub-ass-override", "scale")
            mpv.setOptionString("keep-open", "yes")
            applySubtitleStyleOptions(mpv, currentConfig.subtitleStyle)
            mpv.setOptionString("panscan", "0.0")
            mpv.setOptionString("sub-use-margins", "no")
            mpv.setOptionString("sub-ass-force-margins", "no")

            mpv.setOptionString("scale", mpvCfg.scaler.key)
            // dscale (downscaler) is the hot path on phones (1080p/4K video
            // downscaled to the display). High-order scalers (lanczos, spline*)
            // there are costly per-frame GPU for a downscale where bilinear is
            // visually indistinguishable at phone DPI. Leave it unset so mpv
            // uses its default (bilinear / oversample) — matches mpvkt and
            // upstream mpv-android. The user-chosen `scale` still drives the
            // upscaler.
            // (Previously mirrored `scale`, which forced e.g. lanczos on the
            // downscale path — a steady GPU tax even on capable hardware.)
            if (mpvCfg.deband) {
                mpv.setOptionString("deband", "yes")
            }
            if (mpvCfg.interpolation) {
                mpv.setOptionString("interpolation", "yes")
                mpv.setOptionString("video-sync", "display-resample")
            }
            mpv.setOptionString("framedrop", mpvCfg.frameDrop.key)
            mpv.setOptionString("vd-lavc-skiploopfilter", mpvCfg.skipLoopFilter.key)
            // Force CPU-side AV1 film-grain synthesis. The GPU film-grain path
            // (default on hwdec) stalls on several drivers — frames back up and
            // playback stutters even though the decoder is keeping up. This is
            // the documented workaround for https://github.com/mpv-player/mpv/issues/14651
            // and is what mpvkt sets unconditionally.
            mpv.setOptionString("vd-lavc-film-grain", "cpu")

            val demuxerMax = when (mpvCfg.demuxerMaxBytes) {
                MpvDemuxerMaxBytes.AUTO -> {
                    if (isLowRamDevice) DEMUXER_MAX_BYTES_LOW else DEMUXER_MAX_BYTES_NORMAL
                }
                else -> mpvCfg.demuxerMaxBytes.bytes
            }
            val demuxerMaxBack = when (mpvCfg.demuxerMaxBytes) {
                MpvDemuxerMaxBytes.AUTO -> {
                    if (isLowRamDevice) DEMUXER_MAX_BACK_BYTES_LOW else DEMUXER_MAX_BACK_BYTES_NORMAL
                }
                else -> mpvCfg.demuxerMaxBytes.bytes / 2
            }
            mpv.setOptionString("demuxer-max-bytes", demuxerMax.toString())
            mpv.setOptionString("demuxer-max-back-bytes", demuxerMaxBack.toString())

            // Debug builds surface libass/vo/demuxer trace messages so subtitle
            // render/decode issues (font-provider death, empty bitmaps) are
            // visible without recompiling; release keeps warn to stay quiet and
            // cheap. Mirrors mpvkt's per-build msg-level (all=v debug / all=warn
            // release). A debug-build sub/ass trace is what pinpointed the
            // "can't find selected font provider" → empty-bitmap subtitle bug.
            val msgLevel = if (com.raulshma.jellyplay.feature.player.video.BuildConfig.DEBUG) "all=v" else "all=warn"
            mpv.setOptionString("msg-level", msgLevel)

            // `fast` bundles vd-lavc-fast (skips some loop-filter / ref-frame
            // work) and cheap scaler defaults — a steady per-frame decode/render
            // saving mpvkt applies unconditionally. Previously gated to SW_ONLY
            // only, so the dominant HW path paid the full-quality decode cost
            // that ExoPlayer's MediaCodec pipeline never does.
            mpv.setOptionString("profile", "fast")
            if (currentConfig.decoderMode == DecoderMode.SW_ONLY && isLowRamDevice) {
                mpv.setOptionString("vf", "format=yuv420p")
            }

            if (currentConfig.audioPassthrough) {
                mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
            }

            // Tag the output stream so Android routes it correctly (movie role →
            // speaker, ignores notifications).
            mpv.setOptionString("audio-set-media-role", "yes")

            mpv.setOptionString(
                "audio-channels",
                channelMixModeToAudioChannels(
                    currentConfig.audioEffects.channelMixMode,
                    currentConfig.audioEffects.channelMixEnabled,
                ),
            )

            val afFilters = mutableListOf<String>()
            // Normalization filters (DYNAMIC compression / TRACK-ALBUM loudnorm).
            if (currentConfig.audioEffects.audioNormalizationEnabled) {
                audioNormalizationModeToAfFilter(currentConfig.audioEffects.audioNormalizationMode)?.let {
                    afFilters.add(it)
                }
            }
            // Dialogue-boost voice-band de-noise: cut sub-bass rumble below
            // the ~85 Hz voice fundamental. Mirrors the HighPassFilterAudioProcessor
            // stage on the ExoPlayer path. The EQ vocal-band lift is applied
            // separately via the EqualizerHelper overlay (no af filter needed).
            if (currentConfig.audioEffects.dialogueBoostEnabled) {
                afFilters.add("highpass=f=80")
            }
            if (afFilters.isNotEmpty()) {
                mpv.setOptionString("af", afFilters.joinToString(","))
            }

            // Free-form, user-authored options — applied LAST so a power user
            // can override any curated structured value above (intent: the user
            // is explicitly opting out of the app's default). One bad line must
            // not abort init, so each is applied in its own try/catch and the
            // failure is logged (unknown options / bad values throw an exception
            // from setOptionString). See MpvEngineConfig.mpvExtraConfig.
            val rawOptions = parseMpvConfigOptions(mpvCfg.mpvExtraConfig)
            for (option in rawOptions) {
                try {
                    mpv.setOptionString(option.key, option.value)
                } catch (e: Exception) {
                    Log.w(TAG, "mpv extra config: rejected '${option.key}=${option.value}' (${e.message})")
                }
            }
        }

        override fun postInitOptions() {
            mpv.addObserver(observer)
            mpv.addLogObserver(logObserver)
            mpv.observeProperty("pause", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("speed", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("paused-for-cache", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("eof-reached", MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty("time-pos", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("demuxer-cache-duration", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty("demuxer-cache-time", MPV.mpvFormat.MPV_FORMAT_INT64)
            mpv.observeProperty("sid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("aid", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("track-list", MPV.mpvFormat.MPV_FORMAT_NODE)
            mpv.observeProperty("sub-visibility", MPV.mpvFormat.MPV_FORMAT_FLAG)
            // G10: subtitle-sync preview for embedded subs. sub-text fires on
            // each displayed-line change; sub-start gives its media-time start.
            // Both together let us accumulate a TimedCue list as subs play.
            mpv.observeProperty("sub-text", MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty("sub-start", MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            assignAudioSessionId(mpv)
        }

        override fun observeProperties() {}

        fun removeObserver() {
            try { mpv.removeObserver(observer) } catch (_: Exception) {}
            try { mpv.removeLogObserver(logObserver) } catch (_: Exception) {}
        }
    }

    /**
     * Allocates a real Android audio session id and pushes it into mpv's
     * audiotrack / aaudio outputs so Android [android.media.audiofx.AudioEffect]
     * instances (dialogue boost, night mode) can bind to mpv's output.
     * Must run after [PlayerMPVView.initialize] has created the mpv handle
     * and before playback starts.
     */
    private fun assignAudioSessionId(mpv: MPV) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val sid = audioManager?.generateAudioSessionId() ?: AudioManager.ERROR
            if (sid == AudioManager.ERROR) {
                generatedAudioSessionId = 0
                return
            }
            generatedAudioSessionId = sid
            try { mpv.setPropertyInt("audiotrack-session-id", sid) } catch (_: Exception) {}
            try { mpv.setPropertyInt("aaudio-session-id", sid) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to assign MPV audio session id", e)
        }
    }

    /** Applies effects implemented through Android's per-session AudioEffect API. */
    private fun applyAndroidAudioEffects() {
        val sid = audioSessionId
        if (sid == 0) return
        val effects = currentConfig.audioEffects

        // Dialogue Boost and the equalizer share one system Equalizer instance.
        equalizerHelper.attach(sid)
        equalizerHelper.setEnabled(
            equalizerEnabled = effects.equalizerEnabled,
            dialogueBoostEnabled = effects.dialogueBoostEnabled,
        )
        dialogueBoost.attach(sid)
        dialogueBoost.setStrength(effects.dialogueBoostStrength)
        dialogueBoost.setEnabled(effects.dialogueBoostEnabled)

        nightMode.attach(sid)
        nightMode.setStrength(effects.nightModeStrength)
        nightMode.setEnabled(effects.nightModeEnabled)
    }

    override fun load(request: PlaybackRequest) {
        recreateEngineScopeIfInactive()
        // Engine may have been release()d and is being reused — clear the
        // teardown guard so observer callbacks are honoured again.
        released = false
        pendingRequest = request
        pendingSubtitles = request.externalSubtitles
        // Rebuild the side-loaded-subtitle id registry for the new item: each
        // external subtitle's label (the `title` arg passed to `sub-add`) maps
        // to its SubtitleSource.id, so buildTracks can stamp that stable id onto
        // the resulting MediaTrack.id instead of the synthetic mpv id. See
        // [sideLoadedSubtitleIds].
        sideLoadedSubtitleIds = request.externalSubtitles
            .filter { it.id.isNotBlank() }
            .associate { it.label to it.id }
        // Reset observer-driven caches for the new item. The first time-pos /
        // duration observations will repopulate these as the demuxer resolves.
        serverDurationMs = request.serverDurationMs
        cachedPositionMs = request.startPositionMs
        cachedDurationMs = 0L
        cachedBufferedPositionMs = 0L
        cachedSubStartSec = -1.0
        _currentCues.value = emptyList()
        _liveSubtitleCue.value = null
        Log.d(
            TAG,
            "MPV load requested: uri=${redactSensitive(request.uri)}, start=${request.startPositionMs}ms, " +
                "externalSubtitles=${request.externalSubtitles.size}, headers=${request.headers.keys}, " +
                "serverDurationMs=${request.serverDurationMs}"
        )

        mpvView?.let { view ->
            try {
                applyAndroidAudioEffects()
                configureMpvForRequest(view, request)
                view.playFile(request.uri)
                pendingRequest = null
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
                _errorFlow.tryEmit(EngineError.Source(httpStatus = null, cause = e))
            }
        }
    }

    override fun release() {
        // Mark torn-down first so any observer callback that races in during
        // the async native destroy bails at its `released` guard instead of
        // mutating state (e.g. a late `pause=false` would otherwise flip
        // _isPlaying back true on a half-destroyed engine).
        released = true
        pendingRequest = null
        pendingSubtitles = emptyList()
        sideLoadedSubtitleIds = emptyMap()
        // Note: there is no AudioManager.releaseAudioSessionId() —
        // Android's AudioSystem reclaims unreferenced session ids, so the
        // prior allocation via generateAudioSessionId() has no manual release.
        // Just drop our handle so the next load() allocates a fresh one.
        generatedAudioSessionId = 0
        mainHandler.removeCallbacksAndMessages(null)
        // Cancel the coalescer's pending debounce before the scope cancel so a
        // not-yet-fired buildTracks() can never race mpv_terminate_destroy.
        // Because buildTracks() now runs on the main thread (see refreshTracks),
        // any read that already started is guaranteed to finish before this
        // release() runs — the main looper is single-threaded.
        trackRefresh.cancel()
        dialogueBoost.detach()
        equalizerHelper.detach()
        nightMode.detach()
        engineScope.cancel()
        val view = mpvView
        mpvView = null
        view?.let {
            // Native teardown (stop + mpv_terminate_destroy) is synchronous and
            // can block for hundreds of ms. Run it on the dedicated release
            // thread so the main looper (which invoked release() from the
            // Compose onDispose) stays responsive. Safe because: scope is
            // cancelled, observers removed, mpvView already nulled — this is
            // the final operation on the handle.
            it.removeObserver()
            releaseHandler.post {
                try { it.mpv.command("stop") } catch (_: Exception) {}
                try { it.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
            }
        }
        _playbackState.value = EnginePlaybackState.IDLE
        _isPlaying.value = false
        _availableTracks.value = emptyList()
        _bufferedPositionMs.value = 0L
        _videoStats.value = EngineVideoStats()
        _currentCues.value = emptyList()
        _liveSubtitleCue.value = null
        cachedPositionMs = 0L
        cachedDurationMs = 0L
        cachedBufferedPositionMs = 0L
        cachedSubStartSec = -1.0
        serverDurationMs = 0L
        // Recreate the scope so a re-used engine stays usable without waiting
        // for the next load(). A cancelled scope silently swallows new
        // launches (no-ops), which would otherwise lose the position ticker.
        recreateEngineScopeIfInactive()
        // Stop the dedicated release thread once the engine is fully
        // torn down. The last scheduled runnable has already captured `view`
        // and will run to completion, but no new work can be enqueued because
        // mpvView is null. Lazy re-init resurrects the thread if the engine
        // is ever re-used.
        if (releaseThread.isAlive) {
            runCatching { releaseThread.quitSafely() }
        }
    }

    override fun play() {
        try {
            if (_playbackState.value == EnginePlaybackState.ENDED) {
                mpvView?.mpv?.command("seek", "0", "absolute")
            }
            mpvView?.mpv?.setPropertyBoolean("pause", false)
        } catch (e: Exception) { Log.w(TAG, "play failed", e) }
    }

    override fun pause() {
        try { mpvView?.mpv?.setPropertyBoolean("pause", true) } catch (e: Exception) { Log.w(TAG, "pause failed", e) }
    }

    override fun stop() {
        try {
            mpvView?.mpv?.command("stop")
        } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
    }

    override fun seekTo(positionMs: Long) {
        try {
            // Fixed-precision seconds with 6 decimals (byte-identical to
            // "%.6f".format for positionMs >= 0; MPV clamps anyway). Avoids the
            // Formatter + StringBuilder allocation per seek, which fires many
            // times/sec during scrub / gesture-seek.
            val secsStr = formatFixed(positionMs / 1000.0, 6)
            mpvView?.mpv?.command("seek", secsStr, "absolute")
        } catch (e: Exception) { Log.w(TAG, "seekTo failed", e) }
    }

    override fun setPlaybackSpeed(speed: Float) {
        try { mpvView?.mpv?.setPropertyDouble("speed", speed.toDouble()) } catch (e: Exception) { Log.w(TAG, "setPlaybackSpeed failed", e) }
    }

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        val mpvCfg = (newConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()

        try {
            val mpv = mpvView?.mpv ?: return

            if (oldConfig.audioDelayMs != newConfig.audioDelayMs) {
                mpv.setPropertyDouble("audio-delay", newConfig.audioDelayMs / 1000.0)
            }
            if (oldConfig.subtitleDelayMs != newConfig.subtitleDelayMs) {
                mpv.setPropertyDouble("sub-delay", newConfig.subtitleDelayMs / 1000.0)
            }

            if (oldConfig.decoderMode != newConfig.decoderMode || (oldConfig.engineSpecific as? MpvEngineConfig)?.hwdecOverride != mpvCfg.hwdecOverride) {
                val hwdecValue = mpvCfg.hwdecOverride?.key ?: decoderModeToHwdec(newConfig.decoderMode)
                mpv.setPropertyString("hwdec", hwdecValue)
            }

            if (oldConfig.audioPassthrough != newConfig.audioPassthrough) {
                if (newConfig.audioPassthrough) {
                    mpv.setOptionString("audio-spdif", "ac3,eac3,dts,dtshd,truehd")
                } else {
                    mpv.setOptionString("audio-spdif", "")
                }
            }

            val oldMpvCfg = oldConfig.engineSpecific as? MpvEngineConfig
            if (oldMpvCfg?.audioOutput != mpvCfg.audioOutput || oldMpvCfg?.audioFallback != mpvCfg.audioFallback) {
                val aoValue = buildString {
                    append(mpvCfg.audioOutput.key)
                    mpvCfg.audioFallback?.let { append(",").append(it.key) }
                }
                mpv.setPropertyString("ao", aoValue)
            }

            if (oldMpvCfg?.scaler != mpvCfg.scaler) {
                mpv.setPropertyString("scale", mpvCfg.scaler.key)
                // dscale mirrors the upscaler at init (see initOptions), but at
                // runtime we leave mpv's default downscaler — only the upscaler
                // changes here.
            }
            if (oldMpvCfg?.deband != mpvCfg.deband) {
                mpv.setPropertyString("deband", if (mpvCfg.deband) "yes" else "no")
            }
            if (oldMpvCfg?.interpolation != mpvCfg.interpolation) {
                mpv.setPropertyString("interpolation", if (mpvCfg.interpolation) "yes" else "no")
                if (mpvCfg.interpolation) {
                    mpv.setPropertyString("video-sync", "display-resample")
                }
            }
            if (oldMpvCfg?.frameDrop != mpvCfg.frameDrop) {
                mpv.setPropertyString("framedrop", mpvCfg.frameDrop.key)
            }
            if (oldMpvCfg?.skipLoopFilter != mpvCfg.skipLoopFilter) {
                mpv.setPropertyString("vd-lavc-skiploopfilter", mpvCfg.skipLoopFilter.key)
            }

            if (oldConfig.subtitleStyle != newConfig.subtitleStyle) {
                applySubtitleStyleInternal(newConfig.subtitleStyle)
            }

            if (oldConfig.audioEffects.channelMixMode != newConfig.audioEffects.channelMixMode ||
                oldConfig.audioEffects.channelMixEnabled != newConfig.audioEffects.channelMixEnabled
            ) {
                mpv.setPropertyString(
                    "audio-channels",
                    channelMixModeToAudioChannels(
                        newConfig.audioEffects.channelMixMode,
                        newConfig.audioEffects.channelMixEnabled,
                    ),
                )
            }

            val oldAudioFx = oldConfig.audioEffects
            val newAudioFx = newConfig.audioEffects
            // Rebuild the af chain when normalization OR dialogue-boost changes,
            // since dialogue boost contributes a highpass stage to the chain.
            if (oldAudioFx.audioNormalizationEnabled != newAudioFx.audioNormalizationEnabled ||
                oldAudioFx.audioNormalizationMode != newAudioFx.audioNormalizationMode ||
                oldAudioFx.dialogueBoostEnabled != newAudioFx.dialogueBoostEnabled
            ) {
                val afFilters = mutableListOf<String>()
                if (newAudioFx.audioNormalizationEnabled) {
                    audioNormalizationModeToAfFilter(newAudioFx.audioNormalizationMode)?.let {
                        afFilters.add(it)
                    }
                }
                // Dialogue-boost rumble cut (mirrors ExoPlayer HighPassFilterAudioProcessor).
                if (newAudioFx.dialogueBoostEnabled) {
                    afFilters.add("highpass=f=80")
                }
                val filterString = afFilters.joinToString(",")
                if (filterString.isNotEmpty()) {
                    mpv.setPropertyString("af", filterString)
                } else {
                    mpv.command("af", "clr", "")
                }
            }

            if (oldConfig.videoEffects != newConfig.videoEffects) {
                applyVideoFilters(newConfig.videoEffects)
            }

            if (oldAudioFx.dialogueBoostStrength != newAudioFx.dialogueBoostStrength ||
                oldAudioFx.dialogueBoostEnabled != newAudioFx.dialogueBoostEnabled ||
                oldAudioFx.nightModeStrength != newAudioFx.nightModeStrength ||
                oldAudioFx.nightModeEnabled != newAudioFx.nightModeEnabled ||
                oldAudioFx.equalizerEnabled != newAudioFx.equalizerEnabled
            ) {
                applyAndroidAudioEffects()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reconfigure MPV audio effects", e)
        }
    }

    private fun applyVideoFilters(effects: VideoEffectsConfig) {
        try {
            val filters = mutableListOf<String>()
            val hasBrightness = effects.brightness != 0f
            val hasContrast = effects.contrast != 1f
            val hasSaturation = effects.saturation != 1f
            val hasHue = effects.hue != 0f
            val hasRgbGain = effects.redGain != 1f || effects.greenGain != 1f || effects.blueGain != 1f
            if (hasBrightness || hasContrast || hasSaturation || hasHue || hasRgbGain) {
                val eqParts = mutableListOf<String>()
                if (hasBrightness) eqParts.add("brightness=${effects.brightness}")
                if (hasContrast) eqParts.add("contrast=${effects.contrast}")
                if (hasSaturation) eqParts.add("saturation=${effects.saturation}")
                if (hasHue) eqParts.add("hue=${effects.hue}")
                if (effects.redGain != 1f) eqParts.add("gamma_r=${effects.redGain}")
                if (effects.greenGain != 1f) eqParts.add("gamma_g=${effects.greenGain}")
                if (effects.blueGain != 1f) eqParts.add("gamma_b=${effects.blueGain}")
                filters.add("eq=${eqParts.joinToString(":")}")
            }
            if (effects.sharpness > 0f) {
                filters.add("unsharp=5:5:${(effects.sharpness * 1.5f).coerceIn(0.5f, 3.0f)}")
            }
            if (effects.gaussianBlur > 0f) {
                // lavfi gblur sigma ~ half the user value to keep 0..10 range sensible
                filters.add("lavfi=[gblur=sigma=${effects.gaussianBlur / 2f}]")
            }
            val rawDiscrete = kotlin.math.round(effects.rotationDegrees / 90f).toInt() * 90
            val discrete = ((rawDiscrete % 360) + 360) % 360
            mpvView?.mpv?.setPropertyDouble("video-rotate", discrete.toDouble())

            if (filters.isNotEmpty()) {
                mpvView?.mpv?.setPropertyString("vf", filters.joinToString(","))
            } else {
                mpvView?.mpv?.command("vf", "clr", "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply MPV video filters", e)
        }
    }

    override fun selectTrack(type: TrackType, index: Int) {
        try {
            val m = mpvView?.mpv ?: return
            if (type == TrackType.AUDIO) {
                Log.d(TAG, "Selecting MPV audio track id=$index")
                if (index < 0) {
                    m.setPropertyString("aid", "auto")
                } else {
                    try {
                        m.setPropertyInt("aid", index)
                    } catch (_: Exception) {
                        m.setPropertyString("aid", "$index")
                    }
                }
            } else {
                Log.d(TAG, "Selecting MPV subtitle track id=$index")
                if (index < 0) {
                    m.setPropertyString("sid", "no")
                } else {
                    try {
                        m.setPropertyInt("sid", index)
                    } catch (_: Exception) {
                        m.setPropertyString("sid", "$index")
                    }
                    m.setPropertyBoolean("sub-visibility", true)
                }
            }
            refreshTracks("select-${type.name.lowercase()}")
            logSubtitleRenderState("select-${type.name.lowercase()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select MPV ${type.name.lowercase()} track id=$index", e)
        }
    }

    /**
     * Selects a secondary subtitle track rendered alongside the primary (G4).
     * mpv supports this natively via `secondary-sid`; the secondary track renders
     * above the primary by default. An [index] < 0 clears it ("no").
     */
    override fun setSecondarySubtitleTrack(index: Int) {
        try {
            val m = mpvView?.mpv ?: return
            Log.d(TAG, "Selecting MPV secondary subtitle track id=$index")
            if (index < 0) {
                m.setPropertyString("secondary-sid", "no")
            } else {
                try {
                    m.setPropertyInt("secondary-sid", index)
                } catch (_: Exception) {
                    m.setPropertyString("secondary-sid", "$index")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select MPV secondary subtitle track id=$index", e)
        }
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // Intentional no-op. MPV plays single-URL streams (not adaptive
        // manifests), so there is no variant ladder to cap — the only lever
        // would be requesting a transcode from the server, which the
        // ViewModel already negotiates via PlaybackRepository before load().
    }

    override val volume: Float
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble("volume") ?: 100.0) / 100.0).toFloat().coerceIn(0f, 1f)
        } catch (_: Exception) { 1f }

    override fun setVolume(value: Float) {
        try {
            val clamped = clamp01(value)
            rememberUnmuteVolumeIfAudible(clamped)
            mpvView?.mpv?.setPropertyDouble("volume", (clamped * 100.0).coerceIn(0.0, 200.0))
            MediaStreamVolume.setNormalized(context, clamped)
        } catch (_: Exception) {}
    }

    override fun increaseVolume(delta: Float) {
        try {
            val m = mpvView?.mpv ?: return
            val current = m.getPropertyDouble("volume") ?: 100.0
            val next = (current + delta * 100.0).coerceIn(0.0, 200.0)
            m.setPropertyDouble("volume", next)
            val next01 = (next / 100.0).toFloat().coerceIn(0f, 1f)
            rememberUnmuteVolumeIfAudible(next01)
            MediaStreamVolume.setNormalized(context, next01)
        } catch (_: Exception) {}
    }

    override fun decreaseVolume(delta: Float) {
        try {
            val m = mpvView?.mpv ?: return
            val current = m.getPropertyDouble("volume") ?: 100.0
            val next = (current - delta * 100.0).coerceAtLeast(0.0)
            m.setPropertyDouble("volume", next)
            val next01 = (next / 100.0).toFloat().coerceIn(0f, 1f)
            rememberUnmuteVolumeIfAudible(next01)
            MediaStreamVolume.setNormalized(context, next01)
        } catch (_: Exception) {}
    }

    override fun setMuted(muted: Boolean) {
        try { mpvView?.mpv?.setPropertyBoolean("mute", muted) } catch (_: Exception) {}
        try {
            if (muted) {
                snapshotSystemVolumeForMute()
                MediaStreamVolume.setNormalized(context, 0f)
            } else {
                MediaStreamVolume.setNormalized(context, unmuteTarget())
            }
        } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
        val fontsDir = fontProvider.provideFontsDir()
        val configDir = java.io.File(context.filesDir, "mpv")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        // NOTE: do NOT set FONTCONFIG_FILE / FONTCONFIG_PATH. Pointing fontconfig
        // at a minimal app-written fonts.conf breaks libass's font-provider init
        // on some devices (e.g. Adreno 509 / Nokia 6.1 Plus: "can't find selected
        // font provider"), which makes every subtitle rasterize to an empty
        // bitmap. libass uses the system fontconfig by default, which resolves
        // the ASS/SRT font families against /system/fonts; the bundled fallback
        // is still picked up via sub-fonts-dir above. This matches mpvkt, which
        // sets neither env var.

        val view = try {
            PlayerMPVView(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PlayerMPVView", e)
            return View(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        }

        try {
            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()
            view.initialize(configDir.absolutePath, context.cacheDir.absolutePath)
            view.setVo(mpvCfg.videoOutput.key)
            applySubtitleStyleInternal(currentConfig.subtitleStyle)
        } catch (e: Exception) {
            Log.e(TAG, "MPV initialize failed", e)
            return view
        }
        // Publish only after initialize() succeeded — otherwise every later
        // op on the engine throws repeatedly against a half-initialized view.
        mpvView = view
        // postInitOptions has now allocated the audio session on the concrete
        // MPV handle. Bind the Android effects before the first file starts so
        // persisted dialogue boost/night mode settings are audible immediately.
        applyAndroidAudioEffects()

        pendingRequest?.let { request ->
            pendingRequest = null
            try {
                configureMpvForRequest(view, request)
                view.playFile(request.uri)
            } catch (e: Exception) {
                Log.e(TAG, "playFile failed", e)
            }
        }

        return view
    }

    override fun applySubtitleStyle(style: SubtitleStyle) {
        // mpv applies styles via properties, not via a View.
        applySubtitleStyleInternal(style)
    }
    
    private fun applySubtitleStyleInternal(style: SubtitleStyle) {
        try {
            val m = mpvView?.mpv ?: return
            applySubtitleStyleProperties(m, style)
            runCatching { m.command("sub-reload") }
            logSubtitleRenderState("style")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply MPV subtitle style", e)
        }
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        val numeric = ratio.ratio
        val aspectValue = when {
            numeric != null && numeric > 0f -> {
                val w = (numeric * 100).toInt()
                val h = 100
                val gcd = gcd(w, h)
                "${w / gcd}:${h / gcd}"
            }
            else -> "-1"
        }
        val m = mpvView?.mpv ?: return
        try { m.setPropertyString("video-aspect-override", aspectValue) } catch (_: Exception) {}

        val isZoom = ratio == AspectRatio.CROP
        try {
            m.setPropertyDouble("panscan", if (isZoom) 1.0 else 0.0)
            m.setPropertyString("sub-use-margins", if (isZoom) "yes" else "no")
            m.setPropertyString("sub-ass-force-margins", if (isZoom) "yes" else "no")
        } catch (_: Exception) {}
    }

    /**
     * Toggles mpv's native subtitle rendering via the live `sub-visibility`
     * property. The screen hides native subs (`visible = false`) while it
     * renders the zoom-safe Compose overlay from [liveSubtitleCue], so captions
     * aren't double-drawn, and restores them (`visible = true`) the moment zoom
     * returns to 1 (full libass fidelity). Cheap, reversible, and already an
     * observed property, so the toggle is consistent with mpv's own state.
     */
    override fun setNativeSubtitlesVisible(visible: Boolean) {
        val m = mpvView?.mpv ?: return
        try {
            m.setPropertyString("sub-visibility", if (visible) "yes" else "no")
        } catch (e: Exception) {
            Log.w(TAG, "setNativeSubtitlesVisible($visible) failed", e)
        }
    }

    override val currentPositionMs: Long
        get() = cachedPositionMs

    /**
     * G10: folds a newly-displayed subtitle line (mpv `sub-text`) into the
     * accumulated cue list via [mergeAccumulatedCues], so the subtitle-sync
     * preview can render prev/active/next for embedded subs without re-fetching
     * bytes. The start time comes from mpv's `sub-start` (cached on each
     * `sub-start` emission); when mpv hasn't reported one yet we fall back to
     * the current playback position. mpv fires `sub-text` only on a line
     * *change*, and may emit an empty string when the line clears — ignored.
     * Covers the played range only (no ahead-lookahead for forward offsets).
     */
    private fun accumulateMpvSubText(text: String) {
        if (text.isBlank()) return
        val startSec = if (cachedSubStartSec >= 0) cachedSubStartSec else cachedPositionMs / 1000.0
        val startUs = (startSec * 1_000_000L).toLong()
        val incoming = listOf(TimedCue(startUs, Long.MAX_VALUE, text))
        _currentCues.value = mergeAccumulatedCues(_currentCues.value, incoming)
    }

    override val durationMs: Long
        get() {
            // Prefer the mpv demuxer's duration when available; fall back to
            // the server-reported runTimeTicks, which for HLS/transcoded
            // streams is the only accurate total-runtime source (mpv's
            // `duration` property is frequently 0 or only partially resolved
            // for a transcoded manifest).
            val engine = cachedDurationMs
            return if (engine > 0L) engine else serverDurationMs
        }

    override val playbackSpeed: Float
        get() = try {
            mpvView?.mpv?.getPropertyDouble("speed")?.toFloat() ?: 1f
        } catch (_: Exception) { 1f }

    override val audioSessionId: Int
        get() = generatedAudioSessionId

    override val positionFlow: Flow<Long> = positionFlowWithTicker {
        // Push the observer-cached position downstream. No JNI here:
        // time-pos / demuxer-cache-duration / duration are observed properties whose
        // callbacks populate the cached fields. This is the hot path (fires every poll)
        // so keeping it allocation- and JNI-free eliminates the primary source of
        // main-thread jank during mpv playback.
        val posMs = cachedPositionMs
        val dur = durationMs
        if (dur > 0L) {
            _bufferedPositionMs.value = cachedBufferedPositionMs.coerceAtMost(dur)
        } else {
            _bufferedPositionMs.value = cachedBufferedPositionMs
        }
        if (_videoStatsEnabled.value) {
            // Stats require ~12 property reads. Run them on the MAIN thread (the
            // ticker's onActive already runs on engineScope = Dispatchers.Main) to
            // serialise against mpv_terminate_destroy.
            updateVideoStatsOnly(posMs)
        }
    }

    private fun updateVideoStatsOnly(posMs: Long) {
        val m = mpvView?.mpv ?: return
        try {
            val videoBitrateBps = try {
                m.getPropertyDouble("video-bitrate")?.let { br ->
                    if (br > 0) br.toInt() else null
                }
            } catch (_: Exception) { null }
            val audioBitrateBps = try {
                m.getPropertyDouble("audio-bitrate")?.let { br ->
                    if (br > 0) br.toInt() else null
                }
            } catch (_: Exception) { null }
            val combinedBitrate = (videoBitrateBps ?: 0) + (audioBitrateBps ?: 0)
            val bufferHealthMs = (_bufferedPositionMs.value - posMs).coerceAtLeast(0L)
            val bufferSizeBytes = if (combinedBitrate > 0) combinedBitrate * bufferHealthMs / 8000 else 0L
            val newStats = EngineVideoStats(
                videoCodec = try { m.getPropertyString("video-format") } catch (_: Exception) { null },
                videoDecoder = try { m.getPropertyString("hwdec-current") } catch (_: Exception) { null },
                videoResolution = buildString {
                    val w = try { m.getPropertyInt("width") } catch (_: Exception) { null }
                    val h = try { m.getPropertyInt("height") } catch (_: Exception) { null }
                    if (w != null && h != null && w > 0 && h > 0) append("${w}x${h}")
                }.ifEmpty { null },
                videoFrameRate = try {
                    m.getPropertyDouble("container-fps")?.let { fps ->
                        if (fps > 0f) fps.toFloat() else null
                    }
                } catch (_: Exception) { null },
                videoBitrate = videoBitrateBps,
                audioCodec = try { m.getPropertyString("audio-codec") } catch (_: Exception) { null },
                audioSampleRate = try {
                    m.getPropertyInt("audio-params/samplerate")?.let { sr ->
                        if (sr > 0) sr else null
                    }
                } catch (_: Exception) { null },
                audioChannels = try {
                    m.getPropertyInt("audio-params/channel-count")?.let { ch ->
                        if (ch > 0) ch else null
                    }
                } catch (_: Exception) { null },
                audioBitrate = audioBitrateBps,
                estimatedBandwidthBps = combinedBitrate.toLong(),
                droppedFrames = try {
                    m.getPropertyInt("decoder-frame-drop-count")?.toLong() ?: 0L
                } catch (_: Exception) { 0L },
                totalVideoFrames = try {
                    m.getPropertyInt("displayed-frame-count")?.toLong() ?: 0L
                } catch (_: Exception) { 0L },
                bufferedPositionMs = _bufferedPositionMs.value,
                bufferSizeBytes = bufferSizeBytes,
                avsyncMs = m.propDoubleOrNull("total-avsync")?.let { if (it != 0f) it else null },
                displayFps = m.propDoubleOrNull("display-fps")?.let { fps -> if (fps > 0f) fps else null },
                voDelayedMs = m.propDoubleOrNull("vo-delayed")?.let { if (it != 0f) it else null },
                voFrameDropCount = m.propIntOrNull("frame-drop-count")?.toLong(),
            )
            publishStatsIfChanged(newStats)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read MPV video stats", e)
        }
    }

    /**
     * Read an mpv double property, returning null if the property is unset or
     * the read throws (mpv raises on unknown/unavailable properties). Collapses
     * the repeated `try { m.getPropertyDouble(...) } catch { null }` shape that
     * the four G10 stats each carried inline.
     */
    private fun MPV.propDoubleOrNull(name: String): Float? = try {
        getPropertyDouble(name)?.toFloat()
    } catch (_: Exception) {
        null
    }

    /** [propDoubleOrNull] for integer properties. */
    private fun MPV.propIntOrNull(name: String): Int? = try {
        getPropertyInt(name)
    } catch (_: Exception) {
        null
    }

    private fun buildTracks(): List<MediaTrack> {
        val m = mpvView?.mpv ?: return emptyList()
        val trackList = try {
            m.getPropertyNode("track-list")?.asArray()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val result = mutableListOf<MediaTrack>()
        for (node in trackList) {
            val track = node.asMap() ?: continue
            val t = track["type"]?.asString() ?: continue
            val trackType = when (t) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> continue
            }
            val id = track["id"].asTrackId() ?: continue
            val lang = track["lang"]?.asString()
            val title = track["title"]?.asString()
            val codec = track["codec"]?.asString()
            val selected = track["selected"]?.asBoolean() ?: false
            // `external` is true for sub-add'd (side-loaded) tracks and absent/
            // false for container-demuxed tracks. Gates the side-loaded id
            // lookup below so a demuxed track that happens to share a label
            // with a sidecar never inherits the sidecar's stable id.
            val isExternal = track["external"]?.asBoolean() ?: false
            // ff-index is the demuxer/container stream index — present for
            // container-demuxed tracks (== the server's MediaStream.index), null
            // for side-loaded (sub-add) tracks. Used as the robust resolution key
            // in TrackSelectionHelper instead of fragile label matching.
            val ffIndex = track["ff-index"].asTrackId()
            // mpv exposes forced/default flags per track; "forced" subs are
            // marked and default-track mirrors the container's default flag.
            val isForced = track["forced"]?.asBoolean() ?: false
            val isDefault = track["default"]?.asBoolean() ?: false
            val info = TrackLabelInfo(
                title = title,
                language = lang,
                codec = codec,
                isForced = isForced,
                isDefault = isDefault,
            )

            // For side-loaded subtitles, prefer the caller-supplied
            // SubtitleSource.id (looked up by the track's title, which is the
            // label passed to sub-add) over the synthetic mpv id — mirroring
            // ExoPlayer, which propagates the SubtitleConfiguration id into the
            // track format. The offline-subtitle restore path
            // (TrackSelectionPolicy.resolveByOfflineSubtitleId) keys on this id.
            // Demuxed tracks and side-loaded tracks without a registered id fall
            // through to the synthetic `"mpv_${t}_${id}"`.
            val resolvedId = if (trackType == TrackType.SUBTITLE && isExternal) {
                title?.let { sideLoadedSubtitleIds[it] }
            } else {
                null
            }

            result.add(
                MediaTrack(
                    id = resolvedId ?: "mpv_${t}_${id}",
                    index = id,
                    label = TrackLabelFormatter.primary(info),
                    language = lang,
                    isSelected = selected,
                    type = trackType,
                    streamIndex = ffIndex,
                    badges = TrackLabelFormatter.badges(info),
                )
            )
        }
        return result
    }

    /**
     * Handle an [MPV.mpvEvent.MPV_EVENT_END_FILE] event. The mpv END_FILE event
     * node carries `reason` (and `error` for failures). See the mpv docs for
     * `mpv_event_end_file.reason` values:
     *
     * - `MPV_END_FILE_REASON_EOF` (0): the file ended naturally. With
     *   `keep-open=yes` this should rarely arrive (mpv keeps the file open and
     *   flips `eof-reached`), but we treat it as completion for safety.
     * - `MPV_END_FILE_REASON_STOP` (1): a stop command (including our own from
     *   [release]) — ignore.
     * - `MPV_END_FILE_REASON_QUIT` (2): mpv is quitting — ignore; teardown
     *   happens in [release].
     * - `MPV_END_FILE_REASON_ERROR` (3): demuxer/decode/network error — surface
     *   via [errorFlow] so the UI shows the playback-error dialog, but do NOT
     *   mark the item as completed (it didn't finish).
     * - `MPV_END_FILE_REASON_REDIRECT` (4): HLS variant / playlist redirect —
     *   mpv follows these automatically, so treat as a transient buffer rather
     *   than completion.
     *
     * The old code unconditionally set ENDED on every END_FILE, which — for
     * transcoded HLS streams where the server closes the session, drops the
     * connection, or returns a redirect — stopped playback after a few seconds.
     */
    private fun handleEndFile(data: MPVNode) {
        val map = try { data.asMap() } catch (_: Exception) { null }
        val reason = map?.let { it["reason"]?.asInt()?.toInt() }
        val errorCode = map?.let { it["error"]?.asString() }
        Log.d(TAG, "MPV end file: reason=$reason, error=${errorCode ?: "none"}")
        when (reason) {
            MPV_END_FILE_REASON_EOF -> {
                // Defensive: keep-open normally prevents this path, but if the
                // option was overridden, EOF is genuine completion.
                _playbackState.value = EnginePlaybackState.ENDED
            }
            MPV_END_FILE_REASON_REDIRECT -> {
                // mpv is following a playlist/redirect — treat as transient.
                _playbackState.value = EnginePlaybackState.BUFFERING
            }
            MPV_END_FILE_REASON_ERROR -> {
                _playbackState.value = EnginePlaybackState.ERROR
                _errorFlow.tryEmit(mapMpvError(errorCode))
            }
            // STOP / QUIT / null — ignore: not end-of-content.
            else -> {}
        }
    }

    /**
     * Map an mpv END_FILE `error` string onto the [EngineError] taxonomy so the
     * UI can offer the right affordance. The node carries an `mpv_error` int,
     * but the binding exposes it as a string; [mapMpvError] tolerates both the
     * numeric form ("-13") and a descriptive string (e.g. "loading_failed",
     * "ao_init_failed", or a raw network message).
     *
     * Mirrors ExoPlayer's [PlaybackException.toEngineError]: a load/source
     * failure maps to a retryable [EngineError.Network] (transient mpv network
     * drops, HTTP timeouts, server-closed transcodes), while decoder/init/format
     * failures map to [EngineError.Decoder] (not retryable on the same engine).
     * Unknown errors stay non-retryable [EngineError.Unknown].
     */
    private fun mapMpvError(errorCode: String?): EngineError {
        if (errorCode.isNullOrBlank()) return EngineError.Unknown("Playback error (mpv): unknown")
        val raw = "Playback error (mpv): $errorCode"
        val numeric = errorCode.toIntOrNull()
        val textual = errorCode.lowercase()
        // Numeric mpv_error path — the documented END_FILE contract.
        if (numeric != null) {
            return when (numeric) {
                // Load / source failures — transient, retryable.
                MPV_ERROR_LOADING_FAILED -> EngineError.Network(null)
                // Decoder / output / format init — fatal on same engine.
                MPV_ERROR_AO_INIT_FAILED,
                MPV_ERROR_VO_INIT_FAILED,
                MPV_ERROR_NOTHING_TO_PLAY,
                MPV_ERROR_UNKNOWN_FORMAT,
                MPV_ERROR_UNSUPPORTED,
                MPV_ERROR_NOT_IMPLEMENTED,
                -> EngineError.Decoder(codec = null, cause = null)
                else -> EngineError.Unknown(raw)
            }
        }
        // Descriptive-string fallback: some bindings surface the error name or
        // a network message rather than the numeric code. Match keywords so we
        // still recover the retry affordance for transient drops.
        return when {
            "loading_failed" in textual ||
                "network" in textual ||
                "connection" in textual ||
                "timeout" in textual ||
                "protocol" in textual ||
                "http" in textual ||
                "stream" in textual -> EngineError.Network(null)
            "ao_init" in textual ||
                "vo_init" in textual ||
                "format" in textual ||
                "unsupported" in textual ||
                "decoder" in textual ||
                "codec" in textual -> EngineError.Decoder(codec = null, cause = null)
            else -> EngineError.Unknown(raw)
        }
    }

    private fun configureMpvForRequest(view: PlayerMPVView, request: PlaybackRequest) {
        if (request.startPositionMs > 0) {
            val startVal = "+${request.startPositionMs / 1000.0}"
            try { view.mpv.setOptionString("start", startVal) } catch (_: Exception) {}
            try { view.mpv.setPropertyString("start", startVal) } catch (_: Exception) {}
        }

        view.mpv.setOptionString("sub-visibility", "yes")
        view.mpv.setPropertyBoolean("sub-visibility", true)
        request.preferredAudioLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString("alang", normalizeLanguageList(language))
        }
        request.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString("slang", normalizeLanguageList(language))
        }

        if (request.headers.isNotEmpty()) {
            // mpv handles User-Agent as its own property (it drives the default
            // UA for all requests, including the one mpv sends for stream
            // probing). Pull it out so it lands in `user-agent` rather than
            // being buried in http-header-fields, which some servers parse
            // inconsistently. Remaining headers stay in http-header-fields,
            // comma-joined per mpv's documented format.
            val userAgent = request.headers.entries
                .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                ?.value
            if (!userAgent.isNullOrBlank()) {
                try { view.mpv.setOptionString("user-agent", userAgent) } catch (_: Exception) {}
                try { view.mpv.setPropertyString("user-agent", userAgent) } catch (_: Exception) {}
            }
            val headerStr = request.headers.entries
                .filter { !it.key.equals("User-Agent", ignoreCase = true) }
                .joinToString(",") { "${it.key}: ${it.value}" }
            if (headerStr.isNotBlank()) {
                try { view.mpv.setOptionString("http-header-fields", headerStr) } catch (_: Exception) {}
                try { view.mpv.setPropertyString("http-header-fields", headerStr) } catch (_: Exception) {}
            }
            Log.d(TAG, "Applied MPV HTTP headers: ${request.headers.keys}")
        }

        try {
            applySubtitleStyleProperties(view.mpv, currentConfig.subtitleStyle)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply subtitle style inside configureMpvForRequest", e)
        }
    }

    /**
     * Labels of every subtitle currently in mpv's track-list (demuxed + sub-add'd).
     * Used to dedup sub-add calls — matching on label is robust because it is the
     * exact `title` arg passed to `sub-add`. Best-effort: returns an empty set on
     * any track-list read failure so the caller proceeds to add.
     */
    private fun existingSubLabels(): Set<String> = try {
        buildTracks()
            .asSequence()
            .filter { it.type == TrackType.SUBTITLE }
            .mapNotNull { it.label }
            .toSet()
    } catch (_: Exception) {
        emptySet()
    }

    /**
     * Label-uniquify + id-register core shared by the two side-load gates
     * below ([dedupeRuntimeSideLoad] / [dedupePendingSideLoad]).
     *
     * The returned label is both the `title` passed to sub-add AND the
     * registry key buildTracks looks up, so they stay in lockstep by
     * construction. Same-label-but-different-source subs are NOT skipped:
     * their label is uniquified against [usedLabels] ("Label (2)", ...) —
     * skipping them made the second sidecar of a same-titled pair permanently
     * unselectable (it never entered the track-list, so neither its
     * side-loaded id nor any other resolution key existed).
     *
     * On success the caller-supplied id (when present) is registered in
     * [sideLoadedSubtitleIds] so buildTracks can stamp it onto the resulting
     * MediaTrack.id instead of the synthetic mpv id — mirroring ExoPlayer's
     * SubtitleConfiguration.id propagation.
     */
    private fun registerSideLoadedLabel(source: SubtitleSource, usedLabels: MutableSet<String>): String {
        var label = source.label.ifBlank { "External subtitle" }
        if (label in usedLabels) {
            var n = 2
            while ("$label ($n)" in usedLabels) n++
            label = "$label ($n)"
        }
        usedLabels += label
        if (source.id.isNotBlank()) {
            sideLoadedSubtitleIds = sideLoadedSubtitleIds + (label to source.id)
        }
        return label
    }

    /**
     * True when a source with [source]'s label is already in the tracked
     * track-list ([usedLabels]) — a true duplicate to skip, logged here.
     * Shared by both side-load gates; same-label-but-different-source subs
     * that arrive through other paths are uniquified by
     * [registerSideLoadedLabel] instead of skipped.
     */
    private fun skipDuplicateByLabel(source: SubtitleSource, usedLabels: Set<String>): Boolean {
        if (source.label !in usedLabels) return false
        Log.d(TAG, "Skipping duplicate subtitle (already in track-list): label=${source.label}")
        return true
    }

    /**
     * Runtime side-load gate ([addExternalSubtitle]): skips true re-adds — by
     * registered id when the source supplies one, by live track-list label
     * otherwise (the id check can't fire for legacy id-less sources) — then
     * delegates to [registerSideLoadedLabel].
     */
    private fun dedupeRuntimeSideLoad(source: SubtitleSource, usedLabels: MutableSet<String>): String? {
        if (source.id.isNotBlank()) {
            if (sideLoadedSubtitleIds.containsValue(source.id)) {
                Log.d(TAG, "Skipping duplicate subtitle (id already registered): id=${source.id}")
                return null
            }
        } else if (skipDuplicateByLabel(source, usedLabels)) {
            return null
        }
        return registerSideLoadedLabel(source, usedLabels)
    }

    /**
     * Load-time batch gate ([addPendingSubtitles]). load() pre-seeds
     * [sideLoadedSubtitleIds] from request.externalSubtitles so buildTracks can
     * stamp ids before sub-add runs — an id-based skip here would drop EVERY
     * batch entry. The authoritative guard is therefore the live track-list
     * label check only (the file just started; its track-list is fresh);
     * everything else delegates to [registerSideLoadedLabel].
     */
    private fun dedupePendingSideLoad(source: SubtitleSource, usedLabels: MutableSet<String>): String? {
        if (skipDuplicateByLabel(source, usedLabels)) return null
        return registerSideLoadedLabel(source, usedLabels)
    }

    private fun addPendingSubtitles(mpv: MPV) {
        val subtitles = pendingSubtitles
        if (subtitles.isEmpty()) return

        // [dedupePendingSideLoad] guards against duplicate sub-add against
        // the live track-list; usedLabels keeps growing across the batch so
        // two same-titled pending subs don't collide with each other either.
        val usedLabels = existingSubLabels().toMutableSet()

        subtitles.forEach { sub ->
            val label = dedupePendingSideLoad(sub, usedLabels) ?: return@forEach
            // "select" forces the track active; "auto" leaves selection to mpv's
            // slang/sub-auto heuristics, which drop a side-loaded track that has
            // no language and no matching slang. A subtitle flagged isDefault is
            // the source explicitly asking for it to be shown, so select it.
            val flags = if (sub.isDefault) "select" else "auto"
            try {
                Log.d(
                    TAG,
                    "Adding Jellyfin subtitle to MPV: id=${sub.id}, label='$label', lang=${sub.language}, " +
                        "codec=${sub.codec}, default=${sub.isDefault}, forced=${sub.isForced}, flags=$flags, " +
                        "url=${redactSensitive(sub.url)}"
                )
                if (sub.language.isNullOrBlank()) {
                    mpv.command("sub-add", mpvOpenableUrl(sub.url), flags, label)
                } else {
                    // Local val captures the non-null value: SubtitleSource.language
                    // now lives in :feature:player:core (different module), so
                    // Kotlin can no longer smart-cast the cross-module property.
                    // The else branch proves non-blank (hence non-null).
                    val language = sub.language!!
                    mpv.command("sub-add", mpvOpenableUrl(sub.url), flags, label, language)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add Jellyfin subtitle: ${redactSensitive(sub.url)}", e)
            }
        }
        pendingSubtitles = emptyList()
        logSubtitleRenderState("sub-add")
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val mpv = mpvView?.mpv ?: return
        // [dedupeRuntimeSideLoad] skips true re-adds (double-tap, re-attach
        // after a config reload) and uniquifies same-label different-source
        // subs — see its KDoc for why skipping those would strand the row.
        val label = dedupeRuntimeSideLoad(source, existingSubLabels().toMutableSet()) ?: return
        try {
            // mpv cannot open File.toURI()'s single-slash file:/ URIs — see
            // [mpvOpenableUrl].
            val openUrl = mpvOpenableUrl(source.url)
            if (source.language.isNullOrBlank()) {
                mpv.command("sub-add", openUrl, "select", label)
            } else {
                // Local val captures the non-null value: SubtitleSource.language
                // now lives in :feature:player:core (different module), so
                // Kotlin can no longer smart-cast the cross-module property.
                // The else branch proves non-blank (hence non-null).
                val language = source.language!!
                mpv.command("sub-add", openUrl, "select", label, language)
            }
            Log.d("SubtitleUse", "mpv sub-add ok: id=${source.id}, label='$label', url=${redactSensitive(openUrl)}")
            refreshTracks("addExternalSubtitle", delayMs = 500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add external subtitle: ${redactSensitive(source.url)}", e)
        }
    }

    private fun normalizeLanguageList(language: String): String =
        language.split(',', ';')
            .map { it.trim().replace('_', '-') }
            .filter { it.isNotBlank() }
            .joinToString(",")

    private fun refreshTracks(reason: String, delayMs: Long = 0L) {
        if (released) return
        if (delayMs > 0) {
            // Delayed refreshes enumerate late-arriving tracks on HLS/transcoded
            // streams and must NOT be cancelled by an intervening immediate
            // refresh, so they keep their own postDelayed slot.
            //
            // buildTracks() runs on the MAIN thread (not Dispatchers.IO):
            // release() also runs on main and posts mpv_terminate_destroy to a
            // background thread. Reading on main serialises the getPropertyNode
            // JNI call against destroy — a main-thread read that has already
            // started always finishes before release() can post destroy, so the
            // mpv handle is never used concurrently from two threads. Offloading
            // to Dispatchers.IO previously widened a use-after-free window that
            // hung the player during subtitle-reload engine swaps (ANR).
            val action = Runnable {
                if (released) return@Runnable
                val tracks = try { buildTracks() } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh MPV tracks ($reason)", e); return@Runnable
                }
                publishTracks(tracks, reason)
            }
            mainHandler.postDelayed(action, delayMs)
        } else {
            // Immediate refresh: route through the coalescer so the select +
            // sid/aid/track-list observer burst collapses into a single
            // buildTracks() read (see TrackRefreshCoalescer). The coalescer
            // launches on engineScope (Dispatchers.Main), so the read stays
            // main-threaded for the destroy-serialization reason above.
            trackRefresh.request()
        }
    }

    /**
     * Assigns the freshly-built track list to [_availableTracks] (only when it
     * actually changed — a no-op StateFlow set still propagates a comparison)
     * and logs the refresh. Reads the current value inline so coalesced
     * refreshes compare against the latest published list.
     */
    private fun publishTracks(tracks: List<MediaTrack>, reason: String) {
        val prior = _availableTracks.value
        if (tracks != prior) {
            _availableTracks.value = tracks
        }
        Log.d(TAG, "MPV tracks refreshed ($reason): ${describeTracks(tracks)}")
    }

    private fun describeTracks(tracks: List<MediaTrack>): String {
        val audio = tracks.filter { it.type == TrackType.AUDIO }
        val subtitles = tracks.filter { it.type == TrackType.SUBTITLE }
        val selectedSubtitle = subtitles.firstOrNull { it.isSelected }?.let { "${it.index}:${it.label}" } ?: "none"
        return "audio=${audio.size}, subtitles=${subtitles.size}, selectedSubtitle=$selectedSubtitle, " +
            "subtitleTracks=${subtitles.take(8).joinToString { "${it.index}:${it.label}${if (it.isSelected) "*" else ""}" }}" +
            if (subtitles.size > 8) ", ..." else ""
    }

    private fun applySubtitleStyleOptions(mpv: MPV, style: SubtitleStyle) {
        val values = subtitleStyleValues(style)
        if (style.applyCustomStyle) {
            customSubtitleStyleEntries(style, values).forEach { (k, v) -> mpv.safeSetOption(k, v) }
            mpv.safeSetOption("sub-font", style.fontFamilyName?.takeIf { it.isNotBlank() } ?: fontProvider.bundledFallbackFamilyName() ?: "sans-serif")
            mpv.safeSetOption("sub-scale", (style.fontSize.toDouble() / SubtitleDefaults.REFERENCE_FONT_SIZE).toString())
        } else {
            // Reset to mpv native defaults — the subset mpv needs at init time
            // (ass-override, typeface toggles, font, scale). All reset strings
            // and the scale magnitude come from the tested MpvStyleMapping
            // (sourced from its single DEFAULTS table via defaultInitEntries),
            // so this branch cannot drift from DEFAULTS and is unit-covered.
            MpvStyleMapping.defaultInitEntries().forEach { (k, v) -> mpv.safeSetOption(k, v) }
            mpv.safeSetOption("sub-font", fontProvider.bundledFallbackFamilyName() ?: "sans-serif")
            mpv.safeSetOption("sub-scale", MpvStyleMapping.defaultScale.toString())
        }

        mpv.safeSetOption("sub-font-size", SubtitleDefaults.MPV_LIBASS_REFERENCE_FONT_SIZE.toString())
        val subPosValue = (100 - (style.verticalPosition * 100).toInt()).coerceIn(0, 100)
        mpv.safeSetOption("sub-pos", subPosValue.toString())
        mpv.safeSetOption("sub-margin-y", values.marginY.toString())
        mpv.safeSetOption("sub-delay", (currentConfig.subtitleDelayMs / 1000.0).toString())
    }

    private fun applySubtitleStyleProperties(mpv: MPV, style: SubtitleStyle) {
        val values = subtitleStyleValues(style)
        mpv.safeSetPropertyBoolean("sub-visibility", true)
        if (style.applyCustomStyle) {
            customSubtitleStyleEntries(style, values).forEach { (k, v) -> mpv.safeSetPropertyString(k, v) }
            // Numeric properties are typed (Double) for the runtime path.
            // sub-border-* are the canonical mpv/libass names; sub-outline-* are
            // deprecated aliases that silently no-op on some libass versions.
            mpv.safeSetPropertyDouble("sub-border-size", values.outlineSize)
            mpv.safeSetPropertyDouble("sub-shadow-offset", values.shadowOffset)
            mpv.safeSetPropertyString("sub-font", style.fontFamilyName?.takeIf { it.isNotBlank() } ?: fontProvider.bundledFallbackFamilyName() ?: "sans-serif")
            mpv.safeSetPropertyDouble("sub-scale", style.fontSize.toDouble() / SubtitleDefaults.REFERENCE_FONT_SIZE)
        } else {
            // Reset to mpv native defaults — string pairs and numeric magnitudes
            // both come from the tested MpvStyleMapping (sourced from its single
            // DEFAULTS table), so this branch is unit-covered. sub-ass-justify is
            // boolean-typed on mpv; the mapping emits it as a "no" string pair,
            // applied here via the boolean setter.
            MpvStyleMapping.defaultEntries().forEach { (k, v) ->
                if (k == "sub-ass-justify") mpv.safeSetPropertyBoolean(k, false)
                else mpv.safeSetPropertyString(k, v)
            }
            mpv.safeSetPropertyString("sub-font", fontProvider.bundledFallbackFamilyName() ?: "sans-serif")
            mpv.safeSetPropertyDouble("sub-border-size", MpvStyleMapping.defaultBorderSize)
            mpv.safeSetPropertyDouble("sub-shadow-offset", MpvStyleMapping.defaultShadowOffset)
            mpv.safeSetPropertyDouble("sub-scale", MpvStyleMapping.defaultScale)
        }

        mpv.safeSetPropertyDouble("sub-font-size", SubtitleDefaults.MPV_LIBASS_REFERENCE_FONT_SIZE.toDouble())
        val subPosValue = (100 - (style.verticalPosition * 100).toInt()).coerceIn(0, 100)
        mpv.safeSetPropertyInt("sub-pos", subPosValue)
        mpv.safeSetPropertyInt("sub-margin-y", values.marginY)
        mpv.safeSetPropertyDouble("sub-delay", currentConfig.subtitleDelayMs / 1000.0)
    }

    /**
     * The string-typed subtitle-style key/value pairs shared by both
     * [applySubtitleStyleOptions] (init-time, setOptionString) and
     * [applySubtitleStyleProperties] (runtime, setPropertyString). Delegates to
     * [MpvStyleMapping.customStyleEntries] so the mapping is
     * unit-testable without a live mpv handle. Callers apply each pair through
     * their own setter.
     */
    private fun customSubtitleStyleEntries(
        style: SubtitleStyle,
        @Suppress("UNUSED_PARAMETER") values: MpvStyleMapping.MpvStyleValues,
    ): List<Pair<String, String>> = MpvStyleMapping.customStyleEntries(style)

    private fun subtitleStyleValues(style: SubtitleStyle): MpvStyleMapping.MpvStyleValues =
        MpvStyleMapping.computeValues(style)

    private fun MPVNode?.asTrackId(): Int? =
        this?.asInt()?.toInt() ?: this?.asString()?.toIntOrNull()

    /**
     * Diagnostic-only snapshot of the subtitle render state. Each call performs
     * ~7 synchronous mpv property reads on the MAIN thread — never call this
     * from the hot observer/refresh path. Runs on main to serialise against
     * mpv_terminate_destroy (same reasoning as buildTracks in refreshTracks);
     * debug-only and restricted to explicit user-action entry points (selectTrack,
     * subtitle style apply, external sub-add).
     */
    private fun logSubtitleRenderState(reason: String) {
        if (!com.raulshma.jellyplay.feature.player.video.BuildConfig.DEBUG) return
        if (released) return
        val m = mpvView?.mpv ?: return
        try {
            val sid = m.getPropertyString("sid")
            val visible = m.getPropertyBoolean("sub-visibility")
            val subText = try { m.getPropertyString("sub-text") } catch (_: Exception) { null }
            val selected = _availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE && it.isSelected }
            Log.d(
                TAG,
                "MPV subtitle render state ($reason): sid=$sid, visible=$visible, " +
                    "fontSize=${m.getPropertyDouble("sub-font-size")}, marginY=${m.getPropertyInt("sub-margin-y")}, " +
                    "pos=${m.getPropertyInt("sub-pos")}, selected=${selected?.index}:${selected?.label}, " +
                    "activeText=${subText?.take(80).orEmpty()}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read MPV subtitle render state ($reason)", e)
        }
    }

    private fun logMpvMessage(prefix: String, level: Int, text: String) {
        if (!com.raulshma.jellyplay.feature.player.video.BuildConfig.DEBUG && level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN) return
        val cleanText = redactSensitive(text.trim()).takeIf { it.isNotBlank() } ?: return
        if (level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN && !MPV_SUBTITLE_LOG_PATTERN.containsMatchIn("$prefix $cleanText")) {
            return
        }

        val message = "MPV ${mpvLogLevelName(level)} [$prefix] $cleanText"
        when {
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> Log.e(TAG, message)
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> Log.w(TAG, message)
            level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> Log.i(TAG, message)
            else -> Log.d(TAG, message)
        }
    }

    private fun mpvLogLevelName(level: Int): String = when {
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_FATAL -> "fatal"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_ERROR -> "error"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN -> "warn"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_INFO -> "info"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_V -> "verbose"
        level <= MPV.mpvLogLevel.MPV_LOG_LEVEL_DEBUG -> "debug"
        else -> "trace"
    }

    private fun redactSensitive(value: String): String =
        value
            .replace(REDACT_API_KEY, "\$1***")
            .replace(REDACT_API_KEY_ENCODED, "\$1***")
            .replace(REDACT_EMBY_TOKEN, "\$1***")

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }

    private fun MPV.safeSetOption(name: String, value: String) {
        try {
            setOptionString(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set option $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyString(name: String, value: String) {
        try {
            setPropertyString(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyDouble(name: String, value: Double) {
        try {
            setPropertyDouble(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyInt(name: String, value: Int) {
        try {
            setPropertyInt(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }

    private fun MPV.safeSetPropertyBoolean(name: String, value: Boolean) {
        try {
            setPropertyBoolean(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set property $name to $value", e)
        }
    }
}

/**
 * Pure mapping helpers for mpv option/property values that were
 * previously duplicated verbatim between `initOptions` (load-time,
 * `setOptionString`) and `updateConfig` (live, `setPropertyString`).
 * Keeping the mapping in one place stops the two sites from drifting.
 */

/**
 * Converts [url] into a form mpv's stream layer can actually open.
 *
 * `File.toURI()` produces single-slash `"file:/data/…"` URIs, and mpv's file
 * stream handler only strips the scheme for the `"file://…"` form — the
 * single-slash form is opened literally as a relative path and fails with
 * ENOENT (observed on-device: a freshly downloaded side-load's `sub-add`
 * succeeded but its demuxer open failed, so the track never materialized,
 * while ExoPlayer opened the identical URI fine). Returns the decoded
 * absolute path for file URIs; bare paths and every other scheme
 * (`content://`, `http(s)://`) pass through unchanged. Top-level for
 * unit-testability without an engine.
 */
internal fun mpvOpenableUrl(url: String): String =
    if (url.startsWith("file:")) {
        try {
            java.net.URI(url).path?.takeIf { it.isNotEmpty() } ?: url
        } catch (_: Exception) {
            url
        }
    } else {
        url
    }

internal fun decoderModeToHwdec(mode: DecoderMode): String = when (mode) {
    // Zero-copy `mediacodec` first: mpv picks the first entry that inits, and
    // `mediacodec-copy` (GPU→CPU→GPU per frame) almost always inits when listed
    // first, so copy-first ordering silently forced every HW decode through the
    // slow path — the primary cause of mpv lag vs. zero-copy ExoPlayer. Keep
    // copy as fallback, then SW last.
    DecoderMode.HW_PREFERRED -> "mediacodec,mediacodec-copy,no"
    DecoderMode.HW_ONLY -> "mediacodec,mediacodec-copy"
    DecoderMode.SW_ONLY -> "no"
}

internal fun channelMixModeToAudioChannels(
    mode: ChannelMixMode,
    enabled: Boolean = true,
): String = if (!enabled) {
    "auto"
} else when (mode) {
    ChannelMixMode.STEREO_DOWNMIX -> "stereo"
    ChannelMixMode.MONO -> "mono"
    ChannelMixMode.SURROUND_UPMIX -> "5.1"
    ChannelMixMode.AUTO -> "auto"
}

/** Returns null for NONE so callers can omit it from the af chain. */
internal fun audioNormalizationModeToAfFilter(mode: AudioNormalizationMode): String? = when (mode) {
    AudioNormalizationMode.DYNAMIC -> "acompressor=ratio=3:threshold=0.05:attack=10:release=200"
    AudioNormalizationMode.TRACK, AudioNormalizationMode.ALBUM -> "loudnorm=I=-23:LRA=7:tp=-1"
    AudioNormalizationMode.NONE -> null
}
