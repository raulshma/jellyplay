package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrSeriesEpisode
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient wrapper around [SonarrApiClientImpl] that applies [RetryPolicy] to
 * every [SonarrApiClient] method.
 *
 * **Note:** This class deliberately implements [SonarrApiClient] directly
 * (rather than using Kotlin interface delegation `by delegate`) so that adding
 * a new method to the interface produces a compile error here, forcing the
 * author to wire it through [req]. See [ResilientRadarrApiClient] and
 * [com.raulshma.jellyplay.core.network.seerr.ResilientSeerrApiClient] for the
 * original incident this pattern prevents.
 */
@Singleton
class ResilientSonarrApiClient @Inject constructor(
    private val delegate: SonarrApiClientImpl,
) : SonarrApiClient {

    private suspend fun <T> req(block: suspend () -> Result<T>): Result<T> =
        RetryPolicy.executeWithRetry(
            maxRetries = MAX_RETRIES,
            block = block,
        )

    override suspend fun getQueue(baseUrl: String, apiKey: String): Result<List<ArrQueueItem>> =
        req { delegate.getQueue(baseUrl, apiKey) }

    override suspend fun deleteQueueItem(
        baseUrl: String, apiKey: String, id: Int, options: ArrQueueDeleteOptions,
    ): Result<Unit> = req { delegate.deleteQueueItem(baseUrl, apiKey, id, options) }

    override suspend fun deleteQueueItems(
        baseUrl: String, apiKey: String, ids: List<Int>, options: ArrQueueDeleteOptions,
    ): Result<Unit> = req { delegate.deleteQueueItems(baseUrl, apiKey, ids, options) }

    override suspend fun grabQueueItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        req { delegate.grabQueueItem(baseUrl, apiKey, id) }

    override suspend fun importQueueItem(baseUrl: String, apiKey: String, downloadId: String): Result<Unit> =
        req { delegate.importQueueItem(baseUrl, apiKey, downloadId) }

    override suspend fun getCalendar(
        baseUrl: String, apiKey: String, start: String, end: String,
    ): Result<List<ArrCalendarItem>> = req { delegate.getCalendar(baseUrl, apiKey, start, end) }

    override suspend fun getHistory(
        baseUrl: String, apiKey: String, eventType: Int?,
    ): Result<List<ArrHistoryItem>> = req { delegate.getHistory(baseUrl, apiKey, eventType) }

    override suspend fun getBlocklist(
        baseUrl: String, apiKey: String, page: Int, pageSize: Int,
    ): Result<List<ArrBlocklistItem>> = req { delegate.getBlocklist(baseUrl, apiKey, page, pageSize) }

    override suspend fun deleteBlocklistItem(baseUrl: String, apiKey: String, id: Int): Result<Unit> =
        req { delegate.deleteBlocklistItem(baseUrl, apiKey, id) }

    override suspend fun deleteBlocklistItems(baseUrl: String, apiKey: String, ids: List<Int>): Result<Unit> =
        req { delegate.deleteBlocklistItems(baseUrl, apiKey, ids) }

    override suspend fun getWanted(
        baseUrl: String, apiKey: String, page: Int, pageSize: Int,
    ): Result<List<ArrWantedItem>> = req { delegate.getWanted(baseUrl, apiKey, page, pageSize) }

    override suspend fun postCommand(
        baseUrl: String, apiKey: String, commandName: ArrCommandName,
        seriesId: Int?, episodeIds: List<Int>?, seasonNumber: Int?,
    ): Result<ArrCommand> =
        req { delegate.postCommand(baseUrl, apiKey, commandName, seriesId, episodeIds, seasonNumber) }

    override suspend fun findSeriesByTvdb(baseUrl: String, apiKey: String, tvdbId: Int): Result<Int?> =
        req { delegate.findSeriesByTvdb(baseUrl, apiKey, tvdbId) }

    override suspend fun getEpisodeInfo(
        baseUrl: String, apiKey: String, seriesId: Int, seasonNumber: Int, episodeNumber: Int,
    ): Result<SonarrEpisodeInfo?> =
        req { delegate.getEpisodeInfo(baseUrl, apiKey, seriesId, seasonNumber, episodeNumber) }

    override suspend fun getSeasonSummaries(
        baseUrl: String, apiKey: String, seriesId: Int,
    ): Result<List<SonarrSeasonSummary>> =
        req { delegate.getSeasonSummaries(baseUrl, apiKey, seriesId) }

    override suspend fun deleteEpisodeFile(baseUrl: String, apiKey: String, episodeFileId: Int): Result<Unit> =
        req { delegate.deleteEpisodeFile(baseUrl, apiKey, episodeFileId) }

    override suspend fun monitorEpisodes(
        baseUrl: String, apiKey: String, episodeIds: List<Int>, monitored: Boolean,
    ): Result<Unit> =
        req { delegate.monitorEpisodes(baseUrl, apiKey, episodeIds, monitored) }

    override suspend fun getSeriesInfo(baseUrl: String, apiKey: String, tvdbId: Int): Result<SonarrSeriesInfo?> =
        req { delegate.getSeriesInfo(baseUrl, apiKey, tvdbId) }

    override suspend fun getEpisodesForSeries(
        baseUrl: String, apiKey: String, seriesId: Int,
    ): Result<List<ArrSeriesEpisode>> =
        req { delegate.getEpisodesForSeries(baseUrl, apiKey, seriesId) }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> =
        req { delegate.testConnection(baseUrl, apiKey) }

    companion object {
        internal const val MAX_RETRIES = 4
    }
}
