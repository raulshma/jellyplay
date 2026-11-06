package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.PerformanceReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The §5.5 scoring table, tier ladder, entry-SoC family table, RAM buckets and
 * the fingerprint rules (§5.14 DevicePerformanceClassifierTest bullets).
 *
 * The "4 GB / 6 GB" bullets use realistic *usable*-RAM values (a marketing
 * "4 GB" device reports ~3.7–3.9 GB to `MemoryInfo.totalMem`) — the sticky
 * cache is bucket-based, so the exact jitter does not matter.
 */
class DevicePerformanceClassifierTest {

    /** A default HIGH device (score 0): 8 GB, 8 cores, 4K HW decode, HW HEVC. */
    private fun signals(
        isAndroid: Boolean = true,
        isLowRamDevice: Boolean = false,
        totalRamMb: Int = 8 * 1024,
        cpuCores: Int = 8,
        maxHardwareDecodeHeight: Int? = 2160,
        hardwareHevcDecode: Boolean = true,
        socFamily: String? = "qcom sm8250",
        gpuRenderer: String? = "Adreno (TM) 650",
        isEmulator: Boolean = false,
        maxHardwareDecodeBitrate: Long? = 120_000_000L,
        manufacturer: String? = "Google",
        model: String? = "Pixel 5",
        device: String? = "redfin",
        board: String? = "redfin",
    ): DevicePerformanceSignals = DevicePerformanceSignals(
        isAndroid = isAndroid,
        isLowRamDevice = isLowRamDevice,
        totalRamMb = totalRamMb,
        cpuCores = cpuCores,
        maxHardwareDecodeHeight = maxHardwareDecodeHeight,
        hardwareHevcDecode = hardwareHevcDecode,
        maxHardwareDecodeBitrate = maxHardwareDecodeBitrate,
        socFamily = socFamily,
        gpuRenderer = gpuRenderer,
        isEmulator = isEmulator,
        manufacturer = manufacturer,
        model = model,
        device = device,
        board = board,
    )

    private fun classify(signals: DevicePerformanceSignals) = DevicePerformanceClassifier.classify(signals)

    // ── §5.14 bullets — the tier outcomes ─────────────────────────────────

    @Test
    fun `4 GB 4 cores 1080p decode is LOW`() {
        // 3.7 GB usable (< 4 GB band) +3, 4 cores +2, 1080p decode (no 1440p
        // capability — the literal "< 1440" band) +2 → 7.
        val result = classify(
            signals(totalRamMb = 3_800, cpuCores = 4, maxHardwareDecodeHeight = 1080),
        )
        assertEquals(DevicePerformanceTier.LOW, result.tier)
        assertEquals(7, result.score)
        assertEquals(
            listOf(PerformanceReason.LOW_RAM, PerformanceReason.FEW_CORES, PerformanceReason.NO_HARDWARE_1440P),
            result.reasons,
        )
    }

    @Test
    fun `6 GB 8 cores 1080p decode with entry SoC is LOW`() {
        // 5.7 GB usable (< 6 GB) +2, no-1440p decode +2, Helio G99 entry
        // family +3 → 7. The entry SoC is the plan's answer to "RAM/cores
        // alone overestimate devices like a 6 GB entry phone".
        val result = classify(
            signals(totalRamMb = 5_800, cpuCores = 8, maxHardwareDecodeHeight = 1080, socFamily = "Helio G99"),
        )
        assertEquals(DevicePerformanceTier.LOW, result.tier)
        assertEquals(7, result.score)
        assertEquals(
            listOf(PerformanceReason.MODERATE_RAM, PerformanceReason.NO_HARDWARE_1440P, PerformanceReason.ENTRY_SOC),
            result.reasons,
        )
    }

    @Test
    fun `8 GB 8 cores 4K decode with HW HEVC is HIGH`() {
        val result = classify(signals())
        assertEquals(DevicePerformanceTier.HIGH, result.tier)
        assertEquals(0, result.score)
        assertTrue(result.reasons.isEmpty())
    }

    @Test
    fun `exact 6 GB 8 cores 4K decode is MEDIUM`() {
        // §5.14 bullet: "6 GB / 8 cores / 4K decode → MEDIUM". A real "6 GB"
        // device reports under 6144 MB usable (< 6 GB band) → +2 → MEDIUM.
        // (The plan's "(score 1)" annotation is internally inconsistent with
        // its own ladder — score 1 is HIGH territory, 2..4 is MEDIUM; the
        // ladder is normative, so this pins the bullet's TIER with a usable-
        // RAM value and the ladder edge below pins score 1 → HIGH.)
        val result = classify(signals(totalRamMb = 5_500))
        assertEquals(DevicePerformanceTier.MEDIUM, result.tier)
        assertEquals(2, result.score)
        assertEquals(listOf(PerformanceReason.MODERATE_RAM), result.reasons)
    }

    @Test
    fun `score exactly 1 is HIGH per the tier ladder`() {
        // 6144 MB exactly skips the < 6 GB band → only < 8 GB matches → the
        // ladder's "score <= 1 → HIGH" edge.
        val result = classify(signals(totalRamMb = 6 * 1024))
        assertEquals(1, result.score)
        assertEquals(DevicePerformanceTier.HIGH, result.tier)
    }

    @Test
    fun `probe failure is MEDIUM with PROBE_FAILED`() {
        val result = DevicePerformanceClassifier.classifyProbeFailure()
        assertEquals(DevicePerformanceTier.MEDIUM, result.tier)
        assertEquals(0, result.score)
        assertEquals(listOf(PerformanceReason.PROBE_FAILED), result.reasons)
    }

    // ── the §5.5 scoring table, row by row ────────────────────────────────

    @Test
    fun `low-RAM flag adds 3 with LOW_RAM`() {
        val result = classify(signals(isLowRamDevice = true, totalRamMb = 8 * 1024))
        assertEquals(3, result.score)
        assertTrue(PerformanceReason.LOW_RAM in result.reasons)
    }

    @Test
    fun `RAM bands are exclusive first-match`() {
        // < 4 GB: +3, only one RAM reason even though it also matches < 6/< 8.
        val low = classify(signals(totalRamMb = 2 * 1024))
        assertEquals(3, low.score)
        assertEquals(listOf(PerformanceReason.LOW_RAM), low.reasons)
        // < 6 GB: +2. < 8 GB: +1.
        assertEquals(2, classify(signals(totalRamMb = 5 * 1024)).score)
        assertEquals(1, classify(signals(totalRamMb = 7 * 1024)).score)
        assertEquals(0, classify(signals(totalRamMb = 8 * 1024)).score)
    }

    @Test
    fun `core bands are exclusive first-match`() {
        // <= 4: +2 (both bands match, only the stricter counts).
        val few = classify(signals(cpuCores = 4))
        assertEquals(2, few.score)
        assertEquals(listOf(PerformanceReason.FEW_CORES), few.reasons)
        // <= 6: +1. > 6: none.
        assertEquals(1, classify(signals(cpuCores = 6)).score)
        assertEquals(0, classify(signals(cpuCores = 7)).score)
    }

    @Test
    fun `null decode height scores as no hardware 1080p`() {
        val result = classify(signals(maxHardwareDecodeHeight = null))
        assertEquals(4, result.score)
        assertEquals(listOf(PerformanceReason.NO_HARDWARE_1080P), result.reasons)
    }

    @Test
    fun `decode height bands are exclusive first-match`() {
        // The bands are the literal "< N" comparisons: exactly 1080p decode
        // has no 1440p capability (+2), exactly 1440p has no 4K (+1).
        assertEquals(4, classify(signals(maxHardwareDecodeHeight = 720)).score)
        assertEquals(2, classify(signals(maxHardwareDecodeHeight = 1080)).score)
        assertEquals(1, classify(signals(maxHardwareDecodeHeight = 1440)).score)
        assertEquals(1, classify(signals(maxHardwareDecodeHeight = 2159)).score)
        assertEquals(0, classify(signals(maxHardwareDecodeHeight = 2160)).score)
    }

    @Test
    fun `missing hardware HEVC adds 1`() {
        val result = classify(signals(hardwareHevcDecode = false))
        assertEquals(1, result.score)
        assertEquals(listOf(PerformanceReason.NO_HARDWARE_HEVC), result.reasons)
    }

    @Test
    fun `emulator adds 2`() {
        val result = classify(signals(isEmulator = true))
        assertEquals(2, result.score)
        assertEquals(listOf(PerformanceReason.EMULATOR), result.reasons)
        // Emulator alone lands in the middle tier, not LOW.
        assertEquals(DevicePerformanceTier.MEDIUM, result.tier)
    }

    @Test
    fun `tier ladder is 5-LOW 2-4-MEDIUM 1-and-below-HIGH`() {
        assertEquals(DevicePerformanceTier.LOW, DevicePerformanceClassifier.tierForScore(5))
        assertEquals(DevicePerformanceTier.LOW, DevicePerformanceClassifier.tierForScore(12))
        assertEquals(DevicePerformanceTier.MEDIUM, DevicePerformanceClassifier.tierForScore(4))
        assertEquals(DevicePerformanceTier.MEDIUM, DevicePerformanceClassifier.tierForScore(2))
        assertEquals(DevicePerformanceTier.HIGH, DevicePerformanceClassifier.tierForScore(1))
        assertEquals(DevicePerformanceTier.HIGH, DevicePerformanceClassifier.tierForScore(0))
    }

    @Test
    fun `desktop signals short-circuit to HIGH regardless of heap or cores`() {
        // Desktop reports the JVM heap as totalRamMb and runs no codec probe;
        // §5.13 fixes desktop AUTO to HIGH.
        val result = classify(
            signals(
                isAndroid = false,
                totalRamMb = 512,
                cpuCores = 2,
                maxHardwareDecodeHeight = null,
                hardwareHevcDecode = false,
                socFamily = "Linux",
            ),
        )
        assertEquals(DevicePerformanceTier.HIGH, result.tier)
        assertEquals(0, result.score)
        assertTrue(result.reasons.isEmpty())
    }

    // ── the entry-SoC family table ─────────────────────────────────────────

    @Test
    fun `entry SoC families match case-insensitively across the documented table`() {
        val families = mapOf(
            "Helio G99" to true,
            "helio g85" to true,
            "Helio A22" to true,
            "Snapdragon 460" to true,
            "snapdragon 6 gen 1" to true,
            "Unisoc T618" to true,
            "unisoc tiger" to true,
            "MT6768" to true,
            "mt6877" to true,
            "Exynos 7884" to true,
            "exynos 850" to true,
            "rk3318" to true,
            "rk3566 box" to true,
            "Amlogic S905X4" to true,
            "amlogic" to true,
        )
        families.forEach { (soc, expected) ->
            assertEquals(expected, DevicePerformanceClassifier.isEntrySoc(soc), soc)
        }
    }

    @Test
    fun `flagship SoCs are not entry`() {
        listOf("Snapdragon 8 Gen 2", "sm8250", "Exynos 990", "Dimensity 1200", "helio p70")
            .forEach { soc ->
                assertFalse(DevicePerformanceClassifier.isEntrySoc(soc), soc)
            }
    }

    @Test
    fun `null or blank SoC is not entry`() {
        assertFalse(DevicePerformanceClassifier.isEntrySoc(null))
        assertFalse(DevicePerformanceClassifier.isEntrySoc(""))
        assertFalse(DevicePerformanceClassifier.isEntrySoc("   "))
    }

    @Test
    fun `entry SoC adds 3 with ENTRY_SOC reason`() {
        val result = classify(signals(socFamily = "UNISOC T610"))
        assertEquals(3, result.score)
        assertEquals(listOf(PerformanceReason.ENTRY_SOC), result.reasons)
    }

    // ── RAM bucketing ──────────────────────────────────────────────────────

    @Test
    fun `RAM buckets cover the marketing sizes`() {
        val expected = mapOf(
            1_900 to 2,
            2_560 to 3,
            3_583 to 3,
            3_584 to 4,
            5_119 to 4,
            5_120 to 6,
            7_167 to 6,
            7_168 to 8,
            10_239 to 8,
            10_240 to 12,
            14_335 to 12,
            14_336 to 16,
            18_431 to 16,
            18_432 to 32,
            32_768 to 32,
        )
        expected.forEach { (mb, bucket) ->
            assertEquals(bucket, DevicePerformanceClassifier.ramBucketGb(mb), "$mb MB")
        }
    }

    @Test
    fun `reported-RAM jitter stays inside one bucket`() {
        // A "6 GB" phone reporting anywhere in 5.5–6.1 GB usable → one bucket,
        // so the sticky cache never churns on reported-value jitter.
        val low = DevicePerformanceClassifier.ramBucketGb(5_500)
        val high = DevicePerformanceClassifier.ramBucketGb(6_100)
        assertEquals(low, high)
    }

    // ── the fingerprint (§5.5) ─────────────────────────────────────────────

    @Test
    fun `fingerprint is stable and 64 lowercase hex chars`() {
        val fp = DevicePerformanceClassifier.fingerprint(signals())
        assertEquals(fp, DevicePerformanceClassifier.fingerprint(signals()))
        assertEquals(64, fp.length)
        assertTrue(fp.all { it in "0123456789abcdef" })
    }

    @Test
    fun `fingerprint is stable across free-RAM and other jitter changes`() {
        // Free memory is not even a signal field; the volatile values that ARE
        // signals (decode bitrate, GL renderer) are deliberately excluded from
        // the fingerprint so the sticky tier does not churn on them.
        val base = DevicePerformanceClassifier.fingerprint(signals())
        assertEquals(
            base,
            DevicePerformanceClassifier.fingerprint(signals(maxHardwareDecodeBitrate = 42_000_000L)),
            "decode bitrate jitter must not invalidate the cache",
        )
        assertEquals(
            base,
            DevicePerformanceClassifier.fingerprint(signals(gpuRenderer = "Adreno (TM) 6XX")),
            "renderer string jitter must not invalidate the cache",
        )
    }

    @Test
    fun `fingerprint changes when the SoC family changes`() {
        assertNotEquals(
            DevicePerformanceClassifier.fingerprint(signals(socFamily = "qcom sm8250")),
            DevicePerformanceClassifier.fingerprint(signals(socFamily = "qcom sm8350")),
        )
    }

    @Test
    fun `fingerprint changes across a RAM bucket boundary but not within one`() {
        val base = DevicePerformanceClassifier.fingerprint(signals(totalRamMb = 5_800))
        assertEquals(
            base,
            DevicePerformanceClassifier.fingerprint(signals(totalRamMb = 6_100)),
            "jitter within one bucket must keep the fingerprint",
        )
        assertNotEquals(
            base,
            DevicePerformanceClassifier.fingerprint(signals(totalRamMb = 7_200)),
            "a bucket change must invalidate the cache",
        )
    }

    @Test
    fun `fingerprint changes with cores, decode capability, identity and emulator flag`() {
        val base = DevicePerformanceClassifier.fingerprint(signals())
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(cpuCores = 6)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(maxHardwareDecodeHeight = 1080)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(hardwareHevcDecode = false)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(isEmulator = true)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(manufacturer = "Samsung")))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(model = "Galaxy S21")))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(device = "o1s")))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprint(signals(board = "exynos2100")))
    }

    @Test
    fun `fingerprint changes across detector versions`() {
        val current = DevicePerformanceClassifier.fingerprint(signals())
        assertEquals(DevicePerformanceClassifier.DETECTOR_VERSION, 1, "plan pins the initial detector version at 1")
        assertNotEquals(
            current,
            DevicePerformanceClassifier.fingerprint(signals(), detectorVersion = 2),
            "bumping the detector version must force a re-detect on every device",
        )
    }

    // ── the cheap drift fingerprint ────────────────────────────────────────

    @Test
    fun `cheap fingerprint is stable and differs from the full fingerprint`() {
        val cheap = DevicePerformanceClassifier.fingerprintCheap(signals())
        assertEquals(cheap, DevicePerformanceClassifier.fingerprintCheap(signals()))
        assertEquals(64, cheap.length)
        assertNotEquals(
            DevicePerformanceClassifier.fingerprint(signals()),
            cheap,
            "the two identities hash different field sets",
        )
    }

    @Test
    fun `cheap fingerprint ignores the codec-scan fields`() {
        // The property the playback-path split exists for: a ROM update that
        // changes decode capability does not trip the per-resolve drift check
        // (it cannot — the cheap path never reads the codec list); the safe
        // identity signals still do.
        val base = DevicePerformanceClassifier.fingerprintCheap(signals())
        assertEquals(
            base,
            DevicePerformanceClassifier.fingerprintCheap(signals(maxHardwareDecodeHeight = 1080)),
            "decode height is a codec-scan field the cheap path never gathers",
        )
        assertEquals(
            base,
            DevicePerformanceClassifier.fingerprintCheap(signals(hardwareHevcDecode = false)),
            "the HEVC flag is a codec-scan field the cheap path never gathers",
        )
    }

    @Test
    fun `cheap fingerprint still tracks the safe identity signals`() {
        val base = DevicePerformanceClassifier.fingerprintCheap(signals())
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(socFamily = "qcom sm8350")))
        // 7_100 MB lands in the 6 GB bucket (the 8 GB band starts at 7_168 MB);
        // the cheap fingerprint hashes the bucket, so the band must be crossed.
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(totalRamMb = 7_100)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(cpuCores = 6)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(isEmulator = true)))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(model = "Galaxy S21")))
        assertNotEquals(base, DevicePerformanceClassifier.fingerprintCheap(signals(), detectorVersion = 2))
    }
}
