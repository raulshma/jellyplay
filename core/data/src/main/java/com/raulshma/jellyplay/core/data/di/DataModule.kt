package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.LyricsRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
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
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
import com.raulshma.jellyplay.core.data.network.OkHttpConfigProviderImpl
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextSchedulerImpl
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncSchedulerImpl
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
            downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
        ): DownloadDelegate = DownloadDelegate(context, downloadRepository, playbackRepository)

        // LyricsRepository is a narrow (ISP) view of the same MediaRepository singleton
        // (MediaRepository extends LyricsRepository). Providing it via delegation guarantees
        // a single shared instance — no duplicate caches.
        @dagger.Provides
        @Singleton
        fun provideLyricsRepository(mediaRepository: MediaRepository): LyricsRepository =
            mediaRepository

        // Note: GetHomeSectionsUseCase and GetMediaDetailUseCase previously had
        // explicit @Provides @Singleton methods here. They were removed because
        // each use case already has @Inject constructor — Hilt provides them
        // automatically (unscoped, which is the correct scope for stateless
        // transformers). No consumer in the codebase injects these directly
        // today; they remain available for future use via constructor injection.
    }

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(impl: PlaybackRepositoryImpl): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindDownloadIntake(impl: DownloadIntakeImpl): DownloadIntake

    @Binds
    @Singleton
    abstract fun bindOfflineRepository(impl: OfflineRepositoryImpl): OfflineRepository

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
    abstract fun bindOkHttpConfigProvider(impl: OkHttpConfigProviderImpl): OkHttpConfigProvider
}
