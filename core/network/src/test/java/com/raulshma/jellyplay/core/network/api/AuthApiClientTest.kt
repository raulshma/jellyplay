package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthApiClientImplTest {

    private lateinit var engine: JellyfinApiEngine
    private lateinit var authClient: AuthApiClientImpl
    private lateinit var addressRouter: ServerAddressRouter

    private val testServer = ServerInfo(
        id = "server-1",
        name = "Test Server",
        address = "https://test.example.com",
    )

    private val testUser = UserInfo(
        id = "user-1",
        name = "testuser",
        serverAddress = "https://test.example.com",
        accessToken = "token-123",
        serverId = "server-1",
    )

    @Before
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val okHttpClient = OkHttpClient()
        addressRouter = ServerAddressRouter()
        engine = JellyfinApiEngine(
            context = mockk(relaxed = true),
            jellyfinLazy = dagger.Lazy { jellyfin },
            okHttpClientLazy = dagger.Lazy { okHttpClient },
            deviceProfileProvider = DeviceProfileProvider(DeviceCodecCapabilities()),
            addressRouter = addressRouter,
        )
        val libraryClient = LibraryApiClientImpl(engine, mockk(relaxed = true))
        authClient = AuthApiClientImpl(engine, libraryClient, addressRouter)
    }

    @Test
    fun `initial state has no server`() = runTest {
        assertNull(engine.currentServer.value)
        assertNull(engine.currentUser.value)
    }

    @Test
    fun `setServer updates current server`() = runTest {
        authClient.setServer(testServer)

        assertEquals(testServer, engine.currentServer.value)
    }

    @Test
    fun `setUser updates current user`() = runTest {
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        assertEquals(testUser, engine.currentUser.value)
    }

    @Test
    fun `disconnect clears server and user`() = runTest {
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        authClient.disconnect()

        assertNull(engine.currentServer.value)
        assertNull(engine.currentUser.value)
        assertNull(engine.api)
    }

    @Test
    fun `getServerUrl returns server address`() = runTest {
        engine.updateServer(testServer)

        assertEquals("https://test.example.com", authClient.getServerUrl())
    }

    @Test
    fun `getServerUrl returns null when no server`() = runTest {
        assertNull(authClient.getServerUrl())
    }

    @Test
    fun `getAccessToken returns user token`() = runTest {
        engine.updateUser(testUser)

        assertEquals("token-123", authClient.getAccessToken())
    }

    @Test
    fun `getAccessToken returns null when no user`() = runTest {
        assertNull(authClient.getAccessToken())
    }

    @Test
    fun `currentServer flow reflects state changes`() = runTest {
        authClient.setServer(testServer)

        val server = authClient.currentServer.first()
        assertEquals(testServer, server)
    }

    @Test
    fun `currentUser flow reflects state changes`() = runTest {
        engine.updateServer(testServer)
        engine.updateUser(testUser)

        val user = authClient.currentUser.first()
        assertEquals(testUser, user)
    }

    @Test
    fun `disconnect then setServer restores server state`() = runTest {
        authClient.setServer(testServer)
        authClient.disconnect()
        assertNull(engine.currentServer.value)

        authClient.setServer(testServer)
        assertEquals(testServer, engine.currentServer.value)
    }
}
