package com.raulshma.jellyplay.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.BookAnnotationEntity
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
 * Adapter tests for the `reader/` namespace (ADR 0011's annotation backup,
 * ADR 0003's local-first semantics): real in-memory Room for the
 * `book_annotations` store plus a temp-file DataStore mirror — the per-book
 * `ann/{itemId}` keying with the FULL annotation list as the value (export
 * DTO's shape, install-local ids omitted), the mirror-based dirty/deleted
 * detection, the replace-at-book adoption, and both delete directions.
 */
class JellyPlayReaderSyncAdapterTest {

    private lateinit var database: JellyPlayDatabase
    private lateinit var mirrorStore: DataStore<Preferences>
    private lateinit var adapter: JellyPlayReaderSyncAdapter

    @BeforeTest
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<JellyPlayDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        mirrorStore = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-reader-adapter-test").apply { mkdirs() }
            File(dir, "reader-${System.nanoTime()}.preferences_pb").absolutePath.toPath()
        }
        adapter = JellyPlayReaderSyncAdapter(
            annotationDao = database.bookAnnotationDao(),
            mirrorStore = mirrorStore,
        )
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    private suspend fun seed(
        itemId: String = "book-1",
        cfi: String = "epubcfi(/6/4!/4/10,/1:0,/1:100)",
        note: String? = null,
        updatedAt: Long = 1_000L,
    ): BookAnnotationEntity {
        val entity = BookAnnotationEntity(
            itemId = itemId,
            cfi = cfi,
            style = "HIGHLIGHT",
            color = "YELLOW",
            anchorText = "the quoted line",
            note = note,
            chapterLabel = "Chapter 1",
            createdAt = 900L,
            updatedAt = updatedAt,
        )
        database.bookAnnotationDao().upsert(entity)
        return entity
    }

    private suspend fun rows(itemId: String = "book-1"): List<BookAnnotationEntity> =
        database.bookAnnotationDao().getAll().filter { it.itemId == itemId }

    private fun valueOf(vararg annotations: BookAnnotationEntity): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            annotations.forEach { row ->
                add(
                    buildJsonObject {
                        put("cfi", row.cfi)
                        put("style", row.style)
                        put("color", row.color)
                        put("anchorText", row.anchorText)
                        row.note?.let { put("note", it) }
                        put("chapterLabel", row.chapterLabel)
                        put("createdAt", row.createdAt)
                        put("updatedAt", row.updatedAt)
                    },
                )
            }
        }

    // ------------------------------------------------------------------
    // snapshot: ann/{itemId} keys, the full annotation list as the value
    // ------------------------------------------------------------------

    @Test
    fun snapshot_keysPerBook_valueCarriesTheFullListWithoutLocalIds() = runTest {
        val first = seed(note = "a note")
        val second = seed(cfi = "epubcfi(/6/4!/4/20)", updatedAt = 2_000L)

        val snapshot = adapter.snapshot()

        val key = snapshot.keys.single()
        assertEquals("ann/book-1", key)
        // Install-local Room ids stay off the wire; the payload rows are
        // identified by their CFI.
        assertEquals(valueOf(first, second), snapshot.getValue(key))
    }

    // ------------------------------------------------------------------
    // dirty / synced: the mirror cycle
    // ------------------------------------------------------------------

    @Test
    fun dirtyUntilMarkSynced_thenClean() = runTest {
        seed()
        val snapshot = adapter.snapshot()

        assertTrue(adapter.dirtyValues(snapshot).isNotEmpty()) // no mirror yet

        adapter.markSynced(snapshot)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    // ------------------------------------------------------------------
    // deletes roam: clearing a book's annotations is an outbound tombstone,
    // an inbound tombstone clears the book's rows
    // ------------------------------------------------------------------

    @Test
    fun localClear_reportsDeletedKey_deleteRemoteClearsIt_noResurrection() = runTest {
        seed()
        val snapshot = adapter.snapshot()
        adapter.markSynced(snapshot)

        database.bookAnnotationDao().deleteByItemId("book-1")

        assertEquals(setOf("ann/book-1"), adapter.deletedKeys())

        adapter.deleteRemote(setOf("ann/book-1"))
        assertTrue(adapter.deletedKeys().isEmpty())
        assertTrue(rows().isEmpty())
        assertNullMirror("ann/book-1")
    }

    @Test
    fun deleteRemote_clearsOnlyTheNamedBook() = runTest {
        seed(itemId = "book-1")
        seed(itemId = "book-2")
        adapter.markSynced(adapter.snapshot())

        adapter.deleteRemote(setOf("ann/book-1"))

        assertTrue(rows("book-1").isEmpty())
        assertEquals(1, rows("book-2").size)
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun deleteRemote_unparseableKey_stillClearsMirror() = runTest {
        adapter.markSynced(mapOf("weird" to JsonPrimitive("[]")))

        adapter.deleteRemote(setOf("weird"))

        assertNullMirror("weird")
    }

    // ------------------------------------------------------------------
    // applyRemote: REPLACE-AT-BOOK with fresh install-local ids
    // ------------------------------------------------------------------

    @Test
    fun applyRemote_replacesTheBooksWholeSet() = runTest {
        seed(cfi = "epubcfi(/old)")
        seed(cfi = "epubcfi(/old-2)")

        val remote = buildJsonArray {
            add(
                buildJsonObject {
                    put("cfi", "epubcfi(/remote)")
                    put("style", "UNDERLINE")
                    put("color", "BLUE")
                    put("anchorText", "remote line")
                    put("note", "from the other device")
                    put("chapterLabel", "Chapter 2")
                    put("createdAt", 5_000L)
                    put("updatedAt", 6_000L)
                },
            )
        }

        adapter.applyRemote(mapOf("ann/book-1" to remote))

        val stored = rows()
        assertEquals(1, stored.size)
        assertEquals("epubcfi(/remote)", stored.single().cfi)
        assertEquals("from the other device", stored.single().note)
        // The engine marks the adopted keys synced right after applyRemote;
        // the mirror cycle then reads clean.
        adapter.markSynced(adapter.snapshot())
        assertTrue(adapter.dirtyValues(adapter.snapshot()).isEmpty())
    }

    @Test
    fun applyRemote_malformedOrNonArray_skipped() = runTest {
        seed()

        adapter.applyRemote(
            mapOf(
                // Not a JSON array.
                "ann/book-1" to JsonPrimitive("garbage"),
                // An entry missing the CFI anchor.
                "ann/book-2" to buildJsonArray {
                    add(buildJsonObject { put("style", "HIGHLIGHT") })
                },
            ),
        )

        // The local set survives both garbage rows untouched.
        assertEquals(1, rows("book-1").size)
        assertTrue(rows("book-2").isEmpty())
    }

    @Test
    fun applyRemote_jsonNullValue_defensivelyDeletesTheBook() = runTest {
        seed()

        adapter.applyRemote(mapOf("ann/book-1" to JsonNull))

        assertTrue(rows().isEmpty())
    }

    // ------------------------------------------------------------------
    // the wire value round-trips through plain JSON
    // ------------------------------------------------------------------

    @Test
    fun snapshotValue_roundTripsThroughJson() = runTest {
        seed(note = "a note")

        val decoded = Json.parseToJsonElement(adapter.snapshot().values.single().toString())

        assertEquals(valueOf(rows().single()), decoded)
    }

    private suspend fun assertNullMirror(key: String) {
        mirrorStore.edit { } // settle the write side
        assertEquals(
            null,
            mirrorStore.data.first()[stringPreferencesKey("jpsync.mirror.reader.$key")],
        )
    }
}
