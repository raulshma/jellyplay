package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.datastore.toEnumOrNull
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SegmentBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **in-player video experience** preference domain:
 * seek/controls/hold-speed timing, orientation/aspect, gestures, brightness and
 * volume memory, autoplay (video + trailer), cinema mode, trickplay, episode
 * browser, playback metadata, clock/time-remaining HUD, TV zoom, incognito mode,
 * and the per-`MediaSegmentType` skip behaviour map.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters (including the bounds coercions below), read projection, legacy
 * migration, and reset-key list end-to-end. Mirrors the `PlaybackStore` /
 * `AppearanceStore` / `ServerIdentityStore` shape.
 *
 * **Headline migration — the segment-behaviour legacy fallback:**
 * [readSegmentBehaviors] reads the JSON `Map<MediaSegmentType, SegmentBehavior>`
 * blob (merging over [SegmentBehavior.DEFAULT_BEHAVIORS]); when that blob is
 * absent it falls back from the four legacy booleans (`skip_intro_enabled`,
 * `skip_outro_enabled`, `auto_skip_intro`, `auto_skip_outro`) into INTRO/OUTRO
 * SegmentBehaviors, so a pre-blob install keeps its prior intro/outro behaviour.
 *
 * **Cross-key invariants owned here:**
 *  - [setVideoPassOutProtectionHours] coerces the value to `coerceAtLeast(0)`.
 *  - [setVideoSkipBackOnResumeMs] coerces the value to `coerceAtLeast(0L)`.
 *  - [setSegmentBehaviors] re-encodes the whole map as a single JSON edit.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names so existing data is read in place
 * — no migration file, no second delegate.
 */
class VideoPlayerStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val VIDEO_SEEK_DURATION_MS = longPreferencesKey("video_seek_duration_ms")
        val VIDEO_CONTROLS_TIMEOUT_MS = longPreferencesKey("video_controls_timeout_ms")
        val VIDEO_DEFAULT_ORIENTATION = stringPreferencesKey("video_default_orientation")
        val VIDEO_DEFAULT_ASPECT_RATIO = stringPreferencesKey("video_default_aspect_ratio")
        val VIDEO_GESTURES_ENABLED = booleanPreferencesKey("video_gestures_enabled")
        val VIDEO_PASS_OUT_PROTECTION_HOURS = intPreferencesKey("video_pass_out_protection_hours")
        val VIDEO_SKIP_BACK_ON_RESUME_MS = longPreferencesKey("video_skip_back_on_resume_ms")
        val VIDEO_HOLD_SPEED_ENABLED = booleanPreferencesKey("video_hold_speed_enabled")
        val VIDEO_HOLD_SPEED_MULTIPLIER = floatPreferencesKey("video_hold_speed_multiplier")
        val VIDEO_DEFAULT_SPEED = floatPreferencesKey("video_default_speed")
        val VIDEO_AUTOPLAY_NEXT = booleanPreferencesKey("video_autoplay_next")
        val TRAILER_AUTOPLAY = booleanPreferencesKey("trailer_autoplay")
        val CINEMA_MODE_ENABLED = booleanPreferencesKey("cinema_mode_enabled")
        val VIDEO_SWIPE_SEEK_MAX_MS = longPreferencesKey("video_swipe_seek_max_ms")
        val VIDEO_REMEMBER_BRIGHTNESS = booleanPreferencesKey("video_remember_brightness")
        val VIDEO_BRIGHTNESS_LEVEL = floatPreferencesKey("video_brightness_level")
        val VIDEO_AUTO_SKIP_INTRO = booleanPreferencesKey("video_auto_skip_intro")
        val VIDEO_AUTO_SKIP_OUTRO = booleanPreferencesKey("video_auto_skip_outro")
        val VIDEO_REMEMBER_MUTED = booleanPreferencesKey("video_remember_muted")
        val VIDEO_MUTED = booleanPreferencesKey("video_muted")
        val VIDEO_GESTURE_INDICATOR_SIDE = stringPreferencesKey("video_gesture_indicator_side")
        val TRICKPLAY_ENABLED = booleanPreferencesKey("trickplay_enabled")
        val TRICKPLAY_ON_SEEK_GESTURE = booleanPreferencesKey("trickplay_on_seek_gesture")
        val VIDEO_EPISODE_BROWSER_ENABLED = booleanPreferencesKey("video_episode_browser_enabled")
        val VIDEO_SHOW_PLAYBACK_METADATA = booleanPreferencesKey("video_show_playback_metadata")
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey("video_preload_buffer_size")
        // The direct-play video byte-cache cap (VideoStreamCache's LRU bound),
        // added in a later change — never string-typed in the legacy store,
        // so the read below uses plain `prefs[key] ?: default`.
        val VIDEO_CACHE_SIZE_MB = intPreferencesKey("video_cache_size_mb")
        val SHOW_CLOCK_IN_PLAYER = booleanPreferencesKey("show_clock_in_player")
        val SHOW_TIME_REMAINING = booleanPreferencesKey("show_time_remaining")
        val TV_ZOOM_MODE_PERCENT = floatPreferencesKey("tv_zoom_mode_percent")
        val INCOGNITO_MODE_ENABLED = booleanPreferencesKey("incognito_mode_enabled")

        val SEGMENT_BEHAVIORS = stringPreferencesKey("segment_behaviors")
        val SKIP_INTRO_ENABLED = stringPreferencesKey("skip_intro_enabled")
        val SKIP_OUTRO_ENABLED = stringPreferencesKey("skip_outro_enabled")
        val AUTO_SKIP_INTRO = stringPreferencesKey("auto_skip_intro")
        val AUTO_SKIP_OUTRO = stringPreferencesKey("auto_skip_outro")
        val SKIP_SEGMENTS_ON_SEEK = booleanPreferencesKey("skip_segments_on_seek")
    }

    private var cachedSegmentBehaviors: ParsedCache<Map<MediaSegmentType, SegmentBehavior>> =
        ParsedCache(null, SegmentBehavior.DEFAULT_BEHAVIORS)

    /**
     * The in-player video preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so a
     * write to an unrelated preference does not re-derive these fields.
     */
    val videoPlayer: StateFlow<VideoPlayerSlice> =
        dataStore.sliceStateFlow(scope, seed = VideoPlayerSlice(), read = ::read)

    /**
     * Pure read of the in-player video fields from a raw [Preferences] snapshot.
     * Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): VideoPlayerSlice = VideoPlayerSlice(
        videoSeekDurationMs = PreferenceCodec.readLong(prefs, Keys.VIDEO_SEEK_DURATION_MS, "video_seek_duration_ms", 10_000L),
        videoControlsTimeoutMs = PreferenceCodec.readLong(prefs, Keys.VIDEO_CONTROLS_TIMEOUT_MS, "video_controls_timeout_ms", 5_000L),
        videoDefaultOrientation = readOrientation(prefs),
        videoDefaultAspectRatio = prefs[Keys.VIDEO_DEFAULT_ASPECT_RATIO] ?: "AUTO",
        videoGesturesEnabled = PreferenceCodec.readBool(prefs, Keys.VIDEO_GESTURES_ENABLED, "video_gestures_enabled", true),
        videoPassOutProtectionHours = PreferenceCodec.readInt(prefs, Keys.VIDEO_PASS_OUT_PROTECTION_HOURS, "video_pass_out_protection_hours", 0),
        videoSkipBackOnResumeMs = PreferenceCodec.readLong(prefs, Keys.VIDEO_SKIP_BACK_ON_RESUME_MS, "video_skip_back_on_resume_ms", 0L),
        videoHoldSpeedEnabled = PreferenceCodec.readBool(prefs, Keys.VIDEO_HOLD_SPEED_ENABLED, "video_hold_speed_enabled", true),
        videoHoldSpeedMultiplier = PreferenceCodec.readFloat(prefs, Keys.VIDEO_HOLD_SPEED_MULTIPLIER, "video_hold_speed_multiplier", 2.0f),
        videoDefaultSpeed = PreferenceCodec.readFloat(prefs, Keys.VIDEO_DEFAULT_SPEED, "video_default_speed", 1.0f),
        videoAutoplayNext = PreferenceCodec.readBool(prefs, Keys.VIDEO_AUTOPLAY_NEXT, "video_autoplay_next", true),
        trailerAutoplay = PreferenceCodec.readBool(prefs, Keys.TRAILER_AUTOPLAY, "trailer_autoplay", true),
        cinemaModeEnabled = PreferenceCodec.readBool(prefs, Keys.CINEMA_MODE_ENABLED, "cinema_mode_enabled", false),
        videoSwipeSeekMaxMs = PreferenceCodec.readLong(prefs, Keys.VIDEO_SWIPE_SEEK_MAX_MS, "video_swipe_seek_max_ms", 120_000L),
        videoRememberBrightness = PreferenceCodec.readBool(prefs, Keys.VIDEO_REMEMBER_BRIGHTNESS, "video_remember_brightness", true),
        videoBrightnessLevel = PreferenceCodec.readFloat(prefs, Keys.VIDEO_BRIGHTNESS_LEVEL, "video_brightness_level", 0.5f),
        videoAutoSkipIntro = PreferenceCodec.readBool(prefs, Keys.VIDEO_AUTO_SKIP_INTRO, "video_auto_skip_intro", false),
        videoAutoSkipOutro = PreferenceCodec.readBool(prefs, Keys.VIDEO_AUTO_SKIP_OUTRO, "video_auto_skip_outro", false),
        videoRememberMuted = PreferenceCodec.readBool(prefs, Keys.VIDEO_REMEMBER_MUTED, "video_remember_muted", true),
        videoMuted = PreferenceCodec.readBool(prefs, Keys.VIDEO_MUTED, "video_muted", false),
        videoGestureIndicatorSide = readGestureIndicatorSide(prefs),
        trickplayEnabled = PreferenceCodec.readBool(prefs, Keys.TRICKPLAY_ENABLED, "trickplay_enabled", true),
        trickplayOnSeekGesture = PreferenceCodec.readBool(prefs, Keys.TRICKPLAY_ON_SEEK_GESTURE, "trickplay_on_seek_gesture", true),
        videoEpisodeBrowserEnabled = PreferenceCodec.readBool(prefs, Keys.VIDEO_EPISODE_BROWSER_ENABLED, "video_episode_browser_enabled", true),
        videoShowPlaybackMetadata = PreferenceCodec.readBool(prefs, Keys.VIDEO_SHOW_PLAYBACK_METADATA, "video_show_playback_metadata", true),
        videoPreloadBufferSize = readPreloadBufferSize(prefs),
        videoCacheSizeMb = prefs[Keys.VIDEO_CACHE_SIZE_MB] ?: 1024,
        showClockInPlayer = PreferenceCodec.readBool(prefs, Keys.SHOW_CLOCK_IN_PLAYER, "show_clock_in_player", false),
        showTimeRemaining = PreferenceCodec.readBool(prefs, Keys.SHOW_TIME_REMAINING, "show_time_remaining", false),
        tvZoomModePercent = PreferenceCodec.readFloat(prefs, Keys.TV_ZOOM_MODE_PERCENT, "tv_zoom_mode_percent", 0f),
        incognitoModeEnabled = PreferenceCodec.readBool(prefs, Keys.INCOGNITO_MODE_ENABLED, "incognito_mode_enabled", false),
        segmentBehaviors = readSegmentBehaviorsCached(prefs),
        skipSegmentsOnSeek = PreferenceCodec.readBool(prefs, Keys.SKIP_SEGMENTS_ON_SEEK, "skip_segments_on_seek", false),
    )

    /**
     * Memoised segment-behaviour read over the `segment_behaviors` JSON blob.
     *
     * [CachedJsonNullPolicy.NoMemoOnNull] — this store's pre-promotion policy,
     * preserved verbatim: only the JSON-blob decode is memoised. When the blob
     * is absent the legacy-boolean fallback must still run on EVERY emission
     * (its result depends on the four SKIP_* / AUTO_* keys, not on the raw
     * string), so a null raw never short-circuits against the cache — that
     * would freeze the legacy migration out entirely.
     *
     * The [PreferenceCodec.cachedJson] `parse`/`onNull` closures both route
     * through [readSegmentBehaviors], whose null-raw leg IS the legacy
     * fallback (and whose decode leg owns its own corrupt-blob tolerance, so
     * the helper's fallback `default` is only a belt-and-braces guard).
     */
    private fun readSegmentBehaviorsCached(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.SEGMENT_BEHAVIORS],
            cache = cachedSegmentBehaviors,
            default = SegmentBehavior.DEFAULT_BEHAVIORS,
            parse = { readSegmentBehaviors(prefs) },
            onNull = { readSegmentBehaviors(prefs) },
            cacheRef = { cachedSegmentBehaviors = it },
            nullPolicy = CachedJsonNullPolicy.NoMemoOnNull,
        )

    private fun readOrientation(prefs: Preferences): OrientationMode =
        prefs[Keys.VIDEO_DEFAULT_ORIENTATION].toEnumOrNull() ?: OrientationMode.SENSOR_LANDSCAPE

    private fun readGestureIndicatorSide(prefs: Preferences): GestureIndicatorSide =
        prefs[Keys.VIDEO_GESTURE_INDICATOR_SIDE].toEnumOrNull() ?: GestureIndicatorSide.OPPOSITE

    private fun readPreloadBufferSize(prefs: Preferences): PreloadBufferSize =
        prefs[Keys.VIDEO_PRELOAD_BUFFER_SIZE].toEnumOrNull() ?: PreloadBufferSize.MEDIUM

    /**
     * Reads the per-`MediaSegmentType` skip behaviour map. When the JSON
     * `segment_behaviors` blob is present it is decoded and merged over
     * [SegmentBehavior.DEFAULT_BEHAVIORS] (stored values win). When the blob is
     * absent this falls back from the four legacy booleans
     * (`skip_intro_enabled`, `skip_outro_enabled`, `auto_skip_intro`,
     * `auto_skip_outro`) into INTRO/OUTRO SegmentBehaviors, merged over the
     * defaults — so a pre-blob install keeps its prior intro/outro behaviour.
     * When neither is present the defaults are returned unchanged.
     */
    private fun readSegmentBehaviors(prefs: Preferences): Map<MediaSegmentType, SegmentBehavior> {
        val raw = prefs[Keys.SEGMENT_BEHAVIORS]
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
            } catch (_: Exception) { SegmentBehavior.DEFAULT_BEHAVIORS }
        }

        val hasLegacyKeys = prefs.contains(Keys.SKIP_INTRO_ENABLED) ||
            prefs.contains(Keys.SKIP_OUTRO_ENABLED) ||
            prefs.contains(Keys.AUTO_SKIP_INTRO) ||
            prefs.contains(Keys.AUTO_SKIP_OUTRO)
        if (!hasLegacyKeys) return SegmentBehavior.DEFAULT_BEHAVIORS

        val migrated = mutableMapOf<MediaSegmentType, SegmentBehavior>()
        val skipIntro = prefs[Keys.SKIP_INTRO_ENABLED]?.toBoolean() ?: true
        val skipOutro = prefs[Keys.SKIP_OUTRO_ENABLED]?.toBoolean() ?: true
        val autoIntro = prefs[Keys.AUTO_SKIP_INTRO]?.toBoolean() ?: false
        val autoOutro = prefs[Keys.AUTO_SKIP_OUTRO]?.toBoolean() ?: false
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

    // ------------------------------------------------------------------
    // Setters — bounds coercions and the whole-map JSON edit live here.
    // ------------------------------------------------------------------

    suspend fun setVideoSeekDurationMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SEEK_DURATION_MS] = ms }
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = ms }
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_ORIENTATION] = mode.name }
    }

    suspend fun setVideoGesturesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_GESTURES_ENABLED] = enabled }
    }

    suspend fun setVideoPassOutProtectionHours(hours: Int) {
        dataStore.edit { it[Keys.VIDEO_PASS_OUT_PROTECTION_HOURS] = hours.coerceAtLeast(0) }
    }

    suspend fun setVideoSkipBackOnResumeMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SKIP_BACK_ON_RESUME_MS] = ms.coerceAtLeast(0L) }
    }

    suspend fun setVideoHoldSpeedEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_HOLD_SPEED_ENABLED] = enabled }
    }

    suspend fun setVideoHoldSpeedMultiplier(multiplier: Float) {
        dataStore.edit { it[Keys.VIDEO_HOLD_SPEED_MULTIPLIER] = multiplier }
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_SPEED] = speed }
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        dataStore.edit { it[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = ratio }
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTOPLAY_NEXT] = enabled }
    }

    suspend fun setTrailerAutoplay(enabled: Boolean) {
        dataStore.edit { it[Keys.TRAILER_AUTOPLAY] = enabled }
    }

    suspend fun setCinemaModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CINEMA_MODE_ENABLED] = enabled }
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        dataStore.edit { it[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = ms }
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_REMEMBER_BRIGHTNESS] = enabled }
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        dataStore.edit { it[Keys.VIDEO_BRIGHTNESS_LEVEL] = level }
    }

    suspend fun setVideoAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTO_SKIP_INTRO] = enabled }
    }

    suspend fun setVideoAutoSkipOutro(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_AUTO_SKIP_OUTRO] = enabled }
    }

    suspend fun setVideoRememberMuted(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_REMEMBER_MUTED] = enabled }
    }

    suspend fun setVideoMuted(muted: Boolean) {
        dataStore.edit { it[Keys.VIDEO_MUTED] = muted }
    }

    suspend fun setVideoGestureIndicatorSide(side: GestureIndicatorSide) {
        dataStore.edit { it[Keys.VIDEO_GESTURE_INDICATOR_SIDE] = side.name }
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.TRICKPLAY_ENABLED] = enabled }
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        dataStore.edit { it[Keys.TRICKPLAY_ON_SEEK_GESTURE] = enabled }
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = enabled }
    }

    suspend fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_SHOW_PLAYBACK_METADATA] = enabled }
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { it[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = size.name }
    }

    /** Sibling of [com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore.setAudioCacheSizeMb]. */
    suspend fun setVideoCacheSizeMb(sizeMb: Int) {
        dataStore.edit { it[Keys.VIDEO_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setShowClockInPlayer(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_CLOCK_IN_PLAYER] = enabled }
    }

    suspend fun setShowTimeRemaining(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_TIME_REMAINING] = enabled }
    }

    suspend fun setTvZoomModePercent(percent: Float) {
        dataStore.edit { it[Keys.TV_ZOOM_MODE_PERCENT] = percent }
    }

    suspend fun setIncognitoModeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.INCOGNITO_MODE_ENABLED] = enabled }
    }

    /**
     * Skip-on-forward-seek: when on, a user-initiated seek that lands
     * strictly inside an AUTO_SKIP segment is pulled to the segment's end
     * (with a "Skipped …" confirmation). Internal, app-driven seeks are never
     * affected — the gate rides the ViewModel's `seekTo(userInitiated)`.
     */
    suspend fun setSkipSegmentsOnSeek(enabled: Boolean) {
        dataStore.edit { it[Keys.SKIP_SEGMENTS_ON_SEEK] = enabled }
    }

    suspend fun setSegmentBehaviors(behaviors: Map<MediaSegmentType, SegmentBehavior>) {
        dataStore.edit { prefs ->
            prefs[Keys.SEGMENT_BEHAVIORS] = PreferenceCodec.json.encodeToString(
                behaviors.mapKeys { it.key.name }.mapValues { it.value.name },
            )
        }
    }

    /**
     * Updates a single segment type's behaviour, read-modify-writing the stored
     * map (merged over the defaults + legacy fallback) so callers don't have to
     * reconstruct the whole map. Preserves the 100-entry behaviour of the other
     * per-item maps is N/A here (segment types are a bounded enum set).
     */
    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        dataStore.edit { prefs ->
            val current = readSegmentBehaviors(prefs).toMutableMap()
            current[type] = behavior
            prefs[Keys.SEGMENT_BEHAVIORS] = PreferenceCodec.json.encodeToString(
                current.mapKeys { it.key.name }.mapValues { it.value.name },
            )
        }
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
     * [category]. Every in-player key owned here descends under
     * `PreferenceResetCategory.PLAYBACK`, matching the facade's
     * `resetCategoryKeys`.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.PLAYBACK -> listOf(
            Keys.VIDEO_SEEK_DURATION_MS,
            Keys.VIDEO_CONTROLS_TIMEOUT_MS,
            Keys.VIDEO_DEFAULT_ORIENTATION,
            Keys.VIDEO_DEFAULT_ASPECT_RATIO,
            Keys.VIDEO_GESTURES_ENABLED,
            Keys.VIDEO_PASS_OUT_PROTECTION_HOURS,
            Keys.VIDEO_SKIP_BACK_ON_RESUME_MS,
            Keys.VIDEO_HOLD_SPEED_ENABLED,
            Keys.VIDEO_HOLD_SPEED_MULTIPLIER,
            Keys.VIDEO_DEFAULT_SPEED,
            Keys.VIDEO_AUTOPLAY_NEXT,
            Keys.TRAILER_AUTOPLAY,
            Keys.CINEMA_MODE_ENABLED,
            Keys.VIDEO_SWIPE_SEEK_MAX_MS,
            Keys.VIDEO_REMEMBER_BRIGHTNESS,
            Keys.VIDEO_BRIGHTNESS_LEVEL,
            Keys.VIDEO_AUTO_SKIP_INTRO,
            Keys.VIDEO_AUTO_SKIP_OUTRO,
            Keys.VIDEO_REMEMBER_MUTED,
            Keys.VIDEO_MUTED,
            Keys.VIDEO_GESTURE_INDICATOR_SIDE,
            Keys.TRICKPLAY_ENABLED,
            Keys.TRICKPLAY_ON_SEEK_GESTURE,
            Keys.VIDEO_EPISODE_BROWSER_ENABLED,
            Keys.VIDEO_SHOW_PLAYBACK_METADATA,
            Keys.VIDEO_PRELOAD_BUFFER_SIZE,
            Keys.VIDEO_CACHE_SIZE_MB,
            Keys.SHOW_CLOCK_IN_PLAYER,
            Keys.SHOW_TIME_REMAINING,
            Keys.TV_ZOOM_MODE_PERCENT,
            Keys.INCOGNITO_MODE_ENABLED,
            Keys.SEGMENT_BEHAVIORS,
            Keys.SKIP_INTRO_ENABLED,
            Keys.SKIP_OUTRO_ENABLED,
            Keys.AUTO_SKIP_INTRO,
            Keys.AUTO_SKIP_OUTRO,
            Keys.SKIP_SEGMENTS_ON_SEEK,
        )
        else -> emptyList()
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (segment
     * behaviours re-encoded via the enum-keyed map with defaults).
     */
    suspend fun restore(slice: VideoPlayerSlice) {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_SEEK_DURATION_MS] = slice.videoSeekDurationMs
            prefs[Keys.VIDEO_CONTROLS_TIMEOUT_MS] = slice.videoControlsTimeoutMs
            prefs[Keys.VIDEO_DEFAULT_ORIENTATION] = slice.videoDefaultOrientation.name
            prefs[Keys.VIDEO_DEFAULT_ASPECT_RATIO] = slice.videoDefaultAspectRatio
            prefs[Keys.VIDEO_GESTURES_ENABLED] = slice.videoGesturesEnabled
            prefs[Keys.VIDEO_PASS_OUT_PROTECTION_HOURS] = slice.videoPassOutProtectionHours
            prefs[Keys.VIDEO_SKIP_BACK_ON_RESUME_MS] = slice.videoSkipBackOnResumeMs
            prefs[Keys.VIDEO_HOLD_SPEED_ENABLED] = slice.videoHoldSpeedEnabled
            prefs[Keys.VIDEO_HOLD_SPEED_MULTIPLIER] = slice.videoHoldSpeedMultiplier
            prefs[Keys.VIDEO_DEFAULT_SPEED] = slice.videoDefaultSpeed
            prefs[Keys.VIDEO_AUTOPLAY_NEXT] = slice.videoAutoplayNext
            prefs[Keys.TRAILER_AUTOPLAY] = slice.trailerAutoplay
            prefs[Keys.CINEMA_MODE_ENABLED] = slice.cinemaModeEnabled
            prefs[Keys.VIDEO_SWIPE_SEEK_MAX_MS] = slice.videoSwipeSeekMaxMs
            prefs[Keys.VIDEO_REMEMBER_BRIGHTNESS] = slice.videoRememberBrightness
            prefs[Keys.VIDEO_BRIGHTNESS_LEVEL] = slice.videoBrightnessLevel
            prefs[Keys.VIDEO_AUTO_SKIP_INTRO] = slice.videoAutoSkipIntro
            prefs[Keys.VIDEO_AUTO_SKIP_OUTRO] = slice.videoAutoSkipOutro
            prefs[Keys.VIDEO_REMEMBER_MUTED] = slice.videoRememberMuted
            prefs[Keys.VIDEO_MUTED] = slice.videoMuted
            prefs[Keys.VIDEO_GESTURE_INDICATOR_SIDE] = slice.videoGestureIndicatorSide.name
            prefs[Keys.TRICKPLAY_ENABLED] = slice.trickplayEnabled
            prefs[Keys.TRICKPLAY_ON_SEEK_GESTURE] = slice.trickplayOnSeekGesture
            prefs[Keys.SEGMENT_BEHAVIORS] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<Map<MediaSegmentType, SegmentBehavior>>(),
                slice.segmentBehaviors,
            )
            prefs[Keys.VIDEO_EPISODE_BROWSER_ENABLED] = slice.videoEpisodeBrowserEnabled
            prefs[Keys.VIDEO_SHOW_PLAYBACK_METADATA] = slice.videoShowPlaybackMetadata
            prefs[Keys.VIDEO_PRELOAD_BUFFER_SIZE] = slice.videoPreloadBufferSize.name
            prefs[Keys.VIDEO_CACHE_SIZE_MB] = slice.videoCacheSizeMb
            prefs[Keys.SHOW_CLOCK_IN_PLAYER] = slice.showClockInPlayer
            prefs[Keys.SHOW_TIME_REMAINING] = slice.showTimeRemaining
            prefs[Keys.TV_ZOOM_MODE_PERCENT] = slice.tvZoomModePercent
            prefs[Keys.INCOGNITO_MODE_ENABLED] = slice.incognitoModeEnabled
            prefs[Keys.SKIP_SEGMENTS_ON_SEEK] = slice.skipSegmentsOnSeek
        }
    }
}

/**
 * The in-player video preference slice. Plain data class (Compose-free) so the
 * datastore module stays framework-light. Defaults mirror the projection
 * defaults in [VideoPlayerStore.read].
 */
@Immutable
@Serializable
data class VideoPlayerSlice(
    val videoSeekDurationMs: Long = 10_000L,
    val videoControlsTimeoutMs: Long = 5_000L,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoGesturesEnabled: Boolean = true,
    val videoPassOutProtectionHours: Int = 0,
    val videoSkipBackOnResumeMs: Long = 0L,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoAutoplayNext: Boolean = true,
    val trailerAutoplay: Boolean = true,
    val cinemaModeEnabled: Boolean = false,
    val videoSwipeSeekMaxMs: Long = 120_000L,
    val videoRememberBrightness: Boolean = true,
    val videoBrightnessLevel: Float = 0.5f,
    val videoAutoSkipIntro: Boolean = false,
    val videoAutoSkipOutro: Boolean = false,
    val videoRememberMuted: Boolean = true,
    val videoMuted: Boolean = false,
    val videoGestureIndicatorSide: GestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
    val trickplayEnabled: Boolean = true,
    val trickplayOnSeekGesture: Boolean = true,
    val videoEpisodeBrowserEnabled: Boolean = true,
    val videoShowPlaybackMetadata: Boolean = true,
    val videoPreloadBufferSize: PreloadBufferSize = PreloadBufferSize.MEDIUM,
    /** Direct-play byte-cache cap in MB (see [Keys.VIDEO_CACHE_SIZE_MB]). */
    val videoCacheSizeMb: Int = 1024,
    val showClockInPlayer: Boolean = false,
    val showTimeRemaining: Boolean = false,
    val tvZoomModePercent: Float = 0f,
    val incognitoModeEnabled: Boolean = false,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = SegmentBehavior.DEFAULT_BEHAVIORS,
    /**
     * Skip-on-forward-seek. Default **off**: opt-in, because silently
     * remapping a user's seek target is a behavior change they must ask for.
     */
    val skipSegmentsOnSeek: Boolean = false,
)
