package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ConnectionCredentials
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The realtime-socket seam of the auth repository: opens and closes the
 * authenticated session's WebSocket and exposes its live connection state.
 * Split out of [AuthRepository] so the session shell depends on the
 * transport surface alone — [AuthRepositoryImpl] backs both interfaces as
 * one singleton, not a second socket.
 */
interface RealtimeConnection {

    /**
     * Address of the realtime transport's currently active endpoint (primary
     * or the selected alternate after failover), or `null` when no server is
     * connected.
     */
    fun serverUrl(): String?

    /**
     * Live socket connection state: `true` once the handshake completes,
     * `false` after a drop or [disconnect]. Drives the session shell's
     * first-connect arming.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Emits on every realtime-socket open that follows a previous open — a
     * true reconnect (see
     * [JellyfinWebSocketClient.reconnects][com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient.reconnects]
     * for the full vocabulary and first-connect semantics). The first connect
     * of a session does NOT emit: consumers that must also arm on the first
     * connect keep their own initial arm off [isConnected] (see
     * [SessionCoordinator][com.raulshma.jellyplay.shell.SessionCoordinator],
     * which posts capabilities once the server session truly exists).
     */
    val reconnects: SharedFlow<Unit>

    /** Opens (or re-opens) the realtime socket for the authenticated session. */
    fun connect(credentials: ConnectionCredentials)

    /** Closes the realtime socket. Safe to call when already disconnected. */
    fun disconnect()
}
