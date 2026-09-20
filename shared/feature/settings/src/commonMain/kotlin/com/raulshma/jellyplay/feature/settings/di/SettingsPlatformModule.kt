package com.raulshma.jellyplay.feature.settings.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the settings feature's platform seams
 * (the QuickDownloadActions fragment shape, composed into settingsModule via
 * [org.koin.core.module.Module.includes] so the app composition roots keep
 * registering the single `settingsModule`):
 *  - jvmShared actual: binds the `ServerAdminActions` adapter over the
 *    process-wide `AdminRepository` single (dataJvmModule's binding) —
 *    android/desktop behavior unchanged.
 *
 * The android/desktop [com.raulshma.jellyplay.feature.settings.SettingsBackupIo]
 * bindings stay in their existing platform modules
 * (androidSettingsPlatformModule / desktopSettingsPlatformModule).
 */
internal expect fun platformSettingsModule(): Module
