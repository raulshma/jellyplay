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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The onboarding wizard's one flow module. Owns the three things the wizard's
 * screens share:
 *
 *  - **Preferences**, projected centrally off the owning store slices by
 *    [PreferenceProjections]. The fields span 8 domains (appearance, home
 *    discovery, navigation, playback, video player, audio, subtitle,
 *    security) and are combined into one holder so the screen keeps its
 *    single `preferences.field` read pattern.
 *
 *  - **Page truth** — [currentPage] is the single source for which wizard
 *    page is live; the pager only mirrors it. Every turn (Next button,
 *    D-pad, header back, system back, user drag) funnels through
 *    [navigateToPage] / [onPageSettled], which land a pending debounced
 *    Seerr field write first, so the last keystroke is never lost to the
 *    300 ms debounce window when a step is left.
 *
 *  - **Exit** — [exitWizard] is the one verb for leaving the wizard: flush,
 *    then write the completion flag only when [markComplete], then signal
 *    [WizardExit] on [exits] (the screen pops the nav route). Skip and
 *    Start Watching both pass `markComplete = true`; system back on the
 *    first page passes `false`.
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

    /**
     * The live wizard page, clamped to `0..[OnboardingStep.count] - 1`. The
     * screen mirrors this into its pager and reports drag-settled pages back
     * through [onPageSettled].
     */
    private val _currentPage = stateFlow(0)
    val currentPage = _currentPage.flow

    /** One-shot wizard exits, consumed exactly once by the screen. */
    private val _exits = Channel<WizardExit>(Channel.BUFFERED)
    val exits = _exits.receiveAsFlow()

    /**
     * Turns to [target]: lands a pending debounced Seerr field write first,
     * then sets [currentPage] (clamped, so a fast double-tap can never leave
     * the wizard). Every page-turn verb the screen has routes here.
     */
    fun navigateToPage(target: Int) {
        launch {
            seerrEditActions.flushPendingFieldWrites()
            _currentPage.set(target.coerceIn(0, OnboardingStep.count - 1))
        }
    }

    /**
     * Acknowledges a pager page the user dragged to (the pager's settled
     * `currentPage`, not a turn this ViewModel initiated). Rides the same
     * flush+clamp+set as [navigateToPage] — flush-on-turn is universal — but
     * short-circuits when the pager already matches [currentPage], so the
     * screen's settle collector and this ViewModel never feed each other a
     * loop: each side only acts on a difference.
     */
    fun onPageSettled(page: Int) {
        if (page == _currentPage.value) return
        navigateToPage(page)
    }

    /**
     * Wizard back: one page up, or — on the first page — leave the wizard
     * without completing ([WizardExit.Abandon]). The flush still runs on that
     * exit: WELCOME has no fields today, so it is a no-op, but the first page
     * must keep the flush-on-every-verb invariant when fields move onto it.
     */
    fun onBack() {
        val page = _currentPage.value
        if (page > 0) navigateToPage(page - 1) else exitWizard(markComplete = false)
    }

    /**
     * Leaves the wizard: lands a pending debounced Seerr field write, then
     * (only when [markComplete]) persists the completion flag, then signals
     * [WizardExit.Complete] / [WizardExit.Abandon] on [exits]. Skip and
     * Start Watching pass `true`; back on the first page passes `false`.
     */
    fun exitWizard(markComplete: Boolean) {
        launch {
            seerrEditActions.flushPendingFieldWrites()
            if (markComplete) completeOnboarding()
            _exits.trySend(if (markComplete) WizardExit.Complete else WizardExit.Abandon)
        }
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

/**
 * One wizard exit, fired on [OnboardingViewModel.exits] and consumed exactly
 * once by the screen (the `VideoPlayerViewModel.closePlayer` idiom: Channel
 * BUFFERED + `receiveAsFlow()`, never a nullable StateFlow field that would
 * replay the exit to every new collector). The variants are the only two ways
 * the wizard can end:
 *
 *  - [Complete] — skip or Start Watching: the completion flag was persisted
 *    before this exit (the gate never re-fires for a completer).
 *  - [Abandon] — system back on the first page: the wizard closed without
 *    completing (re-runs on the next gate pass).
 *
 * The screen pops the nav route through its existing `onComplete` callback
 * for both variants — the callback is a composable parameter the ViewModel
 * cannot own, which is why exits ride a channel instead.
 */
@Immutable
sealed interface WizardExit {
    data object Complete : WizardExit
    data object Abandon : WizardExit
}
