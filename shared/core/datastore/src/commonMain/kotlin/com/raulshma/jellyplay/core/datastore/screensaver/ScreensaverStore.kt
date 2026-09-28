package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
 * which image categories feed the dream (movies / series / music / photos),
 * the slideshow interval, the ken-burns zoom toggle, the crossfade/slide/none
 * transition style, the title overlay, the local maximum parental rating cap,
 * and the dim-after-delay mode.
 *
 * Also the home of the desktop-shell integration settings (the idle-ambient
 * pair precedent): the Discord Rich Presence toggle (feature 4.2) and the
 * playback-event shell-hook commands + master toggle (feature 4.3). The rows
 * are desktop-gated in the settings surface (SettingsCapabilities flags); the
 * keys themselves are shared-store — harmless where nothing consumes them.
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
        val DREAM_MAX_PARENTAL_RATING = intPreferencesKey("dream_max_parental_rating")
        val DREAM_DIM_AFTER_MS = longPreferencesKey("dream_dim_after_ms")
        val DREAM_DIM_PERCENT = intPreferencesKey("dream_dim_percent")
        val IDLE_AMBIENT_ENABLED = booleanPreferencesKey("idle_ambient_enabled")
        val IDLE_AMBIENT_TIMEOUT_MIN = longPreferencesKey("idle_ambient_timeout_min")
        val DISCORD_PRESENCE_ENABLED = booleanPreferencesKey("discord_presence_enabled")
        val HOOKS_ENABLED = booleanPreferencesKey("hooks_enabled")
        val HOOKS_PLAY_CMD = stringPreferencesKey("hooks_play_cmd")
        val HOOKS_STOP_CMD = stringPreferencesKey("hooks_stop_cmd")
        val HOOKS_ENDED_CMD = stringPreferencesKey("hooks_ended_cmd")
        val HOOKS_IDLE_CMD = stringPreferencesKey("hooks_idle_cmd")
        val HOOKS_IDLE_ENDED_CMD = stringPreferencesKey("hooks_idle_ended_cmd")
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
        dreamMaxParentalRating = readDreamMaxParentalRating(prefs),
        dreamDimAfterMs = PreferenceCodec.readLong(prefs, Keys.DREAM_DIM_AFTER_MS, "dream_dim_after_ms", DEFAULT_DREAM_DIM_AFTER_MS),
        dreamDimPercent = PreferenceCodec.readInt(prefs, Keys.DREAM_DIM_PERCENT, "dream_dim_percent", DEFAULT_DREAM_DIM_PERCENT),
        idleAmbientEnabled = PreferenceCodec.readBool(prefs, Keys.IDLE_AMBIENT_ENABLED, "idle_ambient_enabled", true),
        idleAmbientTimeoutMin = PreferenceCodec.readLong(prefs, Keys.IDLE_AMBIENT_TIMEOUT_MIN, "idle_ambient_timeout_min", DEFAULT_IDLE_AMBIENT_TIMEOUT_MIN),
        discordPresenceEnabled = PreferenceCodec.readBool(prefs, Keys.DISCORD_PRESENCE_ENABLED, "discord_presence_enabled", DEFAULT_DISCORD_PRESENCE_ENABLED),
        hooksEnabled = PreferenceCodec.readBool(prefs, Keys.HOOKS_ENABLED, "hooks_enabled", DEFAULT_HOOKS_ENABLED),
        hooksPlayCmd = prefs[Keys.HOOKS_PLAY_CMD] ?: "",
        hooksStopCmd = prefs[Keys.HOOKS_STOP_CMD] ?: "",
        hooksEndedCmd = prefs[Keys.HOOKS_ENDED_CMD] ?: "",
        hooksIdleCmd = prefs[Keys.HOOKS_IDLE_CMD] ?: "",
        hooksIdleEndedCmd = prefs[Keys.HOOKS_IDLE_ENDED_CMD] ?: "",
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

    /**
     * The stored `dream_max_parental_rating` Int unfolds back to the slice's
     * canonical-age domain: 0 = none (`null` = no local cap), else the stored
     * value is [canonical age + 1] ([encodeDreamMaxParentalRating]'s inverse).
     */
    private fun readDreamMaxParentalRating(prefs: Preferences): Int? =
        PreferenceCodec.readInt(prefs, Keys.DREAM_MAX_PARENTAL_RATING, "dream_max_parental_rating", 0)
            .takeIf { it > 0 }
            ?.let { it - 1 }

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
     * The dream's local maximum parental rating — a second, stricter
     * client-side cap on top of the signed-in user's server policy (the
     * network tail already applies that one). [age] is the canonical rating
     * age (`com.raulshma.jellyplay.core.network.library.parentalRatingAge`);
     * null = no local cap. Stored via [encodeDreamMaxParentalRating] so the
     * G cap (canonical age 0) stays distinguishable from the off sentinel.
     */
    suspend fun setDreamMaxParentalRating(age: Int?) {
        dataStore.edit { it[Keys.DREAM_MAX_PARENTAL_RATING] = encodeDreamMaxParentalRating(age) }
    }

    /** Slideshow runtime after which the dream dims; 0 = never dims. */
    suspend fun setDreamDimAfterMs(ms: Long) {
        dataStore.edit { it[Keys.DREAM_DIM_AFTER_MS] = ms.coerceAtLeast(0L) }
    }

    /** The dim scrim's target opacity in percent; clamped to 0–95. */
    suspend fun setDreamDimPercent(percent: Int) {
        dataStore.edit { it[Keys.DREAM_DIM_PERCENT] = percent.coerceIn(0, MAX_DREAM_DIM_PERCENT) }
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
     * The desktop Discord Rich Presence toggle (feature 4.2). Off (the
     * default) leaves the presence service disconnected.
     */
    suspend fun setDiscordPresenceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCORD_PRESENCE_ENABLED] = enabled }
    }

    /** The desktop playback-event shell hooks' master toggle (feature 4.3). */
    suspend fun setHooksEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HOOKS_ENABLED] = enabled }
    }

    /**
     * The five shell-hook commands (feature 4.3) — mpv-shim event names for
     * familiarity. Empty string = that hook is disabled; per-event
     * placeholders (`{title}` / `{itemId}` / `{position_ms}`) are substituted
     * by the runner.
     */
    suspend fun setHooksPlayCmd(command: String) {
        dataStore.edit { it[Keys.HOOKS_PLAY_CMD] = command }
    }

    suspend fun setHooksStopCmd(command: String) {
        dataStore.edit { it[Keys.HOOKS_STOP_CMD] = command }
    }

    suspend fun setHooksEndedCmd(command: String) {
        dataStore.edit { it[Keys.HOOKS_ENDED_CMD] = command }
    }

    suspend fun setHooksIdleCmd(command: String) {
        dataStore.edit { it[Keys.HOOKS_IDLE_CMD] = command }
    }

    suspend fun setHooksIdleEndedCmd(command: String) {
        dataStore.edit { it[Keys.HOOKS_IDLE_ENDED_CMD] = command }
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
            Keys.DREAM_MAX_PARENTAL_RATING,
            Keys.DREAM_DIM_AFTER_MS,
            Keys.DREAM_DIM_PERCENT,
            Keys.IDLE_AMBIENT_ENABLED,
            Keys.IDLE_AMBIENT_TIMEOUT_MIN,
            Keys.DISCORD_PRESENCE_ENABLED,
            Keys.HOOKS_ENABLED,
            Keys.HOOKS_PLAY_CMD,
            Keys.HOOKS_STOP_CMD,
            Keys.HOOKS_ENDED_CMD,
            Keys.HOOKS_IDLE_CMD,
            Keys.HOOKS_IDLE_ENDED_CMD,
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
            it[Keys.DREAM_MAX_PARENTAL_RATING] = encodeDreamMaxParentalRating(slice.dreamMaxParentalRating)
            it[Keys.DREAM_DIM_AFTER_MS] = slice.dreamDimAfterMs
            it[Keys.DREAM_DIM_PERCENT] = slice.dreamDimPercent
            it[Keys.IDLE_AMBIENT_ENABLED] = slice.idleAmbientEnabled
            it[Keys.IDLE_AMBIENT_TIMEOUT_MIN] = slice.idleAmbientTimeoutMin
            it[Keys.DISCORD_PRESENCE_ENABLED] = slice.discordPresenceEnabled
            it[Keys.HOOKS_ENABLED] = slice.hooksEnabled
            it[Keys.HOOKS_PLAY_CMD] = slice.hooksPlayCmd
            it[Keys.HOOKS_STOP_CMD] = slice.hooksStopCmd
            it[Keys.HOOKS_ENDED_CMD] = slice.hooksEndedCmd
            it[Keys.HOOKS_IDLE_CMD] = slice.hooksIdleCmd
            it[Keys.HOOKS_IDLE_ENDED_CMD] = slice.hooksIdleEndedCmd
        }
    }
}

private val DEFAULT_DREAM_IMAGE_CATEGORIES: Set<DreamImageCategory> =
    setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)

/** Default: the dream never dims (the dim-after picker's "Off" entry). */
internal const val DEFAULT_DREAM_DIM_AFTER_MS = 0L

/**
 * Default: the dim scrim targets 50% opacity — an enabled dim timer must do
 * something visible without a second trip into settings.
 */
internal const val DEFAULT_DREAM_DIM_PERCENT = 50

/**
 * The dim scrim's ceiling (percent) — a fully black dream looks like a crash.
 * Source of truth for both the write-side clamp here and the dream
 * composable's render-side clamp (`DreamSlideshow.kt` in the tv app module).
 */
const val MAX_DREAM_DIM_PERCENT = 95

/** Default: the desktop idle ambient screen appears after 5 idle minutes. */
internal const val DEFAULT_IDLE_AMBIENT_TIMEOUT_MIN = 5L

/**
 * Default: Discord Rich Presence is OFF — an opt-in integration that must
 * not reach out to a local Discord client uninvited.
 */
internal const val DEFAULT_DISCORD_PRESENCE_ENABLED = false

/** Default: the playback-event shell hooks are OFF — an opt-in power-user surface. */
internal const val DEFAULT_HOOKS_ENABLED = false

/**
 * The stored encoding of the dream's local rating cap: 0 = none, else
 * canonical age + 1 — the +1 keeps the G cap (canonical age 0, per the
 * rating table) distinguishable from the off sentinel.
 */
private fun encodeDreamMaxParentalRating(age: Int?): Int = age?.coerceAtLeast(0)?.plus(1) ?: 0

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
    /**
     * The dream's local maximum parental rating (canonical age), or null for
     * no local cap — a second, stricter pass on top of the server policy.
     */
    val dreamMaxParentalRating: Int? = null,
    /** Slideshow runtime after which the dream dims; 0 = never dims. */
    val dreamDimAfterMs: Long = DEFAULT_DREAM_DIM_AFTER_MS,
    /** The dim scrim's target opacity in percent (0–95). */
    val dreamDimPercent: Int = DEFAULT_DREAM_DIM_PERCENT,
    /** Desktop idle ambient screen toggle + timeout (minutes; 0 = off). */
    val idleAmbientEnabled: Boolean = true,
    val idleAmbientTimeoutMin: Long = 5L,
    /** Desktop Discord Rich Presence toggle (feature 4.2); off = no activity published. */
    val discordPresenceEnabled: Boolean = DEFAULT_DISCORD_PRESENCE_ENABLED,
    /**
     * The desktop playback-event shell hooks (feature 4.3): the master
     * toggle plus the five mpv-shim-named commands; empty command = that
     * hook disabled.
     */
    val hooksEnabled: Boolean = DEFAULT_HOOKS_ENABLED,
    val hooksPlayCmd: String = "",
    val hooksStopCmd: String = "",
    val hooksEndedCmd: String = "",
    val hooksIdleCmd: String = "",
    val hooksIdleEndedCmd: String = "",
)
