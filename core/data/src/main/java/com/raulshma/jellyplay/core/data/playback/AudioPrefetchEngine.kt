package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Watches the audio queue + playhead position and proactively warms
 * upcoming-track bytes into [AudioStreamCache] via [CacheWriter].
 *
 * Trigger: when the current track's playhead crosses ~50% of its duration
 * (tracks with unknown/indefinite duration are skipped — no threshold
 * reachable). Bounded by [UserPreferences.audioPrefetchLookahead] and gated
 * by [AudioCachePolicyGuard]. Cancels stale warming jobs on queue mutation.
 *
 * Back-fill is implicit: recently-played tracks remain in the LRU cache and
 * are evicted by [LeastRecentlyUsedCacheEvictor] under pressure.
 */
class AudioPrefetchEngine(
    private val audioStreamCache: AudioStreamCache,
    private val policyGuard: AudioCachePolicyGuard,
    private val playbackRepository: PlaybackRepository,
    private val audioCacheStore: com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore,
    @ApplicationScope private val backgroundScope: CoroutineScope,
) {
    // Late-bound by AudioPlaybackManager.start()
    private var queueProvider: (() -> List<AudioQueueItem>)? = null
    private var currentIndexProvider: (() -> Int)? = null
    private var positionProvider: (() -> Long)? = null
    private var durationProvider: (() -> Long)? = null

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

    private val _warmingState = MutableStateFlow<Map<String, Float>>(emptyMap())
    val warmingState: StateFlow<Map<String, Float>> = _warmingState.asStateFlow()

    private var watchJob: Job? = null
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
    }

    fun stop() {
        watchJob?.cancel()
        watchJob = null
        cancelAllJobs()
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
