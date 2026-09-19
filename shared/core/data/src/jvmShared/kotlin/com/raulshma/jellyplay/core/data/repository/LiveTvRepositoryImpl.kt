package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DvrSeriesTimer
import com.raulshma.jellyplay.core.model.DvrTimer
import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.ProgramFilters
import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient

/**
 *  MediaRepository facade split: the fifteen LiveTvRepository members moved
 * verbatim from [MediaRepositoryImpl] — every one a stateless forward, so
 * this impl owns NO cache state and takes no [MediaRepositoryInternals]:
 * the EPG/recording surface never reads or writes a repository cache, and
 * the family's freshness is the caller's concern (no member ever grew a
 * force lever).
 *
 * Ctor narrowed to the two API family clients that own the routes (the
 * PlaybackRepositoryImpl family-seam precedent, not the JellyfinApiClient
 * union): [LiveTvApiClient] for the LiveTv REST vocabulary, and
 * [MediaInfoApiClient] for the recording delete — Jellyfin has no
 * dedicated LiveTv recording-delete route, so `deleteRecording` goes
 * through the generic item delete that family owns. The family singles
 * compose the same impls the union delegates to, so the wire behavior is
 * byte-identical.
 */
class LiveTvRepositoryImpl(
    private val liveTvApiClient: LiveTvApiClient,
    private val mediaInfoApiClient: MediaInfoApiClient,
) : LiveTvRepository {

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
        addCurrentProgram: Boolean,
        enableFavoriteSorting: Boolean,
        isFavorite: Boolean?,
    ): Result<List<LiveTvChannel>> =
        liveTvApiClient.getLiveTvChannels(startIndex, limit, addCurrentProgram, enableFavoriteSorting, isFavorite)

    override suspend fun getRecommendedPrograms(
        filters: ProgramFilters,
        limit: Int,
    ): Result<List<LiveTvProgram>> =
        liveTvApiClient.getRecommendedPrograms(filters, limit)

    override suspend fun getLiveTvPrograms(channelId: String, startDateUtc: String?, endDateUtc: String?): Result<List<LiveTvProgram>> =
        liveTvApiClient.getLiveTvPrograms(channelId, startDateUtc, endDateUtc)

    override suspend fun getPrograms(channelIds: List<String>, startDateUtc: String, endDateUtc: String): Result<List<LiveTvProgram>> =
        liveTvApiClient.getPrograms(channelIds, startDateUtc, endDateUtc)

    override suspend fun getLiveTvGuide(startDateUtc: String, endDateUtc: String, startIndex: Int, limit: Int): Result<EpgGuide> =
        liveTvApiClient.getLiveTvGuide(startDateUtc, endDateUtc, startIndex, limit)

    override suspend fun getGuideInfo(): Result<GuideInfo> = liveTvApiClient.getGuideInfo()

    override suspend fun getRecordings(limit: Int?, isInProgress: Boolean?): Result<List<LiveTvRecording>> =
        liveTvApiClient.getRecordings(limit, isInProgress)

    /** Permanently deletes a recorded item by its Jellyfin item id. */
    override suspend fun deleteRecording(recordingId: String): Result<Unit> =
        mediaInfoApiClient.deleteItem(recordingId)

    override suspend fun getTimers(isActive: Boolean?, isScheduled: Boolean?): Result<List<DvrTimer>> =
        liveTvApiClient.getTimers(isActive, isScheduled)

    override suspend fun getSeriesTimers(sortBy: String?): Result<List<DvrSeriesTimer>> =
        liveTvApiClient.getSeriesTimers(sortBy)

    override suspend fun getDefaultTimer(programId: String): Result<DvrSeriesTimer> =
        liveTvApiClient.getDefaultTimer(programId)

    override suspend fun createTimer(programId: String): Result<Unit> = liveTvApiClient.createTimer(programId)

    override suspend fun createSeriesTimer(programId: String): Result<Unit> = liveTvApiClient.createSeriesTimer(programId)

    override suspend fun cancelTimer(timerId: String): Result<Unit> = liveTvApiClient.cancelTimer(timerId)

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> =
        liveTvApiClient.cancelSeriesTimer(seriesTimerId)
}
