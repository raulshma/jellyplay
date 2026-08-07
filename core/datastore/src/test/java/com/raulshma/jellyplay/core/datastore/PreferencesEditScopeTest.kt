package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies [PreferencesEditor] routes its [PreferencesEditor.edit] block to the
 * owning store via [PreferencesEditScope], and that the named convenience
 * setters + reset/clear machinery still delegate correctly after the Stage A
 * repoint (call sites now read `editor.edit { appearance.setX() }` instead of
 * the demolished facade forwarding setters).
 *
 * The scope runs on [Dispatchers.Unconfined] so the editor's fire-and-forget
 * `scope.launch` completes inline within each `runTest` block.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesEditScopeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var store: UserPreferencesStore
    private lateinit var editor: PreferencesEditor

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            graph = createPreferenceSliceGraph(scope, dataStore)
            store = createUserPreferencesStore(scope, dataStore)
            val editScope = PreferencesEditScope(
                graph.playbackStore, graph.appearanceStore, graph.videoPlayerStore,
                graph.downloadsStore, graph.engineStore, graph.homeDiscoveryStore,
                graph.audioStore, graph.audioEffectsStore, graph.audioCacheStore,
                graph.libraryStore, graph.navigationStore, graph.networkOfflineStore,
                graph.notificationStore, graph.screensaverStore, graph.securityStore,
                graph.subtitleLanguageStore, graph.syncPlayCastStore, graph.experimentalStore,
                graph.appRuntimeStateStore,
            )
            editor = PreferencesEditor(scope, editScope, store)
            // Drain cached slice flows so the cleared state is observed first.
            graph.appearanceStore.appearance.first()
            graph.playbackStore.playback.first()
        }
    }

    @Test
    fun `edit block routes an appearance write to AppearanceStore`() = runTest {
        editor.edit { appearance.setThemeMode(ThemeMode.DARK) }.join()
        assertEquals(ThemeMode.DARK, graph.appearanceStore.appearance.first().themeMode)
    }

    @Test
    fun `edit block routes a playback write to PlaybackStore`() = runTest {
        editor.edit {
            playback.setPreferredPlayer(PlayerType.MPV)
            playback.setStreamingQuality(StreamingQuality.UHD_4K)
        }.join()
        val slice = graph.playbackStore.playback.first()
        assertEquals(PlayerType.MPV, slice.preferredPlayer)
        assertEquals(StreamingQuality.UHD_4K, slice.streamingQuality)
    }

    @Test
    fun `named setThemeMode convenience delegates via the scope`() = runTest {
        editor.setThemeMode(ThemeMode.LIGHT).join()
        assertEquals(ThemeMode.LIGHT, graph.appearanceStore.appearance.first().themeMode)
    }

    @Test
    fun `named setPreferredPlayer convenience delegates via the scope`() = runTest {
        editor.setPreferredPlayer(PlayerType.MPV).join()
        assertEquals(PlayerType.MPV, graph.playbackStore.playback.first().preferredPlayer)
    }

    @Test
    fun `resetCategory delegates to the facade reset machinery`() = runTest {
        // Write an appearance field, then reset the appearance category — the
        // value must return to its default. ThemeMode default is SYSTEM.
        editor.edit { appearance.setThemeMode(ThemeMode.DARK) }.join()
        assertEquals(ThemeMode.DARK, graph.appearanceStore.appearance.first().themeMode)

        editor.resetCategory(PreferenceResetCategory.APPEARANCE).join()

        assertEquals(ThemeMode.SYSTEM, graph.appearanceStore.appearance.first().themeMode)
    }

    @Test
    fun `clearAllPreferences wipes store slices but preserves onboarding flag`() = runTest {
        val dataStore = TestDataStoreProvider.get(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
        )
        // Stamp onboarding + a preference, then clear.
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")] = true
        }
        editor.edit { appearance.setThemeMode(ThemeMode.DARK) }.join()
        assertEquals(ThemeMode.DARK, graph.appearanceStore.appearance.first().themeMode)

        editor.clearAllPreferences().join()

        // Onboarding survives; the theme write is gone.
        val cleared = graph.appearanceStore.appearance.first()
        assertEquals(ThemeMode.SYSTEM, cleared.themeMode)
        val onboardingKey = androidx.datastore.preferences.core.booleanPreferencesKey("onboarding_completed")
        val onboardingAfter = dataStore.data.first()[onboardingKey]
        assertTrue("onboarding flag must survive clearAllPreferences", onboardingAfter == true)
    }
}
