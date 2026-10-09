package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.SearchHistoryEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adapter tests for the `search/` namespace (ADR 0011's search-history
 * roaming): real in-memory Room for the `search_history` store plus a
 * temp-file DataStore mirror — the sha1(query) keying, the mirror-based
 * dirty/deleted detection, the 50-cap survival, and both delete directions.
 */
class JellyPlaySearchHistorySyncAdapterTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlaySearchHistorySyncAdapter

    private var userId: String? = "user-1"

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        mirrorStore = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-search-adapter-test").apply { mkdirs() }
            File(dir, "search-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
        adapter = JellyPlaySearchHistorySyncAdapter(
            historyDao = database.searchHistoryDao(),
            mirrorStore = mirrorStore,
            userIdProvider = { userId },
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private suspend fun save(query: String, searchedAt: Long = 1_000L) {
        database.searchHistoryDao().insertAndEvict(
            SearchHistoryEntity(query = query, userId = USER, searchedAt = searchedAt),
        )
    }

    private suspend fun rows(): List<SearchHistoryEntity> =
        database.searchHistoryDao().getRecent(USER, 50).first()

    private fun valueOf(query: String, searchedAt: Long) = buildJsonObject {
        put("query", query)
        put("searchedAt", searchedAt)
    }

    // ------------------------------------------------------------------
    // snapshot: sha1(query) keys, {query, searchedAt} values
    // ------------------------------------------------------------------

    @Test
    fun snapshot_keysOnQueryHash_valueCarriesQueryAndStamp() = runTest {
        save("star wars")

        val snapshot = adapter.snapshot()

        val key = snapshot.keys.single()
        assertEquals(40, key.length) // sha1 hex
        assertEquals(valueOf("star wars", 1_000L), snapshot.getValue(key))
    }

    @Test
    fun snapshot_deterministicKey_sameQueryAlwaysSameKey() = runTest {
        save("star wars")

        val first = adapter.snapshot().keys.single()
        // Re-save the same query (the REPLACE path) — the key is stable.
        save("star wars", searchedAt = 2_000L)

        assertEquals(first, adapter.snapshot().keys.single())
    }

    @Test
    fun snapshot_signedOut_readsEmpty() = runTest {
        save("star wars")
        userId = null

        assertTrue(adapter.snapshot().isEmpty())
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean() = runTest {
        save("dora")
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).isNotEmpty()) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    // ------------------------------------------------------------------
    // deletes roam: outbound tombstones + inbound tombstones
    // ------------------------------------------------------------------

    @Test
    fun localDelete_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runTest {
        save("crid")
        val snapshot = adapter.snapshot()
        adapter.markSynced(snapshot)
        val key = snapshot.keys.single()

        // The local delete: the row goes, the mirror entry stays — the
        // pending outbound tombstone the engine pushes.
        database.searchHistoryDao().deleteById(rows().single().id)

        assertEquals(setOf(key), adapter.deletedKeys())

        // The engine's confirmation (or an inbound tombstone): the mirror
        // entry goes, the key reads neither dirty nor deleted.
        adapter.deleteRemote(setOf(key))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(rows().isEmpty())
        assertNullMirror(key)
    }

    @Test
    fun deleteRemote_removesTheRowsHashingToTheKey_only() = runTest {
        save("alpha")
        save("beta")
        adapter.markSynced(adapter.snapshot())

        val alphaKey = adapter.snapshot().entries.first { it.value == valueOf("alpha", 1_000L) }.key
        adapter.deleteRemote(setOf(alphaKey))

        assertEquals(listOf("beta"), rows().map { it.query })
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun clearAll_reportsEveryKeyDeleted() = runTest {
        save("one")
        save("two")
        adapter.markSynced(adapter.snapshot())

        database.searchHistoryDao().clearAll(USER)

        assertTrue(adapter.snapshot().isEmpty())
        assertEquals(2, adapter.deletedKeys().size)
    }

    @Test
    fun deleteRemote_unparseableKey_stillClearsMirror() = runTest {
        adapter.markSynced(mapOf("weird" to JsonPrimitive("{}")))

        adapter.deleteRemote(setOf("weird"))

        assertNullMirror("weird")
    }

    // ------------------------------------------------------------------
    // applyRemote: upsert-at-query, the 50 cap survives
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsTheQuery_keepingTheCap() = runTest {
        adapter.applyRemote(
            mapOf(
                sha1Of("remote query") to valueOf("remote query", 5_000L),
            ),
        )

        val stored = rows().single()
        assertEquals("remote query", stored.query)
        assertEquals(5_000L, stored.searchedAt)
        assertEquals(USER, stored.userId)
    }

    @Test
    fun applyRemote_mismatchedOrMalformed_skipped() = runTest {
        save("local")

        adapter.applyRemote(
            mapOf(
                // Not a JSON object.
                sha1Of("garbage") to JsonPrimitive("garbage"),
                // Missing the query field.
                sha1Of("sparse") to buildJsonObject { put("searchedAt", 1L) },
                // The value's query hashes to a different key than it arrived under.
                sha1Of("liar-key") to valueOf("liar-value", 1L),
            ),
        )

        assertEquals(listOf("local"), rows().map { it.query })
    }

    @Test
    fun applyRemote_adoptionNeverGrowsPastTheClientCap() = runTest {
        for (i in 0 until 50) save("q$i")

        adapter.applyRemote(
            mapOf(sha1Of("q-new") to valueOf("q-new", 9_000L)),
        )

        assertEquals(50, rows().size)
        assertTrue(rows().none { it.query == "q0" }) // the oldest evicted, as a local write would
        assertTrue(rows().any { it.query == "q-new" })
    }

    @Test
    fun applyRemote_jsonNullValue_isIgnored() = runTest {
        adapter.applyRemote(mapOf(sha1Of("gone") to JsonNull))

        assertTrue(rows().isEmpty())
    }

    // ------------------------------------------------------------------
    // the wire value round-trips through plain JSON
    // ------------------------------------------------------------------

    @Test
    fun snapshotValue_roundTripsThroughJson() = runTest {
        save("loop")

        val decoded = Json.parseToJsonElement(adapter.snapshot().values.single().toString())

        assertEquals(valueOf("loop", 1_000L), decoded)
    }

    private suspend fun assertNullMirror(key: String) {
        mirrorStore.edit { } // settle the write side
        assertEquals(
            null,
            mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey("search", key))],
        )
    }

    private fun sha1Of(text: String): String =
        java.security.MessageDigest.getInstance("SHA-1")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private companion object {
        const val USER = "user-1"
    }
}
