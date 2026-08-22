package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStoreImpl
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Subtitle storage surface bindings.
 *
 * [SubtitleProviderRepository] flipped to a Koin bridge (C4 part 2, batch 3):
 * the impl moved into `:shared:core:data` jvmShared once the
 * `Map<SubtitleProviderKind, SubtitleProvider>` multibinding moved to Koin
 * (`networkJvmModule`); `dataJvmModule` constructs it. The `@Binds` became
 * the `@Provides` bridge below — `@Inject` was stripped at the move. The
 * impl still exposes `searchJellyfin(...)` as a concrete method (not on the
 * interface) for the player/editor to bridge the Jellyfin server search.
 *
 * [StreamingSubtitleStore] — durable per-item subtitle storage for streaming
 * (non-downloaded) items — stays Hilt-owned (its impl is still legacy) and
 * keeps its `@Binds`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubtitleModule {

    companion object {
        @Provides
        @Singleton
        fun provideSubtitleProviderRepository(): SubtitleProviderRepository = koin().get()
    }

    @Binds
    @Singleton
    abstract fun bindStreamingSubtitleStore(
        impl: StreamingSubtitleStoreImpl,
    ): StreamingSubtitleStore
}
