package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.network.auth.AtomicSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * wasmJs [SessionIdentityProvider]: the same atomic-session classifier
 * [HomeSession] runs on the JVM, sourced from [AtomicSessionState] (the
 * `networkWasmModule` single the three wasm API clients share) instead of
 * the OkHttp `JellyfinApiClient`. Classification rules, replay-1 transition
 * buffer, mirror semantics and UNKNOWN handling are identical to
 * [HomeSession] — only the session source and the mirror storage differ:
 * the JVM `AtomicReference` is a plain field here (Kotlin/wasm is
 * single-threaded; the classifier itself always runs in [collectorScope]).
 *
 * Server switch detection keys off [SessionIdentity.serverId] — on web the
 * auth client's `toServerInfo` maps the `System/Info/Public` payload's real
 * server GUID into `ServerInfo.id` (random-UUID fallback only when the probe
 * omits it), so the classification compares the same (server GUID, user id)
 * pair the JVM classifier performs.
 *
 * Collector scope: the shared application scope single
 * (`DatastoreQualifiers.applicationScope`, same lifetime discipline as the
 * JVM wiring) — this is a process-lifetime singleton and must not hand-roll
 * its own long-lived scope.
 */
class WasmSessionIdentityProvider(
    private val sessionState: AtomicSessionState,
    collectorScope: CoroutineScope,
) : SessionIdentityProvider {

    private var lastStableIdentity: SessionIdentity? = null

    private val _transitions = MutableSharedFlow<HomeSessionTransition>(
        // Replay 1: identical rationale to HomeSession — a late subscriber is
        // re-delivered the latest transition (all handlers are idempotent
        // invalidations), so a wholesale cache clear can't be missed.
        replay = 1,
        extraBufferCapacity = TRANSITIONS_BUFFER_CAPACITY,
    )

    override val transitions: SharedFlow<HomeSessionTransition> = _transitions.asSharedFlow()

    init {
        collectorScope.launch {
            sessionState.session
                .map { session -> session?.let { SessionIdentity(it.server.id, it.user.id) } }
                .distinctUntilChanged()
                .collect { identity ->
                    val previous = lastStableIdentity
                    lastStableIdentity = identity
                    val transition = when {
                        previous == null && identity == null -> null
                        previous == null -> HomeSessionTransition.SignedIn
                        identity == null -> HomeSessionTransition.SignedOut(previous)
                        // previous == identity is impossible here:
                        // distinctUntilChanged drops re-emissions upstream.
                        previous.serverId == identity.serverId ->
                            HomeSessionTransition.UserSwitched(previous)
                        else -> HomeSessionTransition.ServerSwitched(previous)
                    }
                    if (transition != null) _transitions.emit(transition)
                }
        }
    }

    override suspend fun currentIdentity(): SessionIdentity? =
        sessionState.session.first()?.let { SessionIdentity(it.server.id, it.user.id) }

    override fun currentIdentitySnapshot(): SessionIdentity? = lastStableIdentity

    override suspend fun cacheIdentity(): CacheIdentity {
        val identity = currentIdentity() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(identity.serverId, identity.userId)
    }

    override fun cacheIdentitySnapshot(): CacheIdentity {
        val identity = currentIdentitySnapshot() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(identity.serverId, identity.userId)
    }

    private companion object {
        /** Small buffer so a burst of switches never suspends the collector. */
        private const val TRANSITIONS_BUFFER_CAPACITY = 16
    }
}
