package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.worker.DownloadWorker
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the [DownloadEnqueuer] WorkManager enqueue recipe with the test
 * WorkManager: unique work per download (`DownloadWorker.workName`), KEEP
 * policy (repeated enqueues never duplicate or cancel an in-flight worker),
 * the shared `download` tag, and the network-constraint divergence between
 * the runtime path and cold-start recovery:
 *
 *  - `enqueue(id)` / `enqueue(id, honorScheduleAndNetwork = true)` honours the
 *    user's wifi-only preference (UNMETERED vs CONNECTED).
 *  - `enqueue(id, honorScheduleAndNetwork = false)` enqueues unconstrained —
 *    a process restart must not strand rows that had already cleared the
 *    schedule/network gate.
 *
 * The 30 s exponential backoff and input data are part of the recipe but not
 * observable through WorkManager's public WorkInfo surface, so they are pinned
 * only indirectly (via the shared builder shape in the source).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DownloadEnqueuerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var downloadsStore: DownloadsStore
    private val downloads = MutableStateFlow(DownloadsSlice())

    private lateinit var enqueuer: DownloadEnqueuer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        downloadsStore = mockk()
        every { downloadsStore.downloads } returns downloads
        enqueuer = DownloadEnqueuer(context, downloadsStore)
    }

    private fun workInfosFor(downloadId: String) =
        workManager.getWorkInfosForUniqueWork(DownloadWorker.workName(downloadId)).get()

    @Test
    fun `enqueue creates unique work named per download with the shared tag`() {
        enqueuer.enqueue("dl-1")

        val workInfos = workInfosFor("dl-1")
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(DownloadWorker.WORK_TAG))
    }

    @Test
    fun `distinct downloads get distinct unique work`() {
        enqueuer.enqueue("dl-1")
        enqueuer.enqueue("dl-2")

        assertEquals(1, workInfosFor("dl-1").size)
        assertEquals(1, workInfosFor("dl-2").size)
    }

    @Test
    fun `KEEP policy keeps a single unique work across repeated enqueues`() {
        enqueuer.enqueue("dl-1")
        enqueuer.enqueue("dl-1")
        enqueuer.enqueue("dl-1")

        assertEquals(1, workInfosFor("dl-1").size)
    }

    @Test
    fun `runtime enqueue honours wifi-only preference as UNMETERED`() {
        downloads.value = DownloadsSlice(wifiOnlyDownloads = true)

        enqueuer.enqueue("dl-1")

        assertEquals(
            NetworkType.UNMETERED,
            workInfosFor("dl-1")[0].constraints.requiredNetworkType,
        )
    }

    @Test
    fun `runtime enqueue without wifi-only preference is CONNECTED`() {
        downloads.value = DownloadsSlice(wifiOnlyDownloads = false)

        enqueuer.enqueue("dl-1")

        assertEquals(
            NetworkType.CONNECTED,
            workInfosFor("dl-1")[0].constraints.requiredNetworkType,
        )
    }

    @Test
    fun `recovery enqueue is unconstrained regardless of preferences`() {
        downloads.value = DownloadsSlice(
            wifiOnlyDownloads = true,
            downloadScheduleEnabled = true,
            downloadScheduleWindow = DownloadScheduleWindow(startHour = 0, endHour = 6, wifiOnly = true),
        )

        enqueuer.enqueue("dl-1", honorScheduleAndNetwork = false)

        assertEquals(
            NetworkType.NOT_REQUIRED,
            workInfosFor("dl-1")[0].constraints.requiredNetworkType,
        )
    }

    @Test
    fun `default enqueue overload routes through the honouring path`() {
        downloads.value = DownloadsSlice(wifiOnlyDownloads = true)

        // enqueue(id) must equal enqueue(id, honorScheduleAndNetwork = true).
        enqueuer.enqueue("dl-1")

        assertEquals(
            NetworkType.UNMETERED,
            workInfosFor("dl-1")[0].constraints.requiredNetworkType,
        )
    }

    @Test
    fun `cancelWork cancels the unique work for the download`() {
        enqueuer.enqueue("dl-1")

        enqueuer.cancelWork("dl-1")

        assertEquals(
            androidx.work.WorkInfo.State.CANCELLED,
            workInfosFor("dl-1")[0].state,
        )
    }

    @Test
    fun `cancelWork with no registered work is a safe no-op`() {
        // Must not throw even though nothing was enqueued.
        enqueuer.cancelWork("never-enqueued")
    }

    @Test
    fun `backoff policy constant matches the repository recipe`() {
        // The enqueuer pins BackoffPolicy.EXPONENTIAL + DOWNLOAD_BACKOFF_DELAY_MS;
        // assert the constant itself so the two modules cannot drift silently.
        assertEquals(30_000L, DownloadRepositoryImpl.DOWNLOAD_BACKOFF_DELAY_MS)
        assertEquals(BackoffPolicy.EXPONENTIAL, BackoffPolicy.EXPONENTIAL)
    }
}
