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
 * Verifies [PlaybackSyncSchedulerImpl] enqueues both the periodic backstop and
 * the immediate one-shot drain with the correct unique-work names and tags.
 * KEEP policy means repeated calls do not create duplicate runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlaybackSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: PlaybackSyncSchedulerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = PlaybackSyncSchedulerImpl(context)
    }

    @Test
    fun `enqueuePeriodic creates tagged periodic work`() {
        scheduler.enqueuePeriodic()

        val workInfos = workManager.getWorkInfosForUniqueWork(PlaybackSyncWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(PlaybackSyncWorker.WORK_TAG))
    }

    @Test
    fun `enqueueNow creates tagged one-shot work`() {
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(PlaybackSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(PlaybackSyncWorker.WORK_TAG))
    }

    @Test
    fun `enqueueNow is idempotent under KEEP policy`() {
        scheduler.enqueueNow()
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(PlaybackSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `enqueuePeriodic and enqueueNow use distinct unique names`() {
        scheduler.enqueuePeriodic()
        scheduler.enqueueNow()

        val periodic = workManager.getWorkInfosForUniqueWork(PlaybackSyncWorker.UNIQUE_PERIODIC_NAME).get()
        val oneShot = workManager.getWorkInfosForUniqueWork(PlaybackSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, periodic.size)
        assertEquals(1, oneShot.size)
    }
}
