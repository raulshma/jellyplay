package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the screensaver / dream preference store: defaults, JSON round-trip
 * for the dream image categories, and the long/bool/string setters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreensaverStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ScreensaverStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = ScreensaverStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.screensaver.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.screensaver.first()
        assertEquals(setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES), slice.dreamImageCategories)
        assertEquals(15_000L, slice.dreamSlideshowIntervalMs)
        assertTrue(slice.dreamKenBurnsEnabled)
        assertEquals(DreamTransitionStyle.CROSSFADE, slice.dreamTransitionStyle)
        assertTrue(slice.dreamShowTitle)
    }

    @Test
    fun `setDreamImageCategories round-trips`() = runTest {
        val categories = setOf(DreamImageCategory.MOVIES, DreamImageCategory.MUSIC)
        store.setDreamImageCategories(categories)
        assertEquals(categories, store.screensaver.first().dreamImageCategories)
    }

    @Test
    fun `setDreamSlideshowIntervalMs round-trips`() = runTest {
        store.setDreamSlideshowIntervalMs(30_000L)
        assertEquals(30_000L, store.screensaver.first().dreamSlideshowIntervalMs)
    }

    @Test
    fun `setDreamKenBurnsEnabled round-trips`() = runTest {
        store.setDreamKenBurnsEnabled(false)
        assertFalse(store.screensaver.first().dreamKenBurnsEnabled)
    }

    @Test
    fun `setDreamTransitionStyle round-trips`() = runTest {
        store.setDreamTransitionStyle(DreamTransitionStyle.SLIDE)
        assertEquals(DreamTransitionStyle.SLIDE, store.screensaver.first().dreamTransitionStyle)
    }

    @Test
    fun `setDreamShowTitle round-trips`() = runTest {
        store.setDreamShowTitle(false)
        assertFalse(store.screensaver.first().dreamShowTitle)
    }
}
