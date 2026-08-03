package com.raulshma.jellyplay.core.datastore.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.DlnaDeviceRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the app-runtime-state store (favorite channels, last live-TV
 * channel, Watch Later playlist id, onboarding flag, recent DLNA devices),
 * focusing on the `restore(slice)` round-trip that backs the v2 backup import.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppRuntimeStateStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: AppRuntimeStateStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
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
        assertEquals("channel-1", store.state.first().liveTvLastChannelId)

        // A restore that omits the nullable ids must NOT clear them (matches the
        // legacy `?.let` behaviour documented on restore).
        store.restore(AppRuntimeState(onboardingCompleted = true))

        val after = store.state.first()
        assertEquals("channel-1", after.liveTvLastChannelId)
        assertEquals("playlist-1", after.watchLaterPlaylistId)
        assertTrue(after.onboardingCompleted)
    }
}
