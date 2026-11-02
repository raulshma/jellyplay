package com.raulshma.jellyplay.core.data.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.DevicePerformanceStore
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.model.PerformanceReason
import com.raulshma.jellyplay.core.model.VideoPerformanceMatrix
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

/**
 * The §5.5 sticky-cache contract behind AUTO detection (the §5.14 resolver
 * bullets): the same fingerprint reuses the cached detection, a changed
 * fingerprint re-detects, an explicit profile ignores detection for the
 * effective tier, and Re-detect ignores the cache. Runs against a REAL
 * [DevicePerformanceStore] over a per-test temp DataStore file, so the
 * save/read/clear round-trips are the production ones.
 */
class VideoPerformanceResolverTest {

    private class FakeProbe(var signals: DevicePerformanceSignals?) : DevicePerformanceProbe {
        var probeCount = 0
        override suspend fun probe(): DevicePerformanceSignals? {
            probeCount++
            return signals
        }
    }

    private var nowMs = 0L
    private val clock = EpochMillisSource { nowMs }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
        scope = scope,
    ) {
        File.createTempFile("perf-resolver-test", ".preferences_pb")
            .absolutePath
            .toPath()
    }

    private val probe = FakeProbe(HighEndAndroid)
    private val store = DevicePerformanceStore(dataStore, scope)
    private val playbackStore = PlaybackStore(dataStore, scope)

    private fun resolver() = VideoPerformanceResolver(
        probe = probe,
        store = store,
        playbackStore = playbackStore,
        timeSource = clock,
    )

    /** 8 GB / 8 cores / 4K HW decode / HW HEVC → score 0 → HIGH. */
    private companion object {
        val HighEndAndroid = DevicePerformanceSignals(
            isAndroid = true,
            isLowRamDevice = false,
            totalRamMb = 8 * 1024,
            cpuCores = 8,
            maxHardwareDecodeHeight = 2160,
            hardwareHevcDecode = true,
            maxHardwareDecodeBitrate = 120_000_000L,
            socFamily = "qcom sm8250",
            gpuRenderer = "Adreno (TM) 650",
            isEmulator = false,
            manufacturer = "Google",
            model = "Pixel 5",
            device = "redfin",
            board = "redfin",
        )

        /** 3.7 GB / 4 cores / 1080p / entry SoC → score 5 → LOW. */
        val LowEndAndroid = HighEndAndroid.copy(
            totalRamMb = 3_800,
            cpuCores = 4,
            maxHardwareDecodeHeight = 1080,
            socFamily = "Helio G99",
        )

        /** Same device, jittery diagnostics-only values → same fingerprint. */
        val HighEndJittered = HighEndAndroid.copy(
            maxHardwareDecodeBitrate = 42_000_000L,
            gpuRenderer = "Adreno (TM) 6XX",
        )

        /** SoC changed (user moved the SIM to another device? — whatever) → new fingerprint. */
        val DifferentSoc = HighEndAndroid.copy(socFamily = "qcom sm8350")
    }

    // ── AUTO: cold detect, then sticky reuse ───────────────────────────────

    @Test
    fun `AUTO with no cache probes, classifies and saves`() = runBlocking {
        probe.signals = LowEndAndroid
        val resolver = resolver()

        assertNull(store.read(), "precondition: nothing detected yet")

        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.LOW, profile.tier)
        assertEquals(VideoPerformanceMatrix.forTier(DevicePerformanceTier.LOW), profile.tuning)
        assertEquals(VideoPerformanceProfile.AUTO, profile.requested)

        val saved = store.read()
        assertEquals(DevicePerformanceTier.LOW, saved?.tier)
        // 3.7 GB +3, 4 cores +2, no-1440p decode +2, entry SoC +3 → 10.
        assertEquals(10, saved?.score)
        assertEquals(
            listOf(
                PerformanceReason.LOW_RAM,
                PerformanceReason.FEW_CORES,
                PerformanceReason.NO_HARDWARE_1440P,
                PerformanceReason.ENTRY_SOC,
            ),
            saved?.reasons,
        )
        assertEquals(DevicePerformanceClassifier.DETECTOR_VERSION, saved?.detectorVersion)
        assertEquals(DevicePerformanceClassifier.fingerprint(LowEndAndroid), saved?.fingerprint)
        assertEquals(saved, resolver.resolved.value.detection)
        scope.cancel()
    }

    @Test
    fun `resolved is seeded with the AUTO-MEDIUM safe middle`() {
        val resolver = resolver()
        val seed = resolver.resolved.value
        assertEquals(VideoPerformanceProfile.AUTO, seed.requested)
        assertEquals(DevicePerformanceTier.MEDIUM, seed.tier)
        assertNull(seed.detection)
        scope.cancel()
    }

    @Test
    fun `same fingerprint reuses the cached detection`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.resolve(VideoPerformanceProfile.AUTO)
        val first = store.read()!!

        // Jittery (non-fingerprint) signal changes + the clock moved on — the
        // sticky cache must win, not a fresh classification with a new stamp.
        probe.signals = HighEndJittered
        nowMs = 5_000L
        resolver.resolve(VideoPerformanceProfile.AUTO)

        val second = store.read()!!
        assertEquals(first, second, "the cached detection must be reused verbatim")
        assertEquals(1_000L, second.detectedAtEpochMs)
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)
        scope.cancel()
    }

    @Test
    fun `changed fingerprint re-detects and re-saves`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.resolve(VideoPerformanceProfile.AUTO)
        val first = store.read()!!

        probe.signals = DifferentSoc
        nowMs = 9_000L
        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)

        val second = store.read()!!
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertNotEquals(first.detectedAtEpochMs, second.detectedAtEpochMs)
        assertEquals(9_000L, second.detectedAtEpochMs)
        assertEquals(second, profile.detection)
        assertEquals(DevicePerformanceClassifier.fingerprint(DifferentSoc), second.fingerprint)
        scope.cancel()
    }

    // ── explicit profiles bypass detection for the effective tier ──────────

    @Test
    fun `explicit profile ignores detection for the effective tier`() = runBlocking {
        probe.signals = HighEndAndroid // detection says HIGH
        val resolver = resolver()
        resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)

        val low = resolver.resolve(VideoPerformanceProfile.LOW)
        assertEquals(DevicePerformanceTier.LOW, low.tier, "the pinned profile wins over the detection")
        assertEquals(VideoPerformanceMatrix.forTier(DevicePerformanceTier.LOW), low.tuning)
        assertEquals(DevicePerformanceTier.LOW, resolver.resolved.value.tier)

        val medium = resolver.resolve(VideoPerformanceProfile.MEDIUM)
        assertEquals(DevicePerformanceTier.MEDIUM, medium.tier)
        assertEquals(VideoPerformanceMatrix.forTier(DevicePerformanceTier.MEDIUM), medium.tuning)
        scope.cancel()
    }

    @Test
    fun `explicit profile still carries the cached detection for diagnostics`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        resolver.resolve(VideoPerformanceProfile.AUTO)
        val cached = store.read()!!

        val low = resolver.resolve(VideoPerformanceProfile.LOW)
        assertEquals(cached, low.detection, "settings subtitle / diagnostics keep the cached run")
        scope.cancel()
    }

    // ── redetect ignores the cache ─────────────────────────────────────────

    @Test
    fun `redetect clears the cache, re-probes and saves`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.resolve(VideoPerformanceProfile.AUTO)
        val cached = store.read()!!

        probe.signals = LowEndAndroid
        nowMs = 20_000L
        val redetected = resolver.redetect()

        assertEquals(DevicePerformanceTier.LOW, redetected.tier, "redetect must ignore the HIGH cache")
        assertEquals(20_000L, redetected.detectedAtEpochMs)
        assertNotEquals(cached.fingerprint, redetected.fingerprint)
        assertEquals(redetected, store.read())
        assertEquals(DevicePerformanceTier.LOW, resolver.resolved.value.tier)
        scope.cancel()
    }

    @Test
    fun `redetect keeps an explicit profile's tier while refreshing the detection`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        resolver.resolve(VideoPerformanceProfile.LOW)
        assertEquals(DevicePerformanceTier.LOW, resolver.resolved.value.tier)

        probe.signals = LowEndAndroid
        val redetected = resolver.redetect()
        assertEquals(DevicePerformanceTier.LOW, redetected.tier, "the probe result is saved")
        // The requested profile is explicit LOW → the effective tier stays LOW.
        assertEquals(DevicePerformanceTier.LOW, resolver.resolved.value.tier)
        scope.cancel()
    }

    // ── probe failure semantics ─────────────────────────────────────────────

    @Test
    fun `probe failure with no cache saves MEDIUM with PROBE_FAILED`() = runBlocking {
        probe.signals = null
        val resolver = resolver()

        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.MEDIUM, profile.tier)
        val saved = store.read()!!
        assertEquals(listOf(PerformanceReason.PROBE_FAILED), saved.reasons)
        assertEquals("", saved.fingerprint, "a failed probe can never stick")
        assertEquals(DevicePerformanceTier.MEDIUM, resolver.resolved.value.tier)
        scope.cancel()
    }

    @Test
    fun `probe failure with a usable cache keeps the cache`() = runBlocking {
        probe.signals = LowEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.resolve(VideoPerformanceProfile.AUTO)
        val cached = store.read()!!

        probe.signals = null // transient probe failure
        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.LOW, profile.tier, "a failed read gives zero information")
        assertEquals(cached, profile.detection)
        scope.cancel()
    }

    // ── warm ────────────────────────────────────────────────────────────────

    @Test
    fun `warm populates the resolved profile and survives a throwing probe`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        resolver.warm()
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)

        // A throwing probe must not take startup down: warm swallows, the seed
        // resolution stands, and the next successful resolve recovers.
        probe.signals = null
        store.clear()
        resolver.warm()
        assertEquals(DevicePerformanceTier.MEDIUM, resolver.resolved.value.tier)
        assertEquals(listOf(PerformanceReason.PROBE_FAILED), store.read()?.reasons)
        scope.cancel()
    }
}
