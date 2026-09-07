package com.raulshma.jellyplay.core.data.util

/**
 * Chunk size for IN queries over uncapped id lists: SQLite allows at most 999
 * bound parameters (host variables) per statement — a hard ceiling on Android
 * < 12 — so a whole-library id list must be chunked safely under it or the
 * query throws SQLiteBindOrColumnIndexOutOfRangeException. One constant for
 * every chunked IN query in the module so the consumers can't drift.
 */
internal const val SQLITE_HOST_VARIABLE_CHUNK_SIZE = 900
