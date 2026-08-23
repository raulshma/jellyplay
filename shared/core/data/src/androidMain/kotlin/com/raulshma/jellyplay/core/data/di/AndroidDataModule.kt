package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.network.AndroidNetworkMonitor
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.AndroidOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaExtractorLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the Koin-owned data layer (Phase C4 part 2).
 * Holds the Context-shaped definitions: the connectivity/offline seams
 * (NetworkMonitor / OfflineModeManager), the LruCache-based image-URL
 * memoiser, and the MediaExtractor metadata probe; everything else resolves
 * from [dataJvmModule].
 */
fun androidDataModule(context: Context): Module {
    // Side effect, deliberately before the module definition: common code
    // reads [DataBuildFlags.debugBuild] (the moved BuildConfig.DEBUG seam)
    // possibly as early as single construction, so the flag must be set when
    // the module function runs — the app composition root calls this during
    // startKoin, long before any definition resolves.
    DataBuildFlags.debugBuild =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    return module {
        single<NetworkMonitor> { AndroidNetworkMonitor(context) }

        single<OfflineModeManager> {
            AndroidOfflineModeManager(
                context = context,
                networkMonitor = get(),
                networkOfflineStore = get(),
            )
        }

        single<ImageUrlProvider> {
            ImageUrlProviderImpl(
                playbackRepository = get(),
                appearanceStore = get(),
            )
        }

        single<LocalStreamProbe> { MediaExtractorLocalStreamProbe() }

        // V3 downloads conveyor: the deferred MediaRepository edge of the
        // Koin-owned DownloadRepositoryImpl. Resolves through the app
        // composition root's Hilt interop on first invocation (the desktop
        // counterpart in desktopDataModule throws — no definition there until
        // Phase X). Deferred because MediaRepository stays Hilt-constructed
        // and eagerly pulling it here would re-enter the Hilt graph during
        // Koin single construction.
        single<MediaRepositoryAccess> { MediaRepositoryAccess { get<MediaRepository>() } }
    }
}
