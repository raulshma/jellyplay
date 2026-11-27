package com.raulshma.jellyplay.core.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.raulshma.jellyplay.core.data.repository.SeriesDownloadPolicyStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import io.mockk.coEvery
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
 * Unit tests for [AutoDownloadSchedulerImpl] covering the enable/disable
 * branches, the [AutoDownloadScheduler.checkNow] one-shot arm, and tag
 * association. Uses
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

    /**
     * The gate reads the disk-backed one-shot [DownloadsStore.readDownloadsOrNull],
     * not the shared `downloads` StateFlow projection — the projection has no
     * happens-before edge against a sync() launched right after a write, so
     * it may still hold the pre-write slice. Both surfaces stay stubbed off
     * the same mutable holder; the stale-projection test below pins why the
     * one-shot read is the one the gate trusts.
     */
    private fun preferencesStore(prefsFlow: MutableStateFlow<DownloadsSlice>): DownloadsStore {
        val store = mockk<DownloadsStore>()
        every { store.downloads } returns prefsFlow
        coEvery { store.readDownloadsOrNull() } coAnswers { prefsFlow.value }
        return store
    }

    @Test
    fun `enqueues tagged periodic work when preference enabled`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT))
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, preferencesStore(prefsFlow), policyStore, this@runTest)

        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(AutoDownloadWorker.WORK_TAG))
    }

    @Test
    fun `cancels periodic work when preference disabled`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT))
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, preferencesStore(prefsFlow), policyStore, this@runTest)

        // First, enqueue while enabled.
        scheduler.sync()
        testScheduler.advanceUntilIdle()
        assertEquals(1, workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get().size)

        // Then disable — sync() must cancel the periodic work.
        prefsFlow.value = DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER)
        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertTrue(workInfos.isEmpty() || workInfos.all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `checkNow enqueues tagged one-shot work under its distinct unique name`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT))
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, preferencesStore(prefsFlow), policyStore, this@runTest)

        scheduler.checkNow()
        testScheduler.advanceUntilIdle()

        val oneShot = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_NOW_NAME).get()
        assertEquals(1, oneShot.size)
        assertTrue(oneShot[0].tags.contains(AutoDownloadWorker.WORK_TAG))
        // The manual trigger must not resurrect the periodic schedule.
        assertEquals(0, workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get().size)
    }

    @Test
    fun `checkNow is a no-op when preference disabled`() = runTest {
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER))
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, preferencesStore(prefsFlow), policyStore, this@runTest)

        scheduler.checkNow()
        testScheduler.advanceUntilIdle()

        assertTrue(workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_NOW_NAME).get().isEmpty())
    }

    @Test
    fun `stays scheduled when autoArmFromArr is on with a global NEVER and zero arms`() = runTest {
        // The gh-225 follow-up keep-alive arm: with the global mode dead and
        // no policy rows at all, the arr auto-arm opt-in alone keeps the
        // periodic pass alive — the next pass may arm rows from nothing.
        val prefsFlow = MutableStateFlow(
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER, autoArmFromArr = true),
        )
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, preferencesStore(prefsFlow), policyStore, this@runTest)

        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(1, workInfos.size)
        assertTrue(workInfos[0].tags.contains(AutoDownloadWorker.WORK_TAG))
    }

    @Test
    fun `a stale downloads projection must not cancel the just-enabled pass`() = runTest {
        // The write-then-sync edge the one-shot read exists for: the eagerly
        // shared `downloads` StateFlow still holds the PRE-toggle slice
        // (autoArmFromArr off, global NEVER, zero arms) when the sync's gate
        // runs, while the persisted snapshot the store reads one-shot already
        // carries the toggle. Reading the projection here would cancel the
        // pass the toggle must schedule; the gate must trust the disk read.
        val staleProjection = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER))
        val store = mockk<DownloadsStore>()
        every { store.downloads } returns staleProjection
        coEvery { store.readDownloadsOrNull() } returns
            DownloadsSlice(autoDownloadMode = AutoDownloadMode.NEVER, autoArmFromArr = true)
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, store, policyStore, this@runTest)

        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(
            "the gate must read the persisted slice, not the lagging projection",
            1,
            workInfos.size,
        )
    }

    @Test
    fun `a transient read failure fails open instead of cancelling the periodic work`() = runTest {
        // The degraded-read edge: the one-shot DataStore read throws (transient
        // IO) while the persisted truth would keep the pass alive. A
        // defaults-shaped answer here would CANCEL persisted WorkManager
        // periodic discovery with no automatic later re-scheduling trigger
        // (the gate's pokes are user actions), so the gate must fail open on
        // the null and leave the schedule standing.
        val prefsFlow = MutableStateFlow(DownloadsSlice(autoDownloadMode = AutoDownloadMode.INSTANT))
        val store = preferencesStore(prefsFlow)
        val policyStore = mockk<SeriesDownloadPolicyStore>(relaxed = true)
        coEvery { policyStore.hasSeriesPolicies() } returns false
        val scheduler = AutoDownloadSchedulerImpl(context, store, policyStore, this@runTest)

        scheduler.sync()
        testScheduler.advanceUntilIdle()
        assertEquals(1, workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get().size)

        coEvery { store.readDownloadsOrNull() } throws java.io.IOException("transient read failure")
        scheduler.sync()
        testScheduler.advanceUntilIdle()

        val workInfos = workManager.getWorkInfosForUniqueWork(AutoDownloadWorker.UNIQUE_PERIODIC_NAME).get()
        assertEquals(
            "a failed read is not 'everything off' — the periodic discovery must survive",
            1,
            workInfos.size,
        )
        // The count alone is blind to the defect shape: a wrong cancelUniqueWork
        // also leaves exactly one record — a CANCELLED tombstone. Assert the
        // state so only live, scheduled work passes.
        assertTrue(
            "the surviving record must be live scheduled work (ENQUEUED), not a cancelled " +
                "tombstone — a single CANCELLED record satisfies the count",
            workInfos.all { it.state == WorkInfo.State.ENQUEUED },
        )
    }
}
