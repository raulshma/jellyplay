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
import com.raulshma.jellyplay.desktop.integration.desktopIntegrationModule
import com.raulshma.jellyplay.desktop.player.desktopPlayerModule
import com.raulshma.jellyplay.desktop.update.DesktopInstalledVersion
import com.raulshma.jellyplay.desktop.update.desktopAppUpdateModule
import com.raulshma.jellyplay.feature.details.desktopDetailsPlatformModule
import com.raulshma.jellyplay.feature.music.feedback.desktopMusicMessageBusModule
import com.raulshma.jellyplay.feature.settings.di.desktopSettingsPlatformModule
import com.raulshma.jellyplay.feature.auth.di.desktopAuthPlatformModule
import com.raulshma.jellyplay.feature.book.di.desktopBookPlayerModule
import com.raulshma.jellyplay.feature.photos.di.desktopPhotoExportModule
import com.raulshma.jellyplay.feature.player.video.di.desktopPlayerVideoModule
import com.raulshma.jellyplay.feature.shell.sharedFeatureModules
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The desktop shell's startKoin module list (extracted from Main.kt): the
 * shared core graph, this shell's platform actuals, and the ONE spread of
 * [sharedFeatureModules] (the shared feature declaration in
 * shared/feature/shell — both JVM shells consume it; the per-module
 * conveyor history rides that declaration).
 *
 * The graph loads under Koin's default no-override policy — Main.kt's
 * startKoin sets no allowOverride and every definition is keyed and unique.
 * The desktop auto-update binding ([desktopAppUpdateModule]) is the ONE
 * `AppUpdateRepository` definition: core:data's desktopDataModule ships no
 * update family, so there is no sentinel to replace and no
 * later-module-wins dance (docs/adr/desktop-auto-update.md). Its installed
 * version rides the [DesktopInstalledVersion] single bound beside
 * [DesktopPaths] below.
 */
internal fun desktopKoinModules(paths: DesktopPaths): List<Module> = listOf(
    // The resolved bundle itself, for platform modules that resolve the full
    // paths object via `get<DesktopPaths>()` (desktopPlayerModule's
    // PlayerEngineFactory + Anime4KShaderInstaller); the modules below take
    // their path slices as parameters instead.
    module { single { paths } },
    // The installed-version classification for the desktop auto-update
    // binding (desktopAppUpdateModule resolves it): read once from the
    // generated desktop-build.properties classpath resource — the same
    // resource the About screen's DesktopAppMetaProvider reads. CI
    // release-lane builds classify Release and compare against the GitHub
    // feed for real; every dev/IDE build classifies DevBuild and stays
    // "up to date" by construction (docs/adr/desktop-auto-update.md).
    module { single { DesktopInstalledVersion.read() } },
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
    // that declaration). Desktop's platform
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
    // The companion-plugin's live broadcast events (ADR 0010) surface as
    // one-shot user messages over that same bus (the text is the session
    // controller's title/body fold — server-supplied, UiText.Raw path).
    // Same core:data-cannot-see-core:ui bridge shape as the Android shell's
    // interop-adapter module.
    module {
        single<com.raulshma.jellyplay.core.data.session.JellyPlayBroadcastMessenger> {
            val bus: com.raulshma.jellyplay.core.ui.message.UserMessageBus = get()
            com.raulshma.jellyplay.core.data.session.JellyPlayBroadcastMessenger { text ->
                bus.info(text)
            }
        }
    },
    desktopBookPlayerModule(paths.dataDir),

    // ── Desktop auto-update (ADR desktop-auto-update) ────────────────
    // The ONE AppUpdateRepository definition in this graph — no override:
    // core:data's desktopDataModule ships no update family, so Main.kt's
    // startKoin runs under Koin's default no-override policy. Resolves the
    // DesktopInstalledVersion single above (release-lane builds report
    // genuine newer releases from the GitHub feed; dev builds stay "up to
    // date" by construction), and an available update opens the release
    // page in the user's browser (DesktopAppRoot's About row) — never a
    // silent install. List position is inert (definitions are keyed); it
    // sits here with the rest of this shell's own sections.
    desktopAppUpdateModule(paths.dataDirNio),

    // ── Desktop shell integrations (features 4.2 + 4.3) ─────────────
    // The Discord Rich Presence stack (hand-rolled DiscordIpcClient +
    // DiscordPresenceService over the shared NowPlayingReporter spine) and
    // the playback-event shell hooks (DesktopHookRunner). Both start from
    // launchDesktopStartup (idempotent; the settings toggles gate the
    // behavior) and live here because every collaborator is a desktop-shell
    // or shared-graph type. List position is inert (definitions are keyed).
    desktopIntegrationModule(),

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
