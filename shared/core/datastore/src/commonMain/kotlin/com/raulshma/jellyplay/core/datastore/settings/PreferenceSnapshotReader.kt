package com.raulshma.jellyplay.core.datastore.settings

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import kotlinx.coroutines.flow.first

/**
 * The read-side twin of [com.raulshma.jellyplay.core.datastore.PreferencesEditor]
 * (the single auditable write seam): the ONE owner of the one-shot preference
 * snapshot gather — the 18 domain-store slices + [AppRuntimeStateStore] runtime
 * + [PinRateLimiter] lockout → [PreferenceSliceSnapshot] — that the
 * factory-reset review and import-preview diffs run on.
 *
 * Each slice is read via a single `.first()`; there is no live subscription,
 * matching the rarely-opened review screens that consume it. The read order
 * matches the [PreferenceSliceSnapshot] field order (playback → experimental,
 * then runtime, then PIN lockout) — the same order the former per-VM
 * `buildFromSlices` hand-assemblies used, so observable ordering is unchanged.
 *
 * Store enumeration is delegated to [PreferenceStores]; the runtime state and
 * PIN lockout ride alongside because the snapshot carries them but no domain
 * store owns them. `volumeProfile` is deliberately NOT read — the snapshot's
 * volume-profile slice keeps its default, exactly as the former per-VM
 * assemblies did.
 */
class PreferenceSnapshotReader(
    private val stores: PreferenceStores,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val pinRateLimiter: PinRateLimiter,
) {
    /** Builds the live snapshot once from the 18 domain-store slices + runtime/PIN extras. */
    suspend fun snapshotOnce(): PreferenceSliceSnapshot = PreferenceSliceSnapshot(
        playback = stores.playback.playback.first(),
        videoPlayer = stores.videoPlayer.videoPlayer.first(),
        engine = stores.engine.playerEngine.first(),
        subtitle = stores.subtitle.subtitle.first(),
        audio = stores.audio.audio.first(),
        audioEffects = stores.audioEffects.audioEffects.first(),
        audioCache = stores.audioCache.audioCache.first(),
        appearance = stores.appearance.appearance.first(),
        homeDiscovery = stores.homeDiscovery.homeDiscovery.first(),
        library = stores.library.library.first(),
        navigation = stores.navigation.navigation.first(),
        downloads = stores.downloads.downloads.first(),
        networkOffline = stores.networkOffline.networkOffline.first(),
        notification = stores.notification.notification.first(),
        syncPlayCast = stores.syncPlayCast.syncPlayCast.first(),
        screensaver = stores.screensaver.screensaver.first(),
        security = stores.security.security.first(),
        experimental = stores.experimental.experimental.first(),
        runtime = appRuntimeStateStore.state.first(),
        pinLockout = pinRateLimiter.getPinLockoutState(),
    )
}
