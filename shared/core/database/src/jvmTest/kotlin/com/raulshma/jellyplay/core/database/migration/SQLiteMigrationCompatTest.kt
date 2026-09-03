package com.raulshma.jellyplay.core.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the Room-KMP migration compat shims in [SQLiteMigrationCompat] —
 * the internal [execSQL] overloads that keep the 141 historical `execSQL`
 * call sites byte-identical across the SupportSQLiteDatabase → SQLiteConnection
 * port. The no-arg overload must prepare + step a statement; the bind-args
 * overload must map every supported Kotlin type onto the correct SQLite bind
 * (null/text/integer/real/blob), including the Boolean → 0/1, Int/Short/Byte →
 * Long widening, Float → Double widening, and the toString fallback for
 * unrecognized argument types.
 *
 * The migrations that USE these shims are covered end-to-end by
 * [MigrationTest]; this file pins the shim layer itself.
 */
class SQLiteMigrationCompatTest {

    private lateinit var dbDir: File
    private lateinit var dbFile: File
    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        // Fresh directory per test: the bundled driver journals in WAL mode and
        // stale -wal/-shm sidecars resurrect previous rows (see MigrationTest).
        dbDir = createTempDirectory("jellyplay-db-compat-test").toFile()
        dbFile = File(dbDir, "compat-test.db")
        connection = BundledSQLiteDriver().open(dbFile.absolutePath)
    }

    @AfterTest
    fun teardown() {
        connection.close()
        dbDir.deleteRecursively()
    }

    @Test
    fun `execSQL without bind args prepares and steps the statement`() {
        connection.execSQL("CREATE TABLE t (id TEXT PRIMARY KEY NOT NULL, flag INTEGER NOT NULL)")
        connection.execSQL("INSERT INTO t (id, flag) VALUES ('a', 1)")

        connection.prepare("SELECT id, flag FROM t").use { c ->
            assertTrue(c.step())
            assertEquals("a", c.getText(0))
            assertEquals(1L, c.getLong(1))
        }
    }

    @Test
    fun `execSQL with bind args maps every supported argument type`() {
        connection.execSQL(
            """
            CREATE TABLE t (
                id INTEGER PRIMARY KEY NOT NULL,
                c_null TEXT,
                c_text TEXT,
                c_long INTEGER,
                c_int INTEGER,
                c_short INTEGER,
                c_byte INTEGER,
                c_bool_true INTEGER,
                c_bool_false INTEGER,
                c_double REAL,
                c_float REAL,
                c_blob BLOB,
                c_fallback TEXT
            )
            """.trimIndent()
        )
        val fallbackArg = object : Any() {
            override fun toString(): String = "fb-text"
        }
        connection.execSQL(
            """
            INSERT INTO t (id, c_null, c_text, c_long, c_int, c_short, c_byte,
                           c_bool_true, c_bool_false, c_double, c_float, c_blob, c_fallback)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                1,                      // Int → widened to Long
                null,                   // null → bindNull
                "text",                 // String → bindText
                9_000_000_000L,         // Long → bindLong
                42,                     // Int → bindLong(42)
                7.toShort(),            // Short → widened
                3.toByte(),             // Byte → widened
                true,                   // Boolean → 1
                false,                  // Boolean → 0
                2.5,                    // Double → bindDouble
                1.5f,                   // Float → widened
                byteArrayOf(1, 2, 3),   // ByteArray → bindBlob
                fallbackArg,            // unrecognized → bindText(toString)
            ),
        )

        connection.prepare("SELECT * FROM t WHERE id = 1").use { c ->
            assertTrue(c.step())
            assertTrue(c.isNull(1), "null bind must store SQL NULL")
            assertEquals("text", c.getText(2))
            assertEquals(9_000_000_000L, c.getLong(3))
            assertEquals(42L, c.getLong(4))
            assertEquals(7L, c.getLong(5))
            assertEquals(3L, c.getLong(6))
            assertEquals(1L, c.getLong(7), "Boolean true must bind as 1")
            assertEquals(0L, c.getLong(8), "Boolean false must bind as 0")
            assertEquals(2.5, c.getDouble(9), 0.0)
            assertEquals(1.5, c.getDouble(10), 0.0)
            assertTrue(byteArrayOf(1, 2, 3).contentEquals(c.getBlob(11)), "ByteArray must round-trip as a blob")
            assertEquals("fb-text", c.getText(12), "unrecognized types fall back to bindText(toString)")
        }
    }

    @Test
    fun `execSQL with bind args updates an existing row`() {
        connection.execSQL("CREATE TABLE t (id TEXT PRIMARY KEY NOT NULL, value INTEGER NOT NULL)")
        connection.execSQL("INSERT INTO t (id, value) VALUES ('row', 1)")

        connection.execSQL("UPDATE t SET value = ? WHERE id = ?", arrayOf<Any>(99L, "row"))

        connection.prepare("SELECT value FROM t WHERE id = 'row'").use { c ->
            assertTrue(c.step())
            assertEquals(99L, c.getLong(0))
        }
    }

    @Test
    fun `execSQL with a null-only bind stores SQL NULL`() {
        connection.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY NOT NULL, v TEXT)")
        connection.execSQL("INSERT INTO t (id, v) VALUES (?, ?)", arrayOf<Any?>(1, null))

        connection.prepare("SELECT v FROM t").use { c ->
            assertTrue(c.step())
            assertTrue(c.isNull(0))
        }
    }
}
