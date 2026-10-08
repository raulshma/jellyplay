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
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StillWatchingMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer

/**
 * The in-player video domain's single preference declaration (Stage B of the
 * spec machinery, the [PlaybackPreferenceSpecs] migration precedent): one
 * [PreferenceSpec] row per key `VideoPlayerStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [plainInt] / [plainLong] /
 * [plainFloat] / [enumRow] / [derived]).
 *
 * `VideoPlayerStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and `restore`
 * delegate to the rows' derived writes, and `resetKeysFor` filters the rows by
 * reset category — so a row cannot drift from the machinery that reads or
 * writes it. The reads that carry migration or legacy-fallback logic
 * ([VIDEO_GESTURE_MODE]'s legacy boolean dance,
 * [SEGMENT_BEHAVIORS]'s four-boolean fallback, the raw-string aspect ratio)
 * carry it as [PreferenceSpec.derived] lambdas, documented in place below.
 *
 * Not derivable by design (the accepted residuals, the [PlaybackStore]
 * precedent):
 *  - the bounds-coercion setters (`setVideoPassOutProtectionHours`,
 *    `setVideoSkipBackOnResumeMs`, `setStillWatchingEpisodeThreshold`) and the
 *    read-modify-write `setSegmentBehavior` stay hand-written in the store;
 *  - the derived [restore] keeps ONE hand-written line beside the
 *    [VIDEO_GESTURE_MODE] row — clearing the superseded legacy
 *    `video_gestures_enabled` boolean so a restored snapshot cannot disagree
 *    with the row's read fallback.
 *
 * Adding a preference: one row here, one [VideoPlayerSlice] property, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings and reset lists,
 * not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object VideoPlayerPreferenceSpecs {

    // ------------------------------------------------------------------
    // Transport timing
    // ------------------------------------------------------------------

    val VIDEO_SEEK_DURATION_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
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
    )

    val VIDEO_CONTROLS_TIMEOUT_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
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
    )

    /**
     * (jellyfin-androidtv #3924): pausing should not summon the control
     * overlay. Default **off** — today's show-on-pause behavior. On, the
     * pause arm of a play/pause transport toggle leaves the overlay hidden
     * (the play arm keeps summoning, and an already-visible overlay is never
     * force-hidden — the auto-hide timeout still owns dismissal).
     */
    val VIDEO_HIDE_OSD_ON_PAUSE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_hide_osd_on_pause",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "hide_osd_on_pause",
            titleKey = "ss_hide_osd_on_pause_title",
            subtitleKey = "ss_hide_osd_on_pause_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("pause", "osd", "overlay", "controls", "hide", "show"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    /**
     * Opt-in resume when headphones reconnect. Default **off**.
     * The decision machinery (paused-by-becoming-noisy marker + freshness
     * window) lives in core:data's `HeadsetResumePolicy` — the pref gates it.
     */
    val VIDEO_RESUME_ON_HEADSET_PLUG: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_resume_on_headset_plug",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "resume_on_headset_plug",
            titleKey = "ss_resume_headset_plug_title",
            subtitleKey = "ss_resume_headset_plug_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("headset", "headphones", "resume", "plug", "reconnect", "unplug", "audio"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Orientation / aspect / layout
    // ------------------------------------------------------------------

    val VIDEO_DEFAULT_ORIENTATION: PreferenceSpec<OrientationMode> = PreferenceSpec.enumRow(
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
    )

    /**
     * The raw aspect-ratio string ("AUTO"/"FIT"/"16:9"/…): not an enum — the
     * picker offers literal ratio strings — so the row is a derived string
     * slot serving [VideoPlayerSlice.videoDefaultAspectRatio]'s default on
     * absence, with the identity encode as its derived write.
     */
    val VIDEO_DEFAULT_ASPECT_RATIO: PreferenceSpec<String> = PreferenceSpec.derived(
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
    )

    // ------------------------------------------------------------------
    // Gestures (with the legacy enabled-boolean pairing)
    // ------------------------------------------------------------------

    /**
     * Reads [VideoPlayerSlice.videoGestureMode], falling back to the legacy
     * `video_gestures_enabled` boolean when the enum key is absent: an
     * explicit legacy `false` (gestures disabled) migrates to
     * [GestureMode.NONE]; anything else — legacy `true` or a fresh install —
     * lands on [GestureMode.ALL]. The legacy key is never written again
     * (except factory reset), so the first mode change retires it.
     */
    val VIDEO_GESTURE_MODE: PreferenceSpec<GestureMode> = PreferenceSpec.derived(
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
    )

    /**
     * The legacy `video_gestures_enabled` boolean surface. Not a
     * [VideoPlayerSlice] field — since the mode enum owns the projection, this
     * key is read only by the [VIDEO_GESTURE_MODE] migration (and reset with
     * the rest of the playback keys); it is declared here so its wire name,
     * default and reset participation live with the rows instead of as magic
     * literals (the [PlaybackPreferenceSpecs.FORCE_DIRECT_PLAY] precedent).
     */
    val VIDEO_GESTURES_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_gestures_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_GESTURE_INDICATOR_SIDE: PreferenceSpec<GestureIndicatorSide> = PreferenceSpec.enumRow(
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
    )

    /**
     * Holding the second press of a double-tap in a seek zone
     * repeats the step seek until release (with the accumulating "+Ns" chip)
     * instead of falling through to the long-press hold-speed. Default ON —
     * the off state restores the legacy hold-speed-everywhere behavior.
     * Android-only in the settings catalog, matching the double-tap-seek
     * knob it extends ([VIDEO_SEEK_DURATION_MS]).
     */
    val VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_double_tap_hold_seek_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "double_tap_hold_seek",
            titleKey = "ss_double_tap_hold_seek_title",
            subtitleKey = "ss_double_tap_hold_seek_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("double tap", "hold", "seek", "continuous", "skip", "gesture", "press"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            platformRule = PreferencePlatformRule.ANDROID_ONLY,
        ),
    )

    /**
     * The whole player input mapping (issue #171 generalized): the free-form
     * pattern → action rows over touch / wheel / keyboard / D-pad, persisted
     * as one whole-object JSON blob (the [SEGMENT_BEHAVIORS] precedent). When
     * the blob is absent the read derives the default map from the legacy
     * gesture config — [VIDEO_GESTURE_MODE] plus the hold-speed and
     * double-tap-hold rows — so a pre-mapping install keeps its exact prior
     * behavior (the [VIDEO_GESTURE_MODE] legacy-boolean dance's second
     * generation). A corrupt blob falls back to the factory-default mapping.
     */
    val VIDEO_INPUT_BINDINGS: PreferenceSpec<PlayerInputMap> = PreferenceSpec.derived(
        keyName = "input_bindings",
        default = PlayerInputDefaults.defaultMap(),
        resetCategory = PreferenceResetCategory.PLAYBACK,
        read = { prefs, raw ->
            PreferenceCodec.cachedJson(
                raw = raw,
                cache = cachedInputBindings,
                default = PlayerInputDefaults.defaultMap(),
                parse = ::decodeInputBindings,
                onNull = { legacyInputBindings(prefs) },
                cacheRef = { cachedInputBindings = it },
                nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
            )
        },
        encode = { map ->
            PreferenceCodec.encodeDefaultsJson.encodeToString(
                serializer<PlayerInputMap>(),
                map,
            )
        },
        search = PreferenceSearchSpec(
            id = "input_bindings",
            titleKey = "ss_input_bindings_title",
            subtitleKey = "ss_input_bindings_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("controls", "buttons", "gesture", "bindings", "remap", "customize", "input", "keyboard", "swipe", "brightness", "volume"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
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

    // ------------------------------------------------------------------
    // Speed / brightness / volume memory
    // ------------------------------------------------------------------

    val VIDEO_PASS_OUT_PROTECTION_HOURS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
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
    )

    val VIDEO_SKIP_BACK_ON_RESUME_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
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
    )

    val VIDEO_HOLD_SPEED_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_HOLD_SPEED_MULTIPLIER: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
        keyName = "video_hold_speed_multiplier",
        default = 2.0f,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_DEFAULT_SPEED: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
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
    )

    val VIDEO_AUTOPLAY_NEXT: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    // ------------------------------------------------------------------
    // "Still watching?" + autoplay + cinema + HUD
    // ------------------------------------------------------------------

    val STILL_WATCHING_MODE: PreferenceSpec<StillWatchingMode> = PreferenceSpec.enumRow(
        keyName = "still_watching_mode",
        default = StillWatchingMode.OFF,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            id = "still_watching_mode",
            titleKey = "ss_still_watching_mode_title",
            subtitleKey = "ss_still_watching_mode_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("still watching", "confirm", "binge", "unattended", "idle", "pass out", "autoplay"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        ),
    )

    val STILL_WATCHING_EPISODE_THRESHOLD: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "still_watching_episode_threshold",
        default = 0,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        min = 0,
        search = PreferenceSearchSpec(
            id = "still_watching_episodes",
            titleKey = "ss_still_watching_episodes_title",
            subtitleKey = "ss_still_watching_episodes_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("still watching", "episodes", "threshold", "count", "binge", "consecutive"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
        ),
    )

    val TRAILER_AUTOPLAY: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val CINEMA_MODE_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_SWIPE_SEEK_MAX_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
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
    )

    val VIDEO_REMEMBER_BRIGHTNESS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_BRIGHTNESS_LEVEL: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
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
    )

    val VIDEO_REMEMBER_MUTED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_remember_muted",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_MUTED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_muted",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_AUTO_SKIP_INTRO: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_auto_skip_intro",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_AUTO_SKIP_OUTRO: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_auto_skip_outro",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    // ------------------------------------------------------------------
    // Trickplay / episode browser / metadata / preload / cache
    // ------------------------------------------------------------------

    val TRICKPLAY_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val TRICKPLAY_ON_SEEK_GESTURE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_EPISODE_BROWSER_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_SHOW_PLAYBACK_METADATA: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val VIDEO_PRELOAD_BUFFER_SIZE: PreferenceSpec<PreloadBufferSize> = PreferenceSpec.enumRow(
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
    )

    /**
     * The direct-play video byte-cache cap (VideoStreamCache's LRU bound).
     * Added in a later change — never string-typed in the legacy store — so
     * the shared [PreferenceCodec.readInt] legacy-string fallback this row
     * derives is inert (no string slot can exist for this name); the typed
     * read and default behave exactly like the former plain `prefs[key] ?:
     * default` line.
     */
    val VIDEO_CACHE_SIZE_MB: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "video_cache_size_mb",
        default = 1024,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold):
            // the catalog row declares no ss_*_title twin.
            titleKey = "settings_video_cache_size",
            id = "video_cache_size",
            subtitleKey = "ss_video_cache_size_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("cache", "video cache", "size", "storage", "stream cache"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    val SHOW_CLOCK_IN_PLAYER: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val SHOW_TIME_REMAINING: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    val TV_ZOOM_MODE_PERCENT: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
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
    )

    val INCOGNITO_MODE_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
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
    )

    // ------------------------------------------------------------------
    // Media segments (JSON blob + the four legacy booleans)
    // ------------------------------------------------------------------

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
     * Memoisation holder for the [VIDEO_INPUT_BINDINGS] row's JSON decode
     * (the [cachedSegmentBehaviors] precedent, same [CachedJsonNullPolicy
     * .NoMemoOnNull] shape — the null-raw path is the legacy-config fallback,
     * re-derived per read because it depends on sibling keys).
     */
    private var cachedInputBindings: ParsedCache<PlayerInputMap> =
        ParsedCache(null, PlayerInputDefaults.defaultMap())

    /**
     * Reads [VideoPlayerSlice.segmentBehaviors] — the headline migration, verbatim
     * from the store's former hand-written reader. When the JSON
     * `segment_behaviors` blob is present it is decoded and merged over
     * [SegmentBehavior.DEFAULT_BEHAVIORS] (stored values win). When the blob
     * is absent this falls back from the four legacy booleans
     * ([SKIP_INTRO_ENABLED] / [SKIP_OUTRO_ENABLED] / [AUTO_SKIP_INTRO] /
     * [AUTO_SKIP_OUTRO]) into INTRO/OUTRO SegmentBehaviors, merged over the
     * defaults — so a pre-blob install keeps its prior intro/outro behaviour.
     * When neither is present the defaults are returned unchanged.
     *
     * The derived write re-encodes the enum-keyed map as the JSON object the
     * former setter/restore wrote (enum names as keys and values — identical
     * bytes from either former encoder, since enum serialization is
     * name-based regardless of the `encodeDefaults` setting).
     */
    val SEGMENT_BEHAVIORS: PreferenceSpec<Map<MediaSegmentType, SegmentBehavior>> = PreferenceSpec.derived(
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
    )

    /**
     * The four legacy string-keyed skip booleans the [SEGMENT_BEHAVIORS] read
     * falls back from. Not [VideoPlayerSlice] fields — declared so their wire
     * names (string slots; the god-object era stored them as strings) and
     * their reset participation live with the rows. Their reads route through
     * the [SEGMENT_BEHAVIORS] row's derived lambda, which reads the raw
     * string slots and applies the per-slot defaults.
     */
    val SKIP_INTRO_ENABLED: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "skip_intro_enabled",
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val SKIP_OUTRO_ENABLED: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "skip_outro_enabled",
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val AUTO_SKIP_INTRO: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "auto_skip_intro",
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val AUTO_SKIP_OUTRO: PreferenceSpec<String?> = PreferenceSpec.string(
        keyName = "auto_skip_outro",
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val SKIP_SEGMENTS_ON_SEEK: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "skip_segments_on_seek",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
        search = PreferenceSearchSpec(
            // The search hit restates the row's screen title (the fold): the
            // catalog row declares no ss_*_title twin.
            titleKey = "settings_skip_segments_on_seek",
            id = "skip_segments_on_seek",
            subtitleKey = "ss_skip_segments_on_seek_subtitle",
            categoryKey = "ss_cat_playback",
            keywords = listOf("segment", "skip", "seek", "forward", "commercial", "auto"),
            routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
            isAdvanced = true,
        ),
    )

    // ------------------------------------------------------------------
    // Settings-search standalone entries: the per-segment-type catalog rows.
    // Six catalog facts over the ONE [SEGMENT_BEHAVIORS] knob (the
    // experimental feature-set precedent: the knob is a set, the catalog
    // rows are its members), declared here in the media-segment group's
    // catalog order.
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

    /**
     * The [SEGMENT_BEHAVIORS] row's blob decode + legacy-boolean fallback —
     * extracted verbatim from the store's former private reader so the row's
     * `parse`/`onNull` closures route through it (the null-raw leg IS the
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
     * Every row this store declares, in [VideoPlayerSlice] property order
     * (with the legacy-only rows beside the rows that read them). The reset
     * derivation below and the derivation-integrity test both iterate this —
     * a row declared but forgotten here falls out of reset coverage and is
     * caught by the JVM reset-coverage guard.
     */
    val all: List<PreferenceSpec<*>> = listOf(
        VIDEO_SEEK_DURATION_MS,
        VIDEO_CONTROLS_TIMEOUT_MS,
        VIDEO_HIDE_OSD_ON_PAUSE,
        VIDEO_RESUME_ON_HEADSET_PLUG,
        VIDEO_DEFAULT_ORIENTATION,
        VIDEO_DEFAULT_ASPECT_RATIO,
        VIDEO_GESTURE_MODE,
        VIDEO_GESTURES_ENABLED,
        VIDEO_GESTURE_INDICATOR_SIDE,
        VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED,
        VIDEO_INPUT_BINDINGS,
        VIDEO_PASS_OUT_PROTECTION_HOURS,
        VIDEO_SKIP_BACK_ON_RESUME_MS,
        VIDEO_HOLD_SPEED_ENABLED,
        VIDEO_HOLD_SPEED_MULTIPLIER,
        VIDEO_DEFAULT_SPEED,
        VIDEO_AUTOPLAY_NEXT,
        STILL_WATCHING_MODE,
        STILL_WATCHING_EPISODE_THRESHOLD,
        TRAILER_AUTOPLAY,
        CINEMA_MODE_ENABLED,
        VIDEO_SWIPE_SEEK_MAX_MS,
        VIDEO_REMEMBER_BRIGHTNESS,
        VIDEO_BRIGHTNESS_LEVEL,
        VIDEO_REMEMBER_MUTED,
        VIDEO_MUTED,
        VIDEO_AUTO_SKIP_INTRO,
        VIDEO_AUTO_SKIP_OUTRO,
        TRICKPLAY_ENABLED,
        TRICKPLAY_ON_SEEK_GESTURE,
        VIDEO_EPISODE_BROWSER_ENABLED,
        VIDEO_SHOW_PLAYBACK_METADATA,
        VIDEO_PRELOAD_BUFFER_SIZE,
        VIDEO_CACHE_SIZE_MB,
        SHOW_CLOCK_IN_PLAYER,
        SHOW_TIME_REMAINING,
        TV_ZOOM_MODE_PERCENT,
        INCOGNITO_MODE_ENABLED,
        SEGMENT_BEHAVIORS,
        SKIP_INTRO_ENABLED,
        SKIP_OUTRO_ENABLED,
        AUTO_SKIP_INTRO,
        AUTO_SKIP_OUTRO,
        SKIP_SEGMENTS_ON_SEEK,
    )

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every in-player key
     * descends under `PreferenceResetCategory.PLAYBACK`, matching the
     * facade's `resetCategoryKeys`. The facade aggregates these lists instead
     * of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, in the settings feature's
     * catalog order (the order the retired hand records carried): every row
     * above that carries a [PreferenceSearchSpec], plus the six standalone
     * per-segment-type entries. The settings feature derives its
     * spec-backed catalog rows from this list (and
     * [PlaybackPreferenceSpecs.searchEntries]) over its ordered record
     * spine, binding the declared resource keys to real resources.
     */
    val searchEntries: List<PreferenceSearchSpec> = listOf(
        VIDEO_SEEK_DURATION_MS.searchEntry(),
        VIDEO_DEFAULT_ORIENTATION.searchEntry(),
        VIDEO_GESTURE_MODE.searchEntry(),
        VIDEO_INPUT_BINDINGS.searchEntry(),
        VIDEO_GESTURE_INDICATOR_SIDE.searchEntry(),
        VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.searchEntry(),
        VIDEO_DEFAULT_SPEED.searchEntry(),
        VIDEO_DEFAULT_ASPECT_RATIO.searchEntry(),
        VIDEO_AUTOPLAY_NEXT.searchEntry(),
        STILL_WATCHING_MODE.searchEntry(),
        STILL_WATCHING_EPISODE_THRESHOLD.searchEntry(),
        VIDEO_CONTROLS_TIMEOUT_MS.searchEntry(),
        VIDEO_HIDE_OSD_ON_PAUSE.searchEntry(),
        VIDEO_RESUME_ON_HEADSET_PLUG.searchEntry(),
        VIDEO_SKIP_BACK_ON_RESUME_MS.searchEntry(),
        SHOW_CLOCK_IN_PLAYER.searchEntry(),
        VIDEO_PASS_OUT_PROTECTION_HOURS.searchEntry(),
        TRAILER_AUTOPLAY.searchEntry(),
        CINEMA_MODE_ENABLED.searchEntry(),
        VIDEO_EPISODE_BROWSER_ENABLED.searchEntry(),
        VIDEO_SHOW_PLAYBACK_METADATA.searchEntry(),
        VIDEO_SWIPE_SEEK_MAX_MS.searchEntry(),
        VIDEO_REMEMBER_BRIGHTNESS.searchEntry(),
        TRICKPLAY_ENABLED.searchEntry(),
        VIDEO_PRELOAD_BUFFER_SIZE.searchEntry(),
        VIDEO_CACHE_SIZE_MB.searchEntry(),
        INCOGNITO_MODE_ENABLED.searchEntry(),
        VIDEO_HOLD_SPEED_ENABLED.searchEntry(),
        TV_ZOOM_MODE_PERCENT.searchEntry(),
        VIDEO_BRIGHTNESS_LEVEL.searchEntry(),
        TRICKPLAY_ON_SEEK_GESTURE.searchEntry(),
        SHOW_TIME_REMAINING.searchEntry(),
        searchMediaSegmentIntro,
        searchMediaSegmentOutro,
        searchMediaSegmentPreview,
        searchMediaSegmentRecap,
        searchMediaSegmentCommercial,
        searchMediaSegmentUnknown,
        SKIP_SEGMENTS_ON_SEEK.searchEntry(),
    )

    /**
     * The projection hook for [searchEntries]: a row's declared search entry.
     * Rows without search metadata are simply not listed.
     */
    private fun PreferenceSpec<*>.searchEntry(): PreferenceSearchSpec =
        requireNotNull(search) { "row '$keyName' declares no search entry" }
}
