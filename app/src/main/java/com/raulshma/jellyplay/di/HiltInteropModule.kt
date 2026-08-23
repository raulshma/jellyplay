package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Hilt→Koin interop bridge (reverse of the legacy shim's `koin().get()`).
 *
 * SearchViewModel (first V3 conveyor feature) is Koin-owned, but three of its
 * ctor deps — MediaRepository, UserDataMutator, MediaSearchEngine — are still
 * Hilt-constructed in the legacy DataModule pending the Phase X
 * DownloadRepository flip. These lazy definitions pull them from the Hilt
 * singleton component on first resolution (the search screen opening), so
 * Hilt stays the sole constructor and there is no cold-start cost.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltInteropEntryPoint {
    fun mediaRepository(): MediaRepository
    fun userDataMutator(): UserDataMutator
    fun mediaSearchEngine(): MediaSearchEngine
}

private fun interopEntryPoint(application: Application): HiltInteropEntryPoint =
    EntryPointAccessors.fromApplication(application, HiltInteropEntryPoint::class.java)

fun hiltInteropModule(application: Application): Module = module {
    single<MediaRepository> { interopEntryPoint(application).mediaRepository() }
    single<UserDataMutator> { interopEntryPoint(application).userDataMutator() }
    single<MediaSearchEngine> { interopEntryPoint(application).mediaSearchEngine() }
}
