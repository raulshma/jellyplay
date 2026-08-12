package com.raulshma.jellyplay.core.datastore.search

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
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
 * Exercises [SettingsRecentsStore]: ordered id storage with dedup, move-to-front,
 * cap-at-5, clear, and lenient decoding of a missing or corrupt blob.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRecentsStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: SettingsRecentsStore

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            val dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = SettingsRecentsStore(dataStore, scope)
            // Drain the Eagerly-cached flow so the cleared state is observed
            // before each test writes + reads.
            store.recents.first()
        }
    }

    @Test
    fun `recents empty by default`() = runTest {
        assertTrue(store.recents.first().isEmpty())
    }

    @Test
    fun `addRecent prepends most recent first`() = runTest {
        store.addRecent("appearance")
        store.addRecent("playback")
        store.addRecent("audio")
        assertEquals(listOf("audio", "playback", "appearance"), store.recents.first())
    }

    @Test
    fun `addRecent caps at MAX_RECENTS`() = runTest {
        repeat(7) { i -> store.addRecent((i + 1).toString()) }
        val recents = store.recents.first()
        assertEquals(SettingsRecentsStore.MAX_RECENTS, recents.size)
        // Adding in ascending order means the last-added id is newest (first).
        assertEquals("7", recents.first())
        assertEquals("3", recents.last())
    }

    @Test
    fun `addRecent dedups and moves existing id to front`() = runTest {
        store.addRecent("a")
        store.addRecent("b")
        store.addRecent("c") // [c, b, a]
        store.addRecent("a") // a removed then re-prepended -> [a, c, b]
        assertEquals(listOf("a", "c", "b"), store.recents.first())
    }

    @Test
    fun `addRecent trims whitespace and ignores blank ids`() = runTest {
        store.addRecent("  appearance  ")
        store.addRecent("   ")
        assertEquals(listOf("appearance"), store.recents.first())
    }

    @Test
    fun `clearRecents empties the list`() = runTest {
        store.addRecent("appearance")
        store.addRecent("playback")
        store.clearRecents()
        assertTrue(store.recents.first().isEmpty())
    }

    @Test
    fun `corrupt persisted blob decodes to empty`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = TestDataStoreProvider.get(context)
        // Write a malformed JSON blob straight to the key to simulate corruption.
        dataStore.edit { it[SettingsRecentsStore.Keys.SETTINGS_RECENTS] = "{not valid json" }
        assertTrue(store.recents.first().isEmpty())
    }
}
