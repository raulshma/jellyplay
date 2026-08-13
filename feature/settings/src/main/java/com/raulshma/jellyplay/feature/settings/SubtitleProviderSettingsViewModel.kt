package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProviderSearchOutcome
import com.raulshma.jellyplay.core.data.repository.SubtitleProviderRepository
import com.raulshma.jellyplay.core.datastore.SubtitleProviderPreferencesStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import com.raulshma.jellyplay.core.model.subtitle.SubtitleQuery
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
 * A "Test" action probes a provider with a known TMDB id so the user can verify
 * their Wyzie API key or OpenSubtitles username/password (the OpenSubtitles test
 * also exercises the JWT login flow) before leaving the screen. The status is a
 * small sealed class mirroring Arr's `ServerConnectionStatus`.
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
     * Probes [kind] with a known TMDB id so the user can verify their key works
     * even before flipping the enable Switch on. For OpenSubtitles, search
     * triggers a mandatory JWT login first, so this validates the configured
     * username/password end-to-end. Surfaces [ProviderStatus] per provider.
     */
    fun testProvider(kind: SubtitleProviderKind) {
        // Fail fast when there is nothing configured to test.
        if (preferencesStore.getCredentials(kind)?.isConfigured != true) {
            _providerStatus.update { it + (kind to ProviderStatus.Error("Enter credentials first")) }
            return
        }
        launch {
            _providerStatus.update { it + (kind to ProviderStatus.Testing) }
            // Search a well-known movie (TMDB 11 — Star Wars) just to exercise
            // auth + a minimal search round-trip.
            val query = SubtitleQuery(
                tmdbId = TEST_TMDB_ID,
                languages = listOf("eng"),
            )
            // searchProvider probes this one provider by credentials alone,
            // ignoring the enable toggle — so a freshly pasted key verifies
            // before the user turns the provider on. Same rate-limit/retry path
            // the player/editor use.
            val outcome = subtitleProviderRepository.searchProvider(kind, query)
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

    companion object {
        // Star Wars: A New Hope — a stable, well-indexed TMDB id used purely to
        // exercise the provider's auth + search path during a Test.
        private const val TEST_TMDB_ID = 11
    }
}
