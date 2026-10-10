package com.raulshma.jellyplay.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map


/**
 * Non-secret subtitle-provider preferences, surfaced as
 * [SubtitleProviderPreferences], plus a thin pass-through to
 * [SubtitleProviderSecureCredentialsStore] for credential read-modify-write.
 *
 * Mirrors [ArrPreferencesStore]: Jetpack DataStore Preferences for the toggles,
 * EncryptedSharedPreferences for the secrets, and a [MutableStateFlow] tick to
 * re-emit whenever the encrypted store mutates (it has no Flow API). On any
 * read/parse error, the flow degrades to defaults rather than throwing — the
 * module-wide `dataDegradingToDefaults` corrupt-read policy (see
 * `SliceStateFlow.kt`).
 *
 * The credentials tick is exposed via [credentials] so the repository / settings
 * ViewModel can react to credential writes (e.g. the user pasting an API key)
 * without re-reading the store on every emission.
 */
class SubtitleProviderPreferencesStore constructor(
    private val dataStore: DataStore<Preferences>,
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

    val preferences: Flow<SubtitleProviderPreferences> = dataStore.dataDegradingToDefaults()
        .map { prefs ->
            SubtitleProviderPreferences(
                wyzieEnabled = prefs[Keys.WYZIE_ENABLED] ?: false,
                openSubtitlesEnabled = prefs[Keys.OPENSUBTITLES_ENABLED] ?: false,
            )
        }

    /** Hot flow of the current per-provider credentials snapshot. */
    val credentials: Flow<Map<SubtitleProviderKind, SubtitleProviderCredentials>> = credentialsTick

    suspend fun setWyzieEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WYZIE_ENABLED] = enabled }
    }

    suspend fun setOpenSubtitlesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.OPENSUBTITLES_ENABLED] = enabled }
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

    // ------------------------------------------------------------------
    // SYNC-ONLY SURFACE (jellyplay-plugin-jellyplay settings sync, the
    // `integrations` namespace): a minimal allowlisted read/write pair the
    // sync adapter translates into wire values. NOT a general editing API —
    // the setters above remain the only user-facing write path. The provider
    // credentials (API keys) live in [SubtitleProviderSecureCredentialsStore]
    // and are deliberately absent from the allowlist — they can never sync.
    // ------------------------------------------------------------------

    /**
     * The sync allowlist: raw key name → its typed read/write entry (the
     * shared [SyncEntry] builders — see SyncAllowlist.kt). Key names are
     * private to this store (the adapter never hardcodes them); this map is
     * where renames land. The defaults mirror the read path's inline
     * fallbacks above.
     */
    private val SyncTypedKeys = SyncAllowlist(
        mapOf(
            "subtitle_wyzie_enabled" to booleanEntry(Keys.WYZIE_ENABLED, default = false),
            "subtitle_opensubtitles_enabled" to booleanEntry(Keys.OPENSUBTITLES_ENABLED, default = false),
        ),
    )

    /**
     * The raw key names [syncSnapshot]/[syncApply] may ever touch — the sync
     * allowlist (all non-secret subtitle-provider configuration; see
     * [SyncTypedKeys]).
     */
    val SyncKeys: Set<String> get() = SyncTypedKeys.keys

    /**
     * The raw stored value per allowlisted key (`null` = the key is absent —
     * readers fall back to that key's default). The sync adapter's snapshot
     * face; a corrupt DataStore read degrades to all-absent per the module's
     * corrupt-read policy.
     */
    suspend fun syncSnapshot(): Map<String, String?> = SyncTypedKeys.snapshotFrom(dataStore)

    /**
     * Writes one allowlisted raw value (the sync adapter's adopt face), or
     * resets the key when [value] is `null` — the reset WRITES the entry's
     * default (never a removal: value-presence resets roam as value writes
     * through the value-only adapter, absence does not, so a removal would
     * leave the server's still-standing row to re-adopt on a later cycle and
     * undo the reset). Unallowlisted keys are ignored in BOTH directions; an
     * uncoercible value skips the write, leaving the local value (the
     * prefs-adapter coercion rule).
     */
    suspend fun syncApply(key: String, value: String?) =
        SyncTypedKeys.applyTo(dataStore, key, value)
}
