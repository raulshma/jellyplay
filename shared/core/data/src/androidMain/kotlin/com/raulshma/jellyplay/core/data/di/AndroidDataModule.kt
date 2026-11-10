package com.raulshma.jellyplay.core.data.di

import android.content.Context
import com.raulshma.jellyplay.core.data.network.AndroidNetworkMonitor
import com.raulshma.jellyplay.core.datastore.di.DatastoreQualifiers
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.offline.AndroidOfflineModeManager
import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.LocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.MediaExtractorLocalStreamProbe
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.data.repository.MediaRepositoryAccess
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.data.util.DefaultArtworkUrls
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.data.util.ImageUrlProviderImpl
import com.raulshma.jellyplay.core.data.util.DataBuildFlags
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android platform pick of the Koin-owned data layer (part 2).
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
        single<NetworkMonitor> { AndroidNetworkMonitor(context, applicationScope = get(DatastoreQualifiers.applicationScope)) }

        single<OfflineModeManager> {
            AndroidOfflineModeManager(
                context = context,
                networkMonitor = get(),
                networkOfflineStore = get(),
            )
        }

        single<ImageUrlProvider> {
            ImageUrlProviderImpl(
                imageApiClient = get(),
                appearanceStore = get(),
            )
        }

        single<ArtworkUrls> { DefaultArtworkUrls(get()) }

        single<LocalStreamProbe> { MediaExtractorLocalStreamProbe() }

        // V3 downloads conveyor: the deferred detail-read seam of the
        // Koin-owned DownloadRepositoryImpl. It resolves Koin's own
        // MediaRepositoryImpl single (dataJvmModule), narrowed to its
        // DetailFeed family seam. The same seam exists on desktop through
        // desktopDataModule.
        single<MediaRepositoryAccess> { MediaRepositoryAccess { get<DetailFeed>() } }

        // NOTE: the Android actuals of the app-update seams (ApkInstallBuilder /
        // AppUpdateRepository) used to be registered here; they moved to the
        // app's STORE distribution module (DistributionPlatform.koinModules) so
        // the fdroid line binds none of them and R8 strips the GitHub
        // download/install impls from fdroid APKs. The classes stay in
        // core:data — the desktop graph (desktopDataModule) still binds its own
        // AppUpdateRepository.
    }
}
