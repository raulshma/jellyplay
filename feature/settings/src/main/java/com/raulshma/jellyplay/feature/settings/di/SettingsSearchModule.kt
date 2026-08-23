package com.raulshma.jellyplay.feature.settings.di

import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.feature.settings.SettingsSearchCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the feature/settings catalog behind the core/ui
 * [SettingsSearchProvider] seam. The binding lives here (not in core/ui)
 * because the item lists live next to this module's screens; consumers such
 * as feature/home depend only on core/ui and receive the real catalog when
 * the app-level component assembles both feature modules.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsSearchModule {

    @Singleton
    @Provides
    fun provideSettingsSearchProvider(): SettingsSearchProvider = SettingsSearchCatalog
}
