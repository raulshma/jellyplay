package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
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
 * Unit tests for [AutoDownloadScheduler] covering the enable/disable branches
 * and tag association. Uses
 * WorkManager's official test helper so the KEEP-vs-cancel behaviour is
 * verified against the real (in-memory) scheduler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AutoDownloadSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `enqueues tagged periodic work when preference enabled`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = true))
        val preferencesStore = mockk<DownloadsStore>()
        every { preferencesStore.downloads } returns prefsFlow
        val scheduler = AutoDownloadScheduler(context, preferencesStore, this@runTest)

        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(AutoDownloadWorker.WORK_TAG))
    }

    @Test
    fun `cancels periodic work when preference disabled`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadNewEpisodes = true))
        val preferencesStore = mockk<DownloadsStore>()
        every { preferencesStore.downloads } returns prefsFlow
        val scheduler = AutoDownloadScheduler(context, preferencesStore, this@runTest)

        // First, enqueue while enabled.
        scheduler.sync()
        testScheduler.advanceUntilIdle()
        assertEquals(1, workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get().size)

        // Then disable — sync() must cancel the periodic work.
        prefsFlow.value = DownloadsSlice(autoDownloadNewEpisodes = false)
        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
