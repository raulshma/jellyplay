package com.raulshma.jellyplay.core.network.api

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.DayOfWeek as SdkDayOfWeek
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.TaskTriggerInfoType
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [AdminApiClientImpl]'s near-static dashboards caches, enum fallbacks and
 * remote-command shaping through a recording [org.jellyfin.sdk.api.client.ApiClient]
 * (real [JellyfinApiEngine]; the SDK operations run for real over the recorded
 * transport):
 *  1. `getSystemInfo` / `getItemCounts` are served from a short-TTL cache —
 *     back-to-back calls issue exactly ONE server round-trip;
 *  2. `getItemCounts` derives `totalCount` from the per-library axes;
 *  3. an empty scheduled-tasks response degrades to an empty list;
 *  4. remote commands fall back to safe defaults for unknown wire strings:
 *     play → PLAY_NOW, general command → SET_VOLUME with the nil controller id
 *     and empty arguments; STOP playstate travels in the path;
 *  5. task-trigger updates map day-of-week case-insensitively and degrade an
 *     unknown trigger type to INTERVAL_TRIGGER.
 */
class AdminApiClientImplTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: RecordingApiClient
    private lateinit var admin: AdminApiClientImpl

    @BeforeTest
    fun setup() {
        client = RecordingApiClient()
        engine = JellyfinApiEngine(
            jellyfinLazy = Lazy { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        engine.updateApi(client)
        admin = AdminApiClientImpl(engine)
    }

    private class RecordingApiClient : org.jellyfin.sdk.api.client.ApiClient() {
        var nextBody: String = "{}"
        val requests = mutableListOf<RecordedRequest>()
        override val baseUrl = "https://test.example.com"
        override val accessToken = "token-123"
        override val clientInfo = org.jellyfin.sdk.model.ClientInfo(name = "test", version = "1.0.0")
        override val deviceInfo = org.jellyfin.sdk.model.DeviceInfo(id = "test", name = "test")
        override val httpClientOptions = org.jellyfin.sdk.api.client.HttpClientOptions()
        override val webSocket: org.jellyfin.sdk.api.sockets.SocketApi = mockk(relaxed = true)
        override fun update(
            baseUrl: String?,
            accessToken: String?,
            clientInfo: org.jellyfin.sdk.model.ClientInfo,
            deviceInfo: org.jellyfin.sdk.model.DeviceInfo,
        ) = Unit
        override suspend fun request(
            method: org.jellyfin.sdk.api.client.HttpMethod,
            pathTemplate: String,
            pathParameters: Map<String, Any?>,
            queryParameters: Map<String, Any?>,
            requestBody: Any?,
        ): org.jellyfin.sdk.api.client.RawResponse {
            requests += RecordedRequest(method.name, pathTemplate, pathParameters, queryParameters, requestBody)
            return org.jellyfin.sdk.api.client.RawResponse(nextBody.toByteArray(), 200, emptyMap())
        }
    }

    private data class RecordedRequest(
        val method: String,
        val pathTemplate: String,
        val pathParameters: Map<String, Any?>,
        val queryParameters: Map<String, Any?>,
        val requestBody: Any?,
    )

    @Test
    fun `getSystemInfo is served from the TTL cache on a back-to-back read`() = runTest {
        // SDK SystemInfo requires HasPendingRestart / IsShuttingDown /
        // SupportsLibraryMonitor / WebSocketPortNumber (no defaults) — a body
        // without them is rejected with InvalidContentException.
        client.nextBody = """
            {"ServerName":"Jelly","Version":"10.9.0","HasPendingRestart":false,
             "IsShuttingDown":false,"SupportsLibraryMonitor":true,"WebSocketPortNumber":8096}
        """.trimIndent()

        val first = admin.getSystemInfo().getOrThrow()
        val second = admin.getSystemInfo().getOrThrow()

        assertEquals("Jelly", first.serverName)
        assertEquals(first, second)
        assertEquals(1, client.requests.size, "the second read must not re-hit /System/Info")
    }

    @Test
    fun `getItemCounts derives totalCount and is cached`() = runTest {
        client.nextBody = """
            {"MovieCount":10,"SeriesCount":3,"EpisodeCount":50,"ArtistCount":0,
             "ProgramCount":0,"TrailerCount":0,"SongCount":100,"AlbumCount":5,
             "MusicVideoCount":2,"BoxSetCount":0,"BookCount":7,"ItemCount":0}
        """.trimIndent()

        val counts = admin.getItemCounts().getOrThrow()
        admin.getItemCounts()

        assertEquals(10L, counts.movieCount)
        assertEquals(100L, counts.songCount)
        // 10 + 3 + 50 + 5 + 100 + 2 + 7
        assertEquals(177L, counts.totalCount)
        assertEquals(1, client.requests.size, "the second read must not re-hit /Items/Counts")
    }

    @Test
    fun `an empty scheduled-tasks response degrades to an empty list`() = runTest {
        // A literal JSON null cannot satisfy the SDK's non-null List<TaskInfo>
        // serializer (InvalidContentException), so the degenerate shape the
        // server can actually send is an empty array.
        client.nextBody = "[]"

        val result = admin.getScheduledTasks(isHidden = null, isEnabled = null)

        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `getScheduledTasks maps identity and state fields`() = runTest {
        client.nextBody = """
            [{"Id":"t1","Name":"Scan media library","Key":"RefreshLibrary",
              "State":"Running","IsHidden":false,"CurrentProgressPercentage":12.5}]
        """.trimIndent()

        val tasks = admin.getScheduledTasks(isHidden = false, isEnabled = true).getOrThrow()

        val task = tasks.single()
        assertEquals("t1", task.id)
        assertEquals("RefreshLibrary", task.key)
        assertEquals("Scan media library", task.name)
        assertEquals(12.5, task.currentProgressPercentage)
    }

    @Test
    fun `play falls back to PLAY_NOW for an unknown command string`() = runTest {
        admin.play(
            sessionId = "s1",
            playCommand = "bogus",
            itemIds = listOf("2a2a2a2a-1111-4222-8222-333333333333"),
            startPositionTicks = 0L,
            mediaSourceId = null,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            startIndex = null,
        ).getOrThrow()

        val request = client.requests.single()
        assertEquals("/Sessions/{sessionId}/Playing", request.pathTemplate)
        assertEquals("s1", request.pathParameters["sessionId"])
        assertEquals(
            PlayCommand.PLAY_NOW,
            request.queryParameters["playCommand"],
            "an unparseable command must degrade to PLAY_NOW, never throw",
        )
    }

    @Test
    fun `stopSession issues the STOP playstate command in the path`() = runTest {
        admin.stopSession("s1").getOrThrow()

        val request = client.requests.single()
        assertEquals("/Sessions/{sessionId}/Playing/{command}", request.pathTemplate)
        assertEquals(PlaystateCommand.STOP, request.pathParameters["command"])
    }

    @Test
    fun `sendGeneralCommand degrades to SET_VOLUME with nil controller and empty arguments`() = runTest {
        admin.sendGeneralCommand(
            sessionId = "s1",
            commandName = "not-a-command",
            controllingUserId = null,
            arguments = null,
        ).getOrThrow()

        val body = client.requests.single().requestBody as org.jellyfin.sdk.model.api.GeneralCommand
        assertEquals(GeneralCommandType.SET_VOLUME, body.name)
        assertEquals(java.util.UUID(0L, 0L), body.controllingUserId)
        assertTrue(body.arguments.isEmpty())
    }

    @Test
    fun `updateTaskTriggers maps day-of-week case-insensitively and degrades unknown types`() = runTest {
        admin.updateTaskTriggers(
            taskId = "t1",
            triggers = listOf(
                com.raulshma.jellyplay.core.model.TaskTriggerInfo(
                    type = "DailyTrigger",
                    timeOfDayTicks = null,
                    intervalTicks = null,
                    dayOfWeek = null,
                    maxRuntimeTicks = null,
                ),
                com.raulshma.jellyplay.core.model.TaskTriggerInfo(
                    type = "not-a-trigger",
                    timeOfDayTicks = null,
                    intervalTicks = null,
                    dayOfWeek = "monday",
                    maxRuntimeTicks = null,
                ),
            ),
        ).getOrThrow()

        val body = client.requests.single().requestBody as List<*>
        val triggers = body.filterIsInstance<org.jellyfin.sdk.model.api.TaskTriggerInfo>()
        assertEquals(2, triggers.size)
        assertEquals(TaskTriggerInfoType.DAILY_TRIGGER, triggers[0].type)
        assertEquals(
            TaskTriggerInfoType.INTERVAL_TRIGGER,
            triggers[1].type,
            "an unknown trigger type must degrade to INTERVAL_TRIGGER",
        )
        assertEquals(SdkDayOfWeek.MONDAY, triggers[1].dayOfWeek)
    }

    @Test
    fun `getLogFileContent requests the log by name query and decodes the body`() = runTest {
        client.nextBody = "log line 1\nlog line 2"

        val content = admin.getLogFileContent("server-6381.log").getOrThrow()

        assertEquals("log line 1\nlog line 2", content)
        val request = client.requests.single()
        assertEquals("/System/Logs/Log", request.pathTemplate)
        assertEquals("server-6381.log", request.queryParameters["name"])
    }
}
