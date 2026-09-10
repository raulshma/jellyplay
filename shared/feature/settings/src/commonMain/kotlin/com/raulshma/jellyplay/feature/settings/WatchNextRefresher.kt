package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam behind the "Android TV Watch Next" toggle: the WorkManager
 * scheduler lives in the Hilt-owned Android data shim (no desktop watch-next
 * surface), so the app composition root provides the scheduling impl at the
 * Koin edge. Non-suspend, matching the underlying scheduler.
 */
fun interface WatchNextRefresher {
    fun scheduleRefresh()
}
