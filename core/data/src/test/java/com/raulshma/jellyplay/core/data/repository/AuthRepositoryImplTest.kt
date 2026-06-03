package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryImplTest {

    private val apiClient: JellyfinApiClient = mockk(relaxed = true)
    private val database: JellyPlayDatabase = mockk(relaxed = true)
    private val serverDao: ServerDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val preferencesStore: UserPreferencesStore = mockk(relaxed = true)

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

    @Before
    fun setup() {
        every { apiClient.currentServer } returns flowOf(null)
        every { apiClient.currentUser } returns flowOf(null)
        every { serverDao.getAllServers() } returns flowOf(emptyList())
        every { userDao.getUsersForServer(any()) } returns flowOf(emptyList())
        repository = AuthRepositoryImpl(
            apiClient = apiClient,
            database = database,
            serverDao = serverDao,
            userDao = userDao,
            preferencesStore = preferencesStore,
        )
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
    fun `logout disconnects apiClient and clears preferences`() = runTest {
        repository.logout()

        coVerify { apiClient.disconnect() }
        coVerify { preferencesStore.clearAll() }
    }

    @Test
    fun `restoreSession with valid stored credentials`() = runTest {
        every { preferencesStore.activeServerId } returns flowOf("server-1")
        every { preferencesStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery { userDao.getUserById("user-1") } returns testUserEntity

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { apiClient.setServer(any()) }
        coVerify { apiClient.setUser(any()) }
    }

    @Test
    fun `restoreSession with no stored server succeeds without error`() = runTest {
        every { preferencesStore.activeServerId } returns flowOf(null)
        every { preferencesStore.activeUserId } returns flowOf(null)

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { apiClient.setServer(any()) }
    }

    @Test
    fun `restoreSession clears preferences when server not found in DB`() = runTest {
        every { preferencesStore.activeServerId } returns flowOf("server-1")
        every { preferencesStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns null

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { preferencesStore.setActiveServer("") }
    }

    @Test
    fun `restoreSession clears user when user not found in DB`() = runTest {
        every { preferencesStore.activeServerId } returns flowOf("server-1")
        every { preferencesStore.activeUserId } returns flowOf("user-1")
        coEvery { serverDao.getServerById("server-1") } returns testServerEntity
        coEvery { userDao.getUserById("user-1") } returns null

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        coVerify { preferencesStore.setActiveUser("") }
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
        coVerify { preferencesStore.setActiveUser("") }
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
    fun `isAuthenticated is false when no server or user`() = runTest {
        every { apiClient.currentServer } returns flowOf(null)
        every { apiClient.currentUser } returns flowOf(null)

        val flow = repository.isAuthenticated
        assertFalse(flow.first() ?: true)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.first(): T? =
        firstOrNull()
}
