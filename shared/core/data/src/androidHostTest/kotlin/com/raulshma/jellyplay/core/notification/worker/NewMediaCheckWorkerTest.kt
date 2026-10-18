package com.raulshma.jellyplay.core.notification.worker

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeriesNotificationReads
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrMediaType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LibraryNotificationConfig
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NotificationPreferences
import com.raulshma.jellyplay.core.model.SeriesNotificationConfig
import com.raulshma.jellyplay.core.model.SystemTimeSource
import com.raulshma.jellyplay.core.model.TimeSource
import com.raulshma.jellyplay.core.notification.dispatcher.NotificationDispatcher
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    private val seriesNotificationReads: SeriesNotificationReads = mockk()
    private val seenMediaRepository: SeenMediaRepository = mockk(relaxed = true)
    private val notificationStore: NotificationStore = mockk()
    private val dispatcher: NotificationDispatcher = mockk(relaxed = true)
    private val scheduler: NotificationScheduler = mockk(relaxed = true)
    private val arrRepository: ArrRepository = mockk(relaxed = true)

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** The fixed clock the accelerator tests pin the one-shot delay against. */
    private val fixedNowEpochMs = 1_760_000_000_000L

    private fun enabledPrefs(
        maxPerCheck: Int = 10,
        libraryConfigs: Map<String, LibraryNotificationConfig> = emptyMap(),
        newEpisodesEnabled: Boolean = false,
        seriesConfigs: Map<String, SeriesNotificationConfig> = emptyMap(),
    ) = NotificationPreferences(
        enabled = true,
        maxPerCheck = maxPerCheck,
        libraryConfigs = libraryConfigs,
        newEpisodesEnabled = newEpisodesEnabled,
        seriesConfigs = seriesConfigs,
    )

    private fun stubPreferences(prefs: NotificationPreferences) {
        every { notificationStore.notification } returns MutableStateFlow(
            NotificationSlice(notificationPreferences = prefs),
        )
        // The series pass writes back through the RMW command — stub it here
        // so runs that never assert the write do not crash on the strict
        // store mock; the diff tests re-stub with a capture slot afterwards
        // (the later stub wins).
        coEvery { notificationStore.updateSeriesNotificationConfigs(any()) } returns Unit
    }

    private fun grantPostNotifications(granted: Boolean) {
        val shadow = shadowOf(context as Application)
        if (granted) {
            shadow.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            shadow.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun worker(timeSource: TimeSource = SystemTimeSource()): NewMediaCheckWorker =
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
                        seriesNotificationReads = seriesNotificationReads,
                        seenMediaRepository = seenMediaRepository,
                        notificationStore = notificationStore,
                        dispatcher = dispatcher,
                        scheduler = scheduler,
                        timeSource = timeSource,
                        arrRepository = arrRepository,
                    )
                },
            )
            .build()

    /** The fake clock pinned to [fixedNowEpochMs], for the accelerator delay math. */
    private fun fixedClock() = object : TimeSource {
        override fun nowEpochMillis(): Long = fixedNowEpochMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 10, 12)
        override fun nowElapsedRealtimeMillis(): Long = 0L
    }

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

    // ── D12: the per-series second pass ─────────────────────────────────

    private fun seriesPrefs(config: SeriesNotificationConfig) = enabledPrefs(
        newEpisodesEnabled = true,
        seriesConfigs = mapOf("s1" to config),
    )

    /**
     * Stubs the per-series newest-episode read. Also stubs the tvdb lookup
     * as failed so the accelerator (which every check re-schedules) degrades
     * to a no-op unless the test re-stubs [MediaRepository.getMediaDetail]
     * with a tvdb-carrying detail (the accelerator test does — the later
     * stub wins).
     */
    private fun stubSeriesEpisode(id: String) {
        coEvery {
            seriesNotificationReads.getSeriesNextUpEpisode("s1")
        } returns Result.success(item(id, MediaType.EPISODE))
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.failure(IOException("no arr configured"))
    }

    /** Captures the store RMW transform so the test can apply it to the input map. */

    @Test
    fun `series pass skipped entirely when the new-episodes master switch is off`() = runTest {
        stubPreferences(
            NotificationPreferences(
                enabled = true,
                newEpisodesEnabled = false,
                seriesConfigs = mapOf("s1" to SeriesNotificationConfig()),
            ),
        )
        stubFolders()

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { seriesNotificationReads.getSeriesNextUpEpisode(any()) }
        coVerify(exactly = 0) { notificationStore.updateSeriesNotificationConfigs(any()) }
        verify(exactly = 0) { dispatcher.dispatchSeriesNewEpisode(any(), any(), any()) }
    }

    @Test
    fun `series disabled opt-in is skipped - enabled one checked`() = runTest {
        stubPreferences(
            enabledPrefs(
                newEpisodesEnabled = true,
                seriesConfigs = mapOf(
                    "s-off" to SeriesNotificationConfig(enabled = false),
                    "s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1"),
                ),
            ),
        )
        stubFolders()
        stubSeriesEpisode("ep1")

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { seriesNotificationReads.getSeriesNextUpEpisode("s-off") }
        coVerify(exactly = 1) { seriesNotificationReads.getSeriesNextUpEpisode("s1") }
    }

    @Test
    fun `first check after opt-in records the baseline without notifying`() = runTest {
        stubPreferences(seriesPrefs(SeriesNotificationConfig()))
        stubFolders()
        stubSeriesEpisode("ep1")
        val transform = slot<(Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>>()
        coEvery { notificationStore.updateSeriesNotificationConfigs(capture(transform)) } returns Unit

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // The anti-spam baseline: newest id recorded, nothing dispatched.
        val written = transform.captured(mapOf("s1" to SeriesNotificationConfig()))
        assertEquals("ep1", written.getValue("s1").lastNotifiedEpisodeId)
        verify(exactly = 0) { dispatcher.dispatchSeriesNewEpisode(any(), any(), any()) }
    }

    @Test
    fun `newer episode than the baseline dispatches on the new-episodes channel and writes back`() = runTest {
        stubPreferences(seriesPrefs(SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        stubFolders()
        stubSeriesEpisode("ep2")
        val transform = slot<(Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>>()
        coEvery { notificationStore.updateSeriesNotificationConfigs(capture(transform)) } returns Unit

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 1) {
            dispatcher.dispatchSeriesNewEpisode(
                seriesId = "s1",
                episode = match { it.id == "ep2" },
                prefs = any(),
            )
        }
        val written = transform.captured(mapOf("s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        assertEquals("ep2", written.getValue("s1").lastNotifiedEpisodeId)
    }

    @Test
    fun `same newest episode as the baseline neither dispatches nor rewrites the id`() = runTest {
        stubPreferences(seriesPrefs(SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        stubFolders()
        stubSeriesEpisode("ep1")
        val transform = slot<(Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>>()
        coEvery { notificationStore.updateSeriesNotificationConfigs(capture(transform)) } returns Unit

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 0) { dispatcher.dispatchSeriesNewEpisode(any(), any(), any()) }
        val written = transform.captured(mapOf("s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        assertEquals("ep1", written.getValue("s1").lastNotifiedEpisodeId)
    }

    @Test
    fun `multi-series pass merges every series diff into ONE store write`() = runTest {
        stubPreferences(
            enabledPrefs(
                newEpisodesEnabled = true,
                seriesConfigs = mapOf(
                    "s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1"),
                    "s2" to SeriesNotificationConfig(),
                ),
            ),
        )
        stubFolders()
        coEvery {
            seriesNotificationReads.getSeriesNextUpEpisode("s1")
        } returns Result.success(item("ep2", MediaType.EPISODE))
        coEvery {
            seriesNotificationReads.getSeriesNextUpEpisode("s2")
        } returns Result.success(item("ep9", MediaType.EPISODE))
        coEvery { mediaRepository.getMediaDetail(any()) } returns Result.failure(IOException("no arr configured"))
        val transform = slot<(Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>>()
        coEvery { notificationStore.updateSeriesNotificationConfigs(capture(transform)) } returns Unit

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        // ONE merged RMW edit for the whole pass — not one DataStore write
        // per series (each of which re-ran the preference slice projections).
        coVerify(exactly = 1) { notificationStore.updateSeriesNotificationConfigs(any()) }
        val written = transform.captured(
            mapOf(
                "s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1"),
                "s2" to SeriesNotificationConfig(),
            ),
        )
        assertEquals("ep2", written.getValue("s1").lastNotifiedEpisodeId)
        assertEquals("ep9", written.getValue("s2").lastNotifiedEpisodeId)
        // Only the genuinely-new series dispatches; the first check after
        // opt-in (s2) baselines silently.
        verify(exactly = 1) {
            dispatcher.dispatchSeriesNewEpisode(seriesId = "s1", episode = any(), prefs = any())
        }
    }

    @Test
    fun `failed newest read for one series drops only its write - sibling still merges`() = runTest {
        stubPreferences(
            enabledPrefs(
                newEpisodesEnabled = true,
                seriesConfigs = mapOf(
                    "s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1", lastCheckAt = 123L),
                    "s2" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep8"),
                ),
            ),
        )
        stubFolders()
        coEvery { seriesNotificationReads.getSeriesNextUpEpisode("s1") } returns Result.failure(IOException())
        coEvery {
            seriesNotificationReads.getSeriesNextUpEpisode("s2")
        } returns Result.success(item("ep9", MediaType.EPISODE))
        coEvery { mediaRepository.getMediaDetail(any()) } returns Result.failure(IOException("no arr configured"))
        val transform = slot<(Map<String, SeriesNotificationConfig>) -> Map<String, SeriesNotificationConfig>>()
        coEvery { notificationStore.updateSeriesNotificationConfigs(capture(transform)) } returns Unit

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val written = transform.captured(
            mapOf(
                "s1" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1", lastCheckAt = 123L),
                "s2" to SeriesNotificationConfig(lastNotifiedEpisodeId = "ep8"),
            ),
        )
        // The RMW transform carries the full config map, so the failed-read
        // series rides through UNTOUCHED — no diff branch ran for it (any of
        // them would have stamped lastCheckAt with the fixed clock's now) —
        // while s2's diff lands.
        assertEquals(123L, written.getValue("s1").lastCheckAt)
        assertEquals("ep1", written.getValue("s1").lastNotifiedEpisodeId)
        assertEquals("ep9", written.getValue("s2").lastNotifiedEpisodeId)
        // s2's ep8 → ep9 is genuinely newer, so only it dispatches; the
        // failed-read s1 never reaches the dispatcher.
        verify(exactly = 1) {
            dispatcher.dispatchSeriesNewEpisode(seriesId = "s2", episode = match { it.id == "ep9" }, prefs = any())
        }
    }

    @Test
    fun `arr accelerator schedules one delayed one-shot per upcoming sonarr episode`() = runTest {
        stubPreferences(seriesPrefs(SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        stubFolders()
        stubSeriesEpisode("ep1")
        val airStamp = "2026-10-15T20:00:00Z"
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.success(
            MediaDetail(item = item("s1", MediaType.SERIES), providerIds = mapOf("tvdb" to "42")),
        )
        coEvery { arrRepository.refreshCalendar(any(), any()) } returns Result.success(Unit)
        coEvery { arrRepository.calendar(any(), any()) } returns flowOf(
            listOf(
                ArrCalendarItem(tvdbId = 42, mediaType = ArrMediaType.SERIES, title = "E5", airDateUtc = airStamp),
                // A Radarr (movie) row and another series' row must not match.
                ArrCalendarItem(tvdbId = 99, mediaType = ArrMediaType.SERIES, title = "Other", airDateUtc = airStamp),
                ArrCalendarItem(tvdbId = 42, mediaType = ArrMediaType.MOVIE, title = "Film", airDateUtc = airStamp),
            ),
        )

        val result = worker(fixedClock()).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val expectedDelay = Duration.ofMillis(
            Instant.parse(airStamp).toEpochMilli() + 30L * 60 * 1000 - fixedNowEpochMs,
        )
        verify(exactly = 1) {
            scheduler.enqueueSeriesEpisodeCheck("s1", "42:$airStamp", expectedDelay)
        }
    }

    @Test
    fun `series fetch failure still succeeds - the accelerator is best-effort`() = runTest {
        stubPreferences(seriesPrefs(SeriesNotificationConfig(lastNotifiedEpisodeId = "ep1")))
        stubFolders()
        coEvery { seriesNotificationReads.getSeriesNextUpEpisode("s1") } returns Result.failure(IOException())
        coEvery { mediaRepository.getMediaDetail("s1") } returns Result.failure(IOException())

        val result = worker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        verify(exactly = 0) { dispatcher.dispatchSeriesNewEpisode(any(), any(), any()) }
        verify(exactly = 0) { scheduler.enqueueSeriesEpisodeCheck(any(), any(), any()) }
    }
}
