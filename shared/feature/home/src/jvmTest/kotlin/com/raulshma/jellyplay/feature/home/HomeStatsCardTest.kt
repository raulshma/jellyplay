package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.ViewingStreak
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test

/**
 * H17 stats card: the pure aggregation math plus the loader's admin /
 * non-admin gating. The loader is the only place the card's fetches live, so
 * the gating contract (admin-only richer stats, silent failure, probe-once)
 * is pinned end-to-end here against MockK client doubles.
 */
class HomeStatsCardTest {

    // ── Pure math ─────────────────────────────────────────────────────────

    @Test
    fun approximateWatchMinutes_sumsRuntimesIntoWholeMinutes() {
        val minutes = HomeStatsMath.approximateWatchMinutes(
            listOf(
                600_000_000L, // 1 minute
                1_200_000_000L, // 2 minutes
            ),
        )
        assertEquals(3L, minutes)
    }

    @Test
    fun approximateWatchMinutes_nullRuntimesContributeZero_andFractionFloors() {
        // 30s + null + 30s = one floored minute of 1:00 total.
        val minutes = HomeStatsMath.approximateWatchMinutes(
            listOf(300_000_000L, null, 300_000_000L),
        )
        assertEquals(1L, minutes)
        assertEquals(0L, HomeStatsMath.approximateWatchMinutes(emptyList()))
        assertEquals(0L, HomeStatsMath.approximateWatchMinutes(listOf(null, null)))
    }

    @Test
    fun watchHours_convertsSeconds() {
        assertEquals(2.0, HomeStatsMath.watchHours(7_200L))
        assertEquals(0.0, HomeStatsMath.watchHours(0L))
    }

    @Test
    fun statsUpgradeAllowed_requiresAdminAndAvailablePlugin() {
        assertFalse(HomeStatsMath.statsUpgradeAllowed(isAdmin = false, pluginStatus = PlaybackReportingStatus.AVAILABLE))
        assertFalse(HomeStatsMath.statsUpgradeAllowed(isAdmin = true, pluginStatus = PlaybackReportingStatus.UNAVAILABLE))
        assertFalse(HomeStatsMath.statsUpgradeAllowed(isAdmin = true, pluginStatus = PlaybackReportingStatus.UNKNOWN))
        assertEquals(
            true,
            HomeStatsMath.statsUpgradeAllowed(isAdmin = true, pluginStatus = PlaybackReportingStatus.AVAILABLE),
        )
    }

    // ── Loader gating ─────────────────────────────────────────────────────

    private val mediaInfoApiClient: MediaInfoApiClient = mockk(relaxed = true)
    private val adminStatisticsRepository: AdminStatisticsRepository = mockk(relaxed = true)
    private val pluginStatusFlow = MutableStateFlow(PlaybackReportingStatus.UNAVAILABLE)

    private fun buildLoader() = HomeStatsCardLoader(
        mediaInfoApiClient = mediaInfoApiClient,
        adminStatisticsRepository = adminStatisticsRepository,
    )

    private fun stubCounts(played: Int = 12, unplayed: Int = 34, runtimeMinutes: Long = 90L) {
        coEvery { mediaInfoApiClient.getUserPlayedItemCount(any()) } returns Result.success(played)
        coEvery { mediaInfoApiClient.getUserUnplayedItemCount(any()) } returns Result.success(unplayed)
        coEvery {
            mediaInfoApiClient.getItemsWithUserData(any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(
            // 90 minutes of runtime on the single fetched page.
            played to listOf(
                com.raulshma.jellyplay.core.model.MediaItem(
                    id = "m1",
                    name = "Movie",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.MOVIE,
                    runTimeTicks = runtimeMinutes * HomeStatsMath.TICKS_PER_MINUTE,
                ),
            ),
        )
    }

    @Test
    fun nonAdmin_loadsBaseCounts_withoutTouchingAdminRepository() = runTest {
        stubCounts(played = 12, unplayed = 34, runtimeMinutes = 90L)

        val data = buildLoader().load(userId = "u1", isAdmin = false)

        assertEquals(12, data?.playedCount)
        assertEquals(34, data?.unplayedCount)
        assertEquals(90L, data?.approximateWatchMinutes)
        // The v1 card is exact-free: no admin fields populated, and the
        // admin repository was never consulted for a non-admin.
        assertNull(data?.exactWatchHours)
        assertNull(data?.currentStreakDays)
        assertFalse(data!!.adminEnhanced)
        coVerify(exactly = 0) { adminStatisticsRepository.getUserDetailStatistics(any(), any(), any()) }
        // Non-suspend flow accessor — plain verify.
        verify(exactly = 0) { adminStatisticsRepository.getPlaybackReportingStatus() }
    }

    @Test
    fun playedCountFailure_hidesTheCardSilently() = runTest {
        coEvery { mediaInfoApiClient.getUserPlayedItemCount(any()) } returns Result.failure(IllegalStateException("offline"))
        coEvery { mediaInfoApiClient.getUserUnplayedItemCount(any()) } returns Result.success(34)
        // The three base legs now fire concurrently, so the runtime leg
        // runs even though the played count fails and hides the card — stub
        // it explicitly (the relaxed mock's bare Result can't serve .map).
        coEvery { mediaInfoApiClient.getItemsWithUserData(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(0 to emptyList())

        val data = buildLoader().load(userId = "u1", isAdmin = false)

        assertNull(data)
    }

    @Test
    fun adminWithAvailablePlugin_upgradesWithRealHoursAndStreak() = runTest {
        stubCounts()
        pluginStatusFlow.value = PlaybackReportingStatus.AVAILABLE
        every { adminStatisticsRepository.getPlaybackReportingStatus() } returns pluginStatusFlow
        coEvery { adminStatisticsRepository.getUserDetailStatistics("u1", any(), any()) } returns Result.success(
            UserDetailPage(
                statistics = UserStatistics(totalWatchTimeSec = 7_200L),
                viewingStreak = ViewingStreak(currentStreak = 5),
            ),
        )

        val data = buildLoader().load(userId = "u1", isAdmin = true)

        assertEquals(2.0, data?.exactWatchHours)
        assertEquals(5, data?.currentStreakDays)
        assertEquals(true, data?.adminEnhanced)
    }

    @Test
    fun adminWithoutPlugin_keepsTheApproximateCardOnly() = runTest {
        stubCounts()
        pluginStatusFlow.value = PlaybackReportingStatus.UNAVAILABLE
        every { adminStatisticsRepository.getPlaybackReportingStatus() } returns pluginStatusFlow

        val data = buildLoader().load(userId = "u1", isAdmin = true)

        assertNull(data?.exactWatchHours)
        assertNull(data?.currentStreakDays)
        // The plugin-gated detail fetch must not fire for an unavailable
        // plugin — the degrade-silently contract.
        coVerify(exactly = 0) { adminStatisticsRepository.getUserDetailStatistics(any(), any(), any()) }
    }

    @Test
    fun adminWithUnknownPlugin_refreshesTheProbeOnce_thenUpgrades() = runTest {
        stubCounts()
        pluginStatusFlow.value = PlaybackReportingStatus.UNKNOWN
        every { adminStatisticsRepository.getPlaybackReportingStatus() } returns pluginStatusFlow
        // The refresh publishes the definitive answer into the same flow.
        coEvery { adminStatisticsRepository.refreshPlaybackReportingStatus() } answers {
            pluginStatusFlow.value = PlaybackReportingStatus.AVAILABLE
        }
        coEvery { adminStatisticsRepository.getUserDetailStatistics("u1", any(), any()) } returns Result.success(
            UserDetailPage(statistics = UserStatistics(totalWatchTimeSec = 3_600L)),
        )

        val data = buildLoader().load(userId = "u1", isAdmin = true)

        coVerify(exactly = 1) { adminStatisticsRepository.refreshPlaybackReportingStatus() }
        assertEquals(1.0, data?.exactWatchHours)
    }

    @Test
    fun adminDetailStatsFailure_degradesToTheBaseCard() = runTest {
        stubCounts()
        pluginStatusFlow.value = PlaybackReportingStatus.AVAILABLE
        every { adminStatisticsRepository.getPlaybackReportingStatus() } returns pluginStatusFlow
        coEvery { adminStatisticsRepository.getUserDetailStatistics("u1", any(), any()) } returns
            Result.failure(IllegalStateException("plugin 500"))

        val data = buildLoader().load(userId = "u1", isAdmin = true)

        // The base triplet survives; only the upgrade fields stay empty.
        assertEquals(12, data?.playedCount)
        assertNull(data?.exactWatchHours)
        assertNull(data?.currentStreakDays)
    }

    @Test
    fun runtimeQuery_isCappedAndPlayedFiltered() = runTest {
        stubCounts()
        coEvery { mediaInfoApiClient.getItemsWithUserData(any(), any(), any(), any(), any(), any(), any()) } returns
            Result.success(0 to emptyList())

        buildLoader().load(userId = "u1", isAdmin = false)

        coVerify {
            mediaInfoApiClient.getItemsWithUserData(
                userId = "u1",
                includeItemTypes = HomeStatsCardLoader.WATCHED_RUNTIME_ITEM_TYPES,
                isPlayed = true,
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                startIndex = 0,
                limit = HomeStatsCardLoader.RUNTIME_SAMPLE_CAP,
            )
        }
        assertEquals(200, HomeStatsCardLoader.RUNTIME_SAMPLE_CAP)
    }
}
