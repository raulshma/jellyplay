package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.model.seerr.SeerrPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the Seerr (Overseerr/Jellyseerr) preference store: defaults, the
 * auth-method string persistence (including invalid-value degrade to API_KEY),
 * and the disconnect path that resets every preference AND clears the
 * [SeerrSecureCredentialsStore] secrets behind the shared
 * [FakeSecureKeyValueStorage].
 */
class SeerrPreferencesStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secureStorage: FakeSecureKeyValueStorage
    private lateinit var secureCredentialsStore: SeerrSecureCredentialsStore
    private lateinit var store: SeerrPreferencesStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            secureStorage = FakeSecureKeyValueStorage()
            secureCredentialsStore = SeerrSecureCredentialsStore(secureStorage)
            store = SeerrPreferencesStore(dataStore, secureCredentialsStore, scope)
            // Drain the Eagerly-cached state flow so the cleared state is
            // observed before each test writes + reads.
            store.preferences.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        assertEquals(SeerrPreferences(), store.preferences.first())
        assertFalse(store.isConnected.first())
    }

    @Test
    fun `server url is trimmed and drives isConnected`() = runTest {
        store.setServerUrl("  http://seerr.local:5055  ")

        val prefs = store.preferences.first()
        assertEquals("http://seerr.local:5055", prefs.serverUrl)
        assertTrue(store.isConnected.first())

        store.setServerUrl("   ")
        assertFalse(store.isConnected.first())
    }

    @Test
    fun `auth method persists`() = runTest {
        store.setAuthMethod(SeerrAuthMethod.JELLYFIN)
        assertEquals(SeerrAuthMethod.JELLYFIN, store.preferences.first().authMethod)

        store.setAuthMethod(SeerrAuthMethod.LOCAL)
        assertEquals(SeerrAuthMethod.LOCAL, store.preferences.first().authMethod)
    }

    @Test
    fun `invalid stored auth method degrades to API_KEY`() = runTest {
        dataStore.edit { it[stringPreferencesKey("seerr_auth_method")] = "NOT_A_METHOD" }

        assertEquals(SeerrAuthMethod.API_KEY, store.preferences.first().authMethod)
    }

    @Test
    fun `username and email are trimmed`() = runTest {
        store.setUsername("  jellyfin_user  ")
        store.setEmail(" user@example.com ")

        val prefs = store.preferences.first()
        assertEquals("jellyfin_user", prefs.username)
        assertEquals("user@example.com", prefs.email)
    }

    @Test
    fun `boolean and region setters persist`() = runTest {
        store.setEnabled(true)
        store.setSearchEnabled(true)
        store.setRecommendationsEnabled(true)
        store.setDiscoverEnabled(true)
        store.setDiscoverTrending(false)
        store.setDiscoverPopularMovies(false)
        store.setDiscoverPopularTv(false)
        store.setDiscoverUpcomingMovies(false)
        store.setDiscoverUpcomingTv(false)
        store.setStreamingRegion("GB")
        store.setDiscoverRegion("DE")

        val prefs = store.preferences.first()
        assertTrue(prefs.enabled)
        assertTrue(prefs.searchEnabled)
        assertTrue(prefs.recommendationsEnabled)
        assertTrue(prefs.discoverEnabled)
        assertFalse(prefs.discoverTrending)
        assertFalse(prefs.discoverPopularMovies)
        assertFalse(prefs.discoverPopularTv)
        assertFalse(prefs.discoverUpcomingMovies)
        assertFalse(prefs.discoverUpcomingTv)
        assertEquals("GB", prefs.streamingRegion)
        assertEquals("DE", prefs.discoverRegion)
    }

    @Test
    fun `disconnect resets preferences and clears secure credentials`() = runTest {
        // Seed credentials in the secure store and connect the prefs.
        secureCredentialsStore.setApiKey("secret-key")
        secureCredentialsStore.setPassword("secret-pass")
        secureCredentialsStore.setSessionCookie("cookie")
        store.setServerUrl("http://seerr.local")
        store.setUsername("user")
        store.setAuthMethod(SeerrAuthMethod.JELLYFIN)
        store.setEnabled(true)
        store.setSearchEnabled(true)
        store.setDiscoverTrending(false)
        store.setStreamingRegion("GB")

        store.disconnect()

        // The credential file is cleared...
        assertEquals("", secureCredentialsStore.getApiKey())
        assertEquals("", secureCredentialsStore.getPassword())
        assertEquals("", secureCredentialsStore.getSessionCookie())
        // ...and every preference is back to its default.
        assertEquals(SeerrPreferences(), store.preferences.first())
        assertFalse(store.isConnected.first())
    }
}
