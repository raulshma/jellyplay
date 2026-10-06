package com.raulshma.jellyplay.core.network.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The Jellyfin-plugin-jellyplay client seam — every `jellyplay/` route the
 * companion server plugin serves (see the plugin repo's docs/CONTRACT.md).
 * Split from the other families so the sync engine (data layer) can depend on
 * THIS interface alone, mirroring the NewsletterApiClient pass-through idiom.
 *
 * Availability contract: every call 404s when the plugin is absent. The
 * capability probe ([getCapabilities]) is the ONE bootstrap check — callers
 * gate on the resulting feature set, never on per-endpoint 404 handling.
 */
interface JellyPlayPluginApiClient {
    suspend fun getCapabilities(): Result<JellyPlayCapabilities>

    // ---- settings sync ----

    suspend fun getSettings(profile: String? = null): Result<JellyPlaySettingsSnapshot>

    suspend fun getChangedSettings(since: Long, profile: String? = null): Result<JellyPlaySettingsSnapshot>

    suspend fun applySettings(
        profile: String?,
        deviceId: String?,
        writes: List<JellyPlaySettingWrite>,
    ): Result<JellyPlaySettingsBatchResult>

    suspend fun resetNamespace(ns: String, profile: String? = null): Result<Unit>

    suspend fun resolveProfile(profile: String? = null): Result<JellyPlaySettingsSnapshot>

    /**
     * Cold SSE stream of `settings.changed` / `settings.reset` events for the
     * signed-in user. Cancelling collection closes the connection.
     */
    fun settingsStream(): kotlinx.coroutines.flow.Flow<JellyPlaySseEvent>

    // ---- events & devices ----

    suspend fun registerDevice(deviceId: String, name: String, platform: String, appVersion: String): Result<Unit>

    suspend fun unregisterDevice(deviceId: String): Result<Unit>

    suspend fun getDevices(): Result<List<JellyPlayDevice>>

    /** Cold SSE stream of the events channel (new-media, broadcast, session notices). */
    fun eventsStream(): kotlinx.coroutines.flow.Flow<JellyPlaySseEvent>

    suspend fun broadcast(title: String, body: String, url: String? = null): Result<Unit>

    // ---- inbox messages ----

    suspend fun getMessages(): Result<List<JellyPlayMessage>>

    suspend fun markMessageRead(messageId: String): Result<Unit>

    // ---- Seerr bridge ----

    suspend fun seerrStatus(): Result<JellyPlaySeerrStatus>

    suspend fun seerrLogin(
        authType: String,
        username: String?,
        password: String?,
        quickConnectSecret: String?,
    ): Result<Unit>

    suspend fun seerrLogout(): Result<Unit>

    // ---- ratings ----

    /** null = MDBList unconfigured or no data for the id (plugin 404). */
    suspend fun getMdbListRatings(imdbId: String): Result<JellyPlayRatingsResult?>

    suspend fun getTmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Result<Map<Int, JellyPlayEpisodeRatings>?>

    // ---- recommendations ----

    suspend fun getJellyPlaySimilarItems(itemId: String, limit: Int = 12): Result<List<JellyPlayScoredItem>>

    // ---- anime markers ----

    /** null = no markers cached for the series (plugin 404). */
    suspend fun getAnimeMarkers(seriesId: String, providerSeriesId: String): Result<JellyPlaySeriesMarkers?>

    // ---- rows ----

    /** null = the admin-defined row does not exist (plugin 404). */
    suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?>

    /** The admin-defined custom row catalog (titles + sources, plugin config order). */
    suspend fun getCustomRowCatalog(): Result<JellyPlayRowCatalog?>

    /** null = seasonal rows unconfigured (no TMDB key / no keyword this season). */
    suspend fun getSeasonalRow(keyword: String? = null): Result<JellyPlayRowResult?>

    // ---- user data ----

    suspend fun getBookmarks(itemId: String): Result<List<JellyPlayBookmark>>

    suspend fun upsertBookmark(itemId: String, request: JellyPlayBookmarkRequest): Result<JellyPlayBookmark>

    suspend fun deleteBookmark(itemId: String, bookmarkId: String): Result<Unit>

    suspend fun getUserRatings(filter: String? = null): Result<List<JellyPlayUserRating>>

    // ---- transcodes ----

    /** Admin-only route; 403 for non-admins surfaces as a failed Result. */
    suspend fun getActiveTranscodes(): Result<List<JellyPlayActiveTranscode>>

    suspend fun getMyTranscodes(): Result<List<JellyPlayActiveTranscode>>

    suspend fun cancelTranscode(sessionId: String): Result<Unit>
}

@Serializable
data class JellyPlayRatingEntry(
    val source: String,
    val score: Double? = null,
    val votes: Int? = null,
    val url: String? = null,
)

@Serializable
data class JellyPlayRatingsResult(
    val imdbId: String,
    val ratings: List<JellyPlayRatingEntry> = emptyList(),
)

@Serializable
data class JellyPlayEpisodeRatings(
    val tmdbScore: Double? = null,
    val tmdbVotes: Int? = null,
)

@Serializable
data class JellyPlayScoredItem(
    val itemId: String,
    val name: String,
    val score: Double,
)

@Serializable
data class JellyPlayAnimeMarker(
    val type: String,
    val episodeNumber: Int,
    val note: String? = null,
)

@Serializable
data class JellyPlaySeriesMarkers(
    val seriesId: String,
    val aniListId: String? = null,
    val malId: String? = null,
    val markers: List<JellyPlayAnimeMarker> = emptyList(),
)

@Serializable
data class JellyPlayRowItem(
    val title: String,
    val year: String? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val localItemId: String? = null,
)

@Serializable
data class JellyPlayRowTitle(
    val title: String,
    val source: String = "",
    val limit: Int = 20,
)

@Serializable
data class JellyPlayRowCatalog(
    val rows: List<JellyPlayRowTitle> = emptyList(),
)

@Serializable
data class JellyPlayRowResult(
    val title: String,
    val source: String,
    val items: List<JellyPlayRowItem> = emptyList(),
)

@Serializable
data class JellyPlayBookmark(
    val id: String,
    val itemId: String,
    val position: Double = 0.0,
    val chapterIndex: Int? = null,
    val label: String = "",
    val notes: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class JellyPlayBookmarkRequest(
    val id: String? = null,
    val position: Double = 0.0,
    val chapterIndex: Int? = null,
    val label: String = "",
    val notes: String = "",
)

@Serializable
data class JellyPlayUserRating(
    val itemId: String,
    val name: String,
    val itemType: String = "",
    val likes: Boolean? = null,
    val rating: Int? = null,
    val lastPlayedDate: String? = null,
    val playCount: Int = 0,
)

@Serializable
data class JellyPlayActiveTranscode(
    val sessionId: String,
    val userName: String? = null,
    val deviceName: String? = null,
    val itemName: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val playMethod: String? = null,
    val videoBitrate: Int? = null,
    /** Named flag bits the server decomposed its `[Flags]` reason word into. */
    val transcodeReasons: List<String>? = null,
    val positionTicks: Long = 0,
    val isPaused: Boolean = false,
)

@Serializable
data class JellyPlayCapabilities(
    val contractVersion: Int,
    val pluginVersion: String,
    val features: List<String> = emptyList(),
    val serverNow: Long = 0,
    val deviceProfiles: List<String> = emptyList(),
)

@Serializable
data class JellyPlaySettingsEntry(
    val ns: String,
    val key: String,
    val schemaVersion: Int = 1,
    val updatedAt: Long,
    val deviceId: String = "",
    val profile: String = "",
    val value: JsonElement,
)

@Serializable
data class JellyPlaySettingsSnapshot(
    val head: Long = 0,
    val profile: String = "",
    val settings: List<JellyPlaySettingsEntry> = emptyList(),
)

@Serializable
data class JellyPlaySettingWrite(
    val ns: String,
    val key: String,
    val schemaVersion: Int = 1,
    val updatedAt: Long,
    val value: JsonElement,
)

@Serializable
data class JellyPlayAppliedSetting(val ns: String, val key: String, val updatedAt: Long, val seq: Long)

@Serializable
data class JellyPlayRejectedSetting(val ns: String, val key: String, val reason: String)

@Serializable
data class JellyPlaySettingsBatchResult(
    val head: Long = 0,
    val applied: List<JellyPlayAppliedSetting> = emptyList(),
    val rejected: List<JellyPlayRejectedSetting> = emptyList(),
)

@Serializable
data class JellyPlayDevice(
    val deviceId: String,
    val userId: String = "",
    val name: String = "",
    val platform: String = "",
    val appVersion: String = "",
    val lastSeen: Long = 0,
)

@Serializable
data class JellyPlayMessage(
    val id: String,
    val title: String,
    val body: String = "",
    val color: String = "",
    val linkUrl: String = "",
    val linkLabel: String = "",
    val startsAt: Long? = null,
    val endsAt: Long? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = 0,
    val read: Boolean = false,
)

@Serializable
data class JellyPlaySeerrStatus(
    val configured: Boolean = false,
    val serverUrl: String? = null,
    val linked: Boolean = false,
    val createdAt: Long? = null,
)

/** One decoded server-sent event. [data] stays raw JSON — consumers decode per [event]. */
data class JellyPlaySseEvent(
    val id: Long,
    val event: String,
    val data: String,
)
