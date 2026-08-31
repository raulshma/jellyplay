package com.raulshma.jellyplay.core.database.migration

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JELLY_PLAY_DATABASE_VERSION
import com.raulshma.jellyplay.core.database.crypto.JvmTokenCipher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.database.entity.SyncBaselineEntity
import com.raulshma.jellyplay.core.database.migration.allMigrations
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory

class MigrationTest {

    private lateinit var dbDir: File
    private lateinit var dbFile: File
    private var database: JellyPlayDatabase? = null

    @BeforeTest
    fun setup() {
        // A fresh directory per test, never a shared fixed path: the bundled
        // driver journals in WAL mode, and deleting only the main .db file
        // leaves the -wal/-shm sidecars behind, so the next test's fresh main
        // file replays the previous test's WAL — resurrecting latest-schema
        // tables into a fixture that expects an old shape ("duplicate column
        // name" from createDownloadsTableV5, or stale seeded rows).
        dbDir = createTempDirectory("jellyplay-db-migration-test").toFile()
        dbFile = File(dbDir, "migration-test.db")
    }

    @AfterTest
    fun teardown() {
        database?.close()
        dbDir.deleteRecursively()
    }

    @Test
    fun migrateAllFromV1() = runTest {
        createDatabase(1) { db ->
            db.execSQL(
                """
                CREATE TABLE servers (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    address TEXT NOT NULL,
                    userId TEXT,
                    accessToken TEXT,
                    lastConnected INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO servers (id, name, address, lastConnected) VALUES (?, ?, ?, ?)",
                arrayOf<Any>("server-1", "Test Server", "https://test.example.com", 1700000000000L)
            )
        }

        val db = openWithMigrations()
        val server = db.serverDao().getServerById("server-1")
        assertNotNull(server)
        assertEquals(server!!.name, "Test Server")
        assertEquals(server.address, "https://test.example.com")

        val downloads = db.downloadDao().getAllDownloads().first()
        assertEquals(0, downloads.count())

        db.close()
    }

    @Test
    fun migrateAllFromV2() = runTest {
        createDatabase(2) { db ->
            createServersTable(db)
            createDownloadsTableBase(db)
            db.execSQL(
                "INSERT INTO servers (id, name, address, lastConnected) VALUES (?, ?, ?, ?)",
                arrayOf<Any>("server-1", "Test Server", "https://test.example.com", 1700000000000L)
            )
        }

        val db = openWithMigrations()
        val server = db.serverDao().getServerById("server-1")
        assertNotNull(server)
        assertEquals(server!!.name, "Test Server")
        db.close()
    }

    @Test
    fun migrateAllFromV4() = runTest {
        createDatabase(4) { db ->
            createServersTable(db)
            createDownloadsTableBase(db)
            createUsersTable(db)
            db.execSQL(
                "INSERT INTO servers (id, name, address, lastConnected) VALUES (?, ?, ?, ?)",
                arrayOf<Any>("server-1", "Server 1", "https://s1.com", 1700000000000L)
            )
        }

        val db = openWithMigrations()
        assertNotNull(db.serverDao().getServerById("server-1"))
        db.close()
    }

    @Test
    fun migrateAllFromV5() = runTest {
        createDatabase(5) { db ->
            createServersTable(db)
            createDownloadsTableV5(db)
            createUsersTable(db)
            db.execSQL(
                "INSERT INTO downloads (id, mediaItemId, name, mediaType, downloadPath, downloadUrl, totalSizeBytes, downloadedBytes, status, speedBytesPerSec) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("dl-1", "item-1", "Test Download", "MOVIE", "/path", "https://url", 1000L, 500L, "DOWNLOADING", 5000L)
            )
        }

        val db = openWithMigrations()
        val download = db.downloadDao().getDownloadById("dl-1")
        assertNotNull(download)
        assertEquals(download!!.name, "Test Download")
        assertEquals(5000L, download.speedBytesPerSec)
        db.close()
    }

    @Test
    fun migrateAllFromV8() = runTest {
        createDatabase(8) { db ->
            createServersTable(db)
            createDownloadsTableV5(db)
            createUsersTable(db)
            createLyricsCacheTable(db)
            db.execSQL(
                "INSERT INTO lyrics_cache (itemId, provider, fetchedAt) VALUES (?, ?, ?)",
                arrayOf<Any>("item-1", "provider-1", 1700000000000L)
            )
        }

        val db = openWithMigrations()
        val cached = db.lyricsCacheDao().getByItemId("item-1")
        assertNotNull(cached)
        assertEquals(cached!!.provider, "provider-1")
        db.close()
    }

    @Test
    fun migrateAllFromV10() = runTest {
        createDatabase(10) { db ->
            createServersTable(db)
            createDownloadsTableV5(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_createdAt ON downloads(createdAt)")
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            db.execSQL(
                "INSERT INTO users (userId, serverId, name, accessToken, isAdmin, lastConnected) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("user-1", "server-1", "TestUser", "token123", 1, 1700000000000L)
            )
        }

        val db = openWithMigrations()
        val user = db.userDao().getUserById("user-1")
        assertNotNull(user)
        assertEquals(user!!.name, "TestUser")
        assertEquals(true, user.isAdmin)
        db.close()
    }

    /**
     * Regression for the v34→v35 migration: the `canDeleteContent` column must
     * be added to the users table (defaulting to 0/false) so the Stale Media /
     * Watched Media admin screens stop telling admins they lack delete
     * permission after an app restart. Reuses the v12 starting schema; the chain
     * itself carries it forward to v35, including this column via
     * [MIGRATION_34_35].
     */
    @Test
    fun migrateAllFromV12_addsCanDeleteContentColumn() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            db.execSQL(
                "INSERT INTO users (userId, serverId, name, accessToken, isAdmin, lastConnected) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("user-1", "server-1", "AdminUser", "token123", 1, 1700000000000L)
            )
        }

        val db = openWithMigrations()
        // Pre-existing row picks up the default (false) after migration.
        val migrated = db.userDao().getUserById("user-1")
        assertNotNull(migrated)
        assertEquals(migrated!!.name, "AdminUser")
        assertEquals(false, migrated.canDeleteContent)
        // A fresh write with canDeleteContent = true must round-trip, proving
        // the column exists and is read by the DAO/Room-generated mapping.
        db.userDao().insertUser(migrated.copy(canDeleteContent = true))
        val reloaded = db.userDao().getUserById("user-1")
        assertNotNull(reloaded)
        assertEquals(true, reloaded!!.canDeleteContent)
        db.close()
    }

    /**
     * Verifies the v35→v36 migration creates the `playback_outbox` table with
     * the (itemId, createdAt) indices, queryable through the DAO. This table is
     * the offline playback-progress outbox drained by PlaybackSyncWorker.
     */
    @Test
    fun migrateAllFromV12_addsPlaybackOutbox() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
        }

        val db = openWithMigrations()
        // DAO round-trip proves the table + columns + indices exist and Room's
        // generated mapping reads/writes the entity correctly.
        db.playbackOutboxDao().upsert(
            com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity(
                id = "entry-1",
                itemId = "item-1",
                eventType = "PROGRESS",
                sessionId = "session-1",
                positionTicks = 5_000_000L,
                isPaused = false,
                playMethod = "DIRECT_PLAY",
                mediaSourceId = null,
                recordedAt = 1L,
                createdAt = 1L,
            )
        )
        assertEquals(1, db.playbackOutboxDao().count())
        val pending = db.playbackOutboxDao().getAll()
        assertEquals(1, pending.size)
        assertEquals(pending[0].itemId, "item-1")
        assertEquals(pending[0].eventType, "PROGRESS")
        db.playbackOutboxDao().deleteForItem("item-1")
        assertEquals(0, db.playbackOutboxDao().count())
        db.close()
    }

    /**
     * Verifies the v36→v37 migration adds the `deadLetter` column to
     * `playback_outbox` with a NOT NULL DEFAULT 0, that pre-existing rows
     * default to live (deadLetter = 0), and that flagging a row via
     * [PlaybackOutboxDao.markDeadLetter] excludes it from the drain ([getAll])
     * and the pending [count] — so the sync indicator clears even when an
     * undeliverable entry is retained for audit instead of hard-deleted.
     */
    @Test
    fun migrateAllFromV12_addsPlaybackOutboxDeadLetterColumn() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
        }

        val db = openWithMigrations()
        db.playbackOutboxDao().upsert(
            com.raulshma.jellyplay.core.database.entity.PlaybackOutboxEntity(
                id = "entry-1",
                itemId = "item-1",
                eventType = "PROGRESS",
                sessionId = "session-1",
                positionTicks = 5_000_000L,
                isPaused = false,
                playMethod = "DIRECT_PLAY",
                mediaSourceId = null,
                recordedAt = 1L,
                createdAt = 1L,
            )
        )
        // The migrated column defaults a pre-existing row to live.
        assertEquals(1, db.playbackOutboxDao().getAll().size)
        assertEquals(1, db.playbackOutboxDao().count())
        // Flagging dead-letter retains the row but excludes it from drain + count.
        db.playbackOutboxDao().markDeadLetter("entry-1")
        assertEquals(0, db.playbackOutboxDao().getAll().size)
        assertEquals(0, db.playbackOutboxDao().count())
        db.close()
    }

    /**
     * Verifies the v37→v38 migration adds the `pausedReason` (TEXT, nullable)
     * and `retryCount` (INTEGER NOT NULL DEFAULT 0) columns to `downloads`.
     * Pre-existing rows pick up the defaults (null reason, 0 retries); a fresh
     * DAO write round-trips both columns. The reconnect auto-resume path
     * (DownloadRepositoryImpl.resumeInterruptedDownloads) reads `pausedReason`
     * to skip user-paused rows and `retryCount` to dead-letter exhausted
     * retries, so the columns must exist and map correctly post-migration.
     */
    @Test
    fun migrateAllFromV12_addsDownloadPauseAndRetryColumns() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            // A pre-v38 row: no pausedReason/retryCount columns exist yet.
            db.execSQL(
                "INSERT INTO downloads (id, mediaItemId, name, mediaType, downloadPath, downloadUrl, totalSizeBytes, downloadedBytes, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("dl-1", "item-1", "Test", "MOVIE", "/p", "https://u", 1000L, 500L, "PAUSED"),
            )
        }

        val db = openWithMigrations()
        // Pre-existing row picks up the defaults after migration.
        val migrated = db.downloadDao().getDownloadById("dl-1")
        assertNotNull(migrated)
        assertNull(migrated!!.pausedReason)
        assertEquals(0, migrated.retryCount)
        // A fresh write round-trips both new columns.
        db.downloadDao().insertDownload(
            migrated.copy(
                id = "dl-2",
                pausedReason = "NETWORK",
                retryCount = 2,
            )
        )
        val written = db.downloadDao().getDownloadById("dl-2")
        assertNotNull(written)
        assertEquals(written!!.pausedReason, "NETWORK")
        assertEquals(2, written.retryCount)
        db.close()
    }

    @Test
    fun migrateAllFromV12() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
        }

        val db = openWithMigrations()
        val auditCount = db.auditLogDao().getCount().first()
        assertEquals(0, auditCount)
        db.close()
    }

    /**
     * Verifies the full migration chain (which now ends at v27) creates the
     * `item_playback_preferences` table with the expected (scope, key) unique
     * index, queryable through the DAO. Reuses the v12 starting schema (the
     * chain itself creates every later table, including this one via
     * [MIGRATION_26_27]).
     */
    @Test
    fun migrateAllFromV12_includesItemPlaybackPreferences() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
        }

        val db = openWithMigrations()
        // DAO round-trip confirms the table + columns + unique index exist and
        // that OnConflictStrategy.REPLACE upserts on the (scope, key) pair.
        db.itemPlaybackPreferenceDao().upsert(
            com.raulshma.jellyplay.core.database.entity.ItemPlaybackPreferenceEntity(
                scope = "SERIES",
                key = "series-1",
                audioLanguage = "deu",
                subtitleLanguage = "eng",
                updatedAt = 1L,
            )
        )
        val saved = db.itemPlaybackPreferenceDao().getByKey("SERIES", "series-1")
        assertNotNull(saved)
        assertEquals(saved!!.audioLanguage, "deu")
        assertEquals(1, db.itemPlaybackPreferenceDao().countByScope("SERIES"))
        db.close()
    }

    /**
     * Verifies the v40→v41 migration creates the `home_section_cache` table
     * with the (serverId, userId, cacheKey) composite primary key + identity
     * index, and that a typed payload round-trips through the DAO + JSON
     * encode/decode helpers. This table backs the home-screen
     * stale-while-revalidate cache, so the home screen can render instantly on
     * cold open while a network refresh runs in the background.
     */
    @Test
    fun migrateAllFromV12_addsHomeSectionCache() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
        }

        val db = openWithMigrations()
        val payload = HomeSectionsResult(
            sections = listOf(
                HomeSection(
                    id = "cw",
                    title = "Continue Watching",
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = listOf(
                        MediaItem(
                            id = "item-1",
                            name = "Test Item",
                            mediaType = MediaType.EPISODE,
                        ),
                    ),
                ),
            ),
        )
        db.homeSectionCacheDao().upsert(
            HomeSectionCacheEntity(
                serverId = "srv-1",
                userId = "u1",
                cacheKey = "key-1",
                payloadJson = com.raulshma.jellyplay.core.database.Converters.encodeHomeSectionsResult(payload),
                fetchedAt = 1L,
            )
        )
        val read = db.homeSectionCacheDao().get("srv-1", "u1", "key-1")
        assertNotNull(read)
        assertEquals(1L, read!!.fetchedAt)
        val decoded = read.payload
        assertNotNull(decoded)
        assertEquals(1, decoded!!.sections.size)
        assertEquals(decoded.sections[0].title, "Continue Watching")
        assertEquals(decoded.sections[0].items[0].id, "item-1")
        // Identity-scoped clear must remove the row.
        db.homeSectionCacheDao().clearForIdentity("srv-1", "u1")
        assertNull(db.homeSectionCacheDao().get("srv-1", "u1", "key-1"))
        db.close()
    }


    /**
     * Verifies the v24→v25 migration encrypts plaintext access tokens stored in the
     * `users` and `servers` tables. After the migration, the DB columns hold ciphertext that
     * round-trips through [TokenCipher.decrypt].
     *
     * Invokes [Migration24To25.migrate] directly against a fresh SQLite database rather than
     * going through Room's full schema-validation path, so we don't have to recreate every
     * v24 index/column just to test the token-encryption logic.
     */
    @Test
    fun migrateV24_encryptsExistingPlaintextTokens() {
        val db = openRawDatabase(24) { db ->

                db.execSQL(
                    """
                    CREATE TABLE servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        userId TEXT,
                        accessToken TEXT,
                        lastConnected INTEGER NOT NULL,
                        alternateAddresses TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE users (
                        userId TEXT PRIMARY KEY NOT NULL,
                        serverId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        accessToken TEXT NOT NULL,
                        primaryImageTag TEXT,
                        maxParentalAgeRating INTEGER,
                        enabledFolderIds TEXT,
                        isAdmin INTEGER NOT NULL DEFAULT 0,
                        lastConnected INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO servers (id, name, address, userId, accessToken, lastConnected, alternateAddresses) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>("srv-1", "Server One", "https://s1.example", "u1", "plaintext-server-token", 0L, null)
                )
                db.execSQL(
                    "INSERT INTO users (userId, serverId, name, accessToken, isAdmin, lastConnected) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>("u1", "srv-1", "alice", "plaintext-user-token", 0, 0L)
                )
            
        }

        val tokenCipher = JvmTokenCipher.forTestingWithPersistentKey()
        Migration24To25(tokenCipher).migrate(db)

        db.prepare("SELECT accessToken FROM servers WHERE id = 'srv-1'").use { c ->
            assertTrue(c.step())
            val stored = c.getText(0)
            assertNotEqualsWithMessage("Server token must be encrypted", "plaintext-server-token", stored)
            assertEquals(tokenCipher.decrypt(stored), "plaintext-server-token")
        }
        db.prepare("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            assertTrue(c.step())
            val stored = c.getText(0)
            assertNotEqualsWithMessage("User token must be encrypted", "plaintext-user-token", stored)
            assertEquals(tokenCipher.decrypt(stored), "plaintext-user-token")
        }

        db.close()
    }

    /**
     * Regression: re-running the migration on already-encrypted rows must NOT change
     * them (cipher is idempotent) and must NOT corrupt the data.
     */
    @Test
    fun migrateV24_isIdempotentWhenRunTwice() {
        val db = openRawDatabase(24) { db ->

                db.execSQL(
                    """
                    CREATE TABLE servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        userId TEXT,
                        accessToken TEXT,
                        lastConnected INTEGER NOT NULL,
                        alternateAddresses TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE users (
                        userId TEXT PRIMARY KEY NOT NULL,
                        serverId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        accessToken TEXT NOT NULL,
                        primaryImageTag TEXT,
                        maxParentalAgeRating INTEGER,
                        enabledFolderIds TEXT,
                        isAdmin INTEGER NOT NULL DEFAULT 0,
                        lastConnected INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO users (userId, serverId, name, accessToken, isAdmin, lastConnected) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>("u1", "srv-1", "alice", "plaintext-user-token", 0, 0L)
                )
            
        }

        val tokenCipher = JvmTokenCipher.forTestingWithPersistentKey()
        val migration = Migration24To25(tokenCipher)
        migration.migrate(db)
        // Capture the post-first-migration value.
        val afterFirst = db.prepare("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            c.step(); c.getText(0)
        }
        // Run again — value must not change.
        migration.migrate(db)
        val afterSecond = db.prepare("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            c.step(); c.getText(0)
        }
        assertEquals(afterFirst, afterSecond)
        assertEquals(tokenCipher.decrypt(afterSecond), "plaintext-user-token")

        db.close()
    }

    private fun assertNotEqualsWithMessage(message: String, unexpected: Any?, actual: Any?) {
        kotlin.test.assertNotEquals(unexpected, actual, message)
    }

    /**
     * Opens a fresh raw SQLite database at [version] (user_version pragma set,
     * matching what SupportSQLiteOpenHelper.Callback did pre-KMP) and runs
     * [block]'s DDL/seed statements against it on the bundled JVM driver.
     */
    private fun createDatabase(version: Int, block: (SQLiteConnection) -> Unit) {
        openRawDatabase(version, block).close()
    }

    private fun openRawDatabase(version: Int, block: (SQLiteConnection) -> Unit): SQLiteConnection {
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        connection.execSQL("PRAGMA user_version = $version")
        block(connection)
        return connection
    }

    /**
     * Executes every CREATE TABLE / CREATE INDEX / CREATE VIEW statement from
     * the exported Room schema JSON for [version] (exposed on the test
     * classpath via the module's test sourceSets), reproducing the exact DDL
     * Room generated for that historical version. Fixtures built this way fail
     * loudly when a hand-written assumption about an old shape goes stale.
     */
    private fun execSchema(db: SQLiteConnection, version: Int) {
        val path = "com.raulshma.jellyplay.core.database.JellyPlayDatabase/$version.json"
        val text = javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: error("exported Room schema not found on test classpath: $path")
        val database = Json.parseToJsonElement(text).jsonObject["database"]!!.jsonObject
        for (entity in database["entities"]!!.jsonArray.map { it.jsonObject }) {
            val tableName = entity["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tableName))
            for (index in entity["indices"]?.jsonArray.orEmpty()) {
                db.execSQL(
                    index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)
                )
            }
        }
        for (view in database["views"]?.jsonArray.orEmpty()) {
            val v = view.jsonObject
            db.execSQL(v["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", v["viewName"]!!.jsonPrimitive.content))
        }
    }

    @Test
    fun migrateAllFromV12_addsOfflineSyncColumns() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            db.execSQL(
                "INSERT INTO offline_media (id, name, mediaType) VALUES (?, ?, ?)",
                arrayOf<Any>("item-1", "Test", "MOVIE"),
            )
        }

        val db = openWithMigrations()
        // The pre-existing row picks up a sync_baseline row via the migration
        // (carried from the v42→43-added columns, which were NULL/0 for a v12
        // base row): nullable signature columns are NULL, flag columns are 0.
        val baseline = db.syncBaselineDao().getBaseline("item-1")
        assertNotNull(baseline)
        with(baseline!!) {
            assertNull(syncedPosterTag)
            assertNull(syncedBackdropTag)
            assertNull(syncedMetadataSignature)
            assertNull(syncedSubtitleSignature)
            assertNull(syncedTrickplaySignature)
            assertNull(syncedSegmentsSignature)
            assertNull(syncedMediaSourceId)
            assertNull(syncedMediaSizeBytes)
            assertNull(lastSyncedAt)
            assertEquals(0, syncUpdateAvailable)
            assertEquals(0, syncMediaChanged)
            assertEquals(0, syncChecking)
            assertEquals(0, syncError)
        }
        // A targeted baseline write round-trips through the new columns.
        db.syncBaselineDao().upsert(
            SyncBaselineEntity(
                id = "item-1",
                syncedPosterTag = "poster-1",
                syncedBackdropTag = "backdrop-1",
                syncedMetadataSignature = "sig",
                syncedSubtitleSignature = "sub-sig",
                syncedTrickplaySignature = "trick-sig",
                syncedSegmentsSignature = "seg-sig",
                syncedMediaSourceId = "src-1",
                syncedMediaSizeBytes = 1000L,
                lastSyncedAt = 123L,
                syncUpdateAvailable = 1,
            )
        )
        val updated = db.syncBaselineDao().getBaseline("item-1")
        assertEquals(updated!!.syncedPosterTag, "poster-1")
        assertEquals(updated.syncedMetadataSignature, "sig")
        assertEquals(updated.syncedSubtitleSignature, "sub-sig")
        assertEquals(updated.syncedTrickplaySignature, "trick-sig")
        assertEquals(updated.syncedSegmentsSignature, "seg-sig")
        assertEquals(123L, updated.lastSyncedAt)
        assertEquals(1, updated.syncUpdateAvailable)
        db.close()
    }

    /**
     * Verifies the v43→v44 migration adds the nullable `providerIdsJson` and
     * `externalUrlsJson` columns to `offline_media`. Pre-existing rows pick up
     * null (degrading to a title-only subtitle search until re-download), and a
     * fresh write round-trips both JSON blobs through the DAO so the offline
     * subtitle search can resolve a TMDB/IMDb id without a server round-trip.
     */
    @Test
    fun migrateAllFromV12_addsOfflineProviderIdColumns() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            db.execSQL(
                "INSERT INTO offline_media (id, name, mediaType) VALUES (?, ?, ?)",
                arrayOf<Any>("item-1", "Test", "MOVIE"),
            )
        }

        val db = openWithMigrations()
        // The pre-existing row picks up null for both new columns.
        val baseline = db.offlineMediaDao().getById("item-1")
        assertNotNull(baseline)
        assertNull(baseline!!.providerIdsJson)
        assertNull(baseline.externalUrlsJson)
        // A targeted write round-trips both JSON blobs through the new columns.
        db.offlineMediaDao().upsert(
            baseline.copy(
                providerIdsJson = """{"tmdb":"12345","imdb":"tt67890"}""",
                externalUrlsJson = """[{"name":"TMDB","url":"https://www.themoviedb.org/movie/12345"}]""",
            )
        )
        val updated = db.offlineMediaDao().getById("item-1")
        assertEquals(updated!!.providerIdsJson, """{"tmdb":"12345","imdb":"tt67890"}""")
        assertEquals(updated.externalUrlsJson, """[{"name":"TMDB","url":"https://www.themoviedb.org/movie/12345"}]""")
        db.close()
    }

    /**
     * Verifies the v45→v46 migration adds the nullable sidecar-signature columns
     * (`syncedSubtitleSignature`, `syncedTrickplaySignature`,
     * `syncedSegmentsSignature`) to `offline_media` (now carried by the
     * `sync_baseline` table after the v46→47 split). Pre-existing rows pick up
     * null (degrading to "never recorded" so the comparator treats the axis as
     * first-contact and never flags a spurious change), and a targeted
     * `SyncBaselineDao.upsert` write round-trips all three signatures.
     */
    @Test
    fun migrateAllFromV12_addsOfflineSidecarSignatureColumns() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            db.execSQL(
                "INSERT INTO offline_media (id, name, mediaType) VALUES (?, ?, ?)",
                arrayOf<Any>("item-1", "Test", "MOVIE"),
            )
        }

        val db = openWithMigrations()
        // Pre-existing row: the migration backfills a sync_baseline row whose
        // sidecar-signature columns are NULL (first-contact axis — comparator
        // treats empty/null as "never recorded").
        val baseline = db.syncBaselineDao().getBaseline("item-1")
        assertNotNull(baseline)
        assertNull(baseline!!.syncedSubtitleSignature)
        assertNull(baseline.syncedTrickplaySignature)
        assertNull(baseline.syncedSegmentsSignature)
        // A targeted baseline write round-trips the new signature columns.
        db.syncBaselineDao().upsert(
            SyncBaselineEntity(
                id = "item-1",
                syncedSubtitleSignature = "sub-hash",
                syncedTrickplaySignature = "trick-hash",
                syncedSegmentsSignature = "seg-hash",
                lastSyncedAt = 999L,
            )
        )
        val updated = db.syncBaselineDao().getBaseline("item-1")
        assertEquals(updated!!.syncedSubtitleSignature, "sub-hash")
        assertEquals(updated.syncedTrickplaySignature, "trick-hash")
        assertEquals(updated.syncedSegmentsSignature, "seg-hash")
        db.close()
    }

    /**
     * Validates the v46→v47 split: the `offline_media` row's playback columns
     * move to a new `playback_state` table and its sync baseline + flags move to
     * a new `sync_baseline` table, while `offline_media` keeps identity +
     * metadata only. The migration backfills a row into each new table for
     * every pre-existing `offline_media` row (carrying the prior values, which
     * for a v12 base row are NULL signatures and 0 flags), so the item still
     * resolves through all three DAOs after the split.
     */
    @Test
    fun migrateAllFromV12_splitsPlaybackAndSyncIntoOwnTables() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            // Only v12-base columns exist at creation time.
            db.execSQL(
                "INSERT INTO offline_media (id, name, mediaType, overview, year, createdAt) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>("item-1", "Test", "MOVIE", "ov", 2001, 42L),
            )
        }

        val db = openWithMigrations()
        // The trimmed entity keeps identity + metadata only.
        val row = db.offlineMediaDao().getById("item-1")
        assertNotNull(row)
        assertEquals(row!!.name, "Test")
        assertEquals(row.mediaType, "MOVIE")
        assertEquals(row.overview, "ov")
        assertEquals(2001, row.year)
        assertEquals(42L, row.createdAt)
        // The migration backfills a sync_baseline row carrying the prior (NULL/0)
        // values for a v12 base row.
        val baseline = db.syncBaselineDao().getBaseline("item-1")
        assertNotNull(baseline)
        assertNull(baseline!!.syncedMetadataSignature)
        assertEquals(0, baseline.syncUpdateAvailable)
        assertEquals(0, baseline.syncSubtitlesPending)
        // The migration backfills a playback_state row with default values.
        val playback = db.playbackStateDao().getById("item-1")
        assertNotNull(playback)
        assertEquals(0.0, playback!!.playedPercentage, 0.0)
        assertEquals(false, playback.isPlayed)
        assertEquals(false, playback.isFavorite)
        db.close()
    }

    @Test
    fun allMigrations_coversContiguousRange() {
        val tokenCipher = JvmTokenCipher.forTestingWithPersistentKey()
        val migrations = allMigrations(tokenCipher)
        // One migration per step from v1 up to the current schema version,
        // each handing off to the next with no gaps or duplicate starts.
        // androidx.room.Database has CLASS retention, so getAnnotation() returns
        // null at runtime — the fallback below is the same constant the
        // annotation was compiled from ([JELLY_PLAY_DATABASE_VERSION]), so a
        // version bump lands here automatically.
        val expected = JellyPlayDatabase::class.java
            .getAnnotation(androidx.room.Database::class.java)?.version
            ?: JELLY_PLAY_DATABASE_VERSION
        val startVersions = migrations.map { it.startVersion }
        assertEquals((1 until expected).toList(), startVersions, "every version 1..<current must start exactly one migration")
        migrations.zipWithNext { a, b ->
            assertEquals(a.endVersion, b.startVersion, "migration chain must be contiguous")
        }
        assertEquals(expected, migrations.last().endVersion)
    }

    /**
     * Verifies the v50→v51 migration adds the nullable `chaptersJson` column to
     * `offline_media` (feature contract: OfflineMediaItem.chapters).
     * Pre-existing rows pick up null (degrading to "no chapters" until
     * re-download), and a fresh write round-trips the JSON blob through the DAO.
     */
    @Test
    fun migrateAllFromV12_addsOfflineChaptersColumn() = runTest {
        createDatabase(12) { db ->
            createServersTable(db)
            createDownloadsTableV11(db)
            createUsersTableV10(db)
            createLyricsCacheTable(db)
            createOfflineMediaTable(db)
            db.execSQL(
                "INSERT INTO offline_media (id, name, mediaType) VALUES (?, ?, ?)",
                arrayOf<Any>("item-1", "Test", "MOVIE"),
            )
        }

        val db = openWithMigrations()
        // The pre-existing row picks up null for the new column.
        val baseline = db.offlineMediaDao().getById("item-1")
        assertNotNull(baseline)
        assertNull(baseline!!.chaptersJson)
        // A targeted write round-trips the JSON blob through the new column.
        val chaptersJson =
            """[{"name":"Opening","startPositionTicks":0},{"name":"Credits","startPositionTicks":100000000,"imageTag":"tag"}]"""
        db.offlineMediaDao().upsert(baseline.copy(chaptersJson = chaptersJson))
        assertEquals(chaptersJson, db.offlineMediaDao().getById("item-1")!!.chaptersJson)
        db.close()
    }

    /**
     * Verifies the v50→v51 migration retires every stored metadata signature
     * AND clears what a pre-upgrade retired-format comparison had left
     * persisted — the metadata per-axis flag and its contribution to the
     * composite badge — while genuine other-axis badges survive. See
     * [MIGRATION_50_51]'s KDoc for the full rationale.
     *
     * The starting schema is executed from the exported `50.json` (see
     * [execSchema]) — the exact tables, indices and view Room generated at
     * v50 — so a drift between [MIGRATION_50_51]'s SQL and the real v50 shape
     * (a renamed column, a NOT NULL column without default) fails loudly here
     * instead of only on device.
     */
    @Test
    fun migrateV50_51_retiresLegacyMetadataSignatures() {
        openRawDatabase(50) { db ->
            execSchema(db, 50)
            // Seed against the REAL v50 schema: one row whose v50-format
            // comparison already left a stale metadata flag + lit composite
            // badge, one already-first-contact row, one with a genuine
            // other-axis (images) change whose badge must survive, and one
            // with only a pending subtitle bundle (the subtitle axis counts
            // pending as changed, so its badge must survive too).
            db.execSQL("INSERT INTO offline_media (id, name, mediaType) VALUES ('item-1', 'Test', 'MOVIE')")
            db.execSQL("INSERT INTO offline_media (id, name, mediaType) VALUES ('item-2', 'Test 2', 'MOVIE')")
            db.execSQL("INSERT INTO offline_media (id, name, mediaType) VALUES ('item-3', 'Test 3', 'MOVIE')")
            db.execSQL("INSERT INTO offline_media (id, name, mediaType) VALUES ('item-4', 'Test 4', 'MOVIE')")
            db.execSQL(
                "INSERT INTO sync_baseline (id, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature, " +
                    "syncUpdateAvailable, syncMetadataChanged) " +
                    "VALUES ('item-1', 'poster-1', 'backdrop-1', 'legacy-format-hash', 1, 1)"
            )
            db.execSQL("INSERT INTO sync_baseline (id) VALUES ('item-2')")
            db.execSQL(
                "INSERT INTO sync_baseline (id, syncedMetadataSignature, syncUpdateAvailable, syncImagesChanged) " +
                    "VALUES ('item-3', 'legacy-format-hash-3', 1, 1)"
            )
            db.execSQL(
                "INSERT INTO sync_baseline (id, syncedMetadataSignature, syncUpdateAvailable, " +
                    "syncSubtitlesPending) VALUES ('item-4', 'legacy-format-hash-4', 1, 1)"
            )

            MIGRATION_50_51.migrate(db)

            // Every stored metadata signature is gone; other baseline data survives.
            db.prepare(
                "SELECT id, syncedPosterTag, syncedBackdropTag, syncedMetadataSignature FROM sync_baseline ORDER BY id"
            ).use { c ->
                assertTrue(c.step())
                assertEquals("item-1", c.getText(0))
                assertEquals("poster-1", c.getText(1))
                assertEquals("backdrop-1", c.getText(2))
                assertTrue(c.isNull(3))
                assertTrue(c.step())
                assertEquals("item-2", c.getText(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
                assertTrue(c.step())
                assertEquals("item-3", c.getText(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
                assertTrue(c.step())
                assertEquals("item-4", c.getText(0))
                assertTrue(c.isNull(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
                assertFalse(c.step())
            }
            // The retired format's fallout is cleared at upgrade time instead of
            // lingering until each row's next TTL-gated check: item-1's stale
            // metadata flag and composite badge go dark immediately, item-2 stays
            // clean, and item-3/item-4's genuine non-metadata badges survive.
            db.prepare(
                "SELECT id, syncMetadataChanged, syncImagesChanged, syncSubtitlesChanged, " +
                    "syncSubtitlesPending, syncUpdateAvailable FROM sync_baseline ORDER BY id"
            ).use { c ->
                assertTrue(c.step())
                assertEquals("item-1", c.getText(0))
                assertEquals(0L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertEquals(0L, c.getLong(4))
                assertEquals(0L, c.getLong(5))
                assertTrue(c.step())
                assertEquals("item-2", c.getText(0))
                assertEquals(0L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertEquals(0L, c.getLong(4))
                assertEquals(0L, c.getLong(5))
                assertTrue(c.step())
                assertEquals("item-3", c.getText(0))
                assertEquals(0L, c.getLong(1))
                assertEquals(1L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertEquals(0L, c.getLong(4))
                assertEquals(1L, c.getLong(5))
                assertTrue(c.step())
                assertEquals("item-4", c.getText(0))
                assertEquals(0L, c.getLong(1))
                assertEquals(0L, c.getLong(2))
                assertEquals(0L, c.getLong(3))
                assertEquals(1L, c.getLong(4))
                assertEquals(1L, c.getLong(5))
                assertFalse(c.step())
            }
            // The new column exists and pre-existing rows pick up null chapters.
            db.prepare("SELECT id, chaptersJson FROM offline_media").use { c ->
                assertTrue(c.step())
                assertEquals("item-1", c.getText(0))
                assertTrue(c.isNull(1))
            }
            db.close()
        }
    }

    private fun openWithMigrations(): JellyPlayDatabase {
        val tokenCipher = JvmTokenCipher.forTestingWithPersistentKey()
        val db = Room.databaseBuilder<JellyPlayDatabase>(dbFile.absolutePath)
            .addMigrations(*allMigrations(tokenCipher).toTypedArray())
            .setDriver(BundledSQLiteDriver())
            .build()
        database = db
        return db
    }

    private fun createServersTable(db: SQLiteConnection) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS servers (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                address TEXT NOT NULL,
                userId TEXT,
                accessToken TEXT,
                lastConnected INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    private fun createDownloadsTableBase(db: SQLiteConnection) {
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

    private fun createDownloadsTableV5(db: SQLiteConnection) {
        createDownloadsTableBase(db)
        db.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
    }

    private fun createDownloadsTableV11(db: SQLiteConnection) {
        createDownloadsTableV5(db)
        db.execSQL("ALTER TABLE downloads ADD COLUMN seriesId TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonId TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seriesName TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonName TEXT")
        db.execSQL("ALTER TABLE downloads ADD COLUMN episodeNumber INTEGER")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seasonNumber INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_createdAt ON downloads(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seriesId ON downloads(seriesId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seasonId ON downloads(seasonId)")
    }

    private fun createUsersTable(db: SQLiteConnection) {
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

    private fun createUsersTableV10(db: SQLiteConnection) {
        createUsersTable(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId_lastConnected ON users(serverId, lastConnected)")
        db.execSQL("ALTER TABLE users ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
    }

    private fun createLyricsCacheTable(db: SQLiteConnection) {
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

    private fun createOfflineMediaTable(db: SQLiteConnection) {
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
