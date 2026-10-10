package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistEntity
import com.raulshma.jellyplay.core.database.entity.MoodPlaylistPreferenceEntity
import com.raulshma.jellyplay.core.database.entity.SmartPlaylistEntity
import com.raulshma.jellyplay.core.model.MoodPlaylistPayload
import com.raulshma.jellyplay.core.model.MoodPlaylistPreferencePayload
import com.raulshma.jellyplay.core.model.SmartPlaylistPayload
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter tests for the `playlists` namespace (the settings-backup wave): a
 * real in-memory Room for the smart/mood playlist stores plus a temp-file
 * DataStore mirror — the three key families (`smart/{id}`, `mood/{id}`,
 * `moodpref/{playlistId}`), whole-row payload values, the mirror-based
 * dirty/deleted detection, and both delete directions (a deleted playlist
 * roams to every device).
 */
class JellyPlayPlaylistsSyncAdapterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayPlaylistsSyncAdapter

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        mirrorStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-playlists-adapter-test").apply { mkdirs() }
            File(dir, "playlists-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
        adapter = JellyPlayPlaylistsSyncAdapter(
            smartPlaylistDao = database.smartPlaylistDao(),
            moodPlaylistDao = database.moodPlaylistDao(),
            mirrorStore = mirrorStore,
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
        scope.cancel()
    }

    private suspend fun seedSmart(
        id: String = "smart-1",
        name: String = "New Releases",
        criteriaJson: String = "{\"kind\":\"NEW\",\"limit\":20}",
        updatedAt: Long = 1_000L,
    ): SmartPlaylistEntity {
        val entity = SmartPlaylistEntity(
            id = id,
            name = name,
            criteriaJson = criteriaJson,
            maxItems = 20,
            sortBy = "TITLE",
            createdAt = 500L,
            updatedAt = updatedAt,
        )
        database.smartPlaylistDao().insert(entity)
        return entity
    }

    private suspend fun seedMood(id: String = "mood-1"): MoodPlaylistEntity {
        val entity = MoodPlaylistEntity(
            id = id,
            name = "Chill",
            emoji = "🌙",
            description = "Relaxed tunes",
            genreKeywordsJson = "[\"chill\",\"lo-fi\"]",
            excludedGenresJson = "[\"metal\"]",
            minRating = 3.5f,
            sortBy = "RATING",
            maxItems = 40,
            themeColorHex = "#87CEEB",
            createdAt = 600L,
            updatedAt = 1_100L,
        )
        database.moodPlaylistDao().insert(entity)
        return entity
    }

    private suspend fun seedMoodPref(playlistId: String = "mood-1"): MoodPlaylistPreferenceEntity {
        val entity = MoodPlaylistPreferenceEntity(
            playlistId = playlistId,
            isEnabled = false,
            isFavorite = true,
            lastPlayedAt = 900L,
            updatedAt = 1_200L,
        )
        database.moodPlaylistDao().upsertPreference(entity)
        return entity
    }

    // ------------------------------------------------------------------
    // snapshot: all three key families, whole-row payloads
    // ------------------------------------------------------------------

    @Test
    fun snapshot_coversAllThreeFamilies_withWholeRowPayloads() = runBlocking {
        val smart = seedSmart()
        val mood = seedMood()
        val pref = seedMoodPref()

        val snapshot = adapter.snapshot()

        assertEquals(
            setOf("smart/smart-1", "mood/mood-1", "moodpref/mood-1"),
            snapshot.keys,
        )
        assertEquals(
            buildJsonObject {
                put("id", smart.id)
                put("name", smart.name)
                put("criteriaJson", smart.criteriaJson)
                put("maxItems", 20)
                put("sortBy", "TITLE")
                put("createdAt", 500L)
                put("updatedAt", 1_000L)
            },
            snapshot.getValue("smart/smart-1"),
        )
        assertEquals(
            buildJsonObject {
                put("id", mood.id)
                put("name", mood.name)
                put("emoji", mood.emoji)
                put("description", mood.description)
                put("genreKeywordsJson", mood.genreKeywordsJson)
                put("excludedGenresJson", mood.excludedGenresJson)
                put("minRating", 3.5)
                put("sortBy", "RATING")
                put("maxItems", 40)
                put("themeColorHex", mood.themeColorHex)
                put("createdAt", 600L)
                put("updatedAt", 1_100L)
            },
            snapshot.getValue("mood/mood-1"),
        )
        assertEquals(
            buildJsonObject {
                put("playlistId", pref.playlistId)
                put("isEnabled", false)
                put("isFavorite", true)
                put("lastPlayedAt", 900L)
                put("updatedAt", 1_200L)
            },
            snapshot.getValue("moodpref/mood-1"),
        )
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean_editRedirtiesOnlyTheEditedKey() = runBlocking {
        seedSmart()
        seedMood()
        seedMoodPref()
        val snapshot = adapter.snapshot()

        assertEquals(3, adapter.dirtyValues(snapshot).size) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())

        // A local definition edit re-dirties only its key.
        val mood = database.moodPlaylistDao().getById("mood-1")!!
        database.moodPlaylistDao().insert(mood.copy(name = "Chill (renamed)", updatedAt = 2_000L))
        assertEquals(
            setOf("mood/mood-1"),
            adapter.dirtyValues(adapter.snapshot()).keys,
        )
    }

    // ------------------------------------------------------------------
    // applyRemote: adopt each family, coerce-or-skip
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsEachFamily() = runBlocking {
        val json = Json { ignoreUnknownKeys = true }

        adapter.applyRemote(
            mapOf(
                "smart/remote-smart" to json.encodeToJsonElement(
                    SmartPlaylistPayload(
                        id = "remote-smart",
                        name = "Remote Smart",
                        criteriaJson = "{\"kind\":\"GENRE\",\"genre\":\"Comedy\"}",
                        maxItems = 10,
                        sortBy = "YEAR_DESC",
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                ),
                "mood/remote-mood" to json.encodeToJsonElement(
                    MoodPlaylistPayload(
                        id = "remote-mood",
                        name = "Remote Mood",
                        emoji = "🎧",
                        description = "from afar",
                        genreKeywordsJson = "[\"synthwave\"]",
                        minRating = 4.0f,
                        updatedAt = 3L,
                    ),
                ),
                "moodpref/remote-mood" to json.encodeToJsonElement(
                    MoodPlaylistPreferencePayload(
                        playlistId = "remote-mood",
                        isEnabled = true,
                        isFavorite = true,
                        lastPlayedAt = 42L,
                        updatedAt = 4L,
                    ),
                ),
            ),
        )

        val smart = database.smartPlaylistDao().getById("remote-smart")!!
        assertEquals("Remote Smart", smart.name)
        // The criteria blob rides verbatim; the receiving device parses it.
        assertEquals("{\"kind\":\"GENRE\",\"genre\":\"Comedy\"}", smart.criteriaJson)
        assertEquals("YEAR_DESC", smart.sortBy)

        val mood = database.moodPlaylistDao().getById("remote-mood")!!
        assertEquals("Remote Mood", mood.name)
        assertEquals("[\"synthwave\"]", mood.genreKeywordsJson)
        assertEquals(4.0f, mood.minRating)
        assertNull(mood.excludedGenresJson)

        val pref = database.moodPlaylistDao().getPreference("remote-mood")!!
        assertTrue(pref.isEnabled)
        assertTrue(pref.isFavorite)
        assertEquals(42L, pref.lastPlayedAt)
    }

    @Test
    fun applyRemote_replacesTheLocalTwinAtTheId() = runBlocking {
        seedSmart(name = "Old Name")

        val json = Json { ignoreUnknownKeys = true }
        adapter.applyRemote(
            mapOf(
                "smart/smart-1" to json.encodeToJsonElement(
                    SmartPlaylistPayload(
                        id = "smart-1",
                        name = "New Name",
                        criteriaJson = "{\"kind\":\"NEW\"}",
                    ),
                ),
            ),
        )

        assertEquals(1, database.smartPlaylistDao().getAll().size)
        assertEquals("New Name", database.smartPlaylistDao().getById("smart-1")!!.name)
    }

    @Test
    fun applyRemote_malformedOrMismatched_skipped() = runBlocking {
        seedSmart()

        adapter.applyRemote(
            mapOf(
                // Not a JSON object.
                "smart/smart-2" to JsonPrimitive("garbage"),
                // Payload disagrees with its own key.
                "smart/smart-3" to buildJsonObject {
                    put("id", "smart-999")
                    put("name", "liar")
                    put("criteriaJson", "{}")
                },
                // Unknown key family.
                "vhs/vhs-1" to buildJsonObject {
                    put("id", "vhs-1")
                },
                // Unparseable key.
                "weird" to buildJsonObject { put("id", "weird") },
            ),
        )

        assertEquals(1, database.smartPlaylistDao().getAll().size)
        assertEquals("New Releases", database.smartPlaylistDao().getById("smart-1")!!.name)
    }

    // ------------------------------------------------------------------
    // deletes roam both ways
    // ------------------------------------------------------------------

    @Test
    fun localDelete_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runBlocking {
        seedSmart()
        seedMood()
        seedMoodPref()
        adapter.markSynced(adapter.snapshot())

        database.smartPlaylistDao().deleteById("smart-1")
        database.moodPlaylistDao().deletePreferenceById("mood-1")

        assertEquals(setOf("smart/smart-1", "moodpref/mood-1"), adapter.deletedKeys())

        adapter.deleteRemote(setOf("smart/smart-1", "moodpref/mood-1"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
        assertNull(database.smartPlaylistDao().getById("smart-1"))
        assertNull(database.moodPlaylistDao().getPreference("mood-1"))
        for (key in listOf("smart/smart-1", "moodpref/mood-1")) {
            assertNull(
                mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, key))],
            )
        }
    }

    @Test
    fun deleteRemote_removesEachFamily_andClearsMirrorRegardlessOfKeyShape() = runBlocking {
        seedSmart()
        seedMood()
        seedMoodPref()
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("mood/mood-1", "not-a-playlist-key"))

        assertNull(database.moodPlaylistDao().getById("mood-1"))
        // The mood definition's tombstone does not touch its (independent)
        // preference row — each key roams its own delete.
        assertEquals(1, database.moodPlaylistDao().getAllPreferences().size)
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "mood/mood-1"))],
        )
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "not-a-playlist-key"))],
        )
    }

    @Test
    fun applyRemote_jsonNull_deletesEachFamilyRow() = runBlocking {
        seedSmart()
        seedMood()
        seedMoodPref()

        adapter.applyRemote(
            mapOf(
                "smart/smart-1" to JsonNull,
                "mood/mood-1" to JsonNull,
                "moodpref/mood-1" to JsonNull,
            ),
        )

        assertNull(database.smartPlaylistDao().getById("smart-1"))
        assertNull(database.moodPlaylistDao().getById("mood-1"))
        assertNull(database.moodPlaylistDao().getPreference("mood-1"))
    }

    private companion object {
        const val NAMESPACE = "playlists"
    }
}
