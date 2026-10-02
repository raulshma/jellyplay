package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

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
 * **Spec derivation**: every key this store owns is declared exactly once as a row in
 * [ScreensaverPreferenceSpecs] (wire name, default, reset category, read/write
 * encoding) and the machinery below is derived from those rows — each [Keys]
 * member rebuilds its row's typed key from the row's wire name, each [read]
 * projection row delegates to its row encoding, the single-key setters and
 * [restore] delegate to the rows' derived writes, and [resetKeysFor] filters
 * the rows by reset category. The image-categories JSON memoisation lives in
 * the [ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES] row's encoding now,
 * next to the key it applies to.
 *
 * **Cross-key invariants owned here (hand-written by decision — a bounds
 * policy or a value-type codec spanning more than a row encoding cannot be
 * derived):**
 *  - [setDreamDimAfterMs] coerces to `coerceAtLeast(0)`; [setDreamDimPercent]
 *    clamps to `0..MAX_DREAM_DIM_PERCENT` (the derived write stores the raw
 *    value — the setter is the clamp owner).
 *  - [setDreamMaxParentalRating] / [restore] fold the slice's `Int?` rating
 *    domain into the row's stored Int slot via [encodeDreamMaxParentalRating]
 *    (0 = none, else canonical age + 1); the read unfolds it in
 *    [readDreamMaxParentalRating].
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [ScreensaverPreferenceSpecs] row + one [ScreensaverSlice] property + one
 * [read] / [restore] row (and its setter) + one write-through test line. See
 * the spec KDoc.
 */
class ScreensaverStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [ScreensaverPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard and the key-identity reference for the hand-written codec lines
     * below.
     */
    internal object Keys {
        val DREAM_IMAGE_CATEGORIES = stringPreferencesKey(ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES.keyName)
        val DREAM_SLIDESHOW_INTERVAL_MS = longPreferencesKey(ScreensaverPreferenceSpecs.DREAM_SLIDESHOW_INTERVAL_MS.keyName)
        val DREAM_KEN_BURNS_ENABLED = booleanPreferencesKey(ScreensaverPreferenceSpecs.DREAM_KEN_BURNS_ENABLED.keyName)
        val DREAM_TRANSITION_STYLE = stringPreferencesKey(ScreensaverPreferenceSpecs.DREAM_TRANSITION_STYLE.keyName)
        val DREAM_SHOW_TITLE = booleanPreferencesKey(ScreensaverPreferenceSpecs.DREAM_SHOW_TITLE.keyName)
        val DREAM_MAX_PARENTAL_RATING = intPreferencesKey(ScreensaverPreferenceSpecs.DREAM_MAX_PARENTAL_RATING.keyName)
        val DREAM_DIM_AFTER_MS = longPreferencesKey(ScreensaverPreferenceSpecs.DREAM_DIM_AFTER_MS.keyName)
        val DREAM_DIM_PERCENT = intPreferencesKey(ScreensaverPreferenceSpecs.DREAM_DIM_PERCENT.keyName)
        val IDLE_AMBIENT_ENABLED = booleanPreferencesKey(ScreensaverPreferenceSpecs.IDLE_AMBIENT_ENABLED.keyName)
        val IDLE_AMBIENT_TIMEOUT_MIN = longPreferencesKey(ScreensaverPreferenceSpecs.IDLE_AMBIENT_TIMEOUT_MIN.keyName)
        val DISCORD_PRESENCE_ENABLED = booleanPreferencesKey(ScreensaverPreferenceSpecs.DISCORD_PRESENCE_ENABLED.keyName)
        val HOOKS_ENABLED = booleanPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_ENABLED.keyName)
        val HOOKS_PLAY_CMD = stringPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_PLAY_CMD.keyName)
        val HOOKS_STOP_CMD = stringPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_STOP_CMD.keyName)
        val HOOKS_ENDED_CMD = stringPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_ENDED_CMD.keyName)
        val HOOKS_IDLE_CMD = stringPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_IDLE_CMD.keyName)
        val HOOKS_IDLE_ENDED_CMD = stringPreferencesKey(ScreensaverPreferenceSpecs.HOOKS_IDLE_ENDED_CMD.keyName)
    }

    val screensaver: StateFlow<ScreensaverSlice> =
        dataStore.sliceStateFlow(scope, seed = ScreensaverSlice(), read = ::read)

    /**
     * Pure read of the screensaver fields from a raw [Preferences] snapshot,
     * each field delegated to its [ScreensaverPreferenceSpecs] row encoding
     * (including the image-categories JSON memoisation — the row's derived
     * read), except the parental-rating field, which unfolds the row's stored
     * Int slot into the slice's canonical-age domain. Exposed so the facade
     * can fold these into the whole preference projection without duplicating
     * the read logic.
     */
    internal fun read(prefs: Preferences): ScreensaverSlice = ScreensaverSlice(
        dreamImageCategories = ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES.readFrom(prefs),
        dreamSlideshowIntervalMs = ScreensaverPreferenceSpecs.DREAM_SLIDESHOW_INTERVAL_MS.readFrom(prefs),
        dreamKenBurnsEnabled = ScreensaverPreferenceSpecs.DREAM_KEN_BURNS_ENABLED.readFrom(prefs),
        dreamTransitionStyle = ScreensaverPreferenceSpecs.DREAM_TRANSITION_STYLE.readFrom(prefs),
        dreamShowTitle = ScreensaverPreferenceSpecs.DREAM_SHOW_TITLE.readFrom(prefs),
        dreamMaxParentalRating = readDreamMaxParentalRating(prefs),
        dreamDimAfterMs = ScreensaverPreferenceSpecs.DREAM_DIM_AFTER_MS.readFrom(prefs),
        dreamDimPercent = ScreensaverPreferenceSpecs.DREAM_DIM_PERCENT.readFrom(prefs),
        idleAmbientEnabled = ScreensaverPreferenceSpecs.IDLE_AMBIENT_ENABLED.readFrom(prefs),
        idleAmbientTimeoutMin = ScreensaverPreferenceSpecs.IDLE_AMBIENT_TIMEOUT_MIN.readFrom(prefs),
        discordPresenceEnabled = ScreensaverPreferenceSpecs.DISCORD_PRESENCE_ENABLED.readFrom(prefs),
        hooksEnabled = ScreensaverPreferenceSpecs.HOOKS_ENABLED.readFrom(prefs),
        hooksPlayCmd = ScreensaverPreferenceSpecs.HOOKS_PLAY_CMD.readFrom(prefs),
        hooksStopCmd = ScreensaverPreferenceSpecs.HOOKS_STOP_CMD.readFrom(prefs),
        hooksEndedCmd = ScreensaverPreferenceSpecs.HOOKS_ENDED_CMD.readFrom(prefs),
        hooksIdleCmd = ScreensaverPreferenceSpecs.HOOKS_IDLE_CMD.readFrom(prefs),
        hooksIdleEndedCmd = ScreensaverPreferenceSpecs.HOOKS_IDLE_ENDED_CMD.readFrom(prefs),
    )

    /**
     * The stored `dream_max_parental_rating` Int unfolds back to the slice's
     * canonical-age domain: 0 = none (`null` = no local cap), else the stored
     * value is [canonical age + 1] ([encodeDreamMaxParentalRating]'s inverse)
     * — the hand-written codec beside the
     * [ScreensaverPreferenceSpecs.DREAM_MAX_PARENTAL_RATING] row.
     */
    private fun readDreamMaxParentalRating(prefs: Preferences): Int? =
        ScreensaverPreferenceSpecs.DREAM_MAX_PARENTAL_RATING.readFrom(prefs)
            .takeIf { it > 0 }
            ?.let { it - 1 }

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write;
    // the bounds coercions and the rating codec live below, hand-written.
    // ------------------------------------------------------------------

    suspend fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES.writeTo(it, categories) }
    }

    suspend fun setDreamSlideshowIntervalMs(ms: Long) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_SLIDESHOW_INTERVAL_MS.writeTo(it, ms) }
    }

    suspend fun setDreamKenBurnsEnabled(enabled: Boolean) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_KEN_BURNS_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_TRANSITION_STYLE.writeTo(it, style) }
    }

    suspend fun setDreamShowTitle(enabled: Boolean) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_SHOW_TITLE.writeTo(it, enabled) }
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
        dataStore.edit {
            ScreensaverPreferenceSpecs.DREAM_MAX_PARENTAL_RATING.writeTo(it, encodeDreamMaxParentalRating(age))
        }
    }

    /** Slideshow runtime after which the dream dims; 0 = never dims. */
    suspend fun setDreamDimAfterMs(ms: Long) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_DIM_AFTER_MS.writeTo(it, ms.coerceAtLeast(0L)) }
    }

    /** The dim scrim's target opacity in percent; clamped to 0–95. */
    suspend fun setDreamDimPercent(percent: Int) {
        dataStore.edit { ScreensaverPreferenceSpecs.DREAM_DIM_PERCENT.writeTo(it, percent.coerceIn(0, MAX_DREAM_DIM_PERCENT)) }
    }

    /**
     * The desktop idle "Ready to play" ambient screen. Timeout is in
     * MINUTES; 0 disables regardless of the toggle (the picker's "Off" entry).
     */
    suspend fun setIdleAmbientEnabled(enabled: Boolean) {
        dataStore.edit { ScreensaverPreferenceSpecs.IDLE_AMBIENT_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setIdleAmbientTimeoutMin(minutes: Long) {
        dataStore.edit { ScreensaverPreferenceSpecs.IDLE_AMBIENT_TIMEOUT_MIN.writeTo(it, minutes) }
    }

    /**
     * The desktop Discord Rich Presence toggle (feature 4.2). Off (the
     * default) leaves the presence service disconnected.
     */
    suspend fun setDiscordPresenceEnabled(enabled: Boolean) {
        dataStore.edit { ScreensaverPreferenceSpecs.DISCORD_PRESENCE_ENABLED.writeTo(it, enabled) }
    }

    /** The desktop playback-event shell hooks' master toggle (feature 4.3). */
    suspend fun setHooksEnabled(enabled: Boolean) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_ENABLED.writeTo(it, enabled) }
    }

    /**
     * The five shell-hook commands (feature 4.3) — mpv-shim event names for
     * familiarity. Empty string = that hook is disabled; per-event
     * placeholders (`{title}` / `{itemId}` / `{position_ms}`) are substituted
     * by the runner.
     */
    suspend fun setHooksPlayCmd(command: String) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_PLAY_CMD.writeTo(it, command) }
    }

    suspend fun setHooksStopCmd(command: String) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_STOP_CMD.writeTo(it, command) }
    }

    suspend fun setHooksEndedCmd(command: String) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_ENDED_CMD.writeTo(it, command) }
    }

    suspend fun setHooksIdleCmd(command: String) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_IDLE_CMD.writeTo(it, command) }
    }

    suspend fun setHooksIdleEndedCmd(command: String) {
        dataStore.edit { ScreensaverPreferenceSpecs.HOOKS_IDLE_ENDED_CMD.writeTo(it, command) }
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
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [ScreensaverPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every key owned here
     * sits in the single `SCREENSAVER` category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        ScreensaverPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with — the image-category set through the row's JSON encode).
     */
    suspend fun restore(slice: ScreensaverSlice) {
        dataStore.edit { prefs ->
            ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES.writeTo(prefs, slice.dreamImageCategories)
            ScreensaverPreferenceSpecs.DREAM_SLIDESHOW_INTERVAL_MS.writeTo(prefs, slice.dreamSlideshowIntervalMs)
            ScreensaverPreferenceSpecs.DREAM_KEN_BURNS_ENABLED.writeTo(prefs, slice.dreamKenBurnsEnabled)
            ScreensaverPreferenceSpecs.DREAM_TRANSITION_STYLE.writeTo(prefs, slice.dreamTransitionStyle)
            ScreensaverPreferenceSpecs.DREAM_SHOW_TITLE.writeTo(prefs, slice.dreamShowTitle)
            ScreensaverPreferenceSpecs.DREAM_MAX_PARENTAL_RATING.writeTo(
                prefs,
                encodeDreamMaxParentalRating(slice.dreamMaxParentalRating),
            )
            ScreensaverPreferenceSpecs.DREAM_DIM_AFTER_MS.writeTo(prefs, slice.dreamDimAfterMs)
            ScreensaverPreferenceSpecs.DREAM_DIM_PERCENT.writeTo(prefs, slice.dreamDimPercent)
            ScreensaverPreferenceSpecs.IDLE_AMBIENT_ENABLED.writeTo(prefs, slice.idleAmbientEnabled)
            ScreensaverPreferenceSpecs.IDLE_AMBIENT_TIMEOUT_MIN.writeTo(prefs, slice.idleAmbientTimeoutMin)
            ScreensaverPreferenceSpecs.DISCORD_PRESENCE_ENABLED.writeTo(prefs, slice.discordPresenceEnabled)
            ScreensaverPreferenceSpecs.HOOKS_ENABLED.writeTo(prefs, slice.hooksEnabled)
            ScreensaverPreferenceSpecs.HOOKS_PLAY_CMD.writeTo(prefs, slice.hooksPlayCmd)
            ScreensaverPreferenceSpecs.HOOKS_STOP_CMD.writeTo(prefs, slice.hooksStopCmd)
            ScreensaverPreferenceSpecs.HOOKS_ENDED_CMD.writeTo(prefs, slice.hooksEndedCmd)
            ScreensaverPreferenceSpecs.HOOKS_IDLE_CMD.writeTo(prefs, slice.hooksIdleCmd)
            ScreensaverPreferenceSpecs.HOOKS_IDLE_ENDED_CMD.writeTo(prefs, slice.hooksIdleEndedCmd)
        }
    }
}

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
 * the projection defaults in [ScreensaverStore.read] (declared on the
 * [ScreensaverPreferenceSpecs] rows).
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
