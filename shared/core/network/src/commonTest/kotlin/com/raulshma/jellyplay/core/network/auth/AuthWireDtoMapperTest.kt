package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ServerInfo
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the Phase W wire DTOs' PascalCase field contract and the
 * DTO→core.model mapping semantics (mirrors the jvmShared
 * AuthApiClientImpl.toUserInfo / probeServerInfo behavior these tests
 * substitute for). Decoding runs through the same lenient Json configuration
 * the wasm client uses.
 */
class AuthWireDtoMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun serverAdminUserJson() = """
        {
          "User": {
            "Id": "a1b2c3",
            "Name": "alice",
            "PrimaryImageTag": "img-tag",
            "Policy": {
              "IsAdministrator": true,
              "EnableContentDeletion": true,
              "MaxParentalRating": 12,
              "EnableAllFolders": false,
              "EnabledFolders": ["f1", "f2"]
            },
            "UnknownFutureField": {"ignored": true}
          },
          "AccessToken": "tok-1",
          "ServerId": "server-9",
          "SessionInfo": {"ignored": true}
        }
    """.trimIndent()

    @Test
    fun `authentication result decodes PascalCase wire and ignores unknown keys`() {
        val result = json.decodeFromString<AuthenticationResultDto>(serverAdminUserJson())

        assertEquals("tok-1", result.accessToken)
        assertEquals("a1b2c3", result.user?.id)
        assertEquals("alice", result.user?.name)
        assertEquals("img-tag", result.user?.primaryImageTag)
        assertEquals(true, result.user?.policy?.isAdministrator)
    }

    @Test
    fun `toUserInfo mirrors the jvmShared login mapping`() {
        val result = json.decodeFromString<AuthenticationResultDto>(serverAdminUserJson())
        val userInfo = result.user!!.toUserInfo(
            serverAddress = "https://jf.example",
            accessToken = "tok-1",
            fallbackName = "fallback",
        )

        assertEquals("a1b2c3", userInfo.id)
        assertEquals("alice", userInfo.name)
        assertEquals("https://jf.example", userInfo.serverAddress)
        assertEquals("tok-1", userInfo.accessToken)
        assertEquals(true, userInfo.isAdmin)
        assertEquals(true, userInfo.canDeleteContent)
        assertEquals(12, userInfo.maxParentalAgeRating)
        assertEquals("img-tag", userInfo.primaryImageTag)
        assertEquals(listOf("f1", "f2"), userInfo.enabledFolderIds)
        assertNull(userInfo.serverId, "jvmShared mapping leaves serverId unset")
    }

    @Test
    fun `folder restriction collapses when all folders are enabled`() {
        val result = json.decodeFromString<AuthenticationResultDto>(
            """{"User":{"Id":"u1","Name":"bob","Policy":{"EnableAllFolders":true,
               "EnabledFolders":["f1"]}},"AccessToken":"t"}""".trimIndent().replace("\n", ""),
        )
        val userInfo = result.user!!.toUserInfo("addr", "t", fallbackName = "bob")
        assertEquals(emptyList(), userInfo.enabledFolderIds, "enableAllFolders=true ignores the list")
    }

    @Test
    fun `missing policy fields fall back to non-admin defaults`() {
        val result = json.decodeFromString<AuthenticationResultDto>(
            """{"User":{"Id":"u1"},"AccessToken":"t"}""",
        )
        val userInfo = result.user!!.toUserInfo("addr", "t", fallbackName = "fallback-name")
        assertEquals("fallback-name", userInfo.name, "missing name falls back to the login name")
        assertEquals(false, userInfo.isAdmin)
        assertEquals(false, userInfo.canDeleteContent)
        assertNull(userInfo.maxParentalAgeRating)
        assertEquals(emptyList(), userInfo.enabledFolderIds)
    }

    @Test
    fun `public system info maps with id and name fallbacks`() {
        val full = json.decodeFromString<PublicSystemInfoDto>(
            """{"Id":"srv-1","ServerName":"Media","Version":"10.9","ProductName":"Jellyfin"}""",
        )
        assertEquals(
            ServerInfo(id = "srv-1", name = "Media", address = "https://a"),
            full.toServerInfo(address = "https://a", fallbackServerId = "random"),
        )

        val empty = PublicSystemInfoDto()
        val mapped = empty.toServerInfo(address = "https://a", fallbackServerId = "random-id")
        assertEquals("random-id", mapped.id, "missing id uses the caller's random fallback")
        assertEquals("Jellyfin Server", mapped.name, "missing name uses the generic fallback")
        assertEquals("https://a", mapped.address)
    }

    @Test
    fun `authenticate-by-name request serializes to PascalCase wire`() {
        assertEquals(
            """{"Username":"alice","Pw":"secret"}""",
            json.encodeToString(AuthenticateByNameRequestDto(username = "alice", pw = "secret")),
        )
    }

    @Test
    fun `quick connect result decodes initiate and state payloads`() {
        val initiate = json.decodeFromString<QuickConnectResultDto>(
            """{"Secret":"s1","Code":"123456","Authenticated":false}""",
        )
        assertEquals("s1", initiate.secret)
        assertEquals("123456", initiate.code)

        val state = json.decodeFromString<QuickConnectResultDto>(
            """{"Authenticated":true,"Secret":"s1"}""",
        )
        assertTrue(state.authenticated)
        assertEquals("s1", state.secret)
    }

    @Test
    fun `capabilities payload mirrors the engine's command list and flags`() {
        val caps = defaultClientCapabilities()
        assertEquals(listOf("Video", "Audio"), caps.playableMediaTypes)
        assertEquals(SUPPORTED_REMOTE_COMMANDS, caps.supportedCommands)
        assertEquals(15, caps.supportedCommands.size)
        assertEquals(true, caps.supportsMediaControl)
        assertEquals(true, caps.supportsPersistentIdentifier)
        assertNull(caps.deviceProfile, "wasm v1 sends no DeviceProfile (documented cut)")
        // Spot-pin order against JellyfinApiEngine.SUPPORTED_REMOTE_COMMANDS.
        assertEquals(
            listOf("SetVolume", "VolumeUp", "VolumeDown", "Mute", "Unmute", "ToggleMute"),
            caps.supportedCommands.take(6),
        )
    }
}
