package com.raulshma.jellyplay.di

import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.mp.KoinPlatform
import javax.inject.Singleton

/**
 * Hilt→Koin bridge for the settings-search catalog (V3 settings conveyor).
 *
 * The catalog moved to :shared:feature:settings with the feature, and Koin
 * owns it (settingsModule's SettingsSearchProvider single — the object
 * SettingsSearchCatalog). The former feature/settings SettingsSearchModule
 * @Provides died with the legacy module, but feature/home's HomeViewModel
 * still Hilt-injects the [SettingsSearchProvider] seam, so this app-side
 * module (the composition root sees both frameworks — SyncPlayManager
 * bridge pattern) points Hilt at the Koin single: one instance, one owner.
 *
 * Lives here rather than in the legacy core:data DataModule next to the
 * other koin().get() bridges because core:data has no :core:ui edge, while
 * the app does.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsSearchInteropModule {

    @Singleton
    @Provides
    fun provideSettingsSearchProvider(): SettingsSearchProvider =
        KoinPlatform.getKoin()?.get()
            ?: error("Koin not started — startKoin must run before super.onCreate()")
}
