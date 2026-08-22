package com.raulshma.jellyplay.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.raulshma.jellyplay.core.database.crypto.AndroidTokenCipher
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.migration.allMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Android Keystore-backed cipher (pre-KMP TokenCipher wiring, unchanged). */
    @Provides
    @Singleton
    fun provideTokenCipher(
        @ApplicationContext context: Context,
    ): TokenCipher = AndroidTokenCipher(context)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        tokenCipher: TokenCipher,
    ): JellyPlayDatabase = Room.databaseBuilder(
        context,
        JellyPlayDatabase::class.java,
        "jellyplay.db",
    )
        .addMigrations(*allMigrations(tokenCipher).toTypedArray())
        .fallbackToDestructiveMigrationOnDowngrade()
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
    fun providePlaybackStateDao(database: JellyPlayDatabase) = database.playbackStateDao()

    @Provides
    fun provideSyncBaselineDao(database: JellyPlayDatabase) = database.syncBaselineDao()

    @Provides
    fun provideAuditLogDao(database: JellyPlayDatabase) = database.auditLogDao()

    @Provides
    fun provideScanStateDao(database: JellyPlayDatabase) = database.scanStateDao()

    @Provides
    fun provideSmartPlaylistDao(database: JellyPlayDatabase) = database.smartPlaylistDao()

    @Provides
    fun provideMoodPlaylistDao(database: JellyPlayDatabase) = database.moodPlaylistDao()

    @Provides
    fun provideAudioQueueDao(database: JellyPlayDatabase) = database.audioQueueDao()

    @Provides
    fun provideSearchHistoryDao(database: JellyPlayDatabase) = database.searchHistoryDao()

    @Provides
    fun provideSeenMediaDao(database: JellyPlayDatabase) = database.seenMediaDao()

    @Provides
    fun provideItemPlaybackPreferenceDao(database: JellyPlayDatabase) = database.itemPlaybackPreferenceDao()

    @Provides
    fun providePlaybackOutboxDao(database: JellyPlayDatabase) = database.playbackOutboxDao()

    @Provides
    fun provideHomeSectionCacheDao(database: JellyPlayDatabase) = database.homeSectionCacheDao()
}
