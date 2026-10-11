package com.raulshma.jellyplay.feature.player.video

/**
 * A persisted position older than this is treated as stale on a process-death
 * restore and ignored: the user backgrounded the app long enough that
 * auto-resuming mid-stream (potentially on an episode they auto-advanced past)
 * is worse than landing on Home and continuing via the "Continue Watching" row.
 * Matches the ~1h threshold users report as the trigger; well above any real
 * short backgrounding (notification reply, brief app switch).
 */
internal const val STALE_POSITION_THRESHOLD_MS = 60L * 60L * 1000L

/**
 * Pure resume-position resolver used by
 * [PlaybackSession.resolveStartTicksAfterProcessDeath] so the staleness +
 * "only advance forward" rules are unit-testable without a session.
 *
 * Rules:
 * - No persisted position (`savedPosMs <= 0`): keep the entry point.
 * - Persisted position too old (`persistedAtMs > 0` and older than
 *   [STALE_POSITION_THRESHOLD_MS]): keep the entry point. A zero/missing
 *   timestamp is treated as fresh so a normal resume-from-background keeps
 *   working.
 * - Otherwise resume at the persisted position, but never below the deliberate
 *   entry point (auto-advance may have moved the user forward of the route's
 *   original ticks; rewinding would jump back unexpectedly).
 */
internal fun resolveResumeTicks(
    savedPosMs: Long,
    persistedAtMs: Long,
    nowMs: Long,
    entryPointTicks: Long,
    staleThresholdMs: Long = STALE_POSITION_THRESHOLD_MS,
): Long {
    if (savedPosMs <= 0L) return entryPointTicks
    if (persistedAtMs > 0L && nowMs - persistedAtMs > staleThresholdMs) return entryPointTicks
    val savedTicks = savedPosMs * 10_000
    return if (savedTicks > entryPointTicks) savedTicks else entryPointTicks
}
