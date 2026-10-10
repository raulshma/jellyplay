package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
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
 * Verifies [SettingsSyncSchedulerImpl] enqueues the 12h periodic catch-up and
 * the immediate one-shot flush with the correct unique-work names and tags,
 * and that the periodic arm is gated on the sync engine's own enabled edge
 * (a user who never opted in pins no periodic run). KEEP policy means
 * repeated calls do not create duplicate runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsSyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SettingsSyncSchedulerImpl
    private val enabled = MutableStateFlow(ProfileSyncRepository.SyncState(enabled = false))
    private val syncRepository: ProfileSyncRepository = mockk(relaxed = true) {
        every { state } returns enabled
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = SettingsSyncSchedulerImpl(context, syncRepository)
    }

    @Test
    fun `enqueueNow creates tagged one-shot work`() {
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(SettingsSyncWorker.WORK_TAG))
    }

    @Test
    fun `enqueueNow is idempotent under KEEP policy`() {
        scheduler.enqueueNow()
        scheduler.enqueueNow()

        val workInfos = workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, workInfos.size)
    }

    @Test
    fun `enqueuePeriodicIfEnabled pins no periodic run while sync is disabled`() {
        scheduler.enqueuePeriodicIfEnabled()

        assertEquals(
            0,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get().size,
        )
    }

    @Test
    fun `enqueuePeriodicIfEnabled arms the tagged periodic once the engine is enabled`() {
        enabled.value = ProfileSyncRepository.SyncState(enabled = true)

        scheduler.enqueuePeriodicIfEnabled()

        val workInfos = workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(SettingsSyncWorker.WORK_TAG))
    }

    @Test
    fun `periodic and one-shot use distinct unique names`() {
        enabled.value = ProfileSyncRepository.SyncState(enabled = true)
        scheduler.enqueuePeriodicIfEnabled()
        scheduler.enqueueNow()

        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get().size,
        )
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_NOW_NAME).get().size,
        )
    }

    @Test
    fun `cancelPeriodic de-arms the periodic and a re-enable re-arms it`() {
        enabled.value = ProfileSyncRepository.SyncState(enabled = true)
        scheduler.enqueuePeriodicIfEnabled()
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get()
                .count { it.state == WorkInfo.State.ENQUEUED },
        )

        // Cancel marks the WorkInfo CANCELLED (the record stays listed, the
        // run never fires) — the de-arm is the absence of live work.
        scheduler.cancelPeriodic()
        assertEquals(
            0,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get()
                .count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING },
        )

        // A CANCELLED record is terminal, so KEEP arms a fresh periodic.
        scheduler.enqueuePeriodicIfEnabled()
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(SettingsSyncWorker.UNIQUE_PERIODIC_NAME).get()
                .count { it.state == WorkInfo.State.ENQUEUED },
        )
    }
}
