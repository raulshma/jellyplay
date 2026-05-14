package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SeerrModule {

    @Binds
    @Singleton
    abstract fun bindSeerrRepository(
        impl: SeerrRepositoryImpl,
    ): SeerrRepository
}
