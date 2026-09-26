package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.widget.ContinueWatchingBroadcaster
import com.raulshma.jellyplay.core.data.widget.LibrarySyncHook
import com.raulshma.jellyplay.core.data.worker.DesktopPlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.PlaybackOutboxDrainer
import com.raulshma.jellyplay.core.data.worker.PlaybackOutboxDrainerImpl
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.data.worker.TvWatchNextScheduler
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Home-conveyor family of the desktopDataModule split: the desktop actuals
 * of the four WorkManager/widget-backed HomeViewModel ctor deps — the
 * work-scheduler twins (PlaybackSyncScheduler REAL via the desktop
 * playback-outbox drainer, TvWatchNextScheduler an honest no-op) and the
 * widget bridges (ContinueWatchingBroadcaster / LibrarySyncHook no-ops).
 * Android loads its own actuals in androidCoreDataModule (the
 * schedulers) and the app's androidAppModule (the widget bridges) — the
 * two platform modules never load together. Binding bodies moved verbatim
 * from the pre-split single-module layout — see [desktopDataModule] for
 * the aggregate and the family map.
 */
internal val desktopHomeConveyorModule: Module = module {
    // ── Home conveyor desktop actuals: the four WorkManager/ ──
    // widget-backed HomeViewModel ctor deps have their desktop actuals
    // here (Android: PlaybackSyncScheduler lives in
    // androidCoreDataModule, TvWatchNextScheduler too,
    // ContinueWatchingBroadcaster/LibrarySyncHook in the app's
    // androidAppModule).
    //  - PlaybackSyncScheduler: REAL since the playback-outbox drainer
    //    moved into shared jvmShared — DesktopPlaybackSyncScheduler runs
    //    drainer.drainOnce(0) at startup, on every going-online edge
    //    (network Offline→Online OR the app-level Offline Mode toggling
    //    back online — the shared ReconnectTrigger), and on
    //    SyncStatusStateHolder's manual "sync now"
    //    (see its class KDoc for the declared behaviour delta: desktop
    //    staged outbox rows now actually drain; no periodic backstop).
    //    Android overrides the interface with the WorkManager-backed
    //    PlaybackSyncSchedulerImpl in androidCoreDataModule — the two
    //    platform modules never load together, and Android constructs its
    //    worker-scoped drainer per worker (no Koin single here would fit;
    //    setForeground lives on the running CoroutineWorker).
    //  - TvWatchNextScheduler: the Android TV "Watch Next" OS row has no
    //    desktop equivalent.
    //  - ContinueWatchingBroadcaster: refreshes the Android app widget's
    //    RemoteViews service; no widgets on desktop.
    //  - LibrarySyncHook: fans a library scan out to Android's
    //    auto-download drain + widget refresh; both are no-ops here.
    single<PlaybackOutboxDrainer.UserDataSyncTrigger> {
        // No WorkManager user-data worker on desktop: the drain tail's
        // synchronous cache-invalidate + notifyUserDataChanged fan-out
        // already refreshes the open UI; the 12h async warm-refetch stays
        // Android-only.
        PlaybackOutboxDrainer.UserDataSyncTrigger { }
    }
    single<PlaybackOutboxDrainer> {
        PlaybackOutboxDrainerImpl(
            outbox = get(),
            playbackRepository = get(),
            offlineModeManager = get(),
            playedStateSync = get(),
            offlineRepository = get(),
            mediaRepository = get(),
            cacheInvalidator = get(),
            userDataSyncTrigger = get(),
            // Desktop has no notification surface for a headless drain.
            notifier = PlaybackOutboxDrainer.Notifier.NONE,
        )
    }
    single {
        DesktopPlaybackSyncScheduler(
            drainer = get(),
            networkMonitor = get(),
            offlineModeManager = get(),
            scope = get(DatastoreQualifiers.applicationScope),
        )
    }
    single<PlaybackSyncScheduler> { get<DesktopPlaybackSyncScheduler>() }
    single<TvWatchNextScheduler> {
        object : TvWatchNextScheduler {
            override fun scheduleRefresh() {}
        }
    }
    single<ContinueWatchingBroadcaster> {
        object : ContinueWatchingBroadcaster {
            override fun refreshContinueWatching() {}
        }
    }
    single<LibrarySyncHook> {
        object : LibrarySyncHook {
            override suspend fun onLibraryScanComplete() {}
        }
    }
}
