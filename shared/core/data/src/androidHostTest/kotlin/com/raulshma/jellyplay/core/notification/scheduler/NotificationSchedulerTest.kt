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
        // The only periodic carrying the exponential 5-minute backoff — pinned
        // here so the UniqueWorkSchedules fold cannot silently drop it. WorkInfo
        // exposes no backoff accessor and this source set does not see
        // androidx.room (WorkDatabase's supertype), so the enqueued spec's
        // backoffDelayDuration is read reflectively off the test helper's
        // in-memory database.
        val database = workManager.javaClass.getMethod("getWorkDatabase").invoke(workManager)
        // WorkManager 2.11's Room KSP emits the accessor as the Kotlin
        // property form `workSpecDao()` (previously `getWorkSpecDao()`) —
        // accept both so a version bump cannot re-break the pin.
        val dao = database.javaClass.methods
            .first { it.name == "workSpecDao" || it.name == "getWorkSpecDao" }
            .invoke(database)
        val daoMethods = dao.javaClass.methods
        // The unique work name lives in the WorkName join — the WorkSpec's
        // primary key is the generated UUID. Resolve the id by name first
        // (the assertion above already pinned exactly one row), then load
        // the spec by that id.
        val idAndStates = daoMethods
            .first { it.name == "getWorkSpecIdAndStatesForName" }
            .invoke(dao, NotificationScheduler.WORK_NAME) as List<*>
        val only = idAndStates.single()!!
        val spec = daoMethods
            .first { it.name == "getWorkSpec" }
            .invoke(dao, only.javaClass.getField("id").get(only))!!
        assertEquals(
            java.util.concurrent.TimeUnit.MINUTES.toMillis(5),
            spec.javaClass.getField("backoffDelayDuration").getLong(spec),
        )
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

    // ── the *arr accelerator's per-(series, episode) one-shots ─────

    @Test
    fun `series one-shot lands under the derived unique name with the series tag and a delay`() = runTest {
        val preferencesStore = mockk<NotificationStore>()
        every { preferencesStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = NotificationPreferences())
        )
        val scheduler = NotificationScheduler(context, preferencesStore)

        val uniqueName = NotificationScheduler.seriesEpisodeCheckName("series-1", "42:2026-10-20T20:00:00Z")
        scheduler.enqueueSeriesEpisodeCheck(
            seriesId = "series-1",
            episodeKey = "42:2026-10-20T20:00:00Z",
            initialDelay = java.time.Duration.ofMinutes(45),
        )

        val workInfos = workManager.getWorkInfosForUniqueWork(uniqueName).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
        // The one-shots carry the series tag (not the periodic's WORK_TAG) —
        // the worker's retry-cap gate reads it.
        assertTrue(workInfos[0].tags.contains(NotificationScheduler.SERIES_CHECK_WORK_TAG))
        // The accelerator's whole point: the run fires at airTime + buffer,
        // not immediately.
        val database = workManager.javaClass.getMethod("getWorkDatabase").invoke(workManager)
        val dao = database.javaClass.methods
            .first { it.name == "workSpecDao" || it.name == "getWorkSpecDao" }
            .invoke(database)
        val daoMethods = dao.javaClass.methods
        val idAndStates = daoMethods
            .first { it.name == "getWorkSpecIdAndStatesForName" }
            .invoke(dao, uniqueName) as List<*>
        val only = idAndStates.single()!!
        val spec = daoMethods
            .first { it.name == "getWorkSpec" }
            .invoke(dao, only.javaClass.getField("id").get(only))!!
        assertEquals(
            java.util.concurrent.TimeUnit.MINUTES.toMillis(45),
            spec.javaClass.getField("initialDelay").getLong(spec),
        )
    }

    @Test
    fun `series one-shot re-schedule is a KEEP no-op - idempotent per series plus episode`() = runTest {
        val preferencesStore = mockk<NotificationStore>()
        every { preferencesStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = NotificationPreferences())
        )
        val scheduler = NotificationScheduler(context, preferencesStore)

        val uniqueName = NotificationScheduler.seriesEpisodeCheckName("series-1", "42:2026-10-20T20:00:00Z")
        scheduler.enqueueSeriesEpisodeCheck(
            seriesId = "series-1",
            episodeKey = "42:2026-10-20T20:00:00Z",
            initialDelay = java.time.Duration.ofMinutes(45),
        )
        // The poller's next pass re-schedules the same (series, episode) pair.
        scheduler.enqueueSeriesEpisodeCheck(
            seriesId = "series-1",
            episodeKey = "42:2026-10-20T20:00:00Z",
            initialDelay = java.time.Duration.ofMinutes(44),
        )
        // A different episode keys a different unique name — never collapses.
        scheduler.enqueueSeriesEpisodeCheck(
            seriesId = "series-1",
            episodeKey = "42:2026-10-27T20:00:00Z",
            initialDelay = java.time.Duration.ofMinutes(45),
        )

        assertEquals(1, workManager.getWorkInfosForUniqueWork(uniqueName).get().size)
        assertEquals(
            1,
            workManager.getWorkInfosForUniqueWork(
                NotificationScheduler.seriesEpisodeCheckName("series-1", "42:2026-10-27T20:00:00Z"),
            ).get().size,
        )
    }

    @Test
    fun `series one-shot name is deterministic per series plus episode`() {
        assertEquals(
            NotificationScheduler.seriesEpisodeCheckName("s1", "k"),
            NotificationScheduler.seriesEpisodeCheckName("s1", "k"),
        )
        assertTrue(
            NotificationScheduler.seriesEpisodeCheckName("s1", "k1") !=
                NotificationScheduler.seriesEpisodeCheckName("s1", "k2"),
        )
        assertTrue(
            NotificationScheduler.seriesEpisodeCheckName("s1", "k") !=
                NotificationScheduler.seriesEpisodeCheckName("s2", "k"),
        )
    }
}
