package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.ui.components.JellyPlayPreferenceTheme
import com.raulshma.jellyplay.core.ui.components.rememberPreferenceDarkTheme
import com.raulshma.jellyplay.desktop.harness.DesktopFlowHarness

import org.koin.compose.koinInject
import org.koin.core.context.startKoin

fun main() {
    //  startup baseline: t0 is the literal first statement so every
    // mark below measures against true process start. Marks themselves are
    // AtomicLong writes (~zero cost); everything heavier (JSON emission,
    // auto-exit timer) only arms when a jellyplay.perf.* property is set —
    // see DesktopStartupPerf.
    val bootT0Nanos = System.nanoTime()

    // e2e click-reach bridge (HarnessClickBridge): armed BEFORE any screen
    // composes so the flows lane's Modifier.harnessClickTarget annotations
    // publish bounds from the very first frame. Zero cost on every normal
    // boot (the flag stays false; nothing else reads it).
    com.raulshma.jellyplay.core.ui.harness.HarnessClickBridge.enabled =
        System.getProperty(DesktopFlowHarness.PROP_ENABLED)
            ?.equals("true", ignoreCase = true) == true

    val paths = DesktopPaths.resolve()
    java.io.File(paths.dataDir.toString()).mkdirs()
    java.io.File(paths.configDir.toString()).mkdirs()

    // Bundled libmpv (packaged builds): jpackage installs the app-resources
    // dir (fetchBundledLibmpv's windows-x64 subtree, apps/desktop/
    // build.gradle.kts) next to the jars and the Compose launcher exposes it
    // as compose.application.resources.dir — point JNA at it so playback
    // works with zero user setup. Dev `gradlew run` covers the same need via
    // the run task's jna.library.path, and an explicit MPV_LIBRARY env still
    // wins (MpvLib.load checks it before JNA's search path).
    System.getProperty("compose.application.resources.dir")?.let { resourcesDir ->
        if (java.io.File(resourcesDir, "libmpv-2.dll").isFile) {
            System.setProperty("jna.library.path", resourcesDir)
        }
    }

    //  crash scaffold: hooks the JVM-wide uncaught-exception handler
    // BEFORE anything that can throw (Koin graph, player engines, compose
    // window), then consumes the previous session's crash marker — if the
    // last run recorded an uncaught throwable, log it here and surface a
    // one-line note + log path in the About dialog via DesktopAppRoot.
    val crashHandler = DesktopCrashHandler(logsDir = paths.logsDirNio).install()
    val previousCrash = crashHandler.consumePreviousCrashMarker()
    if (previousCrash != null) {
        println(
            "[JellyPlay] previous session ended unexpectedly; " +
                "crash log: ${previousCrash.logFile} (${previousCrash.crashedAtUtc})",
        )
    }

    val startupPerf = DesktopStartupPerf(
        logsDirNio = paths.logsDirNio,
        bootT0Nanos = bootT0Nanos,
    )
    startupPerf.scheduleMeasurementHooksIfRequested()

    val koinApp = startKoin {
        // Koin 4 dropped the per-definition override flag; the global switch
        // exists for exactly ONE deliberate replacement — the
        // desktopAppUpdateModule at the END of desktopKoinModules' list
        // REPLACES desktopDataModule's sentinel-bound AppUpdateRepository
        // single with the real-version desktop auto-update actual
        // (docs/adr/desktop-auto-update.md). Loaded last so it wins; the
        // KoinModuleRegistrationGuardTest ratchets every other registration.
        allowOverride(true)
        // The module list itself is DesktopKoinModules.kt (the fold): the
        // shared core graph, this shell's platform actuals, and the ONE
        // spread of sharedFeatureModules both JVM shells consume. Startup
        // ORDER below is unchanged — Koin → off-critical-path starts →
        // image loader → window.
        modules(desktopKoinModules(paths))
    }

    //  startup mark: Koin graph construction is the first heavy
    // milestone of boot (module list above is untouched — no reordering).
    startupPerf.markKoinStarted()

    // Off-critical-path startup work this main() used to inline (the fold's
    // extraction — Main.kt keeps paths/window/tray/title-bar): the
    // download/audio manager starts (the documented start ORDER is preserved
    // inside), the runtime icon decode, and the desktop identity prewarm —
    // all in DesktopStartup over the Koin application scope.
    val appIconState = mutableStateOf<Painter?>(null)
    launchDesktopStartup(koinApp, onAppIconDecoded = { appIconState.value = it })

    // Desktop image engine (extracted): the shared JVM Coil builder policy —
    // core:data's jellyPlayImageLoader (serviceLoaderEnabled(false), OkHttp
    // fetcher over the Koin-owned STREAMING client, crossfade, and the
    // lazily-sized ImageCache.DIR disk cache rooted at <configDir>) — over
    // this shell's two divergences. See DesktopImageLoader.
    installDesktopImageLoader(koinApp, configDir = paths.configDir)

    // Window placement memory: restore the last session's floating bounds
    // when they're still reachable on the CURRENT monitor setup (sanitize
    // rejects the docked-laptop-then-undocked case), else launch centered.
    // This replaces the old always-1280x800-at-OS-default placement — an
    // undecorated frame gets the raw Windows cascade (top-left, stepped),
    // never the centered position a decorated frame's dialog logic gives.
    // The placement DANCE (manual maximize, last-session replay,
    // persist-on-dispose) lives in DesktopWindowPlacementController below.
    val windowStateStore = DesktopWindowStateStore(
        paths.configDirNio.resolve("window-state.properties"),
    )
    val savedWindowGeometry = windowStateStore.load()
        ?.let { DesktopWindowStateStore.sanitize(it, DesktopWindowStateStore.availableScreens()) }
    val pxToDp = DesktopWindowPlacementController.pxToDpFactor()

    application {
        val windowState = rememberWindowState(
            position = savedWindowGeometry
                ?.let { WindowPosition((it.x / pxToDp).dp, (it.y / pxToDp).dp) }
                ?: WindowPosition(Alignment.Center),
            width = ((savedWindowGeometry?.width ?: DEFAULT_WINDOW_WIDTH_PX) / pxToDp).dp,
            height = ((savedWindowGeometry?.height ?: DEFAULT_WINDOW_HEIGHT_PX) / pxToDp).dp,
        )
        val showAbout = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        // Runtime icon for the title bar + tray (packaging icons are
        // NOT on the runtime classpath — see DesktopAppIcon). The decode was
        // kicked off BEFORE `application {}` on the Koin application scope
        // (see launchDesktopStartup above); reading .value here subscribes
        // this composition, so the painter pops in on the recomposition that
        // lands it — and a null keeps the pre-existing icon-less fallback
        // (unreadable resource, or the decode simply hasn't landed yet).
        val appIcon = appIconState.value
        // AWT-side window handle so the tray's Show action can restore/focus
        // the ComposeWindow from outside the Window content lambda.
        val windowRef = remember {
            java.util.concurrent.atomic.AtomicReference<ComposeWindow?>(null)
        }

        // Startup marks. windowShownNanos is the AWT-authoritative
        // visibility event. firstFrameNanos resumes when the frame clock
        // delivers the first frame after this root content applies its initial
        // composition — DesktopAppRoot composes inside this same pass, so it is
        // the same frame boundary without threading a callback through
        // DesktopAppRoot (≤1-frame slop vs a true "painted" hook; documented).
        LaunchedEffect(startupPerf) {
            withFrameNanos { /* resume at first produced frame */ }
            startupPerf.markFirstFrame(System.nanoTime())
        }

        // File→Refresh (Ctrl+R) signal into DesktopAppRoot. The scaffold dispatches
        // it to whatever pull-to-refresh screen is active via
        // LocalPullToRefreshRegistry (see DesktopNavScaffold).
        // extraBufferCapacity=1 keeps tryEmit non-suspending and coalesces
        // repeat invocations while a refresh is already running.
        val menuRefreshRequests = remember {
            kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        }

        // F11 fullscreen toggle — shared by the window key handler and the
        // title bar's View menu (the old MenuBar item's exact behavior).
        val toggleFullscreen = {
            windowState.placement =
                if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating
                else WindowPlacement.Fullscreen
        }

        Window(
            state = windowState,
            title = "JellyPlay",
            icon = appIcon,
            undecorated = true,
            onCloseRequest = ::exitApplication,
            // Keyboard accelerators for the DesktopTitleBar menus. The old
            // AWT MenuBar delivered these natively even without Compose
            // focus; the window-level preview handler is the closest
            // equivalent — it sees every key before the Compose focus chain
            // (and also when nothing is focused), then declines everything
            // it doesn't own so route-level handlers (Esc/back, media keys
            // in DesktopAppRoot) are unaffected.
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    // Matching folds through the DesktopAccelerator table (the
                    // same rows the title bar menus render); the effects stay
                    // here. Non-owners decline so route-level handlers
                    // (Esc/back, media keys in DesktopAppRoot) are unaffected.
                    when (DesktopAccelerators.match(event.key, event.isCtrlPressed)?.action) {
                        DesktopAcceleratorAction.Refresh -> {
                            menuRefreshRequests.tryEmit(Unit)
                            true
                        }
                        DesktopAcceleratorAction.Exit -> {
                            exitApplication()
                            true
                        }
                        DesktopAcceleratorAction.ToggleFullscreen -> {
                            toggleFullscreen()
                            true
                        }
                        null -> false
                    }
                }
            },
        ) {
            // The window-placement dance — the manual maximize for the
            // undecorated frame (WindowPlacement.Maximized / AWT
            // MAXIMIZED_BOTH is unusable on a WS_POPUP window), the
            // last-session maximize replay, and the persist-on-dispose
            // decision — lives in DesktopWindowPlacementController over the
            // AWT window; this shell only constructs it and calls it from the
            // listener/effects below (its five rules are pinned by
            // DesktopWindowPlacementControllerTest).
            val placementController = remember {
                DesktopWindowPlacementController(
                    host = AwtDesktopWindowPlacementHost(window),
                    stateStore = windowStateStore,
                    savedMaximized = savedWindowGeometry?.maximized == true,
                )
            }

            DisposableEffect(startupPerf) {
                val composeWindow = window
                windowRef.set(composeWindow)
                val shownListener = object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(e: java.awt.event.WindowEvent?) {
                        startupPerf.markWindowShown()
                        composeWindow.removeWindowListener(this)
                    }
                }
                composeWindow.addWindowListener(shownListener)
                onDispose {
                    composeWindow.removeWindowListener(shownListener)
                    windowRef.compareAndSet(composeWindow, null)
                }
            }

            // Last-session replay of the manual maximize: rememberWindowState
            // above only restores the FLOATING bounds; when the previous
            // session closed maximized, the controller redoes the bounds
            // swap once the AWT window exists (windowOpened, same timing the
            // perf marks rely on — applying bounds mid-composition would
            // fight the initial pack()). The listener self-removes like the
            // perf one above and may be attached unconditionally — the
            // controller's replay is once-only and inert without a saved
            // maximize.
            DisposableEffect(placementController) {
                val maximizeListener = object : java.awt.event.WindowAdapter() {
                    override fun windowOpened(e: java.awt.event.WindowEvent?) {
                        window.removeWindowListener(this)
                        placementController.onWindowOpened()
                    }
                }
                window.addWindowListener(maximizeListener)
                onDispose { window.removeWindowListener(maximizeListener) }
            }

            // Persist the floating geometry on window teardown. This covers
            // every exit path (title-bar close, Ctrl+Q, tray Quit — all
            // funnel into exitApplication, which disposes the composition).
            // The skip-fullscreen and prefer-restore-bounds rules live in the
            // controller; the placement flag is read HERE because
            // WindowState is shell wiring, not window geometry.
            DisposableEffect(placementController) {
                onDispose {
                    placementController.persistOnDispose(
                        isFullscreen = windowState.placement == WindowPlacement.Fullscreen,
                    )
                }
            }

            val toggleMaximize: () -> Unit = {
                placementController.toggleMaximize()
            }

            // Preference-driven theming, the shared wrapper the Android
            // Activities use (JellyPlayPreferenceTheme): every appearance pref —
            // theme variant (Synthwave/Aurora/Sakura/…), theme mode (DARK/LIGHT/
            // SYSTEM/SCHEDULED), OLED, contrast, accent swatch, font scale —
            // resolves from PreferenceProjections.mainPreferences (Koin single,
            // datastoreCommonModule), so the desktop Settings appearance rows
            // re-theme the window live. Material You dynamicTheming is a no-op
            // seam on desktop (dynamicPlatformColorScheme returns null there and
            // the scheme cascade falls through to the brand palettes).
            val projections: PreferenceProjections = koinInject()
            val preferences by projections.mainPreferences.collectAsState()
            val darkTheme = rememberPreferenceDarkTheme(preferences)
            JellyPlayPreferenceTheme(preferences = preferences, darkTheme = darkTheme) {
                // Themed fallback surface: the undecorated window has no
                // native chrome, so without this the pre-theme AWT white
                // would flash through the splash/signed-out panes in dark
                // mode.
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    // The custom title bar replaces the OS caption (theme
                    // mismatch) AND the AWT MenuBar (an undecorated frame has
                    // no menu strip on Windows — the menus moved into the
                    // bar, see DesktopTitleBar). Hidden in true fullscreen so
                    // F11 video playback gets the whole screen.
                    if (windowState.placement != WindowPlacement.Fullscreen) {
                        DesktopTitleBar(
                            icon = appIcon,
                            isMaximized = placementController.isMaximized,
                            onMinimize = { windowState.isMinimized = true },
                            onToggleMaximize = toggleMaximize,
                            onClose = ::exitApplication,
                            onRefresh = { menuRefreshRequests.tryEmit(Unit) },
                            onExit = ::exitApplication,
                            onToggleFullscreen = toggleFullscreen,
                            isFullscreenActive = windowState.placement == WindowPlacement.Fullscreen,
                            onAbout = { showAbout.value = true },
                        )
                    }
                    DesktopAppRoot(
                        showAbout = showAbout.value,
                        onDismissAbout = { showAbout.value = false },
                        previousCrashLogPath = previousCrash?.logFile?.toString(),
                        //  session harness only (screenshots + key
                        // injection); unused on every normal boot path.
                        windowRef = windowRef,
                        menuRefreshRequests = menuRefreshRequests,
                    )
                }
            }
        }

        //  tray affordance. STRICTLY ADDITIVE semantics: closing the
        // window still quits (onCloseRequest above is unchanged) — there is no
        // hide-to-tray behavior here. Skipped entirely when the runtime icon
        // failed to load or AWT exposes no system tray (headless/locked-down
        // sessions; CMP's isTraySupported() is metadata-internal at 1.11.1, so
        // availability is probed via systemTrayAvailable(), see DesktopAppIcon).
        if (appIcon != null && systemTrayAvailable()) {
            Tray(
                icon = appIcon,
                tooltip = "JellyPlay",
                menu = {
                    Item("Show JellyPlay") {
                        // Tray item clicks come back through AWT menu
                        // machinery; compose desktop's own composition runs on
                        // that same AWT event thread, and this hop costs one
                        // loop turn while guaranteeing every future listener
                        // variant stays on-thread.
                        // Window restore/focus itself lives in DesktopTrayActions
                        // (extraction — null path unit-covered; the
                        // visual restore still needs a one-time manual eyeball,
                        // see docs/perf notes + gate report).
                        java.awt.EventQueue.invokeLater {
                            windowState.isMinimized = false
                            DesktopTrayActions.showMainWindow(windowRef.get())
                        }
                    }
                    Item("Quit") { DesktopTrayActions.quit { exitApplication() } }
                },
            )
        }
    }
}

/**
 * First-launch window size (no saved state) in AWT px — 1280x800dp at 100%
 * (scaled through [DesktopWindowPlacementController.pxToDpFactor], where the
 * px↔dp conversion of the placement system lives).
 */
private const val DEFAULT_WINDOW_WIDTH_PX = 1280
private const val DEFAULT_WINDOW_HEIGHT_PX = 800
