package com.raulshma.jellyplay.core.data.di

import com.raulshma.jellyplay.core.data.network.DesktopNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.DesktopOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.DesktopLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.util.DesktopImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import java.nio.file.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop platform pick of the Koin-owned data layer (Phase C4 part 2).
 * Holds the always-connected connectivity seams, the LinkedHashMap-based
 * image-URL memoiser, and the (unsupported, badge-less) desktop stream
 * probe; everything else resolves from [dataJvmModule].
 */
fun desktopDataModule(dataDir: Path): Module {
    // Side effect, deliberately before the module definition: common code
    // reads [DataBuildFlags.debugBuild] (the moved BuildConfig.DEBUG seam)
    // possibly as early as single construction, so the flag must be set when
    // the module function runs. Desktop defaults to debug logging on unless
    // `jellyplay.debug=false` is set on the JVM command line (desktop app
    // builds arrive at Phase V1; jvmTest smoke tests get verbose logs).
    DataBuildFlags.debugBuild = System.getProperty("jellyplay.debug")?.toBoolean() ?: true

    return module {
        single<NetworkMonitor> { DesktopNetworkMonitor() }

        single<OfflineModeManager> {
            DesktopOfflineModeManager(
                networkMonitor = get(),
                networkOfflineStore = get(),
            )
        }

        single<ImageUrlProvider> {
            DesktopImageUrlProvider(
                playbackRepository = get(),
                appearanceStore = get(),
            )
        }

        single<LocalStreamProbe> { DesktopLocalStreamProbe() }
    }
}
