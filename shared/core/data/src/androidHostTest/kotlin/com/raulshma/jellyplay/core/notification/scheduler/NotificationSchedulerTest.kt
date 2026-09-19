package com.raulshma.jellyplay.core.notification.scheduler

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.NotificationPreferences
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
 * Unit tests for [NotificationScheduler] covering the enable/disable branches.
 * Uses WorkManager's official test
 * helper so the KEEP-vs-cancel behaviour is verified against the real
 * (in-memory) scheduler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NotificationSchedulerTest {

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
    fun `scheduleOrUpdate enqueues tagged periodic work when enabled`() = runTest {
        val preferencesStore = mockk<NotificationStore>()
        every { preferencesStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = NotificationPreferences(enabled = true))
        )
        val scheduler = NotificationScheduler(context, preferencesStore)

        scheduler.scheduleOrUpdate()

        val workInfos = workManager.getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(com.raulshma.jellyplay.core.notification.worker.NewMediaCheckWorker.WORK_TAG))
    }

    @Test
    fun `scheduleOrUpdate cancels work when disabled`() = runTest {
        val preferencesStore = mockk<NotificationStore>()
        every { preferencesStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = NotificationPreferences(enabled = true))
        )
        val scheduler = NotificationScheduler(context, preferencesStore)

        // Enqueue while enabled.
        scheduler.scheduleOrUpdate()
        assertEquals(1, workManager.getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get().size)

        // Disable — scheduleOrUpdate() must cancel the work.
        every { preferencesStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = NotificationPreferences(enabled = false))
        )
        scheduler.scheduleOrUpdate()

        val workInfos = workManager.getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
