package com.raulshma.jellyplay.core.data.playback

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Caps how many [com.raulshma.jellyplay.core.data.worker.DownloadWorker]s
 * transfer concurrently.
 *
 * WorkManager enqueues one unique worker per download and may run several in
 * parallel; without a cap the device would fan out every queued download at
 * once. This shared [Semaphore] — sized from `maxConcurrentDownloads` — gates the
 * actual transfer so excess workers block on [withPermit] until a slot frees.
 *
 * The permit count is updated via [configure] whenever the user changes the
 * setting. Because coroutines waiting on a permit are not preempted, shrinking
 * the limit takes effect as in-flight downloads complete.
 *
 * Distinct from the per-file parallel-stream count
 * (`downloadConnections`).
 */
class DownloadConcurrencyLimiter() {

    @Volatile private var max: Int = DEFAULT_MAX
    @Volatile private var permits: Semaphore = Semaphore(permits = DEFAULT_MAX)

    /** Resizes the permit count. Safe to call repeatedly; a no-op when unchanged. */
    fun configure(maxConcurrent: Int) {
        val normalized = maxConcurrent.coerceIn(1, 6)
        // A Semaphore's permit count can't be mutated in place, so we swap the
        // instance when the desired count changes. Workers already waiting on the
        // previous instance still honor its count; new acquisitions use the new one.
        if (normalized != max) {
            max = normalized
            permits = Semaphore(permits = normalized)
        }
    }

    /** Acquires a download slot, runs [block], then releases the slot. */
    suspend fun <T> withPermit(block: suspend () -> T): T = permits.withPermit { block() }

    companion object {
        const val DEFAULT_MAX = 3
    }
}
