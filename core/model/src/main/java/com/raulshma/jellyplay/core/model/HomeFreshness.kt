package com.raulshma.jellyplay.core.model

/**
 * The home screen's entire freshness policy — "how stale can home be?" —
 * behind one seam: the TTLs of the cache layers home flows through
 * (`core:network` sub-call caches, the `core:data` in-memory cache, the
 * `core:database` stale-while-revalidate snapshot) plus the feature-layer
 * refresh cadence that drives refills.
 *
 * Previously these were four uncoordinated clocks: a 2-minute constant in
 * `LibraryApiClientImpl`, a 60-second one in `MediaRepositoryImpl`, a
 * `fetchedAt` column written but never read, and cadence constants on
 * `HomeRefresher`. They lived in modules that cannot see each other
 * (`core:network`, `core:data`, `feature:home`), so every cross-layer
 * "keep these in lockstep" rule was a comment instead of code. All three
 * depend on `core:model`, so — same common-ancestor reasoning as
 * [TtlCache] — the canonical policy lives here and the duplicates were
 * removed.
 *
 * Passive-`now` API: like feature/home's `TtlCacheGate`, [isRoomSnapshotFresh]
 * takes the current time as a parameter instead of reading a clock. This
 * object owns policy, not time; each caller keeps whatever clock it already
 * owns (Hilt-injected `TimeSource` in the repo, test fakes elsewhere).
 */
object HomeFreshness {

    // ── Cache-layer TTLs (how old may served data be?) ──────────────────────

    /** `core:network` home hot-path sub-call caches (latest media, similar items). */
    const val NETWORK_SUBCALL_TTL_MS = 2 * 60_000L

    /** `MediaRepositoryImpl`'s in-memory home-sections cache. */
    const val REPO_MEMORY_TTL_MS = 60_000L

    /** Staleness ceiling for Room `home_section_cache` SWR snapshots (see [isRoomSnapshotFresh]). */
    const val ROOM_SWR_STALE_MS = 24 * 60 * 60_000L

    // ── Refresh cadence (how often is home re-fetched?) ─────────────────────

    /** Seerr discover-sections TTL gate (trending/popular change slowly). */
    const val DISCOVER_TTL_MS = 10 * 60_000L

    /**
     * Minimum spacing for `UserDataChanged`-driven refreshes. The server echoes
     * these to every session — including this device's own ~10s playback-position
     * saves — so the 30s [MIN_REFRESH_INTERVAL_MS] would force a cache-bypassing
     * refetch behind the player for a whole playback session; matching the
     * foreground cadence bounds that to one refresh per minute.
     */
    const val USER_DATA_REFRESH_MIN_INTERVAL_MS = 60_000L

    /** Foreground periodic-refresh interval. */
    const val REFRESH_INTERVAL_FOREGROUND_MS = 60_000L

    /**
     * Background periodic-refresh interval: the Home VM survives in the back
     * stack, so a short interval kept fanning out Seerr requests while the user
     * was on another screen; the resume-if-stale check re-syncs on return.
     */
    const val REFRESH_INTERVAL_BACKGROUND_MS = 15 * 60_000L

    /** Minimum spacing between any two periodic-loop fetches (jitter guard). */
    const val MIN_REFRESH_INTERVAL_MS = 30_000L

    /** Debounce applied when coalescing bursts of `UserDataChanged` pushes into one refresh. */
    const val USER_DATA_CHANGE_REFRESH_DEBOUNCE_MS = 1_000L

    /**
     * Whether a Room stale-while-revalidate snapshot is still worth painting
     * on a cold open. Deliberately wall-clock (epoch millis on both
     * parameters): `fetchedAt` must survive a reboot to serve the next cold
     * open, and monotonic clocks reset on boot. The two memory TTLs above are
     * the opposite split — they run on `SystemClock.elapsedRealtime` (via
     * [TtlCache]'s clock lambda) because they only ever compare two readings
     * within one process.
     *
     * Fresh iff `now - fetchedAt < [ROOM_SWR_STALE_MS]`; a snapshot exactly at
     * the ceiling is stale. A stale snapshot must not instant-paint — return
     * null to it so the cold open shows a spinner instead of ancient content,
     * then the normal refresh proceeds.
     */
    fun isRoomSnapshotFresh(fetchedAtWallMs: Long, nowWallMs: Long): Boolean =
        nowWallMs - fetchedAtWallMs < ROOM_SWR_STALE_MS
}
