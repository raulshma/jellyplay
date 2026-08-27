package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.model.CacheIdentity
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * The single owner of "the active identity changed" for the app, and the
 * jvmShared [SessionIdentityProvider] implementation (wave 15B: the seam's
 * classifier types moved to commonMain so commonMain subscribers can read
 * them; this classifier stays JVM-bound — see the interface KDoc).
 *
 * Historically THREE independent detectors each maintained their own
 * last-identity mirror by combining `apiClient.currentServer` +
 * `apiClient.currentUser` on their own dispatchers:
 *  1. `MediaRepositoryImpl`'s init observer (cache invalidation + the
 *     previous identity's persisted home-section SWR clear),
 *  2. `EpisodeCatalogueImpl`'s verbatim copy of it (catalogue invalidateAll),
 *  3. `HomeViewModel`'s previousUserId collector (scroll reset + refresh).
 *
 * Besides the triplication, combining the two separate StateFlows observed
 * the synthetic `(newServer, oldUser)` intermediate every two-step publish
 * (login / switchUser) produced — an identity that never existed. This class
 * consumes the engine's ATOMIC [JellyfinApiClient.session] flow instead, so a
 * transition is always one stable identity → the next.
 *
 * Classification rules (identical to the three mirrors it replaces):
 *  - `null → null`           : nothing.
 *  - `null → identity`       : [HomeSessionTransition.SignedIn].
 *  - `identity → null`       : [HomeSessionTransition.SignedOut].
 *  - `identity → identity'`  : same serverId → [HomeSessionTransition.UserSwitched],
 *                              different serverId → [HomeSessionTransition.ServerSwitched].
 * Re-emissions of the same identity (token refresh, address failover) are
 * collapsed by `distinctUntilChanged` and produce no transition.
 *
 * Reads:
 *  - [currentIdentity] is the sanctioned suspend read of the source flow
 *    (`.first()`), promoted from `MediaRepositoryImpl`'s SWR bypass — safe to
 *    call from collectors that may run before this class's own collector has
 *    processed an emission. ALL identity-keyed cache reads/writes should use
 *    it: the mirror below lags a switch by one dispatch, and keying against
 *    a lagging mirror reads/writes the previous identity's entries.
 *  - [currentIdentitySnapshot] is the synchronous mirror read for callers
 *    that cannot suspend (best-effort evictions, where staleness is benign
 *    because identity switches clear the caches wholesale); before login /
 *    after logout it is `null`, which callers map to [com.raulshma.jellyplay.core.model.CacheIdentity.UNKNOWN].
 *
 * Collector scope: the shared `@ApplicationScope` application scope (same
 * lifetime discipline as `ServerIdentityStore`) — this is a `@Singleton`
 * living for the process lifetime, and core:data classes must not hand-roll
 * their own long-lived scopes for singleton collectors. The constructor's
 * scope parameter is also the cross-module TEST seam: a JVM test passes its
 * own scope (e.g. runTest's `backgroundScope`) to drive the classifier on
 * the test scheduler; production code lets Hilt inject the application one.
 */
class HomeSession(
    private val apiClient: JellyfinApiClient,
    collectorScope: CoroutineScope,
) : SessionIdentityProvider {

    /**
     * The last classified identity — `null` before the first sign-in and
     * after sign-out. Updated by the collector below BEFORE the corresponding
     * transition is emitted, so a subscriber handling a transition already
     * observes the new identity through [currentIdentitySnapshot].
     */
    private val lastStableIdentity = AtomicReference<SessionIdentity?>(null)

    private val _transitions = MutableSharedFlow<HomeSessionTransition>(
        // Replay 1: the singleton collector in SessionCacheRegistry (and
        // HomeViewModel's own subscription) must not silently miss a
        // transition emitted before its subscription — the miss payload
        // includes the wholesale invalidation AND the previous identity's
        // SWR privacy clear. Every handler is an idempotent invalidation, so
        // re-delivering the latest transition to a late subscriber is
        // always safe.
        replay = 1,
        extraBufferCapacity = TRANSITIONS_BUFFER_CAPACITY,
    )

    /**
     * Identity changes, in order. Replay 1 — a late subscriber is re-delivered
     * the latest transition (handlers are idempotent invalidations), so the
     * privacy clear can't be missed; the never-transitioned start state
     * (fresh install, signed out) has nothing to replay and needs the
     * one-shot identity reads instead.
     */
    override val transitions: SharedFlow<HomeSessionTransition> = _transitions.asSharedFlow()

    init {
        collectorScope.launch {
            apiClient.session
                .map { session -> session?.let { SessionIdentity(it.server.id, it.user.id) } }
                .distinctUntilChanged()
                .collect { identity ->
                    val previous = lastStableIdentity.getAndSet(identity)
                    val transition = when {
                        previous == null && identity == null -> null
                        previous == null -> HomeSessionTransition.SignedIn
                        identity == null -> HomeSessionTransition.SignedOut(previous)
                        // previous == identity is impossible here: distinctUntilChanged
                        // drops re-emissions before they reach this collector.
                        previous.serverId == identity!!.serverId ->
                            HomeSessionTransition.UserSwitched(previous)
                        else -> HomeSessionTransition.ServerSwitched(previous)
                    }
                    if (transition != null) _transitions.emit(transition)
                }
        }
    }

    /**
     * The current identity read from the SOURCE flow (`.first()`), not the
     * mirror. Suspend + non-blocking, and immune to the observe-ordering race
     * where a caller's collector fires before this class's collector has
     * written the mirror. Returns `null` when no identity is established.
     */
    override suspend fun currentIdentity(): SessionIdentity? =
        apiClient.session.first()?.let { SessionIdentity(it.server.id, it.user.id) }

    /**
     * Synchronous read of the [lastStableIdentity] mirror. Use for cache
     * keying from non-suspend contexts; prefer [currentIdentity] whenever the
     * caller can suspend.
     */
    override fun currentIdentitySnapshot(): SessionIdentity? = lastStableIdentity.get()

    /**
     * [currentIdentity] mapped to the [CacheIdentity] key the identity-keyed
     * caches (`TtlCache`, Room snapshots) use. The source-flow read matters
     * right after a switch: the [lastStableIdentity] mirror is updated
     * asynchronously, and keying a fetch against the lagging mirror would
     * read/write the PREVIOUS identity's cache entries in that window.
     * Returns [CacheIdentity.UNKNOWN] before login / after logout — nothing
     * cached under that key can leak across users, since no real identity
     * ever collides with it.
     */
    override suspend fun cacheIdentity(): CacheIdentity {
        val identity = currentIdentity() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(identity.serverId, identity.userId)
    }

    /**
     * Synchronous [CacheIdentity] variant of [currentIdentitySnapshot] for
     * non-suspend callers (best-effort evictions). Mirror staleness is benign
     * there: identity switches clear the caches wholesale via [transitions]
     * regardless of which identity an entry was keyed under.
     */
    override fun cacheIdentitySnapshot(): CacheIdentity {
        val identity = currentIdentitySnapshot() ?: return CacheIdentity.UNKNOWN
        return CacheIdentity.ofOrNull(identity.serverId, identity.userId)
    }

    private companion object {
        /** Small buffer so a burst of switches never suspends the collector. */
        private const val TRANSITIONS_BUFFER_CAPACITY = 16
    }
}
