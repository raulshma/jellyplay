package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
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

/**
 * Pins the subtitle-pending mark at the SQL level. The pairing/atomicity
 * contract is documented canonically on [SyncBaselineDao.markSubtitlesPending];
 * these tests prove each primitive behaves as that contract assumes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyncBaselineDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var syncBaselineDao: SyncBaselineDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncBaselineDao = database.syncBaselineDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun fullBaseline(id: String = "item-1") = SyncBaselineEntity(
        id = id,
        syncedPosterTag = "poster-1",
        syncedBackdropTag = "backdrop-1",
        syncedMetadataSignature = "meta-sig",
        syncedSubtitleSignature = "sub-sig",
        syncedTrickplaySignature = "trick-sig",
        syncedSegmentsSignature = null,
        syncedMediaSourceId = "src-1",
        syncedMediaSizeBytes = 1_000L,
        lastSyncedAt = 42L,
    )

    @Test
    fun `markSubtitlesPending raises retry and badge flags on an existing row`() = runTest {
        syncBaselineDao.upsert(fullBaseline())

        syncBaselineDao.markSubtitlesPending("item-1")

        val row = syncBaselineDao.getBaseline("item-1")
        assertNotNull(row)
        assertFlagRow(row!!)
        // Nothing outside the three columns moves — not even lastSyncedAt.
        assertEquals("sub-sig", row.syncedSubtitleSignature)
        assertEquals("meta-sig", row.syncedMetadataSignature)
        assertEquals(42L, row.lastSyncedAt)
    }

    @Test
    fun `markSubtitlesPending creates a flag-only row when none exists`() = runTest {
        // The composed recipe on an un-seeded item: stub insert + raise in one
        // transaction. Null signatures read as first contact to the freshness
        // check.
        syncBaselineDao.markSubtitlesPending("item-1")

        val row = syncBaselineDao.getBaseline("item-1")
        assertNotNull(row)
        assertFlagRow(row!!)
        assertNull(row.syncedMetadataSignature)
        assertNull(row.syncedSubtitleSignature)
        assertNull(row.lastSyncedAt)
    }

    @Test
    fun `raiseSubtitlesPendingFlags is a counted no-op when no row exists`() = runTest {
        assertEquals(0, syncBaselineDao.raiseSubtitlesPendingFlags("item-1"))
        assertNull(syncBaselineDao.getBaseline("item-1"))
    }

    @Test
    fun `insertSubtitlesPendingStub creates a flag-only row when none exists`() = runTest {
        syncBaselineDao.insertSubtitlesPendingStub("item-1")

        val row = syncBaselineDao.getBaseline("item-1")
        assertNotNull(row)
        assertFlagRow(row!!)
        assertNull(row.syncedMetadataSignature)
        assertNull(row.syncedSubtitleSignature)
        assertNull(row.lastSyncedAt)
    }

    @Test
    fun `insertSubtitlesPendingStub ignores an existing baseline instead of clobbering it`() = runTest {
        syncBaselineDao.upsert(fullBaseline())

        syncBaselineDao.insertSubtitlesPendingStub("item-1")

        val row = syncBaselineDao.getBaseline("item-1")
        assertNotNull(row)
        assertEquals("sub-sig", row!!.syncedSubtitleSignature)
        assertEquals("meta-sig", row.syncedMetadataSignature)
        assertEquals(0, row.syncSubtitlesPending) // stub must not raise anything either
    }

    /** The invariant every marking path must produce: all three flags lit. */
    private fun assertFlagRow(row: SyncBaselineEntity) {
        assertEquals(1, row.syncSubtitlesPending)
        assertEquals(1, row.syncSubtitlesChanged)
        assertEquals(1, row.syncUpdateAvailable)
    }
}
