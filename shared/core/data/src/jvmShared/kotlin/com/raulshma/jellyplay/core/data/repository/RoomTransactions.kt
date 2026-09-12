package com.raulshma.jellyplay.core.data.repository

import androidx.room3.RoomDatabase
import androidx.room3.Transactor
import androidx.room3.useWriterConnection

/**
 * KMP replacement for the Android-only Room 2 `withTransaction`
 * extension (C4 part 2 move note).
 *
 * Room 2.8 declares that extension only in room-runtime's `androidMain`
 * source set, so code shared with the desktop JVM target cannot import it.
 * The multiplatform-blessed equivalent is a writer connection plus the
 * connection-scoped [Transactor.withTransaction]; Android's legacy
 * `beginTransaction()` path it replaces runs `BEGIN IMMEDIATE` under the
 * hood (`beginTransactionNonExclusive`), so [Transactor.SQLiteTransactionType.IMMEDIATE]
 * preserves the transaction mode. Like the Android extension, it serializes
 * write transactions per database (Room allows one write transaction at a
 * time) and rolls back when [block] throws.
 */
internal suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection {
        it.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
    }
