package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.database.entity.UserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServerDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var serverDao: ServerDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        serverDao = database.serverDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insertServer and getServerById`() = runTest {
        val server = ServerEntity(
            id = "server-1",
            name = "Test Server",
            address = "https://test.example.com",
        )
        serverDao.insertServer(server)

        val result = serverDao.getServerById("server-1")
        assertNotNull(result)
        assertEquals("Test Server", result!!.name)
        assertEquals("https://test.example.com", result.address)
    }

    @Test
    fun `getServerById returns null for non-existent`() = runTest {
        val result = serverDao.getServerById("nonexistent")
        assertNull(result)
    }

    @Test
    fun `getServerByAddress finds matching server`() = runTest {
        val server = ServerEntity(
            id = "server-1",
            name = "Test Server",
            address = "https://test.example.com",
        )
        serverDao.insertServer(server)

        val result = serverDao.getServerByAddress("https://test.example.com")
        assertNotNull(result)
        assertEquals("server-1", result!!.id)
    }

    @Test
    fun `getServerByAddress returns null for different address`() = runTest {
        val server = ServerEntity(
            id = "server-1",
            name = "Test Server",
            address = "https://test.example.com",
        )
        serverDao.insertServer(server)

        val result = serverDao.getServerByAddress("https://other.example.com")
        assertNull(result)
    }

    @Test
    fun `getAllServers returns all inserted servers`() = runTest {
        serverDao.insertServer(ServerEntity(id = "s1", name = "Server 1", address = "https://s1.com"))
        serverDao.insertServer(ServerEntity(id = "s2", name = "Server 2", address = "https://s2.com"))

        val servers = serverDao.getAllServers().first()
        assertEquals(2, servers.size)
    }

    @Test
    fun `updateServer modifies existing server`() = runTest {
        serverDao.insertServer(ServerEntity(id = "s1", name = "Original", address = "https://s1.com"))
        serverDao.updateServer(ServerEntity(id = "s1", name = "Updated", address = "https://s1.com"))

        val result = serverDao.getServerById("s1")
        assertEquals("Updated", result!!.name)
    }

    @Test
    fun `deleteServerById removes server`() = runTest {
        serverDao.insertServer(ServerEntity(id = "s1", name = "Server 1", address = "https://s1.com"))
        serverDao.deleteServerById("s1")

        assertNull(serverDao.getServerById("s1"))
    }

    @Test
    fun `insertServer replaces on conflict`() = runTest {
        serverDao.insertServer(ServerEntity(id = "s1", name = "Original", address = "https://s1.com"))
        serverDao.insertServer(ServerEntity(id = "s1", name = "Replaced", address = "https://s1.com"))

        val result = serverDao.getServerById("s1")
        assertEquals("Replaced", result!!.name)
    }

    @Test
    fun `updateServer round-trips alternateAddresses column`() = runTest {
        val alternates = """["https://192.168.1.100:8096","https://lan.example.com"]"""
        serverDao.insertServer(
            ServerEntity(id = "s1", name = "Server 1", address = "https://s1.com")
        )
        serverDao.updateServer(
            ServerEntity(
                id = "s1",
                name = "Server 1",
                address = "https://s1.com",
                alternateAddresses = alternates,
            )
        )

        val result = serverDao.getServerById("s1")
        assertEquals(alternates, result!!.alternateAddresses)
    }

    @Test
    fun `updateServer preserves alternateAddresses across partial updates`() = runTest {
        val alternates = """["https://192.168.1.100:8096"]"""
        serverDao.insertServer(
            ServerEntity(
                id = "s1",
                name = "Server 1",
                address = "https://s1.com",
                alternateAddresses = alternates,
            )
        )
        serverDao.updateServer(
            ServerEntity(
                id = "s1",
                name = "Server 1 (renamed)",
                address = "https://s1.com",
                accessToken = "token-abc",
                alternateAddresses = alternates,
            )
        )

        val result = serverDao.getServerById("s1")
        assertEquals("Server 1 (renamed)", result!!.name)
        assertEquals("token-abc", result.accessToken)
        assertEquals(alternates, result.alternateAddresses)
    }
}
