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
    MEDIACODEC_HW_ONLY("mediacodec-copy,mediacodec", "MediaCodec HW Only (No SW Fallback)"),
    MEDIACODEC_FALLBACK("mediacodec-copy,mediacodec,no", "MediaCodec + SW Fallback"),
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
    val videoOutput: MpvVideoOutput = MpvVideoOutput.GPU,
    val scaler: MpvScaler = MpvScaler.BILINEAR,
    val deband: Boolean = false,
    val interpolation: Boolean = false,
    val audioOutput: MpvAudioOutput = MpvAudioOutput.AUDIOTRACK,
    val audioFallback: MpvAudioOutput? = MpvAudioOutput.AAUDIO,
    val demuxerMaxBytes: MpvDemuxerMaxBytes = MpvDemuxerMaxBytes.AUTO,
    val skipLoopFilter: MpvSkipLoopFilter = MpvSkipLoopFilter.NONE,
    val frameDrop: MpvFrameDrop = MpvFrameDrop.VO,
    val hwdecOverride: MpvHwdec? = null,
) : EngineSpecificConfig

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
