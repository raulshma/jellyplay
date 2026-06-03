package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
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
class UserDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var userDao: UserDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = database.userDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createUser(
        userId: String = "user-1",
        serverId: String = "server-1",
        name: String = "Test User",
        token: String = "token",
    ) = UserEntity(
        userId = userId,
        serverId = serverId,
        name = name,
        accessToken = token,
        lastConnected = 1000L,
    )

    @Test
    fun `insertUser and getUserById`() = runTest {
        val user = createUser()
        userDao.insertUser(user)

        val result = userDao.getUserById("user-1")
        assertNotNull(result)
        assertEquals("Test User", result!!.name)
    }

    @Test
    fun `getUserById returns null for non-existent`() = runTest {
        assertNull(userDao.getUserById("nonexistent"))
    }

    @Test
    fun `getUsersForServer returns users for specific server`() = runTest {
        userDao.insertUser(createUser(userId = "u1", serverId = "s1"))
        userDao.insertUser(createUser(userId = "u2", serverId = "s1"))
        userDao.insertUser(createUser(userId = "u3", serverId = "s2"))

        val users = userDao.getUsersForServer("s1").first()
        assertEquals(2, users.size)
    }

    @Test
    fun `getUsersForServer returns empty for no matches`() = runTest {
        userDao.insertUser(createUser(serverId = "s1"))

        val users = userDao.getUsersForServer("s2").first()
        assertEquals(0, users.size)
    }

    @Test
    fun `getMostRecentUserForServer returns latest by lastConnected`() = runTest {
        userDao.insertUser(createUser(userId = "u1", serverId = "s1", token = "t1").copy(lastConnected = 1000L))
        userDao.insertUser(createUser(userId = "u2", serverId = "s1", token = "t2").copy(lastConnected = 2000L))

        val result = userDao.getMostRecentUserForServer("s1")
        assertNotNull(result)
        assertEquals("u2", result!!.userId)
    }

    @Test
    fun `updateUser modifies existing user`() = runTest {
        userDao.insertUser(createUser(name = "Original"))
        userDao.updateUser(createUser(name = "Updated"))

        val result = userDao.getUserById("user-1")
        assertEquals("Updated", result!!.name)
    }

    @Test
    fun `deleteUserById removes user`() = runTest {
        userDao.insertUser(createUser())
        userDao.deleteUserById("user-1")

        assertNull(userDao.getUserById("user-1"))
    }

    @Test
    fun `deleteUsersForServer removes all users for server`() = runTest {
        userDao.insertUser(createUser(userId = "u1", serverId = "s1"))
        userDao.insertUser(createUser(userId = "u2", serverId = "s1"))
        userDao.insertUser(createUser(userId = "u3", serverId = "s2"))

        userDao.deleteUsersForServer("s1")

        assertNull(userDao.getUserById("u1"))
        assertNull(userDao.getUserById("u2"))
        assertNotNull(userDao.getUserById("u3"))
    }

    @Test
    fun `insertUser replaces on conflict`() = runTest {
        userDao.insertUser(createUser(name = "Original"))
        userDao.insertUser(createUser(name = "Replaced"))

        val result = userDao.getUserById("user-1")
        assertEquals("Replaced", result!!.name)
    }
}
