package com.raulshma.jellyplay.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.entity.HomeSectionCacheEntity
import com.raulshma.jellyplay.core.database.migration.allMigrations
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private var database: JellyPlayDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, "migration-test.db")
        dbFile.parentFile?.mkdirs()
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun teardown() {
        database?.close()
        if (dbFile.exists()) dbFile.delete()
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
        assertEquals("Test Server", server!!.name)
        assertEquals("https://test.example.com", server.address)

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
        assertEquals("Test Server", server!!.name)
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
        assertEquals("Test Download", download!!.name)
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
        assertEquals("provider-1", cached!!.provider)
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
        assertEquals("TestUser", user!!.name)
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
        assertEquals("AdminUser", migrated!!.name)
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
        assertEquals("item-1", pending[0].itemId)
        assertEquals("PROGRESS", pending[0].eventType)
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
        assertEquals(null, migrated!!.pausedReason)
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
        assertEquals("NETWORK", written!!.pausedReason)
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
        assertEquals("deu", saved!!.audioLanguage)
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
        assertEquals("Continue Watching", decoded.sections[0].title)
        assertEquals("item-1", decoded.sections[0].items[0].id)
        // Identity-scoped clear must remove the row.
        db.homeSectionCacheDao().clearForIdentity("srv-1", "u1")
        assertEquals(null, db.homeSectionCacheDao().get("srv-1", "u1", "key-1"))
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
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(24) {
                override fun onCreate(db: SupportSQLiteDatabase) {
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
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase

        val tokenCipher = TokenCipher.forTestingWithPersistentKey()
        Migration24To25(tokenCipher).migrate(db)

        db.query("SELECT accessToken FROM servers WHERE id = 'srv-1'").use { c ->
            assertTrue(c.moveToFirst())
            val stored = c.getString(0)
            assertNotEqualsWithMessage("Server token must be encrypted", "plaintext-server-token", stored)
            assertEquals("plaintext-server-token", tokenCipher.decrypt(stored))
        }
        db.query("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            assertTrue(c.moveToFirst())
            val stored = c.getString(0)
            assertNotEqualsWithMessage("User token must be encrypted", "plaintext-user-token", stored)
            assertEquals("plaintext-user-token", tokenCipher.decrypt(stored))
        }

        db.close()
        helper.close()
    }

    /**
     * Regression: re-running the migration on already-encrypted rows must NOT change
     * them (cipher is idempotent) and must NOT corrupt the data.
     */
    @Test
    fun migrateV24_isIdempotentWhenRunTwice() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(24) {
                override fun onCreate(db: SupportSQLiteDatabase) {
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
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase

        val tokenCipher = TokenCipher.forTestingWithPersistentKey()
        val migration = Migration24To25(tokenCipher)
        migration.migrate(db)
        // Capture the post-first-migration value.
        val afterFirst = db.query("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            c.moveToFirst(); c.getString(0)
        }
        // Run again — value must not change.
        migration.migrate(db)
        val afterSecond = db.query("SELECT accessToken FROM users WHERE userId = 'u1'").use { c ->
            c.moveToFirst(); c.getString(0)
        }
        assertEquals(afterFirst, afterSecond)
        assertEquals("plaintext-user-token", tokenCipher.decrypt(afterSecond))

        db.close()
        helper.close()
    }

    private fun assertNotEqualsWithMessage(message: String, unexpected: Any?, actual: Any?) {
        org.junit.Assert.assertNotEquals(message, unexpected, actual)
    }

    private fun createDatabase(version: Int, block: (SupportSQLiteDatabase) -> Unit) {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
            .builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    block(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = factory.create(config)
        helper.writableDatabase.close()
        helper.close()
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
        // The pre-existing row picks up the migration's defaults for the new
        // sync columns: nullable baseline columns are NULL, flag columns 0.
        val baseline = db.offlineMediaDao().getSyncBaseline("item-1")
        assertNotNull(baseline)
        with(baseline!!) {
            assertEquals(null, syncedPosterTag)
            assertEquals(null, syncedBackdropTag)
            assertEquals(null, syncedMetadataSignature)
            assertEquals(null, syncedMediaSourceId)
            assertEquals(null, syncedMediaSizeBytes)
            assertEquals(null, lastSyncedAt)
            assertEquals(0, syncUpdateAvailable)
            assertEquals(0, syncMediaChanged)
            assertEquals(0, syncChecking)
            assertEquals(0, syncError)
        }
        // A targeted baseline write round-trips through the new columns.
        db.offlineMediaDao().updateSyncBaseline(
            itemId = "item-1",
            posterTag = "poster-1",
            backdropTag = "backdrop-1",
            metadataSignature = "sig",
            mediaSourceId = "src-1",
            mediaSizeBytes = 1000L,
            lastSyncedAt = 123L,
            updateAvailable = 1,
            mediaChanged = 0,
            checking = 0,
            error = 0,
        )
        val updated = db.offlineMediaDao().getSyncBaseline("item-1")
        assertEquals("poster-1", updated!!.syncedPosterTag)
        assertEquals("sig", updated.syncedMetadataSignature)
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
        assertEquals(null, baseline!!.providerIdsJson)
        assertEquals(null, baseline.externalUrlsJson)
        // A targeted write round-trips both JSON blobs through the new columns.
        db.offlineMediaDao().upsert(
            baseline.copy(
                providerIdsJson = """{"tmdb":"12345","imdb":"tt67890"}""",
                externalUrlsJson = """[{"name":"TMDB","url":"https://www.themoviedb.org/movie/12345"}]""",
            )
        )
        val updated = db.offlineMediaDao().getById("item-1")
        assertEquals("""{"tmdb":"12345","imdb":"tt67890"}""", updated!!.providerIdsJson)
        assertEquals(
            """[{"name":"TMDB","url":"https://www.themoviedb.org/movie/12345"}]""",
            updated.externalUrlsJson,
        )
        db.close()
    }

    @Test
    fun allMigrations_coversContiguousRange() {
        val tokenCipher = TokenCipher.forTestingWithPersistentKey()
        val migrations = allMigrations(tokenCipher)
        // One migration per step from v1 up to the current schema version (45),
        // each handing off to the next with no gaps or duplicate starts.
        // androidx.room.Database has CLASS retention, so getAnnotation() returns
        // null at runtime — the hardcoded fallback is the authoritative value
        // and must be bumped alongside JellyPlayDatabase's version.
        val expected = JellyPlayDatabase::class.java
            .getAnnotation(androidx.room.Database::class.java)?.version
            ?: 45
        val startVersions = migrations.map { it.startVersion }
        assertEquals(
            "every version 1..<current must start exactly one migration",
            (1 until expected).toList(),
            startVersions,
        )
        migrations.zipWithNext { a, b ->
            assertEquals(
                "migration chain must be contiguous",
                a.endVersion,
                b.startVersion,
            )
        }
        assertEquals(expected, migrations.last().endVersion)
    }

    private fun openWithMigrations(): JellyPlayDatabase {
        val tokenCipher = TokenCipher.forTestingWithPersistentKey()
        val db = Room.databaseBuilder(
            context,
            JellyPlayDatabase::class.java,
            dbFile.absolutePath,
        )
            .addMigrations(*allMigrations(tokenCipher).toTypedArray())
            .allowMainThreadQueries()
            .build()
        database = db
        return db
    }

    private fun createServersTable(db: SupportSQLiteDatabase) {
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

    private fun createDownloadsTableBase(db: SupportSQLiteDatabase) {
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

    private fun createDownloadsTableV5(db: SupportSQLiteDatabase) {
        createDownloadsTableBase(db)
        db.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
    }

    private fun createDownloadsTableV11(db: SupportSQLiteDatabase) {
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

    private fun createUsersTable(db: SupportSQLiteDatabase) {
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

    private fun createUsersTableV10(db: SupportSQLiteDatabase) {
        createUsersTable(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId_lastConnected ON users(serverId, lastConnected)")
        db.execSQL("ALTER TABLE users ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
    }

    private fun createLyricsCacheTable(db: SupportSQLiteDatabase) {
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

    private fun createOfflineMediaTable(db: SupportSQLiteDatabase) {
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
