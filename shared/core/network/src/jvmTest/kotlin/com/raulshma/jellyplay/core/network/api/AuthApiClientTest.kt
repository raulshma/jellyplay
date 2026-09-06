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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @BeforeTest
    fun setup() {
        val jellyfin = mockk<Jellyfin>(relaxed = true)
        val okHttpClient = OkHttpClient()
        addressRouter = ServerAddressRouter()
        engine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { okHttpClient },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = addressRouter,
        )
        authClient = AuthApiClientImpl(engine, addressRouter)
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
            jellyfinLazy = LazyProvider { jellyfin },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = ServerAddressRouter(),
        )
        val client = AuthApiClientImpl(
            engine = localEngine,
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
            ActiveSession(testServer, testUser),
            localEngine.session.value,
            "failed login must leave the previous session intact",
        )
        assertEquals(testUser, localEngine.currentUser.value)
        assertTrue(
            sessionsSeen.isNotEmpty() &&
                sessionsSeen.all { it == ActiveSession(testServer, testUser) },
            "no SignedOut intermediate may be published mid-attempt: saw $sessionsSeen",
        )
    }


    @Test
    fun `connectToServer while signed in adopts new server and drops user atomically`() = runTest {
        // Settings → Server Management → Add Server reaches connectToServer
        // while a user is signed in. The adopt must be one atomic step — a
        // single-sided updateServer would publish a synthetic
        // (newServer, oldUser) session that HomeSession classifies as a
        // real identity switch, clearing Room under the wrong user.
        val probeRouter = mockk<ServerAddressRouter>(relaxed = true)
        every { probeRouter.activeAddress } returns MutableStateFlow(null)
        coEvery { probeRouter.probe("https://new.example.com") } returns AddressProbeResult(
            reachable = true,
            serverId = "server-2",
            serverName = "New Server",
        )
        val probeEngine = JellyfinApiEngine(
            jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
            okHttpClientLazy = LazyProvider { OkHttpClient() },
            deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
            addressRouter = probeRouter,
        )
        val client = AuthApiClientImpl(
            engine = probeEngine,
            addressRouter = probeRouter,
        )
        probeEngine.updateSession(testServer, testUser)
        assertEquals(ActiveSession(testServer, testUser), probeEngine.session.value)

        val result = client.connectToServer("new.example.com")

        assertEquals("server-2", result.getOrNull()?.id)
        assertNull(
            probeEngine.currentUser.value,
            "the signed-in user must be dropped with the old session",
        )
        assertNull(
            probeEngine.session.value,
            "no synthetic (newServer, oldUser) session may be published",
        )
    }

    @Test
    fun `connectToServer tls-trust probe failure fails after exactly one attempt`() = runTest {
        // Wave-21 review round: no retry can fix an untrusted certificate —
        // retrying just delayed the Add Server trust dialog by 3 probe
        // rounds. The probe wraps TLS-trust failures into a non-retryable
        // signal; RetryPolicy must stop after attempt 1 while the original
        // cause chain (root-cause walk in the dialog classifier) survives.
        val probeRouter = mockk<ServerAddressRouter>(relaxed = true)
        every { probeRouter.activeAddress } returns MutableStateFlow(null)
        coEvery { probeRouter.probe("https://selfsigned.example.com") } returns AddressProbeResult(
            reachable = false,
            error = javax.net.ssl.SSLHandshakeException("PKIX path building failed"),
        )
        val client = AuthApiClientImpl(
            engine = JellyfinApiEngine(
                jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
                okHttpClientLazy = LazyProvider { OkHttpClient() },
                deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
                addressRouter = probeRouter,
            ),
            addressRouter = probeRouter,
        )

        val result = client.connectToServer("selfsigned.example.com")

        assertTrue(result.isFailure)
        io.mockk.coVerify(exactly = 1) { probeRouter.probe(any()) }
        assertTrue(
            result.exceptionOrNull()?.cause is javax.net.ssl.SSLHandshakeException,
            "the wrapped failure must preserve the TLS cause for the root-cause classifier",
        )
    }

    @Test
    fun `connectToServer plain IOException probe failure still retries`() = runTest {
        // The TLS exemption is probe-and-TLS-scoped only: a transient
        // transport failure keeps the pre-existing retry behavior (1 attempt
        // + maxRetries = 3 probes).
        val probeRouter = mockk<ServerAddressRouter>(relaxed = true)
        every { probeRouter.activeAddress } returns MutableStateFlow(null)
        coEvery { probeRouter.probe("https://flaky.example.com") } returns AddressProbeResult(
            reachable = false,
            error = java.io.IOException("connection reset"),
        )
        val client = AuthApiClientImpl(
            engine = JellyfinApiEngine(
                jellyfinLazy = LazyProvider { mockk<Jellyfin>(relaxed = true) },
                okHttpClientLazy = LazyProvider { OkHttpClient() },
                deviceProfileProvider = DeviceProfileProvider(DesktopDeviceCodecCapabilities()),
                addressRouter = probeRouter,
            ),
            addressRouter = probeRouter,
        )

        val result = client.connectToServer("flaky.example.com")

        assertTrue(result.isFailure)
        io.mockk.coVerify(exactly = 3) { probeRouter.probe(any()) }
    }
}
