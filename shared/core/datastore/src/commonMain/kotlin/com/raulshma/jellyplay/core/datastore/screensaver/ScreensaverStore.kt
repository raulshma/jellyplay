package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Deep module owning the **screensaver / Android dream** preference domain:
 * which image categories feed the dream (movies / series / music), the
 * slideshow interval, the ken-burns zoom toggle, the crossfade/slide/none
 * transition style, and the title overlay.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection, JSON memoisation, and reset list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class ScreensaverStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey("dream_image_categories")
        val DREAM_SLIDESHOW_INTERVAL_MS = longPreferencesKey("dream_slideshow_interval_ms")
        val DREAM_KEN_BURNS_ENABLED = booleanPreferencesKey("dream_ken_burns_enabled")
        val DREAM_TRANSITION_STYLE = stringPreferencesKey("dream_transition_style")
        val DREAM_SHOW_TITLE = booleanPreferencesKey("dream_show_title")
        val IDLE_AMBIENT_ENABLED = booleanPreferencesKey("idle_ambient_enabled")
        val IDLE_AMBIENT_TIMEOUT_MIN = longPreferencesKey("idle_ambient_timeout_min")
    }

    /**
     * Memoised decode of the JSON-encoded dream image categories, keyed on the
     * raw string so the decode is skipped when the underlying key has not
     * changed on a given `dataStore.data` emission.
     */
    private var cachedDreamImageCategories: ParsedCache<Set<DreamImageCategory>> =
        ParsedCache(null, DEFAULT_DREAM_IMAGE_CATEGORIES)

    val screensaver: StateFlow<ScreensaverSlice> =
        dataStore.sliceStateFlow(scope, seed = ScreensaverSlice(), read = ::read)

    internal fun read(prefs: Preferences): ScreensaverSlice = ScreensaverSlice(
        dreamImageCategories = readDreamImageCategories(prefs),
        dreamSlideshowIntervalMs = PreferenceCodec.readLong(prefs, Keys.DREAM_SLIDESHOW_INTERVAL_MS, "dream_slideshow_interval_ms", 15_000L),
        dreamKenBurnsEnabled = PreferenceCodec.readBool(prefs, Keys.DREAM_KEN_BURNS_ENABLED, "dream_ken_burns_enabled", true),
        dreamTransitionStyle = readDreamTransitionStyle(prefs),
        dreamShowTitle = PreferenceCodec.readBool(prefs, Keys.DREAM_SHOW_TITLE, "dream_show_title", true),
        idleAmbientEnabled = PreferenceCodec.readBool(prefs, Keys.IDLE_AMBIENT_ENABLED, "idle_ambient_enabled", true),
        idleAmbientTimeoutMin = PreferenceCodec.readLong(prefs, Keys.IDLE_AMBIENT_TIMEOUT_MIN, "idle_ambient_timeout_min", DEFAULT_IDLE_AMBIENT_TIMEOUT_MIN),
    )

    // MemoizeNull (this store's pre-promotion policy): a null raw is a
    // cacheable input — the decoded value depends only on the raw string, so
    // a memoised default is safe to serve.
    private fun readDreamImageCategories(prefs: Preferences): Set<DreamImageCategory> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.DREAM_IMAGE_CATEGORIES],
            cache = cachedDreamImageCategories,
            default = DEFAULT_DREAM_IMAGE_CATEGORIES,
            parse = { json.decodeFromString<Set<DreamImageCategory>>(it) },
            cacheRef = { cachedDreamImageCategories = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    private fun readDreamTransitionStyle(prefs: Preferences): DreamTransitionStyle =
        prefs[Keys.DREAM_TRANSITION_STYLE].toEnumOrNull() ?: DreamTransitionStyle.CROSSFADE

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        dataStore.edit { it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(categories) }
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        dataStore.edit { it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = ms }
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DREAM_KEN_BURNS_ENABLED] = enabled }
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        dataStore.edit { it[Keys.DREAM_TRANSITION_STYLE] = style.name }
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        dataStore.edit { it[Keys.DREAM_SHOW_TITLE] = enabled }
    }

    /**
     * The desktop idle "Ready to play" ambient screen. Timeout is in
     * MINUTES; 0 disables regardless of the toggle (the picker's "Off" entry).
     */
    suspend fun setIdleAmbientEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.IDLE_AMBIENT_ENABLED] = enabled }
    }

    suspend fun setIdleAmbientTimeoutMin(minutes: Long) {
        dataStore.edit { it[Keys.IDLE_AMBIENT_TIMEOUT_MIN] = minutes }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: every key owned here sits in the single
     * `SCREENSAVER` reset category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.SCREENSAVER -> listOf(
            Keys.DREAM_IMAGE_CATEGORIES,
            Keys.DREAM_TRANSITION_STYLE,
            Keys.DREAM_KEN_BURNS_ENABLED,
            Keys.DREAM_SHOW_TITLE,
            Keys.DREAM_SLIDESHOW_INTERVAL_MS,
            Keys.IDLE_AMBIENT_ENABLED,
            Keys.IDLE_AMBIENT_TIMEOUT_MIN,
        )
        else -> emptyList()
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (image-category
     * set via this store's [json] codec).
     */
    suspend fun restore(slice: ScreensaverSlice) {
        dataStore.edit { it ->
            it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(slice.dreamImageCategories)
            it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = slice.dreamSlideshowIntervalMs
            it[Keys.DREAM_KEN_BURNS_ENABLED] = slice.dreamKenBurnsEnabled
            it[Keys.DREAM_TRANSITION_STYLE] = slice.dreamTransitionStyle.name
            it[Keys.DREAM_SHOW_TITLE] = slice.dreamShowTitle
            it[Keys.IDLE_AMBIENT_ENABLED] = slice.idleAmbientEnabled
            it[Keys.IDLE_AMBIENT_TIMEOUT_MIN] = slice.idleAmbientTimeoutMin
        }
    }
}

private val DEFAULT_DREAM_IMAGE_CATEGORIES: Set<DreamImageCategory> =
    setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)

/** Default: the desktop idle ambient screen appears after 5 idle minutes. */
internal const val DEFAULT_IDLE_AMBIENT_TIMEOUT_MIN = 5L

/**
 * The screensaver / dream preference slice. Plain data class. Defaults mirror
 * the projection defaults in [ScreensaverStore.read].
 */
@Immutable
@Serializable
data class ScreensaverSlice(
    val dreamImageCategories: Set<DreamImageCategory> = DEFAULT_DREAM_IMAGE_CATEGORIES,
    val dreamSlideshowIntervalMs: Long = 15_000L,
    val dreamKenBurnsEnabled: Boolean = true,
    val dreamTransitionStyle: DreamTransitionStyle = DreamTransitionStyle.CROSSFADE,
    val dreamShowTitle: Boolean = true,
    /** Desktop idle ambient screen toggle + timeout (minutes; 0 = off). */
    val idleAmbientEnabled: Boolean = true,
    val idleAmbientTimeoutMin: Long = 5L,
)
