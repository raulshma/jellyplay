package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity
import com.raulshma.jellyplay.core.model.ItemPlaybackPreferencePayload
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter tests for the `itemprefs` namespace (the settings-backup wave): a
 * real in-memory Room for the `item_playback_preferences` store plus a
 * temp-file DataStore mirror — the `"{scope}/{key}"` keying with full payload
 * values, the mirror-based dirty/deleted detection, both delete directions,
 * the 100-row roam cap (aged-out rows roam deletes too — intended), and the
 * adopt-side guards (scope-name validation, the subtitle mutual-exclusion
 * invariant, refused rows clearing their mirror entry).
 */
class JellyPlayItemPrefsSyncAdapterTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayItemPrefsSyncAdapter

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        mirrorStore = PreferenceDataStoreFactory.createWithPath(scope = scope) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-itemprefs-adapter-test").apply { mkdirs() }
            File(dir, "itemprefs-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
        adapter = JellyPlayItemPrefsSyncAdapter(
            dao = database.itemPlaybackPreferenceDao(),
            mirrorStore = mirrorStore,
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
        scope.cancel()
    }

    private suspend fun seed(
        scope: String = "ITEM",
        key: String = "item-1",
        updatedAt: Long = 1_000L,
        audioLanguage: String? = "deu",
        subtitleLanguage: String? = null,
        subtitleDisabled: Boolean? = null,
        renderProfile: String? = null,
        preferredMediaSourceId: String? = null,
    ): ItemPlaybackPreferenceEntity {
        val entity = ItemPlaybackPreferenceEntity(
            scope = scope,
            key = key,
            audioLanguage = audioLanguage,
            subtitleLanguage = subtitleLanguage,
            subtitleDisabled = subtitleDisabled,
            renderProfile = renderProfile,
            preferredMediaSourceId = preferredMediaSourceId,
            updatedAt = updatedAt,
        )
        database.itemPlaybackPreferenceDao().upsert(entity)
        return entity
    }

    private suspend fun rows(scope: String, key: String) = database.itemPlaybackPreferenceDao().getByKey(scope, key)

    private fun payloadOf(entity: ItemPlaybackPreferenceEntity) = buildJsonObject {
        put("scope", entity.scope)
        put("key", entity.key)
        put("audioLanguage", entity.audioLanguage)
        put("subtitleLanguage", entity.subtitleLanguage)
        put("subtitleDisabled", entity.subtitleDisabled)
        put("subtitleForced", entity.subtitleForced)
        put("subtitleHearingImpaired", entity.subtitleHearingImpaired)
        put("dialogueBoostStrength", entity.dialogueBoostStrength)
        put("rememberedAudioLabel", entity.rememberedAudioLabel)
        put("rememberedAudioLanguage", entity.rememberedAudioLanguage)
        put("rememberedAudioIndex", entity.rememberedAudioIndex)
        put("rememberedAudioCodec", entity.rememberedAudioCodec)
        put("rememberedSubtitleLabel", entity.rememberedSubtitleLabel)
        put("rememberedSubtitleLanguage", entity.rememberedSubtitleLanguage)
        put("rememberedSubtitleIndex", entity.rememberedSubtitleIndex)
        put("rememberedSubtitleCodec", entity.rememberedSubtitleCodec)
        put("renderProfile", entity.renderProfile)
        put("preferredMediaSourceId", entity.preferredMediaSourceId)
        put("updatedAt", entity.updatedAt)
    }

    // ------------------------------------------------------------------
    // snapshot: {scope}/{key} keys, full payload values
    // ------------------------------------------------------------------

    @Test
    fun snapshot_keysRowsWithFullPayload() = runBlocking {
        val entity = seed(
            scope = "SERIES",
            key = "series-1",
            audioLanguage = "deu",
            subtitleLanguage = "eng",
            renderProfile = "{\"shaderPack\":\"anime4k\"}",
            preferredMediaSourceId = "source-2",
        )

        val snapshot = adapter.snapshot()

        assertEquals(mapOf("SERIES/series-1" to payloadOf(entity)), snapshot)
        // The render-profile blob rides verbatim (opaque on the wire).
        val value = snapshot.getValue("SERIES/series-1").jsonObject
        assertEquals(
            "{\"shaderPack\":\"anime4k\"}",
            (value["renderProfile"] as JsonPrimitive).content,
        )
        assertEquals(1_000L, value["updatedAt"]?.toString()?.toLong())
    }

    // ------------------------------------------------------------------
    // the roam cap: only the 100 most-recent rows sync; aged-out rows roam
    // deletes (intended)
    // ------------------------------------------------------------------

    @Test
    fun snapshot_capKeepsTheMostRecentRows() = runBlocking {
        for (i in 1..ItemPlaybackPreferenceRoamCap.CAP + 50) {
            seed(key = "item-$i", updatedAt = i.toLong())
        }

        val snapshot = adapter.snapshot()

        assertEquals(ItemPlaybackPreferenceRoamCap.CAP, snapshot.size)
        // The oldest 50 aged out of the window...
        for (i in 1..50) assertTrue("ITEM/item-$i" !in snapshot)
        // ...the newest 100 are the sync set.
        for (i in 51..ItemPlaybackPreferenceRoamCap.CAP + 50) {
            assertTrue("ITEM/item-$i" in snapshot)
        }
        // The Room store itself stays unbounded.
        assertEquals(ItemPlaybackPreferenceRoamCap.CAP + 50, database.itemPlaybackPreferenceDao().countByScope("ITEM"))
    }

    @Test
    fun rowAgedOutOfTheCap_reportsDeletedKey() = runBlocking {
        for (i in 1..ItemPlaybackPreferenceRoamCap.CAP) {
            seed(key = "item-$i", updatedAt = i.toLong())
        }
        adapter.markSynced(adapter.snapshot())
        assertTrue(adapter.deletedKeys().isEmpty())

        // One newer write pushes the oldest row (updatedAt = 1) out of the
        // window: it reads as a local delete and roams a tombstone.
        seed(key = "item-new", updatedAt = 10_000L)

        assertEquals(setOf("ITEM/item-1"), adapter.deletedKeys())

        // The engine's confirmation clears the mirror: nothing re-reads
        // dirty or deleted afterwards.
        adapter.deleteRemote(setOf("ITEM/item-1"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "ITEM/item-1"))],
        )
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean_editRedirties() = runBlocking {
        seed(key = "item-1", audioLanguage = "deu")
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).containsKey("ITEM/item-1")) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())

        // A local edit re-dirties the key.
        val row = rows("ITEM", "item-1")!!
        database.itemPlaybackPreferenceDao().upsert(row.copy(audioLanguage = "fra", updatedAt = 2_000L))
        assertEquals(
            mapOf("ITEM/item-1" to adapter.snapshot().getValue("ITEM/item-1")),
            adapter.dirtyValues(adapter.snapshot()),
        )
    }

    // ------------------------------------------------------------------
    // deletes roam both ways
    // ------------------------------------------------------------------

    @Test
    fun localDelete_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runBlocking {
        seed(key = "item-1")
        adapter.markSynced(adapter.snapshot())

        database.itemPlaybackPreferenceDao().deleteByKey("ITEM", "item-1")

        assertEquals(setOf("ITEM/item-1"), adapter.deletedKeys())

        adapter.deleteRemote(setOf("ITEM/item-1"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
        assertNull(rows("ITEM", "item-1"))
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "ITEM/item-1"))],
        )
    }

    @Test
    fun deleteRemote_removesOnlyTheNamedRows() = runBlocking {
        seed(key = "item-1")
        seed(key = "item-2")
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("ITEM/item-1"))

        assertNull(rows("ITEM", "item-1"))
        assertEquals("deu", rows("ITEM", "item-2")?.audioLanguage)
        assertTrue(adapter.deletedKeys().isEmpty())
    }

    @Test
    fun deleteRemote_unparseableKey_stillClearsMirror() = runBlocking {
        adapter.markSynced(mapOf("weird" to JsonPrimitive("{}")))

        adapter.deleteRemote(setOf("weird"))

        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, "weird"))],
        )
    }

    // ------------------------------------------------------------------
    // applyRemote: adopt, coerce-or-skip
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsFullPayload() = runBlocking {
        adapter.applyRemote(
            mapOf(
                "SERIES/series-9" to buildJsonObject {
                    put("scope", "SERIES")
                    put("key", "series-9")
                    put("audioLanguage", "jpn")
                    put("subtitleLanguage", "eng")
                    put("subtitleForced", true)
                    put("dialogueBoostStrength", "HIGH")
                    put("rememberedAudioLabel", "English · 5.1")
                    put("rememberedAudioLanguage", "eng")
                    put("rememberedAudioIndex", 2)
                    put("rememberedAudioCodec", "eac3")
                    put("renderProfile", "{\"toneMapping\":\"bt.2446a\"}")
                    put("preferredMediaSourceId", "source-7")
                    put("updatedAt", 5_000L)
                },
            ),
        )

        val row = rows("SERIES", "series-9")!!
        assertEquals("jpn", row.audioLanguage)
        assertEquals("eng", row.subtitleLanguage)
        assertEquals(true, row.subtitleForced)
        assertEquals("HIGH", row.dialogueBoostStrength)
        assertEquals("English · 5.1", row.rememberedAudioLabel)
        assertEquals(2, row.rememberedAudioIndex)
        assertEquals("eac3", row.rememberedAudioCodec)
        assertEquals("{\"toneMapping\":\"bt.2446a\"}", row.renderProfile)
        assertEquals("source-7", row.preferredMediaSourceId)
        assertEquals(5_000L, row.updatedAt)
    }

    @Test
    fun applyRemote_replacesTheLocalTwinAtTheKey() = runBlocking {
        seed(key = "item-1", audioLanguage = "deu", updatedAt = 1_000L)
        val json = Json { ignoreUnknownKeys = true }

        adapter.applyRemote(
            mapOf(
                "ITEM/item-1" to json.encodeToJsonElement(
                    ItemPlaybackPreferencePayload(
                        scope = "ITEM",
                        key = "item-1",
                        audioLanguage = "fra",
                        updatedAt = 2_000L,
                    ),
                ),
            ),
        )

        val row = rows("ITEM", "item-1")!!
        assertEquals("fra", row.audioLanguage)
        assertEquals(2_000L, row.updatedAt)
    }

    @Test
    fun applyRemote_malformedOrMismatched_skipped() = runBlocking {
        seed(key = "item-1", audioLanguage = "deu")

        adapter.applyRemote(
            mapOf(
                // Not a JSON object.
                "ITEM/item-2" to JsonPrimitive("garbage"),
                // Payload disagrees with its own key.
                "ITEM/item-3" to buildJsonObject {
                    put("scope", "ITEM")
                    put("key", "item-999")
                    put("audioLanguage", "jpn")
                },
                // Unparseable key.
                "weird" to buildJsonObject {
                    put("scope", "ITEM")
                    put("key", "weird")
                },
                // A null rides the defensive delete path (row still absent).
                "ITEM/item-4" to JsonNull,
            ),
        )

        assertEquals(1, database.itemPlaybackPreferenceDao().countByScope("ITEM"))
        assertEquals("deu", rows("ITEM", "item-1")?.audioLanguage)
        assertNull(rows("ITEM", "item-2"))
        assertNull(rows("ITEM", "item-3"))
        assertNull(rows("ITEM", "item-4"))
    }

    // ------------------------------------------------------------------
    // adopt-side guards: the repository's local-write invariants hold for
    // adopted rows too, and hostile rows are refused (and de-mirrored)
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_enforcesTheSubtitleMutualExclusionOnAdopt() = runBlocking {
        // A row LOCAL writes can't produce: the repository's save clears the
        // disabled intent the moment a language is pinned
        // (ItemPlaybackPreferenceRepositoryImpl.save). Adoption must land the
        // row the same way, not verbatim.
        adapter.applyRemote(
            mapOf(
                "ITEM/item-1" to buildJsonObject {
                    put("scope", "ITEM")
                    put("key", "item-1")
                    put("subtitleLanguage", "eng")
                    put("subtitleDisabled", true)
                    put("updatedAt", 5_000L)
                },
            ),
        )

        val row = rows("ITEM", "item-1")!!
        assertEquals("eng", row.subtitleLanguage)
        assertNull(row.subtitleDisabled)
    }

    @Test
    fun applyRemote_hostilePayload_refused_andMirrorEntryCleared() = runBlocking {
        // A key/payload-agreeing row whose scope is NOT a PlaybackPrefScope
        // name (a traversal-shaped row key and a maximal LWW stamp along for
        // the ride) — refused like any garbage row.
        val hostile = buildJsonObject {
            put("scope", "weird")
            put("key", "../../../x")
            put("audioLanguage", "jpn")
            put("updatedAt", Long.MAX_VALUE)
        }
        val hostileKey = "weird/../../../x"
        adapter.markSynced(mapOf(hostileKey to hostile))

        adapter.applyRemote(mapOf(hostileKey to hostile))

        // Not adopted, whatever the stamp claims.
        assertNull(rows("weird", "../../../x"))
        // And the refused row's mirror entry is gone — it can't wedge there
        // re-reading as a locally-deleted key.
        assertNull(
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey(NAMESPACE, hostileKey))],
        )
    }

    private companion object {
        const val NAMESPACE = "itemprefs"
    }
}
