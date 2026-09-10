package com.raulshma.jellyplay.feature.onboarding

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.SeerrSecureCredentialsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.OnboardingPreferences
import com.raulshma.jellyplay.core.model.seerr.SeerrAuthMethod
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

/**
 * Onboarding-wizard preferences, projected centrally off the owning store
 * slices by [PreferenceProjections]. The fields span 8 domains (appearance,
 * home discovery, navigation, playback, video player, audio, subtitle,
 * security) and are combined into one holder so the screen keeps its single
 * `preferences.field` read pattern.
 */
class OnboardingViewModel(
    private val projections: PreferenceProjections,
    val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrSecureCredentialsStore: SeerrSecureCredentialsStore,
    private val editor: PreferencesEditor,
) : JellyPlayViewModel() {

    val preferences: kotlinx.coroutines.flow.StateFlow<OnboardingPreferences> =
        projections.onboardingPreferences

    val seerrPreferences = seerrPreferencesStore.preferences

    private val _currentStep = stateFlow(0)
    val currentStep = _currentStep.flow

    fun setStep(step: Int) {
        _currentStep.set(step.coerceIn(0, OnboardingStep.count - 1))
    }

    fun nextStep() {
        setStep(_currentStep.value + 1)
    }

    fun skipOnboarding() {
        // Skip jumps to the final step rather than silently completing from page 1,
        // which previously permanently dismissed the wizard (Skip == Complete). The
        // user lands on the review/finish step and can complete (or step back) from
        // there, so an accidental tap never throws away the chance to configure.
        setStep(OnboardingStep.count - 1)
    }

    fun completeOnboarding() {
        editor.edit { appRuntimeState.setOnboardingCompleted(true) }
    }

    /**
     * Single write command for the wizard's preference steps:
     * `edit { it.appearance.setThemeMode(mode) }`. Fire-and-forget on the same
     * application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }

    fun hashPin(pin: String): String = editor.hashPin(pin)

    fun setSeerrServerUrl(url: String) {
        launch { seerrPreferencesStore.setServerUrl(url) }
    }

    fun setSeerrApiKey(key: String) {
        launch { seerrSecureCredentialsStore.setApiKey(key) }
    }

    fun setSeerrAuthMethod(method: SeerrAuthMethod) {
        launch { seerrPreferencesStore.setAuthMethod(method) }
    }

    fun setSeerrUsername(username: String) {
        launch { seerrPreferencesStore.setUsername(username) }
    }

    fun setSeerrEmail(email: String) {
        launch { seerrPreferencesStore.setEmail(email) }
    }

    fun setSeerrPassword(password: String) {
        launch { seerrSecureCredentialsStore.setPassword(password) }
    }

    fun setSeerrEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setEnabled(enabled) }
    }

    fun setSeerrSearchEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setSearchEnabled(enabled) }
    }

    fun setSeerrRecommendationsEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setRecommendationsEnabled(enabled) }
    }

    fun setSeerrDiscoverEnabled(enabled: Boolean) {
        launch { seerrPreferencesStore.setDiscoverEnabled(enabled) }
    }

    fun setSeerrStreamingRegion(region: String) {
        launch { seerrPreferencesStore.setStreamingRegion(region) }
    }

    fun setSeerrDiscoverRegion(region: String) {
        launch { seerrPreferencesStore.setDiscoverRegion(region) }
    }

    fun seerrDisconnect() {
        launch { seerrPreferencesStore.disconnect() }
    }
}
