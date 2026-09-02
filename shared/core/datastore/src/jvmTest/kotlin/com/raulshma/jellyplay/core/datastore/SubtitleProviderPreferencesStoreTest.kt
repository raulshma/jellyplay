package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the subtitle-provider preference store: the per-kind enable flags
 * (Wyzie / OpenSubtitles; Jellyfin is always on and untoggleable), and the
 * credentials tick — writing to the secure store (which has no Flow API) must
 * re-emit the hot [SubtitleProviderPreferencesStore.credentials] snapshot.
 */
class SubtitleProviderPreferencesStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secureStorage: FakeSecureKeyValueStorage
    private lateinit var secureCredentialsStore: SubtitleProviderSecureCredentialsStore
    private lateinit var store: SubtitleProviderPreferencesStore

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            secureStorage = FakeSecureKeyValueStorage()
            secureCredentialsStore = SubtitleProviderSecureCredentialsStore(secureStorage)
            store = SubtitleProviderPreferencesStore(dataStore, secureCredentialsStore)
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val prefs = store.preferences.first()
        assertFalse(prefs.wyzieEnabled)
        assertFalse(prefs.openSubtitlesEnabled)
        assertFalse(prefs.isEnabled(SubtitleProviderKind.WYZIE))
        assertFalse(prefs.isEnabled(SubtitleProviderKind.OPENSUBTITLES))
        // Jellyfin uses the active server session — always on.
        assertTrue(prefs.isEnabled(SubtitleProviderKind.JELLYFIN))
    }

    @Test
    fun `per-kind enable flags persist independently`() = runTest {
        store.setWyzieEnabled(true)

        val wyzieOnly = store.preferences.first()
        assertTrue(wyzieOnly.wyzieEnabled)
        assertFalse(wyzieOnly.openSubtitlesEnabled)

        store.setOpenSubtitlesEnabled(true)

        val both = store.preferences.first()
        assertTrue(both.wyzieEnabled)
        assertTrue(both.openSubtitlesEnabled)

        store.setWyzieEnabled(false)

        val openSubtitlesOnly = store.preferences.first()
        assertFalse(openSubtitlesOnly.wyzieEnabled)
        assertTrue(openSubtitlesOnly.openSubtitlesEnabled)
    }

    @Test
    fun `credentials tick starts from the current secure-store snapshot`() = runTest {
        // Seed BEFORE constructing the store: the tick must be pre-populated so
        // the first collection is correct without requiring a poke.
        secureCredentialsStore.setCredentials(
            SubtitleProviderKind.WYZIE,
            SubtitleProviderCredentials.Wyzie(apiKey = "seeded-key"),
        )
        val seededStore = SubtitleProviderPreferencesStore(dataStore, secureCredentialsStore)

        assertEquals(
            mapOf(SubtitleProviderKind.WYZIE to SubtitleProviderCredentials.Wyzie(apiKey = "seeded-key")),
            seededStore.credentials.first(),
        )
    }

    @Test
    fun `setCredentials re-emits the credentials tick after the secure-store write`() = runTest {
        assertEquals(emptyMap(), store.credentials.first())

        val wyzie = SubtitleProviderCredentials.Wyzie(apiKey = "k1")
        store.setCredentials(SubtitleProviderKind.WYZIE, wyzie)

        assertEquals(mapOf(SubtitleProviderKind.WYZIE to wyzie), store.credentials.first())
        assertEquals(wyzie, store.getCredentials(SubtitleProviderKind.WYZIE))

        val openSubtitles = SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p")
        store.setCredentials(SubtitleProviderKind.OPENSUBTITLES, openSubtitles)

        val both = store.credentials.first()
        assertEquals(wyzie, both[SubtitleProviderKind.WYZIE])
        assertEquals(openSubtitles, both[SubtitleProviderKind.OPENSUBTITLES])
    }

    @Test
    fun `clearCredentials re-emits the tick without the cleared kind`() = runTest {
        val wyzie = SubtitleProviderCredentials.Wyzie(apiKey = "k1")
        val openSubtitles = SubtitleProviderCredentials.OpenSubtitles(username = "u", password = "p")
        store.setCredentials(SubtitleProviderKind.WYZIE, wyzie)
        store.setCredentials(SubtitleProviderKind.OPENSUBTITLES, openSubtitles)

        store.clearCredentials(SubtitleProviderKind.WYZIE)

        assertEquals(
            mapOf(SubtitleProviderKind.OPENSUBTITLES to openSubtitles),
            store.credentials.first(),
        )
        assertNull(store.getCredentials(SubtitleProviderKind.WYZIE))
    }

    @Test
    fun `credentials round-trip through the secure store preserves fields`() = runTest {
        val openSubtitles = SubtitleProviderCredentials.OpenSubtitles(
            username = "user",
            password = "pass",
            jwt = "jwt-token",
            jwtExpiresAt = 1_720_000_000_000,
        )
        store.setCredentials(SubtitleProviderKind.OPENSUBTITLES, openSubtitles)

        assertEquals(openSubtitles, secureCredentialsStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES))
        assertTrue(store.credentials.first().getValue(SubtitleProviderKind.OPENSUBTITLES).isConfigured)
    }
}
