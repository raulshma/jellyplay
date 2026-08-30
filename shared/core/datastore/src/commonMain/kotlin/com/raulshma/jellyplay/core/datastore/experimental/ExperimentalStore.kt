package com.raulshma.jellyplay.core.datastore.experimental

import com.raulshma.jellyplay.core.model.wallNowMillis
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.model.ExperimentalFeature
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Deep module owning the **experimental features + misc app / update** preference
 * domain: the opt-in experimental feature set (JSON), the self-update check
 * + auto-download toggles, the dismissed-update suppression window, the app
 * language override, the share-media /
 * search-history / audio-description toggles, and the dismissed-update
 * one-time state.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection, JSON memoisation, and reset list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Boundary note — keys NOT owned here (deliberately):**
 *  - `HAPTICS_ENABLED` → owned by `AppearanceStore`.
 *  - `USER_DATA_SYNC_ENABLED` / `ANDROID_TV_WATCH_NEXT_ENABLED` → owned by
 *    `PlaybackStore`.
 *  - `ONBOARDING_COMPLETED` / `DISMISSED_UPDATE_VERSION` /
 *    `DISMISSED_UPDATE_AT_MS` → one-time state in `resetExcludedKeys`; the two
 *    dismissed-update keys are written via [setDismissedUpdate] but are **not**
 *    in [resetKeys] (a category reset must not re-prompt an already-dismissed
 *    update).
 *  - `SHOW_ADVANCED_SETTINGS` → already a field of `AppearanceSlice`; this
 *    store clears it on reset (it is the experimental-category reset owner per
 *    `PreferenceResetCategory.EXPERIMENTAL`) but does **not** re-project it, to
 *    avoid a second source of truth.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Codec note:** none of the booleans owned here
 * (`self_update_check_enabled`, `self_update_download_enabled`,
 * `show_share_media_option`, `hide_search_history`,
 * `prefer_audio_description`) appear in the legacy-string
 * → typed-key migration lists, so they are read with plain `prefs[key] ?: default`.
 */
class ExperimentalStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val ENABLED_EXPERIMENTAL_FEATURES = stringPreferencesKey("enabled_experimental_features")
        val SELF_UPDATE_CHECK_ENABLED = booleanPreferencesKey("self_update_check_enabled")
        val SELF_UPDATE_DOWNLOAD_ENABLED = booleanPreferencesKey("self_update_download_enabled")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SHOW_SHARE_MEDIA_OPTION = booleanPreferencesKey("show_share_media_option")
        val HIDE_SEARCH_HISTORY = booleanPreferencesKey("hide_search_history")
        val PREFER_AUDIO_DESCRIPTION = booleanPreferencesKey("prefer_audio_description")

        // Reset-owner for the EXPERIMENTAL category (see class doc); field lives
        // on AppearanceSlice, cleared here on reset only.
        val SHOW_ADVANCED_SETTINGS = booleanPreferencesKey("show_advanced_settings")

        // One-time update-dismissal state — written by setDismissedUpdate, never reset.
        val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")
        val DISMISSED_UPDATE_AT_MS = longPreferencesKey("dismissed_update_at_ms")

        // How long a dismissed update stays suppressed for the same version.
        val UPDATE_DISMISS_PERIOD = stringPreferencesKey("update_dismiss_period")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    private var cachedEnabledExperimentalFeatures: ParsedCache<Set<ExperimentalFeature>> =
        ParsedCache(null, emptySet())

    val experimental: StateFlow<ExperimentalSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ExperimentalSlice())

    internal fun read(prefs: Preferences): ExperimentalSlice = ExperimentalSlice(
        enabledExperimentalFeatures = readEnabledExperimentalFeatures(prefs),
        selfUpdateCheckEnabled = prefs[Keys.SELF_UPDATE_CHECK_ENABLED] ?: true,
        selfUpdateDownloadEnabled = prefs[Keys.SELF_UPDATE_DOWNLOAD_ENABLED] ?: false,
        appLanguage = prefs[Keys.APP_LANGUAGE],
        showShareMediaOption = prefs[Keys.SHOW_SHARE_MEDIA_OPTION] ?: true,
        hideSearchHistory = prefs[Keys.HIDE_SEARCH_HISTORY] ?: false,
        preferAudioDescription = prefs[Keys.PREFER_AUDIO_DESCRIPTION] ?: false,
        dismissedUpdateVersion = prefs[Keys.DISMISSED_UPDATE_VERSION],
        dismissedUpdateAtMs = prefs[Keys.DISMISSED_UPDATE_AT_MS] ?: 0L,
        updateDismissPeriod = UpdateDismissPeriod.fromName(prefs[Keys.UPDATE_DISMISS_PERIOD]),
    )

    private fun readEnabledExperimentalFeatures(prefs: Preferences): Set<ExperimentalFeature> {
        val raw = prefs[Keys.ENABLED_EXPERIMENTAL_FEATURES]
        return if (raw != cachedEnabledExperimentalFeatures.raw) {
            try {
                raw?.let {
                    json.decodeFromString<Set<String>>(it)
                        .mapNotNull { name -> ExperimentalFeature.entries.find { e -> e.name == name } }
                        .toSet()
                } ?: emptySet()
            } catch (_: Exception) { emptySet() }
                .also { cachedEnabledExperimentalFeatures = ParsedCache(raw, it) }
        } else {
            cachedEnabledExperimentalFeatures.value
        }
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setEnabledExperimentalFeatures(features: Set<ExperimentalFeature>) {
        dataStore.edit {
            it[Keys.ENABLED_EXPERIMENTAL_FEATURES] = json.encodeToString(features.map { f -> f.name }.toSet())
        }
    }

    suspend fun setSelfUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SELF_UPDATE_CHECK_ENABLED] = enabled }
    }

    /**
     * When enabled, an available update's APK begins downloading immediately
     * after a check instead of waiting for the user to tap Download. Defaults
     * to off; can be toggled from Settings (About) or from the update sheet.
     */
    suspend fun setSelfUpdateDownloadEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SELF_UPDATE_DOWNLOAD_ENABLED] = enabled }
    }

    suspend fun setAppLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.APP_LANGUAGE] = language
            else it.remove(Keys.APP_LANGUAGE)
        }
    }

    suspend fun setShowShareMediaOption(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SHARE_MEDIA_OPTION] = enabled }
    }

    suspend fun setHideSearchHistory(enabled: Boolean) {
        dataStore.edit { it[Keys.HIDE_SEARCH_HISTORY] = enabled }
    }

    suspend fun setPreferAudioDescription(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFER_AUDIO_DESCRIPTION] = enabled }
    }

    /**
     * Records that the user dismissed the prompt for [version], stamping [atMs]
     * so the auto-check can suppress the same version for the configured
     * [UpdateDismissPeriod]. Pass `null` to clear a prior dismissal (e.g. on a
     * fresh update check or after installing). One-time state — these keys are
     * **not** in [resetKeys].
     */
    suspend fun setDismissedUpdate(version: String?, atMs: Long = wallNowMillis()) {
        dataStore.edit {
            if (version == null) {
                it.remove(Keys.DISMISSED_UPDATE_VERSION)
                it.remove(Keys.DISMISSED_UPDATE_AT_MS)
            } else {
                it[Keys.DISMISSED_UPDATE_VERSION] = version
                it[Keys.DISMISSED_UPDATE_AT_MS] = atMs
            }
        }
    }

    /** Persists the "hide dismissed updates for" window (Settings → About). */
    suspend fun setUpdateDismissPeriod(period: UpdateDismissPeriod) {
        dataStore.edit { it[Keys.UPDATE_DISMISS_PERIOD] = period.name }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Matches
     * `PreferenceResetCategory.EXPERIMENTAL`
     * ({ENABLED_EXPERIMENTAL_FEATURES, SHOW_ADVANCED_SETTINGS}). The
     * dismissed-update keys are deliberately omitted — they are one-time state.
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.ENABLED_EXPERIMENTAL_FEATURES,
        Keys.SHOW_ADVANCED_SETTINGS,
    )

    /**
     * Category reset participation: only the two reset-eligible keys owned here
     * sit in the `EXPERIMENTAL` category. The onboarding + dismissed-update keys
     * are `resetExcludedKeys` (runtime/one-time state) and never reset; the
     * misc-app self-update/language/share/history keys are reset by their
     * `MISC_APP` owner elsewhere.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.EXPERIMENTAL -> listOf(
            Keys.ENABLED_EXPERIMENTAL_FEATURES,
            Keys.SHOW_ADVANCED_SETTINGS,
        )
        PreferenceResetCategory.MISC_APP -> listOf(
            Keys.SELF_UPDATE_CHECK_ENABLED,
            Keys.SELF_UPDATE_DOWNLOAD_ENABLED,
            Keys.UPDATE_DISMISS_PERIOD,
            Keys.SHOW_SHARE_MEDIA_OPTION,
            Keys.HIDE_SEARCH_HISTORY,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the keys owned by this store from a
     * decoded [UserPreferences]. The experimental-feature set is written with
     * this store's own [json] codec (name-string set, same shape
     * `setEnabledExperimentalFeatures` uses). One-time state
     * ([Keys.DISMISSED_UPDATE_VERSION] / [Keys.DISMISSED_UPDATE_AT_MS]) is not
     * written back.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.ENABLED_EXPERIMENTAL_FEATURES] = json.encodeToString(userPreferences.enabledExperimentalFeatures.map { feature -> feature.name }.toSet())
            it[Keys.SELF_UPDATE_CHECK_ENABLED] = userPreferences.selfUpdateCheckEnabled
            it[Keys.SELF_UPDATE_DOWNLOAD_ENABLED] = userPreferences.selfUpdateDownloadEnabled
            it[Keys.UPDATE_DISMISS_PERIOD] = userPreferences.updateDismissPeriod.name
            userPreferences.appLanguage?.let { language -> it[Keys.APP_LANGUAGE] = language }
            it[Keys.SHOW_SHARE_MEDIA_OPTION] = userPreferences.showShareMediaOption
            it[Keys.HIDE_SEARCH_HISTORY] = userPreferences.hideSearchHistory
            it[Keys.PREFER_AUDIO_DESCRIPTION] = userPreferences.preferAudioDescription
            it[Keys.SHOW_ADVANCED_SETTINGS] = userPreferences.showAdvancedSettings
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (experimental
     * features as a name-string set via [json], appLanguage nullable). Unlike
     * [restorePreferences] this also writes the dismissed-update one-time state
     * ([Keys.DISMISSED_UPDATE_VERSION] / [Keys.DISMISSED_UPDATE_AT_MS]) so a
     * restore round-trips every slice field; `SHOW_ADVANCED_SETTINGS` is not a
     * slice field, so it is not written here.
     */
    suspend fun restore(slice: ExperimentalSlice) {
        dataStore.edit { it ->
            it[Keys.ENABLED_EXPERIMENTAL_FEATURES] = json.encodeToString(slice.enabledExperimentalFeatures.map { feature -> feature.name }.toSet())
            it[Keys.SELF_UPDATE_CHECK_ENABLED] = slice.selfUpdateCheckEnabled
            it[Keys.SELF_UPDATE_DOWNLOAD_ENABLED] = slice.selfUpdateDownloadEnabled
            it[Keys.UPDATE_DISMISS_PERIOD] = slice.updateDismissPeriod.name
            slice.appLanguage?.let { language -> it[Keys.APP_LANGUAGE] = language }
            it[Keys.SHOW_SHARE_MEDIA_OPTION] = slice.showShareMediaOption
            it[Keys.HIDE_SEARCH_HISTORY] = slice.hideSearchHistory
            it[Keys.PREFER_AUDIO_DESCRIPTION] = slice.preferAudioDescription
            slice.dismissedUpdateVersion?.let { version -> it[Keys.DISMISSED_UPDATE_VERSION] = version }
            it[Keys.DISMISSED_UPDATE_AT_MS] = slice.dismissedUpdateAtMs
        }
    }
}

/**
 * The experimental + misc-app preference slice. Plain data class. Defaults
 * mirror the projection defaults in [ExperimentalStore.read].
 *
 * Note: `showAdvancedSettings` is intentionally **not** a field here — it lives
 * on `AppearanceSlice`. This store clears it on reset only (see
 * [ExperimentalStore.resetKeys]).
 */
@Immutable
@Serializable
data class ExperimentalSlice(
    val enabledExperimentalFeatures: Set<ExperimentalFeature> = emptySet(),
    val selfUpdateCheckEnabled: Boolean = true,
    val selfUpdateDownloadEnabled: Boolean = false,
    val appLanguage: String? = null,
    val showShareMediaOption: Boolean = true,
    val hideSearchHistory: Boolean = false,
    val preferAudioDescription: Boolean = false,
    val dismissedUpdateVersion: String? = null,
    val dismissedUpdateAtMs: Long = 0L,
    val updateDismissPeriod: UpdateDismissPeriod = UpdateDismissPeriod.DEFAULT,
)
