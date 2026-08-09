package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [SubtitleProviderRepository] to its implementation. Mirrors
 * [ArrModule] / [SeerrModule] — the subtitle fan-out repository lives in its
 * own module so its surface stays separable from [DataModule]'s aggregators.
 *
 * The [SubtitleProviderRepositoryImpl] also exposes `searchJellyfin(...)` as a
 * concrete method (not on the interface) for the player/editor to bridge the
 * Jellyfin server search; callers that need it inject
 * [SubtitleProviderRepositoryImpl] directly, while the rest inject the
 * interface.
 *
 * [StreamingSubtitleStore] — durable per-item subtitle storage for streaming
 * (non-downloaded) items — is bound here too since it's part of the same
 * subtitle-storage surface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubtitleModule {

    @Binds
    @Singleton
    abstract fun bindSubtitleProviderRepository(
        impl: SubtitleProviderRepositoryImpl,
    ): SubtitleProviderRepository

    @Binds
    @Singleton
    abstract fun bindStreamingSubtitleStore(
        impl: StreamingSubtitleStoreImpl,
    ): StreamingSubtitleStore
}
