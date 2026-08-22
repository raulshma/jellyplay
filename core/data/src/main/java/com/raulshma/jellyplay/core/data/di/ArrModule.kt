package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bridges [ArrRepository] to the Koin-owned single (C4 part 2, batch 3):
 * `ArrRepositoryImpl` moved into `:shared:core:data` jvmShared and is
 * constructed by `dataJvmModule` (its `SeerrRepository` ctor dep flipped to
 * Koin first). The former `@Binds` became this `@Provides` bridge.
 */
@Module
@InstallIn(SingletonComponent::class)
object ArrModule {

    @Provides
    @Singleton
    fun provideArrRepository(): ArrRepository = koin().get()
}
