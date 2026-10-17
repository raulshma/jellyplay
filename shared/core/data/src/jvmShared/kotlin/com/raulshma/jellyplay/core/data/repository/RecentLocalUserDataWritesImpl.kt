package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.monotonicNowMillis

/**
 * The JVM-backed [RecentLocalUserDataWrites]: a synchronized id → last-write
 * timestamp map, pruned lazily on every access. One Koin single, shared by
 * the recorder ([MediaRepositoryImpl]'s local-notify path) and the consults
 * (the home refresher's user-data chain) so an echo is matched against the
 * writes that caused it.
 *
 * The clock is the default-injected monotonic read ([monotonicNowMillis] —
 * the [TtlCache] idiom): recordings and queries are compared within one
 * process only, so a wall-clock jump (NTP correction, manual set) must not
 * mass-expire or extend the window. Injectable for tests.
 */
class RecentLocalUserDataWritesImpl(
    private val clock: () -> Long = ::monotonicNowMillis,
) : RecentLocalUserDataWrites {

    private val writtenAt = HashMap<String, Long>()

    override fun record(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        val now = clock()
        synchronized(this) {
            prune(now)
            for (itemId in itemIds) {
                writtenAt[itemId] = now
            }
        }
    }

    override fun wereAllRecentlyWritten(itemIds: List<String>): Boolean {
        if (itemIds.isEmpty()) return false
        val now = clock()
        synchronized(this) {
            prune(now)
            return itemIds.all { itemId ->
                writtenAt[itemId]?.let { now - it < ECHO_WINDOW_MS } == true
            }
        }
    }

    /** Drops entries at-or-past the window; runs under the instance monitor. */
    private fun prune(now: Long) {
        writtenAt.entries.removeIf { now - it.value >= ECHO_WINDOW_MS }
    }

    companion object {
        /**
         * How long a locally written item id keeps demoting echoes of
         * itself. Playback saves recur every ~10s during playback, and the
         * refresher's user-data chain (1s debounce + 60s trailing-edge
         * throttle) can spread one burst's echoes over a minute — so the
         * window must comfortably outlive a single burst plus its deferred
         * flush. Three minutes does that with margin while staying small
         * enough that an external device's write to the same item (the
         * false-positive case: its echo arrives item-id-identical) is
         * forced again soon after; the worst a stale demotion buys is a
         * TTL-semantics refresh serving content one periodic tick old.
         */
        const val ECHO_WINDOW_MS = 3 * 60_000L
    }
}
