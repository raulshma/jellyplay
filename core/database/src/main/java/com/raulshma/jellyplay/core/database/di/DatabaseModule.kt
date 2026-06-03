package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.migration.ALL_MIGRATIONS
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): JellyPlayDatabase = Room.databaseBuilder(
        context,
        JellyPlayDatabase::class.java,
        "jellyplay.db",
    )
        .addMigrations(*ALL_MIGRATIONS.toTypedArray())
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

    @Provides
    fun provideAuditLogDao(database: JellyPlayDatabase) = database.auditLogDao()

    @Provides
    fun provideScanStateDao(database: JellyPlayDatabase) = database.scanStateDao()
}
