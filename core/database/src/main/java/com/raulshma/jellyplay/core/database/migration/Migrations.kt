package com.raulshma.jellyplay.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.dao.OFFLINE_MEDIA_WITH_PLAYBACK_SQL
import com.raulshma.jellyplay.core.database.dao.OFFLINE_MEDIA_WITH_PLAYBACK_VIEW_NAME

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
        // No-op: the servers(address) unique index was already created by
        // MIGRATION_15_16. This migration object is retained so Room's
        // 16→17 step still resolves. (Previously it re-ran the same idempotent
        // CREATE UNIQUE INDEX IF NOT EXISTS — harmless copy-paste residue.)
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

// Capture the original container format ("mkv", "mp4", "ts", ...) reported by
// the Jellyfin MediaSource at download time. Used at playback to attach the
// correct MIME type to ExoPlayer, so the right extractor is selected even when
// the on-disk file uses a hardcoded `.mp4` extension. Nullable: pre-existing
// rows degrade to extension-based inference (sniffer fallback at playback).
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN container TEXT")
    }
}

// Add a non-unique index on `offline_media.name`. The OfflineMediaDao.search
// query orders by `name COLLATE NOCASE` and a `CASE WHEN name LIKE 'q%'` prefix
// branch; previously a full table scan ran for every keystroke on the widest
// table (33 columns). The leading-`%` substring LIKE branch cannot be served
// by a B-tree index, but the prefix/order-by branches now benefit. Behavior
// is unchanged; this is purely a query-planner improvement.
val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_name ON offline_media(name)")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_servers_userId ON servers(userId)")
    }
}

// Persist the Jellyfin `enableContentDeletion` user policy flag so the
// Stale Media / Watched Media admin screens can gate the Delete button on a
// value that survives an app restart. Previously the flag lived only in the
// in-memory UserInfo and was dropped by persistSession/restoreSession because
// the users table had no column for it — so every restart reset it to false
// and the admin screens wrongly told admins they lacked permission.
val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN canDeleteContent INTEGER NOT NULL DEFAULT 0")
    }
}

// Outbox for playback-progress events (START / PROGRESS / STOP) that could not
// reach the Jellyfin server because the device was offline. The
// PlaybackSyncWorker drains this table on reconnect / periodically. `recordedAt`
// holds the local capture time used for latest-wins reconciliation against the
// server's lastPlayedDate, and `createdAt` orders the drain queue.
val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_outbox (
                id TEXT NOT NULL PRIMARY KEY,
                itemId TEXT NOT NULL,
                eventType TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                positionTicks INTEGER NOT NULL DEFAULT 0,
                isPaused INTEGER NOT NULL DEFAULT 0,
                playMethod TEXT NOT NULL DEFAULT 'DIRECT_PLAY',
                mediaSourceId TEXT,
                recordedAt INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_outbox_itemId ON playback_outbox(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_outbox_createdAt ON playback_outbox(createdAt)")
    }
}

// Dead-letter flag for the playback outbox. A row whose retry budget is
// exhausted is now flagged (deadLetter = 1) instead of hard-deleted, so the
// telemetry is retained for audit / a future "retry sync" action while still
// being skipped by the drain and excluded from the pending count. Hard-deleting
// discarded rows the server may already have received (the failure could be a
// network blip after a 200) with no record. NOT NULL DEFAULT 0 matches the
// entity's @ColumnInfo(defaultValue = "0").
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playback_outbox ADD COLUMN deadLetter INTEGER NOT NULL DEFAULT 0")
    }
}

// Download pause reason + reconnect retry budget. `pausedReason` distinguishes a
// user long-press pause ("USER") from a network-drop interruption ("NETWORK") so
// the reconnect auto-resume resumes only the latter — a user-paused download
// stays paused until the user resumes it. `retryCount` bounds automatic retries:
// the reconnect listener enqueues fresh WorkManager jobs (KEEP policy) that
// bypass WorkManager's own run-attempt cap, so a persistently failing download
// (storage full, 404, auth) would otherwise re-attempt on every reconnect. After
// MAX_AUTO_RETRY failures the row is left FAILED for a manual retry (dead-letter).
// NOT NULL DEFAULT 0 on retryCount matches the entity's @ColumnInfo(defaultValue).
val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN pausedReason TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
    }
}

// Per-series subtitle role preference: lets a series pin "English Forced" or
// "English SDH" alongside the language so the restore matcher (TrackSelectionHelper)
// carries the right same-language track episode to episode. Both columns nullable:
// NULL means "don't care" (preserves today's language-only behaviour for existing
// rows), so this migration is non-destructive.
val MIGRATION_38_39 = object : Migration(38, 39) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN subtitleForced INTEGER")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN subtitleHearingImpaired INTEGER")
    }
}

// Cross-episode track-scoring memory (G5). Persists the last-selected audio and
// subtitle track per series: its display label (codec folded in), language, and
// positional index within its language group. Lets a specific "English · 5.1"
// pick survive an app restart and carry to the next episode, instead of being
// remembered only in-process. All six columns nullable — NULL means "no track
// remembered", preserving today's language-only behaviour for existing rows, so
// this migration is non-destructive.
val MIGRATION_39_40 = object : Migration(39, 40) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedAudioLabel TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedAudioLanguage TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedAudioIndex INTEGER")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedSubtitleLabel TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedSubtitleLanguage TEXT")
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN rememberedSubtitleIndex INTEGER")
    }
}

// Persistent stale-while-revalidate cache for home-screen sections. The home
// screen rendered nothing until its full section set (8–20 network requests)
// resolved on every cold open past the 60s in-memory TTL; this table holds the
// last successful payload so Home can paint instantly while a network refresh
// runs in the background. Keyed by (serverId, userId, cacheKey) so a user
// switch / logout never serves another user's payload.
val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS home_section_cache (
                serverId TEXT NOT NULL,
                userId TEXT NOT NULL,
                cacheKey TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL,
                PRIMARY KEY(serverId, userId, cacheKey)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_home_section_cache_serverId_userId ON home_section_cache(serverId, userId)"
        )
    }
}

// Per-series / per-item "subtitles off" intent. Lets a user disable subtitles
// for a whole series so every episode loads with subs off (instead of the
// resolver auto-picking the global-language match). Nullable: NULL means
// "inherit" (resolve normally), so existing rows are unaffected. Mutually
// exclusive with subtitleLanguage — the repository keeps them consistent, but
// the column itself has no DB-level constraint.
val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN subtitleDisabled INTEGER")
    }
}

// Offline download resync: persist a freshness baseline (image tags, metadata
// signature, media source id/size) + the last check timestamp and result flags
// so a freshness check can diff a fresh fetch against this baseline, and the UI
// can render an "update available" badge from the DB with zero network. All
// columns are nullable or default to 0 so existing rows are unaffected until
// their first check (or next download, which seeds the baseline).
val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedPosterTag TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedBackdropTag TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedMetadataSignature TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedMediaSourceId TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedMediaSizeBytes INTEGER")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN lastSyncedAt INTEGER")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncUpdateAvailable INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncMediaChanged INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncChecking INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncError INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Persists provider ids (tmdb/imdb/…) and external URLs on offline_media so the
 * offline subtitle search (Wyzie/OpenSubtitles) can resolve a TMDB/IMDb id
 * without a server round-trip. Both columns are nullable JSON blobs: existing
 * rows stay null and the subtitle search degrades to a title query until the
 * item is re-downloaded. Mirrors the `peopleJson` blob pattern from 29→30.
 */
val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN providerIdsJson TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN externalUrlsJson TEXT")
    }
}

// Offline favorite flag, seeded from server UserData at download time and
// updated locally as the user toggles favorite offline. Mirrors the `isPlayed`
// column shape from migration 28→29 (NOT NULL DEFAULT 0 so existing rows
// resolve to not-favorite until the user acts or the item is re-downloaded).
val MIGRATION_44_45 = object : Migration(44, 45) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

// Sidecar-artifact freshness signatures for the download resync feature.
// Subtitles + trickplay signatures are derived from MediaDetail and seeded at
// download time; the segments signature is seeded on the first segments
// resync. All three are nullable so pre-migration rows resolve to "never
// recorded" — the comparator treats an empty/null signature as a first-contact
// axis that never flags a spurious change.
val MIGRATION_45_46 = object : Migration(45, 46) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedSubtitleSignature TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedTrickplaySignature TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedSegmentsSignature TEXT")
    }
}

// Split the overloaded `offline_media` row along its jobs into three tables,
// each with one invariant.
//
//   offline_media      — identity + browsable metadata mirror (trimmed)
//   playback_state     — playback progress + watched/favorite (new)
//   sync_baseline      — freshness baseline signatures + per-axis flags (new)
//
// The freshness module (OfflineSyncManager) finally gets a persistence home of
// its own: the baseline + result flags move off the metadata row, so a metadata
// re-persist can no longer clobber them and the lossy 5-axis-→-1-flag projection
// is replaced by persisted per-axis change flags.
//
// SQLite (minSdk 28 framework) predates `ALTER TABLE … DROP COLUMN`, so the
// `offline_media` trim is done as the canonical create-copy-drop-rename dance.
// Column order in the recreated table follows the trimmed entity declaration
// order; Room's TableInfo equality is order-insensitive, but matching it keeps
// the schema diff readable. Every existing row's playback + sync column values
// are carried into the two new tables before the old columns are dropped, so no
// data is lost. Per-axis change flags (syncMetadataChanged / syncImagesChanged /
// syncSubtitlesChanged / syncTrickplayChanged / syncSegmentsChanged) have no
// derivation from the old coarse `syncUpdateAvailable` flag and default to 0;
// they are populated accurately on the next freshness check.
val MIGRATION_46_47 = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // ── playback_state: create + backfill from the live columns ──────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playback_state (
                id TEXT PRIMARY KEY NOT NULL,
                playbackPositionTicks INTEGER,
                playedPercentage REAL NOT NULL DEFAULT 0.0,
                isPlayed INTEGER NOT NULL DEFAULT 0,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                lastPlayedDate TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO playback_state (
                id, playbackPositionTicks, playedPercentage, isPlayed, isFavorite, lastPlayedDate
            )
            SELECT id, playbackPositionTicks, playedPercentage, isPlayed, isFavorite, lastPlayedDate
            FROM offline_media
            """.trimIndent()
        )

        // ── sync_baseline: create + backfill from the live columns ───────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_baseline (
                id TEXT PRIMARY KEY NOT NULL,
                syncedPosterTag TEXT,
                syncedBackdropTag TEXT,
                syncedMetadataSignature TEXT,
                syncedSubtitleSignature TEXT,
                syncedTrickplaySignature TEXT,
                syncedSegmentsSignature TEXT,
                syncedMediaSourceId TEXT,
                syncedMediaSizeBytes INTEGER,
                lastSyncedAt INTEGER,
                syncUpdateAvailable INTEGER NOT NULL DEFAULT 0,
                syncMediaChanged INTEGER NOT NULL DEFAULT 0,
                syncChecking INTEGER NOT NULL DEFAULT 0,
                syncError INTEGER NOT NULL DEFAULT 0,
                syncMetadataChanged INTEGER NOT NULL DEFAULT 0,
                syncImagesChanged INTEGER NOT NULL DEFAULT 0,
                syncSubtitlesChanged INTEGER NOT NULL DEFAULT 0,
                syncTrickplayChanged INTEGER NOT NULL DEFAULT 0,
                syncSegmentsChanged INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO sync_baseline (
                id, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature,
                syncedSubtitleSignature, syncedTrickplaySignature, syncedSegmentsSignature,
                syncedMediaSourceId, syncedMediaSizeBytes, lastSyncedAt,
                syncUpdateAvailable, syncMediaChanged, syncChecking, syncError
            )
            SELECT id, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature,
                   syncedSubtitleSignature, syncedTrickplaySignature, syncedSegmentsSignature,
                   syncedMediaSourceId, syncedMediaSizeBytes, lastSyncedAt,
                   syncUpdateAvailable, syncMediaChanged, syncChecking, syncError
            FROM offline_media
            """.trimIndent()
        )

        // ── offline_media: trim via create-copy-drop-rename ───────────────────
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_media_new (
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
                createdAt INTEGER NOT NULL DEFAULT 0,
                originalTitle TEXT,
                criticRating REAL,
                studios TEXT,
                tagline TEXT,
                peopleJson TEXT,
                providerIdsJson TEXT,
                externalUrlsJson TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO offline_media_new (
                id, name, mediaType, overview, year, communityRating, officialRating,
                runTimeTicks, parentId, seriesId, seasonId, seriesName, seasonName,
                episodeNumber, seasonNumber, indexNumber, childCount, posterPath,
                backdropPath, blurHashPrimary, blurHashBackdrop, premiereDate, genres,
                createdAt, originalTitle, criticRating, studios, tagline, peopleJson,
                providerIdsJson, externalUrlsJson
            )
            SELECT id, name, mediaType, overview, year, communityRating, officialRating,
                   runTimeTicks, parentId, seriesId, seasonId, seriesName, seasonName,
                   episodeNumber, seasonNumber, indexNumber, childCount, posterPath,
                   backdropPath, blurHashPrimary, blurHashBackdrop, premiereDate, genres,
                   createdAt, originalTitle, criticRating, studios, tagline, peopleJson,
                   providerIdsJson, externalUrlsJson
            FROM offline_media
            """.trimIndent()
        )
        db.execSQL("DROP TABLE offline_media")
        db.execSQL("ALTER TABLE offline_media_new RENAME TO offline_media")
        // Recreate the eight offline_media indices dropped with the old table.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_parentId ON offline_media(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seriesId ON offline_media(seriesId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seasonId ON offline_media(seasonId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_mediaType ON offline_media(mediaType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_name ON offline_media(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seriesId_mediaType ON offline_media(seriesId, mediaType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_seasonId_mediaType ON offline_media(seasonId, mediaType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_mediaType_createdAt ON offline_media(mediaType, createdAt)")

        // Read-only join view backing the browse / detail / search queries —
        // the single shape every OfflineMediaDao read consumes, so the
        // `offline_media ⟕ playback_state` join lives in one place. The SQL is
        // shared verbatim with the @DatabaseView annotation (same const) so
        // Room's post-migration schema check passes.
        db.execSQL(
            "CREATE VIEW `$OFFLINE_MEDIA_WITH_PLAYBACK_VIEW_NAME` AS $OFFLINE_MEDIA_WITH_PLAYBACK_SQL"
        )
    }
}

// Index the two `sync_baseline` flag columns consumed by the "items with
// updates" sheet query and the badge-count flow — both filter on
// `syncUpdateAvailable = 1 OR syncMediaChanged = 1` and re-run on every
// baseline write (a batch check writes one row per item), which full-scanned
// the table. Schema-additive only; no table data changes.
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_baseline_syncUpdateAvailable ON sync_baseline(syncUpdateAvailable)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_baseline_syncMediaChanged ON sync_baseline(syncMediaChanged)")
    }
}

// Two composite indices for read paths whose filter+order columns no existing
// index covers:
//  - `downloads(status, mediaType, createdAt)` serves the completed-audio
//    browse page query (filters `status = 'COMPLETED' AND mediaType IN (...)`,
//    orders by `createdAt`), which previously had to pick between the
//    single-column `status`/`createdAt` indices or full-scan + sort.
//  - `playback_outbox(deadLetter, createdAt)` serves the outbox drain/count
//    queries, all of which filter `WHERE deadLetter = 0` and order by
//    `createdAt`; the table was indexed only by `itemId`/`createdAt`, so
//    `countFlow()` (collected continuously for the sync indicator) sorted the
//    surviving rows on every re-emission.
// Schema-additive only; no table data changes.
val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_status_mediaType_createdAt ON downloads(status, mediaType, createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_outbox_deadLetter_createdAt ON playback_outbox(deadLetter, createdAt)")
    }
}

// The subtitle sidecar bundle's "failed and never fetched" retry state moves
// from a sentinel value inside `syncedSubtitleSignature` to its own flag
// column (`syncSubtitlesPending`), so the signature column stays a pure server
// snapshot. Schema-additive only: the flag feature shipped alongside this
// migration, so no existing row carries the retired sentinel and 0 (not
// pending) is the correct backfill for every pre-existing baseline.
val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sync_baseline ADD COLUMN syncSubtitlesPending INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * The complete, correctly-ordered v1→v50 migration chain, with the
 * token-encrypting [Migration24To25] (which needs a [TokenCipher]) inserted at
 * its true position between v23→v24 and v25→v26. Room matches migrations by
 * start/end version regardless of list order, but keeping the chain in strict
 * ascending order here makes the source a reliable map of the upgrade path and
 * lets [MigrationTest] assert contiguity.
 */
fun allMigrations(tokenCipher: TokenCipher): List<Migration> =
    listOf(
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
        Migration24To25(tokenCipher),
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
        MIGRATION_36_37,
        MIGRATION_37_38,
        MIGRATION_38_39,
        MIGRATION_39_40,
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44,
        MIGRATION_44_45,
        MIGRATION_45_46,
        MIGRATION_46_47,
        MIGRATION_47_48,
        MIGRATION_48_49,
        MIGRATION_49_50,
    )
