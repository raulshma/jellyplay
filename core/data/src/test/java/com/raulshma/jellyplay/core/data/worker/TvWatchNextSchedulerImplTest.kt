package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [TvWatchNextSchedulerImpl] covering the enqueue + tag
 * association branches (startup-and-workers-architecture §7.13). Uses
 * WorkManager's official test helper so the one-shot enqueue is verified
 * against the real (in-memory) scheduler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TvWatchNextSchedulerImplTest {

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
    fun `scheduleRefresh enqueues tagged one-time work`() {
        val scheduler = TvWatchNextSchedulerImpl(context)

        scheduler.scheduleRefresh()

        val workInfos = workManager.getWorkInfosForUniqueWork(TvWatchNextWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(TvWatchNextWorker.WORK_TAG))
    }

    @Test
    fun `scheduleRefresh replaces existing work on re-schedule`() {
        val scheduler = TvWatchNextSchedulerImpl(context)

        scheduler.scheduleRefresh()
        scheduler.scheduleRefresh()

        // REPLACE policy keeps a single unique work entry, not two.
        val workInfos = workManager.getWorkInfosForUniqueWork(TvWatchNextWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
    }
}
