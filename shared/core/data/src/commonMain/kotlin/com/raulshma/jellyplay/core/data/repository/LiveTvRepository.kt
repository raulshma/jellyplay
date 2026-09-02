package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.ProgramFilters

interface LiveTvRepository {

    suspend fun getLiveTvChannels(
        startIndex: Int = 0,
        limit: Int = 50,
        addCurrentProgram: Boolean = true,
        enableFavoriteSorting: Boolean = false,
        isFavorite: Boolean? = null,
    ): Result<List<LiveTvChannel>>

    suspend fun getRecommendedPrograms(
        filters: ProgramFilters = ProgramFilters(),
        limit: Int = 24,
    ): Result<List<LiveTvProgram>>

    suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String? = null,
        endDateUtc: String? = null,
    ): Result<List<LiveTvProgram>>

    suspend fun getPrograms(
        channelIds: List<String>,
        startDateUtc: String,
        endDateUtc: String,
    ): Result<List<LiveTvProgram>>

    suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<EpgGuide>

    suspend fun getGuideInfo(): Result<GuideInfo>

    suspend fun getRecordings(
        limit: Int? = null,
        isInProgress: Boolean? = null,
    ): Result<List<LiveTvRecording>>

    /** Permanently deletes a recorded item by its Jellyfin item id. */
    suspend fun deleteRecording(recordingId: String): Result<Unit>

    suspend fun getTimers(
        isActive: Boolean? = null,
        isScheduled: Boolean? = null,
    ): Result<List<DvrTimer>>

    suspend fun getSeriesTimers(sortBy: String? = null): Result<List<DvrSeriesTimer>>

    suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer>

    suspend fun createTimer(programId: String): Result<Unit>

    suspend fun createSeriesTimer(programId: String): Result<Unit>

    suspend fun cancelTimer(timerId: String): Result<Unit>

    suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit>
}
