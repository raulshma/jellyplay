package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Verifies [PreferencesEditor] — the single auditable write seam. Its surface
 * is deliberately minimal: the [PreferencesEditor.edit] block routes to the
 * owning store via [PreferencesEditScope], the PIN ops delegate to
 * SecurityStore, and the reset/clear machinery delegates to the facade. (The
 * former ~50 one-line named setters had test-only callers and were deleted —
 * their routing is now pinned where it actually lives, on the stores
 * themselves.)
 *
 * The scope runs on [Dispatchers.Unconfined] so the editor's fire-and-forget
 * `scope.launch` completes inline within each `runTest` block.
 */
class PreferencesEditScopeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var graph: PreferenceSliceGraph
    private lateinit var store: UserPreferencesStore
    private lateinit var editor: PreferencesEditor

    @BeforeTest
    fun setup() {
        runBlocking {
            val dataStore = TestDataStoreProvider.get()
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
        val dataStore = TestDataStoreProvider.get()
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
        assertTrue(onboardingAfter == true, "onboarding flag must survive clearAllPreferences")
    }

    @Test
    fun `verifyPin round-trips against the security store's hash`() = runTest {
        // The editor no longer re-exposes setPin — the write goes through the
        // edit scope (the owning store), the read through the editor seam the
        // security VM actually uses.
        editor.edit { security.setPin("1234") }.join()

        assertTrue(editor.verifyPin("1234"), "the stored pin must verify")
        assertTrue(!editor.verifyPin("9999"), "a wrong pin must not verify")
    }

    @Test
    fun `homeDiscovery writes need the per-user namespace the scope documents`() = runTest {
        // HomeDiscovery prefs are user-scoped: a write no-ops without an
        // active user. Activate one through the production seam first
        // (HomeDiscoveryStoreTest's activate() pattern), then write via edit.
        ServerIdentityStore(TestDataStoreProvider.get(), scope).setActiveUser("user-1")
        editor.edit { homeDiscovery.setHomeMode(com.raulshma.jellyplay.core.model.HomeMode.MUSIC) }.join()
        assertEquals(
            com.raulshma.jellyplay.core.model.HomeMode.MUSIC,
            graph.homeDiscoveryStore.homeDiscovery.first().homeMode,
        )
    }
}
