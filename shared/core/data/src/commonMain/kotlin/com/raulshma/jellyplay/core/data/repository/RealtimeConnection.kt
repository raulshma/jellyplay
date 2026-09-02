package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.ConnectionCredentials
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
     * `false` after a drop or [disconnect]. Drives capabilities re-posting on
     * every reconnect.
     */
    val isConnected: StateFlow<Boolean>

    /** Opens (or re-opens) the realtime socket for the authenticated session. */
    fun connect(credentials: ConnectionCredentials)

    /** Closes the realtime socket. Safe to call when already disconnected. */
    fun disconnect()
}
