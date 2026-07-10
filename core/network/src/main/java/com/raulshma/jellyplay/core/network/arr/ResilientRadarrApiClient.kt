package com.raulshma.jellyplay.core.network.arr

import com.raulshma.jellyplay.core.model.arr.ArrBlocklistItem
import com.raulshma.jellyplay.core.model.arr.ArrCalendarItem
import com.raulshma.jellyplay.core.model.arr.ArrCommand
import com.raulshma.jellyplay.core.model.arr.ArrCommandName
import com.raulshma.jellyplay.core.model.arr.ArrHistoryItem
import com.raulshma.jellyplay.core.model.arr.ArrQueueDeleteOptions
import com.raulshma.jellyplay.core.model.arr.ArrQueueItem
import com.raulshma.jellyplay.core.model.arr.ArrWantedItem
import com.raulshma.jellyplay.core.network.RetryPolicy
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resilient wrapper around [RadarrApiClientImpl] that applies [RetryPolicy] to
 * every [RadarrApiClient] method.
 *
 * **Note:** This class deliberately implements [RadarrApiClient] directly
 * (rather than using Kotlin interface delegation `by delegate`) so that adding
 * a new method to the interface produces a compile error here, forcing the
 * author to wire it through [req]. Previously in [ResilientSeerrApiClient] a
 * method silently bypassed retry because the auto-generated delegation
 * forwarded straight to the underlying client; the same risk applies here.
 */
@Singleton
class ResilientRadarrApiClient @Inject constructor(
    private val delegate: RadarrApiClientImpl,
) : RadarrApiClient {

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
        movieIds: List<Int>?, episodeIds: List<Int>?,
    ): Result<ArrCommand> =
        req { delegate.postCommand(baseUrl, apiKey, commandName, movieIds, episodeIds) }

    override suspend fun findMovieIdByTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<Int?> =
        req { delegate.findMovieIdByTmdb(baseUrl, apiKey, tmdbId) }

    override suspend fun getMovieForTmdb(baseUrl: String, apiKey: String, tmdbId: Int): Result<RadarrMovieInfo?> =
        req { delegate.getMovieForTmdb(baseUrl, apiKey, tmdbId) }

    override suspend fun deleteMovieFile(baseUrl: String, apiKey: String, movieFileId: Int): Result<Unit> =
        req { delegate.deleteMovieFile(baseUrl, apiKey, movieFileId) }

    override suspend fun monitorMovies(
        baseUrl: String, apiKey: String, movieIds: List<Int>, monitored: Boolean,
    ): Result<Unit> =
        req { delegate.monitorMovies(baseUrl, apiKey, movieIds, monitored) }

    override suspend fun testConnection(baseUrl: String, apiKey: String): Result<Unit> =
        req { delegate.testConnection(baseUrl, apiKey) }

    companion object {
        internal const val MAX_RETRIES = 4
    }
}
