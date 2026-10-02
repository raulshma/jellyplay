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
import kotlinx.coroutines.withTimeout
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
            // Wait until the eagerly-cached slice has observed the cleared
            // state before each test writes + reads.
            awaitSlice { it == ExperimentalSlice() }
        }
    }

    /**
     * Race-free read for write→assert tests: `dataStore.edit` returns when the
     * value is persisted, but the slice StateFlow is updated asynchronously by
     * the Unconfined collector (data → read → stateIn chain) — a plain
     * `experimental.first()` can observe the pre-write snapshot and flake on a
     * loaded runner (seen on the macOS CI lane). Awaiting the asserted
     * condition ties the read to the write instead of to scheduler luck.
     */
    private suspend fun awaitSlice(
        timeoutMs: Long = 5_000,
        predicate: (ExperimentalSlice) -> Boolean,
    ): ExperimentalSlice = withTimeout(timeoutMs) { store.experimental.first { predicate(it) } }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = awaitSlice { it == ExperimentalSlice() }
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
        assertEquals(features, awaitSlice { it.enabledExperimentalFeatures == features }.enabledExperimentalFeatures)
    }

    @Test
    fun `setEnabledExperimentalFeatures ignores unknown stored names`() = runTest {
        // An unknown name persisted by a prior app version must not break the decode.
        store.setEnabledExperimentalFeatures(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION))
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("enabled_experimental_features")] =
                """["DIRECT_ARR_INTEGRATION","BOGUS_FEATURE"]"""
        }
        val slice = awaitSlice { it.enabledExperimentalFeatures == setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION) }
        assertEquals(setOf(ExperimentalFeature.DIRECT_ARR_INTEGRATION), slice.enabledExperimentalFeatures)
    }

    @Test
    fun `setSelfUpdateCheckEnabled round-trips`() = runTest {
        store.setSelfUpdateCheckEnabled(false)
        assertFalse(awaitSlice { !it.selfUpdateCheckEnabled }.selfUpdateCheckEnabled)
    }

    @Test
    fun `setSelfUpdateDownloadEnabled round-trips`() = runTest {
        store.setSelfUpdateDownloadEnabled(true)
        assertTrue(awaitSlice { it.selfUpdateDownloadEnabled }.selfUpdateDownloadEnabled)
    }

    @Test
    fun `setAppLanguage round-trips and clears`() = runTest {
        store.setAppLanguage("fr")
        assertEquals("fr", awaitSlice { it.appLanguage == "fr" }.appLanguage)
        store.setAppLanguage(null)
        assertNull(awaitSlice { it.appLanguage == null }.appLanguage)
    }

    @Test
    fun `setShowShareMediaOption round-trips`() = runTest {
        store.setShowShareMediaOption(false)
        assertFalse(awaitSlice { !it.showShareMediaOption }.showShareMediaOption)
    }

    @Test
    fun `setHideSearchHistory round-trips`() = runTest {
        store.setHideSearchHistory(true)
        assertTrue(awaitSlice { it.hideSearchHistory }.hideSearchHistory)
    }

    @Test
    fun `setPreferAudioDescription round-trips`() = runTest {
        store.setPreferAudioDescription(true)
        assertTrue(awaitSlice { it.preferAudioDescription }.preferAudioDescription)
    }

    @Test
    fun `setDismissedUpdate writes version and timestamp`() = runTest {
        store.setDismissedUpdate("1.2.3", 1_700_000_000_000L)
        val slice = awaitSlice { it.dismissedUpdateVersion == "1.2.3" }
        assertEquals("1.2.3", slice.dismissedUpdateVersion)
        assertEquals(1_700_000_000_000L, slice.dismissedUpdateAtMs)
    }

    @Test
    fun `setDismissedUpdate null clears both keys`() = runTest {
        store.setDismissedUpdate("1.2.3", 1_700_000_000_000L)
        store.setDismissedUpdate(null)
        val slice = awaitSlice { it.dismissedUpdateVersion == null }
        assertNull(slice.dismissedUpdateVersion)
        assertEquals(0L, slice.dismissedUpdateAtMs)
    }

    @Test
    fun `updateDismissPeriod defaults to 24h`() = runTest {
        assertEquals(UpdateDismissPeriod.HOURS_24, awaitSlice { true }.updateDismissPeriod)
    }

    @Test
    fun `setUpdateDismissPeriod round-trips`() = runTest {
        store.setUpdateDismissPeriod(UpdateDismissPeriod.NEVER)
        assertEquals(UpdateDismissPeriod.NEVER, awaitSlice { it.updateDismissPeriod == UpdateDismissPeriod.NEVER }.updateDismissPeriod)
        store.setUpdateDismissPeriod(UpdateDismissPeriod.WEEK_1)
        assertEquals(UpdateDismissPeriod.WEEK_1, awaitSlice { it.updateDismissPeriod == UpdateDismissPeriod.WEEK_1 }.updateDismissPeriod)
    }

    @Test
    fun `unknown persisted dismiss period name falls back to default`() = runTest {
        store.setUpdateDismissPeriod(UpdateDismissPeriod.DAYS_3)
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("update_dismiss_period")] = "BOGUS"
        }
        assertEquals(
            UpdateDismissPeriod.HOURS_24,
            awaitSlice { it.updateDismissPeriod == UpdateDismissPeriod.HOURS_24 }.updateDismissPeriod,
        )
    }
}
