package com.raulshma.jellyplay.startup

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Best-effort cache maintenance (lyrics cache pruning + offline orphan
 * cleanup). Extracted out of `JellyPlayApplication` so the Application class
 * *composes* startup steps rather than *containing* them.
 *
 * These passes tidy up caches that accumulate over time; they are not on the
 * critical startup path. [cleanupOnce] is event-driven (triggered after the
 * first successful auth) instead of the previous fragile 10 s startup delay.
 */
class CacheMaintenanceInitializer (
    private val mediaRepository: MediaRepository,
    private val offlineRepository: OfflineRepository,
    private val applicationScope: CoroutineScope,
) {
    private val ran = AtomicBoolean(false)

    /** Runs the maintenance passes unconditionally. */
    suspend fun cleanup() {
        mediaRepository.cleanupLyricsCache()
        offlineRepository.cleanupOrphans()
    }

    /**
     * Runs [cleanup] at most once per process, on the application scope so it
     * survives the triggering component's lifecycle. Subsequent calls are
     * no-ops, making it safe to invoke from every auth-success emission.
     */
    fun cleanupOnce() {
        if (!ran.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.IO) { cleanup() }
    }
}
