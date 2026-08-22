package com.raulshma.jellyplay.core.datastore.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the app-runtime-state store (favorite channels, last live-TV
 * channel, Watch Later playlist id, onboarding flag, recent DLNA devices),
 * focusing on the `restore(slice)` round-trip that backs the v2 backup import.
 */
class AppRuntimeStateStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AppRuntimeStateStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = AppRuntimeStateStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.state.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val state = store.state.first()
        assertTrue(state.favoriteChannels.isEmpty())
        assertNull(state.liveTvLastChannelId)
        assertNull(state.watchLaterPlaylistId)
        assertFalse(state.onboardingCompleted)
        assertTrue(state.recentDlnaDevices.isEmpty())
    }

    @Test
    fun `restore(slice) round-trips a fully-populated state`() = runTest {
        val slice = AppRuntimeState(
            favoriteChannels = setOf("music", "news"),
            liveTvLastChannelId = "channel-42",
            watchLaterPlaylistId = "playlist-7",
            onboardingCompleted = true,
            recentDlnaDevices = listOf(
                DlnaDeviceRef("device-1", "Living Room TV", "http://192.168.1.10:8200"),
            ),
        )

        store.restore(slice)

        assertEquals(slice, store.state.first())
    }

    @Test
    fun `restore(slice) with null ids leaves any prior id untouched`() = runTest {
        // Seed a prior last-channel id.
        store.restore(
            AppRuntimeState(liveTvLastChannelId = "channel-1", watchLaterPlaylistId = "playlist-1"),
        )
        assertEquals(store.state.first().liveTvLastChannelId, "channel-1")

        // A restore that omits the nullable ids must NOT clear them (matches the
        // legacy `?.let` behaviour documented on restore).
        store.restore(AppRuntimeState(onboardingCompleted = true))

        val after = store.state.first()
        assertEquals(after.liveTvLastChannelId, "channel-1")
        assertEquals(after.watchLaterPlaylistId, "playlist-1")
        assertTrue(after.onboardingCompleted)
    }
}
