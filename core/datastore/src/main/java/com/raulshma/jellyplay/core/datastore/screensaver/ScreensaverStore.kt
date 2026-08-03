package com.raulshma.jellyplay.core.datastore.screensaver

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
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
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
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class ScreensaverStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey("dream_image_categories")
        val DREAM_SLIDESHOW_INTERVAL_MS = longPreferencesKey("dream_slideshow_interval_ms")
        val DREAM_KEN_BURNS_ENABLED = booleanPreferencesKey("dream_ken_burns_enabled")
        val DREAM_TRANSITION_STYLE = stringPreferencesKey("dream_transition_style")
        val DREAM_SHOW_TITLE = booleanPreferencesKey("dream_show_title")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    /**
     * Memoised decode of the JSON-encoded dream image categories, keyed on the
     * raw string so the decode is skipped when the underlying key has not
     * changed on a given `dataStore.data` emission.
     */
    private var cachedDreamImageCategories: ParsedCache<Set<DreamImageCategory>> =
        ParsedCache(null, DEFAULT_DREAM_IMAGE_CATEGORIES)

    val screensaver: StateFlow<ScreensaverSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ScreensaverSlice())

    internal fun read(prefs: Preferences): ScreensaverSlice = ScreensaverSlice(
        dreamImageCategories = readDreamImageCategories(prefs),
        dreamSlideshowIntervalMs = PreferenceCodec.readLong(prefs, Keys.DREAM_SLIDESHOW_INTERVAL_MS, "dream_slideshow_interval_ms", 15_000L),
        dreamKenBurnsEnabled = PreferenceCodec.readBool(prefs, Keys.DREAM_KEN_BURNS_ENABLED, "dream_ken_burns_enabled", true),
        dreamTransitionStyle = readDreamTransitionStyle(prefs),
        dreamShowTitle = PreferenceCodec.readBool(prefs, Keys.DREAM_SHOW_TITLE, "dream_show_title", true),
    )

    private fun readDreamImageCategories(prefs: Preferences): Set<DreamImageCategory> {
        val raw = prefs[Keys.DREAM_IMAGE_CATEGORIES]
        return if (raw != cachedDreamImageCategories.raw) {
            try {
                raw?.let { json.decodeFromString<Set<DreamImageCategory>>(it) } ?: DEFAULT_DREAM_IMAGE_CATEGORIES
            } catch (_: Exception) { DEFAULT_DREAM_IMAGE_CATEGORIES }
                .also { cachedDreamImageCategories = ParsedCache(raw, it) }
        } else {
            cachedDreamImageCategories.value
        }
    }

    private fun readDreamTransitionStyle(prefs: Preferences): DreamTransitionStyle = try {
        DreamTransitionStyle.valueOf(prefs[Keys.DREAM_TRANSITION_STYLE] ?: DreamTransitionStyle.CROSSFADE.name)
    } catch (_: Exception) {
        DreamTransitionStyle.CROSSFADE
    }

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

    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.DREAM_IMAGE_CATEGORIES,
        Keys.DREAM_TRANSITION_STYLE,
        Keys.DREAM_KEN_BURNS_ENABLED,
        Keys.DREAM_SHOW_TITLE,
        Keys.DREAM_SLIDESHOW_INTERVAL_MS,
    )

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
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the dream keys owned by this store
     * from a decoded [UserPreferences]. The JSON image-category set is written
     * with this store's own [json] codec (same shape `setDreamImageCategories`
     * uses).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.DREAM_IMAGE_CATEGORIES] = json.encodeToString(userPreferences.dreamImageCategories)
            it[Keys.DREAM_SLIDESHOW_INTERVAL_MS] = userPreferences.dreamSlideshowIntervalMs
            it[Keys.DREAM_KEN_BURNS_ENABLED] = userPreferences.dreamKenBurnsEnabled
            it[Keys.DREAM_TRANSITION_STYLE] = userPreferences.dreamTransitionStyle.name
            it[Keys.DREAM_SHOW_TITLE] = userPreferences.dreamShowTitle
        }
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
        }
    }
}

private val DEFAULT_DREAM_IMAGE_CATEGORIES: Set<DreamImageCategory> =
    setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)

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
)
