package com.raulshma.jellyplay.core.data.repository

/**
 * Platform seams the portable download engine calls out through (V3 downloads
 * conveyor). The moved [DownloadRepositoryImpl] previously reached Android
 * surfaces directly — WorkManager enqueue/cancel, the notification group
 * summary, and Coil's image cache — which kept it welded to the legacy module.
 * Each seam below covers exactly one of those call-site clusters; the Android
 * actuals live in the legacy :core:data shim (bridged into Koin by the app
 * composition root), and the desktop actuals in :shared:core:data jvmMain.
 */

/**
 * Starts and stops the platform's background execution for a download row.
 *
 * Android actual: [com.raulshma.jellyplay.core.data.repository.DownloadEnqueuer]
 * enqueues a unique WorkManager job (wifi-only + schedule-window honoured on
 * the runtime path) and cancels it. Desktop actual: the in-process
 * DesktopDownloadManager — [enqueue] kicks the transfer loop for the row,
 * [cancelWork] cooperatively stops an in-flight transfer so the repository's
 * pause/cancel take effect promptly (mirroring the WorkManager cancel the
 * Android repository performed so the worker stops at its next poll tick).
 */
interface DownloadEnqueueCoordinator {

    /**
     * Enqueues (or keeps) the background work for [downloadId]. Runtime
     * semantics: honour the user's wifi-only / download-schedule preferences
     * (cold-start recovery callers bypass the gate on Android by calling the
     * concrete [DownloadEnqueuer] directly).
     */
    fun enqueue(downloadId: String)

    /**
     * Cancels the in-flight background work for [downloadId], if any. Safe to
     * call when no work is registered.
     */
    fun cancelWork(downloadId: String)
}

/**
 * Keeps the download progress summary surface in sync when the repository
 * changes a row's state (pause/cancel/resume/retry/delete) — e.g. in-app
 * controls that never cross a notification action. Best-effort by contract: a
 * platform hiccup must never fail the state change (the repository wraps calls
 * in runCatching).
 */
fun interface DownloadProgressNotifier {

    /** Refreshes the collapsed summary with [inFlightCount] active rows. */
    fun refreshSummary(inFlightCount: Int)
}

/**
 * Preloads an offline image into the platform image cache so offline screens
 * render without network. Android actual: Coil (decoded at 384² to match the
 * display path's memory-cache key). Desktop actual: no-op (Compose desktop
 * has no shared preload cache yet).
 */
fun interface OfflineImagePreloader {

    /** Best-effort preload of [url]; failures are swallowed by the impl. */
    fun preload(url: String)
}

/**
 * Deferred accessor for [MediaRepository] — the construction-cycle-shaped edge
 * of the download repository. Every use of MediaRepository inside
 * [DownloadRepositoryImpl] lives on the series paths (`downloadSeries` and the
 * episode series-seeding in `saveOfflineMediaItem`); a single-item
 * `startDownload` never touches it. Resolving the repository lazily breaks the
 * DownloadRepository ↔ MediaRepository construction cycle.
 *
 * Since the Phase X cluster flip both platform defs (androidDataModule,
 * desktopDataModule) resolve the Koin `MediaRepositoryImpl` single directly —
 * desktop series downloads and auto-download are live.
 */
fun interface MediaRepositoryAccess {

    /** Returns the MediaRepository. May throw on platforms without a definition. */
    operator fun invoke(): MediaRepository
}
