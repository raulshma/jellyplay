package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * The shared write-command shell of the settings ViewModels: the single
 * `edit { }` command over the injected [PreferencesEditor]. Collapses the
 * per-VM copy-paste (nine ViewModels carried the identical forwarding
 * function); the screens' `viewModel.edit { … }` call sites are unchanged.
 * Public only because its subclasses have public constructors — not a stable
 * API surface.
 */
abstract class SettingsEditorViewModel(
    protected val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    /**
     * Single write command for a screen: `edit { it.security.setPin("1234") }`.
     * Fire-and-forget on the same application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }
}

/**
 * [SettingsEditorViewModel] plus the two per-screen shells the section
 * ViewModels used to hand-copy: the advanced-settings gate re-exposure
 * ([showAdvancedSettings] + [setShowAdvancedSettings]) and the category
 * reset ([resetCategory], forwarded by the resettable screens).
 */
abstract class SettingsSectionViewModel(
    private val advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
) : SettingsEditorViewModel(editor) {

    /** Whether this screen exposes its advanced sections. */
    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    /** Persists the gate; every subscribing screen's flow updates through the store. */
    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    /**
     * Resets a single preference category, delegating to the shared
     * [PreferencesEditor] so the coverage-guarded key list stays the single
     * source of truth.
     */
    fun resetCategory(category: PreferenceResetCategory) = editor.resetCategory(category)
}
