package com.raulshma.jellyplay.startup

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.worker.AutoDownloadScheduler
import com.raulshma.jellyplay.core.data.worker.DownloadReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncReconnectListener
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.UserDataSyncScheduler
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.notification.scheduler.NotificationReconnectListener
import com.raulshma.jellyplay.core.notification.scheduler.NotificationScheduler
import com.raulshma.jellyplay.feature.player.video.engine.VideoStreamCache
import com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider
import com.raulshma.jellyplay.widget.NowPlayingWidgetUpdater
import com.raulshma.jellyplay.widget.WidgetWorkScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Bound on the device-id prewarm await; see the launch in [start]. */
private const val DEVICE_ID_PREWARM_TIMEOUT_MS = 5_000L

/**
 * Bound on the persisted-security-slice prewarm — DELIBERATELY the
 * same constant the lock-gate reader it exists to warm runs under
 * ([PlayerActivity.APP_LOCK_GATE_READ_TIMEOUT_MS]), so the prewarm can
 * never wait on the persisted read longer than the gate itself.
 */
private const val SECURITY_SLICE_PREWARM_TIMEOUT_MS =
    com.raulshma.jellyplay.PlayerActivity.APP_LOCK_GATE_READ_TIMEOUT_MS

/**
 * Defers the audio/widget group and the background-scheduler group in
 * [start] out of the t=0 first-frame window — the same idiom (and
 * duration) as SettingsSearchCatalogPrewarmer's default-pass deferral:
 * two seconds in, the first frame has long landed and both groups run
 * uncontended. Every WorkManager enqueue in the scheduler group is
 * KEEP-idempotent, so the deferral changes no semantics.
 */
private const val BACKGROUND_START_DEFERRAL_MS = 2_000L

/**
 * The cold-start prewarm choreography. Extracted out of `JellyPlayApplication`
 * so the Application class *composes* startup steps rather than *containing*
 * them (the [CacheMaintenanceInitializer]/[DownloadRecoveryInitializer]
 * idiom): [start] fires the four launch groups, in the same order, onto the
 * same application scope — this is a locality-only extraction, nothing starts
 * earlier or later than when these blocks lived in `onCreate`.
 *
 * Every collaborator arrives as a memoizing [Lazy] — the same deferred access
 * the former `by lazyFromKoin()` Application fields provided (Hilt removal):
 * definitions are lazy, each resolved single is the same memoized instance
 * the rest of the graph sees, and construction is deferred off the main
 * thread until the IO launch block actually resolves the lazy. That deferral
 * is load-bearing per collaborator; the comments above each parameter are the
 * former field comments.
 *
 * Group order (pinned by [com.raulshma.jellyplay.startup.AppStartupPrewarmsTest]):
 *  1. critical DataStore prewarms + font/stream cache prewarms (below),
 *  2. the audio + widget updaters (after the 2 s deferral),
 *  3. the background schedulers (after the same deferral),
 *  4. best-effort download recovery + the self-update APK sweep.
 */
class AppStartupPrewarms(
    private val applicationScope: CoroutineScope,
    // Exactly the dispatcher the former inline Application blocks launched
    // on; a constructor parameter only so tests can run the whole
    // choreography on virtual time. Production (Koin) omits it.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Deferred single access: the lazy defers construction off the cold-start
    // path until the IO prewarm block actually resolves it. The DataStore
    // prewarms stay FIRST in the critical coroutine — see launchCriticalPrewarms.
    private val networkOfflineStore: Lazy<NetworkOfflineStore>,
    // Resolved ONLY inside the IO prewarm block
    // (same deferred-construction pattern as networkOfflineStore above).
    // ServerIdentityStore.identity is stateIn(Eagerly) on Dispatchers.Default,
    // so on a first-launch cold start the network single's `identity.value
    // .deviceId ?: runBlocking { ensureDeviceId() }` fallback
    // (AndroidNetworkModule) could win the race against that eager DataStore
    // read and block MainViewModel construction on main. The prewarm calls the
    // idempotent ensureDeviceId() itself and awaits the published id, so the
    // runBlocking branch never fires — same UUID either way, and DataStore
    // serializes the (first-launch-only) write.
    private val serverIdentityStore: Lazy<ServerIdentityStore>,
    // Resolved ONLY inside the IO prewarm block
    // (same deferred-construction pattern as serverIdentityStore above).
    // PlayerActivity's lock-gate check reads the PERSISTED security slice via
    // `runBlocking { withTimeoutOrNull(1s) { firstPersistedSecurity() } }` on
    // main (onCreate/onNewIntent/onResume/PiP-expand) — on a cold process that
    // read pays DataStore's initial disk read, blocking main up to the full
    // 1 s budget. firstPersistedSecurity() collects the same cold flow this
    // prewarm collects, and DataStore caches the file snapshot in memory after
    // the first read, so hydrating the slice here (off main, started at t=0 of
    // the process) makes every later gate read a memory replay that resolves
    // without disk IO. PlayerActivity keeps its bounded runBlocking EXACTLY as
    // the fail-closed safety net (timeout ⇒ gate configured) — the prewarm
    // only widens the warm window, it changes no gate semantics.
    private val securityStore: Lazy<SecurityStore>,
    // (the FontProvider/VideoStreamCache prewarm Providers that used
    // to live in the Application left with the player-video migration — both
    // impls are Koin-owned in shared/feature/player-video's
    // androidPlayerVideoModule; they are resolved here, inside the IO
    // launcher, so the font-asset copy + cache-index open stay off the
    // cold-start critical path.)
    private val fontProvider: Lazy<FontProvider>,
    private val videoStreamCache: Lazy<VideoStreamCache>,
    // Lazy defers construction of AudioPlaybackManager (and its transitive
    // 14-dep graph: AudioLibraryBrowser, AudioProgressReporter,
    // AudioCrossfader, QueueUndoStack, LruCache(25), …) off the main thread
    // until the IO launch block below actually resolves it. The start() body
    // already offloads its real work to Dispatchers.IO, so behavior is
    // unchanged; only the construction cost moves off the cold-start
    // critical path.
    private val audioPlaybackManager: Lazy<AudioPlaybackManager>,
    // Lazy defers construction of NowPlayingWidgetUpdater, whose constructor
    // pulls in AudioPlaybackManager — the same 14-dep graph above. A direct
    // single access re-pulls that whole graph before onCreate returns;
    // resolving it inside the IO launch block below keeps it off the
    // cold-start critical path. start() only launches observers on its own
    // scope, so behavior is unchanged.
    private val nowPlayingWidgetUpdater: Lazy<NowPlayingWidgetUpdater>,
    // The remaining schedulers/listeners are likewise deferred: resolving one
    // constructs it (and its transitive graph — several pull Room, OkHttp,
    // repository singletons) inside the IO launch blocks, not during
    // onCreate before the first frame. Each .start()/.enqueue…()/sync() body
    // already runs on its own scope or dispatches to IO, so construction is
    // the only cost on the critical path.
    private val notificationScheduler: Lazy<NotificationScheduler>,
    private val autoDownloadScheduler: Lazy<AutoDownloadScheduler>,
    private val userDataSyncScheduler: Lazy<UserDataSyncScheduler>,
    private val playbackSyncScheduler: Lazy<PlaybackSyncScheduler>,
    private val playbackSyncReconnectListener: Lazy<PlaybackSyncReconnectListener>,
    private val downloadReconnectListener: Lazy<DownloadReconnectListener>,
    private val notificationReconnectListener: Lazy<NotificationReconnectListener>,
    private val widgetWorkScheduler: Lazy<WidgetWorkScheduler>,
    private val downloadRecoveryInitializer: Lazy<DownloadRecoveryInitializer>,
    // Lazy defers construction of AppUpdateRepository (which pulls GitHub +
    // OkHttp) off the cold-start path. cleanupDownloadedUpdate is a cheap file
    // delete, but construction is the cost worth deferring.
    private val appUpdateRepository: Lazy<AppUpdateRepository>,
) {

    /**
     * Fires the four launch groups onto [applicationScope], in the order the
     * former inline `onCreate` blocks fired them. Called once, from
     * `JellyPlayApplication.onCreate`.
     */
    fun start() {
        launchCriticalPrewarms()
        launchDeferredAudioAndWidget()
        launchBackgroundSchedulers()
        launchRecoveryAndSweep()
    }

    /**
     * Critical-path prewarms: (a) the network-offline DataStore slice —
     * the real persisted read happens in the store's eager stateIn
     * upstream, this keeps the flow warm for the imageLoader's
     * lazily-sized DiskCache — (b) the identity slice, and (c) the subtitle
     * font byte cache and video stream cache so the first ASS playback
     * doesn't read fonts on Main.
     *
     * This block used to await, strictly in
     * order, offline read → font prewarm → stream-cache prewarm → audio
     * start → widget start — off-main but serialized. The two cache
     * prewarms now run concurrently (no shared state between the
     * font byte cache and the video stream cache), and the audio+widget
     * group lives in its own sibling launch below (this block's own
     * original comment: "no dependency on the groups below"). The DataStore
     * prewarms stay FIRST in this coroutine: the Coil DiskCache sizing in
     * newImageLoader reads networkOffline.value lazily on the first
     * networked image write, and the documented lazily-sized-DiskCache race
     * needs this read to win that — both slices launch ahead of the async
     * prewarms, preserving exactly the head start they had before.
     */
    private fun launchCriticalPrewarms() {
        applicationScope.launch(ioDispatcher) {
            runCatchingRethrowingCancellation { networkOfflineStore.value.networkOffline.first() }
            // Not a plain `identity.first()` — a
            // stateIn(Eagerly) StateFlow always has its initial value
            // available, so a plain first() returns the all-null placeholder
            // instantly during the DataStore-read window and closes nothing;
            // and a bare `first { deviceId != null }` would hang forever on a
            // fresh install (nothing writes DEVICE_ID until ensureDeviceId
            // runs). Calling the idempotent ensureDeviceId() here — the same
            // call the network single's runBlocking fallback would make —
            // reads-or-persists the UUID off main, and awaiting the non-null
            // publication guarantees every later `.value.deviceId` read in
            // AndroidNetworkModule resolves the fast path.
            runCatchingRethrowingCancellation {
                // Bounded wait: a wedged DataStore (corruption, upstream
                // error stranding the Eagerly-started identity flow on its
                // all-null placeholder) would suspend both ensureDeviceId()
                // — itself a DataStore edit — and a bare first{} forever,
                // gating the font/stream prewarms below, so both run inside
                // the timeout. On timeout they proceed anyway; the network
                // module readers keep their own runBlocking ensureDeviceId()
                // fallback, and DataStore's atomic temp-file write makes a
                // timeout mid-persist safe to abandon.
                withTimeoutOrNull(DEVICE_ID_PREWARM_TIMEOUT_MS) {
                    serverIdentityStore.value.ensureDeviceId()
                    serverIdentityStore.value.identity.first { !it.deviceId.isNullOrEmpty() }
                }
            }
            // Hydrate the persisted security slice for PlayerActivity's
            // lock-gate read (see the parameter comment above). Shares the same
            // "user_prefs" DataStore file as the two reads above, so this
            // piggybacks their already-in-flight/completed initial read and is
            // effectively free on a warm one. Bounded like the identity prewarm so a
            // wedged read can never gate the font/stream prewarms below; on
            // timeout we proceed anyway — PlayerActivity's own 1 s-bounded
            // fail-closed read is unchanged and remains the gate's authority.
            runCatchingRethrowingCancellation {
                withTimeoutOrNull(SECURITY_SLICE_PREWARM_TIMEOUT_MS) {
                    securityStore.value.firstPersistedSecurity()
                }
            }
            // Koin-owned since the player-video migration (the Hilt
            // javax.inject.Provider fields died with the module flip); still
            // resolved here, inside the IO launcher, so the font-asset copy +
            // cache-index open stay off the cold-start critical path.
            coroutineScope {
                // runCatching stays INSIDE each async so a failed prewarm
                // neither cancels the sibling nor fails the outer launch —
                // the same per-call swallow the sequential runCatching chain
                // had.
                // FontProvider.prewarm() suspends, so it needs the
                // cancellation-rethrowing variant; VideoStreamCache.prewarm()
                // doesn't, so plain runCatching still suffices there.
                async { runCatchingRethrowingCancellation { fontProvider.value.prewarm() } }
                async { runCatching { videoStreamCache.value.prewarm() } }
            }
        }
    }

    // The audio + widget updaters — launched as a sibling of the
    // prewarm block above instead of serializing behind it. Resolves the
    // same lazy collaborators as before (AudioPlaybackManager's 14-dep graph +
    // Room queue restore, then NowPlayingWidgetUpdater), in the same
    // order, just no longer gated on the DataStore reads and cache
    // prewarms finishing first.
    private fun launchDeferredAudioAndWidget() {
        applicationScope.launch(ioDispatcher) {
            delay(BACKGROUND_START_DEFERRAL_MS)
            audioPlaybackManager.value.start()
            nowPlayingWidgetUpdater.value.start()
        }
    }

    // Background schedulers — independent enqueue calls, all KEEP-safe.
    // Run concurrently with the critical path so cold start isn't gated on
    // audio init. Each lazy resolution constructs its scheduler here (off
    // the main thread) instead of during onCreate.
    private fun launchBackgroundSchedulers() {
        applicationScope.launch(ioDispatcher) {
            delay(BACKGROUND_START_DEFERRAL_MS)
            widgetWorkScheduler.value.enqueuePeriodic()
            userDataSyncScheduler.value.enqueuePeriodic()
            playbackSyncScheduler.value.enqueuePeriodic()
            playbackSyncReconnectListener.value.start()
            downloadReconnectListener.value.start()
            notificationReconnectListener.value.start()
            autoDownloadScheduler.value.sync()
            notificationScheduler.value.scheduleOrUpdate()
        }
    }

    // Best-effort tail: download recovery and the self-update APK sweep —
    // independent of the groups above.
    private fun launchRecoveryAndSweep() {
        applicationScope.launch(ioDispatcher) {
            downloadRecoveryInitializer.value.recover()
        }
        // Sweep any APK left by a prior self-update. A successful install
        // restarts the process (so this runs in the new version) and leaves the
        // old APK orphaned; a cancelled/failed install also leaves it behind.
        // Safe at startup: onCreate precedes any new download.
        applicationScope.launch(ioDispatcher) {
            runCatching { appUpdateRepository.value.cleanupDownloadedUpdate() }
        }
    }
}
