package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [WatchHistoryRepositoryImpl] over a mocked
 * [JellyfinApiClient]. Covers the decision logic, not the transport:
 *  - [HeatmapFilter] → Playback-Reporting filter param + Jellyfin item-type
 *    mapping (VIDEO → "Movie,Episode" / "Movie,Episode,Series", MUSIC →
 *    "Audio", ALL → null);
 *  - the plugin-vs-fallback branch: available plugin returns play-activity
 *    points directly; unavailable plugin (or empty plugin payload) falls back
 *    to per-day aggregation over the user's played items;
 *  - the year-window pagination stop in [WatchHistoryRepositoryImpl.getPlayedItems].
 */
class WatchHistoryRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk()

    private lateinit var repository: WatchHistoryRepositoryImpl

    private val user = UserInfo(
        id = "u1",
        name = "User",
        serverAddress = "http://server",
        accessToken = "token",
    )

    @BeforeTest
    fun setup() {
        repository = WatchHistoryRepositoryImpl(apiClient)
        coEvery { apiClient.currentUser } returns flowOf(user)
    }

    private fun item(
        id: String,
        lastPlayedDate: String?,
        playCount: Int = 1,
        mediaType: MediaType = MediaType.MOVIE,
        name: String = "Item $id",
    ) = MediaItem(id = id, name = name, mediaType = mediaType, playCount = playCount, lastPlayedDate = lastPlayedDate)

    private suspend fun makePluginAvailable() {
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)
        repository.refreshPlaybackReportingStatus()
        assertEquals(PlaybackReportingStatus.AVAILABLE, repository.playbackReportingStatus.value)
    }

    // ── Plugin status ───────────────────────────────────────────────────

    @Test
    fun `refresh marks AVAILABLE when the plugin check succeeds`() = runTest {
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.success(PlaybackReportingStatus.AVAILABLE)

        repository.refreshPlaybackReportingStatus()

        assertEquals(PlaybackReportingStatus.AVAILABLE, repository.playbackReportingStatus.value)
    }

    @Test
    fun `refresh marks UNAVAILABLE when the plugin check fails`() = runTest {
        coEvery { apiClient.checkPlaybackReportingPlugin() } returns Result.failure(IllegalStateException("down"))

        repository.refreshPlaybackReportingStatus()

        assertEquals(PlaybackReportingStatus.UNAVAILABLE, repository.playbackReportingStatus.value)
    }

    // ── HeatmapFilter → API param mapping (plugin path) ─────────────────

    @Test
    fun `plugin path maps filters and returns the activity points verbatim`() = runTest {
        makePluginAvailable()
        val points = listOf(PlaybackActivityPoint(date = "2024-03-01", value = 4))
        coEvery {
            apiClient.getPlaybackReportingPlayActivity(days = any(), dataType = "count", filter = any())
        } returns Result.success(points)

        // Past year → the deterministic 365-day window.
        val all = repository.getDailyActivity(year = 2024, filter = HeatmapFilter.ALL)
        coVerify(exactly = 1) { apiClient.getPlaybackReportingPlayActivity(days = 365, dataType = "count", filter = null) }
        assertEquals(listOf(DailyWatchActivity(date = "2024-03-01", value = 4)), all)

        repository.getDailyActivity(year = 2024, filter = HeatmapFilter.VIDEO)
        coVerify(exactly = 1) { apiClient.getPlaybackReportingPlayActivity(days = 365, dataType = "count", filter = "Movie,Episode") }

        repository.getDailyActivity(year = 2024, filter = HeatmapFilter.MUSIC)
        coVerify(exactly = 1) { apiClient.getPlaybackReportingPlayActivity(days = 365, dataType = "count", filter = "Audio") }
    }

    @Test
    fun `plugin path with a failing play-activity call degrades to the fallback`() = runTest {
        makePluginAvailable()
        coEvery { apiClient.getPlaybackReportingPlayActivity(any(), any(), any()) } returns Result.failure(RuntimeException("boom"))
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(Pair(1, listOf(item("i1", lastPlayedDate = "2024-03-01T10:00:00.000Z", playCount = 2))))

        val activity = repository.getDailyActivity(year = 2024, filter = HeatmapFilter.ALL)

        assertEquals(listOf(DailyWatchActivity(date = "2024-03-01", value = 2)), activity)
    }

    // ── Fallback daily aggregation ──────────────────────────────────────

    @Test
    fun `fallback aggregates played items per day, sorted by date`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = listOf("Movie", "Episode", "Series"), isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(
            Pair(
                3,
                listOf(
                    item("a", lastPlayedDate = "2024-01-01T20:00:00.000Z", playCount = 2),
                    item("b", lastPlayedDate = "2024-01-01T21:00:00.000Z", playCount = 0), // coerceAtLeast(1)
                    item("c", lastPlayedDate = "2024-01-02T08:00:00.000Z", playCount = 5),
                ),
            )
        )

        val activity = repository.getDailyActivity(year = 2024, filter = HeatmapFilter.VIDEO)

        assertEquals(
            listOf(
                DailyWatchActivity(date = "2024-01-01", value = 3),
                DailyWatchActivity(date = "2024-01-02", value = 5),
            ),
            activity,
        )
    }

    @Test
    fun `fallback skips items without a last played date`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(Pair(2, listOf(item("a", lastPlayedDate = null), item("b", lastPlayedDate = "2024-05-05T00:00:00.000Z"))))

        val activity = repository.getDailyActivity(year = 2024, filter = HeatmapFilter.ALL)

        assertEquals(listOf(DailyWatchActivity(date = "2024-05-05", value = 1)), activity)
    }

    // ── getPlayedItems year window + pagination ─────────────────────────

    @Test
    fun `played items outside the target year are excluded and stop the paging`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(
            Pair(
                3,
                listOf(
                    item("new", lastPlayedDate = "2024-06-01T00:00:00.000Z"),
                    item("old", lastPlayedDate = "2023-12-31T00:00:00.000Z"),
                    item("newer", lastPlayedDate = "2024-05-01T00:00:00.000Z"),
                ),
            )
        )

        val items = repository.getPlayedItems(year = 2024, filter = HeatmapFilter.ALL)

        // The 2023 item stops the scan after this page (descending DatePlayed
        // order ⇒ no later page can hold a 2024 play); the rest of this page's
        // in-year items still count.
        assertEquals(listOf("new", "newer"), items.map { it.id })
        coVerify(exactly = 1) { apiClient.getItemsWithUserData(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `played items paginate until the batch is no longer full`() = runTest {
        val page1 = (1..200).map { item("p1-%03d".format(it), lastPlayedDate = "2024-01-01T00:00:00.000Z") }
        val page2 = (1..200).map { item("p2-%03d".format(it), lastPlayedDate = "2024-01-02T00:00:00.000Z") }
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(Pair(400, page1))
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 200, limit = 200)
        } returns Result.success(Pair(400, page2))

        val items = repository.getPlayedItems(year = 2024, filter = HeatmapFilter.ALL)

        assertEquals(400, items.size)
        coVerify(exactly = 1) {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 200, limit = 200)
        }
    }

    @Test
    fun `played items with an unparseable year are skipped`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = listOf("Audio"), isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(
            Pair(2, listOf(item("bad", lastPlayedDate = "not-a-date"), item("good", lastPlayedDate = "2024-02-02T00:00:00.000Z", mediaType = MediaType.AUDIO))),
        )

        val items = repository.getPlayedItems(year = 2024, filter = HeatmapFilter.MUSIC)

        assertEquals(listOf("good"), items.map { it.id })
    }

    @Test
    fun `played items returns empty when signed out`() = runTest {
        coEvery { apiClient.currentUser } returns flowOf(null)

        assertTrue(repository.getPlayedItems(year = 2024, filter = HeatmapFilter.ALL).isEmpty())
    }

    // ── getItemsForDay ──────────────────────────────────────────────────

    @Test
    fun `plugin path returns the reported details verbatim`() = runTest {
        makePluginAvailable()
        val details = listOf(PlaybackReportingDetail(time = "20:15", itemId = "i1", name = "Movie", type = "Movie", client = "Web", method = "DirectPlay", device = "PC", duration = 1000L))
        coEvery { apiClient.getPlaybackReportingUserItems(userId = "u1", date = "2024-03-01", filter = "Movie,Episode") } returns Result.success(details)

        assertEquals(details, repository.getItemsForDay("2024-03-01", HeatmapFilter.VIDEO))
    }

    @Test
    fun `fallback maps the day's played items with unknown client fidelity`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(
            Pair(
                3,
                listOf(
                    item("a", lastPlayedDate = "2024-03-01T20:15:00.000Z"),
                    item("b", lastPlayedDate = "2024-03-01T09:05:00", mediaType = MediaType.EPISODE),
                    item("c", lastPlayedDate = "2024-03-02T09:05:00.000Z"),
                ),
            )
        )

        val details = repository.getItemsForDay("2024-03-01", HeatmapFilter.ALL)

        assertEquals(listOf("a", "b"), details.map { it.itemId })
        assertEquals("20:15", details[0].time)
        assertEquals("09:05", details[1].time)
        // The fallback can't know client/device/method — it must not fabricate.
        assertTrue(details.all { it.client == "Unknown" && it.device == "Unknown" && it.method == "Unknown" && it.duration == 0L })
        assertEquals("MOVIE", details[0].type)
        assertEquals("EPISODE", details[1].type)
    }

    @Test
    fun `fallback with an unparseable timestamp yields an empty time`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Descending", startIndex = 0, limit = 200)
        } returns Result.success(Pair(1, listOf(item("a", lastPlayedDate = "2024-03-01 garbage"))))

        val details = repository.getItemsForDay("2024-03-01", HeatmapFilter.ALL)

        assertEquals("", details.single().time)
    }

    @Test
    fun `items for day returns empty when signed out`() = runTest {
        coEvery { apiClient.currentUser } returns flowOf(null)

        assertTrue(repository.getItemsForDay("2024-03-01", HeatmapFilter.ALL).isEmpty())
    }

    // ── getMinimumActivityDate ──────────────────────────────────────────

    @Test
    fun `minimum activity date returns the oldest played item's date`() = runTest {
        coEvery {
            apiClient.getItemsWithUserData(userId = "u1", includeItemTypes = null, isPlayed = true, sortBy = "DatePlayed", sortOrder = "Ascending", startIndex = 0, limit = 1)
        } returns Result.success(Pair(1, listOf(item("first", lastPlayedDate = "2021-01-01T00:00:00.000Z"))))

        assertEquals("2021-01-01T00:00:00.000Z", repository.getMinimumActivityDate())
    }

    @Test
    fun `minimum activity date is null when signed out or with no played items`() = runTest {
        coEvery { apiClient.currentUser } returns flowOf(null)
        assertNull(repository.getMinimumActivityDate())

        coEvery { apiClient.currentUser } returns flowOf(user)
        coEvery {
            apiClient.getItemsWithUserData(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(Pair(0, emptyList()))
        assertNull(repository.getMinimumActivityDate())
    }
}
