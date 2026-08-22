package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bridges the legacy Hilt graph to the Koin-owned Seerr layer (C4 part 2,
 * batch 3): `SeerrRepositoryImpl` moved into `:shared:core:data` jvmShared
 * and is constructed by `dataJvmModule`; `SeerrRequestDelegate` (and its
 * manually-constructed `SeerrRequestStateHolder`) moved alongside it. The
 * former `@Binds bindSeerrRepository` became this `@Provides` bridge — with
 * `@Inject` stripped at the move, Hilt resolves both types here instead of
 * building parallel instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {

    @Provides
    @Singleton
    fun provideSeerrRepository(): SeerrRepository = koin().get()

    @Provides
    @Singleton
    fun provideSeerrRequestDelegate(): SeerrRequestDelegate = koin().get()
}
