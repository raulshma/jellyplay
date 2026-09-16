package com.raulshma.jellyplay.core.datastore.experimental

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Knob round-trips through the store (Stage A pilot): every knob read/write
 * must ride the store's own persistence — the same `"user_prefs"` DataStore,
 * the same keys, the same encodings as the slice projection and setters —
 * and knob reads must observe writes made through the store setters (and
 * vice versa), so a knob can never be a parallel source of truth.
 *
 * Same chassis as [ExperimentalStoreTest] (shared test DataStore, cleared
 * per test, Eagerly-cached slice drained in setup).
 */
class ExperimentalStoreKnobTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: ExperimentalStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = ExperimentalStore(dataStore, scope)
            store.experimental.first()
        }
    }

    @Test
    fun `feature knob defaults false and round-trips through the store`() = runTest {
        val knob = store.featureKnob(ExperimentalFeature.HOME_CARD_CLIPPING)
        assertFalse(knob.value.first())
        knob.set(true)
        assertTrue(knob.value.first())
        assertTrue(store.experimental.first().enabledExperimentalFeatures.contains(ExperimentalFeature.HOME_CARD_CLIPPING))
        knob.set(false)
        assertFalse(knob.value.first())
        assertFalse(store.experimental.first().enabledExperimentalFeatures.contains(ExperimentalFeature.HOME_CARD_CLIPPING))
    }

    @Test
    fun `feature knob writes ride the store's single JSON set key`() = runTest {
        // A knob write must not create a parallel slot: it composes with the
        // set setter inside the one `enabled_experimental_features` JSON key.
        store.setEnabledExperimentalFeatures(setOf(ExperimentalFeature.MEDIA_CARD_PEEK))
        store.featureKnob(ExperimentalFeature.HOME_CARD_CLIPPING).set(true)
        store.featureKnob(ExperimentalFeature.DIRECT_ARR_INTEGRATION).set(true)

        assertEquals(
            setOf(ExperimentalFeature.MEDIA_CARD_PEEK, ExperimentalFeature.HOME_CARD_CLIPPING, ExperimentalFeature.DIRECT_ARR_INTEGRATION),
            store.experimental.first().enabledExperimentalFeatures,
        )
        val raw = dataStore.data.first()[stringPreferencesKey("enabled_experimental_features")]
        assertEquals(
            setOf("MEDIA_CARD_PEEK", "HOME_CARD_CLIPPING", "DIRECT_ARR_INTEGRATION"),
            raw?.let { Json { ignoreUnknownKeys = true }.decodeFromString<Set<String>>(it) },
            "the persisted blob must stay the name-set JSON under the legacy key",
        )
    }

    @Test
    fun `simple boolean knob round-trips and matches the slice`() = runTest {
        val knob = store.hideSearchHistory
        assertFalse(knob.value.first())
        knob.set(true)
        assertTrue(knob.value.first())
        assertTrue(store.experimental.first().hideSearchHistory)
        assertEquals(true, dataStore.data.first()[booleanPreferencesKey("hide_search_history")])
    }

    @Test
    fun `enum knob round-trips through the store setter`() = runTest {
        val knob = store.updateDismissPeriod
        assertEquals(UpdateDismissPeriod.HOURS_24, knob.value.first())
        knob.set(UpdateDismissPeriod.NEVER)
        assertEquals(UpdateDismissPeriod.NEVER, knob.value.first())
        assertEquals(UpdateDismissPeriod.NEVER, store.experimental.first().updateDismissPeriod)
        assertEquals("NEVER", dataStore.data.first()[stringPreferencesKey("update_dismiss_period")])
    }

    @Test
    fun `nullable string knob round-trips and clears through the setter`() = runTest {
        val knob = store.appLanguage
        assertNull(knob.value.first())
        knob.set("fr")
        assertEquals("fr", knob.value.first())
        assertEquals("fr", store.experimental.first().appLanguage)
        knob.set(null)
        assertNull(knob.value.first())
        assertNull(store.experimental.first().appLanguage)
    }

    @Test
    fun `knob reads observe writes made through the store setters`() = runTest {
        store.setSelfUpdateCheckEnabled(false)
        assertFalse(store.selfUpdateCheckEnabled.value.first())
        store.setSelfUpdateCheckEnabled(true)
        assertTrue(store.selfUpdateCheckEnabled.value.first())

        store.setEnabledExperimentalFeatures(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION))
        assertTrue(store.featureKnob(ExperimentalFeature.DIRECT_ARR_INTEGRATION).value.first())
        assertFalse(store.featureKnob(ExperimentalFeature.MEDIA_CARD_PEEK).value.first())
    }
}
