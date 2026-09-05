package com.raulshma.jellyplay.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import com.raulshma.jellyplay.core.database.entity.ScanStateEntity
import com.raulshma.jellyplay.core.model.AuditItemDetail
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.PlaybackReportingActivity
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.data.util.TimeSource
import java.time.ZoneId

/**
 * Exercises [AdminStatisticsRepositoryImpl]'s real decision logic against a
 * mocked [JellyfinApiClient] and a real in-memory Room database, with the
 * desktop label seam ([DesktopAdminStatisticsLabels], English literals) so the
 * persisted scan rows' formatting is asserted byte-for-byte:
 *  - plugin status refresh + the 90-day audit-log prune;
 *  - the per-user statistics fan-out (played/unplayed counts, completion rate,
 *    active-from-sessions, plugin watch-time overlay);
 *  - the stale-media scan's label formatting ("Added 3d ago", the 1-day and
 *    today boundaries, "Never played", plays suffix) and its async completion;
 *  - the watched-media scan's partial-watch filter and cross-user dedup;
 *  - the audit-log round-trip ([AdminStatisticsRepositoryImpl.removeMediaItems]
 *    → [AdminStatisticsRepositoryImpl.getAuditHistory]).
 */
class AdminStatisticsRepositoryImplTest {

    private lateinit var database: JellyPlayDatabase
    private val apiClient: JellyfinApiClient = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun TestScope.buildRepository(): AdminStatisticsRepositoryImpl = AdminStatisticsRepositoryImpl(
        apiClient = apiClient,
        auditLogDao = database.auditLogDao(),
        scanStateDao = database.scanStateDao(),
        json = json,
        scope = backgroundScope,
        labels = DesktopAdminStatisticsLabels,
        timeSource = FakeTimeSource(),
    )

    private val user = UserInfo(id = "u1", name = "Admin", serverAddress = "http://server", accessToken = "t", isAdmin = true)

    /**
     * The scans run inside the test's [TestScope.backgroundScope], but Room's
     * suspend DAO calls resume on real executor threads the test scheduler
     * cannot advance — so completion is awaited by polling the row instead of
     * advancing virtual time.
     */
    private suspend fun awaitScanFinished(scanId: String) {
        val deadline = System.currentTimeMillis() + 10_000L
        while (System.currentTimeMillis() < deadline) {
            val row = database.scanStateDao().getById(scanId)
            if (row != null && row.status != ScanPhase.SCANNING.name) return
            delay(10)
        }
        error("scan $scanId did not finish within 10s")
    }

    // ── Plugin status + audit-log prune ─────────────────────────────────

    @Test
    fun `refresh sets the plugin status and prunes audit logs older than 90 days`() = runTest {
        val repository = buildRepository()
        val now = System.currentTimeMillis()
        database.auditLogDao().insert(
            MediaAuditLogEntity(
                id = "old", timestamp = now - 100L * 24 * 60 * 60 * 1000, adminUserId = "u1",
                adminUserName = "Admin", actionType = "STALE_REMOVAL", configJson = "{}", itemCount = 1,
                itemDetailsJson = "[]",
            )
        )
        database.auditLogDao().insert(
            MediaAuditLogEntity(
                id = "recent", timestamp = now - 10L * 24 * 60 * 60 * 1000, adminUserId = "u1",
                adminUserName = "Admin", actionType = "STALE_REMOVAL", configJson = "{}", itemCount = 1,
                itemDetailsJson = "[]",
            )
        )
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)

        repository.refreshPlaybackReportingStatus()

        assertEquals(PlaybackReportingStatus.AVAILABLE, repository.getPlaybackReportingStatus().value)
        val remaining = database.auditLogDao().getAll().first()
        assertEquals(listOf("recent"), remaining.map { it.id })
    }

    @Test
    fun `refresh falls back to UNAVAILABLE when the plugin check fails`() = runTest {
        val repository = buildRepository()
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.failure(IllegalStateException("down"))

        repository.refreshPlaybackReportingStatus()

        assertEquals(PlaybackReportingStatus.UNAVAILABLE, repository.getPlaybackReportingStatus().value)
    }

    // ── Per-user statistics fan-out ─────────────────────────────────────

    @Test
    fun `statistics combine play counts, sessions and completion rate without the plugin`() = runTest {
        val repository = buildRepository()
        coEvery { apiClient.getUsers() } returns Result.success(
            listOf(JellyfinUser(id = "u1", name = "Admin", isAdmin = true), JellyfinUser(id = "u2", name = "Viewer"))
        )
        coEvery { apiClient.getSessions() } returns Result.success(listOf(SessionInfo(id = "s1", userId = "u1")))
        coEvery { apiClient.getUserPlayedItemCount("u1", listOf("Movie")) } returns Result.success(6)
        coEvery { apiClient.getUserPlayedItemCount("u1", listOf("Episode")) } returns Result.success(3)
        coEvery { apiClient.getUserPlayedItemCount("u1", listOf("Audio")) } returns Result.success(1)
        coEvery { apiClient.getUserUnplayedItemCount("u1", listOf("Movie")) } returns Result.success(2)
        coEvery { apiClient.getUserPlayedItemCount("u2", any()) } returns Result.success(0)
        coEvery { apiClient.getUserUnplayedItemCount("u2", any()) } returns Result.success(0)

        val stats = repository.getAllUsersWithStatistics().getOrThrow().associateBy { it.userId }

        assertEquals(2, stats.size)
        val admin = stats.getValue("u1")
        assertEquals(10, admin.totalPlayCount)
        assertEquals(6, admin.moviePlayCount)
        assertEquals(3, admin.episodePlayCount)
        assertEquals(1, admin.songPlayCount)
        // movieTotal = 2 unplayed + 6 played = 8 → 6/8.
        assertEquals(0.75f, admin.completionRate)
        assertTrue(admin.isCurrentlyActive)
        assertTrue(admin.isAdmin)
        assertEquals(0L, admin.totalWatchTimeSec)
        // Plugin status was never refreshed → UNKNOWN → no plugin round-trip.
        coVerify(exactly = 0) { apiClient.getPlaybackReportingUserActivity(any()) }

        assertFalse(stats.getValue("u2").isCurrentlyActive)
        assertEquals(0f, stats.getValue("u2").completionRate)
    }

    @Test
    fun `an available plugin overlays per-user watch time onto the statistics`() = runTest {
        val repository = buildRepository()
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)
        repository.refreshPlaybackReportingStatus()
        coEvery { apiClient.getUsers() } returns Result.success(
            listOf(JellyfinUser(id = "u1", name = "Admin"), JellyfinUser(id = "u2", name = "Viewer"))
        )
        coEvery { apiClient.getSessions() } returns Result.success(emptyList())
        coEvery { apiClient.getUserPlayedItemCount(any(), any()) } returns Result.success(0)
        coEvery { apiClient.getUserUnplayedItemCount(any(), any()) } returns Result.success(0)
        coEvery { apiClient.getPlaybackReportingUserActivity(days = 30) } returns Result.success(
            listOf(PlaybackReportingActivity(userId = "u1", totalTime = 7_200L))
        )

        val stats = repository.getAllUsersWithStatistics().getOrThrow().associateBy { it.userId }

        assertEquals(7_200L, stats.getValue("u1").totalWatchTimeSec)
        assertEquals(0L, stats.getValue("u2").totalWatchTimeSec)
    }

    // ── Stale-media scan + label formatting ─────────────────────────────

    @Test
    fun `stale scan bakes desktop label formatting into the persisted result row`() = runTest {
        val repository = buildRepository()
        val today = LocalDate.now()
        val config = MediaCleanupConfig(
            daysThreshold = 90,
            includeNeverPlayed = true,
            includeItemTypes = setOf("Movie"),
            useDateAdded = false,
        )
        coEvery {
            apiClient.getStaleItems(daysThreshold = 90, includeNeverPlayed = true, includeItemTypes = listOf("Movie"), startIndex = 0, limit = 200, useDateAdded = false)
        } returns Result.success(
            Pair(
                4,
                listOf(
                    // Never played, added 3 days ago → "Added 3d ago".
                    StaleMediaItem(itemId = "a", name = "Three Days", type = "Movie", daysSincePlay = 0, playCount = 0, dateAdded = today.minusDays(3).toString()),
                    // Added today.
                    StaleMediaItem(itemId = "b", name = "Today", type = "Movie", daysSincePlay = 0, playCount = 0, dateAdded = today.toString()),
                    // Added exactly 1 day ago (singular).
                    StaleMediaItem(itemId = "c", name = "Yesterday", type = "Movie", daysSincePlay = 0, playCount = 0, dateAdded = today.minusDays(1).toString()),
                    // Played 40 days ago, twice.
                    StaleMediaItem(itemId = "d", name = "Old Play", type = "Movie", daysSincePlay = 40, playCount = 2, lastPlayedDate = "2024-01-01T10:00:00.000Z"),
                ),
            )
        )

        val scanId = repository.detectStaleMedia(config).getOrThrow()
        awaitScanFinished(scanId)

        val row = database.scanStateDao().getById(scanId)!!
        assertEquals(ScanPhase.COMPLETED.name, row.status)
        assertEquals(4, row.itemsFound)
        val stubs = json.decodeFromString(ListSerializer(com.raulshma.jellyplay.core.model.MediaItemStub.serializer()), row.resultJson!!)
            .associateBy { it.itemId }

        assertEquals("Added 3d ago", stubs.getValue("a").dateText)
        assertEquals("Never played", stubs.getValue("a").detail)
        assertEquals("Added today", stubs.getValue("b").dateText)
        assertEquals("Added 1 day ago", stubs.getValue("c").dateText)
        assertEquals("Played 2024-01-01", stubs.getValue("d").dateText)
        assertEquals("40d since play · 2 plays", stubs.getValue("d").detail)
    }

    // ── Watched-media scan: filter + dedup ──────────────────────────────

    @Test
    fun `watched scan drops partially watched items and dedups across users`() = runTest {
        val repository = buildRepository()
        val config = MediaCleanupConfig(
            includeItemTypes = setOf("Movie"),
            includePartiallyWatched = false,
            minDaysSinceWatched = 30,
        )
        coEvery { apiClient.getUsers() } returns Result.success(
            listOf(JellyfinUser(id = "u1", name = "A"), JellyfinUser(id = "u2", name = "B"))
        )
        val shared = WatchedMediaItem(itemId = "dup", name = "Shared", type = "Movie", playCount = 3, completionPct = 1f)
        coEvery {
            apiClient.getWatchedItems(userId = "u1", includeItemTypes = listOf("Movie"), minDaysSincePlayed = 30, keepFavorites = true, startIndex = 0, limit = 200)
        } returns Result.success(
            Pair(
                4,
                listOf(
                    shared,
                    WatchedMediaItem(itemId = "partial", name = "Partial", type = "Movie", playCount = 1, completionPct = 0.5f),
                    // Kept: >= the 0.9 partial-watch cutoff, yet not a full play.
                    WatchedMediaItem(itemId = "nearly", name = "Nearly", type = "Movie", playCount = 1, completionPct = 0.95f),
                ),
            )
        )
        coEvery {
            apiClient.getWatchedItems(userId = "u2", includeItemTypes = listOf("Movie"), minDaysSincePlayed = 30, keepFavorites = true, startIndex = 0, limit = 200)
        } returns Result.success(Pair(1, listOf(shared)))

        val scanId = repository.detectWatchedMedia(config).getOrThrow()
        awaitScanFinished(scanId)

        val row = database.scanStateDao().getById(scanId)!!
        assertEquals(ScanPhase.COMPLETED.name, row.status)
        val stubs = json.decodeFromString(ListSerializer(com.raulshma.jellyplay.core.model.MediaItemStub.serializer()), row.resultJson!!)

        // The partial item is filtered out; the duplicate id is collapsed to a
        // single row (first occurrence wins).
        assertEquals(listOf("dup", "nearly"), stubs.map { it.itemId })
        assertEquals("3 plays", stubs[0].detail)
        assertEquals("1 plays · 95%", stubs[1].detail)
    }

    // ── Audit-log round-trip ────────────────────────────────────────────

    @Test
    fun `removeMediaItems persists an audit entry readable through getAuditHistory`() = runTest {
        val repository = buildRepository()
        every { apiClient.currentUser } returns flowOf(user)
        coEvery { apiClient.deleteItems(listOf("i1", "i2")) } returns Result.success(2)
        val config = MediaCleanupConfig()

        val entry = repository.removeMediaItems(
            itemIds = listOf("i1", "i2"),
            itemNameMap = mapOf("i1" to "Movie One", "i2" to "Movie Two"),
            actionType = CleanupActionType.STALE_REMOVAL,
            config = config,
        ).getOrThrow()

        assertEquals(2, entry.itemCount)
        assertEquals(CleanupActionType.STALE_REMOVAL, entry.actionType)
        assertEquals("u1", entry.adminUserId)
        assertEquals(listOf("Movie One", "Movie Two"), entry.itemDetails.map { it.name })

        val history = repository.getAuditHistory(null).first()
        assertEquals(1, history.size)
        assertEquals(entry.id, history.single().id)
        assertEquals(listOf("Movie One", "Movie Two"), history.single().itemDetails.map { it.name })

        // Filtering by a different action type surfaces nothing.
        assertTrue(repository.getAuditHistory(CleanupActionType.WATCHED_REMOVAL).first().isEmpty())
        assertEquals(1, repository.getAuditHistory(CleanupActionType.STALE_REMOVAL).first().size)
    }

    // ── Scan progress + result JSON ─────────────────────────────────────

    @Test
    fun `scan progress maps the persisted row and result json round-trips`() = runTest {
        val repository = buildRepository()
        database.scanStateDao().insert(
            ScanStateEntity(
                scanId = "scan-1",
                type = "STALE",
                configJson = "{}",
                status = ScanPhase.SCANNING.name,
                progress = 40,
                total = 100,
                itemsFound = 12,
                resultJson = json.encodeToString(ListSerializer(AuditItemDetail.serializer()), emptyList()),
            )
        )

        val progress = repository.getScanProgress("scan-1").first()
        assertEquals(ScanPhase.SCANNING, progress.phase)
        assertEquals(40, progress.scanned)
        assertEquals(100, progress.total)
        assertEquals(12, progress.itemsFound)

        assertEquals(
            json.encodeToString(ListSerializer(AuditItemDetail.serializer()), emptyList()),
            repository.getScanResultJson("scan-1"),
        )
        assertEquals(null, repository.getScanResultJson("missing"))
    }

    /**
     * Controllable [TimeSource] whose default NOW tracks the real wall clock
     * (same shape as the fake in LyricsRepositoryImplTest): the fixtures stamp
     * audit rows with `System.currentTimeMillis()` deltas (100 vs 10 days
     * ago), so the 90-day prune cutoff must compare against a now in the same
     * epoch-millis regime.
     */
    private class FakeTimeSource(var nowMs: Long = System.currentTimeMillis()) : TimeSource {
        override fun nowEpochMillis(): Long = nowMs
        override fun nowElapsedRealtimeMillis(): Long = nowMs
        override fun today(zone: ZoneId): LocalDate = LocalDate.of(2026, 1, 1)
    }
}
