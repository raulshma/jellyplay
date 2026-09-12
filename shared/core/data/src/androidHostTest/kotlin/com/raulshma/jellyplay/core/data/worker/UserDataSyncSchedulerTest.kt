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
 * Verifies [UserDataSyncSchedulerImpl] enqueues both the 12h periodic backstop
 * and the immediate one-shot refresh (used after a playback-outbox drain) with
 * the correct unique-work names and tags. KEEP policy means repeated calls do
 * not create duplicate runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UserDataSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: UserDataSyncSchedulerImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = UserDataSyncSchedulerImpl(context)
    }

    @Test
    fun `enqueuePeriodic creates tagged periodic work`() {
        scheduler.enqueuePeriodic()

        val workInfos = workManager.getWorkInfosForUniqueWork(UserDataSyncWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(UserDataSyncWorker.WORK_TAG))
    }

    @Test
    fun `enqueueNow creates tagged one-shot work`() {
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(UserDataSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(UserDataSyncWorker.WORK_TAG))
    }

    @Test
    fun `enqueueNow is idempotent under KEEP policy`() {
        scheduler.enqueueNow()
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(UserDataSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `enqueuePeriodic and enqueueNow use distinct unique names`() {
        scheduler.enqueuePeriodic()
        scheduler.enqueueNow()

        val periodic = workManager.getWorkInfosForUniqueWork(UserDataSyncWorker.UNIQUE_PERIODIC_NAME).get()
        val oneShot = workManager.getWorkInfosForUniqueWork(UserDataSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, periodic.size)
        assertEquals(1, oneShot.size)
    }
}
