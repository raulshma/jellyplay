package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the [SettingsSyncWorker] adapter shape: the worker IS thin — one
 * [ProfileSyncRepository.requestSync] call, always [ListenableWorker.Result.success]
 * (the engine's own gates make a disabled or plugin-absent run a quiet no-op,
 * and a failed cycle surfaces in the sync screen's error row, never as a
 * WorkManager retry loop). Built via [TestListenableWorkerBuilder] with a
 * factory-injected repository, the [PlaybackSyncWorkerResilienceTest] idiom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSyncWorkerTest {

    private lateinit var context: Context
    private val syncRepository: ProfileSyncRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    private fun buildWorker(): SettingsSyncWorker =
        TestListenableWorkerBuilder<SettingsSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): SettingsSyncWorker = SettingsSyncWorker(appContext, workerParameters, syncRepository)
            })
            .build()

    @Test
    fun doWork_requestsOneSync_andReportsSuccess() = runTest {
        val worker = buildWorker()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) { syncRepository.requestSync() }
    }
}
