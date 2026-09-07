package com.raulshma.jellyplay.feature.shell

import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Pins the shared session-policy wiring both shells serve to their surfaces
 * (ADR 0001): admin-refresh arbitration THROUGH the wired
 * [AdminRefreshGate] (one refresh per 30 s window, in-flight serialization,
 * only-on-success stamping — the contracts MainViewModelTest pinned against
 * Android's copy and the desktop scaffold's inlined copy duplicated),
 * homeMode collect + optimistic persist, the revoke/plain logout fork, and
 * the app-update check→message mapping. Fake clock + test dispatcher, the
 * AdminRefreshGateTest pattern; the repositories are plain lambdas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellSessionControllerTest {

    private class FakeClock {
        // Starts past the gate's zero timestamp: lastRefreshAtMs is 0 before
        // the first success, so "now" must already be a full window ahead for
        // the first refresh to be eligible (the wall clock always is).
        var now: Long = 100_000L
        fun time(): Long = now
    }

    /** Default wall clock for tests that don't pin the window boundary. */
    private val farFutureMs: () -> Long = { 100_000_000L }

    // ── Admin refresh arbitration through the gate ──────────────────────

    @Test
    fun `refresh runs once inside the window and again once it elapses`() = runTest {
        val clock = FakeClock()
        var refreshCalls = 0
        val controller = controller(
            scope = testScope(),
            nowMs = clock::time,
            refreshCurrentUser = {
                refreshCalls++
                Result.success(userInfo(isAdmin = false))
            },
        )

        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(1, refreshCalls)

        // A millisecond short of the window: the dedupe blocks the re-fetch.
        clock.now += AdminRefreshGate.DEFAULT_INTERVAL_MS - 1
        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(1, refreshCalls)

        // The window is inclusive — at exactly 30 s the refresh runs.
        clock.now += 1
        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(2, refreshCalls)
        assertFalse(controller.isRefreshingAdmin.value)
    }

    @Test
    fun `a refresh in flight suppresses re-entry synchronously`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var refreshCalls = 0
        val controller = controller(
            scope = testScope(),
            refreshCurrentUser = {
                refreshCalls++
                gate.await()
                Result.success(userInfo(isAdmin = false))
            },
        )


        controller.refreshAdminStatusNow()
        advanceUntilIdle() // suspends inside refreshCurrentUser
        assertTrue(controller.isRefreshingAdmin.value, "in-flight flag must be up")

        controller.refreshAdminStatusNow() // early-out: refresh still in flight
        advanceUntilIdle()
        assertEquals(1, refreshCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(controller.isRefreshingAdmin.value)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun `a failed refresh does not advance the dedupe window`() = runTest {
        val clock = FakeClock()
        var fail = true
        var refreshCalls = 0
        val controller = controller(
            scope = testScope(),
            nowMs = clock::time,
            refreshCurrentUser = {
                refreshCalls++
                if (fail) Result.failure(RuntimeException("offline"))
                else Result.success(userInfo(isAdmin = false))
            },
        )

        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(1, refreshCalls)

        // No successful completion → no window stamp → immediately eligible.
        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(2, refreshCalls)

        fail = false
        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(3, refreshCalls)

        // Only NOW does the success stamp close the window for a full interval.
        clock.now += AdminRefreshGate.DEFAULT_INTERVAL_MS - 1
        controller.refreshAdminStatusNow()
        advanceUntilIdle()
        assertEquals(3, refreshCalls)
    }

    @Test
    fun `isAdmin projects the current user's admin flag`() = runTest {
        val currentUser = MutableStateFlow<UserInfo?>(null)
        val scope = testScope()
        val controller = controller(scope = scope, currentUser = currentUser)
        // Eager subscriber so the WhileSubscribed sharing is active (the
        // MainViewModelTest pattern).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            controller.isAdmin.collect { }
        }
        advanceUntilIdle()
        assertFalse(controller.isAdmin.value)

        currentUser.value = userInfo(isAdmin = true)
        advanceUntilIdle()
        assertTrue(controller.isAdmin.value)
    }

    // ── homeMode collect / persist ──────────────────────────────────────

    @Test
    fun `homeMode mirrors the persisted stream and setHomeMode persists optimistically`() = runTest {
        val persisted = mutableListOf<HomeMode>()
        val changes = MutableStateFlow(HomeMode.VIDEO)
        val controller = controller(
            scope = testScope(),
            homeModeChanges = changes,
            persistHomeMode = { persisted.add(it) },
        )
        advanceUntilIdle()
        assertEquals(HomeMode.VIDEO, controller.homeMode.value)

        // Store-driven change (e.g. another surface wrote the pref).
        changes.value = HomeMode.MUSIC
        advanceUntilIdle()
        assertEquals(HomeMode.MUSIC, controller.homeMode.value)

        // The command lands optimistically — BEFORE any store emission or
        // persist completes (the desktop rail switches without waiting).
        controller.setHomeMode(HomeMode.VIDEO)
        assertEquals(HomeMode.VIDEO, controller.homeMode.value)
        advanceUntilIdle()
        assertEquals(listOf(HomeMode.VIDEO), persisted)
    }

    @Test
    fun `without a homeMode stream the state stays default but persistence still runs`() = runTest {
        // The Android wiring: homeMode is rendered from the preferences
        // pipeline, so homeModeChanges is null and the controller only persists.
        val persisted = mutableListOf<HomeMode>()
        val controller = controller(
            scope = testScope(),
            homeModeChanges = null,
            persistHomeMode = { persisted.add(it) },
        )

        controller.setHomeMode(HomeMode.MUSIC)
        assertEquals(HomeMode.MUSIC, controller.homeMode.value)
        advanceUntilIdle()

        assertEquals(listOf(HomeMode.MUSIC), persisted)
    }

    // ── logout fork ─────────────────────────────────────────────────────

    @Test
    fun `logout dispatches the revoke fork to the shell sign-out action`() = runTest {
        val signOuts = mutableListOf<Boolean>()
        val controller = controller(
            scope = testScope(),
            signOut = { revoke -> signOuts.add(revoke) },
        )

        controller.logout(revoke = true)
        controller.logout(revoke = false)
        advanceUntilIdle()

        assertEquals(listOf(true, false), signOuts)
    }

    // ── update check → message mapping ──────────────────────────────────

    @Test
    fun `an available update maps to its latest version`() {
        val message = ShellSessionController.updateCheckMessage(
            Result.success(appUpdateInfo(isUpdateAvailable = true, latestVersion = "1.2.3")),
        )
        assertEquals(UpdateCheckMessage.UpdateAvailable("1.2.3"), message)
    }

    @Test
    fun `an up-to-date check maps to UpToDate`() {
        val message = ShellSessionController.updateCheckMessage(
            Result.success(appUpdateInfo(isUpdateAvailable = false, latestVersion = "1.2.3")),
        )
        assertEquals(UpdateCheckMessage.UpToDate, message)
    }

    @Test
    fun `a failed check maps to the exception message`() {
        val message = ShellSessionController.updateCheckMessage(
            Result.failure(RuntimeException("rate limited")),
        )
        assertIs<UpdateCheckMessage.Failed>(message)
        assertEquals("rate limited", message.reason)
    }

    @Test
    fun `a failure without a message keeps the null reason`() {
        val message = ShellSessionController.updateCheckMessage(
            Result.failure(RuntimeException()),
        )
        assertEquals(UpdateCheckMessage.Failed(null), message)
    }

    // ── helpers ─────────────────────────────────────────────────────────

    /**
     * The shell scope the tests hand the controller. Same shape as the
     * production scopes — Android's viewModelScope, desktop's composition
     * scope — an UNPARENTED job on the test scheduler (MainViewModelTest's
     * `Dispatchers.setMain` pattern): [advanceUntilIdle] drives its launches,
     * and runTest's leak check never waits on it because it is not a child of
     * the test job.
     */
    private fun kotlinx.coroutines.test.TestScope.testScope(): CoroutineScope =
        CoroutineScope(coroutineContext + SupervisorJob())

    private fun controller(
        scope: CoroutineScope,
        nowMs: () -> Long = farFutureMs,
        currentUser: MutableStateFlow<UserInfo?> = MutableStateFlow(null),
        refreshCurrentUser: suspend () -> Result<UserInfo> = {
            Result.success(userInfo(isAdmin = false))
        },
        homeModeChanges: kotlinx.coroutines.flow.Flow<HomeMode>? = null,
        persistHomeMode: suspend (HomeMode) -> Unit = {},
        signOut: suspend (revoke: Boolean) -> Unit = {},
    ) = ShellSessionController(
        scope = scope,
        nowMs = nowMs,
        currentUser = currentUser,
        refreshCurrentUser = refreshCurrentUser,
        persistHomeMode = persistHomeMode,
        homeModeChanges = homeModeChanges,
        signOut = signOut,
    )

    private fun userInfo(isAdmin: Boolean) = UserInfo(
        id = "user-1",
        name = "User",
        serverAddress = "https://server",
        accessToken = "token",
        isAdmin = isAdmin,
    )

    private fun appUpdateInfo(
        isUpdateAvailable: Boolean,
        latestVersion: String,
    ) = AppUpdateInfo(
        latestVersion = latestVersion,
        htmlUrl = "https://github.com/example/jellyplay/releases/tag/v$latestVersion",
        releaseNotes = "notes",
        isUpdateAvailable = isUpdateAvailable,
        downloadAssetUrl = null,
        downloadAssetName = null,
        releaseSize = 0L,
    )
}
