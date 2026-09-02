package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.MediaAuditLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditLogDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var auditLogDao: AuditLogDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        auditLogDao = database.auditLogDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createEntry(
        id: String,
        timestamp: Long,
        actionType: String = "USER_DELETE",
        itemCount: Int = 1,
    ) = MediaAuditLogEntity(
        id = id,
        timestamp = timestamp,
        adminUserId = "admin-1",
        adminUserName = "Admin",
        actionType = actionType,
        configJson = """{"dryRun":false}""",
        itemCount = itemCount,
        itemDetailsJson = """[{"id":"item-1"}]""",
    )

    @Test
    fun `insert then getAll returns entries newest first`() = runTest {
        auditLogDao.insert(createEntry(id = "e-1", timestamp = 1_000L))
        auditLogDao.insert(createEntry(id = "e-2", timestamp = 3_000L))
        auditLogDao.insert(createEntry(id = "e-3", timestamp = 2_000L))

        assertEquals(listOf("e-2", "e-3", "e-1"), auditLogDao.getAll().first().map { it.id })
    }

    @Test
    fun `getAll caps results at the newest 500 rows`() = runTest {
        // Pins the LIMIT 500 cap: the 5 oldest rows must fall off the query.
        for (i in 0..504) {
            auditLogDao.insert(createEntry(id = "e-$i", timestamp = i.toLong()))
        }

        val entries = auditLogDao.getAll().first()
        assertEquals(500, entries.size)
        assertEquals("e-504", entries.first().id)
        assertEquals("e-5", entries.last().id)
        assertTrue(entries.none { it.id in setOf("e-0", "e-1", "e-2", "e-3", "e-4") })
    }

    @Test
    fun `getByActionType filters and orders newest first`() = runTest {
        auditLogDao.insert(createEntry(id = "e-1", timestamp = 1_000L, actionType = "USER_DELETE"))
        auditLogDao.insert(createEntry(id = "e-2", timestamp = 3_000L, actionType = "METADATA_REFRESH"))
        auditLogDao.insert(createEntry(id = "e-3", timestamp = 2_000L, actionType = "USER_DELETE"))

        val deletes = auditLogDao.getByActionType("USER_DELETE").first()
        assertEquals(listOf("e-3", "e-1"), deletes.map { it.id })
    }

    @Test
    fun `deleteOlderThan removes strictly older rows and returns the count`() = runTest {
        auditLogDao.insert(createEntry(id = "e-1", timestamp = 1_000L))
        auditLogDao.insert(createEntry(id = "e-2", timestamp = 2_000L))
        auditLogDao.insert(createEntry(id = "e-3", timestamp = 3_000L))

        // Boundary is exclusive: a row exactly at the cutoff survives.
        val deleted = auditLogDao.deleteOlderThan(2_000L)

        assertEquals(1, deleted)
        val remaining = auditLogDao.getAll().first().map { it.id }
        assertEquals(listOf("e-3", "e-2"), remaining)
    }

    @Test
    fun `getCount reflects inserted rows`() = runTest {
        assertEquals(0, auditLogDao.getCount().first())

        auditLogDao.insert(createEntry(id = "e-1", timestamp = 1_000L))
        auditLogDao.insert(createEntry(id = "e-2", timestamp = 2_000L))

        assertEquals(2, auditLogDao.getCount().first())
    }

    @Test
    fun `insert with REPLACE overwrites an existing id`() = runTest {
        auditLogDao.insert(createEntry(id = "e-1", timestamp = 1_000L, itemCount = 1))
        auditLogDao.insert(createEntry(id = "e-1", timestamp = 9_000L, itemCount = 7))

        val entries = auditLogDao.getAll().first()
        assertEquals(1, entries.size)
        assertEquals(9_000L, entries[0].timestamp)
        assertEquals(7, entries[0].itemCount)
    }
}
