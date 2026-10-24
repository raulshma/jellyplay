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
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
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
 * migration, and reset-key list end-to-end.
 *
 * **Spec derivation:** every key this
 * store owns is declared exactly once as a row in [VideoPlayerPreferenceSpecs]
 * (wire name, default, reset category, read/write encoding) and the machinery
 * below is derived from those rows — each [Keys] member rebuilds its row's
 * typed key from the row's wire name, each [read] projection row delegates to
 * its row encoding, the single-key setters and [restore] delegate to the rows'
 * derived writes, and [resetKeysFor] filters the rows by reset category. The
 * migration semantics (the `video_gestures_enabled` → [GestureMode] fallback,
 * the segment-behaviour four-boolean fallback, the legacy string-key reads)
 * live in the rows' encodings, next to the key they apply to.
 *
 * **Cross-key invariants owned here (hand-written by decision — an invariant
 * or a bounds policy spanning more than a row encoding cannot be derived):**
 *  - [setVideoPassOutProtectionHours] coerces the value to `coerceAtLeast(0)`.
 *  - [setVideoSkipBackOnResumeMs] coerces the value to `coerceAtLeast(0L)`.
 *  - [setStillWatchingEpisodeThreshold] coerces the value to `coerceAtLeast(0)`.
 *  - [setSegmentBehaviors] / [setSegmentBehavior] write the whole (or
 *    read-modify-written) behaviour map as a single JSON edit.
 *  - [restore] clears the superseded legacy `video_gestures_enabled` boolean
 *    beside the [VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE] row.
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

    /**
     * The store's DataStore keys, each derived from its
     * [VideoPlayerPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard and the key-identity reference for the hand-written invariant
     * setters below.
     */
    internal object Keys {
        val VIDEO_SEEK_DURATION_MS = longPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.keyName)
        val VIDEO_CONTROLS_TIMEOUT_MS = longPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.keyName)
        val SKIP_PROMPT_DURATION_MS = longPreferencesKey(VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS.keyName)
        val VIDEO_HIDE_OSD_ON_PAUSE = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.keyName)
        val VIDEO_RESUME_ON_HEADSET_PLUG = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.keyName)
        val VIDEO_DEFAULT_ORIENTATION = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.keyName)
        val VIDEO_DEFAULT_ASPECT_RATIO = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.keyName)
        val VIDEO_GESTURES_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_GESTURES_ENABLED.keyName)
        val VIDEO_GESTURE_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.keyName)
        val VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.keyName)
        val VIDEO_INPUT_BINDINGS = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.keyName)
        val VIDEO_PASS_OUT_PROTECTION_HOURS = intPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.keyName)
        val VIDEO_SKIP_BACK_ON_RESUME_MS = longPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.keyName)
        val VIDEO_HOLD_SPEED_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.keyName)
        val VIDEO_HOLD_SPEED_MULTIPLIER = floatPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.keyName)
        val VIDEO_DEFAULT_SPEED = floatPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.keyName)
        val VIDEO_AUTOPLAY_NEXT = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.keyName)
        val STILL_WATCHING_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.keyName)
        val STILL_WATCHING_EPISODE_THRESHOLD = intPreferencesKey(VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.keyName)
        val TRAILER_AUTOPLAY = booleanPreferencesKey(VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.keyName)
        val CINEMA_MODE_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.keyName)
        val PLAY_TRAILER_BEFORE_MOVIE = booleanPreferencesKey(VideoPlayerPreferenceSpecs.PLAY_TRAILER_BEFORE_MOVIE.keyName)
        val VIDEO_SWIPE_SEEK_MAX_MS = longPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.keyName)
        val VIDEO_REMEMBER_BRIGHTNESS = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.keyName)
        val VIDEO_BRIGHTNESS_LEVEL = floatPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.keyName)
        val VIDEO_AUTO_SKIP_INTRO = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.keyName)
        val VIDEO_AUTO_SKIP_OUTRO = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.keyName)
        val VIDEO_REMEMBER_MUTED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.keyName)
        val VIDEO_MUTED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_MUTED.keyName)
        val VIDEO_GESTURE_INDICATOR_SIDE = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.keyName)
        val TRICKPLAY_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.keyName)
        val TRICKPLAY_ON_SEEK_GESTURE = booleanPreferencesKey(VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.keyName)
        val TRICKPLAY_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.TRICKPLAY_MODE.keyName)
        val VIDEO_EPISODE_BROWSER_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.keyName)
        val VIDEO_SHOW_PLAYBACK_METADATA = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.keyName)
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.keyName)
        val VIDEO_CACHE_SIZE_MB = intPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.keyName)
        val SHOW_CLOCK_IN_PLAYER = booleanPreferencesKey(VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.keyName)
        val SHOW_TIME_REMAINING = booleanPreferencesKey(VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.keyName)
        val AMBIENT_BLUR_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.AMBIENT_BLUR_ENABLED.keyName)
        val BITMAP_SUBTITLE_SCALE_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.BITMAP_SUBTITLE_SCALE_MODE.keyName)
        val PAUSE_SCREENSAVER_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_MODE.keyName)
        val PAUSE_SCREENSAVER_AFTER_MINUTES = intPreferencesKey(VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES.keyName)
        val PAUSE_DETAILS_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.PAUSE_DETAILS_ENABLED.keyName)
        val NEXT_UP_BUTTON_PREROLL_SEC = intPreferencesKey(VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC.keyName)
        val MPV_TONE_MAPPING_CURVE = stringPreferencesKey(VideoPlayerPreferenceSpecs.MPV_TONE_MAPPING_CURVE.keyName)
        val MPV_DEBAND_MODE = stringPreferencesKey(VideoPlayerPreferenceSpecs.MPV_DEBAND_MODE.keyName)
        val MPV_MOTION_INTERPOLATION = booleanPreferencesKey(VideoPlayerPreferenceSpecs.MPV_MOTION_INTERPOLATION.keyName)
        val MPV_INPUT_CONF = stringPreferencesKey(VideoPlayerPreferenceSpecs.MPV_INPUT_CONF.keyName)
        val MPV_CUSTOM_BUTTONS = stringPreferencesKey(VideoPlayerPreferenceSpecs.MPV_CUSTOM_BUTTONS.keyName)
        val AUTOPLAY_EPISODE_LIMIT = intPreferencesKey(VideoPlayerPreferenceSpecs.AUTOPLAY_EPISODE_LIMIT.keyName)
        val SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH =
            booleanPreferencesKey(VideoPlayerPreferenceSpecs.SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH.keyName)
        val TV_ZOOM_MODE_PERCENT = floatPreferencesKey(VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.keyName)
        val INCOGNITO_MODE_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.keyName)

        val SEGMENT_BEHAVIORS = stringPreferencesKey(VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.keyName)
        val SEGMENT_SKIP_DELAYS_MS = stringPreferencesKey(VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.keyName)
        val SKIP_INTRO_ENABLED = stringPreferencesKey(VideoPlayerPreferenceSpecs.SKIP_INTRO_ENABLED.keyName)
        val SKIP_OUTRO_ENABLED = stringPreferencesKey(VideoPlayerPreferenceSpecs.SKIP_OUTRO_ENABLED.keyName)
        val AUTO_SKIP_INTRO = stringPreferencesKey(VideoPlayerPreferenceSpecs.AUTO_SKIP_INTRO.keyName)
        val AUTO_SKIP_OUTRO = stringPreferencesKey(VideoPlayerPreferenceSpecs.AUTO_SKIP_OUTRO.keyName)
        val SKIP_SEGMENTS_ON_SEEK = booleanPreferencesKey(VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.keyName)
    }

    /**
     * The in-player video preference slice, derived directly from the raw
     * DataStore (not mapped through the whole-`UserPreferences` aggregate), so a
     * write to an unrelated preference does not re-derive these fields.
     */
    val videoPlayer: StateFlow<VideoPlayerSlice> =
        dataStore.sliceStateFlow(scope, seed = VideoPlayerSlice(), read = ::read)

    /**
     * Pure read of the in-player video fields from a raw [Preferences] snapshot,
     * each field delegated to its [VideoPlayerPreferenceSpecs] row encoding
     * (including the segment-behaviour legacy migration — the row's derived
     * read). Exposed so the facade can fold these into the whole-`UserPreferences`
     * projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): VideoPlayerSlice = VideoPlayerSlice(
        videoSeekDurationMs = VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.readFrom(prefs),
        videoControlsTimeoutMs = VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.readFrom(prefs),
        skipPromptDurationMs = VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS.readFrom(prefs).coerceIn(VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS_BOUNDS),
        videoHideOsdOnPause = VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.readFrom(prefs),
        videoResumeOnHeadsetPlug = VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.readFrom(prefs),
        videoDefaultOrientation = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.readFrom(prefs),
        videoDefaultAspectRatio = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.readFrom(prefs),
        videoGestureMode = VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.readFrom(prefs),
        videoDoubleTapHoldSeekEnabled = VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.readFrom(prefs),
        videoInputBindings = VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.readFrom(prefs),
        videoPassOutProtectionHours = VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.readFrom(prefs),
        videoSkipBackOnResumeMs = VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.readFrom(prefs),
        videoHoldSpeedEnabled = VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.readFrom(prefs),
        videoHoldSpeedMultiplier = VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.readFrom(prefs),
        videoDefaultSpeed = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.readFrom(prefs),
        videoAutoplayNext = VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.readFrom(prefs),
        autoplayEpisodeLimit = VideoPlayerPreferenceSpecs.AUTOPLAY_EPISODE_LIMIT.readFrom(prefs),
        showNextEpisodeDetailsAfterFinish = VideoPlayerPreferenceSpecs.SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH.readFrom(prefs),
        stillWatchingMode = VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.readFrom(prefs),
        stillWatchingEpisodeThreshold = VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.readFrom(prefs),
        trailerAutoplay = VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.readFrom(prefs),
        cinemaModeEnabled = VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.readFrom(prefs),
        playTrailerBeforeMovie = VideoPlayerPreferenceSpecs.PLAY_TRAILER_BEFORE_MOVIE.readFrom(prefs),
        videoSwipeSeekMaxMs = VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.readFrom(prefs),
        videoRememberBrightness = VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.readFrom(prefs),
        videoBrightnessLevel = VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.readFrom(prefs),
        videoAutoSkipIntro = VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.readFrom(prefs),
        videoAutoSkipOutro = VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.readFrom(prefs),
        videoRememberMuted = VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.readFrom(prefs),
        videoMuted = VideoPlayerPreferenceSpecs.VIDEO_MUTED.readFrom(prefs),
        videoGestureIndicatorSide = VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.readFrom(prefs),
        trickplayEnabled = VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.readFrom(prefs),
        trickplayOnSeekGesture = VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.readFrom(prefs),
        trickplayMode = VideoPlayerPreferenceSpecs.TRICKPLAY_MODE.readFrom(prefs),
        videoEpisodeBrowserEnabled = VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.readFrom(prefs),
        videoShowPlaybackMetadata = VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.readFrom(prefs),
        videoPreloadBufferSize = VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.readFrom(prefs),
        videoCacheSizeMb = VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.readFrom(prefs),
        showClockInPlayer = VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.readFrom(prefs),
        showTimeRemaining = VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.readFrom(prefs),
        ambientBlurEnabled = VideoPlayerPreferenceSpecs.AMBIENT_BLUR_ENABLED.readFrom(prefs),
        tvZoomModePercent = VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.readFrom(prefs),
        bitmapSubtitleScaleMode = VideoPlayerPreferenceSpecs.BITMAP_SUBTITLE_SCALE_MODE.readFrom(prefs),
        incognitoModeEnabled = VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.readFrom(prefs),
        pauseScreensaverMode = VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_MODE.readFrom(prefs),
        pauseScreensaverAfterMinutes = VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES.readFrom(prefs),
        pauseDetailsEnabled = VideoPlayerPreferenceSpecs.PAUSE_DETAILS_ENABLED.readFrom(prefs),
        segmentBehaviors = VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.readFrom(prefs),
        segmentSkipDelaysMs = VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.readFrom(prefs),
        skipSegmentsOnSeek = VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.readFrom(prefs),
        nextUpButtonPrerollSec = VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC.readFrom(prefs),
        mpvToneMappingCurve = VideoPlayerPreferenceSpecs.MPV_TONE_MAPPING_CURVE.readFrom(prefs),
        mpvDebandMode = VideoPlayerPreferenceSpecs.MPV_DEBAND_MODE.readFrom(prefs),
        mpvMotionInterpolation = VideoPlayerPreferenceSpecs.MPV_MOTION_INTERPOLATION.readFrom(prefs),
        mpvInputConf = VideoPlayerPreferenceSpecs.MPV_INPUT_CONF.readFrom(prefs),
        mpvCustomButtons = VideoPlayerPreferenceSpecs.MPV_CUSTOM_BUTTONS.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, JSON — is the row's, not re-declared
    // here); bounds coercions and the whole-map JSON edits live below,
    // hand-written.
    // ------------------------------------------------------------------

    suspend fun setVideoSeekDurationMs(ms: Long) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.writeTo(it, ms) }
    }

    suspend fun setVideoControlsTimeoutMs(ms: Long) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.writeTo(it, ms) }
    }

    suspend fun setSkipPromptDurationMs(ms: Long) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS.writeTo(it, ms.coerceIn(VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS_BOUNDS)) }
    }

    /** Pausing must not summon the control overlay (default off). */
    suspend fun setVideoHideOsdOnPause(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.writeTo(it, enabled) }
    }

    /**
     * Resume when headphones reconnect after the becoming-noisy
     * auto-pause (opt-in, default off; the pause-marker + freshness decision
     * lives in core:data's `HeadsetResumePolicy`).
     */
    suspend fun setVideoResumeOnHeadsetPlug(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.writeTo(it, enabled) }
    }

    suspend fun setVideoDefaultOrientation(mode: OrientationMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.writeTo(it, mode) }
    }

    /**
     * The demoted [GestureMode] preset: writes the mode row AND mass-flips
     * the touch-family enabled flags of the stored mapping
     * ([PlayerInputDefaults.applyGestureModePreset]) in one atomic edit —
     * the mode is a convenience switch over the mapping, never a second
     * gate (the detectors consult the mapping only).
     */
    suspend fun setVideoGestureMode(mode: GestureMode) {
        dataStore.edit { prefs ->
            VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.writeTo(prefs, mode)
            val current = VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.readFrom(prefs)
            VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.writeTo(
                prefs,
                PlayerInputDefaults.applyGestureModePreset(current, mode),
            )
        }
    }

    /** Double-tap-and-hold continuous seek in the seek zones. */
    suspend fun setVideoDoubleTapHoldSeekEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Whole-mapping write (the binding editor's factory reset). Replaces the
     * stored blob verbatim — the editor owns validation (exact-duplicate
     * blocking) and only ever writes full [PlayerInputMap]s.
     */
    suspend fun setVideoInputBindings(map: PlayerInputMap) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.writeTo(it, map) }
    }

    /**
     * Read-modify-write over the stored blob with [transform] applied INSIDE
     * the edit (the current blob is decoded from the same [prefs] snapshot
     * the write lands on — the atomicity [setVideoGestureMode]'s preset
     * write has). A mapping flip from the player and an editor write can
     * therefore never interleave two reads and lose one flip. An unchanged
     * candidate writes nothing.
     */
    suspend fun updateVideoInputBindings(transform: (PlayerInputMap) -> PlayerInputMap) {
        dataStore.edit { prefs ->
            val current = VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.readFrom(prefs)
            val candidate = transform(current)
            if (candidate != current) {
                VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.writeTo(prefs, candidate)
            }
        }
    }

    suspend fun setVideoPassOutProtectionHours(hours: Int) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.writeTo(it, hours.coerceAtLeast(0)) }
    }

    suspend fun setVideoSkipBackOnResumeMs(ms: Long) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.writeTo(it, ms.coerceAtLeast(0L)) }
    }

    suspend fun setVideoHoldSpeedEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setVideoHoldSpeedMultiplier(multiplier: Float) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.writeTo(it, multiplier) }
    }

    suspend fun setVideoDefaultSpeed(speed: Float) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.writeTo(it, speed) }
    }

    suspend fun setVideoDefaultAspectRatio(ratio: String) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.writeTo(it, ratio) }
    }

    suspend fun setVideoAutoplayNext(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.writeTo(it, enabled) }
    }

    /**
     * Cap on how many episodes autoplay back-to-back (`0` = unlimited); the
     * `0` floor mirrors the row's declared `min`.
     */
    suspend fun setAutoplayEpisodeLimit(limit: Int) {
        dataStore.edit {
            VideoPlayerPreferenceSpecs.AUTOPLAY_EPISODE_LIMIT.writeTo(it, limit.coerceAtLeast(0))
        }
    }

    /** Show the next-episode details card after an episode finishes (default on). */
    suspend fun setShowNextEpisodeDetailsAfterFinish(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH.writeTo(it, enabled) }
    }

    /**
     * The "Still watching?" prompt's trigger arms. The hours arm reuses the
     * `video_pass_out_protection_hours` value — this key picks only WHICH
     * arms are on.
     */
    suspend fun setStillWatchingMode(mode: StillWatchingMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.writeTo(it, mode) }
    }

    /**
     * The episode arm's threshold: consecutive auto-played episodes before
     * the confirm prompt. `0` = off; the picker presets are 2/3/5/8.
     */
    suspend fun setStillWatchingEpisodeThreshold(episodes: Int) {
        dataStore.edit { VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.writeTo(it, episodes.coerceAtLeast(0)) }
    }

    suspend fun setTrailerAutoplay(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.writeTo(it, enabled) }
    }

    suspend fun setCinemaModeEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.writeTo(it, enabled) }
    }

    /** Trailer-then-feature: seed the movie session queue with a trailer when one is available. */
    suspend fun setPlayTrailerBeforeMovie(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.PLAY_TRAILER_BEFORE_MOVIE.writeTo(it, enabled) }
    }

    suspend fun setVideoSwipeSeekMaxMs(ms: Long) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.writeTo(it, ms) }
    }

    suspend fun setVideoRememberBrightness(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.writeTo(it, enabled) }
    }

    suspend fun setVideoBrightnessLevel(level: Float) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.writeTo(it, level) }
    }

    suspend fun setVideoAutoSkipIntro(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.writeTo(it, enabled) }
    }

    suspend fun setVideoAutoSkipOutro(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.writeTo(it, enabled) }
    }

    suspend fun setVideoRememberMuted(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.writeTo(it, enabled) }
    }

    suspend fun setVideoMuted(muted: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_MUTED.writeTo(it, muted) }
    }

    suspend fun setVideoGestureIndicatorSide(side: GestureIndicatorSide) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.writeTo(it, side) }
    }

    suspend fun setTrickplayEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setTrickplayOnSeekGesture(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.writeTo(it, enabled) }
    }

    /** The trickplay preview's presentation: bubble, filmstrip or fullscreen. */
    suspend fun setTrickplayMode(mode: TrickplayMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.TRICKPLAY_MODE.writeTo(it, mode) }
    }

    /** What replaces the video after a long pause (off / clock / logo / fade to black). */
    suspend fun setPauseScreensaverMode(mode: PauseScreensaverMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_MODE.writeTo(it, mode) }
    }

    /**
     * Minutes of pause before the pause screensaver arms. Clamped to the
     * row's declared 1–60 bounds (the setter is the clamp owner).
     */
    suspend fun setPauseScreensaverAfterMinutes(minutes: Int) {
        dataStore.edit {
            VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES.writeTo(it, minutes.coerceIn(VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES_BOUNDS))
        }
    }

    /** Whether the pause surface shows item details. */
    suspend fun setPauseDetailsEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.PAUSE_DETAILS_ENABLED.writeTo(it, enabled) }
    }

    /** Ambient blur: the paused frame blurs behind the pause surface. Default off. */
    suspend fun setAmbientBlurEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.AMBIENT_BLUR_ENABLED.writeTo(it, enabled) }
    }

    /** How bitmap subtitle (PGS/VobSub) planes scale onto the frame. Default FIT. */
    suspend fun setBitmapSubtitleScaleMode(mode: BitmapSubtitleScaleMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.BITMAP_SUBTITLE_SCALE_MODE.writeTo(it, mode) }
    }

    /** Seconds before the current item ends that the "Next up" button appears; clamped to 5–120. */
    suspend fun setNextUpButtonPrerollSec(seconds: Int) {
        dataStore.edit {
            VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC.writeTo(it, seconds.coerceIn(VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC_BOUNDS))
        }
    }

    /** mpv `tone-mapping` curve family (HDR→SDR); AUTO writes mpv's `auto` default. */
    suspend fun setMpvToneMappingCurve(curve: MpvToneMappingCurve) {
        dataStore.edit { VideoPlayerPreferenceSpecs.MPV_TONE_MAPPING_CURVE.writeTo(it, curve) }
    }

    /** mpv deband strength (OFF / LIGHT / MEDIUM / HEAVY). */
    suspend fun setMpvDebandMode(mode: MpvDebandMode) {
        dataStore.edit { VideoPlayerPreferenceSpecs.MPV_DEBAND_MODE.writeTo(it, mode) }
    }

    /** mpv motion interpolation (`interpolation=yes`). */
    suspend fun setMpvMotionInterpolation(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.MPV_MOTION_INTERPOLATION.writeTo(it, enabled) }
    }

    /** Raw `input.conf`-style key bindings text for mpv; empty applies nothing. */
    suspend fun setMpvInputConf(conf: String) {
        dataStore.edit { VideoPlayerPreferenceSpecs.MPV_INPUT_CONF.writeTo(it, conf) }
    }

    /** Custom on-screen button definitions; empty applies nothing. */
    suspend fun setMpvCustomButtons(buttons: String) {
        dataStore.edit { VideoPlayerPreferenceSpecs.MPV_CUSTOM_BUTTONS.writeTo(it, buttons) }
    }

    suspend fun setVideoEpisodeBrowserEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.writeTo(it, enabled) }
    }

    suspend fun setVideoShowPlaybackMetadata(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.writeTo(it, enabled) }
    }

    suspend fun setVideoPreloadBufferSize(size: PreloadBufferSize) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.writeTo(it, size) }
    }

    /** Sibling of [com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore.setAudioCacheSizeMb]. */
    suspend fun setVideoCacheSizeMb(sizeMb: Int) {
        dataStore.edit { VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.writeTo(it, sizeMb) }
    }

    suspend fun setShowClockInPlayer(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.writeTo(it, enabled) }
    }

    suspend fun setShowTimeRemaining(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.writeTo(it, enabled) }
    }

    suspend fun setTvZoomModePercent(percent: Float) {
        dataStore.edit { VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.writeTo(it, percent) }
    }

    suspend fun setIncognitoModeEnabled(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.writeTo(it, enabled) }
    }

    /**
     * Skip-on-forward-seek: when on, a user-initiated seek that lands
     * strictly inside an AUTO_SKIP segment is pulled to the segment's end
     * (with a "Skipped …" confirmation). Internal, app-driven seeks are never
     * affected — the gate rides the ViewModel's `seekTo(userInitiated)`.
     */
    suspend fun setSkipSegmentsOnSeek(enabled: Boolean) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.writeTo(it, enabled) }
    }

    suspend fun setSegmentBehaviors(behaviors: Map<MediaSegmentType, SegmentBehavior>) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.writeTo(it, behaviors) }
    }

    /**
     * Updates a single segment type's behaviour, read-modify-writing the stored
     * map (merged over the defaults + legacy fallback) so callers don't have to
     * reconstruct the whole map. Segment types are a bounded enum set, so the
     * hand-written read-modify-write stays (a whole-map encoding cannot express
     * the merge-then-update step).
     */
    suspend fun setSegmentBehavior(type: MediaSegmentType, behavior: SegmentBehavior) {
        dataStore.edit { prefs ->
            val current = VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.readFrom(prefs).toMutableMap()
            current[type] = behavior
            VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.writeTo(prefs, current)
        }
    }

    suspend fun setSegmentSkipDelays(delays: Map<MediaSegmentType, Long>) {
        dataStore.edit { VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.writeTo(it, delays) }
    }

    /**
     * Updates a single segment type's auto-skip delay, read-modify-writing the
     * stored map (merged over the all-zero defaults) so callers don't have to
     * reconstruct the whole map — the [setSegmentBehavior] precedent.
     */
    suspend fun setSegmentSkipDelay(type: MediaSegmentType, delayMs: Long) {
        dataStore.edit { prefs ->
            val current = VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.readFrom(prefs).toMutableMap()
            current[type] = delayMs
            VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.writeTo(prefs, current)
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
     * [category] — the [VideoPlayerPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every in-player key
     * owned here descends under `PreferenceResetCategory.PLAYBACK`, matching
     * the facade's `resetCategoryKeys`.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        VideoPlayerPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with), plus the one hand-written line below — clearing the superseded
     * legacy gestures boolean.
     */
    suspend fun restore(slice: VideoPlayerSlice) {
        dataStore.edit { prefs ->
            VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.writeTo(prefs, slice.videoSeekDurationMs)
            VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.writeTo(prefs, slice.videoControlsTimeoutMs)
            VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS.writeTo(prefs, slice.skipPromptDurationMs.coerceIn(VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS_BOUNDS))
            VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.writeTo(prefs, slice.videoHideOsdOnPause)
            VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.writeTo(prefs, slice.videoResumeOnHeadsetPlug)
            VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.writeTo(prefs, slice.videoDefaultOrientation)
            VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.writeTo(prefs, slice.videoDefaultAspectRatio)
            VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.writeTo(prefs, slice.videoGestureMode)
            VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.writeTo(prefs, slice.videoDoubleTapHoldSeekEnabled)
            VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.writeTo(prefs, slice.videoInputBindings)
            // The legacy boolean is superseded by the mode key; clear it so a
            // restored snapshot cannot disagree with what the VIDEO_GESTURE_MODE
            // row's read would fall back to if the mode key were ever lost.
            prefs.remove(Keys.VIDEO_GESTURES_ENABLED)
            VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.writeTo(prefs, slice.videoPassOutProtectionHours)
            VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.writeTo(prefs, slice.videoSkipBackOnResumeMs)
            VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.writeTo(prefs, slice.videoHoldSpeedEnabled)
            VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.writeTo(prefs, slice.videoHoldSpeedMultiplier)
            VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.writeTo(prefs, slice.videoDefaultSpeed)
            VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.writeTo(prefs, slice.videoAutoplayNext)
            VideoPlayerPreferenceSpecs.AUTOPLAY_EPISODE_LIMIT.writeTo(prefs, slice.autoplayEpisodeLimit)
            VideoPlayerPreferenceSpecs.SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH.writeTo(
                prefs,
                slice.showNextEpisodeDetailsAfterFinish,
            )
            VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.writeTo(prefs, slice.stillWatchingMode)
            VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.writeTo(prefs, slice.stillWatchingEpisodeThreshold)
            VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.writeTo(prefs, slice.trailerAutoplay)
            VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.writeTo(prefs, slice.cinemaModeEnabled)
            VideoPlayerPreferenceSpecs.PLAY_TRAILER_BEFORE_MOVIE.writeTo(prefs, slice.playTrailerBeforeMovie)
            VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.writeTo(prefs, slice.videoSwipeSeekMaxMs)
            VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.writeTo(prefs, slice.videoRememberBrightness)
            VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.writeTo(prefs, slice.videoBrightnessLevel)
            VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.writeTo(prefs, slice.videoAutoSkipIntro)
            VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.writeTo(prefs, slice.videoAutoSkipOutro)
            VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.writeTo(prefs, slice.videoRememberMuted)
            VideoPlayerPreferenceSpecs.VIDEO_MUTED.writeTo(prefs, slice.videoMuted)
            VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.writeTo(prefs, slice.videoGestureIndicatorSide)
            VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.writeTo(prefs, slice.trickplayEnabled)
            VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.writeTo(prefs, slice.trickplayOnSeekGesture)
            VideoPlayerPreferenceSpecs.TRICKPLAY_MODE.writeTo(prefs, slice.trickplayMode)
            VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.writeTo(prefs, slice.segmentBehaviors)
            VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.writeTo(prefs, slice.segmentSkipDelaysMs)
            VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.writeTo(prefs, slice.videoEpisodeBrowserEnabled)
            VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.writeTo(prefs, slice.videoShowPlaybackMetadata)
            VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.writeTo(prefs, slice.videoPreloadBufferSize)
            VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.writeTo(prefs, slice.videoCacheSizeMb)
            VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.writeTo(prefs, slice.showClockInPlayer)
            VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.writeTo(prefs, slice.showTimeRemaining)
            VideoPlayerPreferenceSpecs.AMBIENT_BLUR_ENABLED.writeTo(prefs, slice.ambientBlurEnabled)
            VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.writeTo(prefs, slice.tvZoomModePercent)
            VideoPlayerPreferenceSpecs.BITMAP_SUBTITLE_SCALE_MODE.writeTo(prefs, slice.bitmapSubtitleScaleMode)
            VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.writeTo(prefs, slice.incognitoModeEnabled)
            VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_MODE.writeTo(prefs, slice.pauseScreensaverMode)
            VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES.writeTo(
                prefs,
                slice.pauseScreensaverAfterMinutes.coerceIn(VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES_BOUNDS),
            )
            VideoPlayerPreferenceSpecs.PAUSE_DETAILS_ENABLED.writeTo(prefs, slice.pauseDetailsEnabled)
            VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.writeTo(prefs, slice.skipSegmentsOnSeek)
            VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC.writeTo(
                prefs,
                slice.nextUpButtonPrerollSec.coerceIn(VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC_BOUNDS),
            )
            VideoPlayerPreferenceSpecs.MPV_TONE_MAPPING_CURVE.writeTo(prefs, slice.mpvToneMappingCurve)
            VideoPlayerPreferenceSpecs.MPV_DEBAND_MODE.writeTo(prefs, slice.mpvDebandMode)
            VideoPlayerPreferenceSpecs.MPV_MOTION_INTERPOLATION.writeTo(prefs, slice.mpvMotionInterpolation)
            VideoPlayerPreferenceSpecs.MPV_INPUT_CONF.writeTo(prefs, slice.mpvInputConf)
            VideoPlayerPreferenceSpecs.MPV_CUSTOM_BUTTONS.writeTo(prefs, slice.mpvCustomButtons)
        }
    }
}

/**
 * The in-player video preference slice. Plain data class (Compose-free) so the
 * datastore module stays framework-light. Every default IS its
 * [VideoPlayerPreferenceSpecs] row's declared default — read off the row via a
 * plain accessor, so a preference's default is stated exactly once, in its
 * spec, and cannot drift between the spec (what an absent key reads) and the
 * slice (what a fresh in-memory instance serves). The legacy-only rows
 * (`video_gestures_enabled`, the four string-keyed skip booleans) have no
 * slice property by design. The spec-default equality is pinned field-for-field
 * by `VideoPlayerSpecDerivationTest`.
 */
@Immutable
@Serializable
data class VideoPlayerSlice(
    val videoSeekDurationMs: Long = VideoPlayerPreferenceSpecs.VIDEO_SEEK_DURATION_MS.default,
    val videoControlsTimeoutMs: Long = VideoPlayerPreferenceSpecs.VIDEO_CONTROLS_TIMEOUT_MS.default,
    val skipPromptDurationMs: Long = VideoPlayerPreferenceSpecs.SKIP_PROMPT_DURATION_MS.default,
    /**
     * Pausing must not summon the control
     * overlay. Default **off** — today's show-on-pause behavior; on, only the
     * SUMMONS is suppressed (an already-visible overlay keeps its auto-hide).
     */
    val videoHideOsdOnPause: Boolean = VideoPlayerPreferenceSpecs.VIDEO_HIDE_OSD_ON_PAUSE.default,
    /**
     * Resume when headphones reconnect after the becoming-noisy
     * auto-pause. Default **off** — opt-in; the decision (paused-by-the-event
     * marker + freshness window) lives in core:data's `HeadsetResumePolicy`.
     */
    val videoResumeOnHeadsetPlug: Boolean = VideoPlayerPreferenceSpecs.VIDEO_RESUME_ON_HEADSET_PLUG.default,
    val videoDefaultOrientation: OrientationMode = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ORIENTATION.default,
    val videoDefaultAspectRatio: String = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_ASPECT_RATIO.default,
    val videoGestureMode: GestureMode = VideoPlayerPreferenceSpecs.VIDEO_GESTURE_MODE.default,
    /**
     * double-tap-and-hold continuous seek in the seek zones.
     * Default ON; off restores long-press = hold-speed everywhere.
     */
    val videoDoubleTapHoldSeekEnabled: Boolean = VideoPlayerPreferenceSpecs.VIDEO_DOUBLE_TAP_HOLD_SEEK_ENABLED.default,
    /**
     * The whole player input mapping (touch / wheel / keyboard / D-pad rows).
     * Default = the factory mapping gated by the legacy gesture config —
     * absent blob reads derive it, so this default only serves fresh
     * in-memory slices (and the factory default is the ALL-mode mapping).
     */
    val videoInputBindings: PlayerInputMap = VideoPlayerPreferenceSpecs.VIDEO_INPUT_BINDINGS.default,
    val videoPassOutProtectionHours: Int = VideoPlayerPreferenceSpecs.VIDEO_PASS_OUT_PROTECTION_HOURS.default,
    val videoSkipBackOnResumeMs: Long = VideoPlayerPreferenceSpecs.VIDEO_SKIP_BACK_ON_RESUME_MS.default,
    val videoHoldSpeedEnabled: Boolean = VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_ENABLED.default,
    val videoHoldSpeedMultiplier: Float = VideoPlayerPreferenceSpecs.VIDEO_HOLD_SPEED_MULTIPLIER.default,
    val videoDefaultSpeed: Float = VideoPlayerPreferenceSpecs.VIDEO_DEFAULT_SPEED.default,
    val videoAutoplayNext: Boolean = VideoPlayerPreferenceSpecs.VIDEO_AUTOPLAY_NEXT.default,
    /** Back-to-back autoplay cap; `0` = unlimited. */
    val autoplayEpisodeLimit: Int = VideoPlayerPreferenceSpecs.AUTOPLAY_EPISODE_LIMIT.default,
    /** Show the next-episode details card after an episode finishes. Default on. */
    val showNextEpisodeDetailsAfterFinish: Boolean =
        VideoPlayerPreferenceSpecs.SHOW_NEXT_EPISODE_DETAILS_AFTER_FINISH.default,
    /**
     * "Still watching?" trigger arms. Default **off**: the confirm prompt is
     * a behavior change the user must opt into (see [StillWatchingMode]).
     */
    val stillWatchingMode: StillWatchingMode = VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.default,
    /** Episode arm's threshold — consecutive auto-plays before the prompt; 0 = off. */
    val stillWatchingEpisodeThreshold: Int = VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.default,
    val trailerAutoplay: Boolean = VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.default,
    val cinemaModeEnabled: Boolean = VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.default,
    val playTrailerBeforeMovie: Boolean = VideoPlayerPreferenceSpecs.PLAY_TRAILER_BEFORE_MOVIE.default,
    val videoSwipeSeekMaxMs: Long = VideoPlayerPreferenceSpecs.VIDEO_SWIPE_SEEK_MAX_MS.default,
    val videoRememberBrightness: Boolean = VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_BRIGHTNESS.default,
    val videoBrightnessLevel: Float = VideoPlayerPreferenceSpecs.VIDEO_BRIGHTNESS_LEVEL.default,
    val videoAutoSkipIntro: Boolean = VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_INTRO.default,
    val videoAutoSkipOutro: Boolean = VideoPlayerPreferenceSpecs.VIDEO_AUTO_SKIP_OUTRO.default,
    val videoRememberMuted: Boolean = VideoPlayerPreferenceSpecs.VIDEO_REMEMBER_MUTED.default,
    val videoMuted: Boolean = VideoPlayerPreferenceSpecs.VIDEO_MUTED.default,
    val videoGestureIndicatorSide: GestureIndicatorSide = VideoPlayerPreferenceSpecs.VIDEO_GESTURE_INDICATOR_SIDE.default,
    val trickplayEnabled: Boolean = VideoPlayerPreferenceSpecs.TRICKPLAY_ENABLED.default,
    val trickplayOnSeekGesture: Boolean = VideoPlayerPreferenceSpecs.TRICKPLAY_ON_SEEK_GESTURE.default,
    /** The trickplay preview's presentation: bubble, filmstrip or fullscreen. */
    val trickplayMode: TrickplayMode = VideoPlayerPreferenceSpecs.TRICKPLAY_MODE.default,
    val videoEpisodeBrowserEnabled: Boolean = VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.default,
    val videoShowPlaybackMetadata: Boolean = VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.default,
    val videoPreloadBufferSize: PreloadBufferSize = VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.default,
    /** Direct-play byte-cache cap in MB (see [Keys.VIDEO_CACHE_SIZE_MB]). */
    val videoCacheSizeMb: Int = VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.default,
    val showClockInPlayer: Boolean = VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.default,
    val showTimeRemaining: Boolean = VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.default,
    /** Ambient blur: the paused frame blurs behind the pause surface. Default off. */
    val ambientBlurEnabled: Boolean = VideoPlayerPreferenceSpecs.AMBIENT_BLUR_ENABLED.default,
    val tvZoomModePercent: Float = VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.default,
    /** How bitmap subtitle (PGS/VobSub) planes scale onto the frame. Default FIT. */
    val bitmapSubtitleScaleMode: BitmapSubtitleScaleMode = VideoPlayerPreferenceSpecs.BITMAP_SUBTITLE_SCALE_MODE.default,
    val incognitoModeEnabled: Boolean = VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.default,
    /** What replaces the video after a long pause. Default OFF (keep the paused frame). */
    val pauseScreensaverMode: PauseScreensaverMode = VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_MODE.default,
    /** Minutes of pause before the pause screensaver arms (1–60). */
    val pauseScreensaverAfterMinutes: Int = VideoPlayerPreferenceSpecs.PAUSE_SCREENSAVER_AFTER_MINUTES.default,
    /** Whether the pause surface shows item details. Default off. */
    val pauseDetailsEnabled: Boolean = VideoPlayerPreferenceSpecs.PAUSE_DETAILS_ENABLED.default,
    val segmentBehaviors: Map<MediaSegmentType, SegmentBehavior> = VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.default,
    val segmentSkipDelaysMs: Map<MediaSegmentType, Long> = VideoPlayerPreferenceSpecs.SEGMENT_SKIP_DELAYS_MS.default,
    /**
     * Skip-on-forward-seek. Default **off**: opt-in, because silently
     * remapping a user's seek target is a behavior change they must ask for.
     */
    val skipSegmentsOnSeek: Boolean = VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.default,
    /** Seconds before item end that the "Next up" button appears (5–120). */
    val nextUpButtonPrerollSec: Int = VideoPlayerPreferenceSpecs.NEXT_UP_BUTTON_PREROLL_SEC.default,
    /** mpv `tone-mapping` curve family (HDR→SDR). Default AUTO. */
    val mpvToneMappingCurve: MpvToneMappingCurve = VideoPlayerPreferenceSpecs.MPV_TONE_MAPPING_CURVE.default,
    /** mpv deband strength. Default OFF. */
    val mpvDebandMode: MpvDebandMode = VideoPlayerPreferenceSpecs.MPV_DEBAND_MODE.default,
    /** mpv motion interpolation. Default off. */
    val mpvMotionInterpolation: Boolean = VideoPlayerPreferenceSpecs.MPV_MOTION_INTERPOLATION.default,
    /** Raw `input.conf`-style key bindings text; empty applies nothing. */
    val mpvInputConf: String = VideoPlayerPreferenceSpecs.MPV_INPUT_CONF.default,
    /** Custom on-screen button definitions; empty applies nothing. */
    val mpvCustomButtons: String = VideoPlayerPreferenceSpecs.MPV_CUSTOM_BUTTONS.default,
)
