package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The atomic-session flow trio of `JellyfinApiEngine` (jvmShared): `currentServer` / `currentUser` as
 * separate StateFlows plus the combined [session] published as ONE atomic
 * value, so a session transition is observed as a single step (stable pair →
 * stable pair, or →/from null) — never the synthetic `(newServer, oldUser)`
 * intermediate that `combine(currentServer, currentUser)` produces across a
 * two-step publish.
 *
 * Public since chunk 2: ONE instance is shared by the auth, library
 * and playback clients (the auth client publishes, the others derive
 * per-request base URL + token), so the DI module constructs it and hands it
 * to all three — internal would leak through their constructors.
 *
 * Callers must guard writes with their own mutex critical sections (the
 * engine uses `authMutex`; the auth client does too) — this class only
 * owns the publish collapsing, mirroring `JellyfinApiEngine.publishSession`:
 * a missing side collapses the session to null.
 *
 * Implements [AuthSessionStateStore] so the shared auth spine
 * ([AuthSessionCore]) can drive this holder directly.
 */
class AtomicSessionState : AuthSessionStateStore {

    private val _currentServer = MutableStateFlow<ServerInfo?>(null)
    val currentServer: StateFlow<ServerInfo?> = _currentServer.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    private val _session = MutableStateFlow<ActiveSession?>(null)

    /** The combined session; `null` means no fully established identity. */
    val session: StateFlow<ActiveSession?> = _session.asStateFlow()

    /** [AuthSessionStateStore] read for the shared auth spine ([AuthSessionCore]). */
    override fun currentSession(): ActiveSession? = session.value

    /** Single-side server update, pairing with the current user. */
    override fun updateServer(server: ServerInfo?) {
        _currentServer.value = server
        publishSession(server, _currentUser.value)
    }

    /** Single-side user update, pairing with the current server. */
    fun updateUser(user: UserInfo?) {
        _currentUser.value = user
        publishSession(_currentServer.value, user)
    }

    /**
     * Atomically adopts BOTH sides in one step — the only shape login /
     * switchUser / disconnect may publish from their critical sections.
     */
    override fun updateSession(server: ServerInfo?, user: UserInfo?) {
        _currentServer.value = server
        _currentUser.value = user
        publishSession(server, user)
    }

    /** Publishes the combined session; a missing side collapses it to null. */
    private fun publishSession(server: ServerInfo?, user: UserInfo?) {
        _session.value = if (server != null && user != null) ActiveSession(server, user) else null
    }
}
