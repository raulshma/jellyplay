package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookBookmarkEntity
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adapter tests for the `books/` namespace (the reader bookmarks' migration
 * onto the general sync protocol, ADR 0011): real in-memory Room for the
 * `book_bookmarks` store plus a temp-file DataStore mirror — the payload
 * fidelity (CFI rides the wire value the dedicated bookmark route loses), the
 * mirror-based dirty/deleted detection, and both delete directions.
 */
class JellyPlayBookmarksSyncAdapterTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayBookmarksSyncAdapter

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        mirrorStore = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-books-adapter-test").apply { mkdirs() }
            File(dir, "books-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
        adapter = JellyPlayBookmarksSyncAdapter(
            bookmarkDao = database.bookBookmarkDao(),
            mirrorStore = mirrorStore,
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private suspend fun seedBookmark(
        itemId: String = "book-1",
        positionTicks: Long = 12_345L,
        cfi: String? = "epubcfi(/6/4!/4/10)",
        chapterLabel: String = "Chapter 1",
        createdAt: Long = 1_000L,
    ): BookBookmarkEntity {
        val entity = BookBookmarkEntity(
            itemId = itemId,
            positionTicks = positionTicks,
            cfi = cfi,
            chapterLabel = chapterLabel,
            createdAt = createdAt,
        )
        database.bookBookmarkDao().upsert(entity)
        return entity
    }

    private suspend fun rows(itemId: String = "book-1"): List<BookBookmarkEntity> =
        database.bookBookmarkDao().observeByItemId(itemId).first()

    private fun payloadOf(entity: BookBookmarkEntity) = buildJsonObject {
        put("itemId", entity.itemId)
        put("positionTicks", entity.positionTicks)
        put("cfi", entity.cfi?.let { JsonPrimitive(it) } ?: JsonNull)
        put("chapterLabel", entity.chapterLabel)
        put("createdAt", entity.createdAt)
    }

    // ------------------------------------------------------------------
    // snapshot: the full payload, CFI included
    // ------------------------------------------------------------------

    @Test
    fun snapshot_encodesFullPayload_includingCfi() = runTest {
        val entity = seedBookmark(cfi = "epubcfi(/6/4!/4/10)")

        val snapshot = adapter.snapshot()

        assertEquals(mapOf("book-1/12345" to payloadOf(entity)), snapshot)
        assertEquals("epubcfi(/6/4!/4/10)", snapshot["book-1/12345"]!!["cfi"]?.toString()?.trim('"'))
    }

    @Test
    fun snapshot_pagedBookmark_encodesNullCfi() = runTest {
        seedBookmark(positionTicks = 9_000L, cfi = null)

        val value = adapter.snapshot().getValue("book-1/9000")

        assertEquals(JsonNull, value["cfi"])
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean() = runTest {
        val entity = seedBookmark()
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).containsKey("book-1/12345")) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())

        // A local label edit re-dirties the key.
        database.bookBookmarkDao().deleteAtPosition(entity.itemId, entity.positionTicks)
        seedBookmark(chapterLabel = "Chapter 1 (renamed)")
        assertEquals(
            mapOf("book-1/12345" to adapter.snapshot().getValue("book-1/12345")),
            adapter.dirtyValues(adapter.snapshot()),
        )
    }

    // ------------------------------------------------------------------
    // applyRemote: the full payload lands, the newest twin is replaced
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_adoptsFullPayload_replacingNewestTwinAtJoinKey() = runTest {
        // Two local rows at one position are "the same bookmark" — the pulled
        // row replaces the newest twin instead of accumulating duplicates.
        seedBookmark(positionTicks = 500L, chapterLabel = "older", createdAt = 100L)
        seedBookmark(positionTicks = 500L, chapterLabel = "newer", createdAt = 200L)

        adapter.applyRemote(
            mapOf(
                "book-1/500" to buildJsonObject {
                    put("itemId", "book-1")
                    put("positionTicks", 500L)
                    put("cfi", "epubcfi(/6/14!/4/2)")
                    put("chapterLabel", "remote")
                    put("createdAt", 300L)
                },
            ),
        )

        val remaining = rows()
        assertEquals(1, remaining.size)
        assertEquals("remote", remaining[0].chapterLabel)
        assertEquals("epubcfi(/6/14!/4/2)", remaining[0].cfi) // the CFI the legacy route loses
        assertEquals(300L, remaining[0].createdAt)
    }

    @Test
    fun applyRemote_malformedOrMismatched_skipped() = runTest {
        seedBookmark(positionTicks = 100L, chapterLabel = "local")

        adapter.applyRemote(
            mapOf(
                // Not a JSON object.
                "book-1/200" to JsonPrimitive("garbage"),
                // Missing fields.
                "book-1/300" to buildJsonObject { put("chapterLabel", "sparse") },
                // Payload disagrees with its own key.
                "book-1/400" to buildJsonObject {
                    put("itemId", "book-2")
                    put("positionTicks", 999L)
                    put("cfi", JsonNull)
                    put("chapterLabel", "liar")
                    put("createdAt", 1L)
                },
                // Unparseable key.
                "not-a-key" to buildJsonObject {
                    put("itemId", "book-1")
                    put("positionTicks", 1L)
                    put("chapterLabel", "orphan")
                    put("createdAt", 1L)
                },
            ),
        )

        assertEquals(1, rows().size)
        assertEquals("local", rows()[0].chapterLabel)
        assertTrue(rows("book-2").isEmpty())
    }

    // ------------------------------------------------------------------
    // deletes roam: outbound tombstones + inbound tombstones
    // ------------------------------------------------------------------

    @Test
    fun localDelete_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runTest {
        val entity = seedBookmark()
        val snapshot = adapter.snapshot()
        adapter.markSynced(snapshot)

        // The reader's local delete: the row goes, the mirror entry stays —
        // the pending outbound tombstone the engine pushes.
        database.bookBookmarkDao().deleteAtPosition(entity.itemId, entity.positionTicks)

        assertEquals(setOf("book-1/12345"), adapter.deletedKeys())

        // The engine's confirmation after the applied push (or an inbound
        // tombstone from another device): rows stay gone AND the mirror entry
        // goes — the key re-reads neither dirty nor deleted, so nothing
        // resurrects it on the next cycle.
        adapter.deleteRemote(setOf("book-1/12345"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
        assertTrue(rows().isEmpty())
        assertNull(mirrorStore.data.first()[stringPreferencesKey(JpsyncReservation.mirrorKey("books", "book-1/12345"))])
    }

    @Test
    fun deleteRemote_removesEveryRowAtJoinKey() = runTest {
        val entity = seedBookmark(positionTicks = 700L)
        seedBookmark(positionTicks = 700L, chapterLabel = "dup", createdAt = entity.createdAt + 1)
        seedBookmark(positionTicks = 800L, chapterLabel = "survivor")
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("book-1/700"))

        assertEquals(1, rows().size)
        assertEquals(800L, rows()[0].positionTicks)
        // The tombstoned key's mirror entry is gone; the survivor's stays.
        assertTrue(adapter.deletedKeys().isEmpty())
        assertEquals(1, adapter.snapshot().size)
    }

    @Test
    fun deleteRemote_unparseableKey_stillClearsMirror() = runTest {
        adapter.markSynced(mapOf("weird" to JsonPrimitive("{}")))

        adapter.deleteRemote(setOf("weird"))

        assertNull(
            mirrorStore.data.first()[
                stringPreferencesKey(JpsyncReservation.mirrorKey("books", "weird")),
            ],
        )
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun applyRemote_jsonNullValue_deletesRowsAtJoinKey() = runTest {
        seedBookmark(positionTicks = 100L)

        adapter.applyRemote(mapOf("book-1/100" to JsonNull))

        assertTrue(rows().isEmpty())
    }

    // ------------------------------------------------------------------
    // the wire value is a plain JSON object (opaque to the server)
    // ------------------------------------------------------------------

    @Test
    fun snapshotValue_roundTripsThroughJson() = runTest {
        val entity = seedBookmark()

        val decoded = Json.parseToJsonElement(adapter.snapshot().getValue("book-1/12345").toString())

        assertEquals(payloadOf(entity), decoded)
    }
}
