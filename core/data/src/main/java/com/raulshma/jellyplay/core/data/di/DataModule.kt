package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.DownloadRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.WatchHistoryRepositoryImpl
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import com.raulshma.jellyplay.core.data.util.DownloadDelegate
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
            downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
        ): DownloadDelegate = DownloadDelegate(downloadRepository, playbackRepository)

        @dagger.Provides
        @Singleton
        fun provideGetHomeSectionsUseCase(
            mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
        ): com.raulshma.jellyplay.core.data.usecase.GetHomeSectionsUseCase =
            com.raulshma.jellyplay.core.data.usecase.GetHomeSectionsUseCase(mediaRepository)

        @dagger.Provides
        @Singleton
        fun provideGetMediaDetailUseCase(
            mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
            playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
        ): com.raulshma.jellyplay.core.data.usecase.GetMediaDetailUseCase =
            com.raulshma.jellyplay.core.data.usecase.GetMediaDetailUseCase(mediaRepository, playbackRepository)

        @dagger.Provides
        @Singleton
        fun provideStartMediaDownloadUseCase(
            downloadDelegate: DownloadDelegate,
        ): com.raulshma.jellyplay.core.data.usecase.StartMediaDownloadUseCase =
            com.raulshma.jellyplay.core.data.usecase.StartMediaDownloadUseCase(downloadDelegate)
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
}
