package com.raulshma.jellyplay.feature.shell

/**
 * Admin-status refresh dedupe policy — the 30 s window + in-flight guard both
 * shells hand [com.raulshma.jellyplay.feature.admin.navigation.AdminRouteContainer]
 * (Android: `MainViewModel.refreshAdminStatus`; desktop: the inlined
 * DesktopNavScaffold lambda, formerly with its own copy of the 30_000L
 * constant). Platform-free: the gate owns ONLY the timing/guard decision and
 * is constructed over the inputs each shell already has — its own in-flight
 * state owner (read through [isRefreshInFlight]) and a wall-clock lambda
 * ([nowMs]). The state the shells render stays per-shell, as recorded:
 * the gate never owns or mutates it.
 *
 * Call [shouldStart] synchronously before launching the refresh work (the
 * same-frame two-entries-composing guard), then [onRefreshCompleted] — and
 * only on success — after the repository call returns, so a failed refresh
 * does not push the next attempt a full window out.
 */
class AdminRefreshGate(
    private val isRefreshInFlight: () -> Boolean,
    private val nowMs: () -> Long,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    /**
     * Wall-clock millis of the last successful refresh completion, 0 before
     * the first. Read/written only on each shell's main thread (all callers
     * run there), so a plain non-volatile field is safe.
     */
    var lastRefreshAtMs: Long = 0L
        private set

    /**
     * `true` when a refresh may start: nothing in flight and the last
     * successful refresh is a full [intervalMs] ago. The window is inclusive
     * (`now - last >= interval`), so at exactly [intervalMs] elapsed the
     * refresh runs.
     */
    fun shouldStart(): Boolean =
        !isRefreshInFlight() && nowMs() - lastRefreshAtMs >= intervalMs

    /** Records a completed refresh for the dedupe window. */
    fun onRefreshCompleted() {
        lastRefreshAtMs = nowMs()
    }

    companion object {
        /** Admin-status re-validation window: at most one server call per 30 s. */
        const val DEFAULT_INTERVAL_MS = 30_000L
    }
}
