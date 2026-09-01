package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the offline home's layout-mirror query at the SQL level (issue #147):
 * [com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao.getLatestForIdentity]
 * must return the newest row for the identity regardless of cacheKey, and
 * never leak another identity's row. Ported from the legacy Android test with
 * the shared jvmTest database harness (bundled JVM driver, no Robolectric).
 */
class HomeSectionCacheDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var homeSectionCacheDao: HomeSectionCacheDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        homeSectionCacheDao = database.homeSectionCacheDao()
    }

    @AfterTest
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
