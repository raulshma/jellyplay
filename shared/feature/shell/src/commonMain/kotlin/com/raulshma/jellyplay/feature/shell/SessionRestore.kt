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
 * The launch gate as ONE mutually exclusive phase — the whole
 * restoring/signed-out/signed-in state of the shell in a single value, so
 * "is the splash up" and "what may render" can never be answered
 * inconsistently.
 *
 * This replaces SessionRestore's former two-boolean machine
 * (`isRestoring` + `isAuthenticated`, a mirror of the host's auth flow).
 * Two independently-written booleans could always name frames neither
 * intended: the measured one was (isRestoring=false, isAuthenticated=false)
 * right after a SUCCESSFUL restore with a persisted (server, user) pair —
 * the gate released while the mirror still lagged, so the login screen
 * flashed for a frame or two ("auth flash", see the class KDoc below). The
 * sealed type makes that frame unrepresentable: there is no
 * splash-down-but-destination-unknown value to write. Pinned by
 * SessionRestoreTest (the shell module's jvmTest — the release-discipline
 * pins assert the phase emission list directly).
 */
sealed interface LaunchPhase {

    /**
     * The splash is pinned. The initial phase from construction, re-raised
     * at the top of every [SessionRestore.restore] pass and by
     * [SessionRestore.markRestoring]. Only the choreography itself
     * transitions OUT of this phase, and it does so in the same write that
     * names the destination — see [SessionRestore.restore].
     */
    data object Restoring : LaunchPhase

    /**
     * The gate is down and no session holds: the auth/onboarding branch may
     * render (Android's SignedOutAuthHost-seeded shell branch, desktop's
     * pre-scaffold sign-in pane). The deferred-401 teardown lands here as a
     * defined Authenticated → NeedsAuth transition, not a flash.
     */
    data object NeedsAuth : LaunchPhase

    /**
     * The gate is down and a session is live: the main shell may render
     * (Android's MainContent branch, desktop's DesktopNavScaffold). A later
     * sign-out / revoked-token teardown transitions to [NeedsAuth] through
     * the phase collector.
     */
    data object Authenticated : LaunchPhase
}

/**
 * The session-restore choreography both shells need (Android
 * `SessionCoordinator` / desktop `DesktopAppRoot`): run the restore call,
 * and settle the launch gate into a DEFINITE destination — the
 * [LaunchPhase] the shell renders from — in one atomic write. The shells'
 * `when` dispatches on [phase] directly; Restoring pins the splash,
 * NeedsAuth/Authenticated name the branch, and nothing else is
 * representable.
 *
 * Lives in shared/feature/shell beside [ShellSessionController] as part of
 * the session-policy wiring (the shell-session pattern): pure inputs
 * only — flows, suspend lambdas, a scope — no Koin binding, no repository
 * types — so the module keeps its repository-free signature set. Each shell
 * constructs it directly over its own collaborators.
 *
 * Choreography ([restore], one pass per call):
 *  - re-raise [LaunchPhase.Restoring], (re-)arm the phase collector on the
 *    host scope,
 *  - race the restore call against [warmup] (Android warms its preferences
 *    DataStore during the restore window; desktop passes nothing),
 *  - on a successful restore with a persisted (server, user) pair, wait for
 *    the host's auth flow ([authChanges]) to flip true, bounded by
 *    [AUTH_CONFIRMATION_TIMEOUT_MS],
 *  - settle the phase (success or not): ONE write of NeedsAuth or
 *    Authenticated, decided from [authChanges]' CURRENT value.
 *
 * Why ONE phase instead of the former two booleans — the measured failure
 * the old machine documented: the repository's auth flip resumed the old
 * (pre-mirror) release up to ~10 ms ahead of the rendered authenticated
 * flag, and the mirror choreography that fixed it did so by ordering two
 * writes (mirror first, gate second) — a discipline that still left the
 * timeout path (and any collector stall) able to release the splash while
 * the mirror read false: the (splash-down, auth=false) frame that flashed
 * the server list over Home after a successful restore. The phase machine
 * removes the second field instead of ordering the two: the gate release
 * and the destination determination are the SAME [phase] write, so no
 * frame can exist where the splash is down and neither NeedsAuth nor
 * Authenticated holds. The shell cannot compose signed-out content behind
 * a dropped splash, because "splash dropped" and "branch chosen" are one
 * observation of one value.
 *
 * The wait is now on the host's auth flow directly — the thing the old
 * machine explicitly refused (it had to wait on its own mirror, because
 * the mirror was a SECOND rendered state the release had to outrun). With
 * the phase as the only rendered state there is no second state to race:
 * resuming off the flow emission and writing the phase afterwards cannot
 * strand the shell on a stale branch, and since [authChanges] is a
 * StateFlow, a flip that already landed makes the wait return immediately
 * (the success path releases at zero elapsed time). The wait's remaining
 * job is the grace window: a repository that flips its auth flag some
 * milliseconds AFTER restoreSession returns (a slow adapter write) gets
 * [AUTH_CONFIRMATION_TIMEOUT_MS] to land the flip before the phase
 * decides, so the ordinary cold start still settles Authenticated.
 *
 * The timeout path decides from [authChanges].value read synchronously AT
 * the decision point — never from a stale emission: if the flow has
 * flipped by then (even between the cap firing and the write) the phase
 * lands Authenticated; if it genuinely reads false, NeedsAuth is what the
 * authoritative value says, and a flip that lands later transitions
 * NeedsAuth → Authenticated through the phase collector as a defined
 * transition (at most a bounded flash of the auth host — the documented
 * 2.5 s trade-off, now narrowed: the old path released the splash against
 * a possibly-stale mirror; this path releases the splash INTO the value
 * it just read).
 *
 * The phase collector is launched on the host scope passed to [restore]
 * (cancel-then-replace per call), so it dies with the shell — Android's
 * activity-scoped ViewModel scope, desktop's composition scope. Its
 * discipline: while the gate is up (phase == Restoring) it does NOT write
 * the phase — an auth edge during a restore pass must never drop the
 * splash mid-flight; the suppressed edges are not lost, because the
 * release decision reads [authChanges].value at decision time, which
 * already contains every edge emitted so far. Once the gate is down the
 * collector keeps the phase in step with the auth flow on every edge —
 * the deferred-401 teardown's Authenticated → NeedsAuth bounce and the
 * sign-in-through-the-auth-host's NeedsAuth → Authenticated swap are both
 * these writes. [onAuthChange] rides the same collector AFTER the phase
 * write (Android's false-edge teardown: stop the health monitor, clear the
 * library folders; the rendered state flips first).
 *
 * Per-shell remains: the splash rendering itself — Android's system
 * splash-screen gate polls `phase is LaunchPhase.Restoring`, desktop
 * renders its restore splash while [LaunchPhase.Restoring] holds.
 *
 * @param authChanges the host's auth state
 *   (`AuthRepository.isAuthenticated` — a StateFlow: its `.value` is the
 *   synchronous read the release decision is made from, which is why this
 *   parameter is a StateFlow and not a plain Flow).
 * @param restoreSession the restore call (`AuthRepository.restoreSession`);
 *   only a success with a persisted (server, user) pair waits for the auth
 *   flip.
 * @param currentServer the signed-in server flow, read once after a
 *   successful restore (`null` when nothing persisted — skip the wait).
 * @param currentUser the signed-in user flow, read once after a successful
 *   restore.
 * @param warmup a read raced alongside the restore and awaited before the
 *   wait; default no-op (desktop).
 * @param onAuthChange fired after every phase-relevant auth edge, with the
 *   same edge value; default no-op (desktop).
 */
class SessionRestore(
    private val authChanges: StateFlow<Boolean>,
    private val restoreSession: suspend () -> Result<Unit>,
    private val currentServer: Flow<ServerInfo?>,
    private val currentUser: Flow<UserInfo?>,
    private val warmup: suspend () -> Unit = {},
    private val onAuthChange: suspend (Boolean) -> Unit = {},
) {

    private val _phase = MutableStateFlow<LaunchPhase>(LaunchPhase.Restoring)

    /**
     * The launch gate AND the destination, in one value (see
     * [LaunchPhase]). Android's system splash-screen keep-on-screen
     * condition and both shells' `when` dispatch render from this alone.
     */
    val phase: StateFlow<LaunchPhase> = _phase.asStateFlow()

    private var phaseJob: Job? = null

    /**
     * Synchronously raises the splash gate — the host calls this BEFORE
     * launching [restore] so the gate is up before any suspension: with a
     * non-immediate dispatcher, the launched pass's own re-raise would
     * otherwise leave a window where the splash reads the previous pass's
     * settled destination. Idempotent with [restore]'s internal re-raise.
     * Any auth edge the still-alive previous collector processes in the
     * markRestoring → restore window finds the gate up and stays its hand
     * (the collector never writes while Restoring), so the splash cannot
     * flicker in that window either. Pinned by SessionRestoreTest (the
     * markRestoring pin) and SessionCoordinatorTest (the synchronous
     * start() raise, on a non-immediate test dispatcher).
     */
    fun markRestoring() {
        _phase.value = LaunchPhase.Restoring
    }

    /**
     * One restore pass (see the class KDoc). Suspends until the gate
     * settles — the host's continuation (Android's `onSessionRestored`, the
     * launch-time update check; desktop's splash-gate recomposition) runs
     * strictly after the choreography, destination included.
     */
    suspend fun restore(scope: CoroutineScope) {
        _phase.value = LaunchPhase.Restoring
        phaseJob?.cancel()
        phaseJob = scope.launch {
            authChanges.collect { isAuth ->
                // While the splash is up an auth edge must not drop it
                // mid-restore (the decision below owns the release), so the
                // write is stayed while Restoring — and no edge is lost by
                // that: the decision reads authChanges.value at decision
                // time, which already contains every emitted edge. Once the
                // gate is down this write is the live channel that keeps the
                // rendered branch in step with the session (deferred-401
                // bounce, sign-in swap).
                if (_phase.value != LaunchPhase.Restoring) {
                    _phase.value =
                        if (isAuth) LaunchPhase.Authenticated else LaunchPhase.NeedsAuth
                }
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
                    // Restore succeeded with a persisted server + user, so
                    // the session should be live. Give the host flow's flip
                    // a bounded grace window (it usually already landed
                    // during restoreSession, in which case this returns
                    // immediately — StateFlow emits its current value
                    // first); the cap keeps a corrupted flow from pinning
                    // the splash forever.
                    withTimeoutOrNull(AUTH_CONFIRMATION_TIMEOUT_MS) {
                        authChanges.first { it }
                    }
                }
            }
        }
        // THE release: gate drop and destination determination are one
        // write, decided from the auth flow's CURRENT value — the
        // synchronously readable authority, not a possibly-stale mirror
        // emission. A frame where the splash is down while neither
        // NeedsAuth nor Authenticated holds is unrepresentable by
        // construction; see the class KDoc for the flash frame this
        // replaces.
        _phase.value =
            if (authChanges.value) LaunchPhase.Authenticated else LaunchPhase.NeedsAuth
    }

    companion object {
        /**
         * The grace window for the host flow's auth flip to land after a
         * successful restore. This exists purely to bound the
         * corrupted-flow case — not to outlast a slow cold start. A
         * pathological stall past 2.5 s trades a bounded flash of the auth
         * host (NeedsAuth now, Authenticated on the later flip) for
         * un-blocking the splash instead of pinning it for seconds.
         */
        const val AUTH_CONFIRMATION_TIMEOUT_MS = 2_500L
    }
}
