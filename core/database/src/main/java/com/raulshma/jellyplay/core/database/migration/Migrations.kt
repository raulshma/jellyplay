package com.raulshma.jellyplay.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
    }
}

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
)
