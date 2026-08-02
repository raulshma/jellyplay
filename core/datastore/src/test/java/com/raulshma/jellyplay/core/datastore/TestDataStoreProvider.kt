package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
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
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import kotlinx.coroutines.CoroutineScope

/**
 * Provides the single shared `"user_prefs"` DataStore instance for datastore
 * module tests. AndroidX DataStore throws at runtime if two delegates resolve
 * to the same `.preferences_pb` file in one process, so tests cannot construct
 * each store with its own delegate — they must share one.
 *
 * The delegate lives in `di.UserPreferencesDataStore` (main source) but is
 * `private`; for tests we re-declare it here under a distinct name so both
 * the test and the main `DataStoreModule.provideUserPreferencesDataStore`
 * resolve to the same file (AndroidX guarantees one instance per
 * `(applicationContext, name)` pair).
 */
object TestDataStoreProvider {
    private val Context.testUserPrefsDataStore: DataStore<Preferences> by
        androidx.datastore.preferences.preferencesDataStore(name = "user_prefs")

    fun get(context: Context): DataStore<Preferences> =
        context.applicationContext.testUserPrefsDataStore
}

/**
 * Builds a [UserPreferencesStore] wired to all of its domain-store collaborators,
 * each sharing the same `"user_prefs"` DataStore (AndroidX requires a single
 * delegate per file per process). Keeps tests in sync as new domain stores are
 * added to the facade constructor.
 */
@Suppress("TestFunctionName")
fun createUserPreferencesStore(
    scope: CoroutineScope,
    dataStore: DataStore<Preferences>,
): UserPreferencesStore {
    val playbackStore = PlaybackStore(dataStore, scope)
    val appearanceStore = AppearanceStore(dataStore, scope)
    val videoPlayerStore = VideoPlayerStore(dataStore, scope)
    val downloadsStore = DownloadsStore(dataStore, scope)
    val engineStore = PlayerEngineStore(dataStore, scope)
    val homeDiscoveryStore = HomeDiscoveryStore(dataStore, scope)
    val audioStore = AudioStore(dataStore, scope)
    val audioEffectsStore = AudioEffectsStore(dataStore, scope)
    val audioCacheStore = AudioCacheStore(dataStore, scope)
    val libraryStore = LibraryStore(dataStore, scope)
    val navigationStore = NavigationStore(dataStore, scope)
    val networkOfflineStore = NetworkOfflineStore(dataStore, scope)
    val notificationStore = NotificationStore(dataStore, scope)
    val screensaverStore = ScreensaverStore(dataStore, scope)
    val securityStore = SecurityStore(dataStore, scope)
    val subtitleLanguageStore = SubtitleLanguageStore(dataStore, scope)
    val syncPlayCastStore = SyncPlayCastStore(dataStore, scope)
    val experimentalStore = ExperimentalStore(dataStore, scope)
    val projections = com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections(
        scope,
        playbackStore,
        videoPlayerStore,
        engineStore,
        subtitleLanguageStore,
        audioStore,
        audioEffectsStore,
        audioCacheStore,
        appearanceStore,
        homeDiscoveryStore,
        libraryStore,
        navigationStore,
        downloadsStore,
        networkOfflineStore,
        notificationStore,
        syncPlayCastStore,
        securityStore,
        experimentalStore,
    )
    return UserPreferencesStore(
        scope,
        dataStore,
        projections,
        playbackStore,
        appearanceStore,
        videoPlayerStore,
        downloadsStore,
        engineStore,
        homeDiscoveryStore,
        audioStore,
        audioEffectsStore,
        audioCacheStore,
        libraryStore,
        navigationStore,
        networkOfflineStore,
        notificationStore,
        screensaverStore,
        securityStore,
        subtitleLanguageStore,
        syncPlayCastStore,
        experimentalStore,
    )
}
