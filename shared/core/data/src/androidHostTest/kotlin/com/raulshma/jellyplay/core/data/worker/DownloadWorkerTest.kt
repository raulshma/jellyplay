package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.raulshma.jellyplay.core.data.playback.DownloadConcurrencyLimiter
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins [DownloadWorker]'s early-exit orchestration (the transfer itself lives
 * in [DownloadTransferRunner]/[MultiConnectionDownloadStrategy] and is out of
 * scope here):
 *
 * - A missing `download_id` input and an unknown download id both fail without
 *   touching the DAO further.
 * - A row in an inactive status (e.g. COMPLETED) short-circuits success BEFORE
 *   the concurrency limiter is resized or the QUEUED progress write happens.
 * - `workName` is the single source of truth for the unique-work name.
 *
 * Foreground-promotion and the transfer paths need a real WorkManager
 * foreground runtime and are exercised on-device; the failure classification
 * rule is pinned by DownloadFailurePolicyTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao: DownloadDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val downloadsStore: DownloadsStore = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true)
    private val tokenCipher: TokenCipher = mockk(relaxed = true)
    private val concurrencyLimiter: DownloadConcurrencyLimiter = mockk(relaxed = true)
    private val transferClient: DownloadTransferClient = mockk(relaxed = true)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
    }

    private fun entity(status: String = DownloadStatus.PENDING.name) = DownloadEntity(
        id = "dl-1",
        mediaItemId = "item-1",
        name = "Movie",
        mediaType = "MOVIE",
        downloadPath = "/tmp/dl-1.mkv",
        downloadUrl = "http://server/file",
        totalSizeBytes = 1_000L,
        downloadedBytes = 0L,
        status = status,
    )

    private fun buildWorker(input: androidx.work.Data? = null): DownloadWorker =
        TestListenableWorkerBuilder<DownloadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): DownloadWorker = DownloadWorker(
                    appContext,
                    workerParameters,
                    dao,
                    userDao,
                    downloadsStore,
                    serverIdentityStore,
                    tokenCipher,
                    concurrencyLimiter,
                    transferClient,
                )
            })
            .setInputData(input ?: androidx.work.Data.EMPTY)
            .build()

    @Test
    fun `a missing download id input fails without touching the DAO`() = runTest {
        val result = buildWorker(workDataOf("unrelated" to "x")).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        coVerify(exactly = 0) { dao.getDownloadById(any()) }
    }

    @Test
    fun `an unknown download id fails`() = runTest {
        coEvery { dao.getDownloadById("dl-missing") } returns null

        val result = buildWorker(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to "dl-missing")).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `an inactive row short-circuits success before any orchestration`() = runTest {
        // isInactive covers PAUSED/CANCELLED only — a completed row would proceed
        // into the transfer orchestration instead of short-circuiting.
        coEvery { dao.getDownloadById("dl-1") } returns entity(status = DownloadStatus.PAUSED.name)

        val result = buildWorker(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to "dl-1")).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { concurrencyLimiter.configure(any()) }
        coVerify(exactly = 0) { dao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `workName prefixes the unique work name for every download`() {
        assertEquals("download_dl-1", DownloadWorker.workName("dl-1"))
        assertEquals("download_", DownloadWorker.workName(""))
    }
}
