package com.raulshma.jellyplay.core.database.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        val db = Room.databaseBuilder(
            context,
            JellyPlayDatabase::class.java,
            dbFile.absolutePath,
        )
            .addMigrations(*ALL_MIGRATIONS.toTypedArray())
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
