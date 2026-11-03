package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.DevicePerformanceStore
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.EpochMillisSource
import com.raulshma.jellyplay.core.model.PerformanceReason
import com.raulshma.jellyplay.core.model.PerformanceDetection
import com.raulshma.jellyplay.core.model.ResolvedPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceMatrix
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import com.raulshma.jellyplay.core.model.VideoPerformanceTuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The AUTO detection / effective-tier resolver behind the playback
 * performance profiles (issue #186 §5.5). Sticky by design: AUTO detects
 * once, stores the tier in [DevicePerformanceStore], and reuses it until the
 * detector version changes or the user taps Re-detect.
 *
 * A manual LOW/MEDIUM/HIGH selection bypasses detection **for the effective
 * tier** (the matrix follows the pinned profile) while the cached detection
 * stays available for the settings subtitle and diagnostics.
 *
 * Threading: [resolve] is suspend. AUTO is cache-first and — since the
 * playback-path fix — probe-free **by construction**: [resolve] contains no
 * call to [DevicePerformanceProbe.probe] at all. A cold or invalidated cache
 * answers the MEDIUM seed after a bounded wait on an in-flight startup
 * detect (the caller-side resolve budgets, PlayerSessionManager's ~1 s,
 * remain as dead-simple safety nets); the probe itself runs only from [warm]
 * (app start) and [redetect] (Settings).
 *
 * Drift: the sticky cache is guarded by two identities — the full detection
 * fingerprint (codec scan included, stable across jitter) and the cheap
 * fingerprint over the playback-path-safe signals only, which [resolve]
 * rechecks per AUTO resolve via [DevicePerformanceProbe.cheapFingerprint]
 * (no `MediaCodecList` — safe to run beside a starting codec). A mismatch —
 * or a detection persisted before the cheap field existed — marks the cache
 * drift-pending in memory; the next launch's [warm] re-runs the full
 * detection there, never on the playback path.
 *
 * `timeSource` is the commonMain [EpochMillisSource] seam (the plan's
 * `TimeSource` is its jvmShared supertype; this file lives in commonMain and
 * the Koin graph binds both to the same single).
 */
class VideoPerformanceResolver(
    private val probe: DevicePerformanceProbe,
    private val store: DevicePerformanceStore,
    private val playbackStore: PlaybackStore,
    private val timeSource: EpochMillisSource,
) {

    /**
     * The sticky detection cache, straight from [DevicePerformanceStore.detection]
     * (seeded null, self-loading).
     */
    val detection: StateFlow<PerformanceDetection?> get() = store.detection

    private val _resolved = MutableStateFlow(
        // The AUTO/MEDIUM seed (§5.5): before the first resolve lands, every
        // consumer sees the safe middle — never a wall of caps, never none.
        ResolvedPerformanceProfile(
            requested = VideoPerformanceProfile.AUTO,
            tier = DevicePerformanceTier.MEDIUM,
            tuning = VideoPerformanceMatrix.forTier(DevicePerformanceTier.MEDIUM),
            detection = null,
        ),
    )

    /** The last resolution — what playback requests and the UI should read. */
    val resolved: StateFlow<ResolvedPerformanceProfile> = _resolved.asStateFlow()

    private val detectMutex = Mutex()

    /** Dedupe key of the last logged resolution (§5.12: log on change only, never per frame). */
    private var lastLoggedKey: String? = null

    /**
     * Set by [resolve]'s per-resolve drift check when the live device's cheap
     * fingerprint no longer matches the sticky cache (or the cache predates
     * the cheap field). In-memory only — the consumption point is the next
     * launch's [warm], which re-runs the full detection while still
     * codec-free.
     */
    private var cheapDriftPending = false

    /**
     * Called at app start (the prewarm ladder); the only non-Settings place
     * the probe is allowed to run, so detection can never sit inside session
     * startup (see [resolve]). Three jobs, in order:
     *
     *  1. AUTO with no usable cache → the full detect — the cold-cache probe
     *     lives HERE, at launch, long before any playback request;
     *  2. otherwise → publish from the cache via [resolve] (cheap, probe-free);
     *  3. a drift-pending cache (a cheap-fingerprint mismatch an earlier
     *     [resolve] observed, or a detection persisted before the cheap field
     *     existed) → the full re-detect now, while launch is still codec-free.
     *
     * With a fresh usable cache this is a DataStore read plus a cheap
     * fingerprint recheck and re-detects nothing. Must not block the caller:
     * it is suspend, probes off the main thread, and swallows failures — a
     * broken probe must never take startup down (the next launch's warm
     * retries).
     */
    suspend fun warm() {
        runCatching {
            val requested = playbackStore.playback.value.videoPerformanceProfile
            val coldDetected = requested == VideoPerformanceProfile.AUTO &&
                store.read()?.isUsable() != true
            if (coldDetected) {
                fullDetectAndPublish(requested)
            } else {
                // The drift check rides warm for explicit profiles too: their
                // tier is pinned, but the cached detection feeds the settings
                // diagnostics and should not go quietly stale. (resolve()'s
                // own check only covers the AUTO branch.)
                store.read()?.takeIf { it.isUsable() }?.let { checkCheapDrift(it) }
                resolve(requested)
            }
            // Drift refresh — skipped right after a cold detect: that run
            // just refreshed the cheap fingerprint (or failed, in which case
            // re-probing within the same launch would only double the churn).
            if (!coldDetected &&
                (cheapDriftPending || store.read()?.cheapFingerprint?.isEmpty() == true)
            ) {
                fullDetectAndPublish(requested)
            }
        }.onFailure { Log.w(TAG, "Performance warm-up failed; keeping seed", it) }
    }

    /**
     * Resolves the effective profile for a playback request. [requested]
     * defaults to the persisted `videoPerformanceProfile` preference.
     *
     *  - Explicit LOW/MEDIUM/HIGH → `requested.toTier()`, tuning straight from
     *    [VideoPerformanceMatrix]; the cached detection (when loaded) rides
     *    along for diagnostics only.
     *  - AUTO → the sticky cached detection when it is usable (detector
     *    version match + non-empty fingerprint); a cold, invalidated, or
     *    failed cache answers the MEDIUM seed. **[resolve] never probes.**
     *
     * ## AUTO never probes on the playback path — by construction
     *
     * Every §5.7 call site (the load-pipeline seed, the §5.7 caps folds, the
     * engine-build resolve) funnels through here — i.e. this method runs
     * inside session startup, seconds before the media codec is created. The
     * Android probe's `MediaCodecList(REGULAR_CODECS)` enumeration measured
     * on an OMX-generation device (POCO F1, LineageOS 22.2) deterministically
     * poisons that codec's output-surface attach: the decoder runs, position
     * advances, and the SurfaceView receives zero buffers — audio-only black
     * video for the whole item (issue #186 device validation). The first fix
     * made AUTO cache-first so the probe stayed behind a usable cache; that
     * still left the cold-cache detect reachable from here. This version
     * removes the probe call from the resolve path entirely: a cold cache
     * answers the seed and the next launch's [warm] retries — the invariant
     * holds no matter how the calls interleave.
     */
    suspend fun resolve(
        requested: VideoPerformanceProfile = playbackStore.playback.value.videoPerformanceProfile,
    ): ResolvedPerformanceProfile {
        val (tier, activeDetection) = when (requested) {
            VideoPerformanceProfile.AUTO -> autoTierWithDetection()
            // §5.5: an explicit profile bypasses detection for the effective
            // tier but keeps the cached run for diagnostics. Authoritative
            // read (not the eagerly-seeded [store.detection] value): an early
            // resolve must not mistake the pre-load seed for "no cache".
            else -> requested.toTier() to store.read()
        }
        val resolvedProfile = ResolvedPerformanceProfile(
            requested = requested,
            tier = tier,
            tuning = VideoPerformanceMatrix.forTier(tier),
            detection = activeDetection,
        )
        _resolved.value = resolvedProfile
        logResolution(resolvedProfile)
        return resolvedProfile
    }

    /**
     * User-initiated Re-detect (§5.5): ignores and clears the cache, runs the
     * probe, saves, updates both StateFlows, and returns the fresh detection
     * for the settings screen's loading state. Also logs — a re-detect is
     * exactly the "change" the §5.12 line exists for. A failed probe lands on
     * the MEDIUM + `PROBE_FAILED` safe middle (the cache was just cleared).
     * Settings-only by contract: the user taps this from diagnostics, never
     * mid-playback.
     */
    suspend fun redetect(): PerformanceDetection {
        store.clear()
        return fullDetectAndPublish(playbackStore.playback.value.videoPerformanceProfile)
    }

    /**
     * The probe-and-save core shared by [warm]'s cold-cache detect and drift
     * refresh, and by [redetect]. The only places [DevicePerformanceProbe.probe]
     * may run — launch and Settings, both guaranteed codec-free.
     */
    private suspend fun fullDetectAndPublish(requested: VideoPerformanceProfile): PerformanceDetection =
        detectMutex.withLock {
            val fresh = detectAndSaveLocked(signals = probe.probe())
            publishResolved(requested, fresh)
            fresh
        }

    /**
     * The AUTO path: sticky-cache fold — and, since the playback-path fix, a
     * fold with no probe call in it.
     *
     *  - A usable cache (same [DevicePerformanceClassifier.DETECTOR_VERSION],
     *    non-empty fingerprint) wins outright. Before answering, the cheap
     *    drift fingerprint is rechecked against the live device ([checkCheapDrift]):
     *    a mismatch — or a pre-cheap-field detection — marks the cache
     *    drift-pending for the next launch's [warm]. The cached tier still
     *    answers this resolve; drift scheduling never delays playback.
     *  - A cold or invalidated cache answers the MEDIUM safe-middle seed —
     *    after a bounded wait on any in-flight startup detect (the
     *    [detectMutex] re-read shares [warm]'s fresh result when one lands
     *    inside the caller's resolve budget). Nothing is probed and nothing
     *    is saved: the next launch's warm retries the cold-cache detect.
     */
    private suspend fun autoTierWithDetection(): Pair<DevicePerformanceTier, PerformanceDetection?> {
        val cached = store.read()
        if (cached != null && cached.isUsable()) {
            checkCheapDrift(cached)
            return cached.tier to cached
        }
        return detectMutex.withLock {
            val raced = store.read()
            if (raced != null && raced.isUsable()) {
                // A concurrent detect (startup warm vs the first request) won
                // the race — reuse it instead of answering the seed.
                checkCheapDrift(raced)
                raced.tier to raced
            } else {
                // Probe-free by construction: the seed answers, nothing is
                // saved, and the next launch's warm() owns the retry.
                DevicePerformanceTier.MEDIUM to null
            }
        }
    }

    /**
     * The per-resolve drift check: [DevicePerformanceClassifier.fingerprintCheap]
     * over a fresh gather of the always-safe signals (no codec scan — the
     * probe's cheap half is explicitly safe beside a starting codec). Marks
     * [cheapDriftPending] for the next launch's [warm]; never changes this
     * resolve's answer. An empty cached cheap fingerprint (detection persisted
     * before the field existed) is drift by definition — the refresh
     * repopulates it.
     */
    private suspend fun checkCheapDrift(cached: PerformanceDetection) {
        if (cached.cheapFingerprint.isEmpty()) {
            cheapDriftPending = true
            return
        }
        val current = probe.cheapFingerprint() ?: return
        if (current != cached.cheapFingerprint) {
            Log.i(TAG, "Device fingerprint drifted; performance re-detect scheduled for next launch")
            cheapDriftPending = true
        }
    }

    /**
     * A detection can answer an AUTO resolve only when it is current (same
     * detector version) AND real (non-empty fingerprint — the failed-probe
     * sentinel's empty fingerprint keeps it permanently un-sticky, §5.5's
     * "a failed probe cannot invalidate a usable cache / never sticks").
     */
    private fun PerformanceDetection.isUsable(): Boolean =
        detectorVersion == DevicePerformanceClassifier.DETECTOR_VERSION &&
            fingerprint.isNotEmpty()

    /**
     * Classify → save. Only called under [detectMutex] (from
     * [fullDetectAndPublish]). A null [signals] (failed probe) produces the
     * MEDIUM + `PROBE_FAILED` classification with empty fingerprints — which
     * never match real ones, so the next successful probe re-detects instead
     * of sticking to the failure. Any completed run refreshes the cheap
     * fingerprint, which also retires a pending drift flag.
     */
    private suspend fun detectAndSaveLocked(
        signals: DevicePerformanceSignals?,
    ): PerformanceDetection {
        val classification = if (signals != null) {
            DevicePerformanceClassifier.classify(signals)
        } else {
            DevicePerformanceClassifier.classifyProbeFailure()
        }
        val detection = PerformanceDetection(
            tier = classification.tier,
            score = classification.score,
            reasons = classification.reasons,
            fingerprint = signals?.let { DevicePerformanceClassifier.fingerprint(it) }.orEmpty(),
            cheapFingerprint = signals?.let { DevicePerformanceClassifier.fingerprintCheap(it) }.orEmpty(),
            detectorVersion = DevicePerformanceClassifier.DETECTOR_VERSION,
            detectedAtEpochMs = timeSource.nowEpochMillis(),
        )
        cheapDriftPending = false
        store.save(detection)
        return detection
    }

    /** Publishes [detection]-based resolution for [requested] into [resolved]. */
    private suspend fun publishResolved(
        requested: VideoPerformanceProfile,
        detection: PerformanceDetection,
    ) {
        val tier = when (requested) {
            VideoPerformanceProfile.AUTO -> detection.tier
            else -> requested.toTier()
        }
        val resolvedProfile = ResolvedPerformanceProfile(
            requested = requested,
            tier = tier,
            tuning = VideoPerformanceMatrix.forTier(tier),
            detection = detection,
        )
        _resolved.value = resolvedProfile
        logResolution(resolvedProfile)
    }

    /**
     * The §5.12 structured line, e.g.
     * `profile=AUTO tier=LOW score=6 reasons=LOW_RAM,ENTRY_SOC,NO_HARDWARE_4K
     * caps=1080p/8000000 profileCapped=true bypassed=false fingerprint=abcd1234`.
     *
     * Emitted only when the resolution changes (requested profile, tier,
     * detection run) — never per frame and never per identical request.
     * `bypassed` is the resolver-layer default: mode/source bypass decisions
     * live with the per-request cap resolver, which owns no logging.
     */
    private fun logResolution(profile: ResolvedPerformanceProfile) {
        val detection = profile.detection
        val key = listOf(
            profile.requested.name,
            profile.tier.name,
            detection?.fingerprint.orEmpty(),
            detection?.detectedAtEpochMs?.toString().orEmpty(),
        ).joinToString("|")
        if (key == lastLoggedKey) return
        lastLoggedKey = key

        val tuning = profile.tuning
        val caps = when {
            tuning.maxVideoHeight != null && tuning.maxVideoBitrate != null ->
                "${tuning.maxVideoHeight}p/${tuning.maxVideoBitrate}"
            tuning.maxVideoHeight != null -> "${tuning.maxVideoHeight}p/none"
            tuning.maxVideoBitrate != null -> "none/${tuning.maxVideoBitrate}"
            else -> "none"
        }
        val reasons = detection?.reasons?.joinToString(",")?.ifEmpty { "-" } ?: "-"
        Log.i(
            TAG,
            "profile=${profile.requested.name} tier=${profile.tier.name} " +
                "score=${detection?.score ?: 0} reasons=$reasons caps=$caps " +
                "profileCapped=${tuning.maxVideoHeight != null || tuning.maxVideoBitrate != null} " +
                "bypassed=false fingerprint=${detection?.fingerprint?.take(FINGERPRINT_LOG_CHARS) ?: "-"}",
        )
    }

    private companion object {
        const val TAG = "JellyPlayPerf"
        const val FINGERPRINT_LOG_CHARS = 8
    }
}

/**
 * The explicit-profile → tier fold (§5.5: "explicit profile →
 * `profile.toTier()`"). [VideoPerformanceProfile.AUTO] never routes here at
 * runtime (the resolver's AUTO branch owns it); the MEDIUM default keeps the
 * extension total and matches the resolver's safe-middle seed.
 */
fun VideoPerformanceProfile.toTier(): DevicePerformanceTier = when (this) {
    VideoPerformanceProfile.LOW -> DevicePerformanceTier.LOW
    VideoPerformanceProfile.MEDIUM -> DevicePerformanceTier.MEDIUM
    VideoPerformanceProfile.HIGH -> DevicePerformanceTier.HIGH
    VideoPerformanceProfile.AUTO -> DevicePerformanceTier.MEDIUM
}
