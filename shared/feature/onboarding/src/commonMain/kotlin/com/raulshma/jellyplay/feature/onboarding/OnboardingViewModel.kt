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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Onboarding-wizard preferences, projected centrally off the owning store
 * slices by [PreferenceProjections]. The fields span 8 domains (appearance,
 * home discovery, navigation, playback, video player, audio, subtitle,
 * security) and are combined into one holder so the screen keeps its single
 * `preferences.field` read pattern.
 */
class OnboardingViewModel(
    private val projections: PreferenceProjections,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrSecureCredentialsStore: SeerrSecureCredentialsStore,
    private val editor: PreferencesEditor,
    // The encrypted writes are synchronous; production runs them on IO.
    // Tests pin the test dispatcher (the LogsViewModel.parseDispatcher seam).
    private val seerrSecureWriteDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
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
        SeerrEditActions(scope, seerrPreferencesStore, seerrSecureCredentialsStore, seerrSecureWriteDispatcher)
}

/**
 * Typing-pause window (ms) for the Seerr step's per-keystroke field writes —
 * the same debounce shape as the settings discover-row editor's queries: a
 * burst of keystrokes costs one store write, not one per character.
 */
private const val SEERR_FIELD_WRITE_DEBOUNCE_MS = 300L

/**
 * Every Seerr write the onboarding wizard's Seerr step performs, folded into
 * one facade (the `SettingsNavActions` idiom: a plain class of function
 * members constructed once by the owning ViewModel and handed to the screen
 * as a single parameter, instead of one `onSet*` lambda per field).
 *
 * Click-driven writes (auth method, toggles, regions, disconnect) are
 * fire-and-forget on the constructing ViewModel's scope. The five
 * per-keystroke field writes are debounced to the typing-pause window:
 * server URL / username / email are whole-file DataStore edits, and the API
 * key / password are synchronous encrypted-file writes that would otherwise
 * run once per character — on [Dispatchers.IO], since they block the
 * calling thread and the scope's dispatcher is main. [flushPendingFieldWrites]
 * lets the wizard's continue/submit paths land a pending write before
 * navigating away.
 */
class SeerrEditActions(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val preferencesStore: SeerrPreferencesStore,
    private val credentialsStore: SeerrSecureCredentialsStore,
    private val secureWriteDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private var serverUrlWrite: Job? = null
    private var usernameWrite: Job? = null
    private var emailWrite: Job? = null
    private var apiKeyWrite: Job? = null
    private var passwordWrite: Job? = null

    fun setServerUrl(url: String) {
        serverUrlWrite = debouncedFieldWrite(serverUrlWrite) { preferencesStore.setServerUrl(url) }
    }

    fun setApiKey(key: String) {
        apiKeyWrite = debouncedFieldWrite(apiKeyWrite) {
            withContext(secureWriteDispatcher) { credentialsStore.setApiKey(key) }
        }
    }

    fun setAuthMethod(method: SeerrAuthMethod) = scope.launch { preferencesStore.setAuthMethod(method) }

    fun setUsername(username: String) {
        usernameWrite = debouncedFieldWrite(usernameWrite) { preferencesStore.setUsername(username) }
    }

    fun setEmail(email: String) {
        emailWrite = debouncedFieldWrite(emailWrite) { preferencesStore.setEmail(email) }
    }

    fun setPassword(password: String) {
        passwordWrite = debouncedFieldWrite(passwordWrite) {
            withContext(secureWriteDispatcher) { credentialsStore.setPassword(password) }
        }
    }

    fun setEnabled(enabled: Boolean) = scope.launch { preferencesStore.setEnabled(enabled) }

    fun setSearchEnabled(enabled: Boolean) = scope.launch { preferencesStore.setSearchEnabled(enabled) }

    fun setRecommendationsEnabled(enabled: Boolean) =
        scope.launch { preferencesStore.setRecommendationsEnabled(enabled) }

    fun setDiscoverEnabled(enabled: Boolean) = scope.launch { preferencesStore.setDiscoverEnabled(enabled) }

    fun setStreamingRegion(region: String) = scope.launch { preferencesStore.setStreamingRegion(region) }

    fun setDiscoverRegion(region: String) = scope.launch { preferencesStore.setDiscoverRegion(region) }

    fun disconnect() {
        // A pending debounced field write landing after the clear would
        // resurrect a credential the user just removed — drop them first.
        cancelPendingFieldWrites()
        scope.launch { preferencesStore.disconnect() }
    }

    /**
     * Suspends until every pending debounced field write has landed. The
     * wizard's continue/submit paths call this before navigating so the last
     * keystroke is never left waiting out the debounce window (a no-op when
     * nothing is pending).
     */
    suspend fun flushPendingFieldWrites() {
        listOf(serverUrlWrite, usernameWrite, emailWrite, apiKeyWrite, passwordWrite)
            .forEach { it?.join() }
    }

    private fun cancelPendingFieldWrites() {
        serverUrlWrite?.cancel()
        usernameWrite?.cancel()
        emailWrite?.cancel()
        apiKeyWrite?.cancel()
        passwordWrite?.cancel()
    }

    /**
     * Relaunches [block] after the typing-pause window, cancelling [previous]
     * first — the shared debounce shape of the five per-keystroke field
     * writes: a burst of keystrokes costs at most one store write.
     */
    private fun debouncedFieldWrite(previous: Job?, block: suspend () -> Unit): Job {
        previous?.cancel()
        return scope.launch {
            delay(SEERR_FIELD_WRITE_DEBOUNCE_MS)
            block()
        }
    }
}
