package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.EpgGuide
import com.raulshma.jellyplay.core.model.GuideInfo
import com.raulshma.jellyplay.core.model.ProgramFilters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : LiveTvApiClient {

    private fun userIdUuid() = engine.currentUser.value?.id?.toUUID()

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
        addCurrentProgram: Boolean,
        enableFavoriteSorting: Boolean,
        isFavorite: Boolean?,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvChannel>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getLiveTvChannels(
            userId = userIdUuid(),
            startIndex = startIndex,
            limit = limit,
            addCurrentProgram = addCurrentProgram,
            enableFavoriteSorting = enableFavoriteSorting,
            isFavorite = isFavorite,
            fields = listOf(ItemFields.OVERVIEW, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
        ).content.items.map { it.toLiveTvChannel() }
    }

    override suspend fun getRecommendedPrograms(
        filters: ProgramFilters,
        limit: Int,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvProgram>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getRecommendedPrograms(
            userId = userIdUuid(),
            limit = limit,
            isAiring = filters.isAiring,
            hasAired = filters.hasAired,
            isMovie = filters.isMovie,
            isSeries = filters.isSeries,
            isNews = filters.isNews,
            isKids = filters.isKids,
            isSports = filters.isSports,
            enableTotalRecordCount = false,
            fields = listOf(ItemFields.OVERVIEW, ItemFields.CHANNEL_INFO, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
        ).content.items.map { it.toLiveTvProgram() }
    }

    override suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String?,
        endDateUtc: String?,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvProgram>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getLiveTvPrograms(
            channelIds = listOf(channelId.toUUID()),
            userId = userIdUuid(),
            // minEndDate filters out programs that already ended before the
            // range start (now); maxStartDate filters out programs starting
            // after the range end (end of day). These were previously swapped,
            // which made minEndDate > maxStartDate and yielded an empty list.
            minEndDate = startDateUtc?.toDateTime(),
            maxStartDate = endDateUtc?.toDateTime(),
            fields = listOf(ItemFields.OVERVIEW),
        ).content.items.map { it.toLiveTvProgram() }
    }

    override suspend fun getPrograms(
        channelIds: List<String>,
        startDateUtc: String,
        endDateUtc: String,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvProgram>> = engine.apiResultWithRetry {
        val dto = GetProgramsDto(
            channelIds = channelIds.map { it.toUUID() },
            userId = userIdUuid(),
            minEndDate = startDateUtc.toDateTime(),
            maxStartDate = endDateUtc.toDateTime(),
            sortBy = listOf(ItemSortBy.START_DATE),
            fields = listOf(ItemFields.OVERVIEW),
        )
        engine.requireApi().liveTvApi.getPrograms(dto).content.items.map { it.toLiveTvProgram() }
    }

    override suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int,
        limit: Int,
    ): Result<EpgGuide> = engine.apiResultWithRetry {
        coroutineScope {
            val client = engine.requireApi()
            val uid = userIdUuid()
            val channelsDeferred = async {
                client.liveTvApi.getLiveTvChannels(
                    userId = uid,
                    startIndex = startIndex,
                    limit = limit,
                    addCurrentProgram = false,
                    fields = listOf(ItemFields.OVERVIEW),
                ).content.items.map { it.toLiveTvChannel() }
            }
            val programsDeferred = async {
                val channelIds = channelsDeferred.await().map { it.id }
                val dto = GetProgramsDto(
                    channelIds = channelIds.map { it.toUUID() },
                    userId = uid,
                    minEndDate = startDateUtc.toDateTime(),
                    maxStartDate = endDateUtc.toDateTime(),
                    sortBy = listOf(ItemSortBy.START_DATE),
                    fields = listOf(ItemFields.OVERVIEW),
                )
                client.liveTvApi.getPrograms(dto).content.items.map { it.toLiveTvProgram() }
            }
            EpgGuide(channels = channelsDeferred.await(), programs = programsDeferred.await())
        }
    }

    override suspend fun getGuideInfo(): Result<GuideInfo> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getGuideInfo().content.let {
            GuideInfo(startDate = it.startDate.toString(), endDate = it.endDate.toString())
        }
    }

    override suspend fun getRecordings(
        limit: Int?,
        isInProgress: Boolean?,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvRecording>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getRecordings(
            userId = userIdUuid(),
            limit = limit,
            isInProgress = isInProgress,
            enableTotalRecordCount = false,
            fields = listOf(ItemFields.CAN_DELETE, ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
        ).content.items.map { it.toLiveTvRecording() }
    }

    override suspend fun getTimers(
        isActive: Boolean?,
        isScheduled: Boolean?,
    ): Result<List<com.raulshma.jellyplay.core.model.DvrTimer>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getTimers(
            isActive = isActive,
            isScheduled = isScheduled,
        ).content.items.map { it.toDvrTimer() }
    }

    override suspend fun getSeriesTimers(sortBy: String?): Result<List<com.raulshma.jellyplay.core.model.DvrSeriesTimer>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getSeriesTimers(sortBy = sortBy).content.items.map { it.toDvrSeriesTimer() }
    }

    override suspend fun getDefaultTimer(programId: String): Result<com.raulshma.jellyplay.core.model.DvrSeriesTimer> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getDefaultTimer(programId = programId).content.toDvrSeriesTimer()
    }

    /**
     * Records a single program. fetch the
     * server-derived defaults for the program, then `POST /LiveTv/Timers` with
     * a [TimerInfoDto] seeded from those defaults (so padding/priority/etc.
     * come from server settings rather than being hand-rolled).
     */
    override suspend fun createTimer(programId: String): Result<Unit> = engine.apiResultWithRetry {
        val defaults = engine.requireApi().liveTvApi.getDefaultTimer(programId = programId).content
        val payload = TimerInfoDto(
            programId = defaults.programId,
            channelId = defaults.channelId,
            name = defaults.name,
            overview = defaults.overview,
            startDate = defaults.startDate,
            endDate = defaults.endDate,
            prePaddingSeconds = defaults.prePaddingSeconds,
            postPaddingSeconds = defaults.postPaddingSeconds,
            isPrePaddingRequired = defaults.isPrePaddingRequired,
            isPostPaddingRequired = defaults.isPostPaddingRequired,
            priority = defaults.priority,
        )
        engine.requireApi().liveTvApi.createTimer(payload)
    }

    /**
     * Records a series. Fetches the server-derived series defaults for the
     * program and `POST /LiveTv/SeriesTimers` with that payload, matching the
     * web client's `createLiveTvSeriesTimer` flow.
     */
    override suspend fun createSeriesTimer(programId: String): Result<Unit> = engine.apiResultWithRetry {
        val defaults = engine.requireApi().liveTvApi.getDefaultTimer(programId = programId).content
        engine.requireApi().liveTvApi.createSeriesTimer(defaults)
    }

    override suspend fun cancelTimer(timerId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.cancelTimer(timerId = timerId)
    }

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.cancelSeriesTimer(timerId = seriesTimerId)
    }

    private fun String.toDateTime(): LocalDateTime =
        OffsetDateTime.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime()
}
