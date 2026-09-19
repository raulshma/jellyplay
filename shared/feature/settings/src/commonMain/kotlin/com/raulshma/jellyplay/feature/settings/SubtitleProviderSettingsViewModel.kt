package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProviderSearchOutcome
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for [SubtitleProviderSettingsScreen]. Mirrors [ArrSettingsViewModel]:
 * injects the preferences store + secure credentials store, exposes the
 * preferences as a `StateFlow`, and saves via read-modify-write against the
 * **secure** store (never against the StateFlow's seed value — that pitfall is
 * documented in [ArrSettingsViewModel] and would silently overwrite the
 * encrypted store with a single entry).
 *
 * A "Test" action verifies the **in-progress form text** (not the saved store)
 * so the user can confirm a Wyzie API key or OpenSubtitles username/password
 * works **before** tapping Save. The screen hands the live field values to
 * [testWyzieApiKey] / [testOpenSubtitlesCredentials], which build the credential
 * object and route it through [SubtitleProviderRepository.verifyCredentials] —
 * OpenSubtitles performs a real `/login` there, so a wrong password is caught.
 * Status is the shared [ConnectionProbe] board (the former seal mirrored Arr's
 * `ServerConnectionStatus`; both now ride one machine, whose KDoc declares the
 * single-flight RESTART policy, the cancellation-never-lands-as-Error rule,
 * and the localized fallback texts). One behavior change: the repository's Skipped
 * outcome still lands as Error, but as the localized ProviderNotConfigured
 * fallback instead of the hardcoded "Provider not configured" literal.
 */
class SubtitleProviderSettingsViewModel(
    private val preferencesStore: SubtitleProviderPreferencesStore,
    private val subtitleProviderRepository: SubtitleProviderRepository,
) : JellyPlayViewModel() {

    val preferences: StateFlow<SubtitleProviderPreferences> = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SubtitleProviderPreferences())

    /** Per-provider reachability via the shared [ConnectionProbe] board, keyed by [SubtitleProviderKind]. */
    private val probeBoard: ConnectionProbe<SubtitleProbeRequest, SubtitleProviderKind, Unit> = ConnectionProbe(
        scope = scope,
        keyOf = { request -> request.kind },
        refused = { request ->
            // Fail-fast on unconfigured credentials so the user gets immediate
            // feedback without a network round-trip (a machine-level refusal —
            // no Testing frame, no action call).
            if (!request.credentials.isConfigured) ConnectionProbe.FallbackText.EnterCredentialsFirst
            else null
        },
        action = { request ->
            // verifyCredentials probes this one provider against the passed-in
            // credentials alone, ignoring the enable toggle and the saved store —
            // so a freshly pasted key/password verifies before the user turns the
            // provider on or saves. Same rate-limit/retry path the player uses.
            when (val outcome = subtitleProviderRepository.verifyCredentials(request.kind, request.credentials)) {
                is ProviderSearchOutcome.Success -> ConnectionProbe.Outcome.Reachable(Unit)
                is ProviderSearchOutcome.Error -> ConnectionProbe.unreachable(outcome.message)
                // Skipped still lands as Error, but as the
                // localized ProviderNotConfigured fallback (was the hardcoded
                // "Provider not configured" literal).
                ProviderSearchOutcome.Skipped -> ConnectionProbe.Outcome.Failed(
                    ConnectionProbe.Failure.Declared(ConnectionProbe.FallbackText.ProviderNotConfigured),
                )
            }
        },
    )
    val providerStatus: StateFlow<Map<SubtitleProviderKind, ConnectionProbe.Status<Unit>>> = probeBoard.status

    /** Synchronous credential snapshot for form seeding (read from the secure store). */
    fun credentialSnapshot(kind: SubtitleProviderKind): SubtitleProviderCredentials? =
        preferencesStore.getCredentials(kind)

    fun setWyzieEnabled(enabled: Boolean) {
        launch { preferencesStore.setWyzieEnabled(enabled) }
    }

    fun setOpenSubtitlesEnabled(enabled: Boolean) {
        launch { preferencesStore.setOpenSubtitlesEnabled(enabled) }
    }

    /** Saves the Wyzie API key. Blank clears the stored credential. */
    fun saveWyzieApiKey(apiKey: String) {
        launch {
            val trimmed = apiKey.trim()
            if (trimmed.isBlank()) {
                preferencesStore.clearCredentials(SubtitleProviderKind.WYZIE)
            } else {
                preferencesStore.setCredentials(
                    SubtitleProviderKind.WYZIE,
                    SubtitleProviderCredentials.Wyzie(apiKey = trimmed),
                )
            }
        }
    }

/**
 * Saves the OpenSubtitles credentials (username + password). Blank username
 * clears everything; a non-blank username (with password) is persisted. JWT
 * fields are preserved across a save when username/password don't change (the
 * token is refreshed lazily by the provider on next use). The OpenSubtitles
 * API key is a compiled-in shared app key, so the user never supplies one.
 */
fun saveOpenSubtitlesCredentials(username: String?, password: String?) {
    launch {
        val trimmedUser = username?.trim()?.ifBlank { null }
        if (trimmedUser == null) {
            preferencesStore.clearCredentials(SubtitleProviderKind.OPENSUBTITLES)
            return@launch
        }
        val existing = preferencesStore.getCredentials(SubtitleProviderKind.OPENSUBTITLES)
            as? SubtitleProviderCredentials.OpenSubtitles
        // If username/password changed, drop the cached JWT so the provider
        // re-logs-in with the new credentials next time.
        val userChanged = existing?.username != trimmedUser ||
            existing?.password != password?.ifBlank { null }
        preferencesStore.setCredentials(
            SubtitleProviderKind.OPENSUBTITLES,
            SubtitleProviderCredentials.OpenSubtitles(
                username = trimmedUser,
                password = password?.ifBlank { null },
                jwt = if (userChanged) null else existing?.jwt,
                jwtExpiresAt = if (userChanged) 0 else existing?.jwtExpiresAt ?: 0,
            ),
        )
    }
}

    /**
     * Tests the Wyzie [apiKey] exactly as typed in the form — nothing is read
     * from or written to the store, so the user verifies a freshly pasted key
     * **before** tapping Save. Blank → "Enter credentials first".
     */
    fun testWyzieApiKey(apiKey: String) {
        testCredentials(SubtitleProviderKind.WYZIE, SubtitleProviderCredentials.Wyzie(apiKey.trim()))
    }

    /**
     * Tests the OpenSubtitles [username]/[password] exactly as typed in the form
     * — neither read from nor written to the store, so the user verifies them
     * **before** tapping Save. OpenSubtitles performs a real `/login` in the
     * repository, so a wrong password is caught here.
     */
    fun testOpenSubtitlesCredentials(username: String?, password: String?) {
        val credentials = SubtitleProviderCredentials.OpenSubtitles(
            username = username?.trim()?.ifBlank { null },
            password = password?.ifBlank { null },
        )
        testCredentials(SubtitleProviderKind.OPENSUBTITLES, credentials)
    }

    /**
     * Shared Test path for both providers. Builds nothing from the store — the
     * caller passes the in-progress form credentials — and surfaces the
     * shared board's status for [kind]. Unconfigured credentials are refused
     * synchronously by the board (no Testing frame, no network round-trip).
     * Single-flight is the machine's declared RESTART policy: a second Test
     * tap cancels the in-flight probe for that provider (previously two taps
     * raced, last write winning).
     */
    private fun testCredentials(kind: SubtitleProviderKind, credentials: SubtitleProviderCredentials) {
        probeBoard.probe(SubtitleProbeRequest(kind, credentials))
    }

    /** One provider Test request: the form credentials under test + the keyed kind. */
    private data class SubtitleProbeRequest(
        val kind: SubtitleProviderKind,
        val credentials: SubtitleProviderCredentials,
    )
}
