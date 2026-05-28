package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
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

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN speedBytesPerSec INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
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

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId_lastConnected ON users(serverId, lastConnected)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_createdAt ON downloads(createdAt)")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN isAdmin INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JellyPlayDatabase = Room.databaseBuilder(
        context,
        JellyPlayDatabase::class.java,
        "jellyplay.db",
    )
        .addMigrations(MIGRATION_2_3, MIGRATION_4_5, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
        .fallbackToDestructiveMigration(true)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Provides
    fun provideServerDao(database: JellyPlayDatabase) = database.serverDao()

    @Provides
    fun provideUserDao(database: JellyPlayDatabase) = database.userDao()

    @Provides
    fun provideDownloadDao(database: JellyPlayDatabase) = database.downloadDao()

    @Provides
    fun provideLyricsCacheDao(database: JellyPlayDatabase) = database.lyricsCacheDao()

    @Provides
    fun provideOfflineMediaDao(database: JellyPlayDatabase) = database.offlineMediaDao()
}
