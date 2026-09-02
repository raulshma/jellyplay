package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
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
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore

/**
 * JVM test-source home of the reset-coverage guard machinery, moved out of
 * commonMain with the KMP port (Java reflection is unavailable there, and the
 * guard only runs from tests). Mirrors the pre-KMP bodies.
 */

/**
 * Reflectively enumerates every `Preferences.Key<*>` field declared in [keys]
 * (an `object` holding preference keys). Uses Java reflection (no
 * kotlin-reflect dependency) and is only invoked from the coverage guard, so
 * the reflection cost is never paid in production paths.
 */
fun reflectKeys(keys: Any): List<Preferences.Key<*>> {
    val keyType = Preferences.Key::class.java
    return keys::class.java.declaredFields
        .filter { keyType.isAssignableFrom(it.type) }
        .onEach { it.isAccessible = true }
        .mapNotNull { runCatching { it.get(keys) as? Preferences.Key<*> }.getOrNull() }
}

/**
 * Reflectively enumerates every `Preferences.Key<*>` declared across the
 * facade-owned `Keys` object and each domain store's `Keys` object (and
 * `PinRateLimiter.Keys`). Aggregating from the stores — not a facade copy of
 * their keys — keeps a single declaration owner per key; a store key rename
 * cannot silently drift out of the coverage guard.
 */
fun UserPreferencesStore.declaredKeys(): List<Preferences.Key<*>> = buildList {
    addAll(reflectKeys(UserPreferencesStore.Keys))
    addAll(reflectKeys(PlaybackStore.Keys))
    addAll(reflectKeys(AppearanceStore.Keys))
    addAll(reflectKeys(VideoPlayerStore.Keys))
    addAll(reflectKeys(DownloadsStore.Keys))
    addAll(reflectKeys(PlayerEngineStore.Keys))
    addAll(reflectKeys(HomeDiscoveryStore.Keys))
    addAll(reflectKeys(AudioStore.Keys))
    addAll(reflectKeys(AudioEffectsStore.Keys))
    addAll(reflectKeys(AudioCacheStore.Keys))
    addAll(reflectKeys(LibraryStore.Keys))
    addAll(reflectKeys(NavigationStore.Keys))
    addAll(reflectKeys(NetworkOfflineStore.Keys))
    addAll(reflectKeys(NotificationStore.Keys))
    addAll(reflectKeys(ScreensaverStore.Keys))
    addAll(reflectKeys(SecurityStore.Keys))
    addAll(reflectKeys(SubtitleLanguageStore.Keys))
    addAll(reflectKeys(SyncPlayCastStore.Keys))
    addAll(reflectKeys(ExperimentalStore.Keys))
    addAll(reflectKeys(AppRuntimeStateStore.Keys))
    addAll(reflectKeys(PinRateLimiter.Keys))
}

/**
 * Coverage guard: asserts that the union of every category's key list plus
 * the store's [UserPreferencesStore.resetExcludedKeys] covers every declared
 * key. Returns the uncovered keys (empty when coverage holds).
 */
fun UserPreferencesStore.uncoveredResetKeys(): Set<Preferences.Key<*>> {
    val covered = allResetCategoryKeys() + resetExcludedKeys
    return declaredKeys().filterNot { it in covered }.toSet()
}
