package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import okio.Path.Companion.toPath

/**
 * Provides the single shared `"user_prefs"` DataStore instance for datastore
 * module tests. AndroidX DataStore throws at runtime if two instances resolve
 * to the same `.preferences_pb` file in one process, so tests cannot construct
 * each store with its own factory — they must share one. Every test resets the
 * file in `@BeforeTest` (`dataStore.edit { it.clear() }`), matching the
 * per-class isolation the Robolectric suite had before the KMP port.
 */
object TestDataStoreProvider {
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-datastore-jvmTest").apply { mkdirs() }
            File(dir, "user_prefs.preferences_pb").absolutePath.toPath()
        }
    }

    fun get(): DataStore<Preferences> = dataStore
}

/**
 * Wired graph of the 18 domain stores + [AppRuntimeStateStore] + the
 * [PreferenceProjections] read layer, all sharing one `"user_prefs"` DataStore
 * (AndroidX requires a single instance per file per process). Tests that need
 * direct store access (writes) + a projections/aggregator read surface use this
 * instead of [createUserPreferencesStore], which only returns the facade.
 */
data class PreferenceSliceGraph(
    val playbackStore: PlaybackStore,
    val appearanceStore: AppearanceStore,
    val videoPlayerStore: VideoPlayerStore,
    val downloadsStore: DownloadsStore,
    val engineStore: PlayerEngineStore,
    // Home-discovery keys are namespaced per active user (u_<userId>::) — the
    // identity store is exposed so tests can drive user switches through the
    // same production seam (active_user_id in the shared file).
    val identityStore: ServerIdentityStore,
    val homeDiscoveryStore: HomeDiscoveryStore,
    val audioStore: AudioStore,
    val audioEffectsStore: AudioEffectsStore,
    val audioCacheStore: AudioCacheStore,
    val libraryStore: LibraryStore,
    val navigationStore: NavigationStore,
    val networkOfflineStore: NetworkOfflineStore,
    val notificationStore: NotificationStore,
    val screensaverStore: ScreensaverStore,
    val securityStore: SecurityStore,
    val subtitleLanguageStore: SubtitleLanguageStore,
    val syncPlayCastStore: SyncPlayCastStore,
    val experimentalStore: ExperimentalStore,
    val appRuntimeStateStore: AppRuntimeStateStore,
    val projections: PreferenceProjections,
)

/**
 * Builds the [PreferenceSliceGraph]: all 18 domain stores + [AppRuntimeStateStore]
 * + [PreferenceProjections], each sharing the same `"user_prefs"` DataStore.
 * The single construction site for the store graph — [createUserPreferencesStore]
 * and [createPreferenceProjections] both delegate here so a new store is added
 * in exactly one place.
 */
fun createPreferenceSliceGraph(
    scope: CoroutineScope,
    dataStore: DataStore<Preferences>,
): PreferenceSliceGraph {
    val playbackStore = PlaybackStore(dataStore, scope)
    val appearanceStore = AppearanceStore(dataStore, scope)
    val videoPlayerStore = VideoPlayerStore(dataStore, scope)
    val downloadsStore = DownloadsStore(dataStore, scope)
    val engineStore = PlayerEngineStore(dataStore, scope)
    val identityStore = ServerIdentityStore(dataStore, scope)
    val homeDiscoveryStore = HomeDiscoveryStore(dataStore, scope, identityStore)
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
    val appRuntimeStateStore = AppRuntimeStateStore(dataStore, scope)
    val projections = PreferenceProjections(
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
        screensaverStore,
    )
    return PreferenceSliceGraph(
        playbackStore, appearanceStore, videoPlayerStore, downloadsStore, engineStore,
        identityStore, homeDiscoveryStore, audioStore, audioEffectsStore, audioCacheStore, libraryStore,
        navigationStore, networkOfflineStore, notificationStore, screensaverStore,
        securityStore, subtitleLanguageStore, syncPlayCastStore, experimentalStore,
        appRuntimeStateStore, projections,
    )
}

/**
 * Builds a [PreferenceProjections] read layer over a fresh store graph sharing
 * the given `"user_prefs"` DataStore. For tests that assert projection behavior
 * without needing the facade.
 */
@Suppress("TestFunctionName")
fun createPreferenceProjections(
    scope: CoroutineScope,
    dataStore: DataStore<Preferences>,
): PreferenceProjections = createPreferenceSliceGraph(scope, dataStore).projections

/**
 * Builds a [UserPreferencesStore] wired to all of its domain-store collaborators,
 * each sharing the same `"user_prefs"` DataStore. Keeps tests in sync as new
 * domain stores are added to the facade constructor.
 */
@Suppress("TestFunctionName")
fun createUserPreferencesStore(
    scope: CoroutineScope,
    dataStore: DataStore<Preferences>,
): UserPreferencesStore {
    val g = createPreferenceSliceGraph(scope, dataStore)
    return UserPreferencesStore(
        scope,
        dataStore,
        g.projections,
        g.playbackStore,
        g.appearanceStore,
        g.videoPlayerStore,
        g.downloadsStore,
        g.engineStore,
        g.homeDiscoveryStore,
        g.audioStore,
        g.audioEffectsStore,
        g.audioCacheStore,
        g.libraryStore,
        g.navigationStore,
        g.networkOfflineStore,
        g.notificationStore,
        g.screensaverStore,
        g.securityStore,
        g.subtitleLanguageStore,
        g.syncPlayCastStore,
        g.experimentalStore,
        g.appRuntimeStateStore,
    )
}
