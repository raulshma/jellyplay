package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The screen side of the deferred user-data refresh contract (the read-side
 * twin of the silent flip contract): a user-data change that landed while
 * this screen was in the back stack only marked its data stale — the silent
 * reload/regeneration fires here, on re-entry, never mid-scroll. ViewModels
 * implement [DeferredRefreshHost] by delegating to their
 * `DeferredUserDataRefresher`; this effect is the single wiring every host
 * screen needs, replacing the per-screen `LifecycleResumeEffect` copy.
 */
fun interface DeferredRefreshHost {
    /** Called with true when the host screen resumes, false when it pauses or leaves composition. */
    fun onScreenActiveChanged(active: Boolean)
}

/**
 * Drives [DeferredRefreshHost.onScreenActiveChanged] from the host screen's
 * lifecycle: resumed = on screen. `LifecycleResumeEffect` keys on the host so
 * a recomposition with the same ViewModel re-arms nothing; a NEW host (screen
 * recreated with a fresh VM) restarts the effect cleanly.
 */
@Composable
fun DeferredRefreshEffect(host: DeferredRefreshHost) {
    LifecycleResumeEffect(host) {
        host.onScreenActiveChanged(true)
        onPauseOrDispose { host.onScreenActiveChanged(false) }
    }
}
