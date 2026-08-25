package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.toMediaItem

// Phase X MediaRepository cluster flip: moved verbatim from the legacy
// :core:data shim (same package/name); `@Singleton` / `@Inject` stripped
// (one framework per type — Koin's dataJvmModule constructs this single; the
// legacy DataModule bridges the remaining Hilt injectors via koin().get()).
class OfflineFirstItemResolverImpl(
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
