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
import com.raulshma.jellyplay.core.data.playback.NightModeHelper
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.MpvAudioOutput
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
import com.raulshma.jellyplay.feature.player.video.subtitle.AndroidFontProvider
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
    private val fontProvider: AndroidFontProvider,
) : ReloadablePlayerEngine(context), AndroidSurfaceProvider {

    companion object {
        private const val TAG = "MpvPlayerEngine"
        // Upper bound on how long the cheap-scalar guard in
        // updateVideoStatsOnly may skip the full property re-read.
        private const val FULL_STATS_REREAD_MS = 2_000L
        // Prefix/text filter for which verbose (below WARN) mpv messages are
        // surfaced in debug builds. Covers the subtitle/font/render pipeline
        // (sub/ass/libass/vtt/srt) plus the demux/vo/decode paths that feed it,
        // so a no-render bug can be traced end-to-end without logcat drowning.
        private val MPV_SUBTITLE_LOG_PATTERN =
            Regex("(?i)(sub|subtitle|libass|webvtt|vtt|srt|ssa|ass|ffmpeg|http|stream|vo/|demux|cplayer|vd)")
    }

    private val isLowRamDevice by lazy { EngineDeviceProfile.isLowRamDevice(context) }

    // KMP seam: the legacy module's BuildConfig.DEBUG gate, replaced
    // by the runtime FLAG_DEBUGGABLE read (DataBuildFlags.android precedent —
    // the KMP library plugin generates no BuildConfig). Same value for every
    // standard build type; the three call sites are debug-logging gates.
    private val debugBuild by lazy {
        (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

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
     * `track-list`, matching the label-keyed dedupe in [existingSubLabels].
     * Cleared per item in [load]/[release] so entries never bleed across items;
     * [MpvSubtitleSideLoadPlan] owns the pre-seed/registration, and
     * [buildTracks] (via [MpvTrackCatalog]) consumes it. Reference-swapped
     * (never mutated in place) so reads stay race-free from the main thread.
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

    // Set true by [release] before the async native destroy. Observer callbacks
    // read this at entry and bail, so a callback that races in during the
    // teardown window (after removeObserver but before mpv_terminate_destroy
    // finishes on the release thread) cannot touch torn-down state. Matches
    // mpvkt's `player.isExiting` guard.
    @Volatile private var released = false

    /**
     * The latched mpv playback state the shared [MpvEventFold] folds events
     * over (fileLoaded/eofReached/paused/pausedForCache). Observer callbacks
     * are serialized by mpv's single event queue, so read-modify-write is
     * race-free; reset per item in [load] and in [release].
     */
    @Volatile private var mpvLatches = MpvPlaybackLatches()

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
                when (property) {
                    "pause" -> applyFold(MpvPlaybackEvent.PauseChanged(value))
                    "paused-for-cache" -> applyFold(MpvPlaybackEvent.PausedForCacheChanged(value))
                    "eof-reached" -> applyFold(
                        MpvPlaybackEvent.EofReachedChanged(value, pausedNow = livePauseFlag()),
                    )
                    "sub-visibility" -> Log.d(TAG, "MPV subtitle visibility changed to $value")
                }
            }
            override fun eventProperty(property: String, value: String) {
                when (property) {
                    "sid", "aid" -> {
                        Log.d(TAG, "MPV $property changed to ${redactSensitive(value)}")
                        applyFold(
                            MpvPlaybackEvent.TrackSwitch(
                                if (property == "sid") MpvPlaybackEvent.TrackKind.SUBTITLE
                                else MpvPlaybackEvent.TrackKind.AUDIO,
                            ),
                            refreshReason = "property:$property",
                        )
                    }
                    "sub-text" -> applyFold(MpvPlaybackEvent.SubTextChanged(value))
                }
            }
            override fun eventProperty(property: String, value: MPVNode) {
                if (property == "track-list") {
                    refreshTracks("property:track-list")
                } else if (property == "demuxer-cache-state") {
                    // the range-level buffered surface. Node arrives
                    // parsed; extraction + clamping is the shared pure
                    // derivation (see updateBufferedRangesFromCacheState).
                    updateBufferedRangesFromCacheState(value)
                }
            }
            override fun event(eventId: Int, data: MPVNode) {
                if (released) return
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        Log.d(TAG, "MPV start file; adding ${pendingSubtitles.size} Jellyfin subtitle source(s)")
                        addPendingSubtitles(mpv)
                        applyFold(MpvPlaybackEvent.StartFile)
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
                        refreshTracks("file-loaded")
                        // For HLS/transcoded streams the demuxer populates audio
                        // track-list entries asynchronously after FILE_LOADED;
                        // a single immediate read races ahead of that and yields
                        // an empty audio picker. Re-poll after a short delay so
                        // late-arriving audio/subtitle tracks are enumerated.
                        refreshTracks("file-loaded-late", delayMs = 500)
                        // Fold seeds READY + isPlaying from the LIVE core pause
                        // state: when the core auto-plays (default), `pause`
                        // never *changes*, so the pause observer alone would
                        // never fire (shared MpvEventFold semantics).
                        applyFold(MpvPlaybackEvent.FileLoaded(pausedNow = livePauseFlag()))
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

            // The structured MpvEngineConfig → mpv mapping lives in the shared
            // MpvConfigMapping (desktop parity — this engine is no longer the
            // sole consumer): scale/deband/interpolation(+video-sync)/framedrop/
            // skiploopfilter/demuxer budgets/audio trio/extras, in that order,
            // with the user's mpvExtraConfig lines LAST (a raw line overrides
            // its structured counterpart). Init seeds the runtime diff cache
            // (see onConfigChanged) with exactly the pairs written here so a
            // runtime push of an unchanged config performs zero writes.
            val configPairs = MpvConfigMapping.configPairs(
                config = mpvCfg,
                audioPassthrough = currentConfig.audioPassthrough,
                lowRamDevice = isLowRamDevice,
                deinterlace = currentConfig.deinterlace,
            )
            for (option in configPairs) {
                try {
                    mpv.setOptionString(option.key, option.value)
                } catch (e: Exception) {
                    // One bad line must not abort init — only the free-form
                    // extra-config lines can realistically land here (unknown
                    // option / bad value throw from setOptionString).
                    Log.w(TAG, "mpv config: rejected '${option.key}=${option.value}' (${e.message})")
                }
            }
            lastAppliedEngineConfigProps = configPairs.associate { it.key to it.value }

            // Force CPU-side AV1 film-grain synthesis. The GPU film-grain path
            // (default on hwdec) stalls on several drivers — frames back up and
            // playback stutters even though the decoder is keeping up. This is
            // the documented workaround for https://github.com/mpv-player/mpv/issues/14651
            // and is what mpvkt sets unconditionally.
            mpv.setOptionString("vd-lavc-film-grain", "cpu")

            // Debug builds surface libass/vo/demuxer trace messages so subtitle
            // render/decode issues (font-provider death, empty bitmaps) are
            // visible without recompiling; release keeps warn to stay quiet and
            // cheap. Mirrors mpvkt's per-build msg-level (all=v debug / all=warn
            // release). A debug-build sub/ass trace is what pinpointed the
            // "can't find selected font provider" → empty-bitmap subtitle bug.
            val msgLevel = if (debugBuild) "all=v" else "all=warn"
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

            // Tag the output stream so Android routes it correctly (movie role →
            // speaker, ignores notifications).
            mpv.setOptionString("audio-set-media-role", "yes")

            mpv.setOptionString(
                "audio-channels",
                MpvConfigMapping.effectiveAudioChannels(
                    mpvCfg.audioOutputMode,
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
            // range-level buffered surface alongside the scalar
            // demuxer-cache-time observer above. NODE delivery — the Android
            // binding hands the parsed tree, no JNI re-read needed.
            mpv.observeProperty("demuxer-cache-state", MPV.mpvFormat.MPV_FORMAT_NODE)
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
        // Fresh fold state + fresh side-loaded-subtitle id registry for the
        // new item. [MpvSubtitleSideLoadPlan.planBatch] pre-seeds the registry
        // (raw label → SubtitleSource.id) when the pending batch executes at
        // START_FILE — see [sideLoadedSubtitleIds].
        mpvLatches = MpvPlaybackLatches()
        sideLoadedSubtitleIds = emptyMap()
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
        mpvLatches = MpvPlaybackLatches()
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
        // Published-flow resets live in BasePlayerEngine (C5); mpv's residue
        // (live-subtitle mirror + cached position/duration/buffer reads) goes
        // through the hook that reset fires.
        resetPublishedEngineState()
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

    /**
     * The mpv residue cleared alongside the base published-state reset (C5):
     * the live-subtitle-text mirror (the one engine that publishes it) and
     * the cached native position/duration/buffer reads the ticker seeds from.
     */
    override fun onResetItemScopedState() {
        _liveSubtitleCue.value = null
        cachedPositionMs = 0L
        cachedDurationMs = 0L
        cachedBufferedPositionMs = 0L
        cachedSubStartSec = -1.0
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

    // The structured-config runtime diff cache (MpvConfigMapping.applyChanged):
    // seeded by initOptions with the pairs it wrote, so onConfigChanged writes
    // only actual CHANGES. Same discipline as the desktop engine's
    // lastApplied* caches — an unchanged re-write is at best noise and at
    // worst (af/vf-class properties) a pipeline re-init.
    @Volatile private var lastAppliedEngineConfigProps: Map<String, String> = emptyMap()

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        val mpvCfg = (newConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()
        val oldMpvCfg = oldConfig.engineSpecific as? MpvEngineConfig
        // Slice decisions come from the shared pure delta (EngineConfigDelta.of
        // — the single diff both mpv hosts consume); only the native writes and
        // the mpv-config sub-field checks below stay engine-owned.
        val delta = EngineConfigDelta.of(oldConfig, newConfig)

        try {
            val mpv = mpvView?.mpv ?: return

            if (delta.audioDelayChanged) {
                mpv.setPropertyDouble("audio-delay", newConfig.audioDelayMs / 1000.0)
            }
            if (delta.subtitleDelayChanged) {
                mpv.setPropertyDouble("sub-delay", newConfig.subtitleDelayMs / 1000.0)
            }

            if (delta.decoderModeChanged || oldMpvCfg?.hwdecOverride != mpvCfg.hwdecOverride) {
                val hwdecValue = mpvCfg.hwdecOverride?.key ?: decoderModeToHwdec(newConfig.decoderMode)
                mpv.setPropertyString("hwdec", hwdecValue)
            }

            // The shared mapping's runtime half: diff the new pair list against
            // what init (or the previous change) wrote and push only the
            // changed keys — covers scaler, deband, interpolation(+video-sync),
            // framedrop, skiploopfilter, the demuxer budgets, the audio trio
            // (audio-device / audio-exclusive / audio-spdif via the output
            // mode + passthrough reconciliation) and the extra-config lines.
            if (delta.sharedPairsChanged) {
                // engineSpecific + audioPassthrough + deinterlace + hdrSource
                // (see EngineConfigDelta.sharedPairsChanged).
                val pairs = MpvConfigMapping.configPairs(
                    config = mpvCfg,
                    audioPassthrough = newConfig.audioPassthrough,
                    lowRamDevice = isLowRamDevice,
                    deinterlace = newConfig.deinterlace,
                )
                lastAppliedEngineConfigProps = MpvConfigMapping.applyChanged(pairs, lastAppliedEngineConfigProps) { key, value ->
                    mpv.setPropertyString(key, value)
                }
            }

            if (oldMpvCfg?.audioOutput != mpvCfg.audioOutput || oldMpvCfg?.audioFallback != mpvCfg.audioFallback) {
                val aoValue = buildString {
                    append(mpvCfg.audioOutput.key)
                    mpvCfg.audioFallback?.let { append(",").append(it.key) }
                }
                mpv.setPropertyString("ao", aoValue)
            }

            if (delta.subtitleStyleChanged) {
                applySubtitleStyleInternal(newConfig.subtitleStyle)
            }

            if (delta.channelMixChanged || oldMpvCfg?.audioOutputMode != mpvCfg.audioOutputMode) {
                // The output mode's STEREO forced downmix folds into the same
                // audio-channels write (the effects chain stays the single
                // writer of this pipeline-re-initing property).
                mpv.setPropertyString(
                    "audio-channels",
                    MpvConfigMapping.effectiveAudioChannels(
                        mpvCfg.audioOutputMode,
                        newConfig.audioEffects.channelMixMode,
                        newConfig.audioEffects.channelMixEnabled,
                    ),
                )
            }

            val newAudioFx = newConfig.audioEffects
            // Rebuild the af chain when normalization OR dialogue-boost changes,
            // since dialogue boost contributes a highpass stage to the chain.
            if (delta.audioAfChainChanged) {
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

            if (delta.videoEffectsChanged) {
                applyVideoFilters(newConfig.videoEffects)
            }

            if (delta.audioSessionEffectsChanged) {
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

    // ── Volume / mute seams (C4) ────────────────────────────────────────────
    // The four command bodies are final templates in ReloadablePlayerEngine;
    // mpv contributes the percent-scaled property write/read, its real mute
    // flag, and the LEAVE_UNCHANGED vocabulary (the flag silences; the native
    // volume stays untouched on both transitions — the system-stream sync is
    // the mute's other surface and runs even without a handle, matching the
    // former body). Dispatch keeps the former swallow-all containment.

    override fun dispatchVolumeCommand(command: () -> Unit) {
        try { command() } catch (_: Exception) {}
    }

    override fun readNativeVolume(): Float? = try {
        val m = mpvView?.mpv ?: return null
        // A missing property read defaults to full loudness — only a missing
        // handle or a thrown read aborts the delta templates.
        ((m.getPropertyDouble("volume") ?: 100.0) / 100.0).toFloat()
    } catch (_: Exception) { null }

    override fun applyNativeVolume(normalized: Float) {
        // Throws through to dispatchVolumeCommand's catch on a failed write —
        // the former bodies skipped the system-stream sync in that case too.
        mpvView?.mpv?.setPropertyDouble("volume", normalized * 100.0)
    }

    override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
        PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED

    override fun applyNativeMuteFlag(muted: Boolean) {
        try { mpvView?.mpv?.setPropertyBoolean("mute", muted) } catch (_: Exception) {}
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
        val plan = AspectRatioMapping.mpvPlan(ratio)
        val m = mpvView?.mpv ?: return
        try { m.setPropertyString("video-aspect-override", plan.aspectOverride) } catch (_: Exception) {}
        try {
            m.setPropertyDouble("panscan", plan.panscan)
            m.setPropertyString("sub-use-margins", plan.subUseMargins)
            m.setPropertyString("sub-ass-force-margins", plan.subAssForceMargins)
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

    /**
     * folds the observed `demuxer-cache-state` NODE into
     * [_bufferedRanges]. mpv ships the per-range detail as
     * `seekable-ranges` (start/end seconds) on libmpv >= 0.35 — taken
     * directly when present; otherwise the contiguous window
     * `[demuxer-start-time, cache-end]` is derived, clamped to the item
     * bounds. All decisions live in the shared pure
     * [BufferedRanges.fromDemuxerCacheState] (mirrored by the desktop engine).
     */
    private fun updateBufferedRangesFromCacheState(state: MPVNode) {
        if (released) return
        val map = state.asMap() ?: return
        val seekableRanges = map["seekable-ranges"]?.asArray()?.mapNotNull { entry ->
            val rangeMap = entry.asMap() ?: return@mapNotNull null
            val start = rangeMap["start"]?.asDouble()
            val end = rangeMap["end"]?.asDouble()
            if (start != null && end != null) start to end else null
        }
        _bufferedRanges.value = BufferedRanges.fromDemuxerCacheState(
            demuxerStartTimeSec = map["demuxer-start-time"]?.asDouble(),
            cacheEndSec = map["cache-end"]?.asDouble(),
            seekableRangesSec = seekableRanges,
            durationMs = durationMs,
        )
    }

    override val durationMs: Long
        get() {
            // Prefer the mpv demuxer's duration when available; fall back to
            // the server-reported runTimeTicks (see resolveDurationMs).
            return resolveDurationMs(cachedDurationMs, serverDurationMs)
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

    /** ElapsedRealtime of the last unguarded (full) stats read — see updateVideoStatsOnly. */
    private var lastFullStatsReadMs = 0L

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
            val droppedFrames = try {
                m.getPropertyInt("decoder-frame-drop-count")?.toLong() ?: 0L
            } catch (_: Exception) { 0L }
            val totalVideoFrames = try {
                m.getPropertyInt("displayed-frame-count")?.toLong() ?: 0L
            } catch (_: Exception) { 0L }
            val bufferedPositionMs = _bufferedPositionMs.value

            // Cheap-scalar change guard first (mirrors the Exo adapter): the
            // ~10 string/track reads further down are worth paying only once a
            // scalar actually moved. publishStatsIfChanged stays the final emit
            // guard. The scalar set can freeze while paused (frame counters
            // stop, bitrates drop to 0), so a periodic full re-read is forced
            // anyway to pick up fields the guard never sees move (hwdec
            // switches, avsync, vo-delayed, track changes).
            val nowMs = android.os.SystemClock.elapsedRealtime()
            val last = lastVideoStats
            if (last != null && last.videoBitrate == videoBitrateBps &&
                last.audioBitrate == audioBitrateBps &&
                last.bufferedPositionMs == bufferedPositionMs &&
                last.droppedFrames == droppedFrames &&
                last.totalVideoFrames == totalVideoFrames &&
                nowMs - lastFullStatsReadMs < FULL_STATS_REREAD_MS
            ) {
                return
            }
            lastFullStatsReadMs = nowMs

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
                droppedFrames = droppedFrames,
                totalVideoFrames = totalVideoFrames,
                bufferedPositionMs = bufferedPositionMs,
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

    /**
     * Thin adapter over the shared [MpvTrackCatalog]: this binding's parsed
     * `track-list` node rows translate to [MpvTrackCatalog.MpvTrackEntry] and
     * the catalog builds the contract [MediaTrack] list (side-loaded id
     * stamping, [TrackLabelFormatter] labels, badges — see the catalog KDoc).
     */
    private fun buildTracks(): List<MediaTrack> {
        val m = mpvView?.mpv ?: return emptyList()
        val trackList = try {
            m.getPropertyNode("track-list")?.asArray()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return MpvTrackCatalog.mediaTracks(
            entries = trackList.mapNotNull { it.asTrackEntry() },
            sideLoadedSubtitleIds = sideLoadedSubtitleIds,
        )
    }

    private fun MPVNode.asTrackEntry(): MpvTrackCatalog.MpvTrackEntry? {
        val track = asMap() ?: return null
        val type = track["type"]?.asString() ?: return null
        val id = track["id"].asTrackId() ?: return null
        return MpvTrackCatalog.MpvTrackEntry(
            type = type,
            id = id,
            title = track["title"]?.asString(),
            lang = track["lang"]?.asString(),
            codec = track["codec"]?.asString(),
            selected = track["selected"]?.asBoolean() ?: false,
            // `external` is true for sub-add'd (side-loaded) tracks and absent/
            // false for container-demuxed tracks. Gates the side-loaded id
            // lookup in the catalog so a demuxed track that happens to share a
            // label with a sidecar never inherits the sidecar's stable id.
            external = track["external"]?.asBoolean() ?: false,
            // ff-index is the demuxer/container stream index — present for
            // container-demuxed tracks (== the server's MediaStream.index), null
            // for side-loaded (sub-add) tracks. Used as the robust resolution key
            // in TrackSelectionHelper instead of fragile label matching.
            ffIndex = track["ff-index"].asTrackId(),
            forced = track["forced"]?.asBoolean() ?: false,
            default = track["default"]?.asBoolean() ?: false,
            hearingImpaired = track["hearing-impaired"]?.asBoolean() ?: false,
        )
    }

    /**
     * Folds one mpv event through the shared [MpvEventFold] and applies the
     * declared decisions/side-effects to the published flows. The engine stays
     * a thin adapter: the only native surface here is the `refreshTracks`
     * reason label and the flows themselves.
     */
    private fun applyFold(event: MpvPlaybackEvent, refreshReason: String = "event") {
        applyFoldResult(MpvEventFold.fold(mpvLatches, event), refreshReason)
    }

    private fun applyFoldResult(result: MpvEventFoldResult, refreshReason: String) {
        mpvLatches = result.latches
        result.isPlaying?.let { _isPlaying.value = it }
        result.playbackState?.let { _playbackState.value = it }
        if (result.clearCueHistory) _currentCues.value = emptyList()
        if (result.clearLiveCue) _liveSubtitleCue.value = null
        if (result.refreshTracks) refreshTracks(refreshReason)
        result.liveSubtitleText?.let { text ->
            accumulateMpvSubText(text)
            // Mirror the live line into the overlay flow. mpv fires sub-text
            // only on a line change and emits "" when the line clears (folded
            // as clearLiveCue), so the Compose overlay updates/clears in
            // lockstep with native rendering.
            _liveSubtitleCue.value = text
        }
    }

    /** The LIVE `pause` property read the fold's seeds/re-derivations need. */
    private fun livePauseFlag(): Boolean = try {
        mpvView?.mpv?.getPropertyBoolean("pause") ?: true
    } catch (_: Exception) {
        true
    }

    /**
     * Handle an [MPV.mpvEvent.MPV_EVENT_END_FILE] event. The mpv END_FILE event
     * node carries `reason` (and `error` for failures) — see
     * [MpvPlaybackEvent.EndFileReason] for the shared per-reason semantics
     * (folded through [MpvEventFold]; the string error-code → taxonomy mapping
     * is this engine's [MpvErrorTaxonomy.fromCodeString] edge).
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
        val result = MpvEventFold.fold(
            mpvLatches,
            MpvPlaybackEvent.EndFile(MpvPlaybackEvent.EndFileReason.fromCode(reason ?: -1)),
        )
        if (result.emitEndFileError) {
            _errorFlow.tryEmit(mapMpvError(errorCode))
        }
        applyFoldResult(result, refreshReason = "end-file")
    }

    /**
     * Map an mpv END_FILE `error` string onto the [EngineError] taxonomy so the
     * UI can offer the right affordance. The node carries an `mpv_error` int,
     * but the binding exposes it as a string — the whole mapping (numeric
     * parse, descriptive-keyword fallback, unknown placeholder) lives in the
     * shared [MpvErrorTaxonomy] so the desktop engine's int-code edge cannot
     * drift from this one; this engine keeps only the string hand-off.
     *
     * Mirrors ExoPlayer's [PlaybackException.toEngineError]: a load/source
     * failure maps to a retryable [EngineError.Network] (transient mpv network
     * drops, HTTP timeouts, server-closed transcodes), while decoder/init/format
     * failures map to [EngineError.Decoder] (not retryable on the same engine).
     * Unknown errors stay non-retryable [EngineError.Unknown].
     */
    private fun mapMpvError(errorCode: String?): EngineError =
        MpvErrorTaxonomy.fromCodeString(errorCode)

    private fun configureMpvForRequest(view: PlayerMPVView, request: PlaybackRequest) {
        // mTLS: the tls-* options are file-path options that
        // PERSIST on the view's mpv handle across loads — write the reset
        // trio when no certificate is active (same discipline as
        // http-header-fields below) so the previous item's credentials are
        // never inherited.
        MpvTlsOptions.from(request.tls).forEach { (option, value) ->
            try {
                view.mpv.setOptionString(option, value)
                view.mpv.setPropertyString(option, value)
            } catch (_: Exception) {}
        }

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

    private fun addPendingSubtitles(mpv: MPV) {
        val subtitles = pendingSubtitles
        if (subtitles.isEmpty()) return

        // The shared plan dedupes against the live track-list, uniquifies
        // same-titled sources (usedLabels keeps growing across the batch so
        // two same-titled pending subs don't collide with each other either),
        // flags isDefault sources "select" and pre-seeds/registers the
        // side-loaded id registry — see [MpvSubtitleSideLoadPlan.planBatch].
        val plan = MpvSubtitleSideLoadPlan.planBatch(subtitles, existingSubLabels(), sideLoadedSubtitleIds)
        sideLoadedSubtitleIds = plan.registry
        plan.adds.forEach { add ->
            try {
                val sub = add.source
                Log.d(
                    TAG,
                    "Adding Jellyfin subtitle to MPV: id=${sub.id}, label='${add.label}', lang=${sub.language}, " +
                        "codec=${sub.codec}, default=${sub.isDefault}, forced=${sub.isForced}, flags=${add.flags}, " +
                        "url=${redactSensitive(sub.url)}"
                )
                if (sub.language.isNullOrBlank()) {
                    mpv.command("sub-add", mpvOpenableUrl(sub.url), add.flags, add.label)
                } else {
                    // Local val captures the non-null value: SubtitleSource.language
                    // now lives in :feature:player:core (different module), so
                    // Kotlin can no longer smart-cast the cross-module property.
                    // The else branch proves non-blank (hence non-null).
                    val language = sub.language!!
                    mpv.command("sub-add", mpvOpenableUrl(sub.url), add.flags, add.label, language)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add Jellyfin subtitle: ${redactSensitive(add.source.url)}", e)
            }
        }
        pendingSubtitles = emptyList()
        logSubtitleRenderState("sub-add")
    }

    override fun addExternalSubtitle(source: SubtitleSource) {
        val mpv = mpvView?.mpv ?: return
        // The shared plan skips true re-adds (double-tap, re-attach after a
        // config reload) and uniquifies same-label different-source subs —
        // see [MpvSubtitleSideLoadPlan.planRuntimeAdd] for why skipping those
        // would strand the row.
        when (val plan = MpvSubtitleSideLoadPlan.planRuntimeAdd(source, existingSubLabels(), sideLoadedSubtitleIds)) {
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Skip -> {
                Log.d(TAG, "Skipping duplicate subtitle (${plan.reason})")
            }
            is MpvSubtitleSideLoadPlan.RuntimeAdd.Add -> {
                sideLoadedSubtitleIds = plan.registry
                try {
                    val label = plan.add.label
                    // mpv cannot open File.toURI()'s single-slash file:/ URIs — see
                    // [mpvOpenableUrl].
                    val openUrl = mpvOpenableUrl(source.url)
                    if (source.language.isNullOrBlank()) {
                        mpv.command("sub-add", openUrl, plan.add.flags, label)
                    } else {
                        // Local val captures the non-null value: SubtitleSource.language
                        // now lives in :feature:player:core (different module), so
                        // Kotlin can no longer smart-cast the cross-module property.
                        // The else branch proves non-blank (hence non-null).
                        val language = source.language!!
                        mpv.command("sub-add", openUrl, plan.add.flags, label, language)
                    }
                    Log.d("SubtitleUse", "mpv sub-add ok: id=${source.id}, label='$label', url=${redactSensitive(openUrl)}")
                    refreshTracks("addExternalSubtitle", delayMs = 500)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add external subtitle: ${redactSensitive(source.url)}", e)
                }
            }
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
        if (debugBuild) {
            Log.d(TAG, "MPV tracks refreshed ($reason): ${describeTracks(tracks)}")
        }
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
        if (!debugBuild) return
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
        if (!debugBuild && level > MPV.mpvLogLevel.MPV_LOG_LEVEL_WARN) return
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

    // The regex set lives in MpvLogRedaction (commonMain) so it is
    // test-pinned; this alias keeps the engine's call sites unchanged.
    private fun redactSensitive(value: String): String = MpvLogRedaction.redact(value)

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

// The pure option/property mapping helpers (decoderModeToHwdec,
// channelMixModeToAudioChannels, audioNormalizationModeToAfFilter) moved to
// commonMain (MpvAudioMappings.kt) so the tables stay unit-testable from
// jvmTest; same package, call-sites unchanged.
