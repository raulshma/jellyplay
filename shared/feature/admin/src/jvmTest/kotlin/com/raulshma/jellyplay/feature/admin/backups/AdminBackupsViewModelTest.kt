package com.raulshma.jellyplay.feature.admin.backups

import com.raulshma.jellyplay.core.data.repository.AdminBackupRepository
import com.raulshma.jellyplay.core.data.repository.AdminBackupsSnapshot
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.ServerBackup
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the backups screen flow (`AdminBackupsViewModel`) — the list ladder
 * and, chiefly, the restore state machine (fire → poll → recover):
 *
 *  - the snapshot ladder drives loading/error state and carries the
 *    server-version gate (`supportsBackups = false` hides the feature);
 *  - a create refreshes the list (create is synchronous: the response IS the
 *    manifest);
 *  - restore is fire-and-forget 204: the VM flips to Restoring, polls the
 *    health probe (`probeServer` → `GET /System/Info/Public`) against the
 *    current server's address until it answers, re-establishes the session
 *    through `restoreSession()`, refreshes the list, and settles back to
 *    Idle with the one-shot success flag; a fire failure or poll timeout
 *    lands back in Idle with the failure surfaced and never calls
 *    restoreSession.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdminBackupsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music/livetv conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var backupRepository: AdminBackupRepository
    private lateinit var authRepository: AuthRepository

    private val backup = ServerBackup(
        backupEngineVersion = "1.0.0.0",
        dateCreated = "2026-09-28T10:15:00Z",
        options = BackupComponentOptions(metadata = true, database = true),
        path = "/backups/jf.zip",
        serverVersion = "10.11.2",
    )

    private val serverAddress = "http://server.example:8096"

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        backupRepository = mockk()
        authRepository = mockk()
        every { authRepository.currentServer } returns
            MutableStateFlow(ServerInfo(id = "server-1", name = "Jelly", address = serverAddress))
        coEvery { authRepository.restoreSession() } returns Result.success(Unit)
        coEvery { backupRepository.getBackupsSnapshot() } returns
            Result.success(AdminBackupsSnapshot(backups = listOf(backup)))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── List ladder ──

    @Test
    fun `load populates the backups list`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isSupported)
        assertEquals(listOf(backup), viewModel.state.value.backups)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a feature-hidden snapshot leaves the error clear and hides the feature`() = runTest(mainDispatcher) {
        coEvery { backupRepository.getBackupsSnapshot() } returns
            Result.success(AdminBackupsSnapshot(backups = emptyList(), supportsBackups = false))

        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isSupported)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `load failure surfaces the error`() = runTest(mainDispatcher) {
        coEvery { backupRepository.getBackupsSnapshot() } returns
            Result.failure(RuntimeException("offline"))

        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()

        assertEquals("offline", viewModel.state.value.error)
    }

    @Test
    fun `createBackup refreshes the list after the manifest lands`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.createBackup(any()) } returns
            Result.success(backup.copy(path = "/backups/new.zip"))

        viewModel.createBackup(
            BackupComponentOptions(metadata = true, trickplay = false, subtitles = true, database = true),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            backupRepository.createBackup(
                BackupComponentOptions(metadata = true, trickplay = false, subtitles = true, database = true),
            )
        }
        // init load + the post-create refresh.
        coVerify(exactly = 2) { backupRepository.getBackupsSnapshot() }
        assertFalse(viewModel.state.value.isCreating)
        assertNull(viewModel.state.value.error)
    }

    // ── Restore state machine (fire → poll → recover) ──

    @Test
    fun `restore stays in Restoring while the probe fails and recovers through restoreSession`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.restoreBackup("jf.zip") } returns Result.success(Unit)
        // Down: the restart window. Then the server answers again.
        coEvery { authRepository.probeServer(serverAddress) } returnsMany
            listOf(
                Result.failure(RuntimeException("down")),
                Result.failure(RuntimeException("still down")),
                Result.success(ServerInfo(id = "server-1", name = "Jelly", address = serverAddress)),
            )

        viewModel.restoreBackup("jf.zip")
        // Mid-poll: the 3 s probe interval has only run its first (failing) probe.
        advanceTimeBy(4_000)
        assertEquals(RestorePhase.Restoring, viewModel.state.value.restorePhase)

        advanceUntilIdle()

        // Exactly three probes against the current server's address — the
        // health-probe reachability check, never the authed client.
        coVerify(exactly = 3) { authRepository.probeServer(serverAddress) }
        coVerify(exactly = 1) { authRepository.restoreSession() }
        // The list refreshed after the reconnect (init load + post-restore).
        coVerify(exactly = 2) { backupRepository.getBackupsSnapshot() }
        assertEquals(RestorePhase.Idle, viewModel.state.value.restorePhase)
        assertTrue(viewModel.state.value.restoreComplete)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a failed restore fire returns to Idle with the error and never probes`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.restoreBackup("jf.zip") } returns
            Result.failure(RuntimeException("denied"))

        viewModel.restoreBackup("jf.zip")
        advanceUntilIdle()

        assertEquals(RestorePhase.Idle, viewModel.state.value.restorePhase)
        assertFalse(viewModel.state.value.restoreComplete)
        assertNotNull(viewModel.state.value.error)
        coVerify(exactly = 0) { authRepository.probeServer(any()) }
        coVerify(exactly = 0) { authRepository.restoreSession() }
    }

    @Test
    fun `a poll timeout gives up with the error and skips the reconnection`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.restoreBackup("jf.zip") } returns Result.success(Unit)
        coEvery { authRepository.probeServer(serverAddress) } returns
            Result.failure(RuntimeException("down forever"))

        viewModel.restoreBackup("jf.zip")
        advanceUntilIdle()

        coVerify(exactly = 60) { authRepository.probeServer(serverAddress) }
        coVerify(exactly = 0) { authRepository.restoreSession() }
        assertEquals(RestorePhase.Idle, viewModel.state.value.restorePhase)
        assertFalse(viewModel.state.value.restoreComplete)
        // The unreachable-server feedback is a typed flag — the screen
        // localizes it; the VM carries no user-facing text.
        assertTrue(viewModel.state.value.restoreServerUnreachable)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `a restore in flight refuses a second fire`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.restoreBackup(any()) } returns Result.success(Unit)
        coEvery { authRepository.probeServer(serverAddress) } returns
            Result.failure(RuntimeException("down"))

        viewModel.restoreBackup("jf.zip")
        advanceTimeBy(4_000)
        assertEquals(RestorePhase.Restoring, viewModel.state.value.restorePhase)

        viewModel.restoreBackup("jf.zip")
        advanceUntilIdle()

        coVerify(exactly = 1) { backupRepository.restoreBackup(any()) }
    }

    @Test
    fun `a list reload after the poll timeout clears the unreachable flag`() = runTest(mainDispatcher) {
        val viewModel = AdminBackupsViewModel(backupRepository, authRepository)
        advanceUntilIdle()
        coEvery { backupRepository.restoreBackup(any()) } returns Result.success(Unit)
        coEvery { authRepository.probeServer(serverAddress) } returns
            Result.failure(RuntimeException("down forever"))

        viewModel.restoreBackup("jf.zip")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.restoreServerUnreachable)

        // The error screen's retry is this ladder — a successful reload must
        // hand the UI back, not leave the unreachable message owning it.
        viewModel.loadBackups()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.restoreServerUnreachable)
        assertNull(viewModel.state.value.error)
        assertTrue(viewModel.state.value.isSupported)
    }
}
