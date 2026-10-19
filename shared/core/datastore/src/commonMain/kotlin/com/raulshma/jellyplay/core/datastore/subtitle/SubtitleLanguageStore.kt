package com.raulshma.jellyplay.core.datastore.subtitle

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.LanguageRuleSet
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.SubtitleStylePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **subtitle &amp; language** preference domain: preferred
 * subtitle/audio languages, the forced-only and audio-description toggles, the
 * high-contrast-subtitles accessibility toggle, the in-app language override, the
 * SDR + HDR subtitle styles, the per-item subtitle-sync delay LRU map, the
 * user-named style presets, and the settings preview toggle.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the per-item-delay invariant below), its read
 * projection (with JSON-decode memoisation), and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Spec derivation:** every key this store owns is declared exactly
 * once as a row in [SubtitlePreferenceSpecs] (wire name, default, reset
 * category, read/write encoding) and the machinery below is derived from those
 * rows — each [Keys] member rebuilds its row's typed key from the row's wire
 * name, each [read] projection row delegates to its row encoding, the
 * plain-typed setters and the style-blob rows of [restore] delegate to the
 * rows' derived writes, and [resetKeysFor] filters the rows by reset category.
 * The blob decode memoisation lives in the rows, beside the key it applies
 * to — every blob keeps the store's `MemoizeNull` policy (see
 * [SubtitlePreferenceSpecs]).
 *
 * **Hand-written residuals (the documented opt-outs — a row encoding writes a
 * slot, it cannot remove one or carry per-item semantics):**
 *  - [setPreferredSubtitleLanguage] / [setPreferredAudioLanguage] /
 *    [setAppLanguage] keep the set-or-remove dance (their rows are plain
 *    [PreferenceSpec.string] declarations);
 *  - [setSubtitleDelayForItem] keeps the read-modify-write LRU invariant (a
 *    `delayMs == 0` REMOVES its entry so the map does not grow unbounded);
 *  - [setSubtitleStylePresets] / [setTrackSelectionRules] keep the
 *    identity-by-absence removals, and [restore] keeps their (and the delay
 *    map's) hand write lines — restore writes the value verbatim, even the
 *    identity/empty one the setters would remove.
 *
 * **Per-item-delay invariant owned here:** [setSubtitleDelayForItem] maintains a
 * JSON map under `subtitle_delay_by_item`; a `delayMs == 0` **removes** the
 * entry so the map does not grow unbounded with neutral values.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; the key strings
 * match the legacy `UserPreferencesStore.Keys` names so existing data is read
 * in place — no migration file, no second delegate.
 */
class SubtitleLanguageStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [SubtitlePreferenceSpecs] row — the member rebuilds the row's typed key
     * from the row's single-declared wire name (`Preferences.Key` equality is
     * name-based, so these interoperate with any hand-built key of the same
     * name). Kept as a plain object rather than folded into the rows because
     * it is the reflection anchor for the JVM reset-coverage guard and the
     * key-identity reference for the hand-written invariant setters below.
     */
    internal object Keys {
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey(SubtitlePreferenceSpecs.PREFERRED_SUBTITLE_LANG.keyName)
        val SUBTITLES_FORCED_ONLY = booleanPreferencesKey(SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.keyName)
        val PREFERRED_AUDIO_LANG = stringPreferencesKey(SubtitlePreferenceSpecs.PREFERRED_AUDIO_LANG.keyName)
        val SUBTITLE_DELAY_BY_ITEM = stringPreferencesKey(SubtitlePreferenceSpecs.SUBTITLE_DELAY_BY_ITEM.keyName)
        val SUBTITLE_STYLE = stringPreferencesKey(SubtitlePreferenceSpecs.SUBTITLE_STYLE.keyName)
        val SUBTITLE_PREVIEW_IN_SETTINGS = booleanPreferencesKey(SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.keyName)
        val PREFER_AUDIO_DESCRIPTION = booleanPreferencesKey(SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.keyName)
        val HIGH_CONTRAST_SUBTITLES = booleanPreferencesKey(SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.keyName)
        val APP_LANGUAGE = stringPreferencesKey(SubtitlePreferenceSpecs.APP_LANGUAGE.keyName)
        val HDR_SUBTITLE_STYLE_ENABLED = booleanPreferencesKey(SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.keyName)
        val HDR_SUBTITLE_STYLE = stringPreferencesKey(SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.keyName)
        val TRACK_SELECTION_RULES = stringPreferencesKey(SubtitlePreferenceSpecs.TRACK_SELECTION_RULES.keyName)
        val SUBTITLE_STYLE_PRESETS = stringPreferencesKey(SubtitlePreferenceSpecs.SUBTITLE_STYLE_PRESETS.keyName)
    }

    /**
     * The subtitle &amp; language preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so
     * a write to an unrelated preference does not re-derive these fields.
     */
    val subtitle: StateFlow<SubtitleSlice> =
        dataStore.sliceStateFlow(scope, seed = SubtitleSlice(), read = ::read)

    /**
     * Pure read of the subtitle &amp; language fields from a raw [Preferences]
     * snapshot, each field delegated to its [SubtitlePreferenceSpecs] row
     * encoding. Exposed so the facade can fold these into the whole-
     * `UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): SubtitleSlice = SubtitleSlice(
        preferredSubtitleLanguage = SubtitlePreferenceSpecs.PREFERRED_SUBTITLE_LANG.readStored(prefs),
        subtitlesForcedOnly = SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.readFrom(prefs),
        preferredAudioLanguage = SubtitlePreferenceSpecs.PREFERRED_AUDIO_LANG.readStored(prefs),
        subtitleDelayByItem = SubtitlePreferenceSpecs.SUBTITLE_DELAY_BY_ITEM.readFrom(prefs),
        languageRules = SubtitlePreferenceSpecs.TRACK_SELECTION_RULES.readFrom(prefs),
        subtitleStyle = SubtitlePreferenceSpecs.SUBTITLE_STYLE.readFrom(prefs),
        subtitlePreviewInSettings = SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.readFrom(prefs),
        preferAudioDescription = SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.readFrom(prefs),
        highContrastSubtitles = SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.readFrom(prefs),
        appLanguage = SubtitlePreferenceSpecs.APP_LANGUAGE.readStored(prefs),
        hdrSubtitleStyleEnabled = SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.readFrom(prefs),
        hdrSubtitleStyle = SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.readFrom(prefs),
        userStylePresets = SubtitlePreferenceSpecs.SUBTITLE_STYLE_PRESETS.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — plain-typed setters delegate to their row's derived write
    // (the encoding — JSON blob — is the row's, not re-declared here); the
    // set-or-remove language overrides and the invariant blobs live below,
    // hand-written.
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
        dataStore.edit { SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.writeTo(it, enabled) }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { SubtitlePreferenceSpecs.SUBTITLE_STYLE.writeTo(it, style) }
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
        dataStore.edit { SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setHdrSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit { SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.writeTo(it, style) }
    }

    suspend fun setHighContrastSubtitles(enabled: Boolean) {
        dataStore.edit { SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.writeTo(it, enabled) }
    }

    suspend fun setSubtitlePreviewInSettings(enabled: Boolean) {
        dataStore.edit { SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.writeTo(it, enabled) }
    }

    suspend fun setPreferAudioDescription(enabled: Boolean) {
        dataStore.edit { SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.writeTo(it, enabled) }
    }

    suspend fun setAppLanguage(language: String?) {
        dataStore.edit {
            if (language != null) it[Keys.APP_LANGUAGE] = language
            else it.remove(Keys.APP_LANGUAGE)
        }
    }

    /**
     * Persists the user-named subtitle style presets. The empty list
     * removes the key so storage carries no no-op blob — matching the
     * identity-by-absence read default, like [setTrackSelectionRules].
     */
    suspend fun setSubtitleStylePresets(presets: List<SubtitleStylePreset>) {
        dataStore.edit { prefs ->
            if (presets.isEmpty()) prefs.remove(Keys.SUBTITLE_STYLE_PRESETS)
            else prefs[Keys.SUBTITLE_STYLE_PRESETS] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<List<SubtitleStylePreset>>(),
                presets,
            )
        }
    }

    /**
     * Persists the track-language rule set. The identity rule set
     * (MANUAL preset, nothing configured) removes the key so storage carries
     * no no-op blob — matching the identity-by-absence read default.
     */
    suspend fun setTrackSelectionRules(rules: LanguageRuleSet) {
        dataStore.edit { prefs ->
            if (rules == LanguageRuleSet()) prefs.remove(Keys.TRACK_SELECTION_RULES)
            else prefs[Keys.TRACK_SELECTION_RULES] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<LanguageRuleSet>(), rules,
            )
        }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. Covers both
     * `PreferenceResetCategory.SUBTITLES_LANGUAGE` and the app-wide
     * `MISC_APP` keys ([Keys.APP_LANGUAGE], [Keys.PREFER_AUDIO_DESCRIPTION]).
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [SubtitlePreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. The subtitle/audio/track
     * keys sit in `PreferenceResetCategory.SUBTITLES_LANGUAGE`; the in-app
     * language and audio-description toggles are app-wide (`MISC_APP`),
     * matching the facade's `resetCategoryKeys`.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        SubtitlePreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore — the plain-typed fields and the two style blobs via their
     * row's derived write (the same encoding the row reads with), the nullable
     * language guards and the opted-out blobs via the hand lines whose
     * semantics a row encoding cannot carry (see the class KDoc).
     */
    suspend fun restore(slice: SubtitleSlice) {
        dataStore.edit { prefs ->
            slice.preferredSubtitleLanguage?.let { prefs[Keys.PREFERRED_SUBTITLE_LANG] = it }
            SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.writeTo(prefs, slice.subtitlesForcedOnly)
            slice.preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            prefs[Keys.SUBTITLE_DELAY_BY_ITEM] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<Map<String, Long>>(),
                slice.subtitleDelayByItem,
            )
            SubtitlePreferenceSpecs.SUBTITLE_STYLE.writeTo(prefs, slice.subtitleStyle)
            SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.writeTo(prefs, slice.subtitlePreviewInSettings)
            SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.writeTo(prefs, slice.preferAudioDescription)
            SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.writeTo(prefs, slice.highContrastSubtitles)
            slice.appLanguage?.let { prefs[Keys.APP_LANGUAGE] = it }
            SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.writeTo(prefs, slice.hdrSubtitleStyleEnabled)
            SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.writeTo(prefs, slice.hdrSubtitleStyle)
            prefs[Keys.TRACK_SELECTION_RULES] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<LanguageRuleSet>(),
                slice.languageRules,
            )
            prefs[Keys.SUBTITLE_STYLE_PRESETS] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<List<SubtitleStylePreset>>(),
                slice.userStylePresets,
            )
        }
    }
}

/**
 * The subtitle &amp; language preference slice. Plain data class (Compose-free) so
 * the datastore module stays framework-light. Every default IS its
 * [SubtitlePreferenceSpecs] row's declared default — read off the row via a
 * plain accessor, so a preference's default is stated once, in its spec, and
 * cannot drift between the spec (what an absent key reads) and the slice (what
 * a fresh in-memory instance serves). The HDR style default is the spec's
 * [DEFAULT_HDR_SUBTITLE_STYLE]. The
 * spec-default equality is pinned field-for-field by
 * `SubtitleSpecDerivationTest`.
 */
@Immutable
@Serializable
data class SubtitleSlice(
    val preferredSubtitleLanguage: String? = SubtitlePreferenceSpecs.PREFERRED_SUBTITLE_LANG.default,
    val subtitlesForcedOnly: Boolean = SubtitlePreferenceSpecs.SUBTITLES_FORCED_ONLY.default,
    val preferredAudioLanguage: String? = SubtitlePreferenceSpecs.PREFERRED_AUDIO_LANG.default,
    val subtitleDelayByItem: Map<String, Long> = SubtitlePreferenceSpecs.SUBTITLE_DELAY_BY_ITEM.default,
    /** The track-language rule set; the default instance is the inert identity. */
    val languageRules: LanguageRuleSet = SubtitlePreferenceSpecs.TRACK_SELECTION_RULES.default,
    val subtitleStyle: SubtitleStyle = SubtitlePreferenceSpecs.SUBTITLE_STYLE.default,
    val subtitlePreviewInSettings: Boolean = SubtitlePreferenceSpecs.SUBTITLE_PREVIEW_IN_SETTINGS.default,
    val preferAudioDescription: Boolean = SubtitlePreferenceSpecs.PREFER_AUDIO_DESCRIPTION.default,
    val highContrastSubtitles: Boolean = SubtitlePreferenceSpecs.HIGH_CONTRAST_SUBTITLES.default,
    val appLanguage: String? = SubtitlePreferenceSpecs.APP_LANGUAGE.default,
    val hdrSubtitleStyleEnabled: Boolean = SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE_ENABLED.default,
    val hdrSubtitleStyle: SubtitleStyle = SubtitlePreferenceSpecs.HDR_SUBTITLE_STYLE.default,
    /**
     * The user-named subtitle style presets. Absent-by-default so the
     * v2 backup slice decodes unchanged on older data (unknown keys ignored).
     */
    val userStylePresets: List<SubtitleStylePreset> = SubtitlePreferenceSpecs.SUBTITLE_STYLE_PRESETS.default,
)
