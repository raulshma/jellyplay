package com.raulshma.jellyplay.desktop

import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.data.di.desktopDataModule
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.database.di.desktopDatabaseModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.desktopDatastoreModule
import com.raulshma.jellyplay.core.network.di.desktopNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.core.ui.di.coreUiMessageModule
import com.raulshma.jellyplay.desktop.player.desktopPlayerModule
import com.raulshma.jellyplay.desktop.update.desktopAppUpdateModule
import com.raulshma.jellyplay.feature.details.desktopDetailsPlatformModule
import com.raulshma.jellyplay.feature.music.feedback.desktopMusicMessageBusModule
import com.raulshma.jellyplay.feature.settings.di.desktopSettingsPlatformModule
import com.raulshma.jellyplay.feature.auth.di.desktopAuthPlatformModule
import com.raulshma.jellyplay.feature.book.di.desktopBookPlayerModule
import com.raulshma.jellyplay.feature.library.di.desktopPhotoExportModule
import com.raulshma.jellyplay.feature.player.video.di.desktopPlayerVideoModule
import com.raulshma.jellyplay.feature.shell.sharedFeatureModules
import org.koin.core.module.Module

/**
 * The desktop shell's startKoin module list (extracted from Main.kt): the
 * shared core graph, this shell's platform actuals, and the ONE spread of
 * [sharedFeatureModules] (the shared feature declaration in
 * shared/feature/shell — both JVM shells consume it; the per-module
 * conveyor history rides that declaration).
 *
 * Koin 4 dropped the per-definition override flag; Main.kt runs its startKoin
 * with `allowOverride(true)` for exactly ONE deliberate replacement — the
 * [desktopAppUpdateModule] at the END of this list REPLACES
 * [desktopDataModule]'s sentinel-bound AppUpdateRepository single with the
 * real-version desktop auto-update actual (docs/adr/desktop-auto-update.md).
 * Loaded last so it wins; the KoinModuleRegistrationGuardTest ratchets every
 * other registration.
 */
internal fun desktopKoinModules(paths: DesktopPaths): List<Module> = listOf(
    datastoreCommonModule,
    desktopDatastoreModule(paths.dataDir),
    databaseDaosModule,
    desktopDatabaseModule(paths.databaseFile),
    networkJvmModule,
    desktopNetworkModule(paths.configDir),
    dataJvmModule,
    desktopDataModule(paths.dataDirNio),
    desktopPlayerModule,
    // Video player (registration, playback): the VideoPlayerViewModel is
    // commonMain and live-resolvable here, the SwingPanel/HWND video surface
    // composes inside Route.VideoPlayer, and desktopPlayerModule supplies the
    // per-session mpv PlayerEngineFactory binding (this module deliberately
    // does not — MpvDesktopEngine is an app-layer type). Windows only:
    // DesktopAppRoot keeps Route.VideoPlayer dead-end-guarded on other OSes
    // where no embedded surface exists. The no-op seam bindings in the module
    // still cover those guarded OSes.
    desktopPlayerVideoModule,
    // The shared commonMain feature Koin modules (declared ONCE in
    // shared/feature/shell; the guard test derives its expected set from
    // that declaration — webFeatureModules precedent). Desktop's platform
    // actuals follow inline.
    // The shared list spreads as its typed array — `listOf`'s vararg
    // takes arrays, not lists.
    *sharedFeatureModules.toTypedArray(),
    desktopPhotoExportModule(),
    desktopMusicMessageBusModule(),
    desktopSettingsPlatformModule(
        dataDir = paths.dataDirNio,
        configDir = paths.configDirNio,
        imageCache = desktopCoilImageCacheOps(),
    ),
    desktopDetailsPlatformModule(paths.dataDirNio),
    desktopAuthPlatformModule,
    // core:ui's UserMessageBus module — core, not feature, so it stays
    // inline (the shell's UserMessageHost collects this bus).
    coreUiMessageModule,
    desktopBookPlayerModule(paths.dataDir),

    // ── Desktop auto-update (ADR desktop-auto-update) ────────────────
    // DELIBERATE OVERRIDE (the only one; see this file's KDoc on
    // allowOverride): replaces desktopDataModule's sentinel-bound
    // AppUpdateRepository (`999999.0.0` — isUpdateAvailable could never
    // fire) with the real-version desktop actual. The installed version
    // comes from the generated desktop-build.properties (channel=release
    // only on CI release lanes); dev builds stay "up to date" by
    // construction, and an available update opens the release page in the
    // user's browser (DesktopAppRoot's About row) — never a silent install.
    // Last in the list so the later definition wins the mapping.
    desktopAppUpdateModule(paths.dataDirNio),

    // …subtitle-tester, the FINAL conveyor feature, deliberately has NO
    // registration here: the entire feature (ViewModel, screen, preview
    // engine host, raw-asset factory) lives in the shared module's
    // androidMain — its engine factory and font provider actuals are
    // Android-only (Koin-owned since then) with no desktop halves — so there
    // is no commonMain Koin module to register. The shared settings-search
    // row for Route.SubtitleTester stays unreachable on desktop
    // (LanguageSettings' push is intercepted by the guard); the guard's
    // dead-end set is DERIVED from the shared shell graph's registration
    // ledger (ShellSectionRegistry in DesktopAppRoot) — a route dead-ends
    // exactly when no registered section owns it, so there is no hand-kept
    // list to sync when features gain or change pushed routes.
)
