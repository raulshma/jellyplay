package com.raulshma.jellyplay.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.data.repository.JellyPlayPreferencesSyncAdapter
import com.raulshma.jellyplay.core.data.session.JellyPlayFeatureGate
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "Server plugin" section's forced-lock seam: the composite sync key one
 * feature's toggle rides ([jellyPlayToggleSyncKey]) must match — character for
 * character — the `"ns/key"` form the server's admin-defaults `modes` map
 * addresses. The chain that form crosses, each pinned here:
 *
 *  1. the gate writes `pluginFeature.<featureKey>.enabled` as an ORDINARY
 *     DataStore key (dots, not slashes — [JellyPlayFeatureGate]'s constants);
 *  2. the sync adapter carries DataStore key NAMES verbatim under its `prefs`
 *     namespace (a real temp-file DataStore proves the emitted snapshot key);
 *  3. the composite `prefs/pluginFeature.<featureKey>.enabled` is what
 *     [jellyPlayFeatureLocked] looks up in the sync engine's `forcedKeys`.
 */
class JellyPlayFeatureForcedKeysTest {

    private fun newDataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ) {
            val dir = File(System.getProperty("java.io.tmpdir"), "jellyplay-forced-keys-test").apply { mkdirs() }
            File(dir, "$name.preferences_pb").absolutePath.toPath()
        }

    @Test
    fun `the toggle sync key composes the prefs namespace over the gate's DataStore key name`() {
        // Built from the gate's own constants: the toggle writer and the lock
        // reader can never drift apart. Every TOGGLEABLE feature key shares
        // the same composition shape.
        JellyPlayFeatureGate.TOGGLEABLE_FEATURES.forEach { feature ->
            assertEquals(
                "prefs/" + JellyPlayFeatureGate.TOGGLE_KEY_PREFIX + feature + JellyPlayFeatureGate.TOGGLE_KEY_SUFFIX,
                jellyPlayToggleSyncKey(feature),
            )
        }
        // The concrete shape pinned literally: dots, one slash, `enabled` suffix.
        assertEquals("prefs/pluginFeature.analytics.enabled", jellyPlayToggleSyncKey("analytics"))
    }

    @Test
    fun `the adapter's wire snapshot matches the composite the modes map addresses`() = runTest {
        val store = newDataStore("wire-${System.nanoTime()}").apply { edit { it.clear() } }
        val feature = JellyPlayPluginFeatures.Analytics
        // The gate's own write shape (setEnabled's body, minus the DataStore seam).
        store.edit { it[booleanPreferencesKey(JellyPlayFeatureGate.TOGGLE_KEY_PREFIX + feature + JellyPlayFeatureGate.TOGGLE_KEY_SUFFIX)] = true }

        val adapter = JellyPlayPreferencesSyncAdapter(store)
        val snapshot = adapter.snapshot()

        // The DataStore key name rides the wire verbatim — and the composite
        // the section looks up is exactly ns + "/" + that name.
        assertTrue(snapshot.containsKey("pluginFeature.analytics.enabled"))
        assertEquals(jellyPlayToggleSyncKey(feature), adapter.namespace + "/" + "pluginFeature.analytics.enabled")
    }

    @Test
    fun `only the forced mode locks - suggested and unset stay writable`() {
        val forcedKeys = setOf(
            "prefs/pluginFeature.analytics.enabled",
            "other/row",
        )
        assertTrue(jellyPlayFeatureLocked(JellyPlayPluginFeatures.Analytics, forcedKeys))
        // Suggested/unset keys never enter the forced set (the engine filters),
        // so the lock decision is membership only — pinned against a stray
        // feature whose key shares the prefix.
        assertFalse(jellyPlayFeatureLocked(JellyPlayPluginFeatures.Events, forcedKeys))
        assertFalse(jellyPlayFeatureLocked(JellyPlayPluginFeatures.Messages, emptySet()))
    }
}
