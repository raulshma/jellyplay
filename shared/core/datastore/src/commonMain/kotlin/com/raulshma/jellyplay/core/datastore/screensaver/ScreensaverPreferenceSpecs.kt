package com.raulshma.jellyplay.core.datastore.screensaver

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.serialization.encodeToString

/** Default dream feed: the movies + series categories. */
internal val DEFAULT_DREAM_IMAGE_CATEGORIES: Set<DreamImageCategory> =
    setOf(DreamImageCategory.MOVIES, DreamImageCategory.SERIES)

/**
 * Memoisation holder for the [ScreensaverPreferenceSpecs.DREAM_IMAGE_CATEGORIES]
 * row's JSON decode, keyed on the raw string so the decode is skipped when the
 * key has not changed on a given `dataStore.data` emission. Moved here from a
 * per-`ScreensaverStore` field with the row it belongs to (the playback
 * domain's passthrough-codec cache precedent): the decode is a pure function of
 * the raw string, and a null raw memoises its default exactly once
 * ([CachedJsonNullPolicy.MemoizeNull] — this store's pre-promotion policy). See
 * [PreferenceCodec.cachedJson].
 */
private var cachedDreamImageCategories: ParsedCache<Set<DreamImageCategory>> =
    ParsedCache(null, DEFAULT_DREAM_IMAGE_CATEGORIES)

/**
 * The screensaver / dream domain's single preference declaration: one
 * [PreferenceSpec] row per key `ScreensaverStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainLong] /
 * [enumRow] / [derived]).
 *
 * `ScreensaverStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. Every key this store owns resets under the single
 * `SCREENSAVER` category.
 *
 * Not derivable by design (the accepted residuals, the videoplayer/playback
 * precedent):
 *  - the bounds-coercion setters ([ScreensaverStore.setDreamDimAfterMs] /
 *    [ScreensaverStore.setDreamDimPercent]) stay hand-written in the store —
 *    the derived write stores the raw value, the setter is the clamp owner;
 *  - the `dream_max_parental_rating` row declares the STORED Int slot (wire
 *    name, default, reset participation); the Int? ↔ Int codec that folds the
 *    stored value into the slice's canonical-age domain stays hand-written in
 *    the store (a row encoding cannot change the row's value type) — see
 *    [ScreensaverStore]'s read/`encodeDreamMaxParentalRating`.
 *
 * Adding a preference: one row here, one [ScreensaverSlice] property, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings and reset lists,
 * not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object ScreensaverPreferenceSpecs {

    /**
     * Route kind shared by every settings-search row that deep-links into the
     * main settings screen (this domain's whole catalog: the dream group
     * renders there, and the desktop shell rows beside it) — the plain id the
     * feature layer maps to `Route.Settings`.
     */
    const val ROUTE_SETTINGS = "settings"

    // ------------------------------------------------------------------
    // Dream (Android screensaver)
    // ------------------------------------------------------------------

    /**
     * The JSON-encoded set of dream image categories; the decode is memoised
     * on the raw string — see [cachedDreamImageCategories]. The derived write
     * re-encodes the set exactly as the former setter/restore wrote it.
     */
    val DREAM_IMAGE_CATEGORIES: PreferenceSpec<Set<DreamImageCategory>> = PreferenceSpec.derived(
        keyName = "dream_image_categories",
        default = DEFAULT_DREAM_IMAGE_CATEGORIES,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        read = { _, raw ->
            PreferenceCodec.cachedJson(
                raw = raw,
                cache = cachedDreamImageCategories,
                default = DEFAULT_DREAM_IMAGE_CATEGORIES,
                parse = { PreferenceCodec.json.decodeFromString<Set<DreamImageCategory>>(it) },
                cacheRef = { cachedDreamImageCategories = it },
                nullPolicy = CachedJsonNullPolicy.MemoizeNull,
            )
        },
        encode = { PreferenceCodec.json.encodeToString(it) },
        search = PreferenceSearchSpec(
            id = "screensaver_categories",
            titleKey = "ss_screensaver_categories_title",
            subtitleKey = "ss_screensaver_categories_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "categories", "tv", "movies", "music", "content"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    val DREAM_SLIDESHOW_INTERVAL_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "dream_slideshow_interval_ms",
        default = 15_000L,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "screensaver_slideshow_interval",
            titleKey = "ss_screensaver_slideshow_interval_title",
            subtitleKey = "ss_screensaver_slideshow_interval_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "slideshow", "interval", "tv", "duration", "seconds"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    val DREAM_KEN_BURNS_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "dream_ken_burns_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "screensaver_ken_burns",
            titleKey = "ss_screensaver_ken_burns_title",
            subtitleKey = "ss_screensaver_ken_burns_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "ken burns", "pan", "zoom", "animation", "tv"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    val DREAM_TRANSITION_STYLE: PreferenceSpec<DreamTransitionStyle> = PreferenceSpec.enumRow(
        keyName = "dream_transition_style",
        default = DreamTransitionStyle.CROSSFADE,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "screensaver_transition_style",
            titleKey = "ss_screensaver_transition_style_title",
            subtitleKey = "ss_screensaver_transition_style_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "transition", "style", "crossfade", "slide", "tv"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    val DREAM_SHOW_TITLE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "dream_show_title",
        default = true,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "screensaver_show_title",
            titleKey = "ss_screensaver_show_title_title",
            subtitleKey = "ss_screensaver_show_title_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "title", "tv", "show", "media title", "display"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    /**
     * The STORED `dream_max_parental_rating` slot: 0 = no local cap, else the
     * canonical rating age + 1 (the +1 keeps the G cap — canonical age 0 —
     * distinguishable from the off sentinel). The row declares the wire name,
     * typed storage (with its legacy string fallback), default and reset
     * participation; the slice-facing `Int?` codec stays hand-written in the
     * store beside the row (see the object KDoc's residuals).
     */
    val DREAM_MAX_PARENTAL_RATING: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "dream_max_parental_rating",
        default = 0,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_dream_max_parental_rating",
            id = "screensaver_max_parental_rating",
            subtitleKey = "ss_screensaver_max_parental_rating_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "parental", "rating", "mature", "adult", "kids", "family", "filter"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    /** Slideshow runtime after which the dream dims; 0 = never dims. */
    val DREAM_DIM_AFTER_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "dream_dim_after_ms",
        default = DEFAULT_DREAM_DIM_AFTER_MS,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_dream_dim_after",
            id = "screensaver_dim_after",
            subtitleKey = "ss_screensaver_dim_after_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "dim", "fade", "dark", "night", "delay", "timer", "tv"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    /** The dim scrim's target opacity in percent (the setter clamps 0–95). */
    val DREAM_DIM_PERCENT: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "dream_dim_percent",
        default = DEFAULT_DREAM_DIM_PERCENT,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_dream_dim_percent",
            id = "screensaver_dim_percent",
            subtitleKey = "ss_screensaver_dim_percent_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("screensaver", "dream", "dim", "brightness", "dark", "opacity", "percent", "tv"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    // ------------------------------------------------------------------
    // Desktop shell integrations
    // ------------------------------------------------------------------

    /** The desktop idle "Ready to play" ambient screen toggle. */
    val IDLE_AMBIENT_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "idle_ambient_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "idle_ambient_enabled",
            titleKey = "ss_idle_ambient_enabled_title",
            subtitleKey = "ss_idle_ambient_enabled_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("idle", "ambient", "ready to play", "screensaver", "desktop", "standby"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    /** Idle-ambient timeout in MINUTES; 0 disables regardless of the toggle. */
    val IDLE_AMBIENT_TIMEOUT_MIN: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "idle_ambient_timeout_min",
        default = DEFAULT_IDLE_AMBIENT_TIMEOUT_MIN,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            id = "idle_ambient_timeout",
            titleKey = "ss_idle_ambient_timeout_title",
            subtitleKey = "ss_idle_ambient_timeout_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("idle", "ambient", "timeout", "minutes", "screensaver", "desktop", "standby"),
            routeKind = ROUTE_SETTINGS,
        ),
    )

    /** The desktop Discord Rich Presence toggle (feature 4.2). */
    val DISCORD_PRESENCE_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "discord_presence_enabled",
        default = DEFAULT_DISCORD_PRESENCE_ENABLED,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_discord_presence_enabled",
            id = "discord_presence_enabled",
            subtitleKey = "ss_discord_presence_enabled_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("discord", "rich presence", "rpc", "activity", "watching", "listening", "status", "syncplay", "join"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    /** The desktop playback-event shell hooks' master toggle (feature 4.3). */
    val HOOKS_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "hooks_enabled",
        default = DEFAULT_HOOKS_ENABLED,
        resetCategory = PreferenceResetCategory.SCREENSAVER,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_hooks_enabled",
            id = "hooks_enabled",
            subtitleKey = "ss_hooks_enabled_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "command", "script", "automation", "shell", "mpv", "event", "run"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    // The five shell-hook commands: plain string slots, empty = hook disabled,
    // served by the row default on absence (identity encode).

    val HOOKS_PLAY_CMD: PreferenceSpec<String> = cmdRow(
        "hooks_play_cmd",
        search = PreferenceSearchSpec(
            // Each command row restates its screen title (the fold): the
            // catalog rows declare no ss_*_title twins.
            titleKey = "settings_hooks_play_cmd",
            id = "hooks_play_cmd",
            subtitleKey = "ss_hooks_play_cmd_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "on_play_started", "play", "start", "command", "script"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    val HOOKS_STOP_CMD: PreferenceSpec<String> = cmdRow(
        "hooks_stop_cmd",
        search = PreferenceSearchSpec(
            titleKey = "settings_hooks_stop_cmd",
            id = "hooks_stop_cmd",
            subtitleKey = "ss_hooks_stop_cmd_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "on_play_stopped", "stop", "quit", "command", "script"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    val HOOKS_ENDED_CMD: PreferenceSpec<String> = cmdRow(
        "hooks_ended_cmd",
        search = PreferenceSearchSpec(
            titleKey = "settings_hooks_ended_cmd",
            id = "hooks_ended_cmd",
            subtitleKey = "ss_hooks_ended_cmd_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "on_media_ended", "end", "finished", "eof", "command", "script"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    val HOOKS_IDLE_CMD: PreferenceSpec<String> = cmdRow(
        "hooks_idle_cmd",
        search = PreferenceSearchSpec(
            titleKey = "settings_hooks_idle_cmd",
            id = "hooks_idle_cmd",
            subtitleKey = "ss_hooks_idle_cmd_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "on_idle_enter", "idle", "standby", "command", "script"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    val HOOKS_IDLE_ENDED_CMD: PreferenceSpec<String> = cmdRow(
        "hooks_idle_ended_cmd",
        search = PreferenceSearchSpec(
            titleKey = "settings_hooks_idle_ended_cmd",
            id = "hooks_idle_ended_cmd",
            subtitleKey = "ss_hooks_idle_ended_cmd_subtitle",
            categoryKey = "ss_cat_system",
            keywords = listOf("hooks", "on_idle_exit", "idle", "wake", "resume", "command", "script"),
            routeKind = ROUTE_SETTINGS,
            platformRule = PreferencePlatformRule.DESKTOP_ONLY,
        ),
    )

    /**
     * A shell-hook command row: raw string slot served as `""` on absence,
     * identity encode — the derived-string-row shape of the videoplayer
     * domain's aspect-ratio row.
     */
    private fun cmdRow(keyName: String, search: PreferenceSearchSpec? = null): PreferenceSpec<String> =
        PreferenceSpec.derived(
            keyName = keyName,
            default = "",
            resetCategory = PreferenceResetCategory.SCREENSAVER,
            read = { _, raw -> raw ?: "" },
            encode = { it },
            search = search,
        )

    /**
     * Every row this store declares, in [ScreensaverSlice] property order.
     * The reset derivation below and the derivation-integrity test both
     * iterate this — a row declared but forgotten here falls out of reset
     * coverage and is caught by the JVM reset-coverage guard.
     */
    val all: List<PreferenceSpec<*>> = listOf(
        DREAM_IMAGE_CATEGORIES,
        DREAM_SLIDESHOW_INTERVAL_MS,
        DREAM_KEN_BURNS_ENABLED,
        DREAM_TRANSITION_STYLE,
        DREAM_SHOW_TITLE,
        DREAM_MAX_PARENTAL_RATING,
        DREAM_DIM_AFTER_MS,
        DREAM_DIM_PERCENT,
        IDLE_AMBIENT_ENABLED,
        IDLE_AMBIENT_TIMEOUT_MIN,
        DISCORD_PRESENCE_ENABLED,
        HOOKS_ENABLED,
        HOOKS_PLAY_CMD,
        HOOKS_STOP_CMD,
        HOOKS_ENDED_CMD,
        HOOKS_IDLE_CMD,
        HOOKS_IDLE_ENDED_CMD,
    )

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key owned here
     * sits in the single `SCREENSAVER` bucket. The facade aggregates these
     * lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, in the settings feature's
     * catalog order (the order the retired hand records carried): every row
     * above that carries a [PreferenceSearchSpec]. The settings feature
     * derives its spec-backed catalog rows from this list over its ordered
     * record spine; the two navigation rows (admin dashboard, setup wizard)
     * stay feature-side residuals — their knobs live in stores without spec
     * machinery.
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        DREAM_SHOW_TITLE.searchEntry(),
        DREAM_IMAGE_CATEGORIES.searchEntry(),
        DREAM_SLIDESHOW_INTERVAL_MS.searchEntry(),
        DREAM_KEN_BURNS_ENABLED.searchEntry(),
        DREAM_TRANSITION_STYLE.searchEntry(),
        DREAM_MAX_PARENTAL_RATING.searchEntry(),
        DREAM_DIM_AFTER_MS.searchEntry(),
        DREAM_DIM_PERCENT.searchEntry(),
        IDLE_AMBIENT_ENABLED.searchEntry(),
        IDLE_AMBIENT_TIMEOUT_MIN.searchEntry(),
        DISCORD_PRESENCE_ENABLED.searchEntry(),
        HOOKS_ENABLED.searchEntry(),
        HOOKS_PLAY_CMD.searchEntry(),
        HOOKS_STOP_CMD.searchEntry(),
        HOOKS_ENDED_CMD.searchEntry(),
        HOOKS_IDLE_CMD.searchEntry(),
        HOOKS_IDLE_ENDED_CMD.searchEntry(),
    )

    /** The projection hook for [searchEntries]: a row's declared search entry. */
    private fun PreferenceSpec<*>.searchEntry(): PreferenceSearchSpec =
        requireNotNull(search) { "row '$keyName' declares no search entry" }
}
