package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.BitmapSubtitleScaleMode
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.MpvDebandMode
import com.raulshma.jellyplay.core.model.MpvToneMappingCurve
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StillWatchingMode
import com.raulshma.jellyplay.core.model.TrickplayMode
import com.raulshma.jellyplay.core.model.ensureControlsSummonable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

/**
 * The in-player video domain's single preference declaration: one
 * [PreferenceSpec] row per key `VideoPlayerStore` owns — the canonical
 * persisted key name (which is ALSO the legacy string wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainLong] /
 * [plainFloat] / [enumRow] / [derived]).
 *
 * **The row groups below are the ONE registration of this domain's rows.**
 * Each row is constructed exactly once, inside its per-domain group list
 * (`transportTiming` … `mpvRender`) — nothing re-lists them: [all]
 * derives by concatenation of the groups, `resetKeysFor` derives by filtering
 * [all] on the declared [PreferenceSpec.resetCategory], and [searchEntries]
 * derives from the rows' declared [PreferenceSpec.search] (plus the six
 * hand-only standalone segment catalog entries, appended and clearly
 * separated). Adding a preference is therefore one group entry, one named
 * accessor beside the others below, and nothing else on this object — a row
 * cannot be declared and then forgotten by a second list, because there is no
 * second list.
 *
 * The named `val`s are plain accessors over [row]: the public lookup face of
 * the rows, each naming its row's wire name. No reflection enumerates them —
 * property enumeration is impossible without reflection, and the spec tier is
 * R8-safe by stance ([PreferenceSpec]'s KDoc) — so an accessor that names no
 * declared row fails fast at [row] instead, and the accessor↔row binding is
 * pinned by the JVM `VideoPlayerSpecDerivationTest` (which rebuilds every
 * [VideoPlayerStore.Keys] member through these accessors and compares it to
 * the group-declared rows, the same guard the sibling domains run).
 *
 * `VideoPlayerStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. The [VideoPlayerSlice] property defaults read the rows too
 * (`.default`) — a preference's default is stated once, in its spec, not
 * restated on the slice. The reads that carry migration or
 * legacy-fallback logic ([VIDEO_GESTURE_MODE]'s legacy boolean dance,
 * [SEGMENT_BEHAVIORS]'s four-boolean fallback, the raw-string aspect ratio)
 * carry it as [PreferenceSpec.derived] lambdas, documented in place below.
 *
 * Not derivable by design (the no-reflection rule — see the [PreferenceSpec]
 * KDoc):
 *  - the `Keys` aliases stay hand-built (they are the reflection anchor for
 *    the JVM reset-coverage guard), as do the store's `read`/`restore` rows,
 *    its setters and its clamps;
 *  - the bounds-coercion setters (`setVideoPassOutProtectionHours`,
 *    `setVideoSkipBackOnResumeMs`, `setStillWatchingEpisodeThreshold`) and the
 *    read-modify-write `setSegmentBehavior` stay hand-written in the store;
 *  - the derived `restore` keeps ONE hand-written line beside the
 *    [VIDEO_GESTURE_MODE] row — clearing the legacy
 *    `video_gestures_enabled` boolean so a restored snapshot cannot disagree
 *    with the row's read fallback.
 *
 * Adding a preference: one entry in the row group below (its key name,
 * default, reset category, encoding, search), one named accessor beside the
 * others, one [VideoPlayerSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists,
 * search registration and slice defaults, not the slice plumbing or its
 * coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object VideoPlayerPreferenceSpecs {

    /**
     * Memoisation holder for the [SEGMENT_BEHAVIORS] row's JSON decode, keyed
     * on the raw string. Moved here from a per-`VideoPlayerStore` field with
     * the row it belongs to (the playback domain's passthrough-codec cache
     * precedent): only
     * the JSON-blob decode is memoised, and the null-raw path never consults
     * it ([CachedJsonNullPolicy.NoMemoOnNull], because the null value is the
     * legacy-boolean fallback re-derived on every read). See
     * [PreferenceCodec.cachedJson].
     */
    private var cachedSegmentBehaviors: ParsedCache<Map<MediaSegmentType, SegmentBehavior>> =
        ParsedCache(null, SegmentBehavior.DEFAULT_BEHAVIORS)

    /**
     * Memoisation holder for the `input_bindings` row's JSON decode
     * (the [cachedSegmentBehaviors] precedent, same [CachedJsonNullPolicy
     * .NoMemoOnNull] shape — the null-raw path is the legacy-config fallback,
     * re-derived per read because it depends on sibling keys).
     */
    private var cachedInputBindings: ParsedCache<PlayerInputMap> =
        ParsedCache(null, PlayerInputDefaults.defaultMap())

    /** Upper bound for [SEGMENT_SKIP_DELAYS_MS] values — stored values are clamped to it. */
    private const val MAX_SEGMENT_SKIP_DELAY_MS = 60_000L

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [VideoPlayerSlice] property order (with the legacy-only rows beside
    // the rows that read them). A row is constructed exactly once, here;
    // [all], the reset lists and [searchEntries] all derive from the groups.
    // ------------------------------------------------------------------

    /** Transport timing: seek/controls cadence, the OSD-on-pause and headset-resume gates. */
    private val transportTiming: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainLong(
            keyName = "video_seek_duration_ms",
            default = 10_000L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "seek_duration",
                titleKey = "ss_seek_duration_title",
                subtitleKey = "ss_seek_duration_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("seek", "duration", "skip", "double tap", "seconds"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        PreferenceSpec.plainLong(
            keyName = "video_controls_timeout_ms",
            default = 5_000L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "controls_timeout",
                titleKey = "ss_controls_timeout_title",
                subtitleKey = "ss_controls_timeout_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("controls", "timeout", "hide", "overlay"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainLong(
            keyName = "skip_prompt_duration_ms",
            default = 10_000L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "skip_prompt_duration",
                titleKey = "settings_skip_prompt_duration",
                subtitleKey = "settings_skip_prompt_duration_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("skip", "intro", "outro", "button", "duration", "seconds"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Pausing should not summon the control
         * overlay. Default **off** — today's show-on-pause behavior. On, the
         * pause arm of a play/pause transport toggle leaves the overlay hidden
         * (the play arm keeps summoning, and an already-visible overlay is never
         * force-hidden — the auto-hide timeout still owns dismissal).
         */
        PreferenceSpec.plainBoolean(
            keyName = "video_hide_osd_on_pause",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "hide_osd_on_pause",
                // One-key family: a single screen-face key.
                titleKey = "settings_hide_osd_on_pause",
                subtitleKey = "ss_hide_osd_on_pause_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pause", "osd", "overlay", "controls", "hide", "show"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Opt-in resume when headphones reconnect. Default **off**.
         * The decision machinery (paused-by-becoming-noisy marker + freshness
         * window) lives in core:data's `HeadsetResumePolicy` — the pref gates it.
         */
        PreferenceSpec.plainBoolean(
            keyName = "video_resume_on_headset_plug",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "resume_on_headset_plug",
                // One-key family: a single screen-face key.
                titleKey = "settings_resume_headset_plug",
                subtitleKey = "ss_resume_headset_plug_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("headset", "headphones", "resume", "plug", "reconnect", "unplug", "audio"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /** Orientation / aspect / layout. */
    private val orientationLayout: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "video_default_orientation",
            default = OrientationMode.SENSOR_LANDSCAPE,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "orientation",
                titleKey = "ss_orientation_title",
                subtitleKey = "ss_orientation_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("orientation", "rotation", "landscape", "portrait", "sensor"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * The raw aspect-ratio string ("AUTO"/"FIT"/"16:9"/…): not an enum — the
         * picker offers literal ratio strings — so the row is a derived string
         * slot serving [VideoPlayerSlice.videoDefaultAspectRatio]'s default on
         * absence, with the identity encode as its derived write.
         */
        PreferenceSpec.derived(
            keyName = "video_default_aspect_ratio",
            default = "AUTO",
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw -> raw ?: "AUTO" },
            encode = { it },
            search = PreferenceSearchSpec(
                id = "default_aspect",
                titleKey = "ss_default_aspect_title",
                subtitleKey = "ss_default_aspect_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("aspect", "ratio", "stretch", "zoom", "fit", "fill"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
    )

    /**
     * Gestures, with the legacy enabled-boolean pairing and the whole input
     * mapping. The rows' read lambdas consult sibling rows — the lookups ride
     * the named accessors (invoke-time, so object initialization order is
     * never stressed).
     */
    private val gestures: List<PreferenceSpec<*>> = listOf(
        /*
         * Reads [VideoPlayerSlice.videoGestureMode], falling back to the legacy
         * `video_gestures_enabled` boolean when the enum key is absent: an
         * explicit legacy `false` (gestures disabled) migrates to
         * [GestureMode.NONE]; anything else — legacy `true` or a fresh install —
         * lands on [GestureMode.ALL]. The legacy key is never written again
         * (except factory reset), so the first mode change retires it.
         */
        PreferenceSpec.derived(
            keyName = "video_gesture_mode",
            default = GestureMode.ALL,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { prefs, raw ->
                raw.toEnumOrNull<GestureMode>() ?: run {
                    if (PreferenceCodec.readBool(
                            prefs,
                            VIDEO_GESTURES_ENABLED.typedKey(),
                            VIDEO_GESTURES_ENABLED.keyName,
                            default = true,
                        )
                    ) {
                        GestureMode.ALL
                    } else {
                        GestureMode.NONE
                    }
                }
            },
            encode = { it.name },
            search = PreferenceSearchSpec(
                id = "gestures",
                titleKey = "ss_gestures_title",
                subtitleKey = "ss_gestures_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("gestures", "swipe", "tap", "brightness", "volume", "seeking"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * The legacy `video_gestures_enabled` boolean surface. Not a
         * [VideoPlayerSlice] field — since the mode enum owns the projection, this
         * key is read only by the `video_gesture_mode` migration (and reset with
         * the rest of the playback keys); it is declared here so its wire name,
         * default and reset participation live with the rows instead of as magic
         * literals (the playback domain's `force_direct_play` precedent).
         */
        PreferenceSpec.plainBoolean(
            keyName = "video_gestures_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.enumRow(
            keyName = "video_gesture_indicator_side",
            default = GestureIndicatorSide.OPPOSITE,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "gesture_indicator_side",
                titleKey = "ss_gesture_indicator_side_title",
                subtitleKey = "ss_gesture_indicator_side_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("indicator", "brightness", "volume", "bar", "side", "gesture", "opposite"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * Holding the second press of a double-tap in a seek zone
         * repeats the step seek until release (with the accumulating "+Ns" chip)
         * instead of falling through to the long-press hold-speed. Default ON —
         * the off state restores the legacy hold-speed-everywhere behavior.
         * Android-only in the settings catalog, matching the double-tap-seek
         * knob it extends (`video_seek_duration_ms`).
         */
        PreferenceSpec.plainBoolean(
            keyName = "video_double_tap_hold_seek_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "double_tap_hold_seek",
                // One-key family: a single screen-face key.
                titleKey = "settings_double_tap_hold_seek",
                subtitleKey = "ss_double_tap_hold_seek_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("double tap", "hold", "seek", "continuous", "skip", "gesture", "press"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        /*
         * The whole player input mapping: the free-form
         * pattern → action rows over touch / wheel / keyboard / D-pad, persisted
         * as one whole-object JSON blob (the `segment_behaviors` precedent). When
         * the blob is absent the read derives the default map from the legacy
         * gesture config — `video_gesture_mode` plus the hold-speed and
         * double-tap-hold rows — so a pre-mapping install keeps its exact prior
         * behavior (the legacy-boolean dance's second generation). A corrupt
         * blob falls back to the factory-default mapping.
         */
        PreferenceSpec.derived(
            keyName = "input_bindings",
            default = PlayerInputDefaults.defaultMap(),
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { prefs, raw ->
                // The summon guarantee rides the ONE decode point: the projection
                // the editor renders, the slice the player seeds from, and the
                // current the RMW verb transforms all pass through readFrom, so a
                // locked-out stored map is repaired everywhere at once and the
                // next mapping write persists the repair.
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedInputBindings,
                    default = PlayerInputDefaults.defaultMap(),
                    parse = ::decodeInputBindings,
                    onNull = { legacyInputBindings(prefs) },
                    cacheRef = { cachedInputBindings = it },
                    nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
                ).ensureControlsSummonable()
            },
            encode = { map ->
                PreferenceCodec.encodeDefaultsJson.encodeToString(
                    serializer<PlayerInputMap>(),
                    map,
                )
            },
            search = PreferenceSearchSpec(
                id = "input_bindings",
                // One-key family: a single screen-face key.
                titleKey = "settings_input_bindings",
                // One-key family: a single screen-face key.
                subtitleKey = "settings_input_bindings_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("controls", "buttons", "gesture", "bindings", "remap", "customize", "input", "keyboard", "swipe", "brightness", "volume"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
    )

    /** Speed / brightness / volume memory. */
    private val speedMemory: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "video_pass_out_protection_hours",
            default = 0,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 0,
            search = PreferenceSearchSpec(
                id = "pass_out_protection",
                titleKey = "ss_pass_out_protection_title",
                subtitleKey = "ss_pass_out_protection_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pass out", "fall asleep", "auto pause", "sleep", "hours"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainLong(
            keyName = "video_skip_back_on_resume_ms",
            default = 0L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 0L,
            search = PreferenceSearchSpec(
                id = "skip_back_on_resume",
                titleKey = "ss_skip_back_on_resume_title",
                subtitleKey = "ss_skip_back_on_resume_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("skip", "back", "resume", "rewind", "unpause", "seek"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_hold_speed_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "hold_speed_multiplier",
                titleKey = "ss_hold_speed_multiplier_title",
                subtitleKey = "ss_hold_speed_multiplier_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("hold", "seek", "speed", "multiplier", "fast", "fast forward", "rewind", "long press", "off", "disable"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainFloat(
            keyName = "video_hold_speed_multiplier",
            default = 2.0f,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainFloat(
            keyName = "video_default_speed",
            default = 1.0f,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "default_speed",
                titleKey = "ss_default_speed_title",
                subtitleKey = "ss_default_speed_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("speed", "rate", "fast", "slow", "playback speed"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_autoplay_next",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "video_autoplay_next",
                titleKey = "ss_video_autoplay_next_title",
                subtitleKey = "ss_video_autoplay_next_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("autoplay", "next", "continuous", "episode", "sequence"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * Cap on how many episodes autoplay back-to-back before the sequence
         * stops (0 = unlimited). Sits beside the autoplay toggle it qualifies.
         */
        PreferenceSpec.plainInt(
            keyName = "autoplay_episode_limit",
            default = 0,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 0,
            search = PreferenceSearchSpec(
                id = "autoplay_episode_limit",
                titleKey = "ss_autoplay_episode_limit_title",
                subtitleKey = "ss_autoplay_episode_limit_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("autoplay", "limit", "episodes", "count", "binge", "maximum", "cap"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        /*
         * Show the next-episode details card after an episode finishes
         * (metadata + artwork preview before the autoplay fires). Default on.
         */
        PreferenceSpec.plainBoolean(
            keyName = "show_next_episode_details_after_finish",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "next_episode_details",
                titleKey = "ss_next_episode_details_title",
                subtitleKey = "ss_next_episode_details_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("next episode", "details", "preview", "finish", "autoplay", "card", "up next"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
    )

    /** "Still watching?" + autoplay + cinema + HUD (clock, brightness, mute, segments HUD). */
    private val stillWatchingHud: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "still_watching_mode",
            default = StillWatchingMode.OFF,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "still_watching_mode",
                // One-key family: a single screen-face key.
                titleKey = "settings_still_watching_mode",
                subtitleKey = "ss_still_watching_mode_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("still watching", "confirm", "binge", "unattended", "idle", "pass out", "autoplay"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "still_watching_episode_threshold",
            default = 0,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 0,
            search = PreferenceSearchSpec(
                id = "still_watching_episodes",
                // One-key family: a single screen-face key.
                titleKey = "settings_still_watching_episodes",
                subtitleKey = "ss_still_watching_episodes_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("still watching", "episodes", "threshold", "count", "binge", "consecutive"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "trailer_autoplay",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "autoplay_trailers",
                titleKey = "ss_autoplay_trailers_title",
                subtitleKey = "ss_autoplay_trailers_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("trailer", "autoplay", "preview", "details"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "cinema_mode_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "cinema_mode",
                titleKey = "ss_cinema_mode_title",
                subtitleKey = "ss_cinema_mode_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("cinema", "intro", "preroll", "pre-roll", "trailer"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "play_trailer_before_movie",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "play_trailer_before_movie",
                titleKey = "ss_play_trailer_before_movie_title",
                subtitleKey = "ss_play_trailer_before_movie_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("trailer", "movie", "preroll", "pre-roll", "cinema", "before", "feature"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainLong(
            keyName = "video_swipe_seek_max_ms",
            default = 120_000L,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "swipe_seek_range",
                titleKey = "ss_swipe_seek_range_title",
                subtitleKey = "ss_swipe_seek_range_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("seek range", "swipe limit", "skip max"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_remember_brightness",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "remember_brightness",
                titleKey = "ss_remember_brightness_title",
                subtitleKey = "ss_remember_brightness_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("brightness", "remember", "save", "light"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainFloat(
            keyName = "video_brightness_level",
            default = 0.5f,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "default_brightness_level",
                titleKey = "ss_default_brightness_level_title",
                subtitleKey = "ss_default_brightness_level_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("brightness", "default", "screen", "light", "level"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_remember_muted",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_muted",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_auto_skip_intro",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_auto_skip_outro",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
    )

    /**
     * Trickplay / episode browser / metadata / preload / cache, plus the HUD
     * clock/time-remaining pair, the TV zoom and incognito mode — the rows
     * between the autoplay block and the media segments in slice order.
     */
    private val trickplayBrowser: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "trickplay_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "trickplay_preview",
                titleKey = "ss_trickplay_preview_title",
                subtitleKey = "ss_trickplay_preview_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("trickplay", "thumbnails", "scrubbing", "preview", "seek preview"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "trickplay_on_seek_gesture",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "trickplay_on_gestures",
                titleKey = "ss_trickplay_on_gestures_title",
                subtitleKey = "ss_trickplay_on_gestures_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("trickplay", "thumbnails", "gesture", "swipe", "seek"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The trickplay preview's presentation: bubble over the seek bar, a
         * docked filmstrip, or a fullscreen takeover while scrubbing. Sits
         * beside the trickplay toggles it qualifies.
         */
        PreferenceSpec.enumRow(
            keyName = "trickplay_mode",
            default = TrickplayMode.BUBBLE,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "trickplay_mode",
                titleKey = "ss_trickplay_mode_title",
                subtitleKey = "ss_trickplay_mode_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("trickplay", "preview", "bubble", "filmstrip", "fullscreen", "scrub", "thumbnails"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_episode_browser_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "episode_browser",
                titleKey = "ss_episode_browser_title",
                subtitleKey = "ss_episode_browser_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("episodes", "browser", "list", "in-player"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "video_show_playback_metadata",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "playback_metadata",
                titleKey = "ss_playback_metadata_title",
                subtitleKey = "ss_playback_metadata_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("metadata", "codec", "bitrate", "stream stats", "debug"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.enumRow(
            keyName = "video_preload_buffer_size",
            default = PreloadBufferSize.MEDIUM,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "preload_buffer",
                titleKey = "ss_preload_buffer_title",
                subtitleKey = "ss_preload_buffer_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("buffer", "preload", "cache", "size", "network cache"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The direct-play video byte-cache cap (VideoStreamCache's LRU bound).
         * Added in a later change — never string-typed in the legacy store — so
         * the shared [PreferenceCodec.readInt] legacy-string fallback this row
         * derives is inert (no string slot can exist for this name); the typed
         * read and default behave exactly like the former plain `prefs[key] ?:
         * default` line.
         */
        PreferenceSpec.plainInt(
            keyName = "video_cache_size_mb",
            default = 1024,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                // The search hit restates the row's screen title (the fold):
                // the catalog row declares no ss_*_title twin.
                titleKey = "settings_video_cache_size",
                id = "video_cache_size",
                // One-key family: a single screen-face key.
                subtitleKey = "settings_video_cache_size_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("cache", "video cache", "size", "storage", "stream cache"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_clock_in_player",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "show_clock_player",
                titleKey = "ss_show_clock_player_title",
                subtitleKey = "ss_show_clock_player_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("clock", "time", "player", "wall", "current"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_time_remaining",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "show_time_remaining",
                titleKey = "ss_show_time_remaining_title",
                subtitleKey = "ss_show_time_remaining_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("time", "remaining", "elapsed", "duration", "countdown"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Ambient blur: the paused frame blurs and expands behind the pause
         * surface (the render-adjacent video effect beside the TV zoom below).
         * Default off.
         */
        PreferenceSpec.plainBoolean(
            keyName = "ambient_blur_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "ambient_blur",
                titleKey = "ss_ambient_blur_title",
                subtitleKey = "ss_ambient_blur_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("ambient", "blur", "backdrop", "pause", "glow", "background"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainFloat(
            keyName = "tv_zoom_mode_percent",
            default = 0f,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "tv_zoom_mode",
                titleKey = "ss_tv_zoom_mode_title",
                subtitleKey = "ss_tv_zoom_mode_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("tv", "zoom", "crop", "fill", "screen"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
                platformRule = PreferencePlatformRule.ANDROID_ONLY,
            ),
        ),
        /*
         * How bitmap subtitle (PGS/VobSub) planes scale onto the frame when
         * their native resolution disagrees with the render target — the
         * render-key twin of the TV zoom above.
         */
        PreferenceSpec.enumRow(
            keyName = "bitmap_subtitle_scale_mode",
            default = BitmapSubtitleScaleMode.FIT,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "bitmap_subtitle_scale",
                titleKey = "ss_bitmap_subtitle_scale_title",
                subtitleKey = "ss_bitmap_subtitle_scale_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("bitmap", "subtitle", "pgs", "vobsub", "scale", "stretch", "cover", "fit"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "incognito_mode_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "incognito_mode",
                titleKey = "ss_incognito_mode_title",
                subtitleKey = "ss_incognito_mode_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("incognito", "private", "history", "stealth"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * The pause-screensaver cluster: what replaces the video after a long
         * pause, how long until it arms, and whether the pause surface shows
         * item details. The three rows travel together.
         */
        PreferenceSpec.enumRow(
            keyName = "pause_screensaver_mode",
            default = PauseScreensaverMode.OFF,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "pause_screensaver",
                titleKey = "ss_pause_screensaver_title",
                subtitleKey = "ss_pause_screensaver_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pause", "screensaver", "clock", "logo", "burn-in", "dim", "black"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainInt(
            keyName = "pause_screensaver_after_minutes",
            default = 5,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 1,
            max = 60,
            search = PreferenceSearchSpec(
                id = "pause_screensaver_delay",
                titleKey = "ss_pause_screensaver_delay_title",
                subtitleKey = "ss_pause_screensaver_delay_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pause", "screensaver", "delay", "minutes", "timer", "idle", "timeout"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "pause_details_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "pause_details",
                titleKey = "ss_pause_details_title",
                subtitleKey = "ss_pause_details_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("pause", "details", "metadata", "info", "overlay", "episode"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /**
     * Media segments: the two JSON blobs and the legacy string-keyed skip
     * booleans their reads fall back from.
     */
    private val mediaSegments: List<PreferenceSpec<*>> = listOf(
        /*
         * Reads [VideoPlayerSlice.segmentBehaviors] — the headline migration
         * from the legacy booleans. When the JSON
         * `segment_behaviors` blob is present it is decoded and merged over
         * [SegmentBehavior.DEFAULT_BEHAVIORS] (stored values win). When the blob
         * is absent this falls back from the four legacy booleans
         * (`skip_intro_enabled` / `skip_outro_enabled` / `auto_skip_intro` /
         * `auto_skip_outro`) into INTRO/OUTRO SegmentBehaviors, merged over the
         * defaults — so an install with only the legacy booleans keeps its prior
         * intro/outro behaviour.
         * When neither is present the defaults are returned unchanged.
         *
         * The derived write re-encodes the enum-keyed map as the JSON object
         * the setter/restore wrote (enum names as keys and values — identical
         * bytes either way, since enum serialization is
         * name-based regardless of the `encodeDefaults` setting).
         */
        PreferenceSpec.derived(
            keyName = "segment_behaviors",
            default = SegmentBehavior.DEFAULT_BEHAVIORS,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { prefs, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedSegmentBehaviors,
                    default = SegmentBehavior.DEFAULT_BEHAVIORS,
                    parse = { readSegmentBehaviors(prefs) },
                    onNull = { readSegmentBehaviors(prefs) },
                    cacheRef = { cachedSegmentBehaviors = it },
                    nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
                )
            },
            encode = { behaviors ->
                PreferenceCodec.encodeDefaultsJson.encodeToString(
                    serializer<Map<MediaSegmentType, SegmentBehavior>>(),
                    behaviors,
                )
            },
        ),
        /*
         * Per-segment-type auto-skip delay, in milliseconds, added on top of a
         * segment's start before an AUTO_SKIP behavior fires — the "let the intro
         * play for a couple of seconds, then skip" preference. Only consulted for
         * types whose `segment_behaviors` entry is AUTO_SKIP; all types default to
         * 0 (skip at segment start). Blob shape mirrors `segment_behaviors`: a
         * JSON object keyed by enum name; absent types and unparsable blobs fall
         * back to the all-zero default (no legacy keys — this knob is newer than
         * the blob migration).
         */
        PreferenceSpec.derived(
            keyName = "segment_skip_delays_ms",
            default = MediaSegmentType.DEFAULT_SKIP_DELAYS_MS,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw ->
                if (raw == null) {
                    MediaSegmentType.DEFAULT_SKIP_DELAYS_MS
                } else {
                    try {
                        val stored = PreferenceCodec.json.decodeFromString<Map<String, Long>>(raw)
                        val parsed = stored.mapNotNull { (typeStr, delayMs) ->
                            val type = typeStr.toEnumOrNull<MediaSegmentType>() ?: return@mapNotNull null
                            type to delayMs.coerceIn(0L, MAX_SEGMENT_SKIP_DELAY_MS)
                        }.toMap()
                        MediaSegmentType.DEFAULT_SKIP_DELAYS_MS + parsed
                    } catch (_: Exception) {
                        MediaSegmentType.DEFAULT_SKIP_DELAYS_MS
                    }
                }
            },
            encode = { delays ->
                PreferenceCodec.encodeDefaultsJson.encodeToString(
                    serializer<Map<MediaSegmentType, Long>>(),
                    delays,
                )
            },
        ),
        /*
         * The four legacy string-keyed skip booleans the `segment_behaviors` read
         * falls back from. Not [VideoPlayerSlice] fields — declared so their wire
         * names (string slots; the god-object era stored them as strings) and
         * their reset participation live with the rows. Their reads route through
         * the `segment_behaviors` row's derived lambda, which reads the raw
         * string slots and applies the per-slot defaults.
         */
        PreferenceSpec.string(
            keyName = "skip_intro_enabled",
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.string(
            keyName = "skip_outro_enabled",
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.string(
            keyName = "auto_skip_intro",
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.string(
            keyName = "auto_skip_outro",
            resetCategory = PreferenceResetCategory.PLAYBACK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "skip_segments_on_seek",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                // The search hit restates the row's screen title (the fold):
                // the catalog row declares no ss_*_title twin.
                titleKey = "settings_skip_segments_on_seek",
                id = "skip_segments_on_seek",
                subtitleKey = "ss_skip_segments_on_seek_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("segment", "skip", "seek", "forward", "commercial", "auto"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * How early (seconds) the "Next up" button appears before the current
         * item ends — the segment-adjacent end-of-item cadence knob. Default
         * 30s, bounded to 5–120s.
         */
        PreferenceSpec.plainInt(
            keyName = "next_up_button_preroll_sec",
            default = 30,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            min = 5,
            max = 120,
            search = PreferenceSearchSpec(
                id = "next_up_preroll",
                titleKey = "ss_next_up_preroll_title",
                subtitleKey = "ss_next_up_preroll_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("next up", "button", "preroll", "seconds", "early", "before end", "credits"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /**
     * mpv-engine power-user knobs (advanced-gated): the tone-mapping curve
     * family, the deband strength, motion interpolation, and the two raw
     * escape hatches (input.conf text, custom on-screen buttons). The rows
     * carry no mpv typing here — the engine layer maps the stored values onto
     * mpv options via the core:model enums ([MpvToneMappingCurve.mpvValue] /
     * [MpvDebandMode.mpvValue]).
     */
    private val mpvRender: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "mpv_tone_mapping_curve",
            default = MpvToneMappingCurve.AUTO,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "mpv_tone_mapping_curve",
                titleKey = "ss_mpv_tone_mapping_curve_title",
                subtitleKey = "ss_mpv_tone_mapping_curve_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("mpv", "tone mapping", "hdr", "sdr", "curve", "bt.2390", "hable"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.enumRow(
            keyName = "mpv_deband_mode",
            default = MpvDebandMode.OFF,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "mpv_deband",
                titleKey = "ss_mpv_deband_title",
                subtitleKey = "ss_mpv_deband_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("mpv", "deband", "banding", "gradient", "strength", "dither"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "mpv_motion_interpolation",
            default = false,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "mpv_motion_interpolation",
                titleKey = "ss_mpv_motion_interpolation_title",
                subtitleKey = "ss_mpv_motion_interpolation_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("mpv", "motion", "interpolation", "smooth", "fps", "judder", "soap"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Raw `input.conf`-style key bindings text (the video_default_aspect_ratio
         * raw-string precedent: a derived row with the identity encode, so the
         * derived write/restore machinery stays total). An empty string (the
         * default) applies nothing.
         */
        PreferenceSpec.derived(
            keyName = "mpv_input_conf",
            default = "",
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw -> raw ?: "" },
            encode = { it },
            search = PreferenceSearchSpec(
                id = "mpv_input_conf",
                titleKey = "ss_mpv_input_conf_title",
                subtitleKey = "ss_mpv_input_conf_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("mpv", "input", "key bindings", "keyboard", "remote", "config"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
        /*
         * Custom on-screen button definitions (JSON-ish raw text the player
         * renders as extra transport buttons). Empty = none.
         */
        PreferenceSpec.derived(
            keyName = "mpv_custom_buttons",
            default = "",
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw -> raw ?: "" },
            encode = { it },
            search = PreferenceSearchSpec(
                id = "mpv_custom_buttons",
                titleKey = "ss_mpv_custom_buttons_title",
                subtitleKey = "ss_mpv_custom_buttons_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("mpv", "custom", "buttons", "osd", "toolbar", "actions"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                isAdvanced = true,
            ),
        ),
    )

    /**
     * The absent-blob fallback: the factory mapping gated by the user's
     * legacy gesture config (tier mode + the two per-behavior switches), so
     * the first read after an upgrade reproduces the pre-mapping behavior
     * exactly. The blob is written on the first mapping edit (or mode-preset
     * flip), after which the legacy keys are observation-only.
     */
    private fun legacyInputBindings(prefs: Preferences): PlayerInputMap =
        PlayerInputDefaults.defaultMap(
            gestureMode = VIDEO_GESTURE_MODE.readFrom(prefs),
            holdSpeedEnabled = VIDEO_HOLD_SPEED_ENABLED.readFrom(prefs),
            doubleTapHoldSeekEnabled = VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.readFrom(prefs),
        )

    private fun decodeInputBindings(raw: String): PlayerInputMap =
        PreferenceCodec.json.decodeFromString<PlayerInputMap>(raw)

    /**
     * The `segment_behaviors` row's blob decode + legacy-boolean fallback: the
     * row's `parse`/`onNull` closures route through it (the null-raw leg IS the
     * legacy fallback; the decode leg owns its own corrupt-blob tolerance).
     */
    private fun readSegmentBehaviors(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> {
        // The raw STRING slot (a derived row's typedKey() is typed as the row
        // value, not the wire slot — read the wire name directly here).
        val raw = prefs[stringPreferencesKey(SEGMENT_BEHAVIORS.keyName)]
        if (raw != null) {
            return try {
                val stored = PreferenceCodec.json.decodeFromString<Map<String, String>>(raw)
                val parsed = stored.mapNotNull { (typeStr, behaviorStr) ->
                    val type = typeStr.toEnumOrNull<MediaSegmentType>() ?: return@mapNotNull null
                    val behavior = behaviorStr.toEnumOrNull<SegmentBehavior>() ?: return@mapNotNull null
                    type to behavior
                }.toMap()
                // Merge: defaults fill in any types not explicitly saved, stored values override
                SegmentBehavior.DEFAULT_BEHAVIORS + parsed
            } catch (_: Exception) {
                SegmentBehavior.DEFAULT_BEHAVIORS
            }
        }

        val hasLegacyKeys = prefs.contains(SKIP_INTRO_ENABLED.typedKey()) ||
            prefs.contains(SKIP_OUTRO_ENABLED.typedKey()) ||
            prefs.contains(AUTO_SKIP_INTRO.typedKey()) ||
            prefs.contains(AUTO_SKIP_OUTRO.typedKey())
        if (!hasLegacyKeys) return SegmentBehavior.DEFAULT_BEHAVIORS

        val migrated = mutableMapOf<MediaSegmentType, SegmentBehavior>()
        val skipIntro = prefs[SKIP_INTRO_ENABLED.typedKey()]?.toBoolean() ?: true
        val skipOutro = prefs[SKIP_OUTRO_ENABLED.typedKey()]?.toBoolean() ?: true
        val autoIntro = prefs[AUTO_SKIP_INTRO.typedKey()]?.toBoolean() ?: false
        val autoOutro = prefs[AUTO_SKIP_OUTRO.typedKey()]?.toBoolean() ?: false
        migrated[MediaSegmentType.INTRO] = when {
            autoIntro -> SegmentBehavior.AUTO_SKIP
            skipIntro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        migrated[MediaSegmentType.OUTRO] = when {
            autoOutro -> SegmentBehavior.AUTO_SKIP
            skipOutro -> SegmentBehavior.SHOW_BUTTON
            else -> SegmentBehavior.IGNORE
        }
        return SegmentBehavior.DEFAULT_BEHAVIORS + migrated
    }

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [VideoPlayerSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        transportTiming +
            orientationLayout +
            gestures +
            speedMemory +
            stillWatchingHud +
            trickplayBrowser +
            mediaSegments +
            mpvRender

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every in-player key
     * descends under `PreferenceResetCategory.PLAYBACK`, matching the
     * facade's `resetCategoryKeys`. The facade aggregates these lists instead
     * of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    // ------------------------------------------------------------------
    // Settings-search standalone entries: the per-segment-type catalog rows.
    // Six catalog facts over the ONE `segment_behaviors` knob (the
    // experimental feature-set precedent: the knob is a set, the catalog
    // rows are its members), declared here in the media-segment group's
    // catalog order. These are the domain's ONLY hand-only search entries —
    // every other search entry is declared on its row and reaches
    // [searchEntries] through the derivation below.
    // ------------------------------------------------------------------

    private val searchMediaSegmentIntro = PreferenceSearchSpec(
        // The search hit reuses the enum's core_segment_* faces (the fold):
        // the catalog row declares no ss_*_title twin.
        titleKey = "core_segment_intro",
        id = "media_segment_intro",
        subtitleKey = "core_segment_intro_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "intro", "skip", "opening", "credits", "marker"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaSegmentOutro = PreferenceSearchSpec(
        titleKey = "core_segment_outro",
        id = "media_segment_outro",
        subtitleKey = "core_segment_outro_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "outro", "ending", "skip", "credits", "marker"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaSegmentPreview = PreferenceSearchSpec(
        titleKey = "core_segment_preview",
        id = "media_segment_preview",
        subtitleKey = "core_segment_preview_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "preview", "next episode", "recap", "skip", "marker"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaSegmentRecap = PreferenceSearchSpec(
        titleKey = "core_segment_recap",
        id = "media_segment_recap",
        subtitleKey = "core_segment_recap_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "recap", "previously on", "skip", "marker"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaSegmentCommercial = PreferenceSearchSpec(
        titleKey = "core_segment_commercial",
        id = "media_segment_commercial",
        subtitleKey = "core_segment_commercial_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "commercial", "ad", "advertisement", "skip", "marker"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    private val searchMediaSegmentUnknown = PreferenceSearchSpec(
        titleKey = "core_segment_unknown",
        id = "media_segment_unknown",
        subtitleKey = "core_segment_unknown_desc",
        categoryKey = "ss_cat_playback",
        keywords = listOf("segment", "unknown", "skip", "marker", "unidentified"),
        routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        isAdvanced = true,
    )

    /** The six standalone per-segment-type catalog entries, in catalog order. */
    private val segmentStandaloneSearchEntries: List<PreferenceSearchSpec> = listOf(
        searchMediaSegmentIntro,
        searchMediaSegmentOutro,
        searchMediaSegmentPreview,
        searchMediaSegmentRecap,
        searchMediaSegmentCommercial,
        searchMediaSegmentUnknown,
    )

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order), plus the six
     * hand-only standalone per-segment-type entries appended. Nothing
     * re-lists the searchable rows — a row that declares a search entry is in
     * the catalog by construction, and one without one is out by construction
     * (the former hand list could forget either). The settings feature derives
     * its spec-backed catalog rows from this list (and
     * [PlaybackPreferenceSpecs.searchEntries]) over its ordered record spine,
     * binding the declared resource keys to real resources — it matches
     * entries to rows BY ID, so this list's order is declaration order, not
     * catalog order: the row lists on the feature side own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search } + segmentStandaloneSearchEntries

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val VIDEO_SEEK_DURATION_MS: PreferenceSpec<Long> get() = row("video_seek_duration_ms")

    val VIDEO_CONTROLS_TIMEOUT_MS: PreferenceSpec<Long> get() = row("video_controls_timeout_ms")

    val SKIP_PROMPT_DURATION_MS: PreferenceSpec<Long> get() = row("skip_prompt_duration_ms")

    val VIDEO_HIDE_OSD_ON_PAUSE: PreferenceSpec<Boolean> get() = row("video_hide_osd_on_pause")

    val VIDEO_RESUME_ON_HEADSET_PLUG: PreferenceSpec<Boolean> get() = row("video_resume_on_headset_plug")

    val VIDEO_DEFAULT_ORIENTATION: PreferenceSpec<OrientationMode> get() = row("video_default_orientation")

    val VIDEO_DEFAULT_ASPECT_RATIO: PreferenceSpec<String> get() = row("video_default_aspect_ratio")

    val VIDEO_GESTURE_MODE: PreferenceSpec<GestureMode> get() = row("video_gesture_mode")

    val VIDEO_GESTURES_ENABLED: PreferenceSpec<Boolean> get() = row("video_gestures_enabled")

    val VIDEO_GESTURE_INDICATOR_SIDE: PreferenceSpec<GestureIndicatorSide> get() = row("video_gesture_indicator_side")

    val VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED: PreferenceSpec<Boolean> get() = row("video_double_tap_hold_seek_enabled")

    val VIDEO_INPUT_BINDINGS: PreferenceSpec<PlayerInputMap> get() = row("input_bindings")

    val VIDEO_PASS_OUT_PROTECTION_HOURS: PreferenceSpec<Int> get() = row("video_pass_out_protection_hours")

    val VIDEO_SKIP_BACK_ON_RESUME_MS: PreferenceSpec<Long> get() = row("video_skip_back_on_resume_ms")

    val VIDEO_HOLD_SPEED_ENABLED: PreferenceSpec<Boolean> get() = row("video_hold_speed_enabled")

    val VIDEO_HOLD_SPEED_MULTIPLIER: PreferenceSpec<Float> get() = row("video_hold_speed_multiplier")

    val VIDEO_DEFAULT_SPEED: PreferenceSpec<Float> get() = row("video_default_speed")

    val VIDEO_AUTOPLAY_NEXT: PreferenceSpec<Boolean> get() = row("video_autoplay_next")

    val STILL_WATCHING_MODE: PreferenceSpec<StillWatchingMode> get() = row("still_watching_mode")

    val STILL_WATCHING_EPISODE_THRESHOLD: PreferenceSpec<Int> get() = row("still_watching_episode_threshold")

    val TRAILER_AUTOPLAY: PreferenceSpec<Boolean> get() = row("trailer_autoplay")

    val CINEMA_MODE_ENABLED: PreferenceSpec<Boolean> get() = row("cinema_mode_enabled")

    val PLAY_TRAILER_BEFORE_MOVIE: PreferenceSpec<Boolean> get() = row("play_trailer_before_movie")

    val VIDEO_SWIPE_SEEK_MAX_MS: PreferenceSpec<Long> get() = row("video_swipe_seek_max_ms")

    val VIDEO_REMEMBER_BRIGHTNESS: PreferenceSpec<Boolean> get() = row("video_remember_brightness")

    val VIDEO_BRIGHTNESS_LEVEL: PreferenceSpec<Float> get() = row("video_brightness_level")

    val VIDEO_REMEMBER_MUTED: PreferenceSpec<Boolean> get() = row("video_remember_muted")

    val VIDEO_MUTED: PreferenceSpec<Boolean> get() = row("video_muted")

    val VIDEO_AUTO_SKIP_INTRO: PreferenceSpec<Boolean> get() = row("video_auto_skip_intro")

    val VIDEO_AUTO_SKIP_OUTRO: PreferenceSpec<Boolean> get() = row("video_auto_skip_outro")

    val INCOGNITO_MODE_ENABLED: PreferenceSpec<Boolean> get() = row("incognito_mode_enabled")

    val SHOW_CLOCK_IN_PLAYER: PreferenceSpec<Boolean> get() = row("show_clock_in_player")

    val SHOW_TIME_REMAINING: PreferenceSpec<Boolean> get() = row("show_time_remaining")

    val AMBIENT_BLUR_ENABLED: PreferenceSpec<Boolean> get() = row("ambient_blur_enabled")

    val BITMAP_SUBTITLE_SCALE_MODE: PreferenceSpec<BitmapSubtitleScaleMode> get() = row("bitmap_subtitle_scale_mode")

    val PAUSE_SCREENSAVER_MODE: PreferenceSpec<PauseScreensaverMode> get() = row("pause_screensaver_mode")

    val PAUSE_SCREENSAVER_AFTER_MINUTES: PreferenceSpec<Int> get() = row("pause_screensaver_after_minutes")

    val PAUSE_DETAILS_ENABLED: PreferenceSpec<Boolean> get() = row("pause_details_enabled")

    val NEXT_UP_BUTTON_PREROLL_SEC: PreferenceSpec<Int> get() = row("next_up_button_preroll_sec")

    val MPV_TONE_MAPPING_CURVE: PreferenceSpec<MpvToneMappingCurve> get() = row("mpv_tone_mapping_curve")

    val MPV_DEBAND_MODE: PreferenceSpec<MpvDebandMode> get() = row("mpv_deband_mode")

    val MPV_MOTION_INTERPOLATION: PreferenceSpec<Boolean> get() = row("mpv_motion_interpolation")

    val MPV_INPUT_CONF: PreferenceSpec<String> get() = row("mpv_input_conf")

    val MPV_CUSTOM_BUTTONS: PreferenceSpec<String> get() = row("mpv_custom_buttons")

    val AUTOPLAY_EPISODE_LIMIT: PreferenceSpec<Int> get() = row("autoplay_episode_limit")

    val SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH: PreferenceSpec<Boolean> get() =
        row("show_next_episode_details_after_finish")

    val TRICKPLAY_ENABLED: PreferenceSpec<Boolean> get() = row("trickplay_enabled")

    val TRICKPLAY_ON_SEEK_GESTURE: PreferenceSpec<Boolean> get() = row("trickplay_on_seek_gesture")

    val TRICKPLAY_MODE: PreferenceSpec<TrickplayMode> get() = row("trickplay_mode")

    val VIDEO_EPISODE_BROWSER_ENABLED: PreferenceSpec<Boolean> get() = row("video_episode_browser_enabled")

    val VIDEO_SHOW_PLAYBACK_METADATA: PreferenceSpec<Boolean> get() = row("video_show_playback_metadata")

    val VIDEO_PRELOAD_BUFFER_SIZE: PreferenceSpec<PreloadBufferSize> get() = row("video_preload_buffer_size")

    val VIDEO_CACHE_SIZE_MB: PreferenceSpec<Int> get() = row("video_cache_size_mb")

    val TV_ZOOM_MODE_PERCENT: PreferenceSpec<Float> get() = row("tv_zoom_mode_percent")

    val SEGMENT_BEHAVIORS: PreferenceSpec<Map<MediaSegmentType, SegmentBehavior>> get() = row("segment_behaviors")

    val SEGMENT_SKIP_DELAYS_MS: PreferenceSpec<Map<MediaSegmentType, Long>> get() = row("segment_skip_delays_ms")

    val SKIP_INTRO_ENABLED: PreferenceSpec<String?> get() = row("skip_intro_enabled")

    val SKIP_OUTRO_ENABLED: PreferenceSpec<String?> get() = row("skip_outro_enabled")

    val AUTO_SKIP_INTRO: PreferenceSpec<String?> get() = row("auto_skip_intro")

    val AUTO_SKIP_OUTRO: PreferenceSpec<String?> get() = row("auto_skip_outro")

    val SKIP_SEGMENTS_ON_SEEK: PreferenceSpec<Boolean> get() = row("skip_segments_on_seek")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the JVM derivation test
     * pins every accessor against the store's [VideoPlayerStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "VideoPlayerPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
