package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram

interface LiveTvApiClient {
    suspend fun getLiveTvChannels(startIndex: Int = 0, limit: Int = 50): Result<List<LiveTvChannel>>
    suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String? = null, endDateUtc: String? = null): Result<List<LiveTvProgram>>
    suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int = 0, limit: Int = 50): Result<EpgGuide>
    suspend fun getTimers(): Result<List<DvrTimer>>
    suspend fun getSeriesTimers(): Result<List<DvrSeriesTimer>>
    suspend fun createTimer(programId: String, channelId: String, startDate: String? = null, endDate: String? = null): Result<Unit>
    suspend fun cancelTimer(timerId: String): Result<Unit>
    suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit>
}
