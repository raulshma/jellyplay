package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ONE owner of "is the Jellyfin Playback Reporting plugin available on
 * the active server?" — one [StateFlow] plus one on-demand [refresh], cited
 * by BOTH former owners, `AdminStatisticsRepositoryImpl` (admin statistics
 * pages) and `WatchHistoryRepositoryImpl` (insights heatmap). Previously each
 * repository held its own `MutableStateFlow(UNKNOWN)` and its own
 * `refreshPlaybackReportingStatus()` body making the same
 * `checkPlaybackReportingPlugin()` call with the same
 * `getOrDefault(UNAVAILABLE)` failure fallback — two independently stale
 * answers to one question, where an admin refresh never updated the insights
 * screen's copy and vice versa.
 *
 * Semantics (identical to each former owner's):
 *  - initial value [PlaybackReportingStatus.UNKNOWN] (never checked);
 *  - [refresh] publishes the check result, or
 *    [PlaybackReportingStatus.UNAVAILABLE] when the check FAILS — a failed
 *    check is a definitive "no plugin gating this session", not "unknown";
 *  - consumers gate on `.value == AVAILABLE` exactly as before.
 *
 * Session invalidation: registered with [SessionCacheRegistry] (owner
 * [OWNER]) so every non-`SignedIn` transition (user switch, server switch,
 * sign-out) resets the flow to [PlaybackReportingStatus.UNKNOWN] — the
 * previous identity's answer is about a different server/user and must not
 * gate the next one's reads. This clear is the one deliberate ADDITION over
 * the former owners (neither invalidated on identity change; a switch left
 * the stale answer pinned until the next refresh). `SignedIn` is skipped by
 * the registry wholesale — there is no previous identity to drop, and a
 * fresh install's pre-login UNKNOWN is already the initial value.
 *
 * The per-repository side effects of a refresh STAY at the callers:
 * `AdminStatisticsRepositoryImpl.refreshPlaybackReportingStatus` still runs
 * its 90-day audit-log prune after the status refresh, and
 * `WatchHistoryRepositoryImpl.refreshPlaybackReportingStatus` still drops
 * its played-items memo BEFORE the status refresh (both orderings verbatim).
 * This store owns only the status itself.
 *
 * Scope: a process-lifetime Koin single alongside the repositories it
 * serves. It is state (a flow), not a cache — there is no TTL; freshness is
 * exactly "as of the last refresh".
 */
class PlaybackReportingStatusStore(
    private val apiClient: JellyfinApiClient,
    private val sessionCacheRegistry: SessionCacheRegistry,
) {

    private val _status = MutableStateFlow(PlaybackReportingStatus.UNKNOWN)

    /** The plugin status as of the last [refresh] (or [PlaybackReportingStatus.UNKNOWN] before any). */
    val status: StateFlow<PlaybackReportingStatus> = _status.asStateFlow()

    init {
        // A non-SignedIn identity transition invalidates the answer: the new
        // identity's server may or may not have the plugin, and the stale
        // AVAILABLE/UNAVAILABLE would gate its reads wrongly. See the class
        // KDoc for why this clear is a declared addition over the two former
        // per-repository owners.
        sessionCacheRegistry.registerAction(OWNER) { _status.value = PlaybackReportingStatus.UNKNOWN }
    }

    /**
     * Re-checks the plugin and publishes the result — or
     * [PlaybackReportingStatus.UNAVAILABLE] on a failed check. Suspends until
     * the check settles, so a caller's post-refresh reads (the repositories'
     * own side effects) observe the new value.
     */
    suspend fun refresh() {
        _status.value = apiClient.checkPlaybackReportingPlugin()
            .getOrDefault(PlaybackReportingStatus.UNAVAILABLE)
    }

    private companion object {
        /** [SessionCacheRegistry] owner name for this store's invalidation action. */
        const val OWNER = "playback-reporting-status"
    }
}
