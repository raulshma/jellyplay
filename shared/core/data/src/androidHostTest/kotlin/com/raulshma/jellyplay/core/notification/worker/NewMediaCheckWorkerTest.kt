package com.raulshma.jellyplay.core.notification.worker

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Pins the main `doWork` flow of [NewMediaCheckWorker] (the quiet-hours table
 * itself lives in [NewMediaCheckWorkerQuietHoursTest]):
 *
 * - Disabled preferences cancel the periodic schedule and never touch the server.
 * - A missing POST_NOTIFICATIONS grant (API 33+) aborts before any fetch.
 * - A non-first scan fetches per enabled folder (concurrently, each capped at
 *   `maxPerCheck`), records the genuinely-new items as seen, dispatches only
 *   folders that produced new items, prunes 30-day-old seen rows, and
 *   reconciles seen rows against the live item ids.
 * - The first scan records everything but dispatches nothing (anti-spam).
 * - A folder with notifications disabled is skipped entirely; a folder whose
 *   media-type filter matches nothing contributes nothing.
 * - IO failures surface as `Result.retry()`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NewMediaCheckWorkerTest {

    private val mediaRepository: MediaRepository = mockk()
    private val seenMediaRepository: SeenMediaRepository = mockk(relaxed = true)
    private val notificationStore: NotificationStore = mockk()
    private val dispatcher: NotificationDispatcher = mockk(relaxed = true)
    private val scheduler: NotificationScheduler = mockk(relaxed = true)

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun enabledPrefs(
        maxPerCheck: Int = 10,
        libraryConfigs: Map<String, LibraryNotificationConfig> = emptyMap(),
    ) = NotificationPreferences(
        enabled = true,
        maxPerCheck = maxPerCheck,
        libraryConfigs = libraryConfigs,
    )

    private fun stubPreferences(prefs: NotificationPreferences) {
        every { notificationStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = prefs),
        )
    }

    private fun grantPostNotifications(granted: Boolean) {
        val shadow = shadowOf(context as Application)
        if (granted) {
            shadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            shadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun worker(): NewMediaCheckWorker =
        TestListenableWorkerBuilder<NewMediaCheckWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = NewMediaCheckWorker(
                        appContext,
                        workerParameters,
                        mediaRepository = mediaRepository,
                        seenMediaRepository = seenMediaRepository,
                        notificationStore = notificationStore,
                        dispatcher = dispatcher,
                        scheduler = scheduler,
                    )
                },
            )
            .build()

    private fun item(id: String, mediaType: MediaType = MediaType.MOVIE) = MediaItem(
        id = id,
        name = "Item $id",
        mediaType = mediaType,
    )

    private fun stubFolders(vararg folders: LibraryFolder) {
        coEvery { mediaRepository.getLibraryFolders(any()) } returns Result.success(folders.toList())
    }

    private fun stubLatest(folderId: String, items: List<MediaItem>) {
        coEvery {
            mediaRepository.getLatestMedia(parentId = folderId, limit = any())
        } returns Result.success(items)
    }

    @Before
    fun setUp() {
        grantPostNotifications(granted = true)
    }

    @Test
    fun `disabled preferences cancel the periodic schedule and skip the scan`() = runTest {
        stubPreferences(NotificationPreferences(enabled = false))
        stubFolders(LibraryFolder(id = "f1", name = "Movies"))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 1) { scheduler.cancel() }
        coVerify(exactly = 0) { mediaRepository.getLibraryFolders(any()) }
    }

    @Test
    fun `missing POST_NOTIFICATIONS permission skips the scan`() = runTest {
        grantPostNotifications(granted = false)
        stubPreferences(enabledPrefs())
        stubFolders(LibraryFolder(id = "f1", name = "Movies"))

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 0) { scheduler.cancel() }
        coVerify(exactly = 0) { mediaRepository.getLibraryFolders(any()) }
    }

    @Test
    fun `non-first scan records and dispatches only genuinely-new items`() = runTest {
        stubPreferences(enabledPrefs())
        val folder = LibraryFolder(id = "f1", name = "Movies")
        stubFolders(folder)
        stubLatest("f1", listOf(item("m1"), item("m2")))
        coEvery { seenMediaRepository.count() } returns 1
        coEvery { seenMediaRepository.getSeenIds(any()) } returns setOf("m1")

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The fetch honours the per-check cap.
        coVerify(exactly = 1) { mediaRepository.getLatestMedia(parentId = "f1", limit = 10) }
        // Only the unseen item is recorded as seen, keyed to its folder.
        // (The seen-at timestamp is stamped from the wall clock at write time,
        // so it is matched field-wise rather than by equality.)
        coVerify(exactly = 1) {
            seenMediaRepository.markAsSeen(
                records = match { records ->
                    val written = records.toList()
                    written.size == 1 &&
                        written[0].itemId == "m2" &&
                        written[0].libraryId == "f1" &&
                        written[0].mediaType == "MOVIE"
                },
            )
        }
        // Dispatch carries only the folder → new-items pair.
        verify(exactly = 1) {
            dispatcher.dispatch(
                match<Map<LibraryFolder, List<MediaItem>>> { map ->
                    map.keys.single().id == "f1" && map.values.single().map { it.id } == listOf("m2")
                },
                any(),
            )
        }
        // Housekeeping: 30-day prune + orphan reconcile against the live ids.
        coVerify(exactly = 1) { seenMediaRepository.pruneOlderThan(any()) }
        coVerify(exactly = 1) { seenMediaRepository.reconcileAgainstLiveItemIds(setOf("m1", "m2")) }
    }

    @Test
    fun `first scan records everything but dispatches nothing`() = runTest {
        stubPreferences(enabledPrefs())
        stubFolders(LibraryFolder(id = "f1", name = "Movies"))
        stubLatest("f1", listOf(item("m1"), item("m2")))
        coEvery { seenMediaRepository.count() } returns 0
        coEvery { seenMediaRepository.getSeenIds(any()) } returns emptySet()

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 1) {
            seenMediaRepository.markAsSeen(
                records = match { it.toList().size == 2 },
            )
        }
        verify(exactly = 0) { dispatcher.dispatch(any(), any()) }
        // Nothing was tracked before this scan, so reconcile must not run
        // (it would misread the pre-scan emptiness as mass deletion).
        coVerify(exactly = 0) { seenMediaRepository.reconcileAgainstLiveItemIds(any()) }
    }

    @Test
    fun `empty folder list completes without per-folder fetches or dispatch`() = runTest {
        stubPreferences(enabledPrefs())
        stubFolders()

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getLatestMedia(parentId = any(), limit = any()) }
        verify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `folder with notifications disabled is skipped - enabled folder still checked`() = runTest {
        stubPreferences(
            enabledPrefs(
                libraryConfigs = mapOf(
                    "f1" to LibraryNotificationConfig(enabled = false),
                ),
            ),
        )
        stubFolders(LibraryFolder(id = "f1", name = "Off"), LibraryFolder(id = "f2", name = "On"))
        stubLatest("f2", listOf(item("m1")))
        coEvery { seenMediaRepository.count() } returns 1
        coEvery { seenMediaRepository.getSeenIds(any()) } returns emptySet()

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { mediaRepository.getLatestMedia(parentId = "f1", limit = any()) }
        coVerify(exactly = 1) { mediaRepository.getLatestMedia(parentId = "f2", limit = any()) }
        verify(exactly = 1) {
            dispatcher.dispatch(
                match<Map<LibraryFolder, List<MediaItem>>> { map -> map.keys.single().id == "f2" },
                any(),
            )
        }
    }

    @Test
    fun `media type filter drops items outside the configured types`() = runTest {
        stubPreferences(
            enabledPrefs(
                libraryConfigs = mapOf(
                    "f1" to LibraryNotificationConfig(enabled = true, mediaTypes = setOf("MOVIE")),
                ),
            ),
        )
        stubFolders(LibraryFolder(id = "f1", name = "Movies"))
        stubLatest("f1", listOf(item("movie1"), item("ep1", MediaType.EPISODE)))
        coEvery { seenMediaRepository.count() } returns 1
        coEvery { seenMediaRepository.getSeenIds(any()) } returns emptySet()

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // Only the surviving (type-filtered) ids are checked against seen rows.
        coVerify(exactly = 1) { seenMediaRepository.getSeenIds(listOf("movie1")) }
        verify(exactly = 1) {
            dispatcher.dispatch(
                match<Map<LibraryFolder, List<MediaItem>>> { map ->
                    map.values.single().map { it.id } == listOf("movie1")
                },
                any(),
            )
        }
        // Reconcile sees the filtered live set only.
        coVerify(exactly = 1) { seenMediaRepository.reconcileAgainstLiveItemIds(setOf("movie1")) }
    }

    @Test
    fun `folders fetch returning nothing new across all folders dispatches nothing`() = runTest {
        stubPreferences(enabledPrefs())
        stubFolders(LibraryFolder(id = "f1", name = "Movies"))
        stubLatest("f1", emptyList())
        coEvery { seenMediaRepository.count() } returns 1

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `IO error during the folders fetch retries`() = runTest {
        stubPreferences(enabledPrefs())
        coEvery { mediaRepository.getLibraryFolders(any()) } throws IOException("server unreachable")

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        verify(exactly = 0) { dispatcher.dispatch(any(), any()) }
    }

    @Test
    fun `unexpected error during the folders fetch fails without crashing`() = runTest {
        stubPreferences(enabledPrefs())
        coEvery { mediaRepository.getLibraryFolders(any()) } throws IllegalStateException("bad state")

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }
}
