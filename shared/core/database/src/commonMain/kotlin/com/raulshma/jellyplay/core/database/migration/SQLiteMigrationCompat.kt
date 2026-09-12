package com.raulshma.jellyplay.core.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * Room-KMP migration compat: the historical migrations were written against
 * the Android-only `SupportSQLiteDatabase` API (execSQL overloads); the
 * multiplatform `Migration.migrate` hands out an [androidx.sqlite.SQLiteConnection]
 * instead. These shims keep 141 `execSQL` call sites byte-identical instead of
 * rewriting migration SQL history.
 *
 * The shims are `suspend` because androidx.sqlite 2.7.x's web (js/wasmJs)
 * actuals of [SQLiteConnection.prepare] / [SQLiteStatement.step] are suspend
 * functions (the nonWeb android/jvm/native actuals are blocking — a suspend
 * shim legally calls both, so one common declaration serves every target;
 * every caller sits inside `Migration.migrate`, which is suspend under
 * room3).
 */
internal suspend fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

internal suspend fun SQLiteConnection.execSQL(sql: String, bindArgs: Array<out Any?>) {
    prepare(sql).use { stmt ->
        bindArgs.forEachIndexed { index, arg -> stmt.bindAny(index + 1, arg) }
        stmt.step()
    }
}

private fun SQLiteStatement.bindAny(index: Int, arg: Any?) {
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
