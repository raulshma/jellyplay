package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.OfflineMediaEntity
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
class OfflineMediaDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var offlineMediaDao: OfflineMediaDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        offlineMediaDao = database.offlineMediaDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createMedia(
        id: String = "media-1",
        mediaType: String = "MOVIE",
        seriesId: String? = null,
        seasonId: String? = null,
    ) = OfflineMediaEntity(
        id = id,
        name = "Test Media",
        mediaType = mediaType,
        seriesId = seriesId,
        seasonId = seasonId,
    )

    @Test
    fun `upsert and getById`() = runTest {
        val media = createMedia()
        offlineMediaDao.upsert(media)

        val result = offlineMediaDao.getById("media-1")
        assertNotNull(result)
        assertEquals("Test Media", result!!.name)
    }

    @Test
    fun `getById returns null for non-existent`() = runTest {
        assertNull(offlineMediaDao.getById("nonexistent"))
    }

    @Test
    fun `getTopLevelItems returns series movies audio music`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE"))
        offlineMediaDao.upsert(createMedia(id = "m2", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "m3", mediaType = "AUDIO"))
        offlineMediaDao.upsert(createMedia(id = "m4", mediaType = "EPISODE"))

        val items = offlineMediaDao.getTopLevelItems().first()
        assertEquals(3, items.size)
    }

    @Test
    fun `deleteById removes media`() = runTest {
        offlineMediaDao.upsert(createMedia())
        offlineMediaDao.deleteById("media-1")

        assertNull(offlineMediaDao.getById("media-1"))
    }

    @Test
    fun `deleteBySeriesId removes all items with seriesId`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "m2", seriesId = "series-1"))
        offlineMediaDao.upsert(createMedia(id = "m3", seriesId = "series-2"))

        offlineMediaDao.deleteBySeriesId("series-1")

        assertNull(offlineMediaDao.getById("m1"))
        assertNull(offlineMediaDao.getById("m2"))
        assertNotNull(offlineMediaDao.getById("m3"))
    }

    @Test
    fun `upsertAll inserts multiple items`() = runTest {
        val items = listOf(
            createMedia(id = "m1"),
            createMedia(id = "m2"),
        )
        offlineMediaDao.upsertAll(items)

        assertNotNull(offlineMediaDao.getById("m1"))
        assertNotNull(offlineMediaDao.getById("m2"))
    }

    @Test
    fun `getOfflineItemCount counts top-level items`() = runTest {
        offlineMediaDao.upsert(createMedia(id = "m1", mediaType = "MOVIE"))
        offlineMediaDao.upsert(createMedia(id = "m2", mediaType = "SERIES"))
        offlineMediaDao.upsert(createMedia(id = "m3", mediaType = "EPISODE"))

        val count = offlineMediaDao.getOfflineItemCount().first()
        assertEquals(2, count)
    }
}
