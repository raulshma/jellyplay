package com.raulshma.jellyplay.core.network.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [UserApiClientImpl]'s mapping + read-modify-write discipline through a
 * recording [org.jellyfin.sdk.api.client.ApiClient]:
 *  1. managed users / current user map identity + policy flags;
 *  2. `getLibraryFoldersForEditor` maps folder identity + collection type and
 *     FAILS on an empty server response (never silently returns nothing);
 *  3. `getParentalRatings` groups ratings sharing a score/subScore into one
 *     "/"-joined label (web parity), sorted by score, dropping unscored ones;
 *  4. `renameUser` re-reads the full DTO, renames and POSTs it back (never a
 *     partial UserDto, which would clear policy server-side);
 *  5. `updateUserPassword` with a null password sends a RESET.
 */
class UserApiClientImplTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var client: RecordingApiClient
    private lateinit var users: UserApiClientImpl

    private val userId = "2a2a2a2a-1111-4222-8222-333333333333"

    @BeforeTest
    fun setup() {
        client = RecordingApiClient()
        engine = JellyfinApiEngine(
            jellyfinLazy = dagger.Lazy { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = dagger.Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = com.raulshma.jellyplay.core.network.failover.ServerAddressRouter(),
        )
        engine.updateApi(client)
        users = UserApiClientImpl(engine)
    }

    private class RecordingApiClient : org.jellyfin.sdk.api.client.ApiClient() {
        var nextBody: String = "{}"

        /**
         * Optional per-request body override — return null to fall back to
         * [nextBody]. Lets a test emulate a stateful server across a
         * read-modify-write sequence (the impl re-reads after its POST).
         */
        var bodyOverride: ((method: String, pathTemplate: String) -> String?)? = null
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
            requests += RecordedRequest(method.name, pathTemplate, requestBody)
            val body = bodyOverride?.invoke(method.name, pathTemplate) ?: nextBody
            return org.jellyfin.sdk.api.client.RawResponse(body.toByteArray(), 200, emptyMap())
        }
    }

    private data class RecordedRequest(
        val method: String,
        val pathTemplate: String,
        val requestBody: Any?,
    )

    private fun userDtoJson(id: String, name: String, admin: Boolean = false) = """
        {"Id":"$id","Name":"$name","HasPassword":false,"HasConfiguredPassword":false,
         "HasConfiguredEasyPassword":false,"Policy":${fullPolicyJson(admin)}}
    """.trimIndent()

    /**
     * The server policy DTO has ~25 REQUIRED primitives, so the fixture must
     * spell them all out for the deserializer to accept it.
     */
    private fun fullPolicyJson(admin: Boolean) = """
        {"IsAdministrator":$admin,"IsHidden":false,"IsDisabled":false,
         "EnableUserPreferenceAccess":true,"EnableRemoteControlOfOtherUsers":false,
         "EnableSharedDeviceControl":false,"EnableRemoteAccess":true,
         "EnableLiveTvManagement":false,"EnableLiveTvAccess":true,
         "EnableMediaPlayback":true,"EnableAudioPlaybackTranscoding":true,
         "EnableVideoPlaybackTranscoding":true,"EnablePlaybackRemuxing":true,
         "ForceRemoteSourceTranscoding":false,"EnableContentDeletion":false,
         "EnableContentDownloading":true,"EnableSyncTranscoding":true,
         "EnableMediaConversion":false,"EnableAllDevices":true,"EnableAllChannels":true,
         "EnableAllFolders":true,"InvalidLoginAttemptCount":0,
         "LoginAttemptsBeforeLockout":-1,"MaxActiveSessions":0,
         "EnablePublicSharing":false,"RemoteClientBitrateLimit":0,
         "AuthenticationProviderId":"Default","PasswordResetProviderId":"Default",
         "SyncPlayAccess":"CreateAndJoinGroups"}
    """.trimIndent()

    @Test
    fun `getManagedUsers maps identity and the admin policy flag`() = runTest {
        client.nextBody = """
            [${userDtoJson(userId, "admin-user", admin = true)},
             ${userDtoJson("3a3a3a3a-1111-4222-8222-333333333333", "kid", admin = false)}]
        """.trimIndent()

        val managed = users.getManagedUsers().getOrThrow()

        assertEquals(2, managed.size)
        assertEquals("admin-user", managed[0].name)
        assertTrue(managed[0].policy.isAdministrator)
        assertTrue(!managed[1].policy.isAdministrator)
    }

    @Test
    fun `getCurrentUserId returns the raw id string`() = runTest {
        client.nextBody = userDtoJson(userId, "me")

        assertEquals(userId, users.getCurrentUserId().getOrThrow())
    }

    @Test
    fun `getLibraryFoldersForEditor maps folder identity and collection type`() = runTest {
        client.nextBody = """
            {"TotalRecordCount":1,"StartIndex":0,"Items":[
              {"Id":"f1f1f1f1-1111-4222-8222-333333333333","Name":"Movies",
               "Type":"CollectionFolder","CollectionType":"movies"}
            ]}
        """.trimIndent()

        val folders = users.getLibraryFoldersForEditor().getOrThrow()

        val folder = folders.single()
        assertEquals("Movies", folder.name)
        assertEquals("movies", folder.collectionType)
        assertEquals("CollectionFolder", folder.type)
    }

    @Test
    fun `getLibraryFoldersForEditor fails on an empty server response`() = runTest {
        client.nextBody = "null"

        val result = users.getLibraryFoldersForEditor()

        assertTrue(result.isFailure, "an empty response must surface as a failure, not an empty editor")
    }

    @Test
    fun `getParentalRatings groups same-score ratings into one slash-joined label`() = runTest {
        client.nextBody = """
            [{"Name":"TV-14","RatingScore":{"score":13}},
             {"Name":"PG-13","RatingScore":{"score":13}},
             {"Name":"PG","RatingScore":{"score":7}},
             {"Name":"Unscored"},
             {"Name":"NC-17","RatingScore":{"score":18}}]
        """.trimIndent()

        val ratings = users.getParentalRatings().getOrThrow()

        assertEquals(3, ratings.size, "unscored entries drop; 13-group collapses")
        assertEquals(7, ratings[0].score)
        assertEquals("PG", ratings[0].name)
        assertEquals(13, ratings[1].score)
        assertEquals("TV-14/PG-13", ratings[1].name, "same-score names join with / in server order")
        assertEquals(18, ratings[2].score)
        assertNull(ratings[0].subScore)
    }

    @Test
    fun `renameUser re-reads the full DTO and posts it back renamed`() = runTest {
        client.nextBody = userDtoJson(userId, "old-name")
        // The impl's flow is GET -> POST -> re-read GET. A stateful server
        // would answer the re-read with the new name; emulate that by flipping
        // the served DTO once the POST has travelled (the POST body itself is
        // never decoded — updateUser returns Response<Unit>).
        var renamed = false
        client.bodyOverride = { method, _ ->
            if (method == "POST") {
                renamed = true
                null
            } else if (renamed) {
                userDtoJson(userId, "new-name")
            } else {
                null
            }
        }

        val renamedUser = users.renameUser(userId, "new-name").getOrThrow()

        assertEquals("new-name", renamedUser.name)
        val posted = client.requests.first { it.pathTemplate == "/Users" && it.method == "POST" }
        val body = posted.requestBody as org.jellyfin.sdk.model.api.UserDto
        assertEquals("new-name", body.name)
        assertTrue(body.policy != null, "the POSTed DTO must carry the full policy back")
    }

    @Test
    fun `a null password update sends a password RESET`() = runTest {
        users.updateUserPassword(userId, newPassword = null).getOrThrow()

        val body = client.requests.single().requestBody as org.jellyfin.sdk.model.api.UpdateUserPassword
        assertNull(body.newPw)
        assertTrue(body.resetPassword)
    }

    @Test
    fun `a concrete password update carries the new password without resetting`() = runTest {
        users.updateUserPassword(userId, newPassword = "s3cret").getOrThrow()

        val body = client.requests.single().requestBody as org.jellyfin.sdk.model.api.UpdateUserPassword
        assertEquals("s3cret", body.newPw)
        assertTrue(!body.resetPassword)
        assertNull(body.currentPw, "admin-side resets never require the current password")
    }
}
