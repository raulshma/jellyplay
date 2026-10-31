package com.raulshma.jellyplay.e2e.player

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Static environment for the video-player E2E suite. The suite runs against
 * the Dockerized Jellyfin fixture from tools/e2e/bootstrap-jellyfin.sh, which
 * the host runner exposes to the device via `adb reverse tcp:8096` — hence the
 * loopback default. Override per run with
 * `-Pandroid.testInstrumentationRunnerArguments.serverUrl=http://<host>:8096`.
 */
object PlayerE2eEnv {
    const val APP_ID = "com.raulshma.jellyplay.dev"
    const val DEEP_LINK_SCHEME = "jellyplay"

    const val DEFAULT_SERVER = "http://127.0.0.1:8096"
    const val FIXTURE_USER = "harness"
    const val FIXTURE_PASSWORD = "harness-e2e-pass"

    /** Fixture display names WITHOUT the year (Jellyfin splits "Foo (2026)" into Name + year). */
    const val MOVIE_CLIP = "Harness Test Clip"
    const val TRACKS_CLIP = "E2E Tracks Clip"
    const val EPISODE_1 = "Harness E01"
    const val EPISODE_2 = "Harness E02"
    const val EPISODE_3 = "Harness E03"

    /** Durations baked by bootstrap-jellyfin.sh (ms). */
    const val TRACKS_CLIP_DURATION_MS = 240_000L
    const val EPISODE_DURATION_MS = 150_000L

    val serverUrl: String =
        InstrumentationRegistry.getArguments()
            .getString("serverUrl", DEFAULT_SERVER)
            .trimEnd('/')

    /** 10_000 ticks per millisecond (Jellyfin's 100ns clock). */
    const val TICKS_PER_MS = 10_000L
}

/**
 * Minimal Jellyfin REST client for server-side truth assertions (the UI can
 * lie — ticks on the server cannot). Uses only what the fixture grants:
 * AuthenticateByName + the user-data endpoints the playback reporter writes.
 */
class PlayerFixtureClient(private val base: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val authHeader =
        """MediaBrowser Client="jellyplay-e2e", Device="instrumented", DeviceId="jellyplay-e2e-tests", Version="1.0""""

    @Volatile private var token: String? = null
    @Volatile private var userId: String? = null

    private fun request(path: String, method: String = "GET", body: String? = null): Pair<Int, String> {
        val auth = token?.let { """Authorization: MediaBrowser Token="$it"""" } ?: authHeader
        val req = Request.Builder()
            .url("$base$path")
            .header("X-Emby-Authorization", auth)
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            return resp.code to (resp.body?.string() ?: "")
        }
    }

    private fun require(path: String, method: String = "GET", body: String? = null): String {
        val (code, payload) = request(path, method, body)
        check(code in 200..299) { "fixture call failed: $method $path -> $code" }
        return payload
    }

    /** AuthenticateByName with a fresh token (independent of the app's session). */
    @Synchronized
    fun authenticate() {
        if (token != null) return
        val payload = require(
            "/Users/AuthenticateByName",
            method = "POST",
            body = """{"Username":"${PlayerE2eEnv.FIXTURE_USER}","Pw":"${PlayerE2eEnv.FIXTURE_PASSWORD}"}""",
        )
        val obj = json.parseToJsonElement(payload).jsonObject
        token = obj["AccessToken"]!!.jsonPrimitive.content
        userId = obj["User"]!!.jsonObject["Id"]!!.jsonPrimitive.content
    }

    private fun userIdOrThrow(): String {
        authenticate()
        return userId!!
    }

    /** Id lookup by exact display name + item type (mirrors bootstrap's name-scoped rule). */
    fun itemId(name: String, type: String = "Movie"): String {
        authenticate()
        val payload = require(
            "/Items?searchTerm=${java.net.URLEncoder.encode(name, "UTF-8")}" +
                "&Recursive=true&IncludeItemTypes=$type&Limit=20",
        )
        val items = json.parseToJsonElement(payload).jsonObject["Items"]!!.jsonArray
        return items
            .map { it.jsonObject }
            .first { it["Name"]!!.jsonPrimitive.content == name }["Id"]!!.jsonPrimitive.content
    }

    fun episodeId(name: String): String = itemId(name, type = "Episode")

    /** /Users/{uid}/Items/{id} — the user-data view the resume flow reads back. */
    private fun itemUserData(itemId: String): kotlinx.serialization.json.JsonObject {
        val payload = require("/Users/${userIdOrThrow()}/Items/$itemId")
        return json.parseToJsonElement(payload).jsonObject["UserData"]!!.jsonObject
    }

    /** Server-side playback position in TICKS (0 when none). */
    fun playbackPositionTicks(itemId: String): Long =
        itemUserData(itemId)["PlaybackPositionTicks"]?.jsonPrimitive?.longOrNull ?: 0L

    fun playbackPositionSeconds(itemId: String): Long =
        playbackPositionTicks(itemId) / PlayerE2eEnv.TICKS_PER_MS / 1000

    fun isPlayed(itemId: String): Boolean =
        itemUserData(itemId)["Played"]?.jsonPrimitive?.booleanOrNull ?: false

    /** Wipe resume position + played state so a test starts from a clean slate. */
    fun resetUserData(itemId: String) {
        authenticate()
        // Both endpoints are best-effort: one may 404 depending on server state.
        runCatching { require("/Users/${userIdOrThrow()}/Items/$itemId", method = "DELETE") }
        runCatching { require("/Users/${userIdOrThrow()}/PlayedItems/$itemId", method = "DELETE") }
    }

    /** True only after trickplay tiles exist (bootstrap triggers extraction). */
    fun hasTrickplay(itemId: String): Boolean = runCatching {
        require("/Items/$itemId/Trickplay").contains("Width")
    }.getOrDefault(false)
}
