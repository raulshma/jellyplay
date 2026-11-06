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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath

/**
 * The playback-path contract behind AUTO detection: [VideoPerformanceResolver.resolve]
 * NEVER probes (the cold cache answers the MEDIUM seed — the probe poisons
 * codec initialization on OMX devices, see the resolver's KDoc), [warm] is
 * the launch-time probe owner (cold-cache detect + cheap-fingerprint drift
 * refresh), an explicit profile bypasses detection for the effective tier,
 * and Re-detect ignores the cache. Runs against a REAL [DevicePerformanceStore]
 * over a per-test temp DataStore file, so the save/read/clear round-trips are
 * the production ones.
 */
class VideoPerformanceResolverTest {

    /**
     * [cheapFingerprint] models the real cheap probe: derived from the same
     * signals [probe] gathers (so a post-refresh cache matches the live
     * device), unless [cheapOverride] simulates a ROM/device change the full
     * gather would notice only at the next real probe.
     */
    private class FakeProbe(var signals: DevicePerformanceSignals?) : DevicePerformanceProbe {
        var probeCount = 0
        var cheapCount = 0
        var cheapOverride: String? = null

        override suspend fun probe(): DevicePerformanceSignals? {
            probeCount++
            return signals
        }

        override suspend fun cheapFingerprint(): String? {
            cheapCount++
            cheapOverride?.let { return it }
            return signals?.let { DevicePerformanceClassifier.fingerprintCheap(it) }
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

        /** Same device, jittery diagnostics-only values → same fingerprints. */
        val HighEndJittered = HighEndAndroid.copy(
            maxHardwareDecodeBitrate = 42_000_000L,
            gpuRenderer = "Adreno (TM) 6XX",
        )

        /** SoC changed (user moved the SIM to another device? — whatever) → drift. */
        val DifferentSoc = HighEndAndroid.copy(socFamily = "qcom sm8350")
    }

    // ── AUTO: the resolve path never probes ────────────────────────────────

    @Test
    fun `AUTO with a cold cache answers the MEDIUM seed without probing`() = runBlocking {
        probe.signals = LowEndAndroid
        val resolver = resolver()

        assertNull(store.read(), "precondition: nothing detected yet")

        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.MEDIUM, profile.tier, "the seed answers, not a detection")
        assertEquals(VideoPerformanceMatrix.forTier(DevicePerformanceTier.MEDIUM), profile.tuning)
        assertEquals(VideoPerformanceProfile.AUTO, profile.requested)
        assertNull(profile.detection, "the seed has no detection to carry")
        assertEquals(0, probe.probeCount, "resolve contains no probe call — by construction")
        assertNull(store.read(), "nothing is saved from the resolve path; warm owns the retry")
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
    fun `warm detects on a cold cache, classifies and saves`() = runBlocking {
        probe.signals = LowEndAndroid
        val resolver = resolver()

        resolver.warm()
        assertEquals(1, probe.probeCount, "warm is the cold-cache probe owner")

        val profile = resolver.resolved.value
        assertEquals(VideoPerformanceProfile.AUTO, profile.requested)
        assertEquals(DevicePerformanceTier.LOW, profile.tier)
        assertEquals(VideoPerformanceMatrix.forTier(DevicePerformanceTier.LOW), profile.tuning)

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
        assertEquals(
            DevicePerformanceClassifier.fingerprintCheap(LowEndAndroid),
            saved?.cheapFingerprint,
            "every full detect stamps the cheap drift identity too",
        )
        assertEquals(saved, resolver.resolved.value.detection)
        scope.cancel()
    }

    @Test
    fun `usable cache never re-probes on resolve`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.warm()
        val first = store.read()!!
        assertEquals(1, probe.probeCount, "precondition: warm detected once")

        // Signal jitter, the clock moving on — none of it matters: a
        // version-match cache with a matching cheap fingerprint answers AUTO
        // outright. The probe MUST stay off the playback path (the resolver's
        // KDoc — probing near codec init blacked video on OMX devices).
        probe.signals = HighEndJittered
        nowMs = 5_000L
        val second = resolver.resolve(VideoPerformanceProfile.AUTO)
        val third = resolver.resolve(VideoPerformanceProfile.AUTO)

        assertEquals(1, probe.probeCount, "no probe ever runs on the resolve path")
        assertEquals(2, probe.cheapCount, "the cheap drift check rode each AUTO resolve — and set no pending")
        assertEquals(first, store.read(), "the sticky detection is reused verbatim")
        assertEquals(1_000L, store.read()?.detectedAtEpochMs)
        assertEquals(first, second.detection)
        assertEquals(first, third.detection)
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)

        // And warm over the still-fresh cache is a pure read too.
        resolver.warm()
        assertEquals(1, probe.probeCount)
        scope.cancel()
    }

    @Test
    fun `resolve waits on an in-flight warm detect and shares its result`() = runBlocking {
        // Warm's probe is gated mid-flight while it HOLDS the detect mutex —
        // a resolve arriving then must wait and adopt the fresh detection
        // rather than answer the seed (the fresh-install race's good path).
        val entered = Channel<Unit>()
        val gate = Channel<Unit>()
        var resolvedTier: DevicePerformanceTier? = null
        val gatedProbe = object : DevicePerformanceProbe {
            var probeCount = 0
            override suspend fun probe(): DevicePerformanceSignals? {
                probeCount++
                entered.send(Unit)
                gate.receive()
                return LowEndAndroid
            }
        }
        val resolver = VideoPerformanceResolver(gatedProbe, store, playbackStore, clock)
        val warmJob = launch(Dispatchers.IO) { resolver.warm() }
        entered.receive() // warm is parked inside its probe, holding the mutex

        val resolveJob = launch(Dispatchers.IO) {
            resolvedTier = resolver.resolve(VideoPerformanceProfile.AUTO).tier
        }
        // Release warm: it saves + publishes + releases the mutex, and the
        // parked resolve adopts the fresh result instead of the seed.
        gate.send(Unit)
        warmJob.join()
        resolveJob.join()

        assertEquals(DevicePerformanceTier.LOW, resolvedTier, "resolve shared warm's fresh detect")
        assertEquals(1, gatedProbe.probeCount, "exactly one probe for both callers")
        assertEquals(DevicePerformanceTier.LOW, store.read()?.tier)
        scope.cancel()
    }

    @Test
    fun `stale detector version invalidates the cache and re-detects at the next warm`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.warm()
        val first = store.read()!!

        // The detector moved (an app update shipped a better heuristic) — the
        // stored run's version no longer matches. Resolve degrades to the
        // seed; warm owns the re-detect.
        store.save(first.copy(detectorVersion = DevicePerformanceClassifier.DETECTOR_VERSION - 1))
        probe.signals = DifferentSoc
        nowMs = 5_000L
        val degraded = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.MEDIUM, degraded.tier)
        assertNull(degraded.detection)
        assertEquals(1, probe.probeCount, "resolve never probes — not even on an invalid cache")

        nowMs = 9_000L
        resolver.warm()
        assertEquals(2, probe.probeCount)
        val second = store.read()!!
        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(9_000L, second.detectedAtEpochMs)
        assertEquals(DevicePerformanceClassifier.fingerprint(DifferentSoc), second.fingerprint)
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)
        scope.cancel()
    }

    // ── cheap-fingerprint drift: schedule at resolve, refresh at warm ──────

    @Test
    fun `cheap-fingerprint drift is scheduled by resolve and consumed by warm`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.warm()
        val cached = store.read()!!
        assertEquals(1, probe.probeCount)

        // The device moved (new ROM): the live cheap identity no longer
        // matches the sticky cache.
        probe.cheapOverride = "drifted-device"
        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.HIGH, profile.tier, "drift scheduling never changes the answer")
        assertEquals(cached, profile.detection)
        assertEquals(1, probe.probeCount, "the drift check itself is probe-free")

        nowMs = 9_000L
        resolver.warm()
        assertEquals(2, probe.probeCount, "warm consumed the pending drift with a full re-detect")
        val fresh = store.read()!!
        assertEquals(9_000L, fresh.detectedAtEpochMs)
        assertNotEquals(cached, fresh)

        // The re-detect refreshed the cheap identity, so drift is retired:
        // the next warm is a pure read again.
        probe.cheapOverride = null
        resolver.warm()
        assertEquals(2, probe.probeCount)
        scope.cancel()
    }

    @Test
    fun `an old-schema cache without a cheap fingerprint refreshes at the next warm`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.warm()
        val before = store.read()!!
        assertEquals(1, probe.probeCount)

        // A detection persisted before the cheap field existed: empty cheap
        // fingerprint = "cheap identity unknown" = drift by definition.
        store.save(before.copy(cheapFingerprint = ""))
        nowMs = 9_000L
        resolver.warm()

        assertEquals(2, probe.probeCount, "the one-time schema refresh re-probes at launch")
        assertEquals(9_000L, store.read()?.detectedAtEpochMs)
        assertEquals(
            DevicePerformanceClassifier.fingerprintCheap(HighEndAndroid),
            store.read()?.cheapFingerprint,
            "the refresh repopulates the cheap identity",
        )
        scope.cancel()
    }

    // ── explicit profiles bypass detection for the effective tier ──────────

    @Test
    fun `explicit profile ignores detection for the effective tier`() = runBlocking {
        probe.signals = HighEndAndroid // detection says HIGH
        val resolver = resolver()
        resolver.warm()
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
        resolver.warm()
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
        resolver.warm()
        val cached = store.read()!!

        probe.signals = LowEndAndroid
        nowMs = 20_000L
        val redetected = resolver.redetect()

        assertEquals(DevicePerformanceTier.LOW, redetected.tier, "redetect must ignore the HIGH cache")
        assertEquals(20_000L, redetected.detectedAtEpochMs)
        assertNotEquals(cached.fingerprint, redetected.fingerprint)
        assertEquals(DevicePerformanceClassifier.fingerprintCheap(LowEndAndroid), redetected.cheapFingerprint)
        assertEquals(redetected, store.read())
        assertEquals(DevicePerformanceTier.LOW, resolver.resolved.value.tier)
        scope.cancel()
    }

    @Test
    fun `redetect keeps an explicit profile's tier while refreshing the detection`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        resolver.warm()
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
    fun `probe failure with no cache saves MEDIUM with PROBE_FAILED at warm`() = runBlocking {
        probe.signals = null
        val resolver = resolver()

        resolver.warm()
        assertEquals(DevicePerformanceTier.MEDIUM, resolver.resolved.value.tier)
        val saved = store.read()!!
        assertEquals(listOf(PerformanceReason.PROBE_FAILED), saved.reasons)
        assertEquals("", saved.fingerprint, "a failed probe can never stick")
        assertEquals("", saved.cheapFingerprint)
        scope.cancel()
    }

    @Test
    fun `probe failure with a usable cache keeps the cache`() = runBlocking {
        probe.signals = LowEndAndroid
        val resolver = resolver()
        nowMs = 1_000L
        resolver.warm()
        val cached = store.read()!!

        // A broken probe cannot even reach the resolve path once a cache is
        // usable — the cache-first fold short-circuits before any probe.
        probe.signals = null
        val profile = resolver.resolve(VideoPerformanceProfile.AUTO)
        assertEquals(DevicePerformanceTier.LOW, profile.tier, "a failed read gives zero information")
        assertEquals(cached, profile.detection)
        assertEquals(1, probe.probeCount, "the failing probe never ran again")
        scope.cancel()
    }

    // ── warm: the launch-time probe owner ──────────────────────────────────

    @Test
    fun `warm detects, repeats as a pure read, survives a broken probe and self-heals`() = runBlocking {
        probe.signals = HighEndAndroid
        val resolver = resolver()
        resolver.warm()
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)
        assertEquals(1, probe.probeCount)

        // A usable cache makes a repeat warm a pure read — no second probe;
        // startup detection must never touch the codec-adjacent probe again.
        resolver.warm()
        assertEquals(1, probe.probeCount)
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)

        // A broken probe must not take startup down: warm swallows, the
        // seed-resolution recovery lands on the MEDIUM + PROBE_FAILED middle.
        probe.signals = null
        store.clear()
        resolver.warm()
        assertEquals(DevicePerformanceTier.MEDIUM, resolver.resolved.value.tier)
        assertEquals(listOf(PerformanceReason.PROBE_FAILED), store.read()?.reasons)

        // The failed-probe sentinel carries empty fingerprints, so it never
        // short-circuits: the next warm re-probes and recovers the real tier
        // instead of latching MEDIUM forever.
        probe.signals = HighEndAndroid
        resolver.warm()
        assertEquals(DevicePerformanceTier.HIGH, resolver.resolved.value.tier)
        assertEquals(DevicePerformanceClassifier.fingerprint(HighEndAndroid), store.read()?.fingerprint)
        assertEquals(
            DevicePerformanceClassifier.fingerprintCheap(HighEndAndroid),
            store.read()?.cheapFingerprint,
        )
        scope.cancel()
    }
}
