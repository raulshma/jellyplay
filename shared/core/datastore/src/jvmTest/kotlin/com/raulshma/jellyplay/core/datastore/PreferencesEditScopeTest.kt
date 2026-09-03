package com.raulshma.jellyplay.core.datastore

import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.DownloadScheduleWindow
import com.raulshma.jellyplay.core.model.HomeMode
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    // ------------------------------------------------------------------
    // Named convenience setters: one representative route per remaining
    // owning store, pinning that the editor fan-out reaches the store the
    // field belongs to (the full setter list is pure forwarding).
    // ------------------------------------------------------------------

    @Test
    fun `named setHomeMode convenience delegates to HomeDiscoveryStore`() = runTest {
        // HomeDiscovery prefs are user-scoped: editForUser no-ops without an
        // active user, so activate one through the production seam first
        // (HomeDiscoveryStoreTest's activate() pattern).
        ServerIdentityStore(TestDataStoreProvider.get(), scope).setActiveUser("user-1")
        editor.setHomeMode(HomeMode.MUSIC).join()
        assertEquals(HomeMode.MUSIC, graph.homeDiscoveryStore.homeDiscovery.first().homeMode)
    }

    @Test
    fun `named setNavBarShowLabels convenience delegates to NavigationStore`() = runTest {
        editor.setNavBarShowLabels(false).join()
        assertEquals(false, graph.navigationStore.navigation.first().navBarShowLabels)
    }

    @Test
    fun `named setVideoSeekDurationMs convenience delegates to VideoPlayerStore`() = runTest {
        editor.setVideoSeekDurationMs(30_000L).join()
        assertEquals(30_000L, graph.videoPlayerStore.videoPlayer.first().videoSeekDurationMs)
    }

    @Test
    fun `named setAudioDefaultSpeed convenience delegates to AudioStore`() = runTest {
        editor.setAudioDefaultSpeed(1.5f).join()
        assertEquals(1.5f, graph.audioStore.audio.first().audioDefaultSpeed)
    }

    @Test
    fun `named setPreferredSubtitleLanguage convenience delegates to SubtitleLanguageStore`() = runTest {
        editor.setPreferredSubtitleLanguage("jpn").join()
        assertEquals("jpn", graph.subtitleLanguageStore.subtitle.first().preferredSubtitleLanguage)
    }

    @Test
    fun `named setDownloadScheduleWindow convenience delegates to DownloadsStore`() = runTest {
        val window = DownloadScheduleWindow(startHour = 1, endHour = 5, wifiOnly = false)
        editor.setDownloadScheduleWindow(window).join()
        assertEquals(window, graph.downloadsStore.downloads.first().downloadScheduleWindow)
    }

    @Test
    fun `security setters route through SecurityStore without the edit scope`() = runTest {
        // These go through `run { securityStore... }` (not `edit`), so they must
        // still land on the shared DataStore via the owning store.
        editor.setPinLockEnabled(true).join()
        editor.setAutoLockTimerMs(60_000L).join()

        val security = graph.securityStore.security.first()
        assertTrue(security.pinLockEnabled)
        assertEquals(60_000L, security.autoLockTimerMs)
    }

    @Test
    fun `setPin then verifyPin round-trips through the security store`() = runTest {
        editor.setPin("1234").join()

        assertTrue(editor.verifyPin("1234"), "the stored pin must verify")
        assertTrue(!editor.verifyPin("9999"), "a wrong pin must not verify")
    }
}
