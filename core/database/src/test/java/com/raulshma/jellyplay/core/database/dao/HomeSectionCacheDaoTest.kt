package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeSectionCacheDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var homeSectionCacheDao: HomeSectionCacheDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        homeSectionCacheDao = database.homeSectionCacheDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    /** Row with a distinct [payloadJson] so tests can tell which row won. */
    private fun createEntry(
        serverId: String = "server-1",
        userId: String = "user-A",
        cacheKey: String = "key-default",
        payloadJson: String = "{}",
        fetchedAt: Long = 1_000L,
    ) = HomeSectionCacheEntity(
        serverId = serverId,
        userId = userId,
        cacheKey = cacheKey,
        payloadJson = payloadJson,
        fetchedAt = fetchedAt,
    )

    @Test
    fun `getLatestForIdentity returns null when the table is empty`() = runTest {
        assertNull(homeSectionCacheDao.getLatestForIdentity("server-1", "user-A"))
    }

    @Test
    fun `getLatestForIdentity returns the newest row regardless of cacheKey`() = runTest {
        // The offline home's layout mirror is key-agnostic on purpose (issue
        // #147): a preference change while offline shifts the cacheKey, and the
        // last-fetched row is still the layout the user last saw. Newest-first
        // insertion order proves the winner is ORDER BY fetchedAt, not rowid.
        homeSectionCacheDao.upsert(createEntry(cacheKey = "key-home-v2", payloadJson = """{"v":2}""", fetchedAt = 3_000L))
        homeSectionCacheDao.upsert(createEntry(cacheKey = "key-home-v1", payloadJson = """{"v":1}""", fetchedAt = 1_000L))
        homeSectionCacheDao.upsert(createEntry(cacheKey = "key-extras", payloadJson = """{"v":3}""", fetchedAt = 2_000L))

        val latest = homeSectionCacheDao.getLatestForIdentity("server-1", "user-A")

        assertEquals("""{"v":2}""", latest!!.payloadJson)
    }

    @Test
    fun `getLatestForIdentity stays scoped to the server and user identity`() = runTest {
        homeSectionCacheDao.upsert(
            createEntry(serverId = "server-2", userId = "user-A", payloadJson = """{"other":"server"}""", fetchedAt = 9_000L)
        )
        homeSectionCacheDao.upsert(
            createEntry(serverId = "server-1", userId = "user-B", payloadJson = """{"other":"user"}""", fetchedAt = 9_000L)
        )
        homeSectionCacheDao.upsert(createEntry(payloadJson = """{"own":"identity"}""", fetchedAt = 1_000L))

        val latest = homeSectionCacheDao.getLatestForIdentity("server-1", "user-A")

        // Newer rows belonging to other identities must never win.
        assertEquals("""{"own":"identity"}""", latest!!.payloadJson)
    }
}
