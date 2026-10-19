package com.raulshma.jellyplay.desktop

import androidx.compose.ui.graphics.painter.Painter
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.repository.JellyPlayLiveResyncConnector
import com.raulshma.jellyplay.core.data.session.JellyPlayEventsSessionController
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.data.worker.DesktopPlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopSettingsSyncScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadSummarySurface
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import com.raulshma.jellyplay.desktop.discord.DiscordPresenceService
import com.raulshma.jellyplay.desktop.hooks.DesktopHookRunner
import com.raulshma.jellyplay.desktop.player.Anime4KShaderInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.KoinApplication

/**
 * Bound on the identity prewarm await — the SAME 5 s Android runs under
 * (JellyPlayApplication's DEVICE_ID_PREWARM_TIMEOUT_MS): a wedged
 * DataStore (corruption, an upstream error stranding the Eagerly-started
 * identity flow on its all-null placeholder) would suspend both
 * ensureDeviceId() — itself a DataStore edit — and a bare first{} forever, so
 * the await runs inside the timeout. On timeout the launch proceeds anyway;
 * DesktopNetworkModule's readers keep their own runBlocking ensureDeviceId()
 * fallback, and DataStore's atomic temp-file write makes a timeout
 * mid-persist safe to abandon.
 */
internal const val DEVICE_ID_PREWARM_TIMEOUT_MS = 5_000L

/**
 * The off-critical-path startup work (Main.kt keeps
 * paths/window/tray/title-bar): the manager starts, the runtime icon decode,
 * and the identity prewarm, all on the Koin application scope.
 *
 * @param koinApp the booted Koin application (scope + singles resolve here).
 * @param onAppIconDecoded receives the decoded runtime icon (Main.kt's
 *   appIconState — the Window/Tray/TitleBar slots pick the painter up on the
 *   recomposition that lands it).
 */
internal fun launchDesktopStartup(
    koinApp: KoinApplication,
    onAppIconDecoded: (Painter?) -> Unit,
) {
    // Downloads + real audio: the download engine (the
    // in-process supervisor observing PENDING rows, plus the 6 h
    // auto-download loop) and the desktop audio core's app-lifetime kickoff
    // (persisted queue/state restore + Room persistence observation, matching
    // Android's Application.onCreate `manager.start()`). Construction is
    // documented side-effect free; every start() launches its loops on the
    // manager's OWN scope and is idempotent.
    //
    // These three gets used to resolve
    // synchronously ON MAIN inside the koin→windowShown segment — Room
    // databaseBuilder().build(), DesktopTokenCipher's key-file read-or-create,
    // and DesktopNetworkMonitor's stateIn(initialValue = probeNetworkStatus())
    // synchronous NIC/address enumeration (slow on Windows with
    // WSL/Hyper-V/VPN adapters). Nothing between here and the first frame
    // needs the instances — the Coil factory defers every Koin resolution to
    // the first image load, and the composition resolves ViewModels lazily;
    // when a VM DOES race this block, Koin's memoized single construction
    // hands it the SAME instance, so identities are unchanged. The whole
    // group therefore constructs and starts on the Koin application scope,
    // off the window's critical path.
    // Start ORDER is preserved exactly (download manager → auto-download
    // scheduler → audio queue manager), and an enqueue racing ahead of
    // start() cannot double-start a transfer: kick() reserves the row via
    // putIfAbsent BEFORE launching (the ExistingWorkPolicy.KEEP equivalent),
    // and start()'s own pending-rows observer re-kicks through that same
    // reservation.
    val appScope = koinApp.koin.get<CoroutineScope>(DatastoreQualifiers.applicationScope)
    appScope
        .launch(Dispatchers.Default) {
            koinApp.koin.get<DesktopDownloadManager>().start()
            koinApp.koin.get<DesktopAutoDownloadScheduler>().start()
            koinApp.koin.get<DesktopAudioQueueManager>().start()
            // Playback-outbox drain (startup pass + Offline→Online
            // transition observer): the desktop actual of Android's
            // PlaybackSyncWorker pair; the drainer lives in shared
            // jvmShared.
            koinApp.koin.get<DesktopPlaybackSyncScheduler>().start()
            // Settings/profile sync flush (startup pass + Offline→Online
            // transition observer + the window-focus edge Main.kt's AWT
            // listener feeds): the desktop actual of Android's
            // SettingsSyncWorker/SettingsSyncBackgroundTrigger pair.
            koinApp.koin.get<DesktopSettingsSyncScheduler>().start()

            // The core:data session machinery: the events SSE face, the
            // live-resync half, and the download summary surface. Resolving
            // and starting them here keeps their Room/WS graph construction
            // off MAIN (createdAtStart would run it inside startKoin,
            // signed-out sessions included).
            // Construction is documented side-effect free; all three start()s
            // are idempotent (JellyPlayEventsSessionController idiom), and
            // resolving them here is identity-neutral — a first composition
            // racing this block gets the SAME memoized instance. Android's
            // equivalent kick lives in AppStartupPrewarms' background-
            // scheduler group.
            koinApp.koin.get<JellyPlayEventsSessionController>().start()
            koinApp.koin.get<JellyPlayLiveResyncConnector>().start()
            koinApp.koin.get<DownloadSummarySurface>().start()

            // Pre-resolve the desktop shell's Koin singles - exactly the
            // set rememberDesktopShellServices resolves during
            // first composition. The list lives in DesktopShellServices.kt
            // NEXT TO the koinInject block it mirrors, so the two sides
            // cannot drift; each entry is identity-neutral (Koin memoizes,
            // so the composition's later koinInject() hands back the SAME
            // instance; when the race goes the other way nothing changes).
            // The point is the two heavyweight stragglers:
            // DesktopNetworkMonitor's stateIn seed runs a synchronous
            // NetworkInterface enumeration (slow on adapter-heavy Windows —
            // the codebase's own comment calls it out), and the websocket
            // client pulls the Room/WS graph. Resolution runs on THIS
            // Default-dispatcher coroutine, off the UI thread's first-frame
            // window.
            desktopShellServicePrewarms.forEach { resolve -> resolve(koinApp.koin) }

            // Warm the mpv software-surface probe: the lazy's
            // first access pays a throwaway mpv create/init/free plus a dll
            // map — "tens to low hundreds of ms" by its own KDoc — and
            // DesktopNavScaffold touches it during first composition, on the
            // UI thread. Poking it here moves that cost onto this dispatcher.
            runCatchingRethrowingCancellation {
                com.raulshma.jellyplay.desktop.player.MpvSoftwareSurfaceSupport.isSupported
            }

            // Extract the bundled Anime4K chains into the user's
            // shaders dir (idempotent; re-runs only on a version bump).
            // Off the critical path and never fatal — until it succeeds the
            // shader-dir provider resolves null and packs are simply absent.
            runCatchingRethrowingCancellation {
                koinApp.koin.get<Anime4KShaderInstaller>().ensureInstalled()
            }

            // Desktop shell integrations: the Discord
            // Rich Presence stack and the playback-event shell hooks.
            // Idempotent start()s on the app scope; each stays inert while
            // its settings toggle is off, and a missing Discord client is
            // just a backed-off retry loop — never fatal.
            runCatchingRethrowingCancellation {
                koinApp.koin.get<DiscordPresenceService>().start()
            }
            runCatchingRethrowingCancellation {
                koinApp.koin.get<DesktopHookRunner>().start()
            }
        }

    // Runtime icon (title bar + tray), decoded OFF the pre-window
    // critical path: desktopAppIconOrNull() is a classpath PNG read + Skia
    // decode — small, but running it during the first composition would put
    // the IO + decode inside the measured koin→windowShown segment (the
    // largest of the small synchronous pre-window reads; the mkdirs/
    // crash-marker/window-state IO above deliberately stays — each is
    // correctness-ordered before use). The state starts null — the icon-less
    // fallback, identical to a null when the resource is unreadable — and
    // the Window/Tray/DesktopTitleBar icon slots pick the painter up on the
    // recomposition that lands it (the decode lands a frame or two after
    // first composition — the intended trade).
    appScope.launch {
        onAppIconDecoded(desktopAppIconOrNull())
    }

    // Desktop identity prewarm: ServerIdentityStore.identity is
    // stateIn(Eagerly) on Dispatchers.Default, and DesktopNetworkModule's
    // `?: runBlocking { ensureDeviceId() }` fallback blocks the resolving
    // thread on a first-launch DataStore read+write whenever the network
    // single wins that race. ensureDeviceId() is idempotent (same UUID
    // either way, DataStore serializes the write), so resolving it here —
    // off the EDT, before anything touches the network module — makes the
    // runBlocking branch unreachable in practice, exactly like the Android
    // Application.onCreate prewarm. Not a plain `identity.first()`: a
    // stateIn(Eagerly) StateFlow always has its initial value available, so
    // a plain first() returns the all-null placeholder instantly during the
    // DataStore-read window and closes nothing; and a bare
    // `first { deviceId != null }` would hang forever on a fresh install
    // (nothing writes DEVICE_ID until ensureDeviceId runs). BOUNDED like
    // Android's: withTimeoutOrNull + awaiting the non-null publication
    // (declared-behavior alignment).
    appScope.launch {
        // Same cancellation-safe wrapper as Android's prewarms: plain
        // runCatching would swallow CancellationException and report a
        // cancelled app scope's shutdown as a mere failed prewarm.
        runCatchingRethrowingCancellation {
            withTimeoutOrNull(DEVICE_ID_PREWARM_TIMEOUT_MS) {
                val store = koinApp.koin.get<ServerIdentityStore>()
                store.ensureDeviceId()
                store.identity.first { !it.deviceId.isNullOrEmpty() }
            }
        }
    }
}
