package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import kotlinx.coroutines.flow.StateFlow

/**
 * One owner of the settings "show advanced sections" gate — the read flow and
 * the write command over [AppearanceStore]'s showAdvancedSettings field.
 *
 * Nine settings ViewModels hand-copied the identical pair
 * (`appearanceStore.showAdvancedSettings` +
 * `editor.edit { appearance.setShowAdvancedSettings(enabled) }`); each now
 * keeps its public members (`showAdvancedSettings` /
 * `setShowAdvancedSettings`) and delegates here. The field itself stays on
 * [AppearanceStore] — this is the settings-facing facade, not a second home.
 */
internal class AdvancedSettingsGate(
    appearanceStore: AppearanceStore,
    private val editor: PreferencesEditor,
) {

    /** Whether settings screens expose their advanced sections. */
    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    /** Persists the gate; every subscribing screen's flow updates through the store. */
    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }
}
