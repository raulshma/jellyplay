package com.raulshma.jellyplay.core.network.di

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.network.subtitle.OpenSubtitlesSubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.ResilientSubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProvider
import com.raulshma.jellyplay.core.network.subtitle.SubtitleProviderKey
import com.raulshma.jellyplay.core.network.subtitle.WyzieSubtitleProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import javax.inject.Singleton

/**
 * Binds each external subtitle provider into the
 * `Map<SubtitleProviderKind, SubtitleProvider>` consumed by the fan-out
 * repository. Kept in its own `@Module object` (separate from the abstract
 * [NetworkModule]) because `@IntoMap @Provides` requires an object module.
 *
 * Each raw impl is wrapped in a [ResilientSubtitleProvider] (so
 * [com.raulshma.jellyplay.core.network.RetryPolicy] applies to every call)
 * before being placed in the map. Adding a new provider = a new enum value, one
 * impl, and one `@IntoMap` entry here.
 */
@Module
@InstallIn(SingletonComponent::class)
object SubtitleProviderModule {

    @Provides
    @Singleton
    @IntoMap
    @SubtitleProviderKey(SubtitleProviderKind.WYZIE)
    fun provideWyzieSubtitleProvider(
        impl: WyzieSubtitleProvider,
    ): SubtitleProvider = ResilientSubtitleProvider(impl)

    @Provides
    @Singleton
    @IntoMap
    @SubtitleProviderKey(SubtitleProviderKind.OPENSUBTITLES)
    fun provideOpenSubtitlesSubtitleProvider(
        impl: OpenSubtitlesSubtitleProvider,
    ): SubtitleProvider = ResilientSubtitleProvider(impl)
}
