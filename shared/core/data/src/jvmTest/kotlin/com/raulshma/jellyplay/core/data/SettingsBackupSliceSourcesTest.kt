package com.raulshma.jellyplay.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.datastore.ArrPreferencesStore
import com.raulshma.jellyplay.core.datastore.ArrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.SecureKeyValueStorage
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.datastore.SubtitleProviderSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreferencePayload
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.WidgetConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips the four Wave-2 [ExternalBackupSlice] sources over REAL storage
 * (in-memory Room for the DAO-backed ones, temp-file DataStores for the
 * config surfaces): seed → `read()` → wipe/alter → `restore()` → state equal,
 * plus the seam's WHOLESALE-REPLACE semantics — a row absent from the incoming
 * element is gone after restore — and the surface boundaries (the widget
 * payload-cache keys stay out, secrets stay out of integrations, the local
 * itemprefs slice is NOT roam-capped).
 */
class SettingsBackupSliceSourcesTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var database: JellyPlayDatabase
    private lateinit var seerrDataStore: DataStore<Preferences>
    private lateinit var arrDataStore: DataStore<Preferences>
    private lateinit var subtitleDataStore: DataStore<Preferences>
    private lateinit var userPrefsDataStore: DataStore<Preferences>
    private lateinit var seerrStore: SeerrPreferencesStore
    private lateinit var arrStore: ArrPreferencesStore
    private lateinit var subtitleStore: SubtitleProviderPreferencesStore
    private lateinit var widgetStore: WidgetDataStore

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        seerrDataStore = newDataStore("seerr-prefs")
        arrDataStore = newDataStore("arr-prefs")
        subtitleDataStore = newDataStore("subtitle-prefs")
        userPrefsDataStore = newDataStore("user-prefs")
        val storage = FakeSecureKeyValueStorage()
        seerrStore = SeerrPreferencesStore(
            seerrDataStore,
            SeerrSecureCredentialsStore(storage),
            scope,
        )
        arrStore = ArrPreferencesStore(arrDataStore, ArrSecureCredentialsStore(storage), scope)
        subtitleStore = SubtitleProviderPreferencesStore(subtitleDataStore, SubtitleProviderSecureCredentialsStore(storage))
        widgetStore = WidgetDataStore(userPrefsDataStore, scope)
    }

    @AfterTest
    fun teardown() {
        database.close()
        scope.cancel()
    }

    private fun newDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = scope) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-backup-slices-test").apply { mkdirs() }
            File(dir, "$name-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }

    // ------------------------------------------------------------------
    // integrations
    // ------------------------------------------------------------------

    @Test
    fun integrations_roundTripsSeededSettingsAndResetsAbsentKeys() = runBlocking {
        seerrStore.setServerUrl("https://seerr.local")
        seerrStore.setEnabled(true)
        seerrStore.setDiscoverTrending(false)
        arrStore.setPollIntervalSeconds(120)
        subtitleStore.setWyzieEnabled(true)

        val source = IntegrationsBackupSliceSource(IntegrationsStoreFan(seerrStore, arrStore, subtitleStore))
        val slice = source.read()!!
        assertEquals(BackupSliceKey.INTEGRATIONS, source.key)
        // Kinds are inferred (bool/int primitives); the raw text is what round-trips.
        assertTrue(slice.jsonObject.getValue("seerr_enabled").jsonPrimitive.boolean)
        assertEquals(120, slice.jsonObject.getValue("arr_poll_interval_seconds").jsonPrimitive.content.toInt())
        // The secure credentials never ride the slice.
        assertFalse(slice.jsonObject.containsKey("seerr_api_key"))

        // Wipe everything, then restore: every seeded value comes back.
        for (name in seerrStore.SyncKeys) seerrStore.syncApply(name, null)
        for (name in arrStore.SyncKeys) arrStore.syncApply(name, null)
        for (name in subtitleStore.SyncKeys) subtitleStore.syncApply(name, null)

        source.restore(slice)
        assertEquals("https://seerr.local", seerrStore.syncSnapshot().getValue("seerr_server_url"))
        assertEquals("true", seerrStore.syncSnapshot().getValue("seerr_enabled"))
        assertEquals("false", seerrStore.syncSnapshot().getValue("seerr_discover_trending"))
        assertEquals("120", arrStore.syncSnapshot().getValue("arr_poll_interval_seconds"))
        assertEquals("true", subtitleStore.syncSnapshot().getValue("subtitle_wyzie_enabled"))

        // Wholesale replace: drop seerr_enabled from the incoming object and
        // restore — the absent allowlisted key resets to its default, WRITTEN
        // (value-presence resets roam; absence doesn't — see the stores'
        // syncApply contracts).
        source.restore(
            buildJsonObject {
                slice.jsonObject.forEach { (name, value) -> if (name != "seerr_enabled") put(name, value) }
            },
        )
        assertEquals("false", seerrStore.syncSnapshot().getValue("seerr_enabled"))
        assertEquals("https://seerr.local", seerrStore.syncSnapshot().getValue("seerr_server_url"))
    }

    @Test
    fun integrations_emptyState_isOmittedFromTheBackup() = runBlocking {
        val source = IntegrationsBackupSliceSource(IntegrationsStoreFan(seerrStore, arrStore, subtitleStore))
        assertNull(source.read(), "all-defaults = nothing to back up (slice omitted, never a wipe)")
    }

    // ------------------------------------------------------------------
    // itemPrefs
    // ------------------------------------------------------------------

    @Test
    fun itemPrefs_roundTripsRowsAndReplacesWholesale() = runBlocking {
        val dao = database.itemPlaybackPreferenceDao()
        dao.upsert(row("ITEM", "item-1", audioLanguage = "deu"))
        dao.upsert(row("SERIES", "series-1", subtitleLanguage = "eng", updatedAt = 2_000L))
        val source = ItemPrefsBackupSliceSource(database, dao)
        assertEquals(BackupSliceKey.ITEM_PREFS, source.key)

        val slice = source.read()!!.jsonObject
        assertEquals(setOf("ITEM/item-1", "SERIES/series-1"), slice.keys)
        assertEquals("deu", slice.getValue("ITEM/item-1").jsonObject.getValue("audioLanguage").jsonPrimitive.content)

        // Alter: wipe the seeded rows, keep one DIFFERENT row and a changed
        // local twin — then restore the CAPTURED slice and prove the snapshot
        // replays: the stray row is gone, the twin is replaced by the incoming
        // payload (wholesale replace).
        dao.deleteByKey("ITEM", "item-1")
        dao.deleteByKey("SERIES", "series-1")
        dao.upsert(row("ITEM", "other-item", audioLanguage = "fra"))
        dao.upsert(row("SERIES", "series-1", subtitleLanguage = "jpn", updatedAt = 9_999L))

        source.restore(slice)

        assertNull(dao.getByKey("ITEM", "other-item"), "a row absent from the incoming slice is deleted (wholesale replace)")
        assertEquals("deu", dao.getByKey("ITEM", "item-1")?.audioLanguage)
        assertEquals("eng", dao.getByKey("SERIES", "series-1")?.subtitleLanguage, "the incoming row replaced the local twin")
    }

    @Test
    fun itemPrefs_restoreReImposesTheSubtitleInvariant() = runBlocking {
        val dao = database.itemPlaybackPreferenceDao()
        val source = ItemPrefsBackupSliceSource(database, dao)
        val slice = buildJsonObject {
            put(
                "ITEM/item-1",
                // A row no local write could have produced (pinning a subtitle
                // language clears the disabled intent) — the restore must land
                // it exactly as a local write would have.
                SliceJson.encodeToJsonElement(
                    ItemPlaybackPreferencePayload(
                        scope = "ITEM",
                        key = "item-1",
                        subtitleLanguage = "eng",
                        subtitleDisabled = true,
                        updatedAt = 5_000L,
                    ),
                ),
            )
        }

        source.restore(slice)

        val restored = dao.getByKey("ITEM", "item-1")!!
        assertEquals("eng", restored.subtitleLanguage)
        assertNull(restored.subtitleDisabled, "the local-write invariant holds on the restore path too")
    }

    @Test
    fun itemPrefs_localSliceIsNotRoamCapped() = runBlocking {
        val dao = database.itemPlaybackPreferenceDao()
        repeat(120) { index -> dao.upsert(row("ITEM", "item-$index", updatedAt = index.toLong())) }
        val slice = ItemPrefsBackupSliceSource(database, dao).read()!!.jsonObject
        assertEquals(120, slice.size, "the 100-row roam cap is sync-only — the local backup carries every row")
    }

    @Test
    fun itemPrefs_emptyStore_isOmitted() = runBlocking {
        assertNull(ItemPrefsBackupSliceSource(database, database.itemPlaybackPreferenceDao()).read())
    }

    @Test
    fun itemPrefs_restoreRunsInATransaction_midPassFailureRollsTheWritesBack() = runBlocking {
        val realDao = database.itemPlaybackPreferenceDao()
        realDao.upsert(row("ITEM", "keep-1", audioLanguage = "deu"))
        realDao.upsert(row("ITEM", "keep-2", subtitleLanguage = "eng"))

        // Incoming: keep-1 rewritten, keep-2 ABSENT (the pass deletes it), and
        // a new "boom" row whose upsert is sabotaged to crash the pass after
        // the delete already ran — the pre-transactional failure shape.
        val payloadJson = { payload: ItemPlaybackPreferencePayload ->
            Json.encodeToJsonElement(ItemPlaybackPreferencePayload.serializer(), payload)
        }
        val incoming = buildJsonObject {
            put("ITEM/keep-1", payloadJson(ItemPlaybackPreferencePayload(scope = "ITEM", key = "keep-1", audioLanguage = "fra", updatedAt = 3_000L)))
            put("ITEM/boom", payloadJson(ItemPlaybackPreferencePayload(scope = "ITEM", key = "boom", audioLanguage = "rus", updatedAt = 4_000L)))
        }
        val failingDao = object : ItemPlaybackPreferenceDao by realDao {
            override suspend fun upsert(entity: ItemPlaybackPreferenceEntity) {
                if (entity.key == "boom") throw IllegalStateException("mid-pass crash")
                realDao.upsert(entity)
            }
        }

        assertFailsWith<IllegalStateException> {
            ItemPrefsBackupSliceSource(database, failingDao).restore(incoming)
        }

        // The family pass rolled back WHOLE: the keep-2 delete is undone, the
        // keep-1 rewrite is undone, and no boom row landed.
        assertEquals("deu", realDao.getByKey("ITEM", "keep-1")?.audioLanguage, "the rewrite must roll back")
        assertEquals("eng", realDao.getByKey("ITEM", "keep-2")?.subtitleLanguage, "the delete must roll back with the failed transaction")
        assertNull(realDao.getByKey("ITEM", "boom"))

        // And the database still takes a clean restore afterwards.
        val clean = buildJsonObject {
            incoming.forEach { (name, value) -> if (name != "ITEM/boom") put(name, value) }
        }
        ItemPrefsBackupSliceSource(database, realDao).restore(clean)
        assertEquals("fra", realDao.getByKey("ITEM", "keep-1")?.audioLanguage)
        assertNull(realDao.getByKey("ITEM", "keep-2"), "the clean pass deletes keep-2 for real")
    }

    // ------------------------------------------------------------------
    // playlists
    // ------------------------------------------------------------------

    @Test
    fun playlists_roundTripsAllThreeFamiliesAndReplacesWholesale() = runBlocking {
        val smartDao = database.smartPlaylistDao()
        val moodDao = database.moodPlaylistDao()
        smartDao.insert(SmartPlaylistEntity(id = "sp-1", name = "Recent", criteriaJson = "{}"))
        moodDao.insert(MoodPlaylistEntity(id = "mp-1", name = "Chill", emoji = "🌙", description = "", genreKeywordsJson = "[]"))
        moodDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "mp-1", isFavorite = true))

        val source = PlaylistsBackupSliceSource(database, smartDao, moodDao)
        assertEquals(BackupSliceKey.PLAYLISTS, source.key)
        val slice = source.read()!!.jsonObject
        assertEquals(setOf("smart/sp-1", "mood/mp-1", "moodpref/mp-1"), slice.keys)

        // Alter: delete the smart playlist, rename-replace the mood one, add a
        // stray preference row — then restore the captured slice.
        smartDao.deleteById("sp-1")
        moodDao.insert(MoodPlaylistEntity(id = "mp-1", name = "Mellow", emoji = "🎧", description = "", genreKeywordsJson = "[]"))
        moodDao.upsertPreference(MoodPlaylistPreferenceEntity(playlistId = "stray", isEnabled = false))
        source.restore(slice)

        assertEquals("Recent", smartDao.getById("sp-1")?.name, "the deleted definition is restored")
        assertEquals("Chill", moodDao.getById("mp-1")?.name, "the local twin is replaced by the incoming row")
        assertNull(moodDao.getPreference("stray"), "a preference row absent from the slice is deleted")
        assertEquals(true, moodDao.getPreference("mp-1")?.isFavorite)

        // Wholesale replace with an EMPTY object: every row goes.
        source.restore(buildJsonObject { })
        assertNull(smartDao.getById("sp-1"))
        assertNull(moodDao.getById("mp-1"))
        assertNull(moodDao.getPreference("mp-1"))
    }

    @Test
    fun playlists_emptyTables_areOmitted() = runBlocking {
        assertNull(PlaylistsBackupSliceSource(database, database.smartPlaylistDao(), database.moodPlaylistDao()).read())
    }

    @Test
    fun playlists_familiesCommitInTheirOwnTransaction_midPassFailureRollsOnlyItsFamily() = runBlocking {
        val smartDao = database.smartPlaylistDao()
        val realMoodDao = database.moodPlaylistDao()
        smartDao.insert(SmartPlaylistEntity(id = "sp-1", name = "Recent", criteriaJson = "{}"))
        realMoodDao.insert(MoodPlaylistEntity(id = "mp-1", name = "Chill", emoji = "🌙", description = "", genreKeywordsJson = "[]"))
        realMoodDao.insert(MoodPlaylistEntity(id = "mp-2", name = "Focus", emoji = "🎯", description = "", genreKeywordsJson = "[]"))

        val source = PlaylistsBackupSliceSource(database, smartDao, realMoodDao)
        val slice = source.read()!!.jsonObject
        assertEquals(setOf("smart/sp-1", "mood/mp-1", "mood/mp-2"), slice.keys)

        // Incoming: the mp-1 definition renamed, mp-2 ABSENT (the mood pass
        // deletes it). The mood DAO sabotages the mp-1 insert to crash that
        // pass after its delete already ran.
        val incoming = buildJsonObject {
            slice.forEach { (name, value) -> if (name != "mood/mp-1" && name != "mood/mp-2") put(name, value) }
            put("mood/mp-1", buildJsonObject {
                slice.getValue("mood/mp-1").jsonObject.forEach { (field, fieldJson) ->
                    if (field != "name") put(field, fieldJson)
                }
                put("name", JsonPrimitive("Incoming"))
            })
        }
        val failingMoodDao = object : MoodPlaylistDao by realMoodDao {
            override suspend fun insert(playlist: MoodPlaylistEntity) {
                if (playlist.id == "mp-1") throw IllegalStateException("mid-pass crash")
                realMoodDao.insert(playlist)
            }
        }

        // First make the smart pass's write observable: drop sp-1, so pass 1
        // must genuinely re-insert it before the mood pass ever runs.
        smartDao.deleteById("sp-1")
        assertFailsWith<IllegalStateException> {
            PlaylistsBackupSliceSource(database, smartDao, failingMoodDao).restore(incoming)
        }

        // The smart family pass COMMITTED (per-family transactions — not one
        // whole-restore transaction)…
        assertEquals("Recent", smartDao.getById("sp-1")?.name, "the committed family pass survives a later family's failure")
        // …while the mood pass rolled back WHOLE: the mp-2 delete is undone
        // and the renamed mp-1 insert is undone.
        assertEquals("Chill", realMoodDao.getById("mp-1")?.name, "the rewrite must roll back")
        assertEquals("Focus", realMoodDao.getById("mp-2")?.name, "the delete must roll back with the failed transaction")

        // And the same pass succeeds cleanly once the sabotage is gone.
        PlaylistsBackupSliceSource(database, smartDao, realMoodDao).restore(incoming)
        assertEquals("Incoming", realMoodDao.getById("mp-1")?.name)
        assertNull(realMoodDao.getById("mp-2"), "the clean pass deletes mp-2 for real")
    }

    // ------------------------------------------------------------------
    // widget
    // ------------------------------------------------------------------

    @Test
    fun widget_roundTripsConfigOnly_andNeverCarriesPayloadCaches() = runBlocking {
        widgetStore.setWidgetConfig(WidgetConfig(librarySource = LibraryRecommendationsSource.FAVORITES))
        widgetStore.setWidgetConfigForId(12, WidgetConfig(continueWatchingItemCount = 20))
        // The payload-cache keys are populated (the I/O buffer is live)...
        widgetStore.setContinueWatching(emptyList())

        val source = WidgetBackupSliceSource(widgetStore)
        assertEquals(BackupSliceKey.WIDGET, source.key)
        val slice = source.read()!!.jsonObject
        assertEquals(setOf("widget_config", "widget_configs"), slice.keys, "config only — the cache keys stay out")
        assertEquals(20, slice.getValue("widget_configs").jsonObject.getValue("12")
            .jsonObject.getValue("continueWatchingItemCount").jsonPrimitive.content.toInt())

        // Alter: clear both config keys + write a stray config, then restore.
        widgetStore.setWidgetConfig(WidgetConfig(librarySource = LibraryRecommendationsSource.LATEST))
        widgetStore.removeWidgetConfigForId(12)
        widgetStore.setWidgetConfigForId(13, WidgetConfig(nowPlayingShowArtwork = false))
        source.restore(slice)

        // The suspending read paths (raw prefs mapping) — not the eager sync
        // snapshot, which may lag the write by a dispatch.
        assertEquals(LibraryRecommendationsSource.FAVORITES, widgetStore.widgetConfig.first().librarySource)
        assertEquals(20, widgetStore.getWidgetConfigForId(12).first().continueWatchingItemCount)
        // A per-widget entry absent from the slice is gone (wholesale replace);
        // the reader then falls back to the restored legacy global config —
        // its documented per-widget → legacy → default ladder.
        val configsRaw = widgetStore.configSnapshot().getValue("widget_configs")!!
        assertFalse(configsRaw.contains("\"13\""), "a per-widget config absent from the slice resets to default")
        assertTrue(configsRaw.contains("\"12\""))
        // ...and the payload cache was never touched by the restore.
        val continueWatchingKey = stringPreferencesKey("continue_watching")
        assertTrue(userPrefsDataStore.data.first()[continueWatchingKey] != null)
    }

    @Test
    fun widget_defaultState_isOmitted() = runBlocking {
        assertNull(WidgetBackupSliceSource(widgetStore).read())
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun row(
        scope: String,
        key: String,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
        updatedAt: Long = 1_000L,
    ) = ItemPlaybackPreferenceEntity(
        scope = scope,
        key = key,
        audioLanguage = audioLanguage,
        subtitleLanguage = subtitleLanguage,
        updatedAt = updatedAt,
    )

    /** Minimal in-memory stand-in for the OS-encrypted key-value store. */
    private class FakeSecureKeyValueStorage : SecureKeyValueStorage {
        val raw = mutableMapOf<String, String>()
        override fun getString(key: String, defValue: String?): String? = raw[key] ?: defValue
        override fun putString(key: String, value: String?) {
            if (value == null) raw.remove(key) else raw[key] = value
        }

        override fun remove(key: String) {
            raw.remove(key)
        }
    }
}
