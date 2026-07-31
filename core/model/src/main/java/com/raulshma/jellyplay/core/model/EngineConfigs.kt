package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Stable
sealed interface EngineSpecificConfig

@Immutable
@Serializable
enum class MpvHwdec(val key: String, val displayName: String) {
    MEDIACODEC("mediacodec", "MediaCodec Direct (Zero-Copy)"),
    MEDIACODEC_COPY("mediacodec-copy", "MediaCodec Copy"),
    // mpv picks the first hwdec entry whose init succeeds. Zero-copy
    // `mediacodec` must come first: `mediacodec-copy` copies every decoded
    // frame GPU→CPU→GPU (~3 GB/s at 1080p60), the dominant cause of mpv
    // lag vs. ExoPlayer (which is zero-copy by default). Copy stays as a
    // fallback for devices/codecs where direct surface output fails.
    MEDIACODEC_HW_ONLY("mediacodec,mediacodec-copy", "MediaCodec HW Only (No SW Fallback)"),
    MEDIACODEC_FALLBACK("mediacodec,mediacodec-copy,no", "MediaCodec + SW Fallback"),
    AUTO("auto", "Auto Detect"),
    NO("no", "Software Only"),
}

@Immutable
@Serializable
enum class MpvVideoOutput(val key: String, val displayName: String) {
    GPU("gpu", "GPU (Legacy)"),
    GPU_NEXT("gpu-next", "GPU Next (Better HDR)"),
}

@Immutable
@Serializable
enum class MpvScaler(val key: String, val displayName: String) {
    BILINEAR("bilinear", "Bilinear (Fast)"),
    BICUBIC("bicubic", "Bicubic"),
    SPLINE16("spline16", "Spline16"),
    SPLINE36("spline36", "Spline36"),
    SPLINE64("spline64", "Spline64"),
    LANCZOS("lanczos", "Lanczos"),
    EWA_LANCZOS("ewa_lanczos", "EWA Lanczos"),
    EWA_LANCZOS_SHARP("ewa_lanczossharp", "EWA Lanczos Sharp (HQ)"),
    MITCHELL("mitchell", "Mitchell"),
    CATMULL_ROM("catmull_rom", "Catmull-Rom"),
    OVERSAMPLE("oversample", "Oversample"),
    HERMITE("hermite", "Hermite"),
}

@Immutable
@Serializable
enum class MpvAudioOutput(val key: String, val displayName: String) {
    AUDIOTRACK("audiotrack", "AudioTrack"),
    AAUDIO("aaudio", "AAudio (Low Latency)"),
    OPENSLES("opensles", "OpenSL ES (Legacy)"),
}

@Immutable
@Serializable
enum class MpvFrameDrop(val key: String, val displayName: String) {
    NONE("no", "Disabled"),
    VO("vo", "Video Output Only"),
    DECODER("decoder", "Decoder Only"),
    DECODER_VO("decoder+vo", "Decoder + Video Output"),
}

@Immutable
@Serializable
enum class MpvSkipLoopFilter(val key: String, val displayName: String) {
    NONE("none", "None (Best Quality)"),
    DEFAULT("default", "Default"),
    NONREF("nonref", "Non-Reference"),
    BIDIR("bidir", "Bi-Directional"),
    NONKEY("nonkey", "Non-Key Frames"),
    ALL("all", "All (Fastest)"),
}

@Immutable
@Serializable
enum class MpvDemuxerMaxBytes(val displayName: String, val key: String, val bytes: Long) {
    AUTO("Auto (Device-Based)", "auto", 0),
    MB_32("32 MB", "32MiB", 32 * 1024 * 1024L),
    MB_64("64 MB", "64MiB", 64 * 1024 * 1024L),
    MB_128("128 MB", "128MiB", 128 * 1024 * 1024L),
    MB_256("256 MB", "256MiB", 256 * 1024 * 1024L),
}

@Immutable
@Serializable
data class MpvEngineConfig(
    // gpu-next is the modern vo with better HDR / 10-bit handling; the legacy
    // `gpu` vo is the historical default but is no longer recommended.
    val videoOutput: MpvVideoOutput = MpvVideoOutput.GPU_NEXT,
    // Bilinear matches mpv's own default and mpvkt's config — the cheapest
    // upscaler. High-order scalers (lanczos, spline*) are a steady per-frame
    // GPU tax that ExoPlayer never pays; on mid/low GPUs they cause frame
    // drops. Users who want sharpness can still pick a heavier scaler in
    // settings. The downscaler is left at mpv's default separately (see
    // initOptions dscale comment).
    val scaler: MpvScaler = MpvScaler.BILINEAR,
    val deband: Boolean = false,
    val interpolation: Boolean = false,
    val audioOutput: MpvAudioOutput = MpvAudioOutput.AUDIOTRACK,
    val audioFallback: MpvAudioOutput? = MpvAudioOutput.AAUDIO,
    val demuxerMaxBytes: MpvDemuxerMaxBytes = MpvDemuxerMaxBytes.AUTO,
    // `default` matches mpv's built-in default and mpvkt — non-reference frames
    // skip the in-loop deblock, a real per-frame saving on H.264/HEVC. `none`
    // (best quality) is available in settings for users who want it.
    val skipLoopFilter: MpvSkipLoopFilter = MpvSkipLoopFilter.DEFAULT,
    // decoder+vo: under decode pressure the decoder drops to stay realtime, and
    // the vo drops on display-resample mismatch. `vo` alone can't shed load at
    // the decoder, so a slow decode path (or a momentary hwdec fallback) backs
    // up and stutters. Cheap insurance; visually identical when decode keeps up.
    val frameDrop: MpvFrameDrop = MpvFrameDrop.DECODER_VO,
    val hwdecOverride: MpvHwdec? = null,
    /**
     * Free-form, user-authored `mpv.conf`-style options appended to mpv's
     * startup config **after** every structured option above. This is the power-
     * user escape hatch for mpv knobs JellyPlay's structured settings don't
     * expose (e.g. `scale=ewa_lanczossharp`, `tscale`, `tone-mapping`,
     * `target-prim`, `cache-secs`, `hr-seek`, etc.).
     *
     * Lines are parsed by [parseMpvConfigOptions]: blank lines and `#` comments
     * are ignored; `key=value` (or bare `key` flags) become `setOptionString`
     * calls. Because this runs last, a raw value **overrides** its structured
     * counterpart — which is the intent (the user is opting out of the app's
     * curated default). An empty string (the default) applies nothing.
     */
    val mpvExtraConfig: String = "",
) : EngineSpecificConfig

/**
 * A single parsed `mpv.conf`-style option: a [key] and its [value]. A bare
 * `key` flag (no `=`) parses with value `"yes"`. Named type (rather than the
 * raw `Pair<String, String>` it was before) so call sites read
 * `option.key` / `option.value` instead of the opaque `.first` / `.second`.
 */
@Immutable
@Serializable
data class MpvOption(val key: String, val value: String)

/**
 * Parses free-form mpv config text into ordered [MpvOption]s.
 *
 * Rules (matching mpv's own `mpv.conf` parsing for the common cases):
 *  - Blank lines and lines whose first non-space char is `#` are dropped.
 *  - `key=value` splits on the first `=`; surrounding whitespace trimmed.
 *  - A bare `key` (no `=`) is treated as a boolean flag with value `"yes"`,
 *    mirroring mpv's `--key` shorthand.
 *  - `key=` with an empty value is kept as an empty-string value (mpv uses
 *    this to reset some list options).
 *
 * The result is a plain [List] so the engine layer can apply it with
 * `setOptionString(option.key, option.value)` and unit-test the parsing without
 * a live mpv instance.
 */
fun parseMpvConfigOptions(text: String): List<MpvOption> {
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val eq = line.indexOf('=')
            if (eq >= 0) {
                MpvOption(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
            } else {
                MpvOption(line, "yes")
            }
        }
        .filter { it.key.isNotEmpty() }
        .toList()
}

@Immutable
@Serializable
enum class VlcAudioOutput(val key: String, val displayName: String) {
    AAUDIO("aaudio", "AAudio"),
    AUDIOTRACK("android_audiotrack", "AudioTrack"),
    OPENSLES("opensles", "OpenSL ES"),
}

@Immutable
@Serializable
enum class VlcVideoOutput(val key: String, val displayName: String) {
    ANDROID_DISPLAY("android-display", "Android Display"),
    ANDROID_WINDOW("android-window", "Android Window"),
}

@Immutable
@Serializable
data class LibVlcEngineConfig(
    val audioOutput: VlcAudioOutput = VlcAudioOutput.AAUDIO,
    val audioTimeStretch: Boolean = true,
    val networkCaching: Int = 0,
    val videoOutput: VlcVideoOutput = VlcVideoOutput.ANDROID_DISPLAY,
    val skipLoopFilter: Int = 1,
    val skipFrame: Int = 0,
    val decoderThreads: Int = 0,
    val dropLateFrames: Boolean = true,
    val skipFrames: Boolean = true,
) : EngineSpecificConfig

@Immutable
@Serializable
enum class RefreshRateMode(val displayName: String) {
    /** Never switch the display mode — keep the user/system default. */
    OFF("Off"),
    /** Match the content's frame rate at the current resolution only. */
    FRAME_RATE_ONLY("Frame Rate Only"),
    /** Match frame rate and switch resolution to the content's native size. */
    FRAME_RATE_AND_RESOLUTION("Frame Rate & Resolution"),
}

@Immutable
@Serializable
enum class ExoVideoScalingMode(val displayName: String, val key: String, val value: Int) {
    SCALE_TO_FIT("Fit", "SCALE_TO_FIT", 1),
    SCALE_TO_FIT_WITH_CROPPING("Fill (Crop)", "SCALE_TO_FIT_WITH_CROPPING", 2),
}

@Immutable
@Serializable
enum class ExoFrameRateStrategy(val displayName: String, val key: String, val value: Int) {
    ONLY_IF_SEAMLESS("Seamless Only", "ONLY_IF_SEAMLESS", 0),
    OFF("Disabled", "OFF", 3),
}

@Immutable
@Serializable
enum class ExoAudioOffloadMode(val displayName: String, val key: String, val value: Int) {
    DISABLED("Disabled", "DISABLED", 0),
    ENABLED("Enabled", "ENABLED", 1),
    REQUIRED("Required", "REQUIRED", 2),
}

@Immutable
@Serializable
data class ExoPlayerEngineConfig(
    val videoScalingMode: ExoVideoScalingMode = ExoVideoScalingMode.SCALE_TO_FIT,
    val frameRateStrategy: ExoFrameRateStrategy = ExoFrameRateStrategy.ONLY_IF_SEAMLESS,
    val preferredVideoMimeTypes: List<String> = emptyList(),
    val skipSilence: Boolean = false,
    val audioOffloadMode: ExoAudioOffloadMode = ExoAudioOffloadMode.DISABLED,
    val backBufferDurationMs: Int = 0,
    val enableDecoderFallback: Boolean = true,
) : EngineSpecificConfig
