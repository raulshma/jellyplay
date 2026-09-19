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
import kotlinx.coroutines.launch

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

    /**
     * Edit facade for the Seerr step: everything the step writes, over the
     * same two stores the former per-field VM forwarders used. Passed to the
     * step as a single parameter so the screen doesn't wire 13 lambdas.
     */
    val seerrEditActions =
        SeerrEditActions(scope, seerrPreferencesStore, seerrSecureCredentialsStore)
}

/**
 * Every Seerr write the onboarding wizard's Seerr step performs, folded into
 * one facade (the `SettingsNavActions` idiom: a plain class of function
 * members constructed once by the owning ViewModel and handed to the screen
 * as a single parameter, instead of one `onSet*` lambda per field).
 *
 * Each write is fire-and-forget on the constructing ViewModel's scope and is
 * routed to the store that owns the field: plain preferences go to
 * [SeerrPreferencesStore]; the API key and password go to
 * [SeerrSecureCredentialsStore].
 */
class SeerrEditActions(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val preferencesStore: SeerrPreferencesStore,
    private val credentialsStore: SeerrSecureCredentialsStore,
) {
    fun setServerUrl(url: String) = scope.launch { preferencesStore.setServerUrl(url) }

    fun setApiKey(key: String) = scope.launch { credentialsStore.setApiKey(key) }

    fun setAuthMethod(method: SeerrAuthMethod) = scope.launch { preferencesStore.setAuthMethod(method) }

    fun setUsername(username: String) = scope.launch { preferencesStore.setUsername(username) }

    fun setEmail(email: String) = scope.launch { preferencesStore.setEmail(email) }

    fun setPassword(password: String) = scope.launch { credentialsStore.setPassword(password) }

    fun setEnabled(enabled: Boolean) = scope.launch { preferencesStore.setEnabled(enabled) }

    fun setSearchEnabled(enabled: Boolean) = scope.launch { preferencesStore.setSearchEnabled(enabled) }

    fun setRecommendationsEnabled(enabled: Boolean) =
        scope.launch { preferencesStore.setRecommendationsEnabled(enabled) }

    fun setDiscoverEnabled(enabled: Boolean) = scope.launch { preferencesStore.setDiscoverEnabled(enabled) }

    fun setStreamingRegion(region: String) = scope.launch { preferencesStore.setStreamingRegion(region) }

    fun setDiscoverRegion(region: String) = scope.launch { preferencesStore.setDiscoverRegion(region) }

    fun disconnect() = scope.launch { preferencesStore.disconnect() }
}
