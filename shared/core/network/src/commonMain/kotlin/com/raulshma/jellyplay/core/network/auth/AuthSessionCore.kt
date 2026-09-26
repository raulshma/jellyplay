package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What one [AuthSessionCore.atomicLogin] round-trip brought back from the
 * wire: the raw access token plus the ALREADY-MAPPED model user. The
 * platform lambda owns the round-trip AND its DTO → [UserInfo] mapping
 * (each platform speaks its own DTO dialect — the Jellyfin SDK on
 * jvmShared, the wire DTOs in this package); the core owns the
 * validation on top, checking the token BEFORE the user (the order both
 * pre-fold impls used). Nulls pass straight through to those validation
 * throws.
 */
data class RawLoginOutcome(
    val accessToken: String?,
    val userInfo: UserInfo?,
)

/**
 * The pre-login snapshot [AuthSessionCore.atomicLogin] captures in its
 * opening critical section and hands to [AuthSessionCore.restoreSession] on
 * failure: the whole [ActiveSession] plus the captured platform side state
 * ([S]), both read in the SAME lock so the pair stays atomic with
 * everything a restore might undo.
 */
data class CapturedLoginState<S>(
    val session: ActiveSession?,
    val side: S,
)

/**
 * The session mutation surface a platform injects into [AuthSessionCore] —
 * implemented by the two atomic state holders the auth clients already
 * publish through (`JellyfinApiEngine` on jvmShared,
 * [AtomicSessionState] in this package), so the folded spine drives the SAME state
 * (and, via the injected [Mutex], excludes against the SAME critical
 * sections) as every unfolded platform path: setUser, address failover's
 * session republish, the quick-connect endpoints. A write with one side
 * missing collapses the session to null exactly as both holders do.
 */
interface AuthSessionStateStore {
    fun currentSession(): ActiveSession?
    fun updateServer(server: ServerInfo?)
    fun updateSession(server: ServerInfo?, user: UserInfo?)
}

/**
 * The platform side-channel [AuthSessionCore] keeps atomic with the session
 * publish — the one piece the two pre-fold impls could NOT share, because
 * only the jvmShared engine holds a mutable API client beside its session
 * state. Every hook runs inside the caller's critical section (under the
 * injected [Mutex]), in the exact slot the pre-fold JVM spine placed its
 * `engine.updateApi` calls; [S] is the captured side state a failure may
 * need restored (the jvmShared `ApiClient?`; Unit otherwise).
 */
interface AuthSessionSideEffects<S> {
    /** Snapshot of the side state, captured beside the session in one lock. */
    fun captureSide(): S

    /**
     * Installs the side state for the freshly authenticated user — runs
     * inside the publish lock BEFORE the session write, so no unlocked
     * observer can act on the new session with the old side state.
     */
    fun publishSide(serverInfo: ServerInfo, userInfo: UserInfo)

    /** Puts a captured side state back, inside the restore lock. */
    fun restoreSide(previous: S)

    /** Drops the side state on disconnect, inside the disconnect lock. */
    fun clearSide()
}

/**
 * The no-op port for platforms with no side state to keep atomic with the
 * session — no client object to swap. [S] is Unit and every hook is
 * inert.
 */
val NoOpAuthSessionSideEffects: AuthSessionSideEffects<Unit> = object : AuthSessionSideEffects<Unit> {
    override fun captureSide() = Unit
    override fun publishSide(serverInfo: ServerInfo, userInfo: UserInfo) {}
    override fun restoreSide(previous: Unit) {}
    override fun clearSide() {}
}

/**
 * The auth session-establishment spine every `AuthApiClient` implementation
 * shares — the capture/adopt/try/publish/restore discipline the jvmShared
 * `AuthApiClientImpl` (Jellyfin SDK + OkHttp)
 * carried as a private copy until this
 * fold; ONE commonMain home so the session semantics cannot drift between
 * implementations. The platform files keep their declared divergences (the DTO →
 * user mapping, side-channel presence, retry/probe plumbing) and delegate
 * the spine here.
 *
 * Invariants (moved verbatim in substance from the pre-fold private
 * spines — the load-bearing rationales):
 *
 *  - ONE atomic publish per critical section. A session transition is
 *    observed as a single step (stable pair → stable pair, or →/from null)
 *    — never a synthetic `(newServer, oldUser)` intermediate, which
 *    downstream identity observers would classify as a real identity
 *    switch.
 *
 *  - [atomicLogin]: capture and pre-auth adopt happen in ONE critical
 *    section — the capture (session + side state, see
 *    [CapturedLoginState]) is atomic with everything [restoreSession]
 *    might undo, so a concurrent identity write between the two cannot be
 *    silently clobbered by the restore. The adopt runs only when NO
 *    session is established: adopting over a working session would publish
 *    SignedOut(previous), and identity observers react destructively to
 *    that (cache drop + previous identity's SWR snapshot clear) — a failed
 *    login attempt would wipe the signed-in user's cached home. The
 *    round-trip ([authenticate]) runs UNLOCKED; success replaces the pair
 *    atomically, and failure restores the captured values only if no other
 *    flow published in between ([restoreSession] re-checks under the mutex
 *    — a concurrent publish wins over the restore). RetryPolicy re-runs
 *    the caller's block against the same captured state, so the capture is
 *    idempotent across attempts (each failed attempt restored exactly what
 *    the next one re-captures).
 *
 *  - The side-channel hooks ([AuthSessionSideEffects]) run INSIDE the same
 *    critical sections as the session writes they accompany. On jvmShared,
 *    publishSide re-points the engine's ApiClient BEFORE the session
 *    publish because a failover's rebuildApiFor (mutex-held) landing
 *    between an unguarded updateApi and the publish would rebuild the
 *    client from the OLD user's token while the session claims the new
 *    one — requests then spoke as the wrong user until something rebuilt.
 *    Symmetrically, disconnect's clearSide stays inside the lock so a
 *    failover's rebuildApiFor that already passed its `_api == null`
 *    early-return cannot resurrect a live client on top of the null-out,
 *    leaving an authenticated client with a null session.
 *
 *  - [restoreSession] runs under [NonCancellable], so the restore survives
 *    caller cancellation mid-round-trip.
 */
class AuthSessionCore<S>(
    /**
     * The SAME mutex the injecting platform guards its other session writes
     * with — mutual exclusion must hold across folded and unfolded paths
     * (setUser, failover republishes, engine rebuilds).
     */
    private val mutex: Mutex,
    private val state: AuthSessionStateStore,
    private val sideEffects: AuthSessionSideEffects<S>,
) {

    /**
     * Capture → (signed-out adopt) → unlocked round-trip → atomic publish,
     * restoring the captured state on ANY failure (the class KDoc owns the
     * invariant each step protects). Validation order is fixed here, token
     * first: missing token → `Exception("No access token")`, then missing
     * user → `Exception("Authentication failed")` — both pre-fold impls
     * checked in that order, and the messages ride upstream classifiers.
     */
    suspend fun atomicLogin(
        serverInfo: ServerInfo,
        authenticate: suspend () -> RawLoginOutcome,
    ): UserInfo {
        val previous = mutex.withLock {
            val session = state.currentSession()
            val side = sideEffects.captureSide()
            if (session == null) {
                // Signed-out path: point currentServer at the target server.
                // Identity stays null → no transition fires, nothing to wipe;
                // the follow-up login republishes a real pair.
                state.updateSession(serverInfo, null)
            }
            CapturedLoginState(session, side)
        }
        return try {
            val raw = authenticate()
            raw.accessToken ?: throw Exception("No access token")
            val userInfo = raw.userInfo ?: throw Exception("Authentication failed")
            publishAuthenticatedSession(serverInfo, userInfo)
            userInfo
        } catch (t: Throwable) {
            restoreSession(previous)
            throw t
        }
    }

    /**
     * Single atomic publish of the authenticated (server, user) pair — one
     * critical-section step, so session observers never see the server
     * connected but its user missing (or vice versa). The side-effects
     * publish hook runs INSIDE this lock, BEFORE the session write.
     */
    private suspend fun publishAuthenticatedSession(serverInfo: ServerInfo, userInfo: UserInfo) {
        mutex.withLock {
            sideEffects.publishSide(serverInfo, userInfo)
            state.updateSession(
                serverInfo.copy(
                    userId = userInfo.id,
                    accessToken = userInfo.accessToken,
                    isConnected = true,
                ),
                userInfo,
            )
        }
    }

    /**
     * Puts a captured pre-auth state back after a failed login attempt.
     * Pairs with the pre-auth adopt in [atomicLogin]: without this, a wrong
     * password (or a declined Quick Connect) would leave the app signed out
     * and the previous identity's cached home cleared. The round-trip ran
     * with the mutex RELEASED, so a concurrent auth flow may have published
     * its own session in the meantime — the restore only runs when the
     * session still equals the captured value (checked under the mutex);
     * otherwise the newer publish wins and both the session and the side
     * state are left untouched. Runs under [NonCancellable] so the restore
     * survives caller cancellation, and re-points the side state at the
     * captured one in case the failure landed after a side swap had
     * already happened.
     */
    suspend fun restoreSession(previous: CapturedLoginState<S>) {
        withContext(NonCancellable) {
            mutex.withLock {
                if (state.currentSession() != previous.session) return@withLock
                sideEffects.restoreSide(previous.side)
                state.updateSession(previous.session?.server, previous.session?.user)
            }
        }
    }

    /**
     * `connectToServer`'s atomic adopt of a freshly probed server — adopts
     * the server AND drops any signed-in user in one critical-section step.
     * This path is reachable while authenticated (Settings → Server
     * Management → Add Server); a single-sided updateServer would publish a
     * synthetic (newServer, oldUser) ActiveSession — driving identity
     * observers to a ServerSwitched and a Room clear under the wrong
     * identity. The follow-up login republishes a real pair.
     */
    suspend fun adoptServer(info: ServerInfo) {
        mutex.withLock { state.updateSession(info, null) }
    }

    /** `setServer`: one locked single-side server update, pairing with the current user. */
    suspend fun setServer(info: ServerInfo) {
        mutex.withLock { state.updateServer(info) }
    }

    /**
     * `disconnect`: one atomic publish of the cleared pair (stable → null
     * in a single step), with the side state dropped INSIDE the same lock
     * (see the class KDoc's failover-ordering invariant).
     */
    suspend fun disconnect() {
        mutex.withLock {
            sideEffects.clearSide()
            state.updateSession(null, null)
        }
    }
}
