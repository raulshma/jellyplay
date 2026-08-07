package com.raulshma.jellyplay.core.datastore.subtitle

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **subtitle &amp; language** preference domain: preferred
 * subtitle/audio languages, the forced-only and audio-description toggles, the
 * high-contrast-subtitles accessibility toggle, the in-app language override, the
 * SDR + HDR subtitle styles, the per-item subtitle-sync delay LRU map, and the
 * settings preview toggle.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the per-item-delay invariant below), its read
 * projection (with JSON-decode memoisation), and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Per-item-delay invariant owned here:** [setSubtitleDelayForItem] maintains a
 * JSON map under `subtitle_delay_by_item`; a `delayMs == 0` **removes** the
 * entry so the map does not grow unbounded with neutral values.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; the key strings
 * match the legacy `UserPreferencesStore.Keys` names so existing data is read
 * in place — no migration file, no second delegate.
 */
@Singleton
class SubtitleLanguageStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SUBTITLES_FORCED_ONLY = booleanPreferencesKey("subtitles_forced_only")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val SUBTITLE_DELAY_BY_ITEM = stringPreferencesKey("subtitle_delay_by_item")
        val SUBTITLE_STYLE = stringPreferencesKey("subtitle_style")
        val SUBTITLE_PREVIEW_IN_SETTINGS = booleanPreferencesKey("subtitle_preview_in_settings")
        val PREFER_AUDIO_DESCRIPTION = booleanPreferencesKey("prefer_audio_description")
        val HIGH_CONTRAST_SUBTITLES = booleanPreferencesKey("high_contrast_subtitles")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val HDR_SUBTITLE_STYLE_ENABLED = booleanPreferencesKey("hdr_subtitle_style_enabled")
        val HDR_SUBTITLE_STYLE = stringPreferencesKey("hdr_subtitle_style")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> androidx.datastore.preferences.core.emptyPreferences() }

    /** Memoised decode of the SDR subtitle style blob. */
    private var cachedSubtitleStyle: ParsedCache<SubtitleStyle?> = ParsedCache(null, null)
    /** Memoised decode of the HDR subtitle style blob. */
    private var cachedHdrSubtitleStyle: ParsedCache<SubtitleStyle?> = ParsedCache(null, null)
    /** Memoised decode of the per-item subtitle delay map blob. */
    private var cachedSubtitleDelayByItem: ParsedCache<Map<String, Long>> = ParsedCache(null, emptyMap())

    /**
     * The subtitle &amp; language preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val subtitle: StateFlow<SubtitleSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, SubtitleSlice())

    /**
     * Pure read of the subtitle &amp; language fields from a raw [Preferences]
     * snapshot. Exposed so the facade can fold these into the whole-
     * `UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): SubtitleSlice {
        val subtitleStyleRaw = prefs[Keys.SUBTITLE_STYLE]
        val subtitleStyle = if (subtitleStyleRaw != cachedSubtitleStyle.raw) {
            try {
                subtitleStyleRaw?.let { PreferenceCodec.json.decodeFromString<SubtitleStyle>(it) }
            } catch (_: Exception) { null }.also { cachedSubtitleStyle = ParsedCache(subtitleStyleRaw, it) }
        } else cachedSubtitleStyle.value

        val hdrSubtitleStyleRaw = prefs[Keys.HDR_SUBTITLE_STYLE]
        val hdrSubtitleStyle = if (hdrSubtitleStyleRaw != cachedHdrSubtitleStyle.raw) {
            try {
                hdrSubtitleStyleRaw?.let { PreferenceCodec.json.decodeFromString<SubtitleStyle>(it) }
            } catch (_: Exception) { null }.also { cachedHdrSubtitleStyle = ParsedCache(hdrSubtitleStyleRaw, it) }
        } else cachedHdrSubtitleStyle.value

        val subtitleDelayByItemRaw = prefs[Keys.SUBTITLE_DELAY_BY_ITEM]
        val subtitleDelayByItem = if (subtitleDelayByItemRaw != cachedSubtitleDelayByItem.raw) {
            try {
                subtitleDelayByItemRaw?.let { PreferenceCodec.json.decodeFromString<Map<String, Long>>(it) }
                    ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
                .also { cachedSubtitleDelayByItem = ParsedCache(subtitleDelayByItemRaw, it) }
        } else cachedSubtitleDelayByItem.value

        return SubtitleSlice(
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUBTITLE_LANG],
            subtitlesForcedOnly = PreferenceCodec.readBool(prefs, Keys.SUBTITLES_FORCED_ONLY, "subtitles_forced_only", false),
            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG],
            subtitleDelayByItem = subtitleDelayByItem,
            subtitleStyle = subtitleStyle ?: SubtitleStyle(),
            subtitlePreviewInSettings = PreferenceCodec.readBool(prefs, Keys.SUBTITLE_PREVIEW_IN_SETTINGS, "subtitle_preview_in_settings", true),
            preferAudioDescription = PreferenceCodec.readBool(prefs, Keys.PREFER_AUDIO_DESCRIPTION, "prefer_audio_description", false),
            highContrastSubtitles = PreferenceCodec.readBool(prefs, Keys.HIGH_CONTRAST_SUBTITLES, "high_contrast_subtitles", false),
            appLanguage = prefs[Keys.APP_LANGUAGE],
            hdrSubtitleStyleEnabled = PreferenceCodec.readBool(prefs, Keys.HDR_SUBTITLE_STYLE_ENABLED, "hdr_subtitle_style_enabled", false),
            hdrSubtitleStyle = hdrSubtitleStyle ?: SubtitleStyle(
                fontSize = 28,
                backgroundOpacity = 0.5f,
                edgeType = SubtitleEdgeType.OUTLINE,
            ),
        )
    }

    // ------------------------------------------------------------------
    // Setters — the per-item-delay invariant lives here, behind a narrow
    // surface.
    // ------------------------------------------------------------------

    suspend fun setPreferredSubtitleLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.PREFERRED_SUBTITLE_LANG] = language
            else it.remove(Keys.PREFERRED_SUBTITLE_LANG)
        }
    }

    suspend fun setPreferredAudioLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.PREFERRED_AUDIO_LANG] = language
            else it.remove(Keys.PREFERRED_AUDIO_LANG)
        }
    }

    suspend fun setSubtitlesForcedOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.SUBTITLES_FORCED_ONLY] = enabled }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { it[Keys.SUBTITLE_STYLE] = PreferenceCodec.json.encodeToString(style) }
    }

    /**
     * Persists a per-item subtitle-sync delay. A `delayMs` of 0 removes the
     * entry so the map doesn't grow unbounded with neutral values.
     */
    suspend fun setSubtitleDelayForItem(itemId: String, delayMs: Long) {
        dataStore.edit { prefs ->
            val current = try {
                prefs[Keys.SUBTITLE_DELAY_BY_ITEM]
                    ?.let { PreferenceCodec.json.decodeFromString<Map<String, Long>>(it) } ?: emptyMap()
            } catch (_: Exception) { emptyMap() }
            val updated = if (delayMs == 0L) current - itemId else current + (itemId to delayMs)
            prefs[Keys.SUBTITLE_DELAY_BY_ITEM] = PreferenceCodec.json.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(), updated,
            )
        }
    }

    suspend fun setHdrSubtitleStyleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HDR_SUBTITLE_STYLE_ENABLED] = enabled }
    }

    suspend fun setHdrSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { it[Keys.HDR_SUBTITLE_STYLE] = PreferenceCodec.json.encodeToString(style) }
    }

    suspend fun setHighContrastSubtitles(enabled: Boolean) {
        dataStore.edit { it[Keys.HIGH_CONTRAST_SUBTITLES] = enabled }
    }

    suspend fun setSubtitlePreviewInSettings(enabled: Boolean) {
        dataStore.edit { it[Keys.SUBTITLE_PREVIEW_IN_SETTINGS] = enabled }
    }

    suspend fun setPreferAudioDescription(enabled: Boolean) {
        dataStore.edit { it[Keys.PREFER_AUDIO_DESCRIPTION] = enabled }
    }

    suspend fun setAppLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.APP_LANGUAGE] = language
            else it.remove(Keys.APP_LANGUAGE)
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Aggregated by
     * the facade's reset-coverage guard (covers
     * `PreferenceResetCategory.SUBTITLES_LANGUAGE`).
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.PREFERRED_SUBTITLE_LANG,
        Keys.PREFERRED_AUDIO_LANG,
        Keys.SUBTITLES_FORCED_ONLY,
        Keys.SUBTITLE_PREVIEW_IN_SETTINGS,
        Keys.SUBTITLE_STYLE,
        Keys.HIGH_CONTRAST_SUBTITLES,
        Keys.HDR_SUBTITLE_STYLE_ENABLED,
        Keys.HDR_SUBTITLE_STYLE,
        Keys.SUBTITLE_DELAY_BY_ITEM,
        Keys.APP_LANGUAGE,
        Keys.PREFER_AUDIO_DESCRIPTION,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. The subtitle/audio/track keys sit in
     * `PreferenceResetCategory.SUBTITLES_LANGUAGE`; the in-app language and
     * audio-description toggles are app-wide (`MISC_APP`), matching the
     * facade's `resetCategoryKeys`.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.SUBTITLES_LANGUAGE -> listOf(
            Keys.PREFERRED_SUBTITLE_LANG,
            Keys.PREFERRED_AUDIO_LANG,
            Keys.SUBTITLES_FORCED_ONLY,
            Keys.SUBTITLE_PREVIEW_IN_SETTINGS,
            Keys.SUBTITLE_STYLE,
            Keys.HIGH_CONTRAST_SUBTITLES,
            Keys.HDR_SUBTITLE_STYLE_ENABLED,
            Keys.HDR_SUBTITLE_STYLE,
            Keys.SUBTITLE_DELAY_BY_ITEM,
        )
        PreferenceResetCategory.MISC_APP -> listOf(
            Keys.APP_LANGUAGE,
            Keys.PREFER_AUDIO_DESCRIPTION,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the subtitle &amp; language keys owned
     * by this store from a decoded [UserPreferences], mirroring the facade's
     * restore body exactly (including the nullable language guards and the
     * JSON map / style round-trips).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { prefs ->
            userPreferences.preferredSubtitleLanguage?.let { prefs[Keys.PREFERRED_SUBTITLE_LANG] = it }
            prefs[Keys.SUBTITLES_FORCED_ONLY] = userPreferences.subtitlesForcedOnly
            userPreferences.preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            prefs[Keys.SUBTITLE_DELAY_BY_ITEM] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(),
                userPreferences.subtitleDelayByItem,
            )
            prefs[Keys.SUBTITLE_STYLE] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<SubtitleStyle>(),
                userPreferences.subtitleStyle,
            )
            prefs[Keys.SUBTITLE_PREVIEW_IN_SETTINGS] = userPreferences.subtitlePreviewInSettings
            prefs[Keys.PREFER_AUDIO_DESCRIPTION] = userPreferences.preferAudioDescription
            prefs[Keys.HIGH_CONTRAST_SUBTITLES] = userPreferences.highContrastSubtitles
            userPreferences.appLanguage?.let { prefs[Keys.APP_LANGUAGE] = it }
            prefs[Keys.HDR_SUBTITLE_STYLE_ENABLED] = userPreferences.hdrSubtitleStyleEnabled
            prefs[Keys.HDR_SUBTITLE_STYLE] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<SubtitleStyle>(),
                userPreferences.hdrSubtitleStyle,
            )
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (nullable
     * language guards + JSON map/style round-trips with defaults).
     */
    suspend fun restore(slice: SubtitleSlice) {
        dataStore.edit { prefs ->
            slice.preferredSubtitleLanguage?.let { prefs[Keys.PREFERRED_SUBTITLE_LANG] = it }
            prefs[Keys.SUBTITLES_FORCED_ONLY] = slice.subtitlesForcedOnly
            slice.preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            prefs[Keys.SUBTITLE_DELAY_BY_ITEM] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(),
                slice.subtitleDelayByItem,
            )
            prefs[Keys.SUBTITLE_STYLE] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<SubtitleStyle>(),
                slice.subtitleStyle,
            )
            prefs[Keys.SUBTITLE_PREVIEW_IN_SETTINGS] = slice.subtitlePreviewInSettings
            prefs[Keys.PREFER_AUDIO_DESCRIPTION] = slice.preferAudioDescription
            prefs[Keys.HIGH_CONTRAST_SUBTITLES] = slice.highContrastSubtitles
            slice.appLanguage?.let { prefs[Keys.APP_LANGUAGE] = it }
            prefs[Keys.HDR_SUBTITLE_STYLE_ENABLED] = slice.hdrSubtitleStyleEnabled
            prefs[Keys.HDR_SUBTITLE_STYLE] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<SubtitleStyle>(),
                slice.hdrSubtitleStyle,
            )
        }
    }
}

/**
 * The subtitle &amp; language preference slice. Plain data class (Compose-free) so
 * the datastore module stays framework-light. Defaults mirror the projection
 * defaults in [SubtitleLanguageStore.read].
 */
@Immutable
@Serializable
data class SubtitleSlice(
    val preferredSubtitleLanguage: String? = null,
    val subtitlesForcedOnly: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val subtitleDelayByItem: Map<String, Long> = emptyMap(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val subtitlePreviewInSettings: Boolean = true,
    val preferAudioDescription: Boolean = false,
    val highContrastSubtitles: Boolean = false,
    val appLanguage: String? = null,
    val hdrSubtitleStyleEnabled: Boolean = false,
    val hdrSubtitleStyle: SubtitleStyle = SubtitleStyle(
        fontSize = 28,
        backgroundOpacity = 0.5f,
        edgeType = SubtitleEdgeType.OUTLINE,
    ),
)
