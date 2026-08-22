package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bridges the legacy Hilt graph to the Koin-owned
 * `Map<SubtitleProviderKind, SubtitleProvider>` fan-out consumed by the
 * subtitle repository (Phase C4 part 2 subtitle-map flip).
 *
 * The map's construction moved verbatim into `:shared:core:network`'s
 * `networkJvmModule` (each raw provider wrapped in a
 * `ResilientSubtitleProvider` so `RetryPolicy` applies to every call).
 * The former `@IntoMap` entries and the `SubtitleProviderKey` `@MapKey`
 * annotation were deleted with the flip — a new provider now means a new
 * enum value, one impl, and one Koin map entry.
 */
@Module
@InstallIn(SingletonComponent::class)
object SubtitleProviderModule {

    @Provides
    @Singleton
    fun provideSubtitleProviders(): Map<SubtitleProviderKind, @JvmSuppressWildcards SubtitleProvider> =
        koin().get()
}
