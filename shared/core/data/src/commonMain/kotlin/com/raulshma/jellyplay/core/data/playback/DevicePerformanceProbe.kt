package com.raulshma.jellyplay.core.data.playback

/**
 * The raw device signals the AUTO performance detector (issue #186 §5.5)
 * classifies into a [com.raulshma.jellyplay.core.model.DevicePerformanceTier].
 * Pure data — no platform types — so the classifier that consumes it is
 * JVM-testable and the Android/desktop probes are thin gatherers.
 *
 * The four trailing identity fields ([manufacturer] / [model] / [device] /
 * [board]) carry the Build fingerprint inputs the §5.5 detection fingerprint
 * hashes; they are not scored, they only disambiguate the fingerprint when the
 * same SoC/RAM shape appears on two devices. Platform probes that have no such
 * identity leave them null.
 */
data class DevicePerformanceSignals(
    /** False on desktop, where the classifier short-circuits to HIGH (§5.13). */
    val isAndroid: Boolean,
    /** `ActivityManager.isLowRamDevice` (Android) — false when unmeasured. */
    val isLowRamDevice: Boolean,
    /** Total device RAM in megabytes (`MemoryInfo.totalMem`). */
    val totalRamMb: Int,
    val cpuCores: Int,
    /**
     * Tallest hardware-decodable video height from the codec scan; null when
     * no hardware decoder was found or the scan did not run. On Android null
     * scores as "no hardware 1080p" (+4); on desktop the classifier ignores it
     * (no codec probe exists there).
     */
    val maxHardwareDecodeHeight: Int?,
    /** True when any hardware decoder advertises `video/hevc`. */
    val hardwareHevcDecode: Boolean,
    /** Highest hardware-decode bitrate range upper bound (bits/sec), if scanned. */
    val maxHardwareDecodeBitrate: Long?,
    /**
     * SoC identity string (`Build.SOC_MODEL` with `HARDWARE`/`BOARD`/`DEVICE`
     * fallbacks on Android; the OS name on desktop). Matched case-insensitively
     * against the documented entry-SoC family table.
     */
    val socFamily: String?,
    /** GL renderer string when available — diagnostics only, never scored. */
    val gpuRenderer: String?,
    val isEmulator: Boolean,
    val manufacturer: String? = null,
    val model: String? = null,
    val device: String? = null,
    val board: String? = null,
)

/**
 * The platform seam the [VideoPerformanceResolver] probes for AUTO detection.
 *
 * Implementations must be safe to call from any coroutine: gather everything
 * off the main thread (the probe itself hops to a background dispatcher) and
 * wrap every platform read in `runCatching`-style guards — a failed sub-read
 * degrades that field, never crashes.
 *
 * [probe] returns null only when the probe could not gather even the basic
 * signals (RAM/cores) — the resolver classifies that as MEDIUM +
 * `PROBE_FAILED` (§5.5 "probe failure → MEDIUM safe middle").
 *
 * The two methods are split by playback-path safety: [probe] gathers the
 * codec scan (`MediaCodecList`), which poisons codec initialization on
 * OMX-generation devices and therefore must run only at app start ([warm])
 * or from Settings (Re-detect); [cheapFingerprint] gathers only the always-
 * safe signals so the resolver can check device drift on every AUTO resolve.
 */
interface DevicePerformanceProbe {
    suspend fun probe(): DevicePerformanceSignals?

    /**
     * The drift-check fingerprint ([DevicePerformanceClassifier.fingerprintCheap])
     * over a fresh gather of the playback-path-safe signals — everything
     * [probe] reads except the codec scan. Null when the cheap gather fails:
     * no drift information, the resolver keeps the cache. The default null
     * opts platforms out (desktop: AUTO is pinned HIGH, drift is meaningless
     * there).
     */
    suspend fun cheapFingerprint(): String? = null
}
