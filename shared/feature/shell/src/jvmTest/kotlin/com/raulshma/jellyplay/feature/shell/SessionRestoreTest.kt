package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared session-restore choreography as a ONE-PHASE machine
 * ([LaunchPhase]): the gate release and the destination determination are
 * the SAME [SessionRestore.phase] write, so the former two-boolean machine's
 * auth-flash frame — (splash down, authenticated=false) right after a
 * successful restore — is unrepresentable. Concretely, the pins:
 *  - a fresh gate sits in [LaunchPhase.Restoring] (splash pinned);
 *  - a successful restore lands EXACTLY one destination — the recorded
 *    phase-emission list is `[Restoring, Authenticated]` (or
 *    `[Restoring, NeedsAuth]`), i.e. the FIRST non-Restoring value the shell
 *    can observe is already the branch, with no intermediate phase;
 *  - the 2.5 s timeout decides AND releases in that one step (virtual-time
 *    assertions at cap-1 / cap), from the auth flow's CURRENT value;
 *  - a deferred sign-out is a defined Authenticated → NeedsAuth transition;
 *  - markRestoring returns the gate to Restoring;
 *  - an auth edge during a restore pass never drops the splash (the
 *    collector stays its hand while Restoring — the decision owns the
 *    release), and exactly one collector survives a re-run.
 *
 * The old suite pinned an ORDERING between two flows (mirror flip before
 * splash release); that ordering is meaningless when gate and destination
 * are one value, so the emission-list assertions replace it. The
 * restore/warmup seams stay plain lambdas (the ShellSessionControllerTest
 * pattern); the test dispatcher's virtual clock bounds the timeout test;
 * [SessionRestore.restore] completes, so it launches as a plain test child,
 * its phase collector running on the unparented host scope the hosts hand
 * it. The tests' own phase RECORDERS ride [TestScope.backgroundScope] on an
 * [UnconfinedTestDispatcher] — the teardown the old suite performed by hand
 * (`recorders.forEach { it.cancel() }`): backgroundScope cancels them at
 * test end so runTest's leak check sees no active child job, and the
 * unconfined dispatcher on the test's own scheduler delivers each phase
 * write to the recorder inline with the write, keeping the emission-list
 * pins exact (a backgroundScope collector on the default dispatcher would
 * have its resumptions starved once the foreground work drains).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionRestoreTest {

    /** Everything the choreography reaches through, recorded. */
    private class FakeRestore {
        val auth = MutableStateFlow(false)
        val server = MutableStateFlow<ServerInfo?>(null)
        val user = MutableStateFlow<UserInfo?>(null)

        var restoreResult: Result<Unit> = Result.success(Unit)

        /** Runs inside the restore call, before its result returns. */
        var onRestoreSession: () -> Unit = {}

        /** When set, the restore call parks on it (a mid-flight hold). */
        var hold: CompletableDeferred<Unit>? = null

        /** The edge values delivered to `onAuthChange`, in order. */
        val authEdges = mutableListOf<Boolean>()

        /** The restore-call invocations, in order. */
        val restoreCalls = mutableListOf<Unit>()

        fun sessionRestore() = SessionRestore(
            authChanges = auth,
            restoreSession = {
                restoreCalls.add(Unit)
                onRestoreSession()
                hold?.await()
                restoreResult
            },
            currentServer = server,
            currentUser = user,
            onAuthChange = { authEdges.add(it) },
        )
    }

    // ── release discipline ──────────────────────────────────────────────

    @Test
    fun `a fresh gate sits in Restoring with the destination undecided`() {
        val fake = FakeRestore()
        val sessionRestore = fake.sessionRestore()

        // Before any pass runs: the splash reads from the phase alone, and
        // the only representable value is the pinned splash.
        assertEquals(LaunchPhase.Restoring, sessionRestore.phase.value)
    }

    @Test
    fun `a successful restore lands Authenticated in one observable phase write`() = runTest {
        val fake = FakeRestore()
        // The on-device flash shape: the persisted (server, user) pair and
        // the auth flip land DURING the restore call.
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
            fake.auth.value = true
        }
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)

        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        // The first non-Restoring emission IS the destination: no
        // intermediate frame exists between splash and branch — this exact
        // list is the pin that kills the former (splash-down, auth=false)
        // flash frame.
        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.Authenticated), phases)
        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)
        // The release rode the flow flip (already landed during
        // restoreSession — the StateFlow wait returned immediately), not
        // the timeout cap.
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `a flip inside the grace window lands Authenticated before the cap`() = runTest {
        val fake = FakeRestore()
        // Restore succeeds with a persisted pair, but the auth flip trails
        // the restore call (a slow adapter write) — landing 100 ms later,
        // well inside the grace window.
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
        }
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)

        launch { sessionRestore.restore(testScope()) }
        launch {
            delay(100)
            fake.auth.value = true
        }
        advanceUntilIdle()

        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.Authenticated), phases)
        assertEquals(100, testScheduler.currentTime, "the release must ride the flip, not the cap")
    }

    @Test
    fun `the timeout decides and releases in one step when the flow never flips`() = runTest {
        val fake = FakeRestore()
        // Restore "succeeds" with a persisted pair, but the auth flow is
        // corrupted (never flips): the cap must un-block the splash.
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
        }
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        runCurrent()

        assertEquals(LaunchPhase.Restoring, sessionRestore.phase.value, "precondition: parked on the grace wait")
        advanceTimeBy(SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS - 1)
        assertEquals(
            LaunchPhase.Restoring,
            sessionRestore.phase.value,
            "a millisecond short of the cap the splash must still hold",
        )
        // advanceTimeBy only runs tasks scheduled STRICTLY before its new
        // time — the timer lands exactly on the cap, so run it.
        advanceTimeBy(1)
        runCurrent()

        // The decision (from the auth flow's CURRENT value, read
        // synchronously) and the release are one write: Restoring →
        // NeedsAuth directly, no intermediate frame.
        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.NeedsAuth), phases)
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value)
        assertEquals(SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `a timeout release still transitions Authenticated on a later flip`() = runTest {
        val fake = FakeRestore()
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
        }
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceTimeBy(SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS)
        runCurrent()
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value)

        // The documented 2.5 s trade-off, narrowed: a flow that flips after
        // the timeout decided does not strand the shell — the collector
        // moves the phase to Authenticated as a DEFINED transition.
        fake.auth.value = true
        advanceUntilIdle()

        assertEquals(
            listOf(LaunchPhase.Restoring, LaunchPhase.NeedsAuth, LaunchPhase.Authenticated),
            phases,
        )
        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)
    }

    @Test
    fun `a failed restore settles NeedsAuth without waiting`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.NeedsAuth), phases)
        assertEquals(0, testScheduler.currentTime, "a failed restore must not wait on the grace window")
        // The collector still ran its initial pass off the current
        // (signed-out) edge — the host teardown hook sees it.
        assertEquals(listOf(false), fake.authEdges)
        assertEquals(1, fake.restoreCalls.size)
    }

    @Test
    fun `a successful restore without a persisted pair settles NeedsAuth without waiting`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.success(Unit)
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.NeedsAuth), phases)
        assertEquals(0, testScheduler.currentTime, "no persisted pair → no grace wait")
    }

    @Test
    fun `a deferred sign-out transitions Authenticated to NeedsAuth`() = runTest {
        val fake = FakeRestore()
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
            fake.auth.value = true
        }
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)

        // The deferred-401 teardown shape: the repository tears the session
        // down AFTER the gate released. The "home then bounce to login"
        // behavior is a defined Authenticated → NeedsAuth phase transition,
        // not a flash — and the false edge still fires the host teardown
        // hook (stop health monitor / clear library folders).
        fake.auth.value = false
        advanceUntilIdle()

        assertEquals(
            listOf(LaunchPhase.Restoring, LaunchPhase.Authenticated, LaunchPhase.NeedsAuth),
            phases,
        )
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value)
        assertEquals(listOf(false, true, false), fake.authEdges)
    }

    // ── gate + collector wiring ─────────────────────────────────────────

    @Test
    fun `markRestoring returns the gate to Restoring`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value)

        // The host's synchronous gate-up (SessionCoordinator.start, before
        // it launches the pass): the very next read sees the pinned splash
        // with no suspension in between, and the pass's own re-raise stays
        // an idempotent safety.
        sessionRestore.markRestoring()
        assertEquals(LaunchPhase.Restoring, sessionRestore.phase.value)
    }

    @Test
    fun `a re-run re-raises the gate, stays its collector's hand while Restoring, and leaves exactly one collector`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value)

        // A session signs in between runs; the collector mirrors it into
        // the phase.
        fake.auth.value = true
        advanceUntilIdle()
        assertEquals(listOf(false, true), fake.authEdges)
        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)

        // The re-run re-raises the gate for its own pass…
        val hold = CompletableDeferred<Unit>()
        fake.hold = hold
        launch { sessionRestore.restore(testScope()) }
        runCurrent()
        assertEquals(LaunchPhase.Restoring, sessionRestore.phase.value, "the re-run must re-raise the splash gate")

        // …and an auth edge DURING the pass must not drop the splash: the
        // collector stays its hand while Restoring (the decision below owns
        // the release); the edge is not lost — the hook still sees it, and
        // the decision reads the flow's current value.
        fake.auth.value = false
        advanceUntilIdle()
        assertEquals(LaunchPhase.Restoring, sessionRestore.phase.value, "an edge mid-restore must not drop the splash")
        assertEquals(listOf(false, true, true, false), fake.authEdges)

        hold.complete(Unit)
        advanceUntilIdle()
        assertEquals(LaunchPhase.NeedsAuth, sessionRestore.phase.value, "the decision read the flow's current value")

        // Exactly ONE collector survives the replace: a later edge fires
        // the hook once (a surviving stale collector would double-fire it).
        fake.auth.value = true
        advanceUntilIdle()
        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)
        assertEquals(
            listOf(LaunchPhase.Restoring, LaunchPhase.NeedsAuth, LaunchPhase.Authenticated, LaunchPhase.Restoring, LaunchPhase.NeedsAuth, LaunchPhase.Authenticated),
            phases,
        )
        assertEquals(listOf(false, true, true, false, true), fake.authEdges)
    }

    @Test
    fun `warmup races the restore and lands before the gate settles`() = runTest {
        val fake = FakeRestore()
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
            fake.auth.value = true
        }
        var warmups = 0
        val sessionRestore = SessionRestore(
            authChanges = fake.auth,
            restoreSession = {
                fake.onRestoreSession()
                fake.restoreResult
            },
            currentServer = fake.server,
            currentUser = fake.user,
            warmup = { warmups++ },
        )
        val phases = mutableListOf<LaunchPhase>()
        collectPhases(sessionRestore, phases)
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertEquals(LaunchPhase.Authenticated, sessionRestore.phase.value)
        assertEquals(listOf(LaunchPhase.Restoring, LaunchPhase.Authenticated), phases)
        assertEquals(1, warmups, "the warm-up read ran once per restore pass")
        assertEquals(0, testScheduler.currentTime)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /**
     * Records [SessionRestore.phase] emissions into [phases] for an
     * emission-list pin. The teardown the old suite performed by hand
     * (cancel the recording collectors after [advanceUntilIdle]) is carried
     * structurally here: the collector rides [TestScope.backgroundScope], so
     * runTest cancels it at test end instead of failing with
     * UncompletedCoroutinesError over an active child job. The
     * [UnconfinedTestDispatcher] on the test's own scheduler is load-bearing:
     * it delivers each phase write to the recorder INLINE with the write
     * (exact, ordered emission lists — the pins read like a trace), where a
     * backgroundScope collector on the default StandardTestDispatcher would
     * have its resumptions starved by [advanceUntilIdle] once the foreground
     * (restore-pass) work drains.
     */
    private fun kotlinx.coroutines.test.TestScope.collectPhases(
        sessionRestore: SessionRestore,
        phases: MutableList<LaunchPhase>,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sessionRestore.phase.collect { phases.add(it) }
        }
    }

    /**
     * The host scope the tests hand the choreography (the
     * ShellSessionControllerTest pattern): an UNPARENTED job on the test
     * scheduler — [advanceUntilIdle] drives its launches, and runTest's leak
     * check never waits on it because it is not a child of the test job.
     */
    private fun kotlinx.coroutines.test.TestScope.testScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())
}

/** Top level beside the other shell tests' fakes. */
private fun testServer() = ServerInfo(
    id = "server-1",
    name = "Server",
    address = "https://server.example",
)

private fun testUser() = UserInfo(
    id = "user-1",
    name = "User",
    serverAddress = "https://server.example",
    accessToken = "token-1",
)
