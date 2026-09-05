package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.repository.MediaCacheInvalidator
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
 * Pins the [UserDataSyncWorker] gating ladder + refresh contract:
 *
 *  - The worker is a **no-op success** when `userDataSyncEnabled` is off, or
 *    when no user has ever signed in (`activeUserId` null/blank) — and in each
 *    of those cases it must NOT touch the repositories.
 *  - An enabled run invalidates the in-memory caches first, then re-fetches
 *    exactly CONTINUE_WATCHING + NEXT_UP (the user-data-bearing home rows).
 *  - A refresh failure escalates `retry` → `failure` after MAX_RETRIES (3) so
 *    persistent server problems stay observable in WorkManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDataSyncWorkerTest {

    private lateinit var context: Context
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val cacheInvalidator: MediaCacheInvalidator = mockk(relaxed = true)
    private val playbackStore: PlaybackStore = mockk()
    private val serverIdentityStore: ServerIdentityStore = mockk()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice(userDataSyncEnabled = true))
        every { serverIdentityStore.activeUserId } returns MutableStateFlow("user-1")
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = emptyList()))
    }

    private fun buildWorker(runAttemptCount: Int = 0): UserDataSyncWorker =
        TestListenableWorkerBuilder<UserDataSyncWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): UserDataSyncWorker = UserDataSyncWorker(
                    appContext,
                    workerParameters,
                    mediaRepository,
                    cacheInvalidator,
                    playbackStore,
                    serverIdentityStore,
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()

    // ── Gating ladder: no-op successes ────────────────────────────────
    // (The `playback.firstOrNull() == null` guard is unreachable through a
    // StateFlow-typed store — a StateFlow always emits its default — so the
    // disabled / no-user gates below are the observable no-op paths.)

    @Test
    fun `disabled preference short-circuits success without cache invalidation`() = runTest {
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice(userDataSyncEnabled = false))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { cacheInvalidator.invalidateCaches() }
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    @Test
    fun `no active user short-circuits success`() = runTest {
        every { serverIdentityStore.activeUserId } returns MutableStateFlow(null)

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { cacheInvalidator.invalidateCaches() }
    }

    @Test
    fun `blank active user short-circuits success`() = runTest {
        every { serverIdentityStore.activeUserId } returns MutableStateFlow("  ")

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    // ── Enabled run: invalidate then refresh ──────────────────────────

    @Test
    fun `enabled run invalidates caches then refetches continue-watching and next-up`() = runTest {
        val querySlot = slot<HomeSectionQuery>()

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerifyOrder {
            cacheInvalidator.invalidateCaches()
            mediaRepository.getHomeSections(capture(querySlot), any())
        }
        assertEquals(setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP), querySlot.captured.enabledSections)
    }

    // ── Retry escalation ──────────────────────────────────────────────

    @Test
    fun `home sections failure on an early attempt returns retry`() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        val result = buildWorker(runAttemptCount = 0).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `home sections failure after exhausting retries returns failure`() = runTest {
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        // MAX_RETRIES is 3: attempt 3 is past the retry budget.
        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
