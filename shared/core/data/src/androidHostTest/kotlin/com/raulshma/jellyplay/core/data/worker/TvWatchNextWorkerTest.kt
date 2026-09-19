package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins the [TvWatchNextWorker] shell — the worker owns only preference gating
 * and [androidx.work.ListenableWorker.Result] mapping; the publishing logic
 * itself lives in [com.raulshma.jellyplay.core.data.tv.TvWatchNextPublisher]
 * (covered by `TvWatchNextPublisherTest`):
 *
 *  - `androidTvWatchNextEnabled = false` → the publisher **clears** the row
 *    (never publishes) and the worker succeeds.
 *  - Enabled: the publisher publishes from a CONTINUE_WATCHING + NEXT_UP home
 *    query; a publish failure escalates `retry` → `failure` after MAX_RETRIES
 *    (3). TV-ness comes from the leanback system feature — on phones publish/
 *    clear are framework no-ops that still succeed, so the disabled-clearing
 *    path is asserted on both device classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvWatchNextWorkerTest {

    private lateinit var context: Context
    private val mediaRepository: MediaRepository = mockk(relaxed = true)
    private val playbackRepository: PlaybackRepository = mockk(relaxed = true)
    private val playbackStore: PlaybackStore = mockk()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        every { playbackStore.playback } returns
            MutableStateFlow(PlaybackSlice(androidTvWatchNextEnabled = true))
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.success(HomeSectionsResult(sections = emptyList()))
    }

    private fun buildWorker(runAttemptCount: Int = 0): TvWatchNextWorker =
        TestListenableWorkerBuilder<TvWatchNextWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): TvWatchNextWorker = TvWatchNextWorker(
                    appContext,
                    workerParameters,
                    mediaRepository,
                    playbackRepository,
                    playbackStore,
                )
            })
            .setRunAttemptCount(runAttemptCount)
            .build()

    private fun makeDeviceTv() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    // ── Gating ────────────────────────────────────────────────────────

    @Test
    fun `disabled preference clears the row and succeeds without publishing on a phone`() = runTest {
        every { playbackStore.playback } returns
            MutableStateFlow(PlaybackSlice(androidTvWatchNextEnabled = false))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    @Test
    fun `disabled preference clears the row and succeeds on a TV`() = runTest {
        makeDeviceTv()
        every { playbackStore.playback } returns
            MutableStateFlow(PlaybackSlice(androidTvWatchNextEnabled = false))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    // ── Enabled: publish + result mapping ─────────────────────────────

    @Test
    fun `enabled run publishes from the continue-watching + next-up query and succeeds on a TV`() = runTest {
        makeDeviceTv()

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            mediaRepository.getHomeSections(
                HomeSectionQuery(
                    enabledSections = setOf(HomeSectionType.CONTINUE_WATCHING, HomeSectionType.NEXT_UP),
                ),
                any(),
            )
        }
    }

    @Test
    fun `enabled run on a phone is a successful no-op publish`() = runTest {
        // No leanback feature: the publisher short-circuits before the
        // repository read; the worker still reports success.
        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getHomeSections(any(), any()) }
    }

    @Test
    fun `publish failure on an early attempt returns retry`() = runTest {
        makeDeviceTv()
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        val result = buildWorker(runAttemptCount = 0).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `publish failure after exhausting retries returns failure`() = runTest {
        makeDeviceTv()
        coEvery { mediaRepository.getHomeSections(any(), any()) } returns
            Result.failure(RuntimeException("server unreachable"))

        // MAX_RETRIES is 3: attempt 3 is past the retry budget.
        val result = buildWorker(runAttemptCount = 3).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
