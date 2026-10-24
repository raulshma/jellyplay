package com.raulshma.jellyplay.feature.player.video.engine

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleCallbacks
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPassthroughCodec
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.DeinterlaceMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EngineSpecificConfig
import com.raulshma.jellyplay.core.model.EqualizerSettings
import com.raulshma.jellyplay.core.model.HdrFormatPreference
import com.raulshma.jellyplay.core.model.MaxAudioChannelsEnum
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlaybackTls
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.TrackType
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PlaybackRequest(
    val uri: String,
    val title: String,
    val startPositionMs: Long = 0,
    val artworkUri: String? = null,
    val externalSubtitles: List<SubtitleSource> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    /**
     * Engine-level audio-description bias: when true the Exo adapter adds
     * `ROLE_FLAG_DESCRIBES_VIDEO` to its preferred audio role flags, so the
     * platform track selector starts out leaning toward AD narration before
     * the helper's label-bridge rung makes the authoritative pick. Engines
     * without a role-flag concept (mpv/VLC) ignore it — their AD handling is
     * title-based in the selection policy.
     */
    val preferAudioDescription: Boolean = false,
    val maxVideoBitrate: Int? = null,
    val serverUrl: String? = null,
    val authToken: String? = null,
    /**
     * Server-resolved play method for [uri], when known. Engines use it to
     * scope byte-level stream caching to content-stable URLs: only Direct
     * Play / Direct Stream may hit the cache, while Transcode sessions (and
     * HLS remux playlists served off session-keyed transcode URLs) would
     * churn it. `null` when the caller performed no server resolution
     * (offline files, local preview hosts) — treated as non-cacheable.
     */
    val playMethod: PlayMethod? = null,
    val minBufferMs: Int = 15_000,
    val maxBufferMs: Int = 50_000,
    /**
     * Server-reported total runtime in milliseconds, derived from the media
     * item's `runTimeTicks`. UNIVERSAL (not engine-specific): both the Exo
     * and mpv adapters use it as the duration fallback when the demuxer
     * cannot resolve one for HLS/transcoded streams — on mpv because the
     * `duration` property is frequently 0 or only partially resolved there.
     * `0` when no server runtime is available (e.g. unknown-length items).
     */
    val serverDurationMs: Long = 0L,
    /**
     * The sanctioned per-engine escape hatch (the same slot
     * [EngineConfig.engineSpecific] rides). Carries the members only SOME
     * adapters consume — the Exo-side normalization gain + MIME hint, the
     * mpv-side TLS file paths — behind the single
     * [com.raulshma.jellyplay.core.model.PlaybackRequestSpecific] request
     * variant (see its KDoc for why it is one variant, not one per engine).
     * Read it through the typed [requestSpecific] projection; engines that
     * consume nothing ignore the slot entirely.
     */
    val engineSpecific: EngineSpecificConfig? = null,
) {
    /**
     * Typed view of [engineSpecific]: the request-side payload the adapters
     * unpack (Exo: gain + MIME; mpv: TLS paths), or `null` when the caller
     * supplied none (or a config-side variant — a type error at the call
     * site, tolerated here the way [EngineConfig.engineSpecific] is).
     */
    val requestSpecific: com.raulshma.jellyplay.core.model.PlaybackRequestSpecific?
        get() = engineSpecific as? com.raulshma.jellyplay.core.model.PlaybackRequestSpecific
}

/**
 * Pure mapping from [com.raulshma.jellyplay.core.model.PlaybackTls] onto
 * mpv's TLS options: the three `tls-*` file-path options, or the reset
 * (empty-value) writes when no certificate is active — the same diff-then-write
 * discipline `http-header-fields` has (options PERSIST on the mpv context, so
 * the previous item's credentials must be cleared, never inherited). Shared by
 * the Android and desktop mpv engines so the emitted option set cannot drift
 * between platforms.
 *
 * Option names are the CURRENT mpv surface (`tls-cert-file` / `tls-key-file`
 * / `tls-ca-file`, verified against the bundled libmpv v0.40): older mpv
 * spelled the client pair `tls-cert`/`tls-key` and the plan's draft named
 * them `tls-client-cert`/`tls-client-key` — neither exists today, and
 * unknown option writes fail silently on libmpv.
 */
object MpvTlsOptions {

    private const val OPTION_CLIENT_CERT = "tls-cert-file"
    private const val OPTION_CLIENT_KEY = "tls-key-file"
    private const val OPTION_CA_FILE = "tls-ca-file"

    /** The option writes the request's `requestSpecific.tls` translates to. */
    fun from(tls: PlaybackTls?): List<Pair<String, String>> = when (tls) {
        null -> listOf(
            OPTION_CLIENT_CERT to "",
            OPTION_CLIENT_KEY to "",
            OPTION_CA_FILE to "",
        )
        else -> buildList {
            add(OPTION_CLIENT_CERT to tls.clientCertificatePath)
            add(OPTION_CLIENT_KEY to tls.clientKeyPath)
            tls.caPath?.let { add(OPTION_CA_FILE to it) }
        }
    }
}

data class SubtitleSource(
    val url: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val id: String,
)

data class EngineConfig(
    val decoderMode: DecoderMode = DecoderMode.HW_PREFERRED,
    val audioPassthrough: Boolean = false,
    /**
     * The per-codec passthrough allow-list under [audioPassthrough]: mpv
     * composes its `audio-spdif` list from the enabled codecs, libVLC its
     * `--codec` allowlist. Consumed by engines with a passthrough surface —
     * ExoPlayer (decode-to-PCM only) ignores it.
     */
    val audioPassthroughCodecs: Set<AudioPassthroughCodec> = AudioPassthroughCodec.ALL,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val audioEffects: AudioEffectsConfig = AudioEffectsConfig(),
    val videoEffects: VideoEffectsConfig = VideoEffectsConfig(),
    val engineSpecific: EngineSpecificConfig? = null,
    /**
     * Deinterlacing: mpv `deinterlace` on the mpv engines; other
     * engines no-op it. SESSION-SCOPED on the player surface (gear-menu cycle,
     * folded into every config build while the session lives, reverted on
     * exit) — never persisted.
     */
    val deinterlace: DeinterlaceMode = DeinterlaceMode.AUTO,
    /**
     * Whether the CURRENT item's video stream is HDR — resolved by the
     * config builder via `isHdrFromStreams`. Transient session input, never
     * persisted. The mpv engines combine it with `MpvEngineConfig.hdrPassthrough`
     * (and the display's reported target) to decide whether HDR passthrough is
     * ACTIVE — and whether `tone-mapping` writes are suppressed (HDR→HDR).
     */
    val hdrSource: Boolean = false,
    /**
     * The CURRENT item's HDR transfer family — the typed twin of [hdrSource]
     * (invariant: `hdrSource == (hdrContent != HdrContentInfo.NONE)`), derived
     * by the config builder via `hdrContentFromStreams`. Transient session
     * input, never persisted. Engines with an HDR output surface resolve their
     * passthrough-vs-tone-mapping posture from it
     * (`resolveExoHdrOutput` / `resolveMpvHdrOutput`); other engines ignore it.
     */
    val hdrContent: HdrContentInfo = HdrContentInfo.NONE,
    /**
     * The user's HDR delivery posture for direct play
     * ([com.raulshma.jellyplay.core.model.HdrFormatPreference]) — which HDR
     * family may reach the display and which wins when the device decodes
     * more than one. [HdrFormatPreference.AUTO] (the default) keeps the
     * engine's default negotiation; [HdrFormatPreference.SDR_ONLY] forces
     * tone mapping down and never HDR passthrough; the family-only arms
     * restrict the allowed OUTPUT transfer families through
     * [applyHdrFormatPreference] (the OUTPUT half of the posture — the
     * decoder-side half for Exo rides
     * [com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig.hdrFormatPreference],
     * which the engine's owners consume). Persisted in the PlaybackStore
     * slice; folded into every config build by EngineConfigBuilder, so a
     * runtime change reaches the engines through the shared-pairs diff
     * ([EngineConfigDelta.sharedPairsChanged]).
     */
    val hdrFormatPreference: HdrFormatPreference = HdrFormatPreference.AUTO,
    /**
     * Optional DRM hook. When non-null, engines that support DRM (currently
     * ExoPlayerEngine on Android) attach the supplied session manager —
     * `androidx.media3.exoplayer.drm.DrmSessionManager` on Android, passed
     * through the type-erased [EngineDrmSessionManagerProvider]. Defaults to
     * `null` so non-DRM playback — and the rest of the codebase — is
     * unaffected. See [EngineDrmSessionManagerProvider].
     */
    val drmSessionManagerProvider: EngineDrmSessionManagerProvider? = null,
)

data class AudioEffectsConfig(
    val dialogueBoostEnabled: Boolean = false,
    val dialogueBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeEnabled: Boolean = false,
    val nightModeStrength: EffectStrength = EffectStrength.MODERATE,
    val nightModeGain: Int = 0,
    val equalizerEnabled: Boolean = false,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
    val audioNormalizationMode: AudioNormalizationMode = AudioNormalizationMode.NONE,
    val audioNormalizationEnabled: Boolean = false,
    val channelMixMode: ChannelMixMode = ChannelMixMode.AUTO,
    val channelMixEnabled: Boolean = false,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: EffectStrength = EffectStrength.MODERATE,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 500,
    val reverbPreset: com.raulshma.jellyplay.core.model.ReverbPreset = com.raulshma.jellyplay.core.model.ReverbPreset.NONE,
    val volumeBoostEnabled: Boolean = false,
    val volumeBoostGain: Int = 0,
    /**
     * L/R steering balance in `[-1, 1]` (mirrors Android's in-sink
     * [BalanceAudioProcessor] semantics: `+` attenuates the LEFT side).
     * Applied by engines with a native balance surface (desktop mpv: a
     * layout-gated `pan` stage in the `af` chain). `0f` = centered.
     */
    val lrBalance: Float = 0f,
    /**
     * Pitch shift in semitones (Android: media3 `PlaybackParameters.pitch`
     * multiplier). Engines map this onto their native pitch surface
     * (desktop mpv: the runtime `pitch` property). `0f` = unity.
     */
    val pitchSemitones: Float = 0f,
    /**
     * FINAL per-track ReplayGain gain in dB, pre-computed by the host
     * manager (track gain + user pre-amp, with the Android
     * ALBUM+shuffled → 0 rule already folded in — mirrors
     * `AudioEffectsProcessor.applyReplayGain`). TRACK/ALBUM engines apply
     * it as a static gain stage; `null` = no per-track gain (DYNAMIC uses
     * the compressor stage instead; NONE disables both).
     */
    val replayGainEffectiveDb: Float? = null,
    /**
     * The speaker-layout cap ([MaxAudioChannelsEnum.AUTO] = uncapped):
     * mpv folds it into the `audio-channels` write when the effects chain
     * has no opinion; ExoPlayer clamps the ChannelMixAudioProcessor output
     * layout; libVLC maps it onto its `--stereo-mode` lever.
     */
    val maxAudioChannels: MaxAudioChannelsEnum = MaxAudioChannelsEnum.AUTO,
    /**
     * Stereo-downmix loudness compensation in dB (0–12; 0 = off), applied
     * through the loudness-enhancer session effect alongside the volume
     * boost (the stronger of the two gains wins — one shared effect).
     */
    val downmixBoostDb: Float = 0f,
    /**
     * The user's ADVISORY "allow spatial audio" preference. There is no
     * app-side spatialization enable (the platform spatializer engages when
     * the system setting is on and the track/route qualify); this flag gates
     * what the ENGINE does to keep the stream spatializable: while spatial
     * audio is effective the engine neutralizes its own 5.1→2.0 downmix and
     * the speaker-layout cap so multichannel PCM reaches the AudioTrack, and
     * holds the Virtualizer session effect off (mutually exclusive — spatial
     * wins). `false` = the engine's downmix/cap/virtualizer behavior is
     * unchanged. ExoPlayer-only; other engines ignore it.
     */
    val spatialAudioEnabled: Boolean = false,
    /**
     * Qualifies [spatialAudioEnabled] to headphone-class outputs only (wired
     * / Bluetooth / USB-BT / BLE headsets, hearing aids) — the routes
     * platform spatialization targets. `true` (default): the engine only
     * neutralizes its downmix/cap while such an output is active.
     */
    val spatialAudioOnlyHeadphones: Boolean = true,
)

@Immutable
data class EngineCapabilities(
    val supportsPip: Boolean = false,
    val supportsMiniMode: Boolean = false,
    val supportsCues: Boolean = false,
    val supportsAudioDelay: Boolean = false,
    val supportsSubtitleDelay: Boolean = false,
    val supportsAudioPassthrough: Boolean = false,
    val supportsSubtitleStyle: Boolean = false,
    val supportsSubtitleVerticalPosition: Boolean = false,
    val supportsDialogueBoost: Boolean = false,
    val supportsNightMode: Boolean = false,
    val supportsAudioNormalization: Boolean = false,
    val supportsChannelMixing: Boolean = false,
    val supportsVideoFilters: Boolean = false,
    val supportsLiveQualitySwitch: Boolean = false,
    val supportsBandwidthEstimate: Boolean = false,
    val supportsAssOverride: Boolean = false,
    /**
     * Distinguishes "renders ASS/SSA" (`supportsAssOverride = true`, both
     * ExoPlayer and mpv) from "applies the user's style overrides to ASS/SSA
     * tracks" — mpv live via libass `--ass-override=force`; ExoPlayer by
     * rewriting the header's `Style: Default` line before libass parses the
     * track (`AssStyleOverrideParserFactory`; ass-media 0.5.0 binds no
     * style-override JNI), which is apply-at-load: style edits land on the next
     * subtitle-track load rather than mid-playback. When `false`, ASS tracks
     * render with their embedded styling and the user's colors, borders, and
     * Force-override only affect SRT/VTT.
     */
    val supportsAssStyleOverride: Boolean = false,
    val supportsFontFamily: Boolean = false,
    val supportsFreeFormColors: Boolean = false,
    val supportsBorderStyles: Boolean = false,
    /**
     * Renders a *secondary* subtitle track alongside the primary (e.g. English +
     * romaji, or a translation above the original). mpv exposes this natively
     * via `secondary-sid`; ExoPlayer would need a second stacked SubtitleView
     * (not yet implemented); LibVLC's freetype can't cleanly stack. When `false`,
     * the engine no-ops `setSecondarySubtitleTrack`.
     */
    val supportsSecondarySubtitles: Boolean = false,
    /**
     * Capture the current video frame to an image. `true` for every real engine
     * (all three render to a `SurfaceView` readable via `PixelCopy`); `false` for
     * [NoOpEngine]/EXTERNAL. The overflow menu's "Capture Frame" item is gated on
     * this so it is hidden for engines that cannot capture.
     */
    val supportsScreenshot: Boolean = false,
    /**
     * Renders side-loaded **image** subtitle files (PGS `.sup`, VobSub/DVB) —
     * mpv only, via its libav bitmap-subtitle decoders. ExoPlayer's bitmap-cue
     * path exists but is not reliable for external `.sup` sidecars, and LibVLC
     * has no image-subtitle decoder at all. Gates offline side-loading of image
     * entries from the download manifest (`OfflineSubtitleEntry.isImage`):
     * engines with `false` silently skip them.
     */
    val supportsImageSubtitles: Boolean = false,
    /**
     * Honors [EngineConfig.deinterlace] — the gear-menu "Deinterlace"
     * cycle is gated on this. Both mpv engines map the mode onto mpv's
     * `deinterlace` property; ExoPlayer/libVLC/no-op engines leave it `false`
     * so the menu item is hidden rather than a dead control.
     */
    val supportsDeinterlace: Boolean = false,
    /**
     * Can hand the platform's spatializer (Android `android.media.Spatializer`,
     * API 32+) a track it may spatialize: the engine renders multichannel PCM
     * through a sink whose output layout the platform can engage, and does not
     * force `SPATIALIZATION_BEHAVIOR_NEVER` on its stream. ExoPlayer only.
     *
     * DECLARED capability, not a promise: actual engagement is runtime-gated —
     * the device must run API 32+ with a supported spatializer, the system
     * spatial-audio setting must be on (user-controlled; there is no app-side
     * enable), the output must qualify (typically headphones), and the engine
     * must not defeat the stream (its own 5.1→2.0 downmix and speaker cap are
     * neutralized while spatial audio is effective — see SpatialAudioPolicy).
     * mpv/LibVLC render through their own audio outputs with no observable
     * Spatializer surface, so they advertise `false`.
     *
     * Spec reliance ("auto-disable when passthrough or ≥5.1
     * output is active"): covered structurally, no dedicated branch — this
     * engine cannot passthrough ([supportsAudioPassthrough] = `false`, it
     * always decodes to PCM), and ≥5.1 speaker/receiver routes fall outside
     * the headphones-only qualification (HDMI/USB-audio out is not
     * headphone-class) plus the platform spatializer's own route rules.
     */
    val supportsSpatialAudio: Boolean = false,
    /**
     * Honors `MpvEngineConfig.hdrPassthrough` — prefers an HDR output when the
     * display reports an HDR target. Both mpv hosts qualify (desktop:
     * `target-colorspace-hint` on the HWND-embed `vo=gpu-next`; Android: the
     * same property on the SurfaceView `vo=gpu-next` path, evaluated against
     * the `video-target-params` probe). ExoPlayer/LibVLC have no equivalent
     * switch — `false`. Gates the settings row's applicability and the stats
     * overlay's passthrough rows.
     */
    val supportsHdrPassthrough: Boolean = false,
    /**
     * Applies an in-engine HDR→SDR tone map for HDR content on a display that
     * cannot output it. ExoPlayer only: the Media3 GL video-graph path
     * (`setVideoEffects` → `DefaultVideoFrameProcessor`'s SDR-output branch;
     * see `resolveExoHdrOutput`). mpv needs no flag — its `tone-mapping`
     * default already handles HDR→SDR (its dial lives in `MpvEngineConfig`).
     * LibVLC has no tone-mapping surface at all — `false`.
     */
    val supportsHdrToneMapping: Boolean = false,
) {
    /**
     * Admission for the free-form (hex/HSV) subtitle color pickers: the
     * engine must honor free-form ARGB colors *and* apply user styling to
     * ASS tracks, so the picked color holds on every track type. ExoPlayer
     * (FORCE header rewrite — apply-at-load) and mpv (live) qualify; LibVLC
     * renders ASS as-authored, so the picker would look broken there.
     * Derived, not a matrix field: keep the two source flags explicit there.
     */
    val supportsCustomSubtitleColors: Boolean
        get() = supportsFreeFormColors && supportsAssStyleOverride
}

enum class EnginePlaybackState {
    IDLE, BUFFERING, READY, ENDED, ERROR
}

/**
 * How an engine keeps subtitles visible under pinch-zoom / crop. Declared by
 * [MediaEngine.zoomSafeSubtitleStrategy]; the screen dispatches its zoom-safe
 * subtitle rendering on this value rather than on a pair of capability booleans.
 */
enum class ZoomSafeSubtitleStrategy {
    /** No zoom-safe path; captions scale/translate with the video (libVLC, External). */
    DISABLED,

    /**
     * Engine reparents its native subtitle View into an app-supplied host
     * (`AndroidSurfaceProvider.setExternalSubtitleHost`, Android-only seam);
     * full native fidelity, relocated outside the zoom transform (ExoPlayer).
     */
    NATIVE_PINNED,

    /**
     * Engine emits the live subtitle line via [MediaEngine.liveSubtitleCue] and
     * toggles native rendering via [MediaEngine.setNativeSubtitlesVisible]; the
     * screen renders a Compose overlay while zoomed (mpv — libass composites
     * into the GPU surface and cannot be reparented).
     */
    COMPOSE_CUE,
}

data class MediaTrack(
    val id: String,
    val index: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean,
    val type: TrackType,
    /**
     * The demuxer/container stream index (mpv `ff-index`), when available. For
     * container-demuxed tracks this matches the server's `MediaStream.index`,
     * so it is the robust key for resolving a stored Jellyfin stream selection
     * to an engine track — label-based matching breaks when titles are blank,
     * duplicated, or translated. Null for side-loaded (`sub-add`) tracks, which
     * have no container index and are matched by label/url instead.
     */
    val streamIndex: Int? = null,
    /**
     * Ordered role badges (Forced/Default/SDH) to render beside the label. Built
     * by [TrackLabelFormatter.badges] from the engine's role flags or, for
     * server-origin tracks, the Jellyfin `isForced`/`isDefault` fields.
     */
    val badges: List<TrackBadge> = emptyList(),
)

/**
 * The honest spatial-audio state model for the ExoPlayer engine.
 *
 * **Research constraint this model encodes (do not "fix" it into a fake
 * on/off):** Android's spatializer (`android.media.Spatializer`, API 32+) is a
 * QUERY-ONLY surface. There is no app-side enable — the platform spatializes a
 * track when (a) the device has a supported spatializer, (b) the user's system
 * spatial-audio setting is on (`Spatializer.isEnabled()`, user-controlled in
 * system settings), (c) the current output route qualifies
 * (`Spatializer.isAvailable()` / `canBeSpatialized(...)`, typically
 * headphones), and (d) the stream actually reaches the `AudioTrack`
 * multichannel and non-offloaded. The only app-side controls are opt-outs
 * (`SPATIALIZATION_BEHAVIOR_NEVER`, marking content pre-spatialized) — never an
 * enable. Media3 1.11 surfaces the same truth: `SpatializerWrapper` (its public
 * util) only queries/state-listens, and `DefaultAudioSink` exposes no
 * spatialization option — the platform engages at the mixer for qualifying
 * AudioTracks.
 *
 * The app-side feature is therefore: qualification gating + "don't defeat it"
 * + honest UI state. The user's in-app toggle is ADVISORY — it gates what the
 * ENGINE does to keep the stream spatializable (holding the in-sink 5.1→2.0
 * downmix and the speaker-layout cap out of the way, and holding the
 * Virtualizer session effect off — the two effects are mutually exclusive and
 * spatial wins), never the spatialization itself.
 */
enum class SpatialAudioState {
    /**
     * The platform cannot spatialize this content here — no supported
     * spatializer, a non-qualifying output route, or a format it cannot
     * process. Derived from `Spatializer.canBeSpatialized` for the playing
     * format (capability-only, independent of the system setting). The UI's
     * "not spatializable on this output" reason.
     */
    UNAVAILABLE,

    /**
     * The ENGINE's own configuration holds spatial out: the session renders
     * through audio offload (which bypasses the mixer path the spatializer
     * effect lives in), or the headphones-only qualification is on while the
     * output is not headphone-class. The downmix/cap behavior is unchanged.
     */
    OFF,

    /**
     * The route/device COULD spatialize (`canBeSpatialized`, no hard gate) but
     * a user-facing switch is off — the in-app advisory toggle, or the system
     * spatial-audio setting. The "flip this (or the system switch) to engage"
     * state the UI surfaces honestly.
     */
    AVAILABLE,

    /**
     * Spatial audio is EFFECTIVE for this session: the user's advisory toggle
     * is on, the system spatializer is enabled, the content can be
     * spatialized on the current output, and no disqualifier (offload,
     * headphones gate) holds. The engine neutralizes its downmix + speaker
     * cap and holds the Virtualizer off so the platform has a clean
     * multichannel stream.
     */
    ACTIVE,
}

@Immutable
data class EngineVideoStats(
    val videoCodec: String? = null,
    val videoDecoder: String? = null,
    val videoResolution: String? = null,
    val videoFrameRate: Float? = null,
    val videoBitrate: Int? = null,
    val videoColorRange: String? = null,
    val videoHdrType: String? = null,
    val videoColorDepth: String? = null,
    val audioCodec: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val audioBitrate: Int? = null,
    val estimatedBandwidthBps: Long = 0,
    val droppedFrames: Long = 0,
    val totalVideoFrames: Long = 0,
    val bufferedPositionMs: Long = 0,
    val bufferSizeBytes: Long = 0,
    /**
     * Audio/video sync deviation in ms — mpv's `total-avsync`. Negative = audio
     * leads video. Null on engines that don't expose it (ExoPlayer, LibVLC). The
     * key diagnostic for frame-pace / drift issues.
     */
    val avsyncMs: Float? = null,
    /**
     * The display (output) refresh rate in Hz — mpv's `display-fps`, or the
     * platform `Display.mode.refreshRate` for ExoPlayer. Helps confirm a refresh-
     * rate switch actually took effect.
     */
    val displayFps: Float? = null,
    /**
     * Time the video frame was scheduled to display but was delayed, in ms —
     * mpv's `vo-delayed`. Non-null only for mpv. Indicates vsync/display backlog.
     */
    val voDelayedMs: Float? = null,
    /**
     * Cumulative frame drops reported by the vo (display-side), distinct from
     * [droppedFrames] (decoder-side). mpv's `frame-drop-count`.
     */
    val voFrameDropCount: Long? = null,
    /**
     * HDR passthrough is ACTIVE for the running session: the setting
     * is on, the item is HDR, and the display target reports HDR — mpv's
     * `video-target-params` probe on both mpv hosts, the Android
     * `Display.HdrCapabilities` probe on ExoPlayer (an EXPECTATION there:
     * Media3's own EGL-capability check remains the render-time arbiter).
     * Drives the "HDR output active" badge in the stats overlay. `false` on
     * engines that expose no target surface (LibVLC, software-render mpv).
     */
    val hdrOutputActive: Boolean = false,
    /**
     * One-line HDR passthrough diagnostic for the stats overlay: set
     * when passthrough is requested but NOT active — e.g. the display target
     * stayed SDR, so the tone mapping took over. `null` = nothing to
     * report.
     */
    val hdrOutputNotice: String? = null,
    /**
     * The display's HDR capability summary for the stats overlay: the HDR
     * families the current display accepts ("HDR10, Dolby Vision"), "SDR"
     * for a display with no HDR types. `null` when no probe answered (the
     * Android display probe was unavailable) or the engine has no display
     * probe at all (the desktop mpv host answers per-target via
     * `video-target-params` instead; LibVLC exposes nothing). Sits beside
     * [videoHdrType] so content family and display capability read as one
     * line of diagnosis.
     */
    val displayHdrTypes: String? = null,
    /**
     * The engine's effective spatial-audio state ([SpatialAudioState]).
     * Null on engines/surfaces that expose no spatializer facts (mpv,
     * LibVLC, pre-API-32 Android). The stats overlay's honest "what the
     * platform spatializer is doing" row — there is no app-side
     * spatialization enable, so this reports rather than controls.
     */
    val audioSpatialState: SpatialAudioState? = null,
    /**
     * Spatial won over a concurrently-requested Virtualizer for this
     * session — the mutual-exclusion report the spatial-audio policy
     * produces when the state is ACTIVE while a Virtualizer was also
     * requested. Rendered as the "(virtualizer superseded)" note beside
     * [audioSpatialState] — a separate flag so the note does not ride
     * inside the state value.
     */
    val audioSpatialSuperseded: Boolean = false,
)

/**
 * Static codec/resolution/HDR/audio-channel slice of [EngineVideoStats] — the
 * only fields [com.raulshma.jellyplay.feature.player.video.components.PlaybackMetadataRow]
 * actually reads. Projecting these out via `derivedStateOf` insulates the row
 * from the high-churn fields (droppedFrames, bufferedPositionMs, videoBitrate,
 * estimatedBandwidthBps) that tick multiple times per second during playback,
 * so the metadata row recomposes only when a displayed field actually changes.
 */
@Immutable
data class PlaybackMetadataSnapshot(
    val videoCodec: String? = null,
    val videoResolution: String? = null,
    val videoHdrType: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
)

/**
 * The strategy interface every playback backend implements.
 *
 * This is intentionally a single wide contract rather than a composition of
 * role interfaces: a previous split into [PlaybackLifecycle] / [PlaybackControl]
 * / [PlaybackState] / [EngineConfigurable] / [TrackControl] / [SubtitleStyling]
 * / [VideoSurfaceBinding] delivered no decoupling, because no consumer ever
 * depended on a narrow role — every call site reached through [MediaEngine].
 * The split was pure ceremony (see deletion-test note in the architecture
 * review). The members are grouped below by concern to keep the surface
 * navigable.
 *
 * Members declared directly here are either overrides of
 * [com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine] or
 * [PlayerLifecycleCallbacks], or special-case internal hooks. Identity
 * accessors (e.g. [displayName]) live on this contract precisely so consumers
 * never type-test a concrete adapter to recover an engine-specific value.
 */
@Stable
interface MediaEngine :
    PlayerLifecycleCallbacks,
    com.raulshma.jellyplay.core.data.remote.RemotePlayableEngine {

    // ── RemotePlayableEngine overrides (re-declared here to carry the contract
    //     and, for some, a default). ──
    override fun release()
    override fun play()
    override fun pause()
    override fun stop()
    override fun seekTo(positionMs: Long)
    override val isPlaying: StateFlow<Boolean>
    override val currentPositionMs: Long
    override fun selectTrack(type: TrackType, index: Int)
    override fun setMaxVideoBitrate(bps: Int?)

    // ── Scrubbing ──
    /**
     * Notifies the engine that a user-driven scrub session started or ended
     * (seek-bar drag, swipe seek, double-tap-hold repeat seek). Media3's
     * adapter maps this to `ExoPlayer.setScrubbingModeEnabled` — decode-only
     * frames + dynamic scheduling while the drag is live — so the many
     * preview seeks feel instant; engines without a scrubbing concept
     * (mpv/VLC — their absolute seeks are already precise) inherit the no-op.
     * Purely advisory: the committed seek still goes through [seekTo].
     */
    fun onScrubbingChanged(active: Boolean) {}

    // ── Identity ──
    /**
     * Stable, human-readable engine name for user-facing strings (the
     * unsupported-audio-delay toast, engine pickers, stats overlays). Every
     * adapter returns the matching
     * [com.raulshma.jellyplay.core.model.PlayerType.displayName]. Consumers
     * MUST NOT type-test concrete engine classes to recover this name — read
     * this property instead, so a new backend needs no call-site changes.
     */
    val displayName: String

    // ── Source loading & teardown ──
    fun load(request: PlaybackRequest)

    // ── Speed control ──
    fun setPlaybackSpeed(speed: Float)
    val playbackSpeed: Float

    // ── Reactive, hot state surface: playback state, duration, position/
    //    buffering/video-stats flows, and the adaptive polling knobs that drive
    //    the high-frequency tickers consumed by leaf UI. ──
    val playbackState: StateFlow<EnginePlaybackState>
    val durationMs: Long
    val positionFlow: Flow<Long>

    /**
     * Structured playback errors. Engines map their native error surface
     * (ExoPlayer `PlaybackException`, mpv/libvlc events) onto the
     * [EngineError] taxonomy so the UI can distinguish retryable from fatal
     * failures and offer the right affordance. The bare `Flow<String>`
     * channel was replaced because every error collapsed to
     * [EngineError.Unknown] and the retry / switch-engine paths never fired.
     */
    val errorFlow: Flow<EngineError>

    /**
     * One-shot subtitle events (e.g. a malformed track being auto-disabled).
     * Hot flow; collectors should treat each value as transient. Engines that
     * never produce subtitle events expose an empty flow.
     */
    val subtitleEvents: Flow<SubtitleEvent>

    val bufferedPositionMs: StateFlow<Long>
    val videoStats: StateFlow<EngineVideoStats>

    /**
     * Buffered playback ranges in ms on the item's absolute timeline
     * — the multi-band generalization of [bufferedPositionMs]. Ranges are
     * clamped to `[0, durationMs]`, non-degenerate, sorted and merged (see
     * [BufferedRanges.normalize]); discontinuities survive so the seek bar can
     * shade each cached window separately (a forward seek drops the old
     * window; the back-buffer stays behind the playhead). Engines that expose
     * no buffer surface inherit the always-empty [BufferedRanges.EMPTY]
     * default instead of faking a full-track band.
     */
    val bufferedRanges: StateFlow<List<LongRange>>
        get() = BufferedRanges.EMPTY

    /**
     * Accumulated subtitle cues for the active track, for the subtitle-sync
     * preview. Populated by engines that can surface cue text as it renders
     * (ExoPlayer via `onCues`); empty for engines with no cue-text API
     * (libVLC) — see [EngineCapabilities.supportsCues]. These cover only the
     * played range (no ahead-lookahead); external text subs use the full-track
     * re-parse path in the player feature for bidirectional offset preview.
     */
    val currentCues: StateFlow<List<TimedCue>>

    /**
     * The currently-displayed subtitle line as plain text, or `null` when no
     * line is active. Distinct from [currentCues] (which accumulates the played
     * range for the sync preview): this is the single live line the screen can
     * render in a zoom-safe Compose overlay. Only emitted by engines whose
     * [zoomSafeSubtitleStrategy] is [ZoomSafeSubtitleStrategy.COMPOSE_CUE]
     * (mpv, via its `sub-text` property with ASS override tags stripped);
     * `null` forever on every other engine.
     */
    val liveSubtitleCue: StateFlow<CharSequence?>

    val pollingIntervalMs: StateFlow<Long>
    val videoStatsEnabled: StateFlow<Boolean>
    fun setPollingIntervalMs(ms: Long)
    fun setVideoStatsEnabled(enabled: Boolean)

    val audioSessionId: Int

    // ── Capability advertisement and live configuration.
    //
    //    [EngineCapabilities] is the runtime query surface the UI reads to
    //    show/hide controls — see [EngineCapabilityMatrix]. ──
    val capabilities: EngineCapabilities
    fun updateConfig(config: EngineConfig)

    // ── Track enumeration and runtime subtitle-track addition. ──
    val availableTracks: StateFlow<List<MediaTrack>>
    fun addExternalSubtitle(source: SubtitleSource) {}

    /**
     * Selects a *secondary* subtitle track (rendered alongside the primary).
     * Engines with [EngineCapabilities.supportsSecondarySubtitles] = false no-op
     * this call. An [index] < 0 clears the secondary track.
     */
    fun setSecondarySubtitleTrack(index: Int) {}

    // ── Zoom/crop-safe subtitle strategy ──
    //
    //    The engine declares HOW it keeps captions pinned to the screen under
    //    pinch-zoom/crop; the screen renders from [zoomSafeSubtitleStrategy]
    //    instead of reverse-engineering the strategy from a pair of capability
    //    booleans. Each strategy carries its own mechanical contract:
    //      · NATIVE_PINNED — the engine reparents its native subtitle View into
    //        an app-supplied host (AndroidSurfaceProvider.setExternalSubtitleHost,
    //        the Android-only surface seam); full native fidelity, just
    //        relocated (ExoPlayer).
    //      · COMPOSE_CUE    — the engine emits the live line via
    //        [liveSubtitleCue] and toggles native rendering via
    //        [setNativeSubtitlesVisible]; the screen renders a Compose overlay
    //        while zoomed (mpv: libass composites into the GPU surface).
    //      · DISABLED       — no zoom-safe path; captions scale/translate with
    //        the video (libVLC, External). ──

    /**
     * How this engine keeps subtitles visible when the video is pinch-zoomed or
     * cropped. The screen reads this once and dispatches on the strategy; a
     * fourth engine added later defaults to [ZoomSafeSubtitleStrategy.DISABLED]
     * instead of silently-wrong behaviour from two unset booleans.
     */
    val zoomSafeSubtitleStrategy: ZoomSafeSubtitleStrategy
        get() = ZoomSafeSubtitleStrategy.DISABLED

    // ── Per-engine subtitle styling applied to the engine's native subtitle
    //    surface (Media3 `SubtitleView` / libass / VLC freetype), and the
    //    mechanical hooks the screen's zoom-safe strategies drive. ──
    /**
     * Apply [style] to this engine's own native subtitle surface. Each engine
     * owns its surface (ExoPlayer its `PlayerView`/`SubtitleView`, mpv its
     * libass properties, libVLC its freetype media options), so no `View` is
     * handed across the seam — the previous `view` parameter had three
     * incompatible meanings (load-bearing for ExoPlayer, ignored by mpv,
     * triggering a media reload for libVLC). Callers just hand the [style].
     */
    fun applySubtitleStyle(style: SubtitleStyle)

    /**
     * Which `sub-*` styling keys the user's custom mpv config (the in-app
     * Advanced MPV Configuration, plus Android's `<config-dir>/mpv.conf`)
     * owns for this session — the engine skips writing those keys, so the
     * matching in-app subtitle-style controls are no-ops for them. The
     * subtitle-style UI reads this snapshot to explain that interaction
     * (owned keys, the ASS-override requirement, the mpv.conf quoting trap)
     * instead of letting edits silently do nothing. Defaults to
     * [MpvSubtitleOwnership.NONE] on every engine without a user-config
     * surface, so the notice never renders for them.
     */
    val subtitleStyleOwnership: MpvSubtitleOwnership
        get() = MpvSubtitleOwnership.NONE

    /**
     * Toggles the engine's native subtitle rendering at runtime. Used to hide
     * native subs while the screen renders a zoom-safe Compose overlay (see
     * [liveSubtitleCue] / [ZoomSafeSubtitleStrategy.COMPOSE_CUE]), avoiding
     * double-drawn captions. mpv maps this to `sub-visibility`; engines without
     * a runtime toggle no-op.
     */
    fun setNativeSubtitlesVisible(visible: Boolean) {}

    /**
     * Executes one user-authored raw engine command — the custom on-screen
     * player buttons' "command" / "toggleFilter" actions (issue #27; an mpv
     * command line such as `vf toggle sharpen` or `no-osd cycleValues speed`).
     * The command is split on whitespace into the engine's command arguments;
     * whitespace-embedded values are not supported by this surface.
     *
     * Default no-op returning `false`: only mpv-backed engines override it
     * (mpv is the only engine with a raw command surface), so callers must
     * treat `false` (or a non-mpv engine) as "the action did not apply" and
     * stay silent — never fail playback over a custom command. Engines that
     * override it absorb their own failures into the Boolean.
     */
    fun executeUserCommand(command: String): Boolean = false

    // ── Native surface creation and aspect-ratio control. ──
    //    Surface creation itself is platform-specific and lives on
    //    platform-seam interfaces (`AndroidSurfaceProvider` in this module's
    //    androidMain) — a `View` cannot cross into commonMain. Aspect-ratio
    //    control stays here because it is engine-logical, not view-mechanical.

    /**
     * Apply the user's aspect-ratio choice to this engine's native surface.
     * The [AspectRatio] enum is the engine-neutral contract; each adapter maps
     * it to its own native mode (ExoPlayer `RESIZE_MODE_*`, mpv
     * `video-aspect-override`/`panscan`, libVLC `aspectRatio`/`scale`) so no
     * media3 constant crosses the seam.
     */
    fun setAspectRatio(ratio: AspectRatio)
}
