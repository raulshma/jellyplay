package com.raulshma.jellyplay.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
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

    private fun openWithMigrations(): JellyPlayDatabase {
        val tokenCipher = TokenCipher.forTestingWithPersistentKey()
        val db = Room.databaseBuilder(
            context,
            JellyPlayDatabase::class.java,
            dbFile.absolutePath,
        )
            .addMigrations(*ALL_MIGRATIONS.toTypedArray(), Migration24To25(tokenCipher))
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
