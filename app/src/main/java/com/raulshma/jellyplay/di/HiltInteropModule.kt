package com.raulshma.jellyplay.di

import android.app.Application
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.playback.AudioQueueFacade
import com.raulshma.jellyplay.core.data.repository.StreamingSubtitleStore
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import com.raulshma.jellyplay.feature.music.feedback.MusicMessageBus
import com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Hilt→Koin interop bridge (reverse of the legacy shim's `koin().get()`).
 *
 * SearchViewModel (first V3 conveyor feature) originally reached this way for
 * three ctor deps — MediaRepository, UserDataMutator, MediaSearchEngine —
 * that were still Hilt-constructed pending the Phase X MediaRepository flip.
 * That flip landed: the whole cluster is Koin-owned (dataJvmModule) on both
 * platforms, so those three singles left this module (one framework per
 * type). The Koin-owned ViewModels (search/library/music/livetv/newsletter/
 * insights) now resolve the Koin-owned impls directly.
 *
 * The music conveyor item (third) still reaches this way for its Hilt-owned
 * deps: DownloadIntake / AudioQueueFacade for the ViewModels, plus the
 * UserMessageBus behind the shared module's [MusicMessageBus] seam
 * (HiltMusicMessageBus below).
 *
 * The settings conveyor (Wave 2) originally reached this way for the
 * Hilt-bound AdminRepository too — that single (and its
 * AdminStatisticsRepository sibling) left with the admin flip (Wave wB):
 * both repositories are Koin-owned in dataJvmModule on both platforms now,
 * so the shared settings/admin ViewModels resolve them directly.
 *
 * DownloadRepository left this module with the V3 downloads conveyor: the
 * download engine moved to :shared:core:data and Koin (dataJvmModule) owns
 * the DownloadRepository single directly — a Koin def bridging back to Hilt
 * would be a second framework for the same type, so it was removed (one
 * framework per type). The engine's MediaRepository edge now resolves the
 * Koin-owned single through the androidDataModule's MediaRepositoryAccess def.
 *
 * The PlaybackSourceResolver reverse bridge (added by the Phase X cluster
 * flip, when the impl still used android.net.Uri) left with the
 * playback-flips wave: the impl moved to :shared:core:data Uri-free, so Koin
 * (dataJvmModule) owns it on both platforms and the legacy DataModule
 * bridges Hilt injectors to the single — no reverse direction remains here.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltInteropEntryPoint {
    fun downloadIntake(): DownloadIntake
    fun audioQueueFacade(): AudioQueueFacade
    fun userMessageBus(): UserMessageBus
    fun streamingSubtitleStore(): StreamingSubtitleStore
    fun playerEngineFactory(): PlayerEngineFactory
    fun fontProvider(): FontProvider
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
    single<DownloadIntake> { interopEntryPoint(application).downloadIntake() }
    single<AudioQueueFacade> { interopEntryPoint(application).audioQueueFacade() }
    single<MusicMessageBus> { HiltMusicMessageBus(interopEntryPoint(application).userMessageBus()) }
    // Editor conveyor (ninth feature): the StreamingSubtitleStore interface
    // lives in shared :core:data commonMain but its impl
    // (StreamingSubtitleStoreImpl) stays Hilt-bound in legacy :core:data's
    // SubtitleModule — same lazy interop single as above. The player's
    // Hilt injectors keep constructing the impl directly and are unaffected.
    single<StreamingSubtitleStore> { interopEntryPoint(application).streamingSubtitleStore() }
    // Subtitle-tester conveyor (final feature): the tester's Koin-owned
    // ViewModel ctor-injects the two playback singletons that stay Hilt-bound
    // in :feature:player:video. Lazy is load-bearing here in particular:
    // PlayerEngineFactory owns a process-wide media3 DefaultBandwidthMeter,
    // and FontProvider materializes a font cache on first use — neither may
    // be touched at startKoin time.
    single<PlayerEngineFactory> { interopEntryPoint(application).playerEngineFactory() }
    single<FontProvider> { interopEntryPoint(application).fontProvider() }
}
