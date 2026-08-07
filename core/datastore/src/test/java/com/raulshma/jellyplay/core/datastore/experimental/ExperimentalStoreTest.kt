package com.raulshma.jellyplay.core.datastore.experimental

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.ExperimentalFeature
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
 * Exercises the experimental + misc-app preference store: defaults, JSON
 * round-trip for the experimental feature set, the language-nullable setter,
 * and the dismissed-update one-time-state setter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExperimentalStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ExperimentalStore
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get(context)
            dataStore.edit { it.clear() }
            store = ExperimentalStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.experimental.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.experimental.first()
        assertTrue(slice.enabledExperimentalFeatures.isEmpty())
        assertTrue(slice.selfUpdateCheckEnabled)
        assertNull(slice.appLanguage)
        assertTrue(slice.showShareMediaOption)
        assertFalse(slice.hideSearchHistory)
        assertFalse(slice.preferAudioDescription)
        assertNull(slice.dismissedUpdateVersion)
        assertEquals(0L, slice.dismissedUpdateAtMs)
    }

    @Test
    fun `setEnabledExperimentalFeatures round-trips`() = runTest {
        val features = setOf(ExperimentalFeature.HOME_CARD_CLIPPING, ExperimentalFeature.MEDIA_CARD_PEEK)
        store.setEnabledExperimentalFeatures(features)
        assertEquals(features, store.experimental.first().enabledExperimentalFeatures)
    }

    @Test
    fun `setEnabledExperimentalFeatures ignores unknown stored names`() = runTest {
        // An unknown name persisted by a prior app version must not break the decode.
        store.setEnabledExperimentalFeatures(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION))
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("enabled_experimental_features")] =
                """["DIRECT_ARR_INTEGRATION","BOGUS_FEATURE"]"""
        }
        val slice = store.experimental.first()
        assertEquals(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION), slice.enabledExperimentalFeatures)
    }

    @Test
    fun `setSelfUpdateCheckEnabled round-trips`() = runTest {
        store.setSelfUpdateCheckEnabled(false)
        assertFalse(store.experimental.first().selfUpdateCheckEnabled)
    }

    @Test
    fun `setAppLanguage round-trips and clears`() = runTest {
        store.setAppLanguage("fr")
        assertEquals("fr", store.experimental.first().appLanguage)
        store.setAppLanguage(null)
        assertNull(store.experimental.first().appLanguage)
    }

    @Test
    fun `setShowShareMediaOption round-trips`() = runTest {
        store.setShowShareMediaOption(false)
        assertFalse(store.experimental.first().showShareMediaOption)
    }

    @Test
    fun `setHideSearchHistory round-trips`() = runTest {
        store.setHideSearchHistory(true)
        assertTrue(store.experimental.first().hideSearchHistory)
    }

    @Test
    fun `setPreferAudioDescription round-trips`() = runTest {
        store.setPreferAudioDescription(true)
        assertTrue(store.experimental.first().preferAudioDescription)
    }

    @Test
    fun `setDismissedUpdate writes version and timestamp`() = runTest {
        store.setDismissedUpdate("1.2.3", 1_700_000_000_000L)
        val slice = store.experimental.first()
        assertEquals("1.2.3", slice.dismissedUpdateVersion)
        assertEquals(1_700_000_000_000L, slice.dismissedUpdateAtMs)
    }

    @Test
    fun `setDismissedUpdate null clears both keys`() = runTest {
        store.setDismissedUpdate("1.2.3", 1_700_000_000_000L)
        store.setDismissedUpdate(null)
        val slice = store.experimental.first()
        assertNull(slice.dismissedUpdateVersion)
        assertEquals(0L, slice.dismissedUpdateAtMs)
    }
}
