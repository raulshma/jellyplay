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
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Receiver scope for [PreferencesEditor.edit], exposing the 18 domain stores +
 * [AppRuntimeStateStore] so write-side call sites reach the owning store
 * directly instead of going through `UserPreferencesStore` forwarding setters.
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
@Singleton
class PreferencesEditScope @Inject constructor(
    val playback: PlaybackStore,
    val appearance: AppearanceStore,
    val videoPlayer: VideoPlayerStore,
    val downloads: DownloadsStore,
    val engine: PlayerEngineStore,
    val homeDiscovery: HomeDiscoveryStore,
    val audio: AudioStore,
    val audioEffects: AudioEffectsStore,
    val audioCache: AudioCacheStore,
    val library: LibraryStore,
    val navigation: NavigationStore,
    val networkOffline: NetworkOfflineStore,
    val notification: NotificationStore,
    val screensaver: ScreensaverStore,
    val security: SecurityStore,
    val subtitle: SubtitleLanguageStore,
    val syncPlayCast: SyncPlayCastStore,
    val experimental: ExperimentalStore,
    val appRuntimeState: AppRuntimeStateStore,
)
