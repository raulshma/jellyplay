package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
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
    val widgetDataStore = WidgetDataStore(dataStore, scope)
    val serverIdentityStore = ServerIdentityStore(dataStore, scope)
    val pinRateLimiter = PinRateLimiter(dataStore, scope)
    val playbackStore = PlaybackStore(dataStore, scope)
    val appearanceStore = AppearanceStore(dataStore, scope)
    val videoPlayerStore = VideoPlayerStore(dataStore, scope)
    val downloadsStore = DownloadsStore(dataStore, scope)
    val engineStore = PlayerEngineStore(dataStore, scope)
    val homeDiscoveryStore = HomeDiscoveryStore(dataStore, scope)
    return UserPreferencesStore(
        scope,
        dataStore,
        widgetDataStore,
        serverIdentityStore,
        pinRateLimiter,
        playbackStore,
        appearanceStore,
        videoPlayerStore,
        downloadsStore,
        engineStore,
        homeDiscoveryStore,
    )
}
