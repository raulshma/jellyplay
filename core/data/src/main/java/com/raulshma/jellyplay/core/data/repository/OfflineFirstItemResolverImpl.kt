package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.toMediaItem
import javax.inject.Inject
import javax.inject.Singleton

// C4p2 note: the [OfflineFirstItemResolver] interface + [ResolvedMediaRef]
// moved to :shared:core:data commonMain; this impl stays in the legacy module
// because its constructor takes the Android-coupled [OfflineModeManager]
// (ConnectivityManager/ProcessLifecycleOwner), which has no Koin definition
// yet. It migrates with the OfflineModeManager/connectivity seam.

@Singleton
class OfflineFirstItemResolverImpl @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val mediaRepository: MediaRepository,
    private val offlineModeManager: OfflineModeManager,
    private val imageUrlProvider: ImageUrlProvider,
) : OfflineFirstItemResolver {

    override suspend fun resolveMediaRef(id: String): ResolvedMediaRef {
        val offline = offlineRepository.getOfflineItem(id)
        if (offline != null) {
            val url = offline.posterPath ?: imageUrlProvider.getImageUrl(id)
            return ResolvedMediaRef(item = offline.toMediaItem(), posterUrl = url)
        }
        // Online-only fallback for items watched but never downloaded. Skipped
        // while offline to avoid a guaranteed-failing network call.
        if (offlineModeManager.offlineMode.value == OfflineMode.ONLINE) {
            mediaRepository.getMediaDetail(id)
                .getOrNull()
                ?.item
                ?.let { item ->
                    return ResolvedMediaRef(item = item, posterUrl = imageUrlProvider.getImageUrl(id))
                }
        }
        return ResolvedMediaRef(item = null, posterUrl = imageUrlProvider.getImageUrl(id))
    }
}
