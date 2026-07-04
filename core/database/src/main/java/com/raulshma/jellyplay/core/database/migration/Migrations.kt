package com.raulshma.jellyplay.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS downloads (
                id TEXT PRIMARY KEY NOT NULL,
                mediaItemId TEXT NOT NULL,
                name TEXT NOT NULL,
                mediaType TEXT NOT NULL,
                downloadPath TEXT NOT NULL,
                downloadUrl TEXT NOT NULL,
                totalSizeBytes INTEGER NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                status TEXT NOT NULL,
                mediaSourceId TEXT,
                imageUrl TEXT,
                imageBlurHash TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_mediaItemId ON downloads(mediaItemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status ON downloads(status)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                userId TEXT PRIMARY KEY NOT NULL,
                serverId TEXT NOT NULL,
                name TEXT NOT NULL,
                accessToken TEXT NOT NULL,
                primaryImageTag TEXT,
                maxParentalAgeRating INTEGER,
                enabledFolderIds TEXT,
                lastConnected INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId ON users(serverId)")
    }
}

// No-op: version bump to keep Room schema in sync after entity annotation
// changes that did not require a SQL schema modification.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
    }
}

// MIGRATION_5_6 through MIGRATION_8_9: no-op version bumps added when
// entities were touched but no schema change was required. The bumps
// guarantee Room treats the database as up-to-date without rewriting
// the migration history.

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lyrics_cache (
                itemId TEXT PRIMARY KEY NOT NULL,
                provider TEXT NOT NULL,
                artistName TEXT,
                trackName TEXT,
                syncedLyrics TEXT,
                plainLyrics TEXT,
                duration REAL,
                lrcLibId INTEGER,
                fetchedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_cache_fetchedAt ON lyrics_cache(fetchedAt)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId_lastConnected ON users(serverId, lastConnected)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_createdAt ON downloads(createdAt)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN seriesId TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonId TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seriesName TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonName TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN episodeNumber INTEGER")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonNumber INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_media (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                mediaType TEXT NOT NULL,
                overview TEXT,
                year INTEGER,
                communityRating REAL,
                officialRating TEXT,
                runTimeTicks INTEGER,
                parentId TEXT,
                seriesId TEXT,
                seasonId TEXT,
                seriesName TEXT,
                seasonName TEXT,
                episodeNumber INTEGER,
                seasonNumber INTEGER,
                indexNumber INTEGER,
                childCount INTEGER,
                posterPath TEXT,
                backdropPath TEXT,
                blurHashPrimary TEXT,
                blurHashBackdrop TEXT,
                premiereDate TEXT,
                genres TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_parentId ON offline_media(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seriesId ON offline_media(seriesId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seasonId ON offline_media(seasonId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_mediaType ON offline_media(mediaType)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seriesId ON downloads(seriesId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seasonId ON downloads(seasonId)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS media_audit_log (
                id TEXT PRIMARY KEY NOT NULL,
                timestamp INTEGER NOT NULL,
                adminUserId TEXT NOT NULL,
                adminUserName TEXT NOT NULL,
                actionType TEXT NOT NULL,
                configJson TEXT NOT NULL,
                itemCount INTEGER NOT NULL,
                itemDetailsJson TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_audit_log_timestamp ON media_audit_log(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_audit_log_actionType ON media_audit_log(actionType)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scan_state (
                scanId TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                configJson TEXT NOT NULL,
                status TEXT NOT NULL,
                progress INTEGER NOT NULL DEFAULT 0,
                total INTEGER NOT NULL DEFAULT 0,
                itemsFound INTEGER NOT NULL DEFAULT 0,
                resultJson TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_state_status ON scan_state(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scan_state_createdAt ON scan_state(createdAt)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS lyrics_cache_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId TEXT NOT NULL,
                provider TEXT NOT NULL,
                artistName TEXT,
                trackName TEXT,
                syncedLyrics TEXT,
                plainLyrics TEXT,
                duration REAL,
                lrcLibId INTEGER,
                fetchedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_cache_new_fetchedAt ON lyrics_cache_new(fetchedAt)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lyrics_cache_new_itemId_provider ON lyrics_cache_new(itemId, provider)")
        db.execSQL("INSERT OR IGNORE INTO lyrics_cache_new (itemId, provider, artistName, trackName, syncedLyrics, plainLyrics, duration, lrcLibId, fetchedAt) SELECT itemId, provider, artistName, trackName, syncedLyrics, plainLyrics, duration, lrcLibId, fetchedAt FROM lyrics_cache")
        db.execSQL("DROP TABLE lyrics_cache")
        db.execSQL("ALTER TABLE lyrics_cache_new RENAME TO lyrics_cache")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smart_playlists (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                criteriaJson TEXT NOT NULL,
                maxItems INTEGER NOT NULL DEFAULT 50,
                sortBy TEXT NOT NULL DEFAULT 'RANDOM',
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_playlists_createdAt ON smart_playlists(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_playlists_name ON smart_playlists(name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mood_playlists (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                description TEXT NOT NULL,
                genreKeywordsJson TEXT NOT NULL,
                excludedGenresJson TEXT,
                minRating REAL,
                sortBy TEXT NOT NULL DEFAULT 'RANDOM',
                maxItems INTEGER NOT NULL DEFAULT 50,
                themeColorHex TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_playlists_createdAt ON mood_playlists(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_playlists_name ON mood_playlists(name)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mood_playlist_preferences (
                playlistId TEXT PRIMARY KEY NOT NULL,
                isEnabled INTEGER NOT NULL DEFAULT 1,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audio_queue (
                id TEXT PRIMARY KEY NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                imageUrl TEXT,
                mediaSourceId TEXT,
                durationMs INTEGER NOT NULL DEFAULT 0,
                normalizationGain REAL,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_queue_position ON audio_queue(position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audio_queue_createdAt ON audio_queue(createdAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audio_queue_state (
                id INTEGER PRIMARY KEY NOT NULL,
                currentIndex INTEGER NOT NULL DEFAULT -1,
                currentPositionMs INTEGER NOT NULL DEFAULT 0,
                isPlaying INTEGER NOT NULL DEFAULT 0,
                repeatMode INTEGER NOT NULL DEFAULT 0,
                shuffleEnabled INTEGER NOT NULL DEFAULT 0,
                playbackSpeed REAL NOT NULL DEFAULT 1.0,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_servers_address ON servers(address)")
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_servers_address ON servers(address)")
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS search_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                query TEXT NOT NULL,
                userId TEXT NOT NULL,
                searchedAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_userId ON search_history(userId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_search_history_query_userId ON search_history(query, userId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_searchedAt ON search_history(searchedAt)")
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS seen_media (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId TEXT NOT NULL,
                libraryId TEXT NOT NULL,
                mediaType TEXT NOT NULL,
                seenAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_seen_media_itemId ON seen_media(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_seen_media_seenAt ON seen_media(seenAt)")
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_mediaItemId_status ON downloads(mediaItemId, status)")
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seriesId_mediaType ON offline_media(seriesId, mediaType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seasonId_mediaType ON offline_media(seasonId, mediaType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seriesId_status ON downloads(seriesId, status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seasonId_status ON downloads(seasonId, status)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE servers ADD COLUMN alternateAddresses TEXT")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN errorMessage TEXT")
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Migration v24 → v25: encrypts plaintext Jellyfin
 * access tokens stored in the `users.accessToken` and `servers.accessToken` columns using
 * the Keystore-backed [TokenCipher]. Existing encrypted rows are left untouched (cipher is
 * idempotent), and rows with empty/null tokens are skipped.
 *
 * After this migration runs, the DB columns contain ciphertext that's only readable via
 * [TokenCipher.decrypt]. The encryption key lives in the Android Keystore and never leaves
 * the device, so an attacker extracting the DB file via `adb backup` or root access cannot
 * read the tokens.
 *
 * If the Keystore is transiently unavailable (rare — usually right after a fresh boot before
 * the user has unlocked the device), the migration re-throws to surface the failure; Room
 * will retry on next launch.
 */
class Migration24To25(
    private val tokenCipher: TokenCipher,
) : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        encryptUserTokens(db)
        encryptServerTokens(db)
    }

    private fun encryptUserTokens(db: SupportSQLiteDatabase) {
        db.query("SELECT userId, accessToken FROM users").use { cursor ->
            while (cursor.moveToNext()) {
                val userId = cursor.getString(0)
                val rawToken = if (cursor.isNull(1)) null else cursor.getString(1)
                val encrypted = try {
                    tokenCipher.encrypt(rawToken)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Migration 24→25: failed to encrypt token for user $userId. " +
                            "Aborting migration so Room can retry on next launch.",
                        e,
                    )
                }
                if (encrypted != rawToken) {
                    db.execSQL(
                        "UPDATE users SET accessToken = ? WHERE userId = ?",
                        arrayOf(encrypted ?: "", userId),
                    )
                }
            }
        }
    }

    private fun encryptServerTokens(db: SupportSQLiteDatabase) {
        db.query("SELECT id, accessToken FROM servers").use { cursor ->
            while (cursor.moveToNext()) {
                val serverId = cursor.getString(0)
                val rawToken = if (cursor.isNull(1)) null else cursor.getString(1)
                val encrypted = try {
                    tokenCipher.encrypt(rawToken)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Migration 24→25: failed to encrypt token for server $serverId.",
                        e,
                    )
                }
                if (encrypted != rawToken) {
                    db.execSQL(
                        "UPDATE servers SET accessToken = ? WHERE id = ?",
                        arrayOf(encrypted, serverId),
                    )
                }
            }
        }
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_mediaType_createdAt ON offline_media(mediaType, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status_priority_createdAt ON downloads(status, priority, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_search_history_userId_searchedAt ON search_history(userId, searchedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_media_audit_log_actionType_timestamp ON media_audit_log(actionType, timestamp)")
    }
}

// Per-series / per-item playback-language preferences.
// The table mirrors ItemPlaybackPreferenceEntity exactly; the (scope, key)
// pair is unique so OnConflictStrategy.REPLACE acts as an upsert.
val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS item_playback_preferences (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scope TEXT NOT NULL,
                key TEXT NOT NULL,
                audioLanguage TEXT,
                subtitleLanguage TEXT,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_playback_preferences_scope_key ON item_playback_preferences(scope, key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_playback_preferences_updatedAt ON item_playback_preferences(updatedAt)")
    }
}

// Per-item / per-series dialogue-boost strength.
// Nullable column: NULL means "no per-item rule" (resolve to the effective default).
val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN dialogueBoostStrength TEXT")
    }
}

// Offline playback progress: position ticks, played percentage, isPlayed, and
// last-played date. Lets downloads render watched state and
// resume positions while offline, seeded from server UserData at download time.
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN playbackPositionTicks INTEGER")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN playedPercentage REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN lastPlayedDate TEXT")
    }
}

// Rich metadata for offline detail screens: original title, critic rating,
// studios (comma-joined), tagline, and a JSON blob of cast/people. Lets the
// redesigned offline detail screens show the same information as the online
// detail screen. All columns are nullable so pre-existing rows degrade
// gracefully until the item is re-downloaded.
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN originalTitle TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN criticRating REAL")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN studios TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN tagline TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN peopleJson TEXT")
    }
}

// Explicit single-column index on lyrics_cache(itemId). The composite
// (itemId, provider) index already serves `WHERE itemId = :itemId` via a
// left-prefix match, but adding a dedicated index makes the intent unambiguous
// and documents that the per-item lookup path is indexed. The composite unique
// index is retained.
val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_cache_itemId ON lyrics_cache(itemId)")
    }
}

val ALL_MIGRATIONS = listOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
    MIGRATION_19_20,
    MIGRATION_20_21,
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
)
