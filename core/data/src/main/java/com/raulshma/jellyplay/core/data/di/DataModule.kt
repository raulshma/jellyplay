package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogue
import com.raulshma.jellyplay.core.data.catalogue.EpisodeCatalogueImpl
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlayedStateSync
import com.raulshma.jellyplay.core.data.repository.PlayedStateSyncImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepository
import com.raulshma.jellyplay.core.data.repository.SeenMediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.download.DownloadIntakeImpl
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import com.raulshma.jellyplay.core.data.util.SystemTimeSource
import com.raulshma.jellyplay.core.data.util.TimeSource
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.DownloadTransferClient
import com.raulshma.jellyplay.core.data.worker.OkHttpDownloadTransferClient
import com.raulshma.jellyplay.core.network.config.OkHttpConfigProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        @dagger.Provides
        @Singleton
        fun provideDownloadDelegate(
            @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
            writer: com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter,
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
        ): DownloadDelegate = DownloadDelegate(context, writer, playbackRepository)

        // StoragePolicy takes the download-byte aggregate as a suspend lambda so
        // it stays pure logic (testable without a DAO). Bound here so the cap
        // rule has a single owner instead of being duplicated across
        // DownloadRepositoryImpl.startDownload / downloadSeries.
        @dagger.Provides
        @Singleton
        fun provideStoragePolicy(
            networkOfflineStore: com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore,
            downloadsStore: com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore,
            downloadDao: com.raulshma.jellyplay.core.database.dao.DownloadDao,
        ): com.raulshma.jellyplay.core.data.repository.StoragePolicy =
            com.raulshma.jellyplay.core.data.repository.StoragePolicy(
                networkOfflineStore = networkOfflineStore,
                downloadsStore = downloadsStore,
                currentBytesProvider = { downloadDao.getTotalDownloadedBytes() },
            )

        // LyricsRepository is a narrow (ISP) view of the same MediaRepository singleton
        // (MediaRepository extends LyricsRepository). Providing it via delegation guarantees
        // a single shared instance — no duplicate caches.
        @dagger.Provides
        @Singleton
        fun provideLyricsRepository(mediaRepository: MediaRepository): LyricsRepository =
            mediaRepository
    }

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    // The deep "Episode Catalogue" seam: the single owner of the series
    // seasons/episodes snapshot. See EpisodeCatalogue kdoc — depends on
    // JellyfinApiClient + OfflineRepository only (never MediaRepository), so
    // MediaRepositoryImpl can take it without forming a DI cycle.
    @Binds
    @Singleton
    abstract fun bindEpisodeCatalogue(impl: EpisodeCatalogueImpl): EpisodeCatalogue

    @Binds
    @Singleton
    abstract fun bindPlayedStateSync(impl: PlayedStateSyncImpl): PlayedStateSync

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: PlaybackRepositoryImpl): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackOutboxRepository(impl: PlaybackOutboxRepositoryImpl): PlaybackOutboxRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    // The narrow write surface shared by DownloadDelegate. Bound to the same
    // DownloadRepositoryImpl singleton — a real seam in the type graph, not a
    // second instance. Keeps DownloadDelegate off the 25-method god-interface.
    @Binds
    @Singleton
    abstract fun bindOfflineDownloadWriter(impl: DownloadRepositoryImpl): com.raulshma.jellyplay.core.data.repository.OfflineDownloadWriter

    @Binds
    @Singleton
    abstract fun bindDownloadIntake(impl: DownloadIntakeImpl): DownloadIntake

    @Binds
    @Singleton
    abstract fun bindOfflineRepository(impl: OfflineRepositoryImpl): OfflineRepository

    // The deep "Playback Source Resolver" seam: the single owner of the
    // download-vs-stream fork. See PlaybackSourceResolver kdoc — every caller
    // already depends on :core:data, so this @Binds is the only wiring needed.
    @Binds
    @Singleton
    abstract fun bindPlaybackSourceResolver(
        impl: com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolverImpl,
    ): com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver

    @Binds
    @Singleton
    abstract fun bindAdminStatisticsRepository(impl: AdminStatisticsRepositoryImpl): AdminStatisticsRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(impl: SearchHistoryRepositoryImpl): SearchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindWatchHistoryRepository(impl: WatchHistoryRepositoryImpl): WatchHistoryRepository

    @Binds
    @Singleton
    abstract fun bindImageUrlProvider(impl: ImageUrlProviderImpl): ImageUrlProvider

    @Binds
    @Singleton
    abstract fun bindSeenMediaRepository(impl: SeenMediaRepositoryImpl): SeenMediaRepository

    @Binds
    @Singleton
    abstract fun bindItemPlaybackPreferenceRepository(impl: ItemPlaybackPreferenceRepositoryImpl): ItemPlaybackPreferenceRepository

    @Binds
    @Singleton
    abstract fun bindTvWatchNextScheduler(impl: TvWatchNextSchedulerImpl): TvWatchNextScheduler

    @Binds
    @Singleton
    abstract fun bindUserDataSyncScheduler(impl: UserDataSyncSchedulerImpl): UserDataSyncScheduler

    @Binds
    @Singleton
    abstract fun bindPlaybackSyncScheduler(impl: PlaybackSyncSchedulerImpl): PlaybackSyncScheduler

    // OkHttp adapter for the download transfer seam (DownloadTransferRunner /
    // DownloadWorker depend on the interface; the fake in src/test backs it in
    // unit tests). @Inject constructor on the impl, interface bound here.
    @Binds
    @Singleton
    abstract fun bindDownloadTransferClient(impl: OkHttpDownloadTransferClient): DownloadTransferClient

    @Binds
    @Singleton
    abstract fun bindOkHttpConfigProvider(impl: OkHttpConfigProviderImpl): OkHttpConfigProvider

    @Binds
    @Singleton
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource

    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(impl: com.raulshma.jellyplay.core.data.update.AppUpdateRepositoryImpl): com.raulshma.jellyplay.core.data.update.AppUpdateRepository
}
