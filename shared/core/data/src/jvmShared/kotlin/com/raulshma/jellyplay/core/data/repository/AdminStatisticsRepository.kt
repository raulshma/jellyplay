package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.AuditLogEntry
import com.raulshma.jellyplay.core.model.CleanupActionType
import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaCleanupConfig
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.ScanPhase
import com.raulshma.jellyplay.core.model.ScanProgress
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.model.UserDetailPage
import com.raulshma.jellyplay.core.model.UserStatistics
import com.raulshma.jellyplay.core.model.WatchedMediaItem
import kotlinx.coroutines.flow.Flow

interface AdminStatisticsRepository {

    fun getPlaybackReportingStatus(): Flow<PlaybackReportingStatus>

    suspend fun refreshPlaybackReportingStatus()

    /**
     * Every user's list-row statistics from the page's aggregate reads (the
     * user roster, the active sessions, the plugin's per-user watch time).
     *
     * The per-user played/unplayed counts are NOT part of this read: each is
     * a per-user round-trip fan-out (N×4 requests on a whole roster, repeated
     * per refresh), so they ride [getUserPlayCounts] — fetched per user as
     * needed (the list rows as they become visible, the CSV export's set)
     * and cached by the caller. The rows returned here carry zeros for the
     * count fields until a fetched [UserPlayCounts] is merged in
     * ([UserPlayCounts.mergedInto]).
     */
    suspend fun getAllUsersWithStatistics(): Result<List<UserStatistics>>

    /**
     * One user's played/unplayed counters — the four round-trip read behind
     * the list rows' type pills and completion bar and the CSV export's count
     * columns. Strict: a failed counter fails the whole read (Result.failure)
     * so the caller can leave the row's counts pending and retry later
     * instead of caching a zero that reads as "no plays".
     */
    suspend fun getUserPlayCounts(userId: String): Result<UserPlayCounts>

    suspend fun getUserDetailStatistics(
        userId: String,
        page: Int = 0,
        pageSize: Int = 50,
    ): Result<UserDetailPage>

    suspend fun detectStaleMedia(config: MediaCleanupConfig): Result<String>

    suspend fun detectWatchedMedia(config: MediaCleanupConfig): Result<String>

    fun getScanProgress(scanId: String): Flow<ScanProgress>

    suspend fun getScanResultJson(scanId: String): String?

    suspend fun removeMediaItems(
        itemIds: List<String>,
        itemNameMap: Map<String, String>,
        actionType: CleanupActionType,
        config: MediaCleanupConfig,
    ): Result<AuditLogEntry>

    fun getAuditHistory(actionType: CleanupActionType? = null): Flow<List<AuditLogEntry>>
}

/**
 * One user's played/unplayed counters (the [AdminStatisticsRepository.getUserPlayCounts]
 * payload). The completion-rate math
 * (`movieTotal = unplayed + played; played/total else 0`) lives here exactly
 * once — the statistics builder and every count merge reads it from this
 * type, so the two can never drift.
 */
data class UserPlayCounts(
    val moviePlayed: Int = 0,
    val episodePlayed: Int = 0,
    val songPlayed: Int = 0,
    val movieUnplayed: Int = 0,
) {

    /** Movies total = unplayed + played (the completion rate's denominator). */
    val movieTotal: Int get() = movieUnplayed + moviePlayed

    /** The played/movie-total share the rows and CSV render; 0 with no movies. */
    val completionRate: Float
        get() = if (movieTotal > 0) moviePlayed.toFloat() / movieTotal else 0f

    /**
     * Fills the count fields of a (list-row) [UserStatistics] that was built
     * without them — the aggregate list read carries zeros until the per-user
     * counts land.
     */
    fun mergedInto(stats: UserStatistics): UserStatistics = stats.copy(
        totalPlayCount = moviePlayed + episodePlayed + songPlayed,
        moviePlayCount = moviePlayed,
        episodePlayCount = episodePlayed,
        songPlayCount = songPlayed,
        completionRate = completionRate,
    )
}
