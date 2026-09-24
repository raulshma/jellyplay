package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The session-restore choreography both shells need (Android
 * `SessionCoordinator` / desktop `DesktopAppRoot`): run the restore call, and
 * only settle the splash gate once a successful restore has VISIBLY landed —
 * the authenticated mirror ([isAuthenticated], the flag the shell renders
 * from) included.
 *
 * Lives in shared/feature/shell beside [ShellSessionController] as part of
 * the session-policy wiring (ADR 0001's construction pattern): pure inputs
 * only — flows, suspend lambdas, a scope — no Koin binding, no repository
 * types — so the module keeps its repository-free signature set. Each shell
 * constructs it directly over its own collaborators.
 *
 * Choreography ([restore], one pass per call):
 *  - raise [isRestoring], (re-)arm the mirror collector on the host scope,
 *  - race the restore call against [warmup] (Android warms its preferences
 *    DataStore during the restore window; desktop passes nothing),
 *  - on a successful restore with a persisted (server, user) pair, wait for
 *    the MIRROR — not the host's auth flow — to flip true, bounded by
 *    [AUTH_CONFIRMATION_TIMEOUT_MS],
 *  - release [isRestoring] (success or not).
 *
 * The mirror discipline is the point: the mirror collector writes
 * [isAuthenticated] off the host's auth flow, and the release below waits on
 * that SAME flow the shell renders from, so resuming off the release can
 * never land while the shell still composes the signed-out host. Waiting on
 * the host flow instead has a measured failure mode (Android, on device): the
 * repository's flip resumed the restore up to ~10 ms ahead of the rendered
 * mirror — a (isRestoring=false, isAuthenticated=false) frame that flashed
 * the server list over Home. The timeout caps the wait so a corrupted flow
 * can't pin the splash forever: a pathological stall past 2.5 s trades one
 * possible frame of auth flash for un-blocking the gate.
 *
 * The mirror collector is launched on the host scope passed to [restore]
 * (cancel-then-replace per call), so it dies with the shell exactly as the
 * hosts' former hand-rolled collectors did — Android's activity-scoped
 * ViewModel scope, desktop's composition scope. [onAuthChange] rides the
 * same collector after every mirror write (Android's false-edge teardown:
 * stop the health monitor, clear the library folders; the mirror write lands
 * first, preserving the former single-collector order).
 *
 * Per-shell remains: the splash rendering itself — Android's system
 * splash-screen gate polls [isRestoring], desktop renders its restore splash
 * while it holds; Android also exposes [isAuthenticated] to its shell
 * verbatim.
 *
 * @param authChanges the host's auth state (`AuthRepository.isAuthenticated`);
 *   mirrored into [isAuthenticated] and gated on by the release.
 * @param restoreSession the restore call (`AuthRepository.restoreSession`);
 *   only a success with a persisted (server, user) pair waits for the mirror.
 * @param currentServer the signed-in server flow, read once after a
 *   successful restore (`null` when nothing persisted — skip the wait).
 * @param currentUser the signed-in user flow, read once after a successful
 *   restore.
 * @param warmup a read raced alongside the restore and awaited before the
 *   wait; default no-op (desktop).
 * @param onAuthChange fired after every mirror write, with the same edge
 *   value; default no-op (desktop).
 */
class SessionRestore(
    private val authChanges: Flow<Boolean>,
    private val restoreSession: suspend () -> Result<Unit>,
    private val currentServer: Flow<ServerInfo?>,
    private val currentUser: Flow<UserInfo?>,
    private val warmup: suspend () -> Unit = {},
    private val onAuthChange: suspend (Boolean) -> Unit = {},
) {

    private val _isRestoring = MutableStateFlow(true)

    /**
     * The splash gate: true from construction (and re-raised at the top of
     * every [restore]) until the choreography settles. Android's system
     * splash-screen keep-on-screen condition polls it; desktop renders its
     * restore splash while it holds.
     */
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)

    /**
     * The rendered authenticated flag — the host's auth flow mirrored by this
     * class's own collector. The restore release waits on THIS flow (see the
     * class KDoc for why the host flow must not be waited on directly), and
     * shells render from it verbatim.
     */
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private var mirrorJob: Job? = null

    /**
     * Synchronously raises the splash gate — the host calls this BEFORE
     * launching [restore] so the gate is up before any suspension: with a
     * non-immediate dispatcher, the launched pass's own re-raise would
     * otherwise leave a window where the splash reads the previous pass's
     * `false`. Idempotent with [restore]'s internal re-raise.
     */
    fun markRestoring() {
        _isRestoring.value = true
    }

    /**
     * One restore pass (see the class KDoc). Suspends until the gate
     * settles — the host's continuation (Android's `onSessionRestored`, the
     * launch-time update check; desktop's splash-gate recomposition) runs
     * strictly after the choreography, mirror included.
     */
    suspend fun restore(scope: CoroutineScope) {
        _isRestoring.value = true
        mirrorJob?.cancel()
        mirrorJob = scope.launch {
            authChanges.collect { isAuth ->
                _isAuthenticated.value = isAuth
                onAuthChange(isAuth)
            }
        }
        coroutineScope {
            val authDeferred = async { restoreSession() }
            val warmupDeferred = async { warmup() }
            val result = authDeferred.await()
            warmupDeferred.await()
            if (result.isSuccess) {
                val server = currentServer.first()
                val user = currentUser.first()
                if (server != null && user != null) {
                    // Restore succeeded with a persisted server + user, so the
                    // authenticated flag should already be true. Wait on the
                    // MIRROR — the flag the shell renders from, written by the
                    // collector above — not the host flow: resuming off the
                    // mirror write itself guarantees the release below cannot
                    // land while the shell still composes the signed-out auth
                    // host.
                    withTimeoutOrNull(AUTH_CONFIRMATION_TIMEOUT_MS) {
                        _isAuthenticated.first { it }
                    }
                }
            }
        }
        _isRestoring.value = false
    }

    companion object {
        /**
         * The mirror flip trails the restore by ~10 ms, so this exists purely
         * to bound the corrupted-flow case — not to outlast a slow cold start.
         * A pathological stall past 2.5 s trades one possible frame of auth
         * flash for un-blocking the splash instead of pinning it for seconds.
         */
        const val AUTH_CONFIRMATION_TIMEOUT_MS = 2_500L
    }
}
