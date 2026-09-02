package com.raulshma.jellyplay.core.data.session

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.TtlCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The single home for identity reactions in `core:data`.
 *
 * A [SessionIdentityProvider] (android/desktop: [HomeSession]; wasmJs: the
 * AtomicSessionState-backed provider) classifies the engine's atomic session
 * flow into [HomeSessionTransition]s; this registry is the ONE subscriber that turns
 * those transitions into cache drops. Before it, every identity-aware
 * repository hand-rolled the same `init {}` collector on its own
 * `SupervisorJob()` scope (`MediaRepositoryImpl`, `EpisodeCatalogueImpl`) —
 * each a second collector to keep alive, order, and test. New reactions
 * REGISTER here instead:
 *
 *  - `registerCaches(owner, caches)` for plain [TtlCache]s whose
 *    wholesale drop is the whole reaction. This is only the SECONDARY
 *    guard — identity-keyed caches already miss by construction on a wrong
 *    identity (see [com.raulshma.jellyplay.core.model.CacheIdentity]); the
 *    clear just reclaims the previous identity's entries immediately
 *    instead of waiting out the TTL.
 *  - `registerAction(owner, action)` when the reaction is more than a
 *    clear (e.g. `MediaRepositoryImpl` also clears the previous identity's
 *    persisted home-section SWR rows, `EpisodeCatalogueImpl` bumps its
 *    in-flight epoch). The action receives the transition so it can read
 *    [HomeSessionTransition.previousIdentity].
 *
 * Rules, identical to the collectors this registry replaced:
 *  - [HomeSessionTransition.SignedIn] (restore / first login) does NOT
 *    clear — there is no previous identity to drop.
 *  - [HomeSessionTransition.UserSwitched] / [ServerSwitched] / [SignedOut]
 *    clear every registered cache and run every registered action, both in
 *    registration order.
 *
 * One owner's failure cannot kill the stream: each cache clear and each
 * action is caught and logged per-owner, so a throwing reaction is isolated
 * and the remaining owners still observe the transition. Registration is
 * idempotent per owner — re-registering with the same owner replaces the
 * previous registration (map semantics; singletons register exactly once,
 * but re-registration must not double-fire).
 *
 * Collector scope: the shared `@ApplicationScope` application coroutine
 * scope (same lifetime discipline as `ServerIdentityStore`) — the registry
 * is a `@Singleton` living for the process lifetime, and `core:data`
 * repositories must not hand-roll their own long-lived scopes for this job.
 */
class SessionCacheRegistry(
    sessionIdentity: SessionIdentityProvider,
    private val scope: CoroutineScope,
) {

    // LinkedHashMap, not a concurrent map: reactions run in REGISTRATION
    // order, which hash-bucket iteration would not give. Access is
    // synchronized on the map ([guardUnderLock]; a pass-through on
    // single-threaded wasmJs); the collector copies under the lock and runs
    // outside it because actions suspend.
    private val registeredCaches = LinkedHashMap<String, List<TtlCache<*>>>()
    private val registeredActions = LinkedHashMap<String, suspend (HomeSessionTransition) -> Unit>()

    init {
        scope.launch {
            sessionIdentity.transitions.collect { transition ->
                if (transition is HomeSessionTransition.SignedIn) return@collect
                // Caches first, then actions: an action like Media's
                // persisted-SWR clear observes the same post-drop world the
                // plain cache owners do.
                val cachesToClear = guardUnderLock(registeredCaches) { registeredCaches.toList() }
                cachesToClear.forEach { (owner, caches) ->
                    caches.forEach { cache ->
                        runOwnerSafely(owner) { cache.clear() }
                    }
                }
                val actionsToRun = guardUnderLock(registeredActions) { registeredActions.toList() }
                actionsToRun.forEach { (owner, action) ->
                    runOwnerSafely(owner) { action(transition) }
                }
            }
        }
    }

    /**
     * Registers [caches] to be cleared wholesale on every non-SignedIn
     * transition. Replaces any previous registration under [owner].
     */
    fun registerCaches(owner: String, vararg caches: TtlCache<*>) {
        guardUnderLock(registeredCaches) { registeredCaches[owner] = caches.toList() }
    }

    /**
     * Registers [action] to run on every non-SignedIn transition. Replaces
     * any previous registration under [owner] — an owner name is one
     * registration slot, not an accumulator, so a re-registered singleton
     * cannot stack duplicate reactions.
     */
    fun registerAction(owner: String, action: suspend (HomeSessionTransition) -> Unit) {
        guardUnderLock(registeredActions) { registeredActions[owner] = action }
    }

    /**
     * Runs one owner's reaction, isolating its failure: logged and skipped,
     * never propagated into the collector (which would kill the stream for
     * every other owner). Cancellation of the collector scope itself is
     * re-thrown — that is shutdown, not a bad owner.
     */
    private inline fun runOwnerSafely(owner: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Identity reaction for owner=$owner failed; continuing with other owners", t)
        }
    }

    private companion object {
        private const val TAG = "SessionCacheRegistry"
    }
}
