package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The atomic-session flow trio behind the engine's session state
 * (`JellyfinApiEngine` on jvmShared carries the platform twin): `currentServer` /
 * `currentUser` as separate StateFlows plus the combined [session] published
 * as ONE atomic value, so a session transition is observed as a single step
 * (stable pair → stable pair, or →/from null) — never the synthetic
 * `(newServer, oldUser)` intermediate that `combine(currentServer,
 * currentUser)` produces across a two-step publish.
 *
 * The public surface is READ-ONLY. The former "callers must guard writes
 * with their own mutex critical sections" caveat is retired: there are no
 * public writes left to guard. The single write — [publishSessionState] —
 * is the internal primitive the shared auth spine's mutex-held critical
 * sections compose (via [AuthSessionStateStore]); the production holder is
 * the engine, whose public multi-write (`updateSession`) wraps its own
 * primitive with `authMutex`.
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

    /**
     * The raw session publish primitive — both sides + the collapsed session
     * in one step. Caller holds the spine's mutex (see
     * [AuthSessionStateStore]); module-internal so the spine and this
     * module's tests can drive it.
     */
    override fun publishSessionState(server: ServerInfo?, user: UserInfo?) {
        _currentServer.value = server
        _currentUser.value = user
        _session.value = if (server != null && user != null) ActiveSession(server, user) else null
    }
}
