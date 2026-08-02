package com.raulshma.jellyplay.core.datastore

import androidx.test.core.app.ApplicationProvider
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the factory-reset key coverage. The union of every
 * [PreferenceResetCategory]'s key list (plus the runtime-state exclusion set)
 * must cover every `Preferences.Key` declared in `UserPreferencesStore.Keys`,
 * so adding a new user setting cannot silently fall out of the reset surface.
 *
 * The reflective enumeration lives in [UserPreferencesStore.declaredKeys]; this
 * test just asserts the diff is empty and prints any uncovered key on failure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserPreferencesStoreResetCoverageTest {

    @Test
    fun `every preference key is covered by reset or exclusion`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dataStore = TestDataStoreProvider.get(context)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = createUserPreferencesStore(scope, dataStore)

        val uncovered = store.uncoveredResetKeys()

        assertTrue(
            "Uncovered preference keys (add to a resetCategory list or resetExcludedKeys): " +
                uncovered.joinToString(", ") { keyForDebug(it) },
            uncovered.isEmpty(),
        )
    }

    /** Best-effort name for a Preferences.Key — its public `name` field. */
    private fun keyForDebug(key: androidx.datastore.preferences.core.Preferences.Key<*>): String {
        return runCatching {
            key.javaClass.getDeclaredField("name").apply { isAccessible = true }.get(key) as? String
        }.getOrNull() ?: key.toString()
    }
}
