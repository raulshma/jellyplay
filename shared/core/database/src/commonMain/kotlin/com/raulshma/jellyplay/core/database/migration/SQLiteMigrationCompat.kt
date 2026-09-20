package com.raulshma.jellyplay.core.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Room-KMP migration compat: the historical migrations were written against
 * the Android-only `SupportSQLiteDatabase` API (execSQL overloads); the
 * multiplatform `Migration.migrate` hands out an [androidx.sqlite.SQLiteConnection]
 * instead. These shims keep the 141 `execSQL` call sites byte-identical
 * instead of rewriting migration SQL history.
 *
 * The shims are expect/actual because androidx.sqlite 2.7.x declares
 * [SQLiteConnection.prepare] and [SQLiteStatement.step] only in its platform
 * actuals — blocking on android/jvm — while the common expects carry neither.
 * Target compilations see the merged common+actual API, but the *metadata*
 * compilation compiles commonMain against the common-only dependency klibs
 * and rejects any `prepare`/`step` reference. Declaring the shims suspend
 * here (one signature legally wrapping both the blocking and the suspend
 * actuals, since every caller sits inside the suspend `Migration.migrate`)
 * keeps every call site in commonMain; the bodies live once per variant in
 * jvmShared and are textually identical.
 */
internal expect suspend fun SQLiteConnection.execSQL(sql: String)

internal expect suspend fun SQLiteConnection.execSQL(sql: String, bindArgs: Array<out Any?>)

/**
 * Collect-then-update scan shared by the row-rewriting migrations
 * ([Migration24To25], [Migration53To54]): fully drains [select] — a
 * two-column query shaped `SELECT <key>, <payload> FROM …` with a non-null
 * TEXT key in position 0 and a nullable TEXT payload in position 1 — into
 * memory, and only then runs [updateRow] for each collected row.
 *
 * The row's UPDATE must never be issued while the SELECT cursor is still
 * stepping: the UPDATE writes the very column the open cursor scans (v24→v25
 * rewrites `users.accessToken` / `servers.accessToken`; v53→v54 filters on
 * `downloads.container IS NULL`), and SQLite may revisit or skip rows whose
 * scanned columns change mid-scan — a skipped row would silently keep its
 * stale value (for 24→25, a plaintext token). Collecting the pending rows
 * first is the safe pattern; this shim exists so that rule is enforced and
 * documented in exactly one place.
 */
internal expect suspend fun SQLiteConnection.collectRowsThenUpdate(
    select: String,
    updateRow: suspend (key: String, payload: String?) -> Unit,
)

/** Binds one dynamically-typed argument from the `SupportSQLiteDatabase` bind list. */
internal fun SQLiteStatement.bindAny(index: Int, arg: Any?) {
    when (arg) {
        null -> bindNull(index)
        is String -> bindText(index, arg)
        is Long -> bindLong(index, arg)
        is Int -> bindLong(index, arg.toLong())
        is Short -> bindLong(index, arg.toLong())
        is Byte -> bindLong(index, arg.toLong())
        is Boolean -> bindLong(index, if (arg) 1L else 0L)
        is Double -> bindDouble(index, arg)
        is Float -> bindDouble(index, arg.toDouble())
        is ByteArray -> bindBlob(index, arg)
        else -> bindText(index, arg.toString())
    }
}
