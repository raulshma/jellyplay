package com.raulshma.jellyplay.feature.settings.di

import org.koin.core.module.Module

/**
 * Platform registration fragment for the settings feature's web seams
 * (the QuickDownloadActions fragment shape, composed into settingsModule via
 * [org.koin.core.module.Module.includes] so the app composition roots keep
 * registering the single `settingsModule`):
 *  - jvmShared actual: binds the `ServerAdminActions` adapter over the
 *    process-wide `AdminRepository` single (dataJvmModule's binding) —
 *    android/desktop behavior unchanged;
 *  - wasmJs actual: binds the honest no-op actions and the no-filesystem
 *    [com.raulshma.jellyplay.feature.settings.SettingsBackupIo] (the browser
 *    shell has no settings storage layout; the null backup-file-picker actual
 *    keeps the export/import rows hidden, so its payload calls are
 *    unreachable).
 *
 * The android/desktop [com.raulshma.jellyplay.feature.settings.SettingsBackupIo]
 * bindings stay in their existing platform modules
 * (androidSettingsPlatformModule / desktopSettingsPlatformModule) — only the
 * wasm graph needs one from this fragment.
 */
internal expect fun platformSettingsModule(): Module
