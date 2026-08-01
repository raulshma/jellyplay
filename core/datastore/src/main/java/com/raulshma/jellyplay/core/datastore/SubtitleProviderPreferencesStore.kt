package com.raulshma.jellyplay.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subtitleProviderDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitle_provider_prefs")

/**
 * Non-secret subtitle-provider preferences, surfaced as
 * [SubtitleProviderPreferences], plus a thin pass-through to
 * [SubtitleProviderSecureCredentialsStore] for credential read-modify-write.
 *
 * Mirrors [ArrPreferencesStore]: Jetpack DataStore Preferences for the toggles,
 * EncryptedSharedPreferences for the secrets, and a [MutableStateFlow] tick to
 * re-emit whenever the encrypted store mutates (it has no Flow API). On any
 * read/parse error, the flow degrades to defaults rather than throwing —
 * matching the sibling store's `.catch { emptyPreferences() }` pattern.
 *
 * The credentials tick is exposed via [credentials] so the repository / settings
 * ViewModel can react to credential writes (e.g. the user pasting an API key)
 * without re-reading the store on every emission.
 */
@Singleton
class SubtitleProviderPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureCredentialsStore: SubtitleProviderSecureCredentialsStore,
) {

    private object Keys {
        val WYZIE_ENABLED = booleanPreferencesKey("subtitle_wyzie_enabled")
        val OPENSUBTITLES_ENABLED = booleanPreferencesKey("subtitle_opensubtitles_enabled")
    }

    /**
     * Hot trigger re-emitted whenever credentials are written. Seeded with the
     * current set so the first collection is correct without requiring callers
     * to poke. The repository derives `configuredProviders` from this + the
     * toggle state in [preferences].
     */
    private val credentialsTick = MutableStateFlow(snapshotCredentials())

    val preferences: Flow<SubtitleProviderPreferences> = context.subtitleProviderDataStore.data
        .catch { _ -> emit(emptyPreferences()) }
        .map { prefs ->
            SubtitleProviderPreferences(
                wyzieEnabled = prefs[Keys.WYZIE_ENABLED] ?: false,
                openSubtitlesEnabled = prefs[Keys.OPENSUBTITLES_ENABLED] ?: false,
            )
        }

    /** Hot flow of the current per-provider credentials snapshot. */
    val credentials: Flow<Map<SubtitleProviderKind, SubtitleProviderCredentials>> = credentialsTick

    suspend fun setWyzieEnabled(enabled: Boolean) {
        context.subtitleProviderDataStore.edit { it[Keys.WYZIE_ENABLED] = enabled }
    }

    suspend fun setOpenSubtitlesEnabled(enabled: Boolean) {
        context.subtitleProviderDataStore.edit { it[Keys.OPENSUBTITLES_ENABLED] = enabled }
    }

    fun setCredentials(kind: SubtitleProviderKind, credentials: SubtitleProviderCredentials) {
        secureCredentialsStore.setCredentials(kind, credentials)
        credentialsTick.value = snapshotCredentials()
    }

    fun clearCredentials(kind: SubtitleProviderKind) {
        secureCredentialsStore.clearCredentials(kind)
        credentialsTick.value = snapshotCredentials()
    }

    fun getCredentials(kind: SubtitleProviderKind): SubtitleProviderCredentials? =
        secureCredentialsStore.getCredentials(kind)

    private fun snapshotCredentials(): Map<SubtitleProviderKind, SubtitleProviderCredentials> =
        SubtitleProviderKind.entries
            .mapNotNull { kind -> secureCredentialsStore.getCredentials(kind)?.let { kind to it } }
            .toMap()
}
