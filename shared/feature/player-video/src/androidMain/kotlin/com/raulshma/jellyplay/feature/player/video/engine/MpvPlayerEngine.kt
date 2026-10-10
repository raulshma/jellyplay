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
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvBinding
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvCore
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvEndFileError
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeHost
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvIntakeValue
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvProperties
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsProjection
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvStatsReads
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleSideLoadPlan
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleStyleApplier
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleStylePhase
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvUserSubtitleKeys
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvVideoEffectChain
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvPropertySurface

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

    // Read side of the ownership gate (the snapshot lives on [core] — the
    // shared MpvCore ownership choreography): the subtitle-style UI renders
    // its custom-config notice (owned keys, ASS case, quoting trap) from this
    // snapshot, so the explanation always matches what the writes skip.
    override val subtitleStyleOwnership: MpvSubtitleOwnership
        get() = core.ownershipSnapshot

    // The currently-displayed subtitle line, exposed to the screen for the
    // zoom-safe Compose overlay (zoomSafeSubtitleStrategy = COMPOSE_CUE).
    // Distinct from the accumulated [currentCues] history: this is the single
    // live line, cleared on blank/track-switch/stop. Driven by mpv's `sub-text`
    // property (ASS override tags already stripped by mpv).
    // Backing flow: the state chassis's `_liveSubtitleCue` (written by the
    // shared [MpvCore] fold machinery).

    private var mpvView: PlayerMPVView? = null
    private var pendingRequest: PlaybackRequest? = null

    // Android audio session id generated via AudioManager and pushed into
    // mpv's audiotrack/aaudio outputs so Android AudioEffects (dialogue
    // boost, night mode) can bind to mpv's output. Previously read back
    // the string property "audio-device-id" as an int, which always
    // threw and returned 0 — leaving the effect chain unbound.
    @Volatile private var generatedAudioSessionId: Int = 0

    // mpv handles its own internal EQ via af filters; this helper exists
    // solely to host the dialogue-boost overlay (see DialogueBoostHelper
    // kdoc) on the engine's audio session. User EQ settings never flow
    // through it — the helper stays at FLAT base levels with only the
    // boost offsets overlaid.
    private val equalizerHelper = EqualizerHelper()
    private val dialogueBoost = DialogueBoostHelper(equalizerHelper)
    private val nightMode = NightModeHelper()

    // The Android [MpvBinding] over this view's mpv handle — the platform
    // seam of the shared [MpvCore] choreography (writes absorb this JNI
    // wrapper's throw-on-failure contract with the shipped log; commands
    // propagate the throw so the core's transport-error seam keeps the
    // shipped per-op logs; reads default exactly like the former
    // inline try/getProperty/catch bodies).
    private val binding = AndroidMpvBinding()

    /**
     * The host residue: the callbacks only THIS engine implements (the
     * debug-only logs, the `postDelayed` delayed-track-refresh slots, the
     * `sub-reload` style extra, the audio-session-less transport logs). The
     * shared choreography invokes them exactly where the former hand-mirrored
     * bodies ran their platform extras.
     */
    private val coreHosts = object : MpvCore.Hosts() {
        override fun onTransportError(op: String, error: Throwable) {
            Log.w(TAG, "Failed to $op", error)
        }

        override fun onSubVisibilityObserved(visible: Boolean) {
            Log.d(TAG, "MPV subtitle visibility changed to $visible")
        }

        override fun scheduleTrackRefresh(reason: String, delayMs: Long) {
            // Delayed refreshes enumerate late-arriving tracks on
            // HLS/transcoded streams and must NOT be cancelled by an
            // intervening immediate refresh, so they keep their own
            // postDelayed slot.
            //
            // buildTracks() runs on the MAIN thread (not Dispatchers.IO):
            // release() also runs on main and posts mpv_terminate_destroy to
            // a background thread. Reading on main serialises the
            // getPropertyNode JNI call against destroy — a main-thread read
            // that has already started always finishes before release() can
            // post destroy, so the mpv handle is never used concurrently from
            // two threads. Offloading to Dispatchers.IO previously widened a
            // use-after-free window that hung the player during
            // subtitle-reload engine swaps (ANR).
            val action = Runnable {
                if (core.released) return@Runnable
                val tracks = try {
                    core.buildTracks()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to refresh MPV tracks ($reason)", e); return@Runnable
                }
                // The delayed slot's own landing (same change-guard the
                // immediate/coalesced path gets from the core's flow write).
                if (tracks != _availableTracks.value) {
                    _availableTracks.value = tracks
                }
                if (debugBuild) {
                    Log.d(TAG, "MPV tracks refreshed ($reason): ${describeTracks(tracks)}")
                }
            }
            mainHandler.postDelayed(action, delayMs)
        }

        override fun onTracksPublished(tracks: List<MediaTrack>, reason: String) {
            // The landing is the core's (change-guarded against the same flow
            // value the former guard compared); this log is the shipped extra.
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

        override fun onTrackBuildFailed(reason: String, error: Throwable) {
            Log.w(TAG, "Failed to refresh MPV tracks ($reason)", error)
        }

        override fun onStartFile() {
            // Surface side-loaded subs + early track entries. mpv's
            // track-list observer does not reliably fire for externally added
            // (sub-add) tracks or for the demuxer entries of an
            // HLS/transcoded stream that resolve slightly after start-file,
            // so re-poll explicitly. (The two delayed slots moved ahead of
            // the fold application here; their bodies run >= 200 ms later
            // either way — the fold does not touch tracks.)
            core.flushPendingSubtitles()
            core.refreshTracks("start-file", delayMs = 200)
            core.refreshTracks("start-file-late", delayMs = 800)
        }

        override fun onBeforeFileLoaded() {
            // For HLS/transcoded streams the demuxer populates audio
            // track-list entries asynchronously after FILE_LOADED; a single
            // immediate read races ahead of that and yields an empty audio
            // picker. Re-poll after a short delay so late-arriving
            // audio/subtitle tracks are enumerated.
            core.refreshTracks("file-loaded")
            core.refreshTracks("file-loaded-late", delayMs = 500)
        }

        override fun onStyleApplied(style: SubtitleStyle) {
            // Runtime extras of the explicit style-apply funnel: the
            // sub-reload + debug render-state log (the shared applier owns
            // the property writes).
            mpvView?.mpv?.let { m ->
                runCatching { m.command(MpvProperties.CMD_SUB_RELOAD) }
            }
            logSubtitleRenderState("style")
        }

        override fun onUnsalvageableConfKeys(keys: Set<String>) {
            Log.w(
                TAG,
                "mpv.conf: values dropped by mpv's conf parser (unquoted # starts a comment; " +
                    "quote values like sub-color=\"#FF0000\"): $keys",
            )
        }

        override fun emitSubAdd(add: MpvSubtitleSideLoadPlan.SubAdd): Boolean {
            val m = mpvView?.mpv ?: return false
            val sub = add.source
            Log.d(
                TAG,
                "Adding Jellyfin subtitle to MPV: id=${sub.id}, label='${add.label}', lang=${sub.language}, " +
                    "codec=${sub.codec}, default=${sub.isDefault}, forced=${sub.isForced}, flags=${add.flags}, " +
                    "url=${redactSensitive(sub.url)}"
            )
            return try {
                // Android transport spelling: mpv cannot open File.toURI()'s
                // single-slash file:/ URIs ([mpvOpenableUrl]), and a blank
                // language OMITS the `lang` arg entirely (desktop passes "").
                val language = sub.language?.takeIf { it.isNotBlank() }
                if (language == null) {
                    m.command(MpvProperties.CMD_SUB_ADD, mpvOpenableUrl(sub.url), add.flags, add.label)
                } else {
                    m.command(MpvProperties.CMD_SUB_ADD, mpvOpenableUrl(sub.url), add.flags, add.label, language)
                }
                true
            } catch (e: Exception) {
                // Per-row containment lives here (inside the executor seam) so
                // one bad add never aborts the rest of the batch.
                Log.e(TAG, "Failed to add Jellyfin subtitle: ${redactSensitive(add.source.url)}", e)
                false
            }
        }

        override fun onSubAddOk(source: SubtitleSource, add: MpvSubtitleSideLoadPlan.SubAdd) {
            Log.d("SubtitleUse", "mpv sub-add ok: id=${source.id}, label='${add.label}', url=${redactSensitive(source.url)}")
            core.refreshTracks("addExternalSubtitle", delayMs = 500)
        }

        override fun onSubAddSkipped(reason: String) {
            Log.d(TAG, "Skipping duplicate subtitle ($reason)")
        }

        override fun onPendingSubtitlesFlushed() {
            logSubtitleRenderState("sub-add")
        }

        override fun subtitleDelayMs(): Long = currentConfig.subtitleDelayMs

        override fun fallbackFontFamily(): String? = fontProvider.bundledFallbackFamilyName()

        override fun subStartSeconds(): Double? = core.cachedSubStartSec.takeIf { it >= 0 }
    }

    /**
     * The shared mpv choreography ([MpvCore], player-video commonMain) over
     * this engine's [binding] and [coreHosts]: the property-intake funnel +
     * cached playback scalars, the fold wiring, the track/side-load
     * machinery, the transport + subtitle-style + aspect choreography and
     * the config-ownership snapshot. `by lazy` because the [MpvCore.Hosts]
     * callbacks close over `core` (and [MpvCore] takes the hosts) — lazy
     * breaks the reference cycle; every access happens post-construction.
     */
    private val core: MpvCore by lazy {
        MpvCore(
            host = MpvIntakeHost.ANDROID,
            binding = binding,
            isPlayingFlow = _isPlaying,
            playbackStateFlow = _playbackState,
            currentCuesSink = _currentCues,
            liveSubtitleCueSink = _liveSubtitleCue,
            bufferedRangesSink = _bufferedRanges,
            availableTracksSink = _availableTracks,
            errorSink = { error -> _errorFlow.tryEmit(error) },
            // Android publishes the buffered scalar from its ticker (clamped
            // to the duration) — no observer-cadence sink.
            bufferedSink = null,
            scopeProvider = { engineScope },
            hosts = coreHosts,
            // The Android seek path has always waited for the `time-pos`
            // observer (no write-through); its speed getter live-reads.
            eagerSeekPositionCache = false,
            cacheSpeedPropertyWrites = false,
            // The START_FILE flush covers the load-time batch; runtime adds
            // plan immediately (the shipped shape).
            queueSubtitlesBeforeFileLoaded = false,
        )
    }

    // The observer-cached playback scalars (`cachedPositionMs` family), the
    // side-loaded-subtitle id registry, the fold applier and the track
    // coalescer live on [MpvCore] now — the former private field mirrors were
    // the last hand-maintained twins.

    /**
     * Dedicated background thread for native mpv teardown. [release] is invoked
     * from the Compose `onDispose` on the main thread; `BaseMPVView.destroy()`
     * runs `mpv_terminate_destroy()` which synchronously tears down the GPU
     * context, demuxer, network threads, and libass — blocking for hundreds of
     * ms to seconds. Routing stop+destroy onto this thread keeps the main
     * looper responsive on
     * player close. Created lazily so non-mpv engines pay nothing.
     *
     * Self-healing like [BasePlayerEngine.engineScope]: every release quits the
     * thread, so each read must return a live generation — a Handler cached
     * across releases would post into the quit looper, which silently discards
     * the message and skips the destroy entirely.
     */
    private var releaseThreadGeneration: HandlerThread? = null

    private val releaseThread: HandlerThread
        get() = releaseThreadGeneration
            ?.takeIf { it.isAlive }
            ?: HandlerThread("MpvRelease", android.os.Process.THREAD_PRIORITY_BACKGROUND)
                .also { it.start() }
                .also { releaseThreadGeneration = it }
    private val releaseHandler: Handler get() = Handler(releaseThread.looper)

    private inner class PlayerMPVView(
        ctx: Context,
    ) : BaseMPVView(ctx, null) {

        /**
         * The observer is a pure TRANSLATOR now: native callback shapes →
         * the shared [MpvCore.onPropertyChange] / event entries. Extraction
         * quirks (the typed overloads, the released guards, the eof arm's
         * live pause read, the sid/aid debug log) stay — every DECISION is
         * the core's.
         */
        private val observer = object : MPV.EventObserver {
            override fun eventProperty(property: String) {}
            override fun eventProperty(property: String, value: Long) {
                core.onPropertyChange(property, MpvIntakeValue.Whole(value))
            }
            override fun eventProperty(property: String, value: Double) {
                core.onPropertyChange(property, MpvIntakeValue.Decimal(value))
            }
            override fun eventProperty(property: String, value: Boolean) {
                if (core.released) return
                core.onPropertyChange(
                    property,
                    MpvIntakeValue.Flag(value),
                    // Only the eof-reached arm's shipped body paid the live
                    // pause read (the fold's eof=false re-derivation input) —
                    // the conditional keeps the other flag arms JNI-free.
                    livePaused = if (property == MpvProperties.EOF_REACHED) {
                        binding.readFlag(MpvProperties.PAUSE)
                    } else {
                        true
                    },
                )
            }
            override fun eventProperty(property: String, value: String) {
                if (property == MpvProperties.SID || property == MpvProperties.AID) {
                    Log.d(TAG, "MPV $property changed to ${redactSensitive(value)}")
                }
                core.onPropertyChange(property, MpvIntakeValue.Text(value))
            }
            override fun eventProperty(property: String, value: MPVNode) {
                when (property) {
                    // the range-level buffered surface. Node arrives parsed;
                    // flattening + clamping is the shared pure derivation
                    // (see MpvCore.decodeBufferedRanges).
                    MpvProperties.DEMUXER_CACHE_STATE ->
                        core.onPropertyChange(property, MpvIntakeValue.Node, nodePayload = value.asPlainValue())
                    else -> core.onPropertyChange(property, MpvIntakeValue.Node)
                }
            }
            override fun event(eventId: Int, data: MPVNode) {
                if (core.released) return
                when (eventId) {
                    MPV.mpvEvent.MPV_EVENT_START_FILE -> {
                        Log.d(TAG, "MPV start file; adding ${core.pendingSubtitles.size} Jellyfin subtitle source(s)")
                        core.onStartFile()
                    }
                    MPV.mpvEvent.MPV_EVENT_FILE_LOADED -> {
                        Log.d(TAG, "MPV file loaded")
                        core.onFileLoaded()
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
            val configDir = mpvConfigDir
            mpv.setOptionString(MpvProperties.CONFIG, "yes")
            mpv.setOptionString(MpvProperties.CONFIG_DIR, configDir.absolutePath)

            val fontsDir = fontProvider.provideFontsDir()
            mpv.setOptionString(MpvProperties.SUB_FONTS_DIR, fontsDir.absolutePath)
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
            mpv.setOptionString(MpvProperties.SUB_FONT_PROVIDER, "none")
            // Default the requested family to the bundled fallback's own family
            // so libass matches it exactly under the none provider. Overridden
            // per-style by the shared MpvSubtitleStyleApplier when the user picks a
            // font, and ASS tracks ignore sub-font unless sub-ass-override=force.
            // Yields to a user-owned sub-font like every other styling key —
            // this is an init option, so an ungated write would beat mpv.conf.
            fontProvider.bundledFallbackFamilyName()?.let { mpv.safeSetOptionUnlessUserOwned(MpvProperties.SUB_FONT, it) }

            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()

            val hwdecValue = mpvCfg.hwdecOverride?.key ?: decoderModeToHwdec(currentConfig.decoderMode)
            mpv.setOptionString(MpvProperties.HWDEC, hwdecValue)
            mpv.setOptionString(MpvProperties.HWDEC_CODECS, "all")

            val aoValue = buildString {
                append(mpvCfg.audioOutput.key)
                mpvCfg.audioFallback?.let { append(",").append(it.key) }
            }
            mpv.setOptionString(MpvProperties.AO, aoValue)
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
            // These static sub-* options yield to user-owned keys (mpv.conf /
            // extra config) — sub-visibility and the margin pair are always
            // app-owned (runtime/UI-driven) and skip the check.
            mpv.safeSetOptionUnlessUserOwned(MpvProperties.SUB_SCALE_WITH_WINDOW, "no")
            mpv.safeSetOptionUnlessUserOwned(MpvProperties.SUB_AUTO, "fuzzy")
            mpv.setOptionString(MpvProperties.SUB_VISIBILITY, "yes")
            mpv.safeSetOptionUnlessUserOwned(MpvUserSubtitleKeys.ASS_OVERRIDE_KEY, "scale")
            mpv.setOptionString(MpvProperties.KEEP_OPEN, "yes")
            // The init-time half of the shared subtitle-style choreography:
            // option-string writes (the handle takes no runtime properties
            // yet), ownership-gated, reference-pinned font size —
            // [MpvSubtitleStyleApplier] owns the full table. This runs INSIDE
            // BaseMPVView.initialize(), before the view is published to
            // [mpvView] — so the writes go through the LOCAL handle via
            // [MpvSurface], not through [binding] (whose getter reads
            // mpvView). The runtime half of the choreography (applySubtitleStyle,
            // configureMpvForRequest) routes through the core instead.
            MpvSubtitleStyleApplier.apply(
                surface = MpvSurface(mpv),
                style = currentConfig.subtitleStyle,
                phase = MpvSubtitleStylePhase.INIT,
                ownedKeys = core.ownedStyleKeys,
                fallbackFontFamily = fontProvider.bundledFallbackFamilyName(),
                subtitleDelayMs = currentConfig.subtitleDelayMs,
            )
            mpv.setOptionString(MpvProperties.PANSCAN, "0.0")
            mpv.setOptionString(MpvProperties.SUB_USE_MARGINS, "no")
            mpv.setOptionString(MpvProperties.SUB_ASS_FORCE_MARGINS, "no")

            // The structured MpvEngineConfig → mpv mapping lives in the shared
            // MpvConfigMapping (desktop parity — this engine is no longer the
            // sole consumer): scale/deband/interpolation(+video-sync)/framedrop/
            // skiploopfilter/demuxer budgets/audio trio/extras, in that order,
            // with the user's mpvExtraConfig lines LAST (a raw line overrides
            // its structured counterpart). Init seeds the shared applier's
            // runtime diff cache (see onConfigChanged) with exactly the pairs
            // written here so a runtime push of an unchanged config performs
            // zero writes.
            val configPairs = MpvConfigMapping.configPairs(
                config = mpvCfg,
                audioPassthrough = currentConfig.audioPassthrough,
                passthroughCodecs = currentConfig.audioPassthroughCodecs,
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
            configApplier.seedAppliedConfigProps(configPairs.associate { it.key to it.value })

            // Force CPU-side AV1 film-grain synthesis. The GPU film-grain path
            // (default on hwdec) stalls on several drivers — frames back up and
            // playback stutters even though the decoder is keeping up. This is
            // the documented workaround for https://github.com/mpv-player/mpv/issues/14651
            // and is what mpvkt sets unconditionally.
            mpv.setOptionString(MpvProperties.VD_LAVC_FILM_GRAIN, "cpu")

            // Debug builds surface libass/vo/demuxer trace messages so subtitle
            // render/decode issues (font-provider death, empty bitmaps) are
            // visible without recompiling; release keeps warn to stay quiet and
            // cheap. Mirrors mpvkt's per-build msg-level (all=v debug / all=warn
            // release). A debug-build sub/ass trace is what pinpointed the
            // "can't find selected font provider" → empty-bitmap subtitle bug.
            val msgLevel = if (debugBuild) "all=v" else "all=warn"
            mpv.setOptionString(MpvProperties.MSG_LEVEL, msgLevel)

            // `fast` bundles vd-lavc-fast (skips some loop-filter / ref-frame
            // work) and cheap scaler defaults — a steady per-frame decode/render
            // saving mpvkt applies unconditionally. Previously gated to SW_ONLY
            // only, so the dominant HW path paid the full-quality decode cost
            // that ExoPlayer's MediaCodec pipeline never does.
            mpv.setOptionString(MpvProperties.PROFILE, "fast")
            if (currentConfig.decoderMode == DecoderMode.SW_ONLY && isLowRamDevice) {
                mpv.setOptionString(MpvProperties.VF, "format=yuv420p")
            }

            // Tag the output stream so Android routes it correctly (movie role →
            // speaker, ignores notifications).
            mpv.setOptionString(MpvProperties.AUDIO_SET_MEDIA_ROLE, "yes")

            mpv.setOptionString(
                MpvProperties.AUDIO_CHANNELS,
                MpvConfigMapping.effectiveAudioChannels(
                    mpvCfg.audioOutputMode,
                    currentConfig.audioEffects.channelMixMode,
                    currentConfig.audioEffects.channelMixEnabled,
                    currentConfig.audioEffects.maxAudioChannels,
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
                mpv.setOptionString(MpvProperties.AF, afFilters.joinToString(","))
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
            // time-pos MUST be observed as DOUBLE: as INT64, mpv emits
            // a property change only when the whole-second value
            // changes (1 update/sec), so currentPositionMs quantizes
            // to 1000ms steps. SyncPlay's correction loop compares it
            // against a continuously-advancing server clock and read
            // the 0..1000ms quantization gap as drift, SkipToSync-
            // seeking (and pulsing "Syncing") on most 2s correction
            // ticks — the endless syncing/synced cycle on mpv. DOUBLE
            // updates per frame, giving ms precision. (The DECISION for
            // each observed property lives in the shared MpvPropertyIntake
            // table; only the observe formats live here.)
            mpv.observeProperty(MpvProperties.PAUSE, MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty(MpvProperties.SPEED, MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty(MpvProperties.PAUSED_FOR_CACHE, MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty(MpvProperties.EOF_REACHED, MPV.mpvFormat.MPV_FORMAT_FLAG)
            mpv.observeProperty(MpvProperties.TIME_POS, MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty(MpvProperties.DURATION, MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty(MpvProperties.DEMUXER_CACHE_DURATION, MPV.mpvFormat.MPV_FORMAT_DOUBLE)
            mpv.observeProperty(MpvProperties.DEMUXER_CACHE_TIME, MPV.mpvFormat.MPV_FORMAT_INT64)
            // range-level buffered surface alongside the scalar
            // demuxer-cache-time observer above. NODE delivery — the Android
            // binding hands the parsed tree, no JNI re-read needed.
            mpv.observeProperty(MpvProperties.DEMUXER_CACHE_STATE, MPV.mpvFormat.MPV_FORMAT_NODE)
            mpv.observeProperty(MpvProperties.SID, MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty(MpvProperties.AID, MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty(MpvProperties.TRACK_LIST, MPV.mpvFormat.MPV_FORMAT_NODE)
            mpv.observeProperty(MpvProperties.SUB_VISIBILITY, MPV.mpvFormat.MPV_FORMAT_FLAG)
            // G10: subtitle-sync preview for embedded subs. sub-text fires on
            // each displayed-line change; sub-start gives its media-time start.
            // Both together let us accumulate a TimedCue list as subs play.
            mpv.observeProperty(MpvProperties.SUB_TEXT, MPV.mpvFormat.MPV_FORMAT_STRING)
            mpv.observeProperty(MpvProperties.SUB_START, MPV.mpvFormat.MPV_FORMAT_DOUBLE)
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
            try { mpv.setPropertyInt(MpvProperties.AUDIOTRACK_SESSION_ID, sid) } catch (_: Exception) {}
            try { mpv.setPropertyInt(MpvProperties.AAUDIO_SESSION_ID, sid) } catch (_: Exception) {}
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
        // Engine may have been release()d and is being reused — beginLoad
        // clears the teardown guard + per-item state (pending batch, fold
        // latches, side-load registry, server duration rung, cue flows) so
        // observer callbacks are honoured again with fresh fold state.
        core.beginLoad(request)
        // Reset the observer-cached playback scalars for the new item; the
        // first time-pos / duration observations repopulate them as the
        // demuxer resolves. The playhead seeds from the request's start.
        core.resetPlaybackCaches(positionMs = request.startPositionMs)
        pendingRequest = request
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
        // the async native destroy bails at the core's `released` guard
        // instead of mutating state (e.g. a late `pause=false` would otherwise
        // flip _isPlaying back true on a half-destroyed engine).
        core.released = true
        pendingRequest = null
        core.clearPendingSubtitles()
        core.resetSideLoadedRegistry()
        core.resetFoldLatches()
        // Note: there is no AudioManager.releaseAudioSessionId() —
        // Android's AudioSystem reclaims unreferenced session ids, so the
        // prior allocation via generateAudioSessionId() has no manual release.
        // Just drop our handle so the next load() allocates a fresh one.
        generatedAudioSessionId = 0
        mainHandler.removeCallbacksAndMessages(null)
        // Cancel the coalescer's pending debounce before the scope cancel so a
        // not-yet-fired buildTracks() can never race mpv_terminate_destroy.
        // Because buildTracks() now runs on the main thread (see the
        // scheduleTrackRefresh host), any read that already started is
        // guaranteed to finish before this release() runs — the main looper
        // is single-threaded.
        core.cancelTrackRefresh()
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
                try { it.mpv.command(MpvProperties.CMD_STOP) } catch (_: Exception) {}
                try { it.destroy() } catch (e: Exception) { Log.w(TAG, "destroy", e) }
            }
        }
        // Published-flow resets live in BasePlayerEngine (C5); mpv's residue
        // (live-subtitle mirror + cached position/duration/buffer reads) goes
        // through the hook that reset fires.
        resetPublishedEngineState()
        core.serverDurationMs = 0L
        // The scope stays cancelled here; a re-used engine gets a fresh live
        // generation from BasePlayerEngine's self-healing engineScope on its
        // next read, so the former post-release revive is not needed.
        // Stop the dedicated release thread once the engine is fully
        // torn down. The last scheduled runnable has already captured `view`
        // and will run to completion, but no new work can be enqueued because
        // mpvView is null. The self-healing accessor resurrects the thread if
        // the engine is ever re-used.
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
        core.resetPlaybackCaches()
    }

    override fun play() {
        core.play()
    }

    override fun pause() {
        core.pause()
    }

    override fun stop() {
        core.commandStop()
    }

    override fun seekTo(positionMs: Long) {
        core.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        core.setPlaybackSpeed(speed)
    }

    // The structured-config runtime diff cache lives on the shared
    // [MpvConfigApplier] below (`lastAppliedConfigProps` — seeded by
    // initOptions with the pairs it wrote, so onConfigChanged writes only
    // actual CHANGES). Same discipline as the desktop engine's former
    // lastApplied* caches — an unchanged re-write is at best noise and at
    // worst (af/vf-class properties) a pipeline re-init.

    /** mpv's config-dir (`<filesDir>/mpv`); the user's `mpv.conf` lives here. */
    private val mpvConfigDir: java.io.File get() = java.io.File(context.filesDir, "mpv")

    /**
     * Reads the user's mpv.conf (the Android ownership source — the desktop
     * runs config=no) and hands it plus the in-app extra config to the shared
     * [MpvCore.refreshUserOwnedSubtitleKeys], which owns the snapshot + the
     * unsalvageable-key report. Refreshed at surface-create (before
     * initOptions runs, since init writes must already respect conf
     * ownership) and on every config change; every subtitle-style write site
     * consults it so the user's value wins for the whole session (issue #165).
     */
    private fun refreshUserOwnedSubtitleKeys() {
        val confFile = java.io.File(mpvConfigDir, "mpv.conf")
        val confText = if (confFile.isFile) {
            try { confFile.readText() } catch (_: Exception) { null }
        } else null
        val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()
        core.refreshUserOwnedSubtitleKeys(confText, mpvCfg.mpvExtraConfig)
    }

    /**
     * The shared config-delta dispatcher (player-contract, the
     * [MpvFoldApplier] family): it owns the arm ORDER (ownership refresh →
     * audio-delay → sub-delay → hwdec → shared pairs → subtitle style →
     * audio → video — the ladder this engine's hand-mirrored body ran) and
     * the genuinely-shared arms; this engine contributes only its native
     * surfaces — the `hwdecOverride`-aware hwdec value, the `ao` chain +
     * AudioEffect-session arms inside the audio hook, and the low-RAM demuxer
     * extra. The runtime diff cache (`lastAppliedConfigProps`, seeded by
     * `initOptions`) lives on the applier.
     */
    private val configApplier = MpvConfigApplier(
        surface = { if (binding.isAlive()) binding else null },
        extras = { MpvConfigApplier.Extras(lowRamDevice = isLowRamDevice) },
        refreshOwnedKeys = ::refreshUserOwnedSubtitleKeys,
        hwdecValue = { cfg ->
            (cfg.engineSpecific as? MpvEngineConfig)?.hwdecOverride?.key
                ?: decoderModeToHwdec(cfg.decoderMode)
        },
        applySubtitleStyle = { cfg -> core.applySubtitleStyleRuntime(cfg.subtitleStyle) },
        applyAudioEffects = { old, new, delta, _ -> applyAndroidAudioArms(old, new, delta) },
        applyVideoEffects = { cfg -> applyVideoFilters(cfg.videoEffects) },
    )

    override fun onConfigChanged(oldConfig: EngineConfig, newConfig: EngineConfig) {
        configApplier.applyDelta(oldConfig, newConfig)
    }

    /**
     * This engine's audio arms inside the shared dispatch's audio hook —
     * the bodies (and their internal arm conditions) are Android-native and
     * stay here verbatim; only their ORDER-anchored position moved into the
     * applier (they always ran after the subtitle-style re-apply, which is
     * where the applier invokes the hook).
     */
    private fun applyAndroidAudioArms(oldConfig: EngineConfig, newConfig: EngineConfig, delta: EngineConfigDelta) {
        try {
            val mpv = mpvView?.mpv ?: return
            val mpvCfg = newConfig.engineSpecific as? MpvEngineConfig ?: MpvEngineConfig()
            val oldMpvCfg = oldConfig.engineSpecific as? MpvEngineConfig

            if (oldMpvCfg?.audioOutput != mpvCfg.audioOutput || oldMpvCfg?.audioFallback != mpvCfg.audioFallback) {
                val aoValue = buildString {
                    append(mpvCfg.audioOutput.key)
                    mpvCfg.audioFallback?.let { append(",").append(it.key) }
                }
                mpv.setPropertyString(MpvProperties.AO, aoValue)
            }

            if (delta.channelMixChanged || oldMpvCfg?.audioOutputMode != mpvCfg.audioOutputMode) {
                // The output mode's STEREO forced downmix folds into the same
                // audio-channels write (the effects chain stays the single
                // writer of this pipeline re-initing property); the channel
                // cap rides along when the effects chain has no opinion.
                mpv.setPropertyString(
                    MpvProperties.AUDIO_CHANNELS,
                    MpvConfigMapping.effectiveAudioChannels(
                        mpvCfg.audioOutputMode,
                        newConfig.audioEffects.channelMixMode,
                        newConfig.audioEffects.channelMixEnabled,
                        newConfig.audioEffects.maxAudioChannels,
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
                    mpv.setPropertyString(MpvProperties.AF, filterString)
                } else {
                    mpv.command(MpvProperties.CMD_AF_CLR, "clr", "")
                }
            }

            if (delta.audioSessionEffectsChanged) {
                applyAndroidAudioEffects()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reconfigure MPV audio effects", e)
        }
    }

    /**
     * Pushes the video-effects config onto mpv. The chain body is the shared
     * [MpvVideoEffectChain] (the desktop builder moved to player-contract —
     * one parity table, one stage order, one number format for both hosts);
     * this engine keeps only its transport writes and the declared
     * rotation-write divergence (FORMAT_DOUBLE here — this binding accepts
     * it; the desktop's JNA transport needs the string form). The caller's
     * `delta.videoEffectsChanged` guard is the diff discipline (this engine
     * has no vf diff cache — the delta fires only on a real slice change).
     */
    private fun applyVideoFilters(effects: VideoEffectsConfig) {
        try {
            val chain = MpvVideoEffectChain.buildVfChain(effects)
            val rawDiscrete = kotlin.math.round(effects.rotationDegrees / 90f).toInt() * 90
            val discrete = ((rawDiscrete % 360) + 360) % 360
            mpvView?.mpv?.setPropertyDouble(MpvProperties.VIDEO_ROTATE, discrete.toDouble())

            if (chain != null) {
                mpvView?.mpv?.setPropertyString(MpvProperties.VF, chain)
            } else {
                mpvView?.mpv?.command(MpvProperties.CMD_VF_CLR, "clr", "")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply MPV video filters", e)
        }
    }

    override fun selectTrack(type: TrackType, index: Int) {
        Log.d(TAG, "Selecting MPV ${type.name.lowercase()} track id=$index")
        // The selection DECISIONS (deselect arms, int-then-string, the
        // subtitle arm's sub-visibility re-enable) are the core's; this
        // engine keeps the log + the immediate refresh + the debug
        // render-state log that shipped around them.
        core.selectTrack(type, index)
        core.refreshTracks("select-${type.name.lowercase()}")
        logSubtitleRenderState("select-${type.name.lowercase()}")
    }

    /**
     * Selects a secondary subtitle track rendered alongside the primary (G4).
     * mpv supports this natively via `secondary-sid`; the secondary track renders
     * above the primary by default. An [index] < 0 clears it ("no").
     */
    override fun setSecondarySubtitleTrack(index: Int) {
        Log.d(TAG, "Selecting MPV secondary subtitle track id=$index")
        core.setSecondarySubtitleTrack(index)
    }

    override fun setMaxVideoBitrate(bps: Int?) {
        // Intentional no-op. MPV plays single-URL streams (not adaptive
        // manifests), so there is no variant ladder to cap — the only lever
        // would be requesting a transcode from the server, which the
        // ViewModel already negotiates via PlaybackRepository before load().
    }

    override val volume: Float
        get() = try {
            ((mpvView?.mpv?.getPropertyDouble(MpvProperties.VOLUME) ?: 100.0) / 100.0).toFloat().coerceIn(0f, 1f)
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
        ((m.getPropertyDouble(MpvProperties.VOLUME) ?: 100.0) / 100.0).toFloat()
    } catch (_: Exception) { null }

    override fun applyNativeVolume(normalized: Float) {
        // Throws through to dispatchVolumeCommand's catch on a failed write —
        // the former bodies skipped the system-stream sync in that case too.
        mpvView?.mpv?.setPropertyDouble(MpvProperties.VOLUME, normalized * 100.0)
    }

    override fun nativeVolumeRestore(muted: Boolean): PlaybackVolumePolicy.NativeVolumeRestore =
        PlaybackVolumePolicy.NativeVolumeRestore.LEAVE_UNCHANGED

    override fun applyNativeMuteFlag(muted: Boolean) {
        try { mpvView?.mpv?.setPropertyBoolean(MpvProperties.MUTE, muted) } catch (_: Exception) {}
    }

    override fun createSurfaceView(context: Context): View {
        // mpv-android-lib pins a JNI global ref to whatever Context flows
        // through BaseMPVView.initialize() → MPV.create() → nativeCreate(),
        // and that ref can outlive destroy() (MPV.destroy runs
        // destroySession() before nativeDestroy(), so a throw from the former
        // skips the ref cleanup). The Compose AndroidView factory hands us the
        // Activity here — feeding it in pinned every destroyed PlayerActivity
        // under "GC Root: Global variable in native code" (853 kB per close,
        // LeakCanary-verified). The application context is process-lifetime,
        // so a surviving native ref on it is harmless.
        val viewContext = context.applicationContext
        val fontsDir = fontProvider.provideFontsDir()
        val configDir = mpvConfigDir
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
            PlayerMPVView(viewContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create PlayerMPVView", e)
            return View(viewContext).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        }

        try {
            val mpvCfg = (currentConfig.engineSpecific as? MpvEngineConfig) ?: MpvEngineConfig()
            // Conf ownership must be known BEFORE initialize(): initOptions'
            // setOptionString writes run inside it and already have
            // command-line priority over mpv.conf, so they are the first
            // writer that must yield to user-owned sub-* keys.
            refreshUserOwnedSubtitleKeys()
            view.initialize(configDir.absolutePath, context.cacheDir.absolutePath)
            view.setVo(mpvCfg.videoOutput.key)
            core.applySubtitleStyleRuntime(currentConfig.subtitleStyle)
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
        // mpv applies styles via properties, not via a View. The RUNTIME
        // funnel of the shared choreography ([MpvCore]): the
        // [MpvSubtitleStyleApplier] writes + this engine's sub-reload /
        // render-state-log extras via [MpvCore.Hosts.onStyleApplied].
        core.applySubtitleStyleRuntime(style)
    }

    override fun setAspectRatio(ratio: AspectRatio) {
        core.setAspectRatio(ratio)
    }

    /**
     * Toggles mpv's native subtitle rendering via the live `sub-visibility`
     * property — the shared [MpvCore] write (the screen hides native subs
     * (`visible = false`) while it renders the zoom-safe Compose overlay from
     * [liveSubtitleCue], so captions aren't double-drawn, and restores them
     * the moment zoom returns to 1 — full libass fidelity).
     */
    override fun setNativeSubtitlesVisible(visible: Boolean) {
        core.setNativeSubtitlesVisible(visible)
    }

    override val currentPositionMs: Long
        get() = core.cachedPositionMs

    // G10: the sub-text cue accumulation is the shared [MpvCore.accumulateSubText]
    // (the fold's onLiveSubtitleLine sink); this engine's declared start-time
    // source — the EVENT-CACHED `sub-start` — is the [MpvCore.Hosts.subStartSeconds]
    // override above.

    override val durationMs: Long
        // The shared engine→server fallback ladder ([MpvCore.effectiveDurationMs]):
        // the mpv demuxer's duration when available; the server-reported
        // runTimeTicks otherwise (HLS/transcoded streams where `duration`
        // resolves 0/partial).
        get() = core.effectiveDurationMs

    override val playbackSpeed: Float
        get() = try {
            mpvView?.mpv?.getPropertyDouble(MpvProperties.SPEED)?.toFloat() ?: 1f
        } catch (_: Exception) { 1f }

    override val audioSessionId: Int
        get() = generatedAudioSessionId

    override val positionFlow: Flow<Long> = positionFlowWithTicker {
        // Push the observer-cached position downstream. No JNI here:
        // time-pos / demuxer-cache-duration / duration are observed properties
        // whose callbacks populate [MpvCore]'s cached fields. This is the hot
        // path (fires every poll) so keeping it allocation- and JNI-free
        // eliminates the primary source of main-thread jank during mpv
        // playback.
        val posMs = core.cachedPositionMs
        val dur = durationMs
        if (dur > 0L) {
            _bufferedPositionMs.value = core.cachedBufferedPositionMs.coerceAtMost(dur)
        } else {
            _bufferedPositionMs.value = core.cachedBufferedPositionMs
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
            // The shared property-name table + sanitize fold lives in
            // [MpvStatsProjection] (player-contract, desktop parity); this
            // adapter owns only the reads seam, the change-guard and its
            // FULL_STATS_REREAD cadence.
            val reads = MpvStatsReadSource(m)
            val scalars = MpvStatsProjection.readGuardScalars(reads)
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
            if (last != null && last.videoBitrate == scalars.videoBitrateBps &&
                last.audioBitrate == scalars.audioBitrateBps &&
                last.bufferedPositionMs == bufferedPositionMs &&
                last.droppedFrames == scalars.droppedFrames &&
                last.totalVideoFrames == scalars.totalVideoFrames &&
                nowMs - lastFullStatsReadMs < FULL_STATS_REREAD_MS
            ) {
                return
            }
            lastFullStatsReadMs = nowMs

            publishStatsIfChanged(
                MpvStatsProjection.project(
                    reads = reads,
                    scalars = scalars,
                    positionMs = posMs,
                    bufferedPositionMs = bufferedPositionMs,
                ),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read MPV video stats", e)
        }
    }

    /**
     * The Android [MpvStatsReads] over the mpv wrapper: each read absorbs the
     * wrapper's throw-on-unavailable contract (the former inline
     * try/getProperty/catch-null shape and the propDoubleOrNull helpers,
     * folded into the seam).
     */
    private class MpvStatsReadSource(private val mpv: MPV) : MpvStatsReads {
        override fun readString(name: String): String? =
            try { mpv.getPropertyString(name) } catch (_: Exception) { null }

        override fun readDouble(name: String): Double? =
            try { mpv.getPropertyDouble(name) } catch (_: Exception) { null }

        override fun readLong(name: String): Long? =
            try { mpv.getPropertyInt(name)?.toLong() } catch (_: Exception) { null }
    }

    // The track machinery (buildTracks / readTrackEntries / the MPVNode
    // flattening / existingSubLabels / the live pause read) lives on
    // [MpvCore] over [binding] — the former private twins were byte-for-byte
    // the desktop's [MpvLib.readNode] shapes.

    /**
     * Handle an [MPV.mpvEvent.MPV_EVENT_END_FILE] event: this engine keeps
     * only the node decode (the payload's `reason` int + `error` string) and
     * hands both to the shared [MpvCore.onEndFile] — the fold, the
     * error-emission ordering and the taxonomy mapping live there (see
     * [MpvPlaybackEvent.EndFileReason] for the shared per-reason semantics).
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
        core.onEndFile(reason, MpvEndFileError.StringCode(errorCode))
    }

    private fun configureMpvForRequest(view: PlayerMPVView, request: PlaybackRequest) {
        // mTLS: the tls-* options are file-path options that
        // PERSIST on the view's mpv handle across loads — write the reset
        // trio when no certificate is active (same discipline as
        // http-header-fields below) so the previous item's credentials are
        // never inherited.
        MpvTlsOptions.from(request.requestSpecific?.tls).forEach { (option, value) ->
            try {
                view.mpv.setOptionString(option, value)
                view.mpv.setPropertyString(option, value)
            } catch (_: Exception) {}
        }

        if (request.startPositionMs > 0) {
            val startVal = "+${request.startPositionMs / 1000.0}"
            try { view.mpv.setOptionString(MpvProperties.START, startVal) } catch (_: Exception) {}
            try { view.mpv.setPropertyString(MpvProperties.START, startVal) } catch (_: Exception) {}
        }

        view.mpv.setOptionString(MpvProperties.SUB_VISIBILITY, "yes")
        view.mpv.setPropertyBoolean(MpvProperties.SUB_VISIBILITY, true)
        request.preferredAudioLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString(MpvProperties.ALANG, normalizeLanguageList(language))
        }
        request.preferredSubtitleLanguage?.takeIf { it.isNotBlank() }?.let { language ->
            view.mpv.setOptionString(MpvProperties.SLANG, normalizeLanguageList(language))
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
                try { view.mpv.setOptionString(MpvProperties.USER_AGENT, userAgent) } catch (_: Exception) {}
                try { view.mpv.setPropertyString(MpvProperties.USER_AGENT, userAgent) } catch (_: Exception) {}
            }
            val headerStr = request.headers.entries
                .filter { !it.key.equals("User-Agent", ignoreCase = true) }
                .joinToString(",") { "${it.key}: ${it.value}" }
            if (headerStr.isNotBlank()) {
                try { view.mpv.setOptionString(MpvProperties.HTTP_HEADER_FIELDS, headerStr) } catch (_: Exception) {}
                try { view.mpv.setPropertyString(MpvProperties.HTTP_HEADER_FIELDS, headerStr) } catch (_: Exception) {}
            }
            Log.d(TAG, "Applied MPV HTTP headers: ${request.headers.keys}")
        }

        try {
            // Runtime choreography via the shared applier (no sub-reload /
            // render-state log here — those extras belong to the explicit
            // style-apply funnel). The local-handle surface (not [binding]):
            // this can run before the view is published to mpvView.
            MpvSubtitleStyleApplier.apply(
                surface = MpvSurface(view.mpv),
                style = currentConfig.subtitleStyle,
                phase = MpvSubtitleStylePhase.RUNTIME,
                ownedKeys = core.ownedStyleKeys,
                fallbackFontFamily = fontProvider.bundledFallbackFamilyName(),
                subtitleDelayMs = currentConfig.subtitleDelayMs,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply subtitle style inside configureMpvForRequest", e)
        }
    }

    // The pending-subtitle flush (the START_FILE batch) is the shared
    // [MpvCore.flushPendingSubtitles] — planBatch (dedupe against the live
    // track-list, same-title uniquify, isDefault→"select"), the registry
    // store-back BEFORE any add executes, each planned add through
    // [MpvCore.Hosts.emitSubAdd] (this engine's `sub-add` transport spelling),
    // then the render-state debug log via onPendingSubtitlesFlushed.

    override fun addExternalSubtitle(source: SubtitleSource) {
        // The shared plan skips true re-adds (double-tap, re-attach after a
        // config reload) and uniquifies same-label different-source subs —
        // see [MpvSubtitleSideLoadPlan.planRuntimeAdd] for why skipping those
        // would strand the row. The ok extras (the SubtitleUse log + the
        // delayed track re-poll) ride [MpvCore.Hosts.onSubAddOk].
        core.addExternalSubtitle(source)
    }

    private fun normalizeLanguageList(language: String): String =
        language.split(',', ';')
            .map { it.trim().replace('_', '-') }
            .filter { it.isNotBlank() }
            .joinToString(",")

    /**
     * The one Android-side node-transport seam of the shared parses (the
     * desktop's `MpvLib.readNode` produces this shape natively): this JNI
     * binding's [MPVNode] tree flattened to plain Kotlin values
     * (Map/List/String/Long/Double/Boolean). `asMap`/`asArray` discriminate
     * the container nodes (the scalar accessors return null off-type, the
     * shape the former inline asTrackEntry extraction relied on too);
     * byte-array nodes have no plain form in a track-list and flatten to null.
     * Shared by the observer's event-carried nodes and the binding's
     * property re-reads.
     */
    private fun MPVNode.asPlainValue(): Any? {
        asMap()?.let { map -> return map.mapValues { it.value.asPlainValue() } }
        asArray()?.let { array -> return array.map { it.asPlainValue() } }
        return asString() ?: asInt() ?: asDouble() ?: asBoolean()
    }

    /**
     * The Android [MpvBinding] over the `is.xyz.mpv` wrapper: the platform
     * seam of the shared [MpvCore]. Absorbs the wrapper's throw-on-failure
     * contract (log + no rethrow) exactly the way the former private
     * safe-setter family and `MpvSurface` did, so the shared choreography is
     * exception-free; commands propagate their throw (the core's
     * transport-error seam keeps the shipped per-op logs); reads default
     * exactly where the former inline try/getProperty/catch bodies did.
     */
    private inner class AndroidMpvBinding : MpvBinding {

        private val mpv: MPV? get() = mpvView?.mpv

        override fun isAlive(): Boolean = mpv != null

        private inline fun safely(what: String, block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set $what", e)
            }
        }

        override fun setOptionString(name: String, value: String) =
            safely("option $name to $value") { mpv?.setOptionString(name, value) }

        override fun setPropertyString(name: String, value: String) =
            safely("property $name to $value") { mpv?.setPropertyString(name, value) }

        override fun setPropertyDouble(name: String, value: Double) =
            safely("property $name to $value") { mpv?.setPropertyDouble(name, value) }

        override fun setPropertyInt(name: String, value: Int) =
            safely("property $name to $value") { mpv?.setPropertyInt(name, value) }

        override fun setPropertyBoolean(name: String, value: Boolean) =
            safely("property $name to $value") { mpv?.setPropertyBoolean(name, value) }

        override fun command(vararg args: String): Boolean {
            val m = mpv ?: return false
            m.command(*args)   // throws on rejection — the caller's catch owns the log
            return true
        }

        override fun readFlag(name: String): Boolean = try {
            mpv?.getPropertyBoolean(name) ?: true
        } catch (_: Exception) {
            true
        }

        override fun readDouble(name: String): Double? = try {
            mpv?.getPropertyDouble(name)
        } catch (_: Exception) {
            null
        }

        override fun readString(name: String): String? = try {
            mpv?.getPropertyString(name)
        } catch (_: Exception) {
            null
        }

        override fun readNode(name: String): Any? = try {
            mpv?.getPropertyNode(name)?.asPlainValue()
        } catch (_: Exception) {
            null
        }

        override fun writeIntOrString(name: String, value: Int): Boolean {
            val m = mpv ?: return false
            return try {
                m.setPropertyInt(name, value)
                true
            } catch (_: Exception) {
                m.setPropertyString(name, value.toString())
                false
            }
        }
    }

    /**
     * The absorbing [MpvPropertySurface] over a LOCAL mpv handle — the
     * init-time and per-request write seam (BaseMPVView.initialize's
     * initOptions and configureMpvForRequest run before/around the view being
     * published to [mpvView], so [binding]'s getter cannot see the handle
     * yet). The runtime choreography routes through [binding] instead.
     */
    private inner class MpvSurface(private val mpv: MPV) : MpvPropertySurface {
        private inline fun safely(what: String, block: () -> Unit) {
            try {
                block()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set $what", e)
            }
        }

        override fun setOptionString(name: String, value: String) =
            safely("option $name to $value") { mpv.setOptionString(name, value) }

        override fun setPropertyString(name: String, value: String) =
            safely("property $name to $value") { mpv.setPropertyString(name, value) }

        override fun setPropertyDouble(name: String, value: Double) =
            safely("property $name to $value") { mpv.setPropertyDouble(name, value) }

        override fun setPropertyInt(name: String, value: Int) =
            safely("property $name to $value") { mpv.setPropertyInt(name, value) }

        override fun setPropertyBoolean(name: String, value: Boolean) =
            safely("property $name to $value") { mpv.setPropertyBoolean(name, value) }
    }

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
        if (core.released) return
        val m = mpvView?.mpv ?: return
        try {
            val sid = m.getPropertyString(MpvProperties.SID)
            val visible = m.getPropertyBoolean(MpvProperties.SUB_VISIBILITY)
            val subText = try { m.getPropertyString(MpvProperties.SUB_TEXT) } catch (_: Exception) { null }
            val selected = _availableTracks.value.firstOrNull { it.type == TrackType.SUBTITLE && it.isSelected }
            Log.d(
                TAG,
                "MPV subtitle render state ($reason): sid=$sid, visible=$visible, " +
                    "fontSize=${m.getPropertyDouble(MpvProperties.SUB_FONT_SIZE)}, marginY=${m.getPropertyInt(MpvProperties.SUB_MARGIN_Y)}, " +
                    "pos=${m.getPropertyInt(MpvProperties.SUB_POS)}, selected=${selected?.index}:${selected?.label}, " +
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

    // The former safe-setter family died with the shared choreography: every
    // subtitle-style write now runs through [MpvSurface] /
    // [MpvSubtitleStyleApplier] (player-contract), which owns the typed writes
    // AND the issue-#165 ownership gating. These two option-string helpers
    // remain for initOptions' static sub-* options (sub-font seed,
    // sub-scale-with-window, sub-auto, sub-ass-override), which predate the
    // applier's style-shaped choreography.
    private fun MPV.safeSetOption(name: String, value: String) {
        try {
            setOptionString(name, value)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set option $name to $value", e)
        }
    }

    // Ownership-gated variant (issue #165): a no-op when the key is user-owned
    // via mpv.conf / extra config, so no scalar write site can forget the
    // check the pair lists get from MpvUserSubtitleKeys.filterOwned. Keys the
    // app functionally drives at runtime (sub-visibility, sub-delay) never
    // route through this. The snapshot lives on the shared [MpvCore].
    private fun MPV.safeSetOptionUnlessUserOwned(name: String, value: String) {
        if (name in core.ownedStyleKeys) return
        safeSetOption(name, value)
    }
}

// The pure option/property mapping helpers (decoderModeToHwdec,
// channelMixModeToAudioChannels, audioNormalizationModeToAfFilter) moved to
// commonMain (MpvAudioMappings.kt) so the tables stay unit-testable from
// jvmTest; same package, call-sites unchanged.
