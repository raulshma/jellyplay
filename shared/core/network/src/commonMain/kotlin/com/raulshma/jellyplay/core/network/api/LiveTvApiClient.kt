package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.ProgramFilters

interface LiveTvApiClient {
    /** `GET /LiveTv/Channels` — channels (optionally with embedded current program). */
    suspend fun getLiveTvChannels(
        startIndex: Int = 0,
        limit: Int = 50,
        addCurrentProgram: Boolean = true,
        enableFavoriteSorting: Boolean = false,
        isFavorite: Boolean? = null,
    ): Result<List<LiveTvChannel>>

    /** `GET /LiveTv/Programs/Recommended` — "On Now" and category rows for the Programs tab. */
    suspend fun getRecommendedPrograms(
        filters: ProgramFilters = ProgramFilters(),
        limit: Int = 24,
    ): Result<List<LiveTvProgram>>

    /** `GET /LiveTv/Programs` — programs for a single channel within an optional time window. */
    suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String? = null,
        endDateUtc: String? = null,
    ): Result<List<LiveTvProgram>>

    /**
     * `POST /LiveTv/Programs` — bulk programs for a set of channels within a
     * time window (the EPG Guide data source).
     */
    suspend fun getPrograms(
        channelIds: List<String>,
        startDateUtc: String,
        endDateUtc: String,
    ): Result<List<LiveTvProgram>>

    /**
     * `GET /LiveTv/Channels` + `POST /LiveTv/Programs` — combined EPG snapshot
     * for the Guide tab, using a real server-side time window (no client filter).
     */
    suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int = 0,
        limit: Int = 50,
    ): Result<EpgGuide>

    /** `GET /LiveTv/GuideInfo` — EPG availability window. */
    suspend fun getGuideInfo(): Result<GuideInfo>

    /** `GET /LiveTv/Recordings` — recorded items. */
    suspend fun getRecordings(
        limit: Int? = null,
        isInProgress: Boolean? = null,
    ): Result<List<LiveTvRecording>>

    /** `GET /LiveTv/Timers` — scheduled recording rules (optionally filtered). */
    suspend fun getTimers(
        isActive: Boolean? = null,
        isScheduled: Boolean? = null,
    ): Result<List<DvrTimer>>

    /** `GET /LiveTv/SeriesTimers` — series recording rules. */
    suspend fun getSeriesTimers(sortBy: String? = null): Result<List<DvrSeriesTimer>>

    /** `GET /LiveTv/Timers/Defaults` — server-derived default timer for a program. */
    suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer>

    /** `POST /LiveTv/Timers` — record once (uses server defaults). */
    suspend fun createTimer(programId: String): Result<Unit>

    /** `POST /LiveTv/SeriesTimers` — record series (uses server defaults). */
    suspend fun createSeriesTimer(programId: String): Result<Unit>

    /** `DELETE /LiveTv/Timers/{id}` — cancel a single recording rule. */
    suspend fun cancelTimer(timerId: String): Result<Unit>

    /** `DELETE /LiveTv/SeriesTimers/{id}` — cancel a series recording rule. */
    suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit>
}
