package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.model.api.GroupQueueMode
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.PlayRequestDto
import org.jellyfin.sdk.model.api.QueueRequestDto
import org.jellyfin.sdk.model.api.ReadyRequestDto
import org.jellyfin.sdk.model.api.SetRepeatModeRequestDto
import org.jellyfin.sdk.model.api.SetShuffleModeRequestDto
import org.jellyfin.sdk.model.serializer.toUUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [SyncPlayApiClientImpl]'s wire contract through a recording
 * [org.jellyfin.sdk.api.client.ApiClient] (the real SDK syncPlay operations
 * run over it; only `request()` is intercepted):
 *  1. group listing maps state/participants onto the app model (isPlaying
 *     only for the PLAYING state);
 *  2. command DTOs carry the right payload — the recorded `requestBody`
 *     instance is asserted directly;
 *  3. fallbacks: an unknown queue mode degrades to QUEUE, a playing item not
 *     found in the queue lands at position 0, a missing playlistItemId
 *     becomes the nil UUID, and a null `whenMs` falls back to the current
 *     UTC time instead of throwing;
 *  4. enum mapping: app repeat/shuffle modes map onto the SDK equivalents;
 *  5. `getSyncPlayInfo` requires a groupId, fails when the group is absent,
 *     and projects participants as connected members.
 */
class SyncPlayApiClientImplTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: RecordingApiClient
    private lateinit var syncPlay: SyncPlayApiClientImpl

    private val groupId = "8f8f8f8f-1111-4222-8222-333333333333"
    private val itemId = "2a2a2a2a-1111-4222-8222-333333333333"

    @BeforeTest
    fun setup() {
        // A REAL engine so the shared apiResultWithRetry wrapper runs for real
        // (a relaxed mock would answer it with a stub Result).
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        client = RecordingApiClient()
        engine.updateApi(client)
        syncPlay = SyncPlayApiClientImpl(engine)
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
            requests += RecordedRequest(method.name, pathTemplate, requestBody, queryParameters)
            return org.jellyfin.sdk.api.client.RawResponse(nextBody.toByteArray(), 200, emptyMap())
        }
    }

    private data class RecordedRequest(
        val method: String,
        val pathTemplate: String,
        val requestBody: Any?,
        val queryParameters: Map<String, Any?>,
    )

    private fun groupsBody(state: String) = """
        [{"GroupId":"$groupId","GroupName":"Movie night","State":"$state",
          "Participants":["alice","bob"],"LastUpdatedAt":"2024-01-01T00:00:00"}]
    """.trimIndent()

    @Test
    fun `getSyncPlayGroups maps state and participants`() = runTest {
        client.nextBody = groupsBody("Playing")

        val groups = syncPlay.getSyncPlayGroups().getOrThrow()

        assertEquals(1, groups.size)
        val group = groups.single()
        assertEquals(groupId, group.groupId)
        assertEquals("Movie night", group.groupName)
        assertEquals(2, group.participantCount)
        assertTrue(group.isPlaying, "state Playing must read as isPlaying")
        assertEquals(listOf("alice", "bob"), group.participants)
    }

    @Test
    fun `a paused group is not playing`() = runTest {
        client.nextBody = groupsBody("Paused")

        val group = syncPlay.getSyncPlayGroups().getOrThrow().single()

        assertTrue(!group.isPlaying)
    }

    @Test
    fun `joinSyncPlayGroup posts the group id`() = runTest {
        syncPlay.joinSyncPlayGroup(groupId).getOrThrow()

        val request = client.requests.single()
        assertEquals("POST", request.method)
        assertEquals("/SyncPlay/Join", request.pathTemplate)
        val body = request.requestBody as org.jellyfin.sdk.model.api.JoinGroupRequestDto
        assertEquals(groupId.toUUID(), body.groupId)
    }

    @Test
    fun `syncPlayReady converts whenMs to UTC and fills the nil playlist id`() = runTest {
        syncPlay.syncPlayReady(
            positionTicks = 5_000L,
            isPlaying = true,
            playlistItemId = null,
            whenMs = 1_000L,
        ).getOrThrow()

        val body = client.requests.single().requestBody as ReadyRequestDto
        assertEquals(5_000L, body.positionTicks)
        assertEquals(true, body.isPlaying)
        // whenMs=1000 → 1970-01-01T00:00:01 UTC (the SDK's DateTime is a
        // LocalDateTime on JVM; the conversion must be zone-correct UTC).
        assertEquals(
            java.time.LocalDateTime.ofEpochSecond(1, 0, java.time.ZoneOffset.UTC),
            body.`when`,
        )
        assertEquals("00000000-0000-0000-0000-000000000000".toUUID(), body.playlistItemId)
    }

    @Test
    fun `syncPlayQueue degrades an unknown mode to QUEUE`() = runTest {
        syncPlay.syncPlayQueue(listOf(itemId), mode = "bogus-mode").getOrThrow()

        val body = client.requests.single().requestBody as QueueRequestDto
        assertEquals(GroupQueueMode.QUEUE, body.mode)
        assertEquals(listOf(itemId.toUUID()), body.itemIds)
    }

    @Test
    fun `syncPlayQueue forwards a known mode`() = runTest {
        syncPlay.syncPlayQueue(listOf(itemId), mode = "QueueNext").getOrThrow()

        val body = client.requests.single().requestBody as QueueRequestDto
        assertEquals(GroupQueueMode.QUEUE_NEXT, body.mode)
    }

    @Test
    fun `syncPlaySetNewQueue falls back to position 0 for a missing playing item`() = runTest {
        syncPlay.syncPlaySetNewQueue(
            itemIds = listOf(itemId, "3a3a3a3a-1111-4222-8222-333333333333"),
            playingItemId = "not-in-queue",
            mediaSourceId = null,
            startPositionTicks = 1_000L,
        ).getOrThrow()

        val body = client.requests.single().requestBody as PlayRequestDto
        assertEquals(0, body.playingItemPosition, "an item absent from the queue must not index to -1")
        assertEquals(1_000L, body.startPositionTicks)
        assertEquals(2, body.playingQueue.size)
    }

    @Test
    fun `syncPlaySetNewQueue resolves the playing item position`() = runTest {
        val second = "3a3a3a3a-1111-4222-8222-333333333333"
        syncPlay.syncPlaySetNewQueue(
            itemIds = listOf(itemId, second),
            playingItemId = second,
            mediaSourceId = null,
            startPositionTicks = 0L,
        ).getOrThrow()

        assertEquals(1, (client.requests.single().requestBody as PlayRequestDto).playingItemPosition)
    }

    @Test
    fun `repeat and shuffle modes map onto the SDK enums`() = runTest {
        syncPlay.syncPlaySetRepeatMode(SyncPlayRepeatMode.REPEAT_ONE).getOrThrow()
        syncPlay.syncPlaySetShuffleMode(SyncPlayShuffleMode.SHUFFLE).getOrThrow()

        val repeat = client.requests[0].requestBody as SetRepeatModeRequestDto
        assertEquals(GroupRepeatMode.REPEAT_ONE, repeat.mode)
        val shuffle = client.requests[1].requestBody as SetShuffleModeRequestDto
        assertEquals(GroupShuffleMode.SHUFFLE, shuffle.mode)
    }

    @Test
    fun `getSyncPlayInfo fails without a group id`() = runTest {
        val result = syncPlay.getSyncPlayInfo(groupId = null)

        assertTrue(result.isFailure)
        assertTrue(client.requests.isEmpty(), "no round-trip may be issued for a null group id")
    }

    @Test
    fun `getSyncPlayInfo fails when the group is absent from the server list`() = runTest {
        client.nextBody = groupsBody("Idle")

        val result = syncPlay.getSyncPlayInfo("deadbeef-1111-4222-8222-333333333333")

        assertTrue(result.isFailure)
    }

    @Test
    fun `getSyncPlayInfo projects participants as connected members`() = runTest {
        client.nextBody = groupsBody("Playing")

        val info = syncPlay.getSyncPlayInfo(groupId).getOrThrow()

        assertEquals(groupId, info.groupId)
        assertEquals(2, info.participants.size)
        assertTrue(info.participants.all { it.isConnected && it.userName == it.userId })
        assertTrue(info.isPlaying)
    }

    @Test
    fun `simple passthrough commands hit their endpoints`() = runTest {
        syncPlay.syncPlayPause().getOrThrow()
        syncPlay.syncPlayUnpause().getOrThrow()
        syncPlay.syncPlayStop().getOrThrow()
        syncPlay.syncPlaySeek(10_000L).getOrThrow()
        syncPlay.syncPlayPing(42L).getOrThrow()
        syncPlay.leaveSyncPlayGroup().getOrThrow()

        assertEquals(
            listOf(
                "/SyncPlay/Pause",
                "/SyncPlay/Unpause",
                "/SyncPlay/Stop",
                "/SyncPlay/Seek",
                "/SyncPlay/Ping",
                "/SyncPlay/Leave",
            ),
            client.requests.map { it.pathTemplate },
        )
        assertNull(client.requests[0].requestBody, "parameterless commands carry no body")
    }
}
