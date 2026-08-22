package com.raulshma.jellyplay.core.datastore.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    /**
     * Phase C4: Koin owns the scope (datastoreCommonModule's
     * DatastoreQualifiers.applicationScope single — SupervisorJob() +
     * Dispatchers.Default); this bridge keeps both frameworks on ONE scope
     * instance.
     */
    @ApplicationScope
    @Singleton
    @Provides
    fun provideApplicationCoroutineScope(): CoroutineScope =
        koin().get(DatastoreQualifiers.applicationScope)
}
