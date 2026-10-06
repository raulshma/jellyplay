package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.network.auth.tokenAuthHeader
import java.io.BufferedReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Request

/**
 * jvmShared impl of the JellyPlay plugin seam over [JellyfinRawRequester] —
 * the same chassis the plugin-catalogue and playback-reporting clients ride.
 *
 * SSE: hand-rolled line parser over a streaming GET (no okhttp-sse dependency)
 * on a client clone with an unbounded read timeout — SSE connections idle by
 * design, and the engine's 15s read ceiling would kill them within a minute
 * of quiet. The clone shares the engine's pool/dispatcher (same trick
 * postForText uses for its long-call endpoints). Cancellation of the
 * collecting scope closes the call; reconnect/backoff policy belongs to the
 * repository layer, not here.
 */
class JellyPlayPluginApiClientImpl(
    private val engine: JellyfinApiEngine,
) : JellyPlayPluginApiClient {

    private val requester = JellyfinRawRequester(engine)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------
    // capabilities
    // ------------------------------------------------------------------

    override suspend fun getCapabilities(): Result<JellyPlayCapabilities> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/capabilities")
            ?: throw Exception("JellyPlay plugin not available: 404")
        json.decodeFromString<JellyPlayCapabilities>(text)
    }

    // ------------------------------------------------------------------
    // settings sync
    // ------------------------------------------------------------------

    override suspend fun getSettings(profile: String?): Result<JellyPlaySettingsSnapshot> = runCatchingIO {
        val text = requester.getBodyText(profileQuery("/jellyplay/settings", profile))
            ?: throw Exception("JellyPlay settings fetch failed: 404")
        json.decodeFromString<JellyPlaySettingsSnapshot>(text)
    }

    override suspend fun getChangedSettings(since: Long, profile: String?): Result<JellyPlaySettingsSnapshot> = runCatchingIO {
        val text = requester.getBodyText(profileQuery("/jellyplay/settings/changed?since=$since", profile))
            ?: throw Exception("JellyPlay settings delta fetch failed: 404")
        json.decodeFromString<JellyPlaySettingsSnapshot>(text)
    }

    override suspend fun applySettings(
        profile: String?,
        deviceId: String?,
        writes: List<JellyPlaySettingWrite>,
    ): Result<JellyPlaySettingsBatchResult> = runCatchingIO {
        val body = buildJsonObject {
            profile?.takeIf { it.isNotEmpty() }?.let { put("profile", it) }
            deviceId?.takeIf { it.isNotEmpty() }?.let { put("deviceId", it) }
            put("writes", json.encodeToJsonElement(ListSerializer(JellyPlaySettingWrite.serializer()), writes))
        }
        val text = requester.postForText("/jellyplay/settings", body.toString(), "JellyPlay settings push failed")
        json.decodeFromString<JellyPlaySettingsBatchResult>(text)
    }

    override suspend fun resetNamespace(ns: String, profile: String?): Result<Unit> = runCatchingIO {
        val path = profileQuery("/jellyplay/settings/$ns", profile)
        requester.deleteStatusOnly(path, "JellyPlay namespace reset failed")
    }

    override suspend fun resolveProfile(profile: String?): Result<JellyPlaySettingsSnapshot> = runCatchingIO {
        val segment = profile?.takeIf { it.isNotEmpty() }?.let { "/$it" } ?: "/"
        val text = requester.getBodyText("/jellyplay/settings/resolved$segment")
            ?: throw Exception("JellyPlay resolved profile fetch failed: 404")
        json.decodeFromString<JellyPlaySettingsSnapshot>(text)
    }

    override fun settingsStream(): Flow<JellyPlaySseEvent> = sseStream("/jellyplay/settings/stream")

    // ------------------------------------------------------------------
    // events & devices
    // ------------------------------------------------------------------

    override suspend fun registerDevice(deviceId: String, name: String, platform: String, appVersion: String): Result<Unit> =
        runCatchingIO {
            val body = buildJsonObject {
                put("deviceId", deviceId)
                put("name", name)
                put("platform", platform)
                put("appVersion", appVersion)
            }
            requester.postStatusOnly("/jellyplay/devices", "JellyPlay device registration failed", body.toString())
        }

    override suspend fun unregisterDevice(deviceId: String): Result<Unit> = runCatchingIO {
        requester.deleteStatusOnly("/jellyplay/devices/$deviceId", "JellyPlay device unregister failed")
    }

    override suspend fun getDevices(): Result<List<JellyPlayDevice>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/devices")
            ?: throw Exception("JellyPlay devices fetch failed: 404")
        json.decodeFromString<List<JellyPlayDevice>>(text)
    }

    override fun eventsStream(): Flow<JellyPlaySseEvent> = sseStream("/jellyplay/events/stream")

    override suspend fun broadcast(title: String, body: String, url: String?): Result<Unit> = runCatchingIO {
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            url?.let { put("url", it) }
        }
        requester.postStatusOnly("/jellyplay/broadcast", "JellyPlay broadcast failed", payload.toString())
    }

    // ------------------------------------------------------------------
    // messages
    // ------------------------------------------------------------------

    override suspend fun getMessages(): Result<List<JellyPlayMessage>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/messages")
            ?: throw Exception("JellyPlay messages fetch failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayMessage.serializer()), wrapper.getValue("messages"))
    }

    override suspend fun markMessageRead(messageId: String): Result<Unit> = runCatchingIO {
        requester.postStatusOnly("/jellyplay/messages/$messageId/read", "JellyPlay message read failed")
    }

    // ------------------------------------------------------------------
    // Seerr bridge
    // ------------------------------------------------------------------

    override suspend fun seerrStatus(): Result<JellyPlaySeerrStatus> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/seerr/status")
            ?: throw Exception("JellyPlay Seerr status failed: 404")
        json.decodeFromString<JellyPlaySeerrStatus>(text)
    }

    override suspend fun seerrLogin(
        authType: String,
        username: String?,
        password: String?,
        quickConnectSecret: String?,
    ): Result<Unit> = runCatchingIO {
        val body = buildJsonObject {
            put("authType", authType)
            username?.let { put("username", it) }
            password?.let { put("password", it) }
            quickConnectSecret?.let { put("quickConnectSecret", it) }
        }
        requester.postStatusOnly("/jellyplay/seerr/login", "JellyPlay Seerr login failed", body.toString())
    }

    override suspend fun seerrLogout(): Result<Unit> = runCatchingIO {
        requester.deleteStatusOnly("/jellyplay/seerr/logout", "JellyPlay Seerr logout failed")
    }

    // ------------------------------------------------------------------
    // ratings
    // ------------------------------------------------------------------

    override suspend fun getMdbListRatings(imdbId: String): Result<JellyPlayRatingsResult?> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/mdblist/ratings?imdbId=" + java.net.URLEncoder.encode(imdbId, "UTF-8"))
            ?: return@runCatchingIO null
        json.decodeFromString<JellyPlayRatingsResult>(text)
    }

    override suspend fun getTmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Result<Map<Int, JellyPlayEpisodeRatings>?> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/tmdb/seasonRatings?tmdbId=" + java.net.URLEncoder.encode(tmdbId, "UTF-8") + "&seasonNumber=$seasonNumber")
            ?: return@runCatchingIO null
        val wrapper = json.decodeFromString<JsonObject>(text)
        val episodes = wrapper.getValue("episodes").jsonObject
        buildMap {
            episodes.forEach { (number, payload) ->
                put(number.toIntOrNull() ?: return@forEach, json.decodeFromJsonElement(JellyPlayEpisodeRatings.serializer(), payload))
            }
        }
    }

    // ------------------------------------------------------------------
    // recommendations
    // ------------------------------------------------------------------

    override suspend fun getJellyPlaySimilarItems(itemId: String, limit: Int): Result<List<JellyPlayScoredItem>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/items/$itemId/similar?limit=$limit")
            ?: throw Exception("JellyPlay similar items failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayScoredItem.serializer()), wrapper.getValue("items"))
    }

    // ------------------------------------------------------------------
    // anime markers
    // ------------------------------------------------------------------

    override suspend fun getAnimeMarkers(seriesId: String, providerSeriesId: String): Result<JellyPlaySeriesMarkers?> = runCatchingIO {
        val text = requester.getBodyText(
            "/jellyplay/animemarkers/series?seriesId=" + java.net.URLEncoder.encode(seriesId, "UTF-8") +
                "&providerSeriesId=" + java.net.URLEncoder.encode(providerSeriesId, "UTF-8"),
        ) ?: return@runCatchingIO null
        json.decodeFromString<JellyPlaySeriesMarkers>(text)
    }

    // ------------------------------------------------------------------
    // rows
    // ------------------------------------------------------------------

    override suspend fun getCustomRow(title: String): Result<JellyPlayRowResult?> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/rows/items?title=" + java.net.URLEncoder.encode(title, "UTF-8"))
            ?: return@runCatchingIO null
        json.decodeFromString<JellyPlayRowResult>(text)
    }

    override suspend fun getCustomRowCatalog(): Result<JellyPlayRowCatalog?> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/rows") ?: return@runCatchingIO null
        json.decodeFromString<JellyPlayRowCatalog>(text)
    }

    override suspend fun getSeasonalRow(keyword: String?): Result<JellyPlayRowResult?> = runCatchingIO {
        val suffix = keyword?.let { "?keyword=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        val text = requester.getBodyText("/jellyplay/seasonal/row$suffix") ?: return@runCatchingIO null
        json.decodeFromString<JellyPlayRowResult>(text)
    }

    // ------------------------------------------------------------------
    // user data
    // ------------------------------------------------------------------

    override suspend fun getBookmarks(itemId: String): Result<List<JellyPlayBookmark>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/bookmarks/" + java.net.URLEncoder.encode(itemId, "UTF-8"))
            ?: throw Exception("JellyPlay bookmarks fetch failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayBookmark.serializer()), wrapper.getValue("bookmarks"))
    }

    override suspend fun upsertBookmark(itemId: String, request: JellyPlayBookmarkRequest): Result<JellyPlayBookmark> = runCatchingIO {
        val text = requester.postForText(
            "/jellyplay/bookmarks/" + java.net.URLEncoder.encode(itemId, "UTF-8"),
            json.encodeToString(JellyPlayBookmarkRequest.serializer(), request),
            "JellyPlay bookmark save failed",
        )
        json.decodeFromString<JellyPlayBookmark>(text)
    }

    override suspend fun deleteBookmark(itemId: String, bookmarkId: String): Result<Unit> = runCatchingIO {
        requester.deleteStatusOnly(
            "/jellyplay/bookmarks/" + java.net.URLEncoder.encode(itemId, "UTF-8") + "/" + java.net.URLEncoder.encode(bookmarkId, "UTF-8"),
            "JellyPlay bookmark delete failed",
        )
    }

    override suspend fun getUserRatings(filter: String?): Result<List<JellyPlayUserRating>> = runCatchingIO {
        val suffix = filter?.let { "?filter=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
        val text = requester.getBodyText("/jellyplay/userratings/mine$suffix")
            ?: throw Exception("JellyPlay user ratings fetch failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayUserRating.serializer()), wrapper.getValue("ratings"))
    }

    // ------------------------------------------------------------------
    // transcodes
    // ------------------------------------------------------------------

    override suspend fun getActiveTranscodes(): Result<List<JellyPlayActiveTranscode>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/transcodes/active")
            ?: throw Exception("JellyPlay active transcodes fetch failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayActiveTranscode.serializer()), wrapper.getValue("transcodes"))
    }

    override suspend fun getMyTranscodes(): Result<List<JellyPlayActiveTranscode>> = runCatchingIO {
        val text = requester.getBodyText("/jellyplay/transcodes/mine")
            ?: throw Exception("JellyPlay transcodes fetch failed: 404")
        val wrapper = json.decodeFromString<JsonObject>(text)
        json.decodeFromJsonElement(ListSerializer(JellyPlayActiveTranscode.serializer()), wrapper.getValue("transcodes"))
    }

    override suspend fun cancelTranscode(sessionId: String): Result<Unit> = runCatchingIO {
        requester.deleteStatusOnly("/jellyplay/transcodes/active/" + java.net.URLEncoder.encode(sessionId, "UTF-8"), "JellyPlay transcode cancel failed")
    }

    // ------------------------------------------------------------------
    // plumbing
    // ------------------------------------------------------------------

    private inline fun <T> runCatchingIO(block: () -> T): Result<T> = runCatching(block)

    private fun profileQuery(path: String, profile: String?): String {
        val value = profile?.takeIf { it.isNotEmpty() } ?: return path
        val separator = if (path.contains('?')) "&" else "?"
        return "$path${separator}profile=$value"
    }

    /**
     * Cold SSE flow. The parse loop runs on Dispatchers.IO inside the flow's
     * scope; collector cancellation aborts the blocking read via call.cancel()
     * from a cancellation watcher (blocking OkHttp reads do not observe
     * coroutine cancellation on their own).
     */
    private fun sseStream(path: String): Flow<JellyPlaySseEvent> = callbackFlow {
        val session = requester.requireSession()
        val client = engine.okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(session.base + path)
            .tokenAuthHeader(session.token)
            .header("Accept", "text/event-stream")
            .build()
        val call = client.newCall(request)

        val readerJob = launch(Dispatchers.IO) {
            var id = 0L
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        close(IllegalStateException("JellyPlay SSE connect failed: ${response.code}"))
                        return@use
                    }
                    val source = response.body?.source() ?: run { close(); return@use }
                    var eventName: String? = null
                    var data = StringBuilder()
                    var eventId = 0L
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.isEmpty() -> {
                                if (eventName != null || data.isNotEmpty()) {
                                    trySend(JellyPlaySseEvent(eventId, eventName.orEmpty(), data.toString()))
                                }
                                eventName = null
                                data = StringBuilder()
                            }
                            line.startsWith("id:") -> eventId = line.removePrefix("id:").trim().toLongOrNull() ?: ++id
                            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                            // retry: and comment lines ignored
                        }
                    }
                }
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }

        awaitClose {
            call.cancel()
            readerJob.cancel()
        }
    }
}
