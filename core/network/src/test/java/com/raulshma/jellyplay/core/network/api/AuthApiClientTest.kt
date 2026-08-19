package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.failover.AddressProbeResult
import com.raulshma.jellyplay.core.network.failover.ServerAddressRouter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `failed authenticateUser restores the previous session`() = runTest {
        // A wrong password (or a dead server) mid-login must not sign the user
        // out of the working session. The pre-auth adopt is skipped while a
        // session is established precisely so the attempt never publishes
        // SignedOut(previousIdentity) — observers treat that transition
        // destructively (cache drop + SWR snapshot clear), which would wipe
        // the signed-in user's cached home on every failed attempt. The
        // session must stay published for the whole round-trip.
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val unauthenticatedApi = mockk<ApiClient>(relaxed = true)
        // Full-arity matchers: partial matchers send mockk's overload auto-
        // hinting down a path that executes the real final createApi.
        every {
            jellyfin.createApi(any(), any(), any(), any(), any())
        } returns unauthenticatedApi
        coEvery { unauthenticatedApi.userApi.authenticateUserByName(any()) } throws
            java.io.IOException("auth round-trip failed")
        val localEngine = JellyfinApiEngine(
            context = mockk(relaxed = true),
            jellyfinLazy = dagger.Lazy { jellyfin },
            okHttpClientLazy = dagger.Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        val client = AuthApiClientImpl(
            engine = localEngine,
            libraryClient = LibraryApiClientImpl(localEngine, mockk(relaxed = true)),
            addressRouter = ServerAddressRouter(),
        )
        localEngine.updateSession(testServer, testUser)
        assertEquals(ActiveSession(testServer, testUser), localEngine.session.value)

        // Observe the session flow for the whole attempt: the invariant is
        // that no SignedOut intermediate is EVER published mid-round-trip
        // (identity observers react destructively to it).
        val sessionsSeen = mutableListOf<ActiveSession?>()
        val sessionCollector = launch { localEngine.session.collect { sessionsSeen.add(it) } }

        val result = client.authenticateUser(testServer, username = "testuser", password = "wrong")

        sessionCollector.cancel()
        assertTrue(result.isFailure)
        assertEquals(
            "failed login must leave the previous session intact",
            ActiveSession(testServer, testUser),
            localEngine.session.value,
        )
        assertEquals(testUser, localEngine.currentUser.value)
        assertTrue(
            "no SignedOut intermediate may be published mid-attempt: saw $sessionsSeen",
            sessionsSeen.isNotEmpty() &&
                sessionsSeen.all { it == ActiveSession(testServer, testUser) },
        )
    }


    @Test
    fun `connectToServer while signed in adopts new server and drops user atomically`() = runTest {
        // Settings → Server Management → Add Server reaches connectToServer
        // while a user is signed in. The adopt must be one atomic step — a
        // single-sided updateServer would publish a synthetic
        // (newServer, oldUser) ActiveSession that HomeSession classifies as a
        // real identity switch, clearing Room under the wrong user.
        val probeRouter = mockk<ServerAddressRouter>(relaxed = true)
        every { probeRouter.activeAddress } returns MutableStateFlow(null)
        coEvery { probeRouter.probe("https://new.example.com") } returns AddressProbeResult(
            reachable = true,
            serverId = "server-2",
            serverName = "New Server",
        )
        val probeEngine = JellyfinApiEngine(
            context = mockk(relaxed = true),
            jellyfinLazy = dagger.Lazy { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = dagger.Lazy { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DeviceCodecCapabilities()),
            addressRouter = probeRouter,
        )
        val client = AuthApiClientImpl(
            engine = probeEngine,
            libraryClient = LibraryApiClientImpl(probeEngine, mockk(relaxed = true)),
            addressRouter = probeRouter,
        )
        probeEngine.updateSession(testServer, testUser)
        assertEquals(ActiveSession(testServer, testUser), probeEngine.session.value)

        val result = client.connectToServer("new.example.com")

        assertEquals("server-2", result.getOrNull()?.id)
        assertNull(
            "the signed-in user must be dropped with the old session",
            probeEngine.currentUser.value,
        )
        assertNull(
            "no synthetic (newServer, oldUser) session may be published",
            probeEngine.session.value,
        )
    }
}
