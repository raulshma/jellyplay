package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Playback performance profiles (issue #186). A profile is a **ceiling**
 * layered over the user's explicit playback settings — it never rewrites a
 * persisted choice. [AUTO] resolves to a [DevicePerformanceTier] detected
 * once per device; [LOW] / [MEDIUM] / [HIGH] pin the tier directly.
 */
@Immutable
@Serializable
enum class VideoPerformanceProfile(override val displayName: String) : HasDisplayName {
    AUTO("Auto"),
    LOW("Low end"),
    MEDIUM("Medium end"),
    HIGH("High end"),
}

/**
 * The device capability tier the AUTO detector (or an explicit profile)
 * resolves playback tuning against. Per-device runtime state — never a
 * preference, never backed up.
 */
@Immutable
@Serializable
enum class DevicePerformanceTier(override val displayName: String) : HasDisplayName {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}

/** Why the detector chose a tier — shown in diagnostics, never a preference. */
@Immutable
@Serializable
enum class PerformanceReason {
    LOW_RAM,
    MODERATE_RAM,
    FEW_CORES,
    MODERATE_CORES,
    NO_HARDWARE_1080P,
    NO_HARDWARE_1440P,
    NO_HARDWARE_4K,
    NO_HARDWARE_HEVC,
    ENTRY_SOC,
    EMULATOR,
    PROBE_FAILED,
    MANUAL_REDETECT,
}

/** The sticky result of one device detection run. Persisted per device. */
@Immutable
@Serializable
data class PerformanceDetection(
    val tier: DevicePerformanceTier,
    val score: Int,
    val reasons: List<PerformanceReason>,
    val fingerprint: String,
    val detectorVersion: Int,
    val detectedAtEpochMs: Long,
)

/**
 * The tuning a [DevicePerformanceTier] applies at playback time. Nullable
 * fields are profile overrides: null = leave the user's value alone. This
 * object is never persisted; it is derived from the tier at runtime by
 * [VideoPerformanceMatrix].
 */
@Immutable
data class VideoPerformanceTuning(
    val tier: DevicePerformanceTier,
    val maxVideoHeight: Int?,
    val maxVideoBitrate: Long?,
    val mpvRenderQuality: MpvRenderQuality? = null,
    val mpvScaler: MpvScaler? = null,
    val forceMpvDebandOff: Boolean = false,
    val forceMpvInterpolationOff: Boolean = false,
    val minLibVlcNetworkCachingMs: Int? = null,
    val libVlcDecoderThreads: Int? = null,
    val maxLibVlcSkipLoopFilter: Int? = null,
    val maxPreloadBuffer: PreloadBufferSize? = null,
    val maxVideoCacheMb: Int? = null,
) {
    companion object {
        /** HIGH semantics: no additional restriction anywhere. */
        val NONE = VideoPerformanceTuning(
            tier = DevicePerformanceTier.HIGH,
            maxVideoHeight = null,
            maxVideoBitrate = null,
        )
    }
}

/** What the user asked for, what the device got, and the tuning that follows. */
@Immutable
data class ResolvedPerformanceProfile(
    val requested: VideoPerformanceProfile,
    val tier: DevicePerformanceTier,
    val tuning: VideoPerformanceTuning,
    val detection: PerformanceDetection?,
)

/**
 * The effective stream caps after folding the profile ceiling over the
 * user/network caps. Pure data — the caller owns the min-fold inputs.
 */
@Immutable
data class PlaybackCaps(
    val maxBitrate: Long?,
    val maxHeight: Int?,
    /** True when the profile cap is the stricter value. */
    val profileCappedBitrate: Boolean,
    val profileCappedHeight: Boolean,
    /** True when the active mode/source cannot enforce server caps. */
    val capsBypassed: Boolean,
)

/**
 * Maps a [DevicePerformanceTier] to its [VideoPerformanceTuning].
 *
 * HIGH is "no additional restriction", not a quality floor: it does not raise
 * the user's quality or force a heavier scaler. The stricter-of rule lives
 * with the callers (the cap resolver); this matrix only states what each tier
 * contributes.
 */
object VideoPerformanceMatrix {
    fun forTier(tier: DevicePerformanceTier): VideoPerformanceTuning = when (tier) {
        DevicePerformanceTier.LOW -> VideoPerformanceTuning(
            tier = DevicePerformanceTier.LOW,
            maxVideoHeight = 1080,
            maxVideoBitrate = 8_000_000L,
            mpvRenderQuality = MpvRenderQuality.PERFORMANCE,
            mpvScaler = MpvScaler.BILINEAR,
            forceMpvDebandOff = true,
            forceMpvInterpolationOff = true,
            minLibVlcNetworkCachingMs = 1_500,
            libVlcDecoderThreads = 2,
            maxLibVlcSkipLoopFilter = 1,
            maxPreloadBuffer = PreloadBufferSize.MEDIUM,
            maxVideoCacheMb = 512,
        )
        DevicePerformanceTier.MEDIUM -> VideoPerformanceTuning(
            tier = DevicePerformanceTier.MEDIUM,
            maxVideoHeight = 1440,
            maxVideoBitrate = 20_000_000L,
            maxVideoCacheMb = 1024,
        )
        DevicePerformanceTier.HIGH -> VideoPerformanceTuning(
            tier = DevicePerformanceTier.HIGH,
            maxVideoHeight = null,
            maxVideoBitrate = null,
        )
    }
}
