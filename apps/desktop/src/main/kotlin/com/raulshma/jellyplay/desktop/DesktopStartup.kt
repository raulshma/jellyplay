package com.raulshma.jellyplay.desktop

import androidx.compose.ui.graphics.painter.Painter
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.worker.DesktopAutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DesktopDownloadManager
import com.raulshma.jellyplay.core.data.worker.DesktopPlaybackSyncScheduler
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.data.playback.DesktopAudioQueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.KoinApplication

/**
 * Bound on the identity prewarm await — the SAME 5 s the Android twin runs
 * under (JellyPlayApplication's DEVICE_ID_PREWARM_TIMEOUT_MS): a wedged
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
 * The off-critical-path startup work Main.kt used to inline (extracted;
 * Main.kt keeps paths/window/tray/title-bar): the manager starts, the runtime
 * icon decode, and the identity prewarm, all on the Koin application scope.
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
    // V3 downloads conveyor + real audio: the download engine (the
    // in-process supervisor observing PENDING rows, plus the 6 h
    // auto-download loop) and the desktop audio core's app-lifetime kickoff
    // (persisted queue/state restore + Room persistence observation — the
    // Android Application.onCreate `manager.start()` twin). Construction is
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
            // PlaybackSyncWorker pair, now that the drainer lives in
            // shared jvmShared — previously desktop staged outbox rows
            // that nothing ever drained.
            koinApp.koin.get<DesktopPlaybackSyncScheduler>().start()
        }

    // Runtime icon (title bar + tray), decoded OFF the pre-window
    // critical path: desktopAppIconOrNull() is a classpath PNG read + Skia
    // decode — small, but it used to run inside a remember {} during the
    // first composition, so the IO + decode sat inside the measured
    // koin→windowShown segment (the largest of the small synchronous
    // pre-window reads; the mkdirs/crash-marker/window-state IO above
    // deliberately stays — each is correctness-ordered before use). The
    // state starts null — the EXISTING icon-less fallback, identical to a
    // null when the resource is unreadable — and the Window/Tray/
    // DesktopTitleBar icon slots pick the painter up on the recomposition
    // that lands it (a frame or two later than the old inline decode — the
    // intended trade).
    appScope.launch {
        onAppIconDecoded(desktopAppIconOrNull())
    }

    // Desktop twin of the identity prewarm: ServerIdentityStore.identity is
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
    // (nothing writes DEVICE_ID until ensureDeviceId runs). BOUNDED like the
    // Android twin (withTimeoutOrNull + awaiting the non-null publication —
    // declared-behavior alignment; this used to be an unbounded
    // fire-and-forget ensureDeviceId() call, the drift this fold fixes).
    appScope.launch {
        // Same cancellation-safe wrapper as the Android twin's prewarms: plain
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
