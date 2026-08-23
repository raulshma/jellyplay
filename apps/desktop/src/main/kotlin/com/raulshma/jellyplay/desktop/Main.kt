package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.raulshma.jellyplay.core.data.di.dataJvmModule
import com.raulshma.jellyplay.core.data.di.desktopDataModule
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.database.di.databaseDaosModule
import com.raulshma.jellyplay.core.database.di.desktopDatabaseModule
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.desktopDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.network.di.desktopNetworkModule
import com.raulshma.jellyplay.core.network.di.networkJvmModule
import com.raulshma.jellyplay.desktop.player.desktopPlayerModule
import com.raulshma.jellyplay.feature.search.di.searchModule
import com.raulshma.jellyplay.feature.library.di.desktopPhotoExportModule
import com.raulshma.jellyplay.feature.library.di.libraryModule
import com.raulshma.jellyplay.feature.music.di.musicModule
import com.raulshma.jellyplay.feature.music.feedback.desktopMusicMessageBusModule
import com.raulshma.jellyplay.feature.livetv.di.liveTvModule
import com.raulshma.jellyplay.feature.downloads.di.downloadsModule
import com.raulshma.jellyplay.feature.syncplay.di.syncPlayModule
import com.raulshma.jellyplay.feature.settings.di.settingsModule
import com.raulshma.jellyplay.feature.settings.di.desktopSettingsPlatformModule
import org.koin.core.context.startKoin

fun main() {
    val paths = DesktopPaths.resolve()
    java.io.File(paths.dataDir.toString()).mkdirs()
    java.io.File(paths.configDir.toString()).mkdirs()

    val koinApp = startKoin {
        modules(
            datastoreCommonModule,
            desktopDatastoreModule(paths.dataDir),
            databaseDaosModule,
            desktopDatabaseModule(paths.databaseFile),
            networkJvmModule,
            desktopNetworkModule(paths.configDir),
            dataJvmModule,
            desktopDataModule(paths.dataDirNio),
            desktopPlayerModule,
            // V3 feature conveyor: search (DI registration; the desktop nav
            // wiring lands later in the conveyor).
            searchModule,
            // …library, second conveyor item — its VM deps resolve lazily, so
            // desktop only needs the (inert) photo-export actual registered.
            libraryModule,
            desktopPhotoExportModule(),
            // …music, third conveyor item — same inert-module pattern: VM deps
            // resolve lazily, desktop only needs the (no-op) message-bus actual.
            musicModule,
            desktopMusicMessageBusModule(),
            // …livetv, fourth conveyor item — documented-latent: VM deps like
            // mediaRepository (Hilt interop) have no desktop definitions yet,
            // but resolution is lazy so boot stays safe (same inert-module
            // pattern; the desktop LiveTvMessenger actual returns null).
            liveTvModule,
            // …downloads, fifth conveyor item — the engine half of the
            // conveyor moved the download stack into :shared:core:data, so
            // desktop single-item downloads now work end-to-end (storage
            // layout under appdata, Range-resumable transfers, the in-process
            // DesktopDownloadManager and the 6 h auto-download loop started
            // below). Series downloads fail loudly until the Phase X
            // MediaRepository flip; the VM's other deps (userDataMutator via
            // Hilt interop) remain documented-latent, so the downloadsModule
            // itself is only resolved once that screen opens.
            downloadsModule,
            // …syncplay, sixth conveyor item — documented-latent: VM deps
            // like mediaRepository (Hilt interop) have no desktop definitions
            // yet (SyncPlayManager/SyncPlayCastStore do resolve from the
            // data/datastore modules), resolution is lazy and the desktop
            // shell has no SyncPlay nav entry, so the module is registered
            // but never instantiated (same inert-module pattern as livetv).
            syncPlayModule,
            // …settings, seventh conveyor item — documented-latent, syncplay
            // pattern: several VM deps (MediaRepository, AdminRepository —
            // Hilt interop) have no desktop definitions, but resolution is
            // lazy so boot stays safe, and the desktop shell has no settings
            // nav entry (grep confirms no route/screen reference), so the
            // module is registered but never instantiated. Only the (no-op
            // /zero-degradation) desktop platform actuals resolve eagerly.
            settingsModule,
            desktopSettingsPlatformModule(),
        )
    }

    // V3 downloads conveyor: the desktop download engine — in-process
    // supervisor observing PENDING rows (resume + reconnect edge handled
    // inside), plus the auto-download loop. Construction is side-effect free;
    // start() launches the loops on the application scope and is idempotent.
    koinApp.koin.get<DesktopDownloadManager>().start()
    koinApp.koin.get<DesktopAutoDownloadScheduler>().start()

    // Desktop image engine: the OkHttp network fetcher self-registers via
    // ServiceLoader from the coil-network-okhttp dependency; crossfade is the
    // only tweak needed on top.
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it).crossfade(true).build()
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 800.dp)
        val showAbout = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        Window(
            state = windowState,
            title = "JellyPlay",
            onCloseRequest = ::exitApplication,
        ) {
            MenuBar {
                Menu("File") {
                    Item("Refresh", shortcut = KeyShortcut(Key.R, ctrl = true)) {
                        // Wired to home refresh when the V1c slice lands.
                    }
                    Separator()
                    Item("Exit", shortcut = KeyShortcut(Key.Q, ctrl = true)) {
                        exitApplication()
                    }
                }
                Menu("View") {
                    Item(
                        if (windowState.placement == WindowPlacement.Fullscreen) "Exit Fullscreen" else "Fullscreen",
                        shortcut = KeyShortcut(Key.F11),
                    ) {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                            else WindowPlacement.Fullscreen
                    }
                }
                Menu("Help") {
                    Item("About JellyPlay") { showAbout.value = true }
                }
            }

            JellyPlayTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
                DesktopAppRoot(showAbout = showAbout.value, onDismissAbout = { showAbout.value = false })
            }
        }
    }
}
