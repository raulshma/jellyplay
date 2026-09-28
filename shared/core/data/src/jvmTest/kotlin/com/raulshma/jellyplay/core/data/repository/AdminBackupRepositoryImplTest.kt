package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [AdminBackupRepositoryImpl]'s one composition — the server-version
 * gate — plus its pass-throughs (pure forwards are not retested):
 *  1. a successful `GET /Backup` maps to a snapshot with
 *     `supportsBackups = true`;
 *  2. the 404 of a server without the backup service folds into a SUCCESSFUL
 *     empty snapshot with `supportsBackups = false` (the feature-hidden arm
 *     the backups screen renders), never an error;
 *  3. any other failure passes through untouched;
 *  4. create/restore delegate to the client.
 */
class AdminBackupRepositoryImplTest {

    private lateinit var adminApiClient: AdminApiClient
    private lateinit var repository: AdminBackupRepositoryImpl

    @BeforeTest
    fun setUp() {
        adminApiClient = mockk()
        repository = AdminBackupRepositoryImpl(adminApiClient = adminApiClient)
    }

    @Test
    fun `a successful list becomes a supportsBackups snapshot`() = runTest {
        val backup = ServerBackup(path = "/b/x.zip", serverVersion = "10.11.2")
        coEvery { adminApiClient.listBackups() } returns Result.success(listOf(backup))

        val snapshot = repository.getBackupsSnapshot().getOrThrow()

        assertTrue(snapshot.supportsBackups)
        assertEquals(listOf(backup), snapshot.backups)
    }

    @Test
    fun `the 404 of a backupless server folds into a feature-hidden snapshot`() = runTest {
        coEvery { adminApiClient.listBackups() } returns
            Result.failure(ApiException(isRetryable = false, httpCode = 404, message = "not found"))

        val snapshot = repository.getBackupsSnapshot().getOrThrow()

        assertFalse(snapshot.supportsBackups)
        assertTrue(snapshot.backups.isEmpty())
    }

    @Test
    fun `any other failure passes through`() = runTest {
        val failure = ApiException(isRetryable = false, httpCode = 500, message = "boom")
        coEvery { adminApiClient.listBackups() } returns Result.failure(failure)

        val result = repository.getBackupsSnapshot()

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `createBackup delegates the options and returns the manifest`() = runTest {
        val options = BackupComponentOptions(database = true)
        val manifest = ServerBackup(path = "/b/new.zip")
        coEvery { adminApiClient.createBackup(options) } returns Result.success(manifest)

        assertEquals(manifest, repository.createBackup(options).getOrThrow())
        coVerify(exactly = 1) { adminApiClient.createBackup(options) }
    }

    @Test
    fun `restoreBackup delegates the archive name`() = runTest {
        coEvery { adminApiClient.restoreBackup("jf.zip") } returns Result.success(Unit)

        assertTrue(repository.restoreBackup("jf.zip").isSuccess)
        coVerify(exactly = 1) { adminApiClient.restoreBackup("jf.zip") }
    }
}
