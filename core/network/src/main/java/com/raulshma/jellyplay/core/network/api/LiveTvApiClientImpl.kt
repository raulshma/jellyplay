package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.EpgGuide
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : LiveTvApiClient {

    override suspend fun getLiveTvChannels(
        startIndex: Int,
        limit: Int,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvChannel>> = engine.apiResultWithRetry {
        val response = engine.requireApi().itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.LIVE_TV_CHANNEL),
            startIndex = startIndex,
            limit = limit,
            fields = listOf(
                ItemFields.OVERVIEW,
                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
            ),
        ).content
        response.items.map { it.toLiveTvChannel() }
    }

    override suspend fun getLiveTvPrograms(
        channelId: String,
        startDateUtc: String?,
        endDateUtc: String?,
    ): Result<List<com.raulshma.jellyplay.core.model.LiveTvProgram>> = engine.apiResultWithRetry {
        val response = engine.requireApi().itemsApi.getItems(
            parentId = channelId.toUUID(),
            includeItemTypes = listOf(BaseItemKind.LIVE_TV_PROGRAM),
            fields = listOf(ItemFields.OVERVIEW),
        ).content
        response.items.map { it.toLiveTvProgram() }
    }

    override suspend fun getLiveTvGuide(
        startDateUtc: String,
        endDateUtc: String,
        startIndex: Int,
        limit: Int,
    ): Result<EpgGuide> = engine.apiResultWithRetry {
        coroutineScope {
            val client = engine.requireApi()
            val channelsDeferred = async {
                client.itemsApi.getItems(
                    includeItemTypes = listOf(BaseItemKind.LIVE_TV_CHANNEL),
                    startIndex = startIndex,
                    limit = limit,
                    fields = listOf(ItemFields.OVERVIEW),
                ).content.items.map { it.toLiveTvChannel() }
            }
            val programsDeferred = async {
                client.itemsApi.getItems(
                    includeItemTypes = listOf(BaseItemKind.LIVE_TV_PROGRAM),
                    limit = 500,
                    fields = listOf(ItemFields.OVERVIEW),
                ).content.items.map { it.toLiveTvProgram() }
            }
            EpgGuide(channels = channelsDeferred.await(), programs = programsDeferred.await())
        }
    }

    override suspend fun getTimers(): Result<List<com.raulshma.jellyplay.core.model.DvrTimer>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getTimers().content.items.map { it.toDvrTimer() }
    }

    override suspend fun getSeriesTimers(): Result<List<com.raulshma.jellyplay.core.model.DvrSeriesTimer>> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.getSeriesTimers().content.items.map { it.toDvrSeriesTimer() }
    }

    override suspend fun createTimer(
        programId: String,
        channelId: String,
        startDate: String?,
        endDate: String?,
    ): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.createTimer(
            org.jellyfin.sdk.model.api.TimerInfoDto(
                programId = programId,
                channelId = channelId.toUUID(),
                startDate = startDate?.let { java.time.LocalDateTime.parse(it.replace("Z", "").replace("T", " ").substringBefore('+').replace(" ", "T")) },
                endDate = endDate?.let { java.time.LocalDateTime.parse(it.replace("Z", "").replace("T", " ").substringBefore('+').replace(" ", "T")) },
            )
        )
    }

    override suspend fun cancelTimer(timerId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.cancelTimer(timerId = timerId)
    }

    override suspend fun cancelSeriesTimer(seriesTimerId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().liveTvApi.cancelSeriesTimer(timerId = seriesTimerId)
    }
}
