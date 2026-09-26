package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the shared session-restore choreography (the Android
 * SessionCoordinator twin desktop used to lack): a successful restore with a
 * persisted (server, user) pair settles the splash gate only AFTER the
 * rendered authenticated mirror has flipped (the measured splash-flash bug
 * class — the release riding the mirror write, never the host flow, never
 * the raw restore completion), the mirror wait is bounded by
 * [SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS] so a corrupted flow can't pin
 * the gate, failures settle immediately, and the mirror collector is
 * cancel-then-replaced per run (exactly one live collector, the Android-only
 * false-edge teardown riding it). Test dispatcher's virtual clock bounds the
 * timeout test; the restore/warmup seams are plain lambdas (the
 * ShellSessionControllerTest pattern). [SessionRestore.restore] itself
 * completes, so it launches as a plain test child; its mirror collector runs
 * on the unparented host scope the hosts hand it.
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
    fun `release never precedes the authenticated mirror on successful restore`() = runTest {
        val fake = FakeRestore()
        // The on-device flash shape: the persisted (server, user) pair and
        // the auth flip land DURING the restore call, so a release off the
        // restore completion (or the host flow) could beat the rendered
        // mirror.
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
            fake.auth.value = true
        }
        val sessionRestore = fake.sessionRestore()

        // A channel of write events (NOT combine — it conflates intermediate
        // pairs away) so the test can assert the ORDER the two exposed flows
        // actually flipped in.
        val events = Channel<String>(Channel.UNLIMITED)
        val recorders = listOf(
            launch { sessionRestore.isRestoring.collect { events.send("restoring=$it") } },
            launch { sessionRestore.isAuthenticated.collect { events.send("auth=$it") } },
        )

        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        recorders.forEach { it.cancel() }
        events.close()

        val order = mutableListOf<String>()
        for (event in events) order += event
        val releaseIndex = order.indexOf("restoring=false")
        val mirrorIndex = order.indexOf("auth=true")
        assertNotEquals(-1, releaseIndex, "isRestoring never released (order=$order)")
        assertNotEquals(-1, mirrorIndex, "authenticated mirror never flipped (order=$order)")
        assertTrue(
            mirrorIndex < releaseIndex,
            "splash gate released before the authenticated mirror flipped: $order",
        )
        assertFalse(sessionRestore.isRestoring.value)
        // The release rode the mirror write, not the timeout cap.
        assertEquals(0, testScheduler.currentTime)
    }

    @Test
    fun `the timeout fires when the mirror never flips`() = runTest {
        val fake = FakeRestore()
        // Restore "succeeds" with a persisted pair, but the auth flow is
        // corrupted (never flips): the cap must un-block the splash.
        fake.onRestoreSession = {
            fake.server.value = testServer()
            fake.user.value = testUser()
        }
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        runCurrent()

        assertTrue(sessionRestore.isRestoring.value, "precondition: parked on the mirror wait")
        advanceTimeBy(SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS - 1)
        assertTrue(
            sessionRestore.isRestoring.value,
            "a millisecond short of the cap the splash must still hold",
        )
        // advanceTimeBy only runs tasks scheduled STRICTLY before its new
        // time — the timer lands exactly on the cap, so run it.
        advanceTimeBy(1)
        runCurrent()
        assertFalse(sessionRestore.isRestoring.value, "the cap must release the splash")
        assertEquals(SessionRestore.AUTH_CONFIRMATION_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `a failed restore settles the splash without the mirror wait`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertFalse(sessionRestore.isRestoring.value)
        assertEquals(0, testScheduler.currentTime, "a failed restore must not wait on the mirror")
        // The mirror collector still ran its initial pass off the current
        // (signed-out) edge — the host teardown hook sees it.
        assertEquals(listOf(false), fake.authEdges)
        assertEquals(1, fake.restoreCalls.size)
    }

    @Test
    fun `a successful restore without a persisted pair skips the mirror wait`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.success(Unit)
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertFalse(sessionRestore.isRestoring.value)
        assertEquals(0, testScheduler.currentTime, "no persisted pair → no mirror wait")
    }

    // ── collector + warmup wiring ───────────────────────────────────────

    @Test
    fun `a re-run re-raises the gate and leaves exactly one mirror collector`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        assertFalse(sessionRestore.isRestoring.value)

        // A session signs in between runs; the mirror mirrors it.
        fake.auth.value = true
        fake.restoreResult = Result.success(Unit)
        fake.server.value = testServer()
        fake.user.value = testUser()
        advanceUntilIdle()
        assertEquals(listOf(false, true), fake.authEdges)

        // The re-run re-raises the gate for its own pass…
        val hold = CompletableDeferred<Unit>()
        fake.hold = hold
        launch { sessionRestore.restore(testScope()) }
        runCurrent()
        assertTrue(sessionRestore.isRestoring.value, "the re-run must re-raise the splash gate")
        hold.complete(Unit)
        advanceUntilIdle()
        assertFalse(sessionRestore.isRestoring.value)

        // …and exactly ONE mirror collector survives the replace: a later
        // edge fires the hook once (a surviving stale collector would
        // double-fire it).
        fake.authEdges.clear()
        fake.auth.value = false
        advanceUntilIdle()
        assertEquals(listOf(false), fake.authEdges)
    }

    @Test
    fun `markRestoring raises the gate synchronously before the pass launches`() = runTest {
        val fake = FakeRestore()
        fake.restoreResult = Result.failure(IllegalStateException("no persisted session"))
        val sessionRestore = fake.sessionRestore()
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()
        assertFalse(sessionRestore.isRestoring.value)

        // The host's synchronous gate-up (SessionCoordinator.start, before it
        // launches the pass): the very next read sees true with no suspension
        // in between, and the pass's own re-raise stays an idempotent safety.
        sessionRestore.markRestoring()
        assertTrue(sessionRestore.isRestoring.value)
    }

    @Test
    fun `warmup races the restore and lands before the splash releases`() = runTest {
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
        launch { sessionRestore.restore(testScope()) }
        advanceUntilIdle()

        assertFalse(sessionRestore.isRestoring.value)
        assertEquals(1, warmups, "the warm-up read ran once per restore pass")
        assertEquals(0, testScheduler.currentTime)
    }

    // ── helpers ─────────────────────────────────────────────────────────

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
