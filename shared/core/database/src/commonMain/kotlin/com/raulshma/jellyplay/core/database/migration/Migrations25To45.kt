package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

val MIGRATION_25_26 = object : Migration(25, 26) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE item_playback_preferences ADD COLUMN dialogueBoostStrength TEXT")
    }
}

// Offline playback progress: position ticks, played percentage, isPlayed, and
// last-played date. Lets downloads render watched state and
// resume positions while offline, seeded from server UserData at download time.
val MIGRATION_28_29 = object : Migration(28, 29) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_lyrics_cache_itemId ON lyrics_cache(itemId)")
    }
}

// Capture the original container format ("mkv", "mp4", "ts", ...) reported by
// the Jellyfin MediaSource at download time. Used at playback to attach the
// correct MIME type to ExoPlayer, so the right extractor is selected even when
// the on-disk file uses a hardcoded `.mp4` extension. Nullable: pre-existing
// rows degrade to extension-based inference (sniffer fallback at playback).
val MIGRATION_31_32 = object : Migration(31, 32) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_offline_media_name ON offline_media(name)")
    }
}

val MIGRATION_33_34 = object : Migration(33, 34) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE users ADD COLUMN canDeleteContent INTEGER NOT NULL DEFAULT 0")
    }
}

// Outbox for playback-progress events (START / PROGRESS / STOP) that could not
// reach the Jellyfin server because the device was offline. The
// PlaybackSyncWorker drains this table on reconnect / periodically. `recordedAt`
// holds the local capture time used for latest-wins reconciliation against the
// server's lastPlayedDate, and `createdAt` orders the drain queue.
val MIGRATION_35_36 = object : Migration(35, 36) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN providerIdsJson TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN externalUrlsJson TEXT")
    }
}

// Offline favorite flag, seeded from server UserData at download time and
// updated locally as the user toggles favorite offline. Mirrors the `isPlayed`
// column shape from migration 28→29 (NOT NULL DEFAULT 0 so existing rows
// resolve to not-favorite until the user acts or the item is re-downloaded).
val MIGRATION_44_45 = object : Migration(44, 45) {
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedSubtitleSignature TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedTrickplaySignature TEXT")
        db.execSQL("ALTER TABLE offline_media ADD COLUMN syncedSegmentsSignature TEXT")
    }
}
