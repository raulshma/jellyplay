package com.raulshma.jellyplay.core.database.di

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.crypto.TokenCipher
import com.raulshma.jellyplay.core.database.dao.AuditLogDao
import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
import com.raulshma.jellyplay.core.database.dao.DownloadDao
import com.raulshma.jellyplay.core.database.dao.HomeSectionCacheDao
import com.raulshma.jellyplay.core.database.dao.ItemPlaybackPreferenceDao
import com.raulshma.jellyplay.core.database.dao.LyricsCacheDao
import com.raulshma.jellyplay.core.database.dao.MoodPlaylistDao
import com.raulshma.jellyplay.core.database.dao.OfflineMediaDao
import com.raulshma.jellyplay.core.database.dao.PlaybackOutboxDao
import com.raulshma.jellyplay.core.database.dao.PlaybackStateDao
import com.raulshma.jellyplay.core.database.dao.ScanStateDao
import com.raulshma.jellyplay.core.database.dao.SearchHistoryDao
import com.raulshma.jellyplay.core.database.dao.SeenMediaDao
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.SmartPlaylistDao
import com.raulshma.jellyplay.core.database.dao.SyncBaselineDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * KMP cutover wiring (docs/kmp-migration-plan.md §Phase C4): Koin owns
 * construction ([databaseDaosModule] + [androidDatabaseModule]); these Hilt
 * providers are thin bridges so Hilt consumers keep compiling and both
 * frameworks share the same instances. Shim deleted at Phase X.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Android Keystore-backed cipher (owned by Koin since Phase C4). */
    @Provides
    @Singleton
    fun provideTokenCipher(): TokenCipher = koin().get()

    @Provides
    @Singleton
    fun provideDatabase(): JellyPlayDatabase = koin().get()

    @Provides
    fun provideServerDao(): ServerDao = koin().get()

    @Provides
    fun provideUserDao(): UserDao = koin().get()

    @Provides
    fun provideDownloadDao(): DownloadDao = koin().get()

    @Provides
    fun provideLyricsCacheDao(): LyricsCacheDao = koin().get()

    @Provides
    fun provideOfflineMediaDao(): OfflineMediaDao = koin().get()

    @Provides
    fun providePlaybackStateDao(): PlaybackStateDao = koin().get()

    @Provides
    fun provideSyncBaselineDao(): SyncBaselineDao = koin().get()

    @Provides
    fun provideAuditLogDao(): AuditLogDao = koin().get()

    @Provides
    fun provideScanStateDao(): ScanStateDao = koin().get()

    @Provides
    fun provideSmartPlaylistDao(): SmartPlaylistDao = koin().get()

    @Provides
    fun provideMoodPlaylistDao(): MoodPlaylistDao = koin().get()

    @Provides
    fun provideAudioQueueDao(): AudioQueueDao = koin().get()

    @Provides
    fun provideSearchHistoryDao(): SearchHistoryDao = koin().get()

    @Provides
    fun provideSeenMediaDao(): SeenMediaDao = koin().get()

    @Provides
    fun provideItemPlaybackPreferenceDao(): ItemPlaybackPreferenceDao = koin().get()

    @Provides
    fun providePlaybackOutboxDao(): PlaybackOutboxDao = koin().get()

    @Provides
    fun provideHomeSectionCacheDao(): HomeSectionCacheDao = koin().get()
}
