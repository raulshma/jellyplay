package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds [ArrRepository] to its single implementation. Mirrors [SeerrModule] —
 * the *arr repository lives in its own module so the surface area stays
 * clearly separable from the core [DataModule] aggregators.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ArrModule {

    @Binds
    @Singleton
    abstract fun bindArrRepository(
        impl: ArrRepositoryImpl,
    ): ArrRepository
}
