package com.raulshma.jellyplay.core.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.raulshma.jellyplay.core.database.dao.OFFLINE_MEDIA_WITH_PLAYBACK_SQL
import com.raulshma.jellyplay.core.database.dao.OFFLINE_MEDIA_WITH_PLAYBACK_VIEW_NAME

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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
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
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE sync_baseline ADD COLUMN syncSubtitlesPending INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Persists the item's chapter list as a nullable JSON blob on offline_media
 * (contract: [com.raulshma.jellyplay.core.model.OfflineMediaItem.chapters]).
 * Existing rows stay null and simply render without chapters until
 * re-download. Mirrors the `peopleJson` blob pattern from 29→30.
 *
 * Chapters also join the metadata resync signature payload, which changes the
 * digest for every item — including those without chapters — so stored v50
 * signatures are hashes of the retired payload format and can never match a
 * freshly computed one. Nulling them here hands every baseline to the
 * comparator's empty-baseline rule ("never recorded" never flags), making the
 * format change a one-time silent re-seed on each row's next freshness check
 * instead of a fleet-wide false "update available".
 *
 * And because that next check is TTL-gated by `lastSyncedAt`, anything the
 * retired-format comparison left persisted would stay visible until it fires:
 * the migration therefore also clears the metadata axis's own change flag and
 * recomputes the composite `syncUpdateAvailable` badge from the surviving
 * axes, so a pre-upgrade false flag goes dark at upgrade time while genuine
 * other-axis badges survive.
 */
val MIGRATION_50_51 = object : Migration(50, 51) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("ALTER TABLE offline_media ADD COLUMN chaptersJson TEXT")
        db.execSQL("UPDATE sync_baseline SET syncedMetadataSignature = NULL")
        db.execSQL(
            "UPDATE sync_baseline SET syncMetadataChanged = 0, " +
                // The subtitle axis counts a pending retry bundle as changed
                // (comparator's subtitleAxisChanged rule), so its flag joins
                // the survivor set alongside the four signature axes.
                "syncUpdateAvailable = CASE WHEN syncImagesChanged = 1 " +
                "OR syncSubtitlesChanged = 1 OR syncSubtitlesPending = 1 " +
                "OR syncTrickplayChanged = 1 OR syncSegmentsChanged = 1 " +
                "THEN 1 ELSE 0 END"
        )
    }
}

// Two index changes, both schema-only (query results identical):
//  - Drop the dead `offline_media(name)` index. Its only consumer,
//    OfflineMediaDao.search, is a '%…%' contains-scan over the
//    offline_media_with_playback view with a CASE-led ORDER BY — no planner
//    path can use a BINARY-collation B-tree there, so the index was pure
//    write amplification on the app's highest-churn table.
//  - Add an ordered composite index on playback_outbox(itemId, deadLetter,
//    createdAt) for PlaybackOutboxDao.getForItemByType, which runs ~every 10 s
//    during playback and filters + sorts by createdAt using only the itemId index.
val MIGRATION_51_52 = object : Migration(51, 52) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL("DROP INDEX IF EXISTS index_offline_media_name")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playback_outbox_itemId_deadLetter_createdAt ON playback_outbox(itemId, deadLetter, createdAt)")
    }
}

// Ordered range index for OfflineMediaDao.getDownloadedEpisodes — the offline
// home's Continue Watching / Next Up source. That reactive query filters
// `mediaType = 'EPISODE'` over the offline_media_with_playback view and orders
// by `seriesId, seasonNumber, episodeNumber` (LIMIT 2000); no existing index
// served that WHERE + ORDER BY combination, so SQLite full-scanned offline_media
// and sorted up to 2000 joined rows on EVERY re-emission — and any write to
// offline_media or playback_state (metadata re-persist, 2 s progress ticks
// during transfers) re-ran the flow. The new index lets the planner walk the
// matching mediaType range already in output order (LEFT JOIN playback_state by
// primary key per row, then a row lookup for the selected offline_media
// columns — ordered, not covering) instead of sorting.
// Schema-additive only; no table data changes.
val MIGRATION_52_53 = object : Migration(52, 53) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "index_offline_media_mediaType_seriesId_seasonNumber_episodeNumber " +
                "ON offline_media(mediaType, seriesId, seasonNumber, episodeNumber)"
        )
    }
}

/**
 * One-time backfill of `downloads.container` for legacy rows. The column was
 * added as nullable by [MIGRATION_31_32] because pre-existing rows had no
 * value, and playback has carried a magic-byte sniffing fallback
 * (PlayerSessionManager.loadOffline over the container sniffer, now in
 * shared:core:data) for those rows ever since. This migration resolves that
 * fallback once, at upgrade time: for every row whose `container` is still
 * NULL, the injected [ContainerProbe] reads the row's `downloadPath` file
 * header and the recognized code is persisted — so the runtime sniffer can
 * eventually be retired.
 *
 * Rows whose file is missing, unreadable or unrecognized stay NULL (the
 * playback-time fallback keeps covering them); a probe failure never aborts
 * the upgrade. Schema-unchanged version bump: no DDL, the step exists so
 * Room treats the backfilled database as current.
 */
class Migration53To54(
    private val containerProbe: ContainerProbe,
) : Migration(53, 54) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.collectRowsThenUpdate(
            "SELECT id, downloadPath FROM downloads WHERE container IS NULL",
        ) { id, downloadPath ->
            // downloadPath is NOT NULL in every downloads schema since the
            // table's creation; the elvis only satisfies the helper's
            // nullable payload type.
            val container = try {
                containerProbe.probe(downloadPath ?: return@collectRowsThenUpdate)
            } catch (_: Exception) {
                // Unreadable/failed probe — the row stays NULL and the
                // migration proceeds.
                null
            } ?: return@collectRowsThenUpdate
            db.execSQL(
                "UPDATE downloads SET container = ? WHERE id = ?",
                arrayOf(container, id),
            )
        }
    }
}

// Reader marks (docs/adr/0003-local-first-reader-marks.md): the local-first
// bookmark + highlight/underline tables. Both keyed by itemId (indexed — the
// reader sheet observes per item) with the same auto-increment Long id the
// other append-only tables (seen_media, search_history) use. Fresh-table
// migration: nothing to backfill, and `positionTicks`/`cfi` semantics are
// owned entirely by the feature that ships alongside this schema step.
val MIGRATION_54_55 = object : Migration(54, 55) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId TEXT NOT NULL,
                positionTicks INTEGER NOT NULL,
                cfi TEXT,
                chapterLabel TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_book_bookmarks_itemId ON book_bookmarks(itemId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_annotations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId TEXT NOT NULL,
                cfi TEXT NOT NULL,
                style TEXT NOT NULL,
                color TEXT NOT NULL,
                anchorText TEXT NOT NULL,
                note TEXT,
                chapterLabel TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_book_annotations_itemId ON book_annotations(itemId)")
    }
}

val MIGRATION_55_56 = object : Migration(55, 56) {
    override suspend fun migrate(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS book_toc_cache (
                itemId TEXT NOT NULL PRIMARY KEY,
                format TEXT NOT NULL,
                pageCount INTEGER NOT NULL,
                entriesJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

