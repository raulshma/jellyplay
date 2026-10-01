package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
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
 */
internal object VideoPlayerPreferenceSpecs {

    // ------------------------------------------------------------------
    // Transport timing
    // ------------------------------------------------------------------

    val VIDEO_SEEK_DURATION_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "video_seek_duration_ms",
        default = 10_000L,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_CONTROLS_TIMEOUT_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "video_controls_timeout_ms",
        default = 5_000L,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    // ------------------------------------------------------------------
    // Orientation / aspect / layout
    // ------------------------------------------------------------------

    val VIDEO_DEFAULT_ORIENTATION: PreferenceSpec<OrientationMode> = PreferenceSpec.enumRow(
        keyName = "video_default_orientation",
        default = OrientationMode.SENSOR_LANDSCAPE,
        resetCategory = PreferenceResetCategory.PLAYBACK,
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
    )

    // ------------------------------------------------------------------
    // Speed / brightness / volume memory
    // ------------------------------------------------------------------

    val VIDEO_PASS_OUT_PROTECTION_HOURS: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "video_pass_out_protection_hours",
        default = 0,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_SKIP_BACK_ON_RESUME_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "video_skip_back_on_resume_ms",
        default = 0L,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_HOLD_SPEED_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_hold_speed_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
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
    )

    val VIDEO_AUTOPLAY_NEXT: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_autoplay_next",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    // ------------------------------------------------------------------
    // "Still watching?" + autoplay + cinema + HUD
    // ------------------------------------------------------------------

    val STILL_WATCHING_MODE: PreferenceSpec<StillWatchingMode> = PreferenceSpec.enumRow(
        keyName = "still_watching_mode",
        default = StillWatchingMode.OFF,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val STILL_WATCHING_EPISODE_THRESHOLD: PreferenceSpec<Int> = PreferenceSpec.plainInt(
        keyName = "still_watching_episode_threshold",
        default = 0,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val TRAILER_AUTOPLAY: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "trailer_autoplay",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val CINEMA_MODE_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "cinema_mode_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_SWIPE_SEEK_MAX_MS: PreferenceSpec<Long> = PreferenceSpec.plainLong(
        keyName = "video_swipe_seek_max_ms",
        default = 120_000L,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_REMEMBER_BRIGHTNESS: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_remember_brightness",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_BRIGHTNESS_LEVEL: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
        keyName = "video_brightness_level",
        default = 0.5f,
        resetCategory = PreferenceResetCategory.PLAYBACK,
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
    )

    val TRICKPLAY_ON_SEEK_GESTURE: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "trickplay_on_seek_gesture",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_EPISODE_BROWSER_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_episode_browser_enabled",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_SHOW_PLAYBACK_METADATA: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "video_show_playback_metadata",
        default = true,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val VIDEO_PRELOAD_BUFFER_SIZE: PreferenceSpec<PreloadBufferSize> = PreferenceSpec.enumRow(
        keyName = "video_preload_buffer_size",
        default = PreloadBufferSize.MEDIUM,
        resetCategory = PreferenceResetCategory.PLAYBACK,
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
    )

    val SHOW_CLOCK_IN_PLAYER: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_clock_in_player",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val SHOW_TIME_REMAINING: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "show_time_remaining",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val TV_ZOOM_MODE_PERCENT: PreferenceSpec<Float> = PreferenceSpec.plainFloat(
        keyName = "tv_zoom_mode_percent",
        default = 0f,
        resetCategory = PreferenceResetCategory.PLAYBACK,
    )

    val INCOGNITO_MODE_ENABLED: PreferenceSpec<Boolean> = PreferenceSpec.plainBoolean(
        keyName = "incognito_mode_enabled",
        default = false,
        resetCategory = PreferenceResetCategory.PLAYBACK,
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
        VIDEO_DEFAULT_ORIENTATION,
        VIDEO_DEFAULT_ASPECT_RATIO,
        VIDEO_GESTURE_MODE,
        VIDEO_GESTURES_ENABLED,
        VIDEO_GESTURE_INDICATOR_SIDE,
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
}
