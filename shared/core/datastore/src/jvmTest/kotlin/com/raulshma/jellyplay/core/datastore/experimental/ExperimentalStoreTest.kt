package com.raulshma.jellyplay.core.datastore.experimental

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
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
 * Exercises the experimental + misc-app preference store: defaults, JSON
 * round-trip for the experimental feature set, the language-nullable setter,
 * and the dismissed-update one-time-state setter.
 */
class ExperimentalStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ExperimentalStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
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
        assertFalse(slice.selfUpdateDownloadEnabled)
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
    fun `setSelfUpdateDownloadEnabled round-trips`() = runTest {
        store.setSelfUpdateDownloadEnabled(true)
        assertTrue(store.experimental.first().selfUpdateDownloadEnabled)
    }

    @Test
    fun `setAppLanguage round-trips and clears`() = runTest {
        store.setAppLanguage("fr")
        assertEquals(store.experimental.first().appLanguage, "fr")
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
        assertEquals(slice.dismissedUpdateVersion, "1.2.3")
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

    @Test
    fun `updateDismissPeriod defaults to 24h`() = runTest {
        assertEquals(UpdateDismissPeriod.HOURS_24, store.experimental.first().updateDismissPeriod)
    }

    @Test
    fun `setUpdateDismissPeriod round-trips`() = runTest {
        store.setUpdateDismissPeriod(UpdateDismissPeriod.NEVER)
        assertEquals(UpdateDismissPeriod.NEVER, store.experimental.first().updateDismissPeriod)
        store.setUpdateDismissPeriod(UpdateDismissPeriod.WEEK_1)
        assertEquals(UpdateDismissPeriod.WEEK_1, store.experimental.first().updateDismissPeriod)
    }

    @Test
    fun `unknown persisted dismiss period name falls back to default`() = runTest {
        store.setUpdateDismissPeriod(UpdateDismissPeriod.DAYS_3)
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("update_dismiss_period")] = "BOGUS"
        }
        assertEquals(UpdateDismissPeriod.HOURS_24, store.experimental.first().updateDismissPeriod)
    }
}
