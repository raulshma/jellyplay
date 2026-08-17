package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ContentBreakdown
import com.raulshma.jellyplay.core.model.JellyfinUser
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NewsletterData
import com.raulshma.jellyplay.core.model.PlaybackActivityPoint
import com.raulshma.jellyplay.core.model.PlaybackReportingActivity
import com.raulshma.jellyplay.core.model.PlaybackReportingDetail
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.model.StaleMediaItem
import com.raulshma.jellyplay.core.model.WatchedMediaItem

interface MediaInfoApiClient {
    suspend fun getNewsletterData(sinceDate: String, limit: Int = 20): Result<NewsletterData>

    // NOTE: JellyPlay backend route required — `POST /newsletter/send` is not yet
    // implemented server-side. This wires up the client so the feature lights up
    // once the route lands; until then the call 404s and Result.failure surfaces it.
    suspend fun sendNewsletter(): Result<Unit>

    // NOTE: JellyPlay backend route required — `POST /newsletter/test` is not yet
    // implemented server-side. This wires up the client so the feature lights up
    // once the route lands; until then the call 404s and Result.failure surfaces it.
    suspend fun sendTestNewsletter(): Result<Unit>
    suspend fun getUsers(): Result<List<JellyfinUser>>

    /** Per-user lookup via `GET /Users/{id}` — avoids the full getUsers() scan for one user. */
    suspend fun getUserById(userId: String): Result<JellyfinUser>
    suspend fun getUserPlayedItemCount(userId: String, includeItemTypes: List<String>? = null): Result<Int>
    suspend fun getUserUnplayedItemCount(userId: String, includeItemTypes: List<String>? = null): Result<Int>
    suspend fun getItemsWithUserData(userId: String, includeItemTypes: List<String>? = null, isPlayed: Boolean? = null, sortBy: String = "SortName", sortOrder: String = "Ascending", startIndex: Int = 0, limit: Int = 50): Result<Pair<Int, List<MediaItem>>>
    suspend fun getStaleItems(daysThreshold: Int, includeNeverPlayed: Boolean, includeItemTypes: List<String>, parentId: String? = null, startIndex: Int = 0, limit: Int = 200, useDateAdded: Boolean = false): Result<Pair<Int, List<StaleMediaItem>>>
    suspend fun getWatchedItems(userId: String, includeItemTypes: List<String>, minDaysSincePlayed: Int = 0, keepFavorites: Boolean = true, parentId: String? = null, startIndex: Int = 0, limit: Int = 200): Result<Pair<Int, List<WatchedMediaItem>>>
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun deleteItems(itemIds: List<String>): Result<Int>
    suspend fun checkPlaybackReportingPlugin(): Result<PlaybackReportingStatus>
    suspend fun getPlaybackReportingUserActivity(days: Int = 30): Result<List<PlaybackReportingActivity>>
    suspend fun getPlaybackReportingPlayActivity(days: Int = 30, dataType: String = "count", filter: String? = null): Result<List<PlaybackActivityPoint>>
    suspend fun getPlaybackReportingUserItems(userId: String, date: String, filter: String? = null): Result<List<PlaybackReportingDetail>>
    suspend fun getPlaybackReportingBreakdown(breakdownType: String, days: Int = 30, filter: String? = null): Result<List<ContentBreakdown>>
    suspend fun getPlaybackReportingArtistBreakdown(days: Int = 30, filter: String? = null): Result<List<ContentBreakdown>>
}
