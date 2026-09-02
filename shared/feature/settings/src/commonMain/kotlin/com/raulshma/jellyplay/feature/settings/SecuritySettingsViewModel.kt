package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.SecurityPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.StateFlow

class SecuritySettingsViewModel(
    private val store: UserPreferencesStore,
    private val projections: com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections,
    private val appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val authRepository: AuthRepository,
) : JellyPlayViewModel() {

    /** Security preference slice — recomposes this screen only on security-key writes. */
    val securityPreferences: StateFlow<SecurityPreferences> = projections.securityPreferences

    val showAdvancedSettings: StateFlow<Boolean> = appearanceStore.showAdvancedSettings

    fun setShowAdvancedSettings(enabled: Boolean) =
        editor.edit { appearance.setShowAdvancedSettings(enabled) }

    fun setPinLockEnabled(enabled: Boolean) = editor.setPinLockEnabled(enabled)

    fun setPin(pin: String) = editor.setPin(pin)

    fun clearPin() = editor.clearPin()

    suspend fun verifyPin(pin: String): Boolean = editor.verifyPin(pin)

    fun setBiometricLockEnabled(enabled: Boolean) = editor.setBiometricLockEnabled(enabled)

    fun setUsePinForPlayerLock(enabled: Boolean) = editor.setUsePinForPlayerLock(enabled)

    fun setAutoLockTimerMs(ms: Long) = editor.setAutoLockTimerMs(ms)

    fun setRemoteControlEnabled(enabled: Boolean) = editor.setRemoteControlEnabled(enabled)

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
