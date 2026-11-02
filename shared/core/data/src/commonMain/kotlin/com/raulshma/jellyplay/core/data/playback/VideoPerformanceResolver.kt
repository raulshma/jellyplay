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
 * once, stores the tier against a stable fingerprint in [DevicePerformanceStore],
 * and reuses it until the fingerprint changes (device/SoC/RAM-bucket/decoder
 * change), the detector version changes, or the user taps Re-detect.
 *
 * A manual LOW/MEDIUM/HIGH selection bypasses detection **for the effective
 * tier** (the matrix follows the pinned profile) while the cached detection
 * stays available for the settings subtitle and diagnostics.
 *
 * Threading: [resolve] is suspend and the AUTO detect path is mutex-guarded,
 * so the startup [warm] and the first playback request can race safely — the
 * loser of the race sees the winner's fresh cache. The caller owns any
 * bounded wait (PlayerSessionManager adds a ~1 s budget so a pathological
 * probe cannot wedge playback; on timeout it falls back to MEDIUM for that
 * request).
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
     * Called at app start (the prewarm ladder); runs detection when AUTO has
     * no fresh cache. Must not block the caller: it is suspend, probes off the
     * main thread, and swallows failures — a broken probe must never take
     * startup down (the next resolve retries).
     */
    suspend fun warm() {
        runCatching { resolve() }
            .onFailure { Log.w(TAG, "Performance warm-up failed; keeping seed", it) }
    }

    /**
     * Resolves the effective profile for a playback request. [requested]
     * defaults to the persisted `videoPerformanceProfile` preference.
     *
     *  - Explicit LOW/MEDIUM/HIGH → `requested.toTier()`, tuning straight from
     *    [VideoPerformanceMatrix]; the cached detection (when loaded) rides
     *    along for diagnostics only.
     *  - AUTO → the sticky cached detection when the fingerprint + detector
     *    version match, else probe → classify → save.
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
     */
    suspend fun redetect(): PerformanceDetection = detectMutex.withLock {
        store.clear()
        val fresh = detectAndSaveLocked(signals = probe.probe())
        publishResolved(playbackStore.playback.value.videoPerformanceProfile, fresh)
        fresh
    }

    /**
     * The AUTO path: probe → fingerprint → sticky-cache fold.
     *
     *  - A usable cache (same fingerprint, same [DevicePerformanceClassifier.DETECTOR_VERSION])
     *    wins — the tier comes from the cached detection, so the score/reasons/
     *    timestamp stay the sticky run's, not a re-classification.
     *  - A failed probe cannot invalidate a usable cache (a failed read gives
     *    zero information about the device — the §5.5 "probe failure → MEDIUM"
     *    safe middle applies to the no-cache case, where it is saved with the
     *    `PROBE_FAILED` reason and an empty fingerprint that can never stick).
     */
    private suspend fun autoTierWithDetection(): Pair<DevicePerformanceTier, PerformanceDetection> =
        detectMutex.withLock {
            val cached = store.read()
            val cachedUsable =
                cached != null && cached.detectorVersion == DevicePerformanceClassifier.DETECTOR_VERSION
            val signals = probe.probe()
            val detection: PerformanceDetection = when {
                signals == null && cachedUsable -> cached!!
                signals == null -> detectAndSaveLocked(signals = null)
                else -> {
                    val fingerprint = DevicePerformanceClassifier.fingerprint(signals)
                    if (cachedUsable && cached!!.fingerprint == fingerprint) {
                        cached
                    } else {
                        detectAndSaveLocked(signals = signals)
                    }
                }
            }
            detection.tier to detection
        }

    /**
     * Classify → save. Only called under [detectMutex]. A null [signals]
     * (failed probe) produces the MEDIUM + `PROBE_FAILED` classification with
     * an empty fingerprint — which never matches a real fingerprint, so the
     * next successful probe re-detects instead of sticking to the failure.
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
            detectorVersion = DevicePerformanceClassifier.DETECTOR_VERSION,
            detectedAtEpochMs = timeSource.nowEpochMillis(),
        )
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
