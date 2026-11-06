package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SessionInfo
import kotlinx.coroutines.flow.Flow

/**
 * AdminRepository facade split (the [AdminBackupRepository] precedent — the
 * [AdminRepositorySurfaceTest] ratchet pins the admin union's member count,
 * so a new admin capability lands as a narrow collaborator): the dashboard's
 * live session feed over the shared WebSocket's `Sessions` push. The screen
 * injects this seam instead of [AdminRepository]; the pull-to-refresh and
 * stop-session REST reads stay on the union.
 */
interface AdminSessionFeed {

    /**
     * The full session list as last pushed by the server (app-lifetime hot
     * flow over the shared socket). Nothing emits before the first push — the
     * REST snapshot seeds the list, and the feed reconciles it as pushes
     * land; a dead socket simply keeps the last seeded list (the pre-feed
     * behavior).
     */
    val sessions: Flow<List<SessionInfo>>

    /**
     * Wall time of the last successful Sessions push (0 = none this process).
     * Freshness signal for [sessions] consumers: the shared flow replays its
     * last emission to every new collector, and that replay can be
     * arbitrarily old, so recency must be judged from the push time, not the
     * collection time.
     */
    val sessionsLastPushAtMs: Long
}
