package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceStores
import com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore

/**
 * Receiver scope for [PreferencesEditor.edit], exposing the 19 domain stores +
 * [AppRuntimeStateStore] so write-side call sites reach the owning store
 * directly instead of going through `UserPreferencesStore` forwarding setters.
 *
 * The 20 slice-backed stores arrive as the ONE [PreferenceStores] aggregate —
 * the same bundle the settings read lanes consume — instead of a positional
 * re-enumeration that every new domain store widened twice (here + the facade
 * constructor). [AppRuntimeStateStore] stays its own parameter: it is the
 * backup-extras store, not a read-lane slice, so it deliberately rides
 * outside the aggregate (the facade keeps the same split).
 *
 * Each property below is the single owner of its slice's invariant-bearing
 * writes (cross-key mutex, coerce, LRU, migration). Callers inside an
 * `editor.edit { ... }` block prefix the store: `appearance.setThemeMode(mode)`,
 * `playback.setPreferredPlayer(player)`, etc.
 *
 * This scope intentionally exposes **only** stores — no reads, no aggregate.
 * Read paths go through the store's own `StateFlow<XSlice>` or
 * [com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections].
 */
class PreferencesEditScope constructor(
    val stores: PreferenceStores,
    val appRuntimeState: AppRuntimeStateStore,
) {
    val playback: PlaybackStore get() = stores.playback
    val appearance: AppearanceStore get() = stores.appearance
    val videoPlayer: VideoPlayerStore get() = stores.videoPlayer
    val downloads: DownloadsStore get() = stores.downloads
    val engine: PlayerEngineStore get() = stores.engine
    val homeDiscovery: HomeDiscoveryStore get() = stores.homeDiscovery
    val audio: AudioStore get() = stores.audio
    val audioEffects: AudioEffectsStore get() = stores.audioEffects
    val audioCache: AudioCacheStore get() = stores.audioCache
    val library: LibraryStore get() = stores.library
    val navigation: NavigationStore get() = stores.navigation
    /** User shortcuts: definitions + header/overflow pin orders (per-user namespaced keys). */
    val shortcuts: ShortcutsStore get() = stores.shortcuts
    val networkOffline: NetworkOfflineStore get() = stores.networkOffline
    val notification: NotificationStore get() = stores.notification
    val screensaver: ScreensaverStore get() = stores.screensaver
    val security: SecurityStore get() = stores.security
    val subtitle: SubtitleLanguageStore get() = stores.subtitle
    val syncPlayCast: SyncPlayCastStore get() = stores.syncPlayCast
    val experimental: ExperimentalStore get() = stores.experimental
    /** Per-content-type volume memory (settings toggle + levels). */
    val volumeProfile: com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore get() = stores.volumeProfile
}
