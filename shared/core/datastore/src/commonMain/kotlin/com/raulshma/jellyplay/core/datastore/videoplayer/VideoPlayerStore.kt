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
 * **Stage B spec derivation** (the [PlaybackStore] precedent): every key this
 * store owns is declared exactly once as a row in [VideoPlayerPreferenceSpecs]
 * (wire name, default, reset category, read/write encoding) and the machinery
 * below is derived from those rows — each [Keys] member rebuilds its row's
 * typed key from the row's wire name, each [read] projection row delegates to
 * its row encoding, the single-key setters and [restore] delegate to the rows'
 * derived writes, and [resetKeysFor] filters the rows by reset category. The
 * migration semantics (the `video_gestures_enabled` → [GestureMode] fallback,
 * the segment-behaviour four-boolean fallback, the legacy string-key reads)
 * live in the rows' encodings now, next to the key they apply to.
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
        val VIDEO_EPISODE_BROWSER_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.keyName)
        val VIDEO_SHOW_PLAYBACK_METADATA = booleanPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.keyName)
        val VIDEO_PRELOAD_BUFFER_SIZE = stringPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.keyName)
        val VIDEO_CACHE_SIZE_MB = intPreferencesKey(VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.keyName)
        val SHOW_CLOCK_IN_PLAYER = booleanPreferencesKey(VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.keyName)
        val SHOW_TIME_REMAINING = booleanPreferencesKey(VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.keyName)
        val TV_ZOOM_MODE_PERCENT = floatPreferencesKey(VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.keyName)
        val INCOGNITO_MODE_ENABLED = booleanPreferencesKey(VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.keyName)

        val SEGMENT_BEHAVIORS = stringPreferencesKey(VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.keyName)
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
        stillWatchingMode = VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.readFrom(prefs),
        stillWatchingEpisodeThreshold = VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.readFrom(prefs),
        trailerAutoplay = VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.readFrom(prefs),
        cinemaModeEnabled = VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.readFrom(prefs),
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
        videoEpisodeBrowserEnabled = VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.readFrom(prefs),
        videoShowPlaybackMetadata = VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.readFrom(prefs),
        videoPreloadBufferSize = VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.readFrom(prefs),
        videoCacheSizeMb = VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.readFrom(prefs),
        showClockInPlayer = VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.readFrom(prefs),
        showTimeRemaining = VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.readFrom(prefs),
        tvZoomModePercent = VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.readFrom(prefs),
        incognitoModeEnabled = VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.readFrom(prefs),
        segmentBehaviors = VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.readFrom(prefs),
        skipSegmentsOnSeek = VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.readFrom(prefs),
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
            VideoPlayerPreferenceSpecs.STILL_WATCHING_MODE.writeTo(prefs, slice.stillWatchingMode)
            VideoPlayerPreferenceSpecs.STILL_WATCHING_EPISODE_THRESHOLD.writeTo(prefs, slice.stillWatchingEpisodeThreshold)
            VideoPlayerPreferenceSpecs.TRAILER_AUTOPLAY.writeTo(prefs, slice.trailerAutoplay)
            VideoPlayerPreferenceSpecs.CINEMA_MODE_ENABLED.writeTo(prefs, slice.cinemaModeEnabled)
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
            VideoPlayerPreferenceSpecs.SEGMENT_BEHAVIORS.writeTo(prefs, slice.segmentBehaviors)
            VideoPlayerPreferenceSpecs.VIDEO_EPISODE_BROWSER_ENABLED.writeTo(prefs, slice.videoEpisodeBrowserEnabled)
            VideoPlayerPreferenceSpecs.VIDEO_SHOW_PLAYBACK_METADATA.writeTo(prefs, slice.videoShowPlaybackMetadata)
            VideoPlayerPreferenceSpecs.VIDEO_PRELOAD_BUFFER_SIZE.writeTo(prefs, slice.videoPreloadBufferSize)
            VideoPlayerPreferenceSpecs.VIDEO_CACHE_SIZE_MB.writeTo(prefs, slice.videoCacheSizeMb)
            VideoPlayerPreferenceSpecs.SHOW_CLOCK_IN_PLAYER.writeTo(prefs, slice.showClockInPlayer)
            VideoPlayerPreferenceSpecs.SHOW_TIME_REMAINING.writeTo(prefs, slice.showTimeRemaining)
            VideoPlayerPreferenceSpecs.TV_ZOOM_MODE_PERCENT.writeTo(prefs, slice.tvZoomModePercent)
            VideoPlayerPreferenceSpecs.INCOGNITO_MODE_ENABLED.writeTo(prefs, slice.incognitoModeEnabled)
            VideoPlayerPreferenceSpecs.SKIP_SEGMENTS_ON_SEEK.writeTo(prefs, slice.skipSegmentsOnSeek)
        }
    }
}

/**
 * The in-player video preference slice. Plain data class (Compose-free) so the
 * datastore module stays framework-light. Defaults mirror the projection
 * defaults in [VideoPlayerStore.read] (declared on the
 * [VideoPlayerPreferenceSpecs] rows).
 */
@Immutable
@Serializable
data class VideoPlayerSlice(
    val videoSeekDurationMs: Long = 10_000L,
    val videoControlsTimeoutMs: Long = 5_000L,
    /**
     * (jellyfin-androidtv #3924): pausing must not summon the control
     * overlay. Default **off** — today's show-on-pause behavior; on, only the
     * SUMMONS is suppressed (an already-visible overlay keeps its auto-hide).
     */
    val videoHideOsdOnPause: Boolean = false,
    /**
     * Resume when headphones reconnect after the becoming-noisy
     * auto-pause. Default **off** — opt-in; the decision (paused-by-the-event
     * marker + freshness window) lives in core:data's `HeadsetResumePolicy`.
     */
    val videoResumeOnHeadsetPlug: Boolean = false,
    val videoDefaultOrientation: OrientationMode = OrientationMode.SENSOR_LANDSCAPE,
    val videoDefaultAspectRatio: String = "AUTO",
    val videoGestureMode: GestureMode = GestureMode.ALL,
    /**
     * double-tap-and-hold continuous seek in the seek zones.
     * Default ON; off restores long-press = hold-speed everywhere.
     */
    val videoDoubleTapHoldSeekEnabled: Boolean = true,
    /**
     * The whole player input mapping (touch / wheel / keyboard / D-pad rows).
     * Default = the factory mapping gated by the legacy gesture config —
     * absent blob reads derive it, so this default only serves fresh
     * in-memory slices (and the factory default is the ALL-mode mapping).
     */
    val videoInputBindings: PlayerInputMap = PlayerInputDefaults.defaultMap(),
    val videoPassOutProtectionHours: Int = 0,
    val videoSkipBackOnResumeMs: Long = 0L,
    val videoHoldSpeedEnabled: Boolean = true,
    val videoHoldSpeedMultiplier: Float = 2.0f,
    val videoDefaultSpeed: Float = 1.0f,
    val videoAutoplayNext: Boolean = true,
    /**
     * "Still watching?" trigger arms. Default **off**: the confirm prompt is
     * a behavior change the user must opt into (see [StillWatchingMode]).
     */
    val stillWatchingMode: StillWatchingMode = StillWatchingMode.OFF,
    /** Episode arm's threshold — consecutive auto-plays before the prompt; 0 = off. */
    val stillWatchingEpisodeThreshold: Int = 0,
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
