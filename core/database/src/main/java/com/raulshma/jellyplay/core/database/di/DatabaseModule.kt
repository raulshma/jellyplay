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

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JellyPlayDatabase = Room.databaseBuilder(
        context,
        JellyPlayDatabase::class.java,
        "jellyplay.db",
    )
        .addMigrations(MIGRATION_2_3, MIGRATION_4_5)
        .fallbackToDestructiveMigration(true)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Provides
    fun provideServerDao(database: JellyPlayDatabase) = database.serverDao()

    @Provides
    fun provideUserDao(database: JellyPlayDatabase) = database.userDao()

    @Provides
    fun provideDownloadDao(database: JellyPlayDatabase) = database.downloadDao()
}
