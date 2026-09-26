package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the Koin-owned data layer (part 2) — the
 * construction-owner aggregate. The former single 337-line module is now
 * sibling family modules in this package (the file's old comment-section
 * boundaries — one Koin module per family), and this aggregate keeps the
 * `desktopDataModule` name alive via `includes` so consumers
 * (apps/desktop's `desktopKoinModules` list, DataKoinModulesTest's desktop
 * smoke graph) and the load-order relationships are unchanged. Every
 * binding body moved verbatim into its family — no renames, no retyping.
 *
 * Families (mirroring the DataKoinModule aggregate pattern; the includes
 * order follows the pre-split file's section order — OfflineModeManager
 * rides at the front with NetworkMonitor because it is the connectivity
 * pair, and registration order is inert in Koin anyway, definitions are
 * keyed):
 *  - [desktopConnectivityModule] — the always-connected NetworkMonitor
 *    pick and the OfflineModeManager built over it.
 *  - [desktopRemoteControlModule] — the remote-control receiver trio.
 *  - [desktopMediaSupportModule] — the LinkedHashMap-based image-URL
 *    memoiser, the (unsupported, badge-less) desktop stream probe, and
 *    the file-backed StreamingSubtitleStore.
 *  - [desktopAdminModule] — the admin-statistics label seam's desktop
 *    actual (base-locale English literals).
 *  - [desktopDownloadsSeamsModule] — the V3 downloads conveyor's desktop
 *    seam actuals: the appdata storage layout, the in-process
 *    DesktopDownloadManager (the DownloadEnqueueCoordinator actual:
 *    enqueue = transfer-loop kick, cancelWork = cooperative stop), no-op
 *    notification / image-preload surfaces, the desktop DownloadIntake,
 *    and the 6 h auto-download loop. Since the MediaRepository cluster
 *    flip the MediaRepositoryAccess actual is REAL (Koin owns
 *    MediaRepositoryImpl on desktop too) — series downloads and
 *    auto-download work end-to-end.
 *  - [desktopHomeConveyorModule] — the Home conveyor's desktop actuals:
 *    the work-scheduler twins plus the honest no-ops.
 *  - [desktopUpdateModule] — the desktop update-check sentinel.
 *
 * Everything not defined by these families resolves from [dataJvmModule].
 *
 * OVERRIDE COUPLING (the ONE deliberate desktop override): apps/desktop's
 * desktopAppUpdateModule — LAST in desktopKoinModules' startKoin list,
 * which runs with allowOverride(true) — REPLACES [desktopUpdateModule]'s
 * sentinel-bound AppUpdateRepository single with the real-version desktop
 * auto-update actual (docs/adr/desktop-auto-update.md). Pointer comments
 * live on both sides (DesktopUpdateKoinModule.kt / DesktopKoinModules.kt).
 */
fun desktopDataModule(dataDir: Path): Module {
    // Side effect, deliberately before the module definition: common code
    // reads [DataBuildFlags.debugBuild] (the moved BuildConfig.DEBUG seam)
    // possibly as early as single construction, so the flag must be set when
    // the module function runs. Desktop defaults to debug logging on unless
    // `jellyplay.debug=false` is set on the JVM command line (desktop app
    // builds arrive at;  jvmTest smoke tests get verbose logs).
    DataBuildFlags.debugBuild = System.getProperty("jellyplay.debug")?.toBoolean() ?: true

    return module {
        includes(
            desktopConnectivityModule,
            desktopRemoteControlModule,
            desktopMediaSupportModule(dataDir),
            desktopAdminModule,
            desktopDownloadsSeamsModule(dataDir),
            desktopHomeConveyorModule,
            // OVERRIDE COUPLING: apps/desktop's desktopAppUpdateModule
            // (last in desktopKoinModules' startKoin list,
            // allowOverride(true)) replaces this family's sentinel-bound
            // AppUpdateRepository single — see DesktopUpdateKoinModule.kt's
            // pointer comment.
            desktopUpdateModule(dataDir),
        )
    }
}
