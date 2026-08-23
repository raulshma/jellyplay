package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.UserDataMutator
import com.raulshma.jellyplay.core.data.search.MediaSearchEngine
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
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
 *
 * The music conveyor item (third) reaches the same way for its Hilt-owned
 * deps: DownloadRepository / DownloadIntake / AudioQueueFacade for the
 * ViewModels, plus the UserMessageBus behind the shared module's
 * [MusicMessageBus] seam (HiltMusicMessageBus below).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltInteropEntryPoint {
    fun mediaRepository(): MediaRepository
    fun userDataMutator(): UserDataMutator
    fun mediaSearchEngine(): MediaSearchEngine
    fun downloadRepository(): DownloadRepository
    fun downloadIntake(): DownloadIntake
    fun audioQueueFacade(): AudioQueueFacade
    fun userMessageBus(): UserMessageBus
}

private fun interopEntryPoint(application: Application): HiltInteropEntryPoint =
    EntryPointAccessors.fromApplication(application, HiltInteropEntryPoint::class.java)

/** Bridges the shared music module's [MusicMessageBus] seam to the Hilt-owned bus. */
private class HiltMusicMessageBus(
    private val bus: UserMessageBus,
) : MusicMessageBus {
    override fun error(message: String) = bus.error(message)
}

fun hiltInteropModule(application: Application): Module = module {
    single<MediaRepository> { interopEntryPoint(application).mediaRepository() }
    single<UserDataMutator> { interopEntryPoint(application).userDataMutator() }
    single<MediaSearchEngine> { interopEntryPoint(application).mediaSearchEngine() }
    single<DownloadRepository> { interopEntryPoint(application).downloadRepository() }
    single<DownloadIntake> { interopEntryPoint(application).downloadIntake() }
    single<AudioQueueFacade> { interopEntryPoint(application).audioQueueFacade() }
    single<MusicMessageBus> { HiltMusicMessageBus(interopEntryPoint(application).userMessageBus()) }
}
