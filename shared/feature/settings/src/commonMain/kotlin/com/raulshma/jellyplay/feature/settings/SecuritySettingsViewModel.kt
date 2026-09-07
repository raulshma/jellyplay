package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class SecuritySettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val advancedSettings: AdvancedSettingsGate,
    private val editor: PreferencesEditor,
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    /** Security preference slice — recomposes this screen only on security-key writes. */
    val securityPreferences: StateFlow<SecurityPreferences> = projections.securityPreferences

    val showAdvancedSettings: StateFlow<Boolean> = advancedSettings.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

    /**
     * Single write command for this screen: `edit { it.security.setPin("1234") }`.
     * Fire-and-forget on the same application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }

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
