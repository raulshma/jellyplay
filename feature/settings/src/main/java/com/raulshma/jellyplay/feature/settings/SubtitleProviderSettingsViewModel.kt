package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProviderSearchOutcome
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

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
 * Status is a small sealed class mirroring Arr's `ServerConnectionStatus`.
 */
@HiltViewModel
class SubtitleProviderSettingsViewModel @Inject constructor(
    private val preferencesStore: SubtitleProviderPreferencesStore,
    private val subtitleProviderRepository: SubtitleProviderRepository,
) : JellyPlayViewModel() {

    val preferences: StateFlow<SubtitleProviderPreferences> = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), SubtitleProviderPreferences())

    private val _providerStatus = MutableStateFlow<Map<SubtitleProviderKind, ProviderStatus>>(emptyMap())
    val providerStatus: StateFlow<Map<SubtitleProviderKind, ProviderStatus>> = _providerStatus.asStateFlow()

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
     * caller passes the in-progress form credentials — and surfaces
     * [ProviderStatus] for [kind]. Fail-fast on unconfigured credentials so the
     * user gets immediate feedback without a network round-trip.
     */
    private fun testCredentials(kind: SubtitleProviderKind, credentials: SubtitleProviderCredentials) {
        if (!credentials.isConfigured) {
            _providerStatus.update { it + (kind to ProviderStatus.Error("Enter credentials first")) }
            return
        }
        launch {
            _providerStatus.update { it + (kind to ProviderStatus.Testing) }
            // verifyCredentials probes this one provider against the passed-in
            // credentials alone, ignoring the enable toggle and the saved store —
            // so a freshly pasted key/password verifies before the user turns the
            // provider on or saves. Same rate-limit/retry path the player uses.
            val outcome = subtitleProviderRepository.verifyCredentials(kind, credentials)
            _providerStatus.update {
                it + (kind to when (outcome) {
                    is ProviderSearchOutcome.Success -> ProviderStatus.Connected
                    is ProviderSearchOutcome.Error -> ProviderStatus.Error(outcome.message)
                    is ProviderSearchOutcome.Skipped -> ProviderStatus.Error("Provider not configured")
                })
            }
        }
    }

    /** Connection status for a single subtitle provider, mirroring Arr's. */
    sealed class ProviderStatus {
        data object Idle : ProviderStatus()
        data object Testing : ProviderStatus()
        data object Connected : ProviderStatus()
        data class Error(val message: String) : ProviderStatus()
    }
}
