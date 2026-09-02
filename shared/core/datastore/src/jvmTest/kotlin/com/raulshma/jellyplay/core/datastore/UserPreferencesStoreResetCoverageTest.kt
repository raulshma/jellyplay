package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Guards the factory-reset key coverage. The union of every
 * [PreferenceResetCategory]'s key list (plus the runtime-state exclusion set)
 * must cover every `Preferences.Key` declared in `UserPreferencesStore.Keys`,
 * so adding a new user setting cannot silently fall out of the reset surface.
 *
 * The reflective enumeration lives in [UserPreferencesStore.declaredKeys]; this
 * test just asserts the diff is empty and prints any uncovered key on failure.
 */
class UserPreferencesStoreResetCoverageTest {

    @Test
    fun `every preference key is covered by reset or exclusion`() {
        val dataStore = TestDataStoreProvider.get()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = createUserPreferencesStore(scope, dataStore)

        val uncovered = store.uncoveredResetKeys()

        assertTrue(uncovered.isEmpty(), "Uncovered preference keys (add to a resetCategory list or resetExcludedKeys): " +
                uncovered.joinToString(", ") { keyForDebug(it) })
    }

    /** Best-effort name for a Preferences.Key — its public `name` field. */
    private fun keyForDebug(key: androidx.datastore.preferences.core.Preferences.Key<*>): String {
        return runCatching {
            key.javaClass.getDeclaredField("name").apply { isAccessible = true }.get(key) as? String
        }.getOrNull() ?: key.toString()
    }
}
