package com.raulshma.jellyplay.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
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
class ItemPlaybackPreferenceDaoTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var dao: ItemPlaybackPreferenceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, JellyPlayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.itemPlaybackPreferenceDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun pref(
        scope: String = "SERIES",
        key: String = "series-1",
        audioLanguage: String? = "deu",
        subtitleLanguage: String? = "eng",
    ) = ItemPlaybackPreferenceEntity(
        scope = scope,
        key = key,
        audioLanguage = audioLanguage,
        subtitleLanguage = subtitleLanguage,
        updatedAt = 1_000L,
    )

    @Test
    fun `upsert and getByKey`() = runTest {
        dao.upsert(pref())
        val result = dao.getByKey("SERIES", "series-1")
        assertNotNull(result)
        assertEquals("deu", result!!.audioLanguage)
        assertEquals("eng", result.subtitleLanguage)
    }

    @Test
    fun `upsert replaces same scope and key`() = runTest {
        dao.upsert(pref(audioLanguage = "deu", subtitleLanguage = "eng"))
        // Second insert with the same (scope, key) must overwrite, not duplicate.
        dao.upsert(pref(audioLanguage = "fra", subtitleLanguage = null))

        val result = dao.getByKey("SERIES", "series-1")
        assertNotNull(result)
        assertEquals("fra", result!!.audioLanguage)
        assertNull(result.subtitleLanguage)
        assertEquals(1, dao.countByScope("SERIES"))
    }

    @Test
    fun `item and series scopes are independent`() = runTest {
        dao.upsert(pref(scope = "ITEM", key = "item-1", audioLanguage = "jpn"))
        dao.upsert(pref(scope = "SERIES", key = "series-1", audioLanguage = "deu"))

        assertEquals("jpn", dao.getByKey("ITEM", "item-1")!!.audioLanguage)
        assertEquals("deu", dao.getByKey("SERIES", "series-1")!!.audioLanguage)
        assertEquals(1, dao.countByScope("ITEM"))
        assertEquals(1, dao.countByScope("SERIES"))
    }

    @Test
    fun `getByKey returns null when absent`() = runTest {
        assertNull(dao.getByKey("SERIES", "missing"))
    }

    @Test
    fun `deleteByKey removes only the matching row`() = runTest {
        dao.upsert(pref(scope = "SERIES", key = "series-1"))
        dao.upsert(pref(scope = "SERIES", key = "series-2"))

        dao.deleteByKey("SERIES", "series-1")

        assertNull(dao.getByKey("SERIES", "series-1"))
        assertNotNull(dao.getByKey("SERIES", "series-2"))
        assertEquals(1, dao.countByScope("SERIES"))
    }

    @Test
    fun `subtitle role fields round-trip`() = runTest {
        // Proves the v38→v39 columns (subtitleForced / subtitleHearingImpaired)
        // exist and are read/written by the Room-generated mapping.
        dao.upsert(
            ItemPlaybackPreferenceEntity(
                scope = "SERIES",
                key = "series-1",
                audioLanguage = null,
                subtitleLanguage = "eng",
                subtitleForced = true,
                subtitleHearingImpaired = true,
                updatedAt = 1_000L,
            )
        )
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertEquals(true, saved!!.subtitleForced)
        assertEquals(true, saved.subtitleHearingImpaired)
    }

    @Test
    fun `subtitle role fields default to null when omitted`() = runTest {
        // Legacy/default behaviour: a row written without the role fields reads
        // both as null (preserves today's language-only semantics).
        dao.upsert(pref(subtitleLanguage = "eng"))
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertNull(saved!!.subtitleForced)
        assertNull(saved.subtitleHearingImpaired)
    }

    @Test
    fun `subtitle disabled field round-trips`() = runTest {
        // Proves the v41→v42 column (subtitleDisabled) exists and is read/written
        // by the Room-generated mapping.
        dao.upsert(
            ItemPlaybackPreferenceEntity(
                scope = "SERIES",
                key = "series-1",
                audioLanguage = null,
                subtitleLanguage = null,
                subtitleDisabled = true,
                updatedAt = 1_000L,
            )
        )
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertEquals(true, saved!!.subtitleDisabled)
    }

    @Test
    fun `subtitle disabled field defaults to null when omitted`() = runTest {
        // Legacy/default behaviour: a row written without the disabled field reads
        // null (preserves today's language-only semantics).
        dao.upsert(pref())
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertNull(saved!!.subtitleDisabled)
    }

    @Test
    fun `remembered track fields round-trip`() = runTest {
        // Proves the v39→v40 columns (the six remembered* fields) exist and are
        // read/written by the Room-generated mapping.
        dao.upsert(
            ItemPlaybackPreferenceEntity(
                scope = "SERIES",
                key = "series-1",
                audioLanguage = null,
                subtitleLanguage = null,
                rememberedAudioLabel = "English · 5.1 · DTS",
                rememberedAudioLanguage = "eng",
                rememberedAudioIndex = 0,
                rememberedSubtitleLabel = "English · SDH",
                rememberedSubtitleLanguage = "eng",
                rememberedSubtitleIndex = 1,
                updatedAt = 1_000L,
            )
        )
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertEquals("English · 5.1 · DTS", saved!!.rememberedAudioLabel)
        assertEquals("eng", saved.rememberedAudioLanguage)
        assertEquals(0, saved.rememberedAudioIndex)
        assertEquals("English · SDH", saved.rememberedSubtitleLabel)
        assertEquals("eng", saved.rememberedSubtitleLanguage)
        assertEquals(1, saved.rememberedSubtitleIndex)
    }

    @Test
    fun `remembered track fields default to null when omitted`() = runTest {
        // Legacy/default behaviour: a row written without the remembered fields
        // reads all six as null (preserves today's language-only semantics).
        dao.upsert(pref())
        val saved = dao.getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertNull(saved!!.rememberedAudioLabel)
        assertNull(saved.rememberedAudioLanguage)
        assertNull(saved.rememberedAudioIndex)
        assertNull(saved.rememberedSubtitleLabel)
        assertNull(saved.rememberedSubtitleLanguage)
        assertNull(saved.rememberedSubtitleIndex)
    }
}
