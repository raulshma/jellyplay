package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class SecuritySettingsViewModel(
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    advancedSettings: AdvancedSettingsGate,
    editor: PreferencesEditor,
    private val authRepository: AuthRepository,
) : SettingsSectionViewModel(advancedSettings, editor) {

    /** Security preference slice — recomposes this screen only on security-key writes. */
    val securityPreferences: StateFlow<SecurityPreferences> = projections.securityPreferences

    /**
     * Verifies the entered PIN against the stored hash off the main thread —
     * a read-back, so it stays a command of its own rather than riding the
     * write-only [edit] path.
     */
    suspend fun verifyPin(pin: String): Boolean = editor.verifyPin(pin)

    fun authorizeQuickConnect(code: String, onResult: (success: Boolean, error: String?) -> Unit) {
        launch {
            authRepository.authorizeQuickConnect(code)
                .onSuccess { authorized ->
                    if (authorized) {
                        onResult(true, null)
                    } else {
                        onResult(false, "Code not found or already used")
                    }
                }
                .onFailure { e ->
                    onResult(false, e.message ?: "Authorization failed")
                }
        }
    }
}
