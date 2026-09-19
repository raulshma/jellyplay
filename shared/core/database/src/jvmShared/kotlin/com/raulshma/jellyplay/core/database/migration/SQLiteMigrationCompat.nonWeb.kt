package com.raulshma.jellyplay.core.database.migration

import androidx.sqlite.SQLiteConnection

/**
 * nonWeb actual (android/jvm): [androidx.sqlite.SQLiteConnection.prepare] and
 * [androidx.sqlite.SQLiteStatement.step] are blocking calls here; the suspend
 * signatures exist so the common expect also serves the web actuals (see
 * SQLiteMigrationCompat.kt for the full story).
 */
internal actual suspend fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

internal actual suspend fun SQLiteConnection.execSQL(sql: String, bindArgs: Array<out Any?>) {
    prepare(sql).use { stmt ->
        bindArgs.forEachIndexed { index, arg -> stmt.bindAny(index + 1, arg) }
        stmt.step()
    }
}

internal actual suspend fun SQLiteConnection.collectRowsThenUpdate(
    select: String,
    updateRow: suspend (key: String, payload: String?) -> Unit,
) {
    val rows = mutableListOf<Pair<String, String?>>()
    prepare(select).use { cursor ->
        while (cursor.step()) {
            rows.add(cursor.getText(0) to if (cursor.isNull(1)) null else cursor.getText(1))
        }
    }
    for ((key, payload) in rows) {
        updateRow(key, payload)
    }
}
