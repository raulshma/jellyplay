package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Watches the audio queue + playhead position and proactively warms
 * upcoming-track bytes into [AudioStreamCache] via [CacheWriter].
 *
 * Triggers: when the current track's playhead crosses ~50% of its duration
 * (tracks with unknown/indefinite duration are skipped — no threshold
 * reachable), and on every queue-cursor move (track change). Both edges are
 * derived from the manager's live position/index flows (see
 * [bindTriggerFlows]); the cache/policy watcher additionally re-evaluates on
 * cache-pref or network-policy changes. Bounded by
 * `AudioCacheSlice.audioPrefetchLookahead` and gated by
 * [AudioCachePolicyGuard]. Cancels stale warming jobs on queue mutation.
 *
 * Back-fill is implicit: recently-played tracks remain in the LRU cache and
 * are evicted by [LeastRecentlyUsedCacheEvictor] under pressure.
 */
class AudioPrefetchEngine(
    private val audioStreamCache: AudioStreamCache,
    private val policyGuard: AudioCachePolicyGuard,
    private val playbackRepository: PlaybackRepository,
    private val audioCacheStore: com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore,
    private val backgroundScope: CoroutineScope,
) {
    // Late-bound by AudioPlaybackManager.start()
    private var queueProvider: (() -> List<AudioQueueItem>)? = null
    private var currentIndexProvider: (() -> Int)? = null
    private var positionProvider: (() -> Long)? = null
    private var durationProvider: (() -> Long)? = null

    // Late-bound by AudioPlaybackManager.start() — the reactive twins of the
    // pull providers above, consumed by the trigger watcher below (read as
    // flows, never polled).
    private var positionFlow: StateFlow<Long>? = null
    private var durationFlow: StateFlow<Long>? = null
    private var currentIndexFlow: StateFlow<Int>? = null

    fun bindProviders(
        queueProvider: () -> List<AudioQueueItem>,
        currentIndexProvider: () -> Int,
        positionProvider: () -> Long,
        durationProvider: () -> Long,
    ) {
        this.queueProvider = queueProvider
        this.currentIndexProvider = currentIndexProvider
        this.positionProvider = positionProvider
        this.durationProvider = durationProvider
    }

    fun bindTriggerFlows(
        positionFlow: StateFlow<Long>,
        durationFlow: StateFlow<Long>,
        currentIndexFlow: StateFlow<Int>,
    ) {
        this.positionFlow = positionFlow
        this.durationFlow = durationFlow
        this.currentIndexFlow = currentIndexFlow
    }

    private val _warmingState = MutableStateFlow<Map<String, Float>>(emptyMap())
    val warmingState: StateFlow<Map<String, Float>> = _warmingState.asStateFlow()

    private var watchJob: Job? = null
    private var triggerJob: Job? = null
    private val activeJobs = mutableMapOf<String, Job>()
    private var avgTrackBytes = 8_000_000L // 8 MB conservative seed

    fun start() {
        if (watchJob?.isActive == true) return
        watchJob = backgroundScope.launch {
            combine(
                audioCacheStore.audioCache,
                policyGuard.isPrefetchAllowed,
            ) { prefs, allowed -> prefs to allowed }
                .distinctUntilChanged()
                .collectLatest { (prefs, allowed) ->
                    if (!prefs.audioCachingEnabled || !allowed || prefs.audioPrefetchLookahead <= 0) {
                        cancelAllJobs()
                        return@collectLatest
                    }
                    evaluateAndWarm(prefs.audioPrefetchLookahead, prefs.audioPrefetchBackfill)
                }
        }
        startTriggerWatcher()
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        triggerJob?.cancel()
        triggerJob = null
        cancelAllJobs()
    }

    /** One warming re-evaluation request (see [startTriggerWatcher]). */
    private sealed interface PrefetchTrigger {
        /** The playhead entered the past-[PREFETCH_THRESHOLD] region of the current track. */
        data object ThresholdCrossed : PrefetchTrigger

        /** The queue cursor moved to another row (skip / natural transition / restore). */
        data object TrackChanged : PrefetchTrigger
    }

    /**
     * The re-trigger watcher. The cache/policy watcher in [start] only
     * re-evaluates when cache prefs or the network policy change — neither
     * reflects the playhead or the cursor, so mid-queue warming would never
     * fire after the initial evaluation. This watcher re-runs
     * [evaluateAndWarm] on two edges derived from the manager's live flows —
     * no polling: the body is a few comparisons, and the position flow only
     * publishes while playback progresses, so an idle or paused session
     * emits nothing:
     *  - [PrefetchTrigger.ThresholdCrossed] — a distinctUntilChanged gate on
     *    the Boolean "at/after [PREFETCH_THRESHOLD]", so it fires once per
     *    entry into the beyond-region (once per track: the reset to ~0 on
     *    every track change re-arms it).
     *  - [PrefetchTrigger.TrackChanged] — every queue-cursor move, covering
     *    skips into tracks that resume already past the threshold.
     * All existing guards stay: the gate below re-checks the same cache
     * prefs + policy the watcher above uses, and [evaluateAndWarm] keeps its
     * own position/duration/queue-bounds/per-item-cached checks.
     */
    private fun startTriggerWatcher() {
        if (triggerJob?.isActive == true) return
        val posFlow = positionFlow ?: return
        val durFlow = durationFlow ?: return
        val idxFlow = currentIndexFlow ?: return
        triggerJob = backgroundScope.launch {
            merge(
                combine(posFlow, durFlow) { pos, dur ->
                    dur > 0L && pos >= dur * PREFETCH_THRESHOLD
                }
                    // drop(1): the StateFlow replays its current Boolean on
                    // subscribe — a session opening already past the
                    // threshold would fire a duplicate of the cache/policy
                    // watcher's initial evaluation (every track re-checked
                    // twice at start). Only a real false→true crossing
                    // triggers; the opening-past case is covered by that
                    // watcher's own initial evaluation.
                    .drop(1)
                    .distinctUntilChanged()
                    .filter { it }
                    .map { PrefetchTrigger.ThresholdCrossed },
                // drop(1): the StateFlow replays its current index on
                // subscribe — not a change. Only real cursor moves trigger.
                idxFlow.drop(1).map { PrefetchTrigger.TrackChanged },
            )
                .conflate()
                .collect {
                    val prefs = audioCacheStore.audioCache.value
                    if (!prefs.audioCachingEnabled ||
                        !policyGuard.isPrefetchAllowed.value ||
                        prefs.audioPrefetchLookahead <= 0
                    ) {
                        return@collect
                    }
                    evaluateAndWarm(prefs.audioPrefetchLookahead, prefs.audioPrefetchBackfill)
                }
        }
    }

    private suspend fun evaluateAndWarm(lookahead: Int, backfill: Int) {
        val qp = queueProvider ?: return
        val cip = currentIndexProvider ?: return
        val pp = positionProvider ?: return
        val dp = durationProvider ?: return
        val pos = pp()
        val dur = dp()
        // Unknown/indefinite duration → cannot compute threshold → skip.
        if (dur <= 0L) return
        if (pos < dur * PREFETCH_THRESHOLD) return

        val queue = qp()
        val idx = cip()
        if (idx < 0 || idx >= queue.size) return

        val upcomingEnd = (idx + 1 + lookahead).coerceAtMost(queue.size)
        val upcoming = queue.subList(idx + 1, upcomingEnd)

        // Back-fill pressure check: reserve bytes for recently-played tracks.
        val reservedBytes = backfill.toLong() * avgTrackBytes
        val prefs = audioCacheStore.audioCache.value
        val maxBytes = prefs.audioCacheSizeMb.toLong() * 1024L * 1024L
        val currentSpace = audioStreamCache.cacheSpaceBytes()
        val throttleCount = if (currentSpace + (upcoming.size * avgTrackBytes) + reservedBytes > maxBytes) {
            max(1, ((maxBytes - currentSpace - reservedBytes) / avgTrackBytes).toInt().coerceAtLeast(0))
        } else {
            upcoming.size
        }

        upcoming.take(throttleCount).forEach { item ->
            if (activeJobs.containsKey(item.id)) return@forEach
            val url = resolveUrl(item)
            if (url.isEmpty()) return@forEach
            // Skip if already cached
            val cached = audioStreamCache.getCachedBytes(url)
            if (cached > 0L && cached >= avgTrackBytes) return@forEach

            val job = backgroundScope.launch {
                _warmingState.value = _warmingState.value + (item.id to 0f)
                val result = audioStreamCache.warmTrack(url)
                result.onSuccess { bytes ->
                    if (bytes > 0L) {
                        // Update running average (exponential moving avg)
                        avgTrackBytes = ((avgTrackBytes * 0.7) + (bytes * 0.3)).toLong()
                    }
                    // Record cellular usage for the cap
                    if (policyGuard.isPrefetchAllowed.value) {
                        policyGuard.recordCellularPrefetch(bytes)
                    }
                }
                _warmingState.value = _warmingState.value - item.id
                activeJobs.remove(item.id)
            }
            activeJobs[item.id] = job
        }
    }

    private fun resolveUrl(item: AudioQueueItem): String {
        // Use the same /Videos/stream endpoint the player uses (static=true
        // returns the raw file, which ExoPlayer can extract and CacheDataSource
        // can side-cache). The /Audio/universal endpoint returns a container
        // format ExoPlayer's progressive extractors cannot sniff.
        return playbackRepository.getStreamUrl(
            itemId = item.id,
            mediaSourceId = item.mediaSourceId ?: "",
            startTimeTicks = 0L,
            maxBitrate = null,
            useAudioEndpoint = false,
        )
    }

    private fun cancelAllJobs() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _warmingState.value = emptyMap()
    }

    companion object {
        private const val PREFETCH_THRESHOLD = 0.5 // 50% of duration
    }
}
