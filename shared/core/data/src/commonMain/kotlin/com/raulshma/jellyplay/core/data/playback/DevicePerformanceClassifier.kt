package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.PerformanceReason

/**
 * Pure scoring / fingerprint / tier logic behind AUTO detection (issue #186
 * §5.5). No platform types — the probes feed [DevicePerformanceSignals] in,
 * this object maps them to a tier + reasons + a stable fingerprint — so the
 * whole heuristic is JVM-testable.
 *
 * ## Scoring (the §5.5 table)
 *
 * Each signal group contributes AT MOST one row — the bands are exclusive
 * first-match (a 2 GB device gets the `< 4 GB` row's +3, not +3+2+1; the
 * reason lists stay one-per-group so diagnostics read `LOW_RAM`, never both
 * RAM reasons at once):
 *
 * | Signal              | Condition              | Points | Reason              |
 * |---------------------|------------------------|--------|---------------------|
 * | Low-RAM flag        | isLowRamDevice         | +3     | LOW_RAM             |
 * | RAM                 | < 4 GB                 | +3     | LOW_RAM             |
 * | RAM                 | < 6 GB                 | +2     | MODERATE_RAM        |
 * | RAM                 | < 8 GB                 | +1     | MODERATE_RAM        |
 * | CPU cores           | <= 4                   | +2     | FEW_CORES           |
 * | CPU cores           | <= 6                   | +1     | MODERATE_CORES      |
 * | Max HW decode height| null or < 1080         | +4     | NO_HARDWARE_1080P   |
 * | Max HW decode height| < 1440                 | +2     | NO_HARDWARE_1440P   |
 * | Max HW decode height| < 2160                 | +1     | NO_HARDWARE_4K      |
 * | HW HEVC             | false                  | +1     | NO_HARDWARE_HEVC    |
 * | Entry SoC family    | known entry family     | +3     | ENTRY_SOC           |
 * | Emulator            | true                   | +2     | EMULATOR            |
 *
 * Tier: score >= 5 → LOW, 2..4 → MEDIUM, <= 1 → HIGH.
 *
 * ## Desktop
 *
 * A non-Android signal set short-circuits to HIGH with no reasons: desktop
 * hosts run no codec probe (a null height there means "not measured", not
 * "cannot decode 1080p"), their RAM seam is the JVM heap max — not machine
 * RAM — and §5.13 fixes desktop AUTO to HIGH ("Android-only UI in v1; AUTO
 * resolves HIGH"). The desktop probe still gathers signals for the settings
 * diagnostics display; only the scoring is skipped.
 *
 * ## Fingerprint
 *
 * `sha256(detectorVersion|manufacturer|model|device|board|socFamily|
 * ramBucket(totalRamMb)|cpuCores|maxHardwareDecodeHeight|hardwareHevcDecode|
 * isEmulator)`. Deliberately excluded (so the tier does not churn): free
 * memory, thermal state, network, OS patch level, the low-RAM flag,
 * [DevicePerformanceSignals.maxHardwareDecodeBitrate] and
 * [DevicePerformanceSignals.gpuRenderer] (jittery values that do not change
 * what the tier means). RAM is bucketed (2/3/4/6/8/12/16/32 GB) so a small
 * reported-value jitter does not invalidate the sticky cache.
 */
object DevicePerformanceClassifier {

    /**
     * Bump when the heuristic changes in a way that should force a re-detect
     * on every device (§5.5: "the detector version changes (an intentional
     * re-detect after improving the heuristic)").
     */
    const val DETECTOR_VERSION = 1

    /** One classifier run: the tier it picked, why, and the raw score. */
    data class DeviceClassification(
        val tier: DevicePerformanceTier,
        val score: Int,
        val reasons: List<PerformanceReason>,
    )

    // ── Scoring thresholds (megabytes / px / counts) ──────────────────────
    private const val RAM_LOW_MB = 4 * 1024
    private const val RAM_MODERATE_MB = 6 * 1024
    private const val RAM_MODERATE_UPPER_MB = 8 * 1024
    private const val FEW_CORES = 4
    private const val MODERATE_CORES = 6
    private const val HEIGHT_1080P = 1080
    private const val HEIGHT_1440P = 1440
    private const val HEIGHT_4K = 2160

    /**
     * The documented entry-SoC family table (§5.5). Matched case-insensitively
     * with `contains` against the combined [DevicePerformanceSignals.socFamily]
     * string. A known entry SoC gets +3 because RAM/cores alone overestimate
     * devices like a 6 GB entry phone — the SoC family is the strongest single
     * signal. Heuristic, not a guarantee; the manual profile selector overrides.
     */
    private val ENTRY_SOC_FAMILIES = listOf(
        "helio g",
        "helio a",
        "snapdragon 4",
        "snapdragon 6",
        "unisoc",
        "mt67",
        "mt68",
        "exynos 7",
        "exynos 8",
        // TV-box SoCs (the plan's `rk3`/`amlogic` families — rk3318/rk3399/
        // rk3566-class boxes are the classic 1080p-stutter reporters).
        "rk3",
        "amlogic",
    )

    /**
     * Classifies a real probe result. See the class KDoc for the table and the
     * desktop short-circuit.
     */
    fun classify(signals: DevicePerformanceSignals): DeviceClassification {
        if (!signals.isAndroid) {
            return DeviceClassification(DevicePerformanceTier.HIGH, 0, emptyList())
        }
        val (score, reasons) = score(signals)
        return DeviceClassification(tierForScore(score), score, reasons)
    }

    /**
     * The probe could not gather even the basic signals: the safe middle
     * (MEDIUM) with [PerformanceReason.PROBE_FAILED]. The empty fingerprint
     * the resolver pairs with this never matches a real fingerprint, so the
     * next successful probe re-detects instead of sticking to the failure.
     */
    fun classifyProbeFailure(): DeviceClassification =
        DeviceClassification(DevicePerformanceTier.MEDIUM, 0, listOf(PerformanceReason.PROBE_FAILED))

    /** The §5.5 tier ladder over the raw score. */
    fun tierForScore(score: Int): DevicePerformanceTier = when {
        score >= 5 -> DevicePerformanceTier.LOW
        score in 2..4 -> DevicePerformanceTier.MEDIUM
        else -> DevicePerformanceTier.HIGH
    }

    /**
     * The entry-SoC family match: case-insensitive `contains` over the
     * combined socFamily string (a probe may report `SOC_MODEL` + board
     * fragments in one string).
     */
    fun isEntrySoc(socFamily: String?): Boolean {
        if (socFamily.isNullOrBlank()) return false
        val normalized = socFamily.lowercase()
        return ENTRY_SOC_FAMILIES.any { normalized.contains(it) }
    }

    /**
     * RAM bucketing for the fingerprint (§5.5: "Use RAM buckets (e.g.
     * 2/3/4/6/8/12/16 GB) so a small reported-value jitter does not invalidate
     * the cache"). The boundaries sit midway between the marketing sizes so a
     * "6 GB" phone reporting 5.5–5.9 GB usable always lands in the 6 bucket.
     */
    fun ramBucketGb(totalRamMb: Int): Int = when {
        totalRamMb < 2_560 -> 2
        totalRamMb < 3_584 -> 3
        totalRamMb < 5_120 -> 4
        totalRamMb < 7_168 -> 6
        totalRamMb < 10_240 -> 8
        totalRamMb < 14_336 -> 12
        totalRamMb < 18_432 -> 16
        else -> 32
    }

    /**
     * The stable detection fingerprint over
     * `detectorVersion|manufacturer|model|device|board|socFamily|ramBucket|
     * cpuCores|maxHardwareDecodeHeight|hardwareHevcDecode|isEmulator`.
     * [detectorVersion] defaults to [DETECTOR_VERSION]; tests override it to
     * prove the version participates.
     */
    fun fingerprint(
        signals: DevicePerformanceSignals,
        detectorVersion: Int = DETECTOR_VERSION,
    ): String = sha256Hex(
        listOf(
            detectorVersion.toString(),
            signals.manufacturer.orEmpty(),
            signals.model.orEmpty(),
            signals.device.orEmpty(),
            signals.board.orEmpty(),
            signals.socFamily.orEmpty(),
            ramBucketGb(signals.totalRamMb).toString(),
            signals.cpuCores.toString(),
            signals.maxHardwareDecodeHeight?.toString().orEmpty(),
            signals.hardwareHevcDecode.toString(),
            signals.isEmulator.toString(),
        ).joinToString(SEPARATOR),
    )

    /** The §5.5 table applied — exclusive first-match bands, one reason per group. */
    private fun score(signals: DevicePerformanceSignals): Pair<Int, List<PerformanceReason>> {
        var score = 0
        val reasons = mutableListOf<PerformanceReason>()

        if (signals.isLowRamDevice) {
            score += 3
            reasons += PerformanceReason.LOW_RAM
        }
        when {
            signals.totalRamMb < RAM_LOW_MB -> {
                score += 3
                reasons += PerformanceReason.LOW_RAM
            }
            signals.totalRamMb < RAM_MODERATE_MB -> {
                score += 2
                reasons += PerformanceReason.MODERATE_RAM
            }
            signals.totalRamMb < RAM_MODERATE_UPPER_MB -> {
                score += 1
                reasons += PerformanceReason.MODERATE_RAM
            }
        }
        when {
            signals.cpuCores <= FEW_CORES -> {
                score += 2
                reasons += PerformanceReason.FEW_CORES
            }
            signals.cpuCores <= MODERATE_CORES -> {
                score += 1
                reasons += PerformanceReason.MODERATE_CORES
            }
        }
        val height = signals.maxHardwareDecodeHeight
        when {
            height == null || height < HEIGHT_1080P -> {
                score += 4
                reasons += PerformanceReason.NO_HARDWARE_1080P
            }
            height < HEIGHT_1440P -> {
                score += 2
                reasons += PerformanceReason.NO_HARDWARE_1440P
            }
            height < HEIGHT_4K -> {
                score += 1
                reasons += PerformanceReason.NO_HARDWARE_4K
            }
        }
        if (!signals.hardwareHevcDecode) {
            score += 1
            reasons += PerformanceReason.NO_HARDWARE_HEVC
        }
        if (isEntrySoc(signals.socFamily)) {
            score += 3
            reasons += PerformanceReason.ENTRY_SOC
        }
        if (signals.isEmulator) {
            score += 2
            reasons += PerformanceReason.EMULATOR
        }
        return score to reasons
    }

    private const val SEPARATOR = "|"
}

/**
 * SHA-256 of [input] (UTF-8) as a lowercase hex string. expect/actual because
 * the classifier lives in commonMain while the JVM(android+desktop) actual
 * rides `java.security.MessageDigest` — the repo's only hashing dependency
 * (see OfflineSyncComparator's sha256Hex / PinHasher's MessageDigest use).
 */
internal expect fun sha256Hex(input: String): String
