package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.ActiveSession
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.network.websocket.JellyfinWebSocketClient
import com.raulshma.jellyplay.core.network.api.ApiException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class AuthRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val webSocketClient: JellyfinWebSocketClient = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val serverDao: ServerDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val serverIdentityStore: ServerIdentityStore = mockk(relaxed = true)
    private val tokenCipher: TokenCipher = mockk(relaxed = true) {
        // Tests store plaintext tokens — make the cipher a no-op pass-through so existing
        // assertions about token values still hold. The cipher's real behaviour is covered
        // by TokenCipherTest (Robolectric) and Migration24To25Test.
        every { encrypt(any()) } answers { firstArg() }
        every { decrypt(any()) } answers { firstArg() }
        every { isEncrypted(any()) } returns false
    }

    private lateinit var repository: AuthRepositoryImpl

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

    private val testServerEntity = ServerEntity(
        id = "server-1",
        name = "Test Server",
        address = "https://test.example.com",
    )

    private val testUserEntity = UserEntity(
        userId = "user-1",
        serverId = "server-1",
        name = "testuser",
        accessToken = "token-123",
    )

    @BeforeTest
    fun setup() {
        mockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        every { apiClient.currentServer } returns flowOf(null)
        every { apiClient.currentUser } returns flowOf(null)
        // isAuthenticated derives from the ATOMIC session flow (never a
        // combine of the two separate flows above).
        every { apiClient.session } returns flowOf(null)
        every { serverDao.getAllServers() } returns flowOf(emptyList())
        every { userDao.getUsersForServer(any()) } returns flowOf(emptyList())
        repository = AuthRepositoryImpl(
            apiClient = apiClient,
            webSocketClient = webSocketClient,
            database = database,
            serverDao = serverDao,
            userDao = userDao,
            serverIdentityStore = serverIdentityStore,
            tokenCipher = tokenCipher,
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
            externalScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("com.raulshma.jellyplay.core.data.repository.RoomTransactionsKt")
    }

    @Test
    fun `addServer success persists server and returns info`() = runTest {
        coEvery { apiClient.connectToServer("https://test.example.com") } returns Result.success(testServer)

        val result = repository.addServer("https://test.example.com")

        assertTrue(result.isSuccess)
        assertEquals("server-1", result.getOrNull()?.id)
        coVerify { serverDao.insertServer(match { it.id == "server-1" && it.name == "Test Server" }) }
    }

    @Test
    fun `addServer failure returns error`() = runTest {
        coEvery { apiClient.connectToServer("https://bad.example.com") } returns
            Result.failure(Exception("Connection refused"))

        val result = repository.addServer("https://bad.example.com")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.insertServer(any()) }
    }

    @Test
    fun `probeServer passes success through without persisting`() = runTest {
        coEvery { apiClient.getServerInfo(altAddress) } returns
            Result.success(testServer.copy(address = altAddress))

        val result = repository.probeServer(altAddress)

        assertTrue(result.isSuccess)
        assertEquals(altAddress, result.getOrNull()?.address)
        // A probe must never mutate persisted state.
        coVerify(exactly = 0) { serverDao.insertServer(any()) }
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `probeServer passes failure through without persisting`() = runTest {
        coEvery { apiClient.getServerInfo(altAddress) } returns
            Result.failure(Exception("unreachable"))

        val result = repository.probeServer(altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.insertServer(any()) }
    }

    @Test
    fun `postCapabilities delegates to the client`() = runTest {
        coEvery { apiClient.postCapabilities() } returns Result.success(Unit)

        val result = repository.postCapabilities()

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { apiClient.postCapabilities() }
    }

    @Test
    fun `logout disconnects apiClient and clears only the session`() = runTest {
        repository.logout()

        coVerify { apiClient.disconnect() }
        // logout must clear the active server/user selection only — NOT wipe
        // the whole DataStore (device id + all user prefs must be preserved).
        coVerify { serverIdentityStore.clearSession() }
    }

    @Test
    fun `restoreSession with valid stored credentials`() = runTest {
        every { serverIdentityStore.activeServerId } returns flowOf("server-1")
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery { userDao.getUserById("user-1") } returns testUserEntity

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { apiClient.setServer(any()) }
        coVerify { apiClient.selectReachableAddress() }
        coVerify { apiClient.setUser(any()) }
    }

    @Test
    fun `restoreSession tolerates address selection failures`() = runTest {
        every { serverIdentityStore.activeServerId } returns flowOf("server-1")
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery { userDao.getUserById("user-1") } returns testUserEntity
        coEvery { apiClient.selectReachableAddress() } throws RuntimeException("probe blew up")

        val result = repository.restoreSession()

        // A selection crash must not break session restore — the primary
        // address is still wired and offline/cached use is unaffected.
        assertTrue(result.isSuccess)
        coVerify { apiClient.setUser(any()) }
    }

    @Test
    fun `restoreSession with no stored server succeeds without error`() = runTest {
        every { serverIdentityStore.activeServerId } returns flowOf(null)
        every { serverIdentityStore.activeUserId } returns flowOf(null)

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { apiClient.setServer(any()) }
    }

    @Test
    fun `restoreSession clears preferences when server not found in DB`() = runTest {
        every { serverIdentityStore.activeServerId } returns flowOf("server-1")
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns null

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { serverIdentityStore.setActiveSession("", "") }
    }

    @Test
    fun `restoreSession clears user when user not found in DB`() = runTest {
        every { serverIdentityStore.activeServerId } returns flowOf("server-1")
        every { serverIdentityStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery { userDao.getUserById("user-1") } returns null

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { serverIdentityStore.setActiveUser("") }
    }

    @Test
    fun `login normalizes server address`() {
        val address = "test.example.com".trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        assertEquals("https://test.example.com", address)
    }

    @Test
    fun `login normalizes address trimming trailing slash`() {
        val address = "https://test.example.com/".trim().trimEnd('/').let {
            if (it.startsWith("http://") || it.startsWith("https://")) it
            else "https://$it"
        }
        assertEquals("https://test.example.com", address)
    }

    @Test
    fun `switchUser succeeds when user not found`() = runTest {
        coEvery { userDao.getUserById("nonexistent") } returns null

        val result = repository.switchUser("nonexistent")

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { apiClient.setUser(any()) }
    }

    @Test
    fun `removeUser deletes user and disconnects if current user`() = runTest {
        every { apiClient.currentUser } returns flowOf(testUser)

        repository.removeUser("user-1")

        coVerify { userDao.deleteUserById("user-1") }
        coVerify { apiClient.disconnect() }
        coVerify { serverIdentityStore.setActiveUser("") }
    }

    @Test
    fun `removeUser does not disconnect if different user`() = runTest {
        every { apiClient.currentUser } returns flowOf(
            testUser.copy(id = "other-user")
        )

        repository.removeUser("user-1")

        coVerify { userDao.deleteUserById("user-1") }
        coVerify(exactly = 0) { apiClient.disconnect() }
    }

    @Test
    fun `removeUser clears user and token from matching servers`() = runTest {
        val serverWithUser = testServerEntity.copy(userId = "user-1", accessToken = "token-123")
        every { serverDao.getAllServers() } returns flowOf(listOf(serverWithUser))
        every { apiClient.currentUser } returns flowOf(null)

        repository.removeUser("user-1")

        coVerify { userDao.deleteUserById("user-1") }
        // Bulk UPDATE replaces the prior per-row read-modify-write loop.
        coVerify { serverDao.clearUserFromServers("user-1") }
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `revokeServerSession revokes on server, deletes user, and clears session`() = runTest {
        every { apiClient.currentUser } returns flowOf(testUser)
        coEvery { apiClient.revokeServerSession() } returns Result.success(Unit)
        val serverWithUser = testServerEntity.copy(userId = "user-1", accessToken = "token-123")
        every { serverDao.getAllServers() } returns flowOf(listOf(serverWithUser))

        repository.revokeServerSession()

        coVerify { apiClient.revokeServerSession() }
        coVerify { userDao.deleteUserById("user-1") }
        coVerify { apiClient.disconnect() }
        coVerify { serverIdentityStore.clearSession() }
    }

    @Test
    fun `isAuthenticated is false when no session is established`() = runTest {
        every { apiClient.currentServer } returns flowOf(null)
        every { apiClient.currentUser } returns flowOf(null)

        val flow = repository.isAuthenticated
        assertFalse(flow.first() ?: true)
    }

    @Test
    fun `isAuthenticated derives from the atomic session, never a synthetic server-user pair`() = runTest {
        // Drive the session flow directly: the stubbed currentServer/
        // currentUser are BOTH non-null here — the synthetic mid-switch pair
        // the old combine(currentServer, currentUser) shape could observe and
        // report authenticated for. Only a fully established atomic session
        // may read as authenticated. A fresh repository is built because the
        // isAuthenticated chain captures apiClient.session at construction.
        val sessionFlow = MutableStateFlow<ActiveSession?>(null)
        every { apiClient.session } returns sessionFlow
        every { apiClient.currentServer } returns MutableStateFlow(testServer)
        every { apiClient.currentUser } returns MutableStateFlow(testUser)
        val repo = AuthRepositoryImpl(
            apiClient = apiClient,
            webSocketClient = webSocketClient,
            database = database,
            serverDao = serverDao,
            userDao = userDao,
            serverIdentityStore = serverIdentityStore,
            tokenCipher = tokenCipher,
            json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true },
            externalScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )

        val states = mutableListOf<Boolean>()
        backgroundScope.launch { repo.isAuthenticated.collect { states.add(it) } }
        runCurrent()
        assertFalse(states.last(), "synthetic (server, user) pair must not authenticate")

        sessionFlow.value = ActiveSession(testServer, testUser)
        runCurrent()
        assertTrue(states.last(), "an established atomic session authenticates")

        sessionFlow.value = null
        runCurrent()
        assertFalse(states.last(), "logout clears authentication in one step")
    }

    // ------------------------------------------------------------------
    // refreshCurrentUser — re-validates admin status against the server.
    // The failure-handling asymmetry here is security-critical:
    //   - 401/403 must demote locally (server has revoked/demoted the user)
    //   - any other failure must KEEP the cached value (flaky network must
    //     not lock an admin out; the server 403s on the real call as backstop)
    // ------------------------------------------------------------------

    private val adminUser = testUser.copy(isAdmin = true, canDeleteContent = true)

    @Test
    fun `refreshCurrentUser updates and persists flags from server policy`() = runTest {
        every { apiClient.currentUser } returns flowOf(adminUser)
        coEvery { apiClient.getCurrentUser() } returns Result.success(
            ManagedUser(
                id = "user-1",
                name = "testuser",
                policy = ManagedUserPolicy(isAdministrator = true, enableContentDeletion = false),
            )
        )
        coEvery { userDao.getUserById("user-1") } returns testUserEntity.copy(
            isAdmin = false, canDeleteContent = false,
        )

        val result = repository.refreshCurrentUser()

        assertTrue(result.isSuccess)
        assertEquals(true, result.getOrNull()?.isAdmin)
        assertEquals(false, result.getOrNull()?.canDeleteContent)
        coVerify { apiClient.setUser(match { it.isAdmin && !it.canDeleteContent }) }
        // Persisted the refreshed flags.
        coVerify { userDao.updateUser(match { it.isAdmin && !it.canDeleteContent }) }
    }

    @Test
    fun `refreshCurrentUser demotes locally and persists on 403 access denied`() = runTest {
        every { apiClient.currentUser } returns flowOf(adminUser)
        coEvery { apiClient.getCurrentUser() } returns Result.failure(
            ApiException.fromHttp(403, "Forbidden"),
        )
        coEvery { userDao.getUserById("user-1") } returns testUserEntity.copy(
            isAdmin = true, canDeleteContent = true,
        )

        val result = repository.refreshCurrentUser()

        // Demote is a *success* result (a definite answer: no longer admin),
        // distinct from a transient failure which returns Result.failure.
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.isAdmin)
        coVerify { apiClient.setUser(match { !it.isAdmin && !it.canDeleteContent }) }
        coVerify { userDao.updateUser(match { !it.isAdmin && !it.canDeleteContent }) }
    }

    @Test
    fun `refreshCurrentUser keeps cached value and does not persist on transient failure`() = runTest {
        every { apiClient.currentUser } returns flowOf(adminUser)
        coEvery { apiClient.getCurrentUser() } returns Result.failure(
            java.io.IOException("Connection reset"),
        )

        val result = repository.refreshCurrentUser()

        // Transient failure → no answer; cached value must be preserved so a
        // flaky network can't lock an admin out.
        assertTrue(result.isFailure)
        // setUser must NOT be called with a demoted value.
        coVerify(exactly = 0) { apiClient.setUser(any()) }
        coVerify(exactly = 0) { userDao.updateUser(any()) }
    }

    @Test
    fun `refreshCurrentUser skips DB write when flags are unchanged`() = runTest {
        every { apiClient.currentUser } returns flowOf(adminUser)
        coEvery { apiClient.getCurrentUser() } returns Result.success(
            ManagedUser(
                id = "user-1",
                name = "testuser",
                policy = ManagedUserPolicy(isAdministrator = true, enableContentDeletion = true),
            )
        )
        // DB already reflects the same flags → no write needed.
        coEvery { userDao.getUserById("user-1") } returns testUserEntity.copy(
            isAdmin = true, canDeleteContent = true,
        )

        repository.refreshCurrentUser()

        // setUser still publishes the refreshed object (idempotent), but the
        // persist step short-circuits because nothing changed.
        coVerify { apiClient.setUser(any()) }
        coVerify(exactly = 0) { userDao.updateUser(any()) }
    }

    @Test
    fun `refreshCurrentUser fails when no active user`() = runTest {
        every { apiClient.currentUser } returns flowOf(null)

        val result = repository.refreshCurrentUser()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { apiClient.getCurrentUser() }
        coVerify(exactly = 0) { apiClient.setUser(any()) }
    }

    // ------------------------------------------------------------------
    // Multi-address management
    // ------------------------------------------------------------------

    private val altAddress = "https://192.168.1.100:8096"
    private val altAddressJson = """["$altAddress"]"""

    private fun serverEntityWithAlt() = testServerEntity.copy(
        alternateAddresses = altAddressJson,
    )

    @Test
    fun `addServerAddress validates and persists alternate address`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery {
            apiClient.getServerInfo(altAddress)
        } returns Result.success(testServer.copy(address = altAddress))

        val result = repository.addServerAddress("server-1", altAddress)

        assertTrue(result.isSuccess)
        val captured = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(captured)) }
        assertNotNull(captured.captured.alternateAddresses)
        assertTrue(captured.captured.alternateAddresses!!.contains(altAddress))
    }

    @Test
    fun `addServerAddress normalizes bare host to https`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery {
            apiClient.getServerInfo("https://lan.example.com")
        } returns Result.success(testServer.copy(address = "https://lan.example.com"))

        val result = repository.addServerAddress("server-1", "lan.example.com")

        assertTrue(result.isSuccess)
        val captured = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(captured)) }
        assertTrue(captured.captured.alternateAddresses!!.contains("https://lan.example.com"))
    }

    @Test
    fun `addServerAddress rejects duplicate of primary address`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()

        val result = repository.addServerAddress("server-1", testServerEntity.address)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `addServerAddress rejects duplicate of existing alternate`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()

        val result = repository.addServerAddress("server-1", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `addServerAddress rejects address pointing to a different server`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()
        coEvery {
            apiClient.getServerInfo(altAddress)
        } returns Result.success(testServer.copy(id = "other-server", address = altAddress))

        val result = repository.addServerAddress("server-1", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `addServerAddress rejects unreachable address`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()
        coEvery {
            apiClient.getServerInfo(altAddress)
        } returns Result.failure(Exception("Connection refused"))

        val result = repository.addServerAddress("server-1", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `addServerAddress fails when server not found`() = runTest {
        coEvery { serverDao.getServerById("missing") } returns null

        val result = repository.addServerAddress("missing", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
        coVerify(exactly = 0) { apiClient.getServerInfo(any()) }
    }

    @Test
    fun `removeServerAddress filters it out and writes back`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()

        val result = repository.removeServerAddress("server-1", altAddress)

        assertTrue(result.isSuccess)
        val captured = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(captured)) }
        assertNull(captured.captured.alternateAddresses)
    }

    @Test
    fun `removeServerAddress keeps remaining alternates`() = runTest {
        val json = """["https://a.example.com","https://b.example.com"]"""
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity.copy(
            alternateAddresses = json,
        )

        repository.removeServerAddress("server-1", "https://a.example.com")

        val captured = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(captured)) }
        assertNotNull(captured.captured.alternateAddresses)
        assertFalse(captured.captured.alternateAddresses!!.contains("https://a.example.com"))
        assertTrue(captured.captured.alternateAddresses!!.contains("https://b.example.com"))
    }

    @Test
    fun `removeServerAddress fails when server not found`() = runTest {
        coEvery { serverDao.getServerById("missing") } returns null

        val result = repository.removeServerAddress("missing", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `switchServerAddress promotes alternate, demotes old primary and reconfigures client`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()
        every { apiClient.currentUser } returns flowOf(testUser)

        val result = repository.switchServerAddress("server-1", altAddress)

        assertTrue(result.isSuccess)
        val captured = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(captured)) }
        assertEquals(altAddress, captured.captured.address)
        assertNotNull(captured.captured.alternateAddresses)
        assertTrue(captured.captured.alternateAddresses!!.contains(testServerEntity.address))
        assertFalse(captured.captured.alternateAddresses!!.contains(altAddress))
        coVerify { apiClient.setServer(any()) }
        coVerify { apiClient.setUser(match { it.serverAddress == altAddress }) }
    }

    @Test
    fun `switchServerAddress rejects address not in alternates`() = runTest {
        coEvery { serverDao.getServerById("server-1") } returns serverEntityWithAlt()

        val result = repository.switchServerAddress("server-1", "https://unknown.example.com")

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
        coVerify(exactly = 0) { apiClient.setServer(any()) }
    }

    @Test
    fun `switchServerAddress fails when server not found`() = runTest {
        coEvery { serverDao.getServerById("missing") } returns null

        val result = repository.switchServerAddress("missing", altAddress)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { serverDao.updateServer(any()) }
    }

    @Test
    fun `persistSession preserves alternate addresses on re-login`() = runTest {
        val serverWithAlts = serverEntityWithAlt()
        coEvery { serverDao.getServerByAddress(testServerEntity.address) } returns serverWithAlts
        coEvery { serverDao.getServerById("server-1") } returns serverWithAlts
        coEvery {
            apiClient.authenticateUser(any<ServerInfo>(), "testuser", "pass")
        } returns Result.success(testUser)
        every { apiClient.currentServer } returns flowOf(testServer)

        val result = repository.login(testServerEntity.address, "testuser", "pass")

        assertTrue(result.isSuccess)
        val updatedSlot = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(updatedSlot)) }
        assertEquals(altAddressJson, updatedSlot.captured.alternateAddresses)
    }

    @Test
    fun `persistSession stores null alternates for brand-new server`() = runTest {
        coEvery { serverDao.getServerByAddress(testServerEntity.address) } returns null
        coEvery { serverDao.getServerById("server-1") } returns null
        coEvery {
            apiClient.authenticateUser(eq(testServerEntity.address), "testuser", "pass")
        } returns Result.success(testUser)
        every { apiClient.currentServer } returns flowOf(testServer)

        repository.login(testServerEntity.address, "testuser", "pass")

        val updatedSlot = slot<ServerEntity>()
        coVerify { serverDao.updateServer(capture(updatedSlot)) }
        assertNull(updatedSlot.captured.alternateAddresses)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(): T? =
        firstOrNull()
}
