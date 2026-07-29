package com.raulshma.jellyplay.startup

import android.content.Context
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.ReconciliationRow
import com.raulshma.jellyplay.core.database.entity.DownloadEntity
import com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer
import com.raulshma.jellyplay.core.model.DownloadStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Verifies the cold-start reconciliation pass: a `COMPLETED` download whose file
 * is missing or truncated must be reset to `PENDING` (so it re-downloads and the
 * offline library self-heals), while a completed row with a full file or an
 * unverifiable legacy row (unknown size) must be left alone. Also covers the
 * stuck-downloads cleanup pass resetting a `FAILED` row's byte offset to 0 when
 * its partial is deleted, so a later resume can't `Range:` against a gapped/
 * missing file.
 *
 * The pending-recovery pass is neutralised by returning empty query results so
 * `WorkManager.getInstance` (unavailable in a plain JVM unit test) is never
 * reached; only the reconciliation and cleanup passes run.
 */
class DownloadRecoveryInitializerTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val context: Context = mockk()
    private val downloadDao: DownloadDao = mockk(relaxed = true)
    private val downloadEnqueuer: DownloadEnqueuer = mockk(relaxed = true)

    private fun initializer() = DownloadRecoveryInitializer(context, downloadDao, downloadEnqueuer)

    private fun stubRecoveryQueriesEmpty() {
        // Neutralise recoverPendingDownloads() and cleanupStuckDownloads() so
        // recover() effectively only exercises reconciliation. Empty result
        // lists mean the loops over them never reach WorkManager.getInstance.
        coEvery { downloadDao.getRecoveryRows(any()) } returns emptyList()
        coEvery { downloadDao.getFailedDownloads() } returns emptyList()
    }

    @Test
    fun `completed download with a missing file is reset to PENDING`() = runTest {
        val missingPath = File(tempFolder.root, "gone.mp4").absolutePath
        coEvery { downloadDao.getCompletedForReconciliation() } returns listOf(
            ReconciliationRow(id = "dl-1", downloadPath = missingPath, totalSizeBytes = 1_000L),
        )
        stubRecoveryQueriesEmpty()

        initializer().recover()

        coVerify { downloadDao.updateProgress("dl-1", 0L, DownloadStatus.PENDING.name) }
    }

    @Test
    fun `completed download with a truncated file is deleted and reset to PENDING`() = runTest {
        val partial = tempFolder.newFile("partial.mp4").apply {
            // Write 100 bytes; expected total is 1_000 → truncated.
            writeBytes(ByteArray(100))
        }
        coEvery { downloadDao.getCompletedForReconciliation() } returns listOf(
            ReconciliationRow(id = "dl-2", downloadPath = partial.absolutePath, totalSizeBytes = 1_000L),
        )
        stubRecoveryQueriesEmpty()

        initializer().recover()

        assertTrue("Truncated partial file should be deleted", !partial.exists())
        coVerify { downloadDao.updateProgress("dl-2", 0L, DownloadStatus.PENDING.name) }
    }

    @Test
    fun `completed download with a full-size file is left alone`() = runTest {
        val full = tempFolder.newFile("full.mp4").apply { writeBytes(ByteArray(1_000)) }
        coEvery { downloadDao.getCompletedForReconciliation() } returns listOf(
            ReconciliationRow(id = "dl-3", downloadPath = full.absolutePath, totalSizeBytes = 1_000L),
        )
        stubRecoveryQueriesEmpty()

        initializer().recover()

        assertTrue("Full file should not be deleted", full.exists())
        coVerify(exactly = 0) { downloadDao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `completed download with unknown size and an existing file is left alone`() = runTest {
        // Legacy rows pre-dating size tracking cannot be verified; resetting
        // them would force needless re-downloads of files that are fine.
        val legacy = tempFolder.newFile("legacy.mp4").apply { writeBytes(ByteArray(500)) }
        coEvery { downloadDao.getCompletedForReconciliation() } returns listOf(
            ReconciliationRow(id = "dl-4", downloadPath = legacy.absolutePath, totalSizeBytes = 0L),
        )
        stubRecoveryQueriesEmpty()

        initializer().recover()

        assertTrue("Unverifiable legacy file should not be deleted", legacy.exists())
        coVerify(exactly = 0) { downloadDao.updateProgress(any(), any(), any()) }
    }

    @Test
    fun `failed download with a partial file deletes it and zeroes its byte offset`() = runTest {
        // A FAILED multi-connection partial (scattered RandomAccessFile writes)
        // can't be appended to, so cleanup deletes the file. The byte offset
        // must also be reset to 0 — otherwise a later resume sends
        // `Range: bytes=N-` against the now-deleted file and corrupts the output.
        val partial = tempFolder.newFile("partial.mp4").apply { writeBytes(ByteArray(100)) }
        coEvery { downloadDao.getCompletedForReconciliation() } returns emptyList()
        coEvery { downloadDao.getRecoveryRows(any()) } returns emptyList()
        coEvery { downloadDao.getFailedDownloads() } returns listOf(
            DownloadEntity(
                id = "dl-5",
                mediaItemId = "item-5",
                name = "Failed",
                mediaType = "MOVIE",
                downloadPath = partial.absolutePath,
                downloadUrl = "https://u",
                totalSizeBytes = 1_000L,
                downloadedBytes = 100L,
                status = DownloadStatus.FAILED.name,
            ),
        )

        initializer().recover()

        assertTrue("FAILED partial file should be deleted", !partial.exists())
        coVerify { downloadDao.updateProgress("dl-5", 0L, DownloadStatus.FAILED.name) }
    }
}
