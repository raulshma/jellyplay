package com.raulshma.jellyplay.core.database.dao

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartPlaylistDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var smartPlaylistDao: SmartPlaylistDao

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        smartPlaylistDao = database.smartPlaylistDao()
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private fun createPlaylist(
        id: String,
        createdAt: Long,
        name: String = "Smart $id",
        criteriaJson: String = """{"genres":["jazz"],"minRating":7.0}""",
    ) = SmartPlaylistEntity(
        id = id,
        name = name,
        criteriaJson = criteriaJson,
        maxItems = 100,
        sortBy = "NAME",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `insert then getById round-trips`() = runTest {
        val playlist = createPlaylist("sp-1", createdAt = 1_000L)
        smartPlaylistDao.insert(playlist)

        assertEquals(playlist, smartPlaylistDao.getById("sp-1"))
    }

    @Test
    fun `getById returns null for missing playlist`() = runTest {
        assertNull(smartPlaylistDao.getById("missing"))
    }

    @Test
    fun `getAll and observeAll order by createdAt desc`() = runTest {
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L))
        smartPlaylistDao.insert(createPlaylist("sp-3", createdAt = 3_000L))
        smartPlaylistDao.insert(createPlaylist("sp-2", createdAt = 2_000L))

        assertEquals(listOf("sp-3", "sp-2", "sp-1"), smartPlaylistDao.getAll().map { it.id })
        assertEquals(
            listOf("sp-3", "sp-2", "sp-1"),
            smartPlaylistDao.observeAll().first().map { it.id },
        )
    }

    @Test
    fun `insert with REPLACE overwrites an existing id`() = runTest {
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L, name = "Old"))
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L, name = "New"))

        val loaded = smartPlaylistDao.getById("sp-1")
        assertNotNull(loaded)
        assertEquals("New", loaded.name)
        assertEquals(1, smartPlaylistDao.getAll().size)
    }

    @Test
    fun `update rewrites criteria and sort fields`() = runTest {
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L))

        smartPlaylistDao.update(
            SmartPlaylistEntity(
                id = "sp-1",
                name = "Smart sp-1",
                criteriaJson = """{"genres":["blues"],"years":[2020,2021,2022]}""",
                maxItems = 10,
                sortBy = "RATING",
                createdAt = 1_000L,
                updatedAt = 9_000L,
            )
        )

        val loaded = smartPlaylistDao.getById("sp-1")!!
        assertEquals("""{"genres":["blues"],"years":[2020,2021,2022]}""", loaded.criteriaJson)
        assertEquals(10, loaded.maxItems)
        assertEquals("RATING", loaded.sortBy)
        assertEquals(9_000L, loaded.updatedAt)
    }

    @Test
    fun `criteria json round-trips unchanged`() = runTest {
        val criteria = """{"genres":["rock","pop"],"excluded":["soundtrack"],"minYear":1990,"maxItems":42}"""
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L, criteriaJson = criteria))

        assertEquals(criteria, smartPlaylistDao.getById("sp-1")?.criteriaJson)
    }

    @Test
    fun `deleteById removes only that playlist`() = runTest {
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L))
        smartPlaylistDao.insert(createPlaylist("sp-2", createdAt = 2_000L))

        smartPlaylistDao.deleteById("sp-1")

        assertNull(smartPlaylistDao.getById("sp-1"))
        assertNotNull(smartPlaylistDao.getById("sp-2"))
    }

    @Test
    fun `deleteAll empties the table`() = runTest {
        smartPlaylistDao.insert(createPlaylist("sp-1", createdAt = 1_000L))
        smartPlaylistDao.insert(createPlaylist("sp-2", createdAt = 2_000L))

        smartPlaylistDao.deleteAll()

        assertTrue(smartPlaylistDao.getAll().isEmpty())
        assertTrue(smartPlaylistDao.observeAll().first().isEmpty())
    }
}
