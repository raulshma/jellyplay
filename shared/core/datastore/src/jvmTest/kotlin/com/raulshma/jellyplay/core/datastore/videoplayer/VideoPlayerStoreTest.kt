package com.raulshma.jellyplay.core.datastore.videoplayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.raulshma.jellyplay.core.datastore.TestDataStoreProvider
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PreloadBufferSize
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StillWatchingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Exercises the in-player video preference store, focusing on the
 * segment-behaviour legacy migration and bounds coercions that previously lived
 * inline in the `UserPreferencesStore` god object with **no** unit coverage.
 */
class VideoPlayerStoreTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var store: VideoPlayerStore
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeTest
    fun setup() {
        runBlocking {
            // Robolectric reuses the same DataStore file across tests; start clean.
            dataStore = TestDataStoreProvider.get()
            dataStore.edit { it.clear() }
            store = VideoPlayerStore(dataStore, scope)
            // Drain the Eagerly-cached slice so the cleared state is observed
            // before each test writes + reads.
            store.videoPlayer.first()
        }
    }

    @Test
    fun `defaults when empty`() = runTest {
        val slice = store.videoPlayer.first()
        assertEquals(10_000L, slice.videoSeekDurationMs)
        assertEquals(5_000L, slice.videoControlsTimeoutMs)
        assertEquals(120_000L, slice.videoSwipeSeekMaxMs)
        assertEquals(0.5f, slice.videoBrightnessLevel)
        assertEquals(1.0f, slice.videoDefaultSpeed)
        assertEquals(SegmentBehavior.DEFAULT_BEHAVIORS, slice.segmentBehaviors)
        assertTrue(slice.videoGestureMode == GestureMode.ALL)
        assertTrue(slice.videoAutoplayNext)
        assertFalse(slice.incognitoModeEnabled)
    }

    @Test
    fun `setSegmentBehaviors round-trips`() = runTest {
        val custom = SegmentBehavior.DEFAULT_BEHAVIORS + (
            MediaSegmentType.INTRO to SegmentBehavior.IGNORE
        )
        store.setSegmentBehaviors(custom)
        val slice = store.videoPlayer.first()
        assertEquals(SegmentBehavior.IGNORE, slice.segmentBehaviors[MediaSegmentType.INTRO])
        // Untouched types keep their default behaviour after the merge.
        assertEquals(SegmentBehavior.DEFAULT_BEHAVIORS[MediaSegmentType.COMMERCIAL], slice.segmentBehaviors[MediaSegmentType.COMMERCIAL])
    }

    @Test
    fun `legacy skip_intro_enabled + auto_skip_intro booleans migrate into segment behaviors`() = runTest {
        // Pre-blob install: the JSON `segment_behaviors` blob is absent, so the
        // four legacy booleans feed the INTRO/OUTRO SegmentBehaviors.
        dataStore.edit {
            // Legacy keys are stored as strings in the god object; emulate that
            // shape so the toBoolean() fallback path is exercised.
            it[stringLegacyKey("skip_intro_enabled")] = "true"
            it[stringLegacyKey("auto_skip_intro")] = "true"
        }
        val slice = store.videoPlayer.first()
        // auto_skip_intro=true wins over skip_intro_enabled=true → AUTO_SKIP.
        assertEquals(SegmentBehavior.AUTO_SKIP, slice.segmentBehaviors[MediaSegmentType.INTRO])
        // No outro legacy key written: skip_outro default true → SHOW_BUTTON.
        assertEquals(SegmentBehavior.SHOW_BUTTON, slice.segmentBehaviors[MediaSegmentType.OUTRO])
    }

    @Test
    fun `setVideoBrightnessLevel round-trips`() = runTest {
        store.setVideoBrightnessLevel(0.75f)
        assertEquals(0.75f, store.videoPlayer.first().videoBrightnessLevel)
    }

    @Test
    fun `setVideoGestureMode round-trips`() = runTest {
        store.setVideoGestureMode(GestureMode.TAP_ONLY)
        assertEquals(GestureMode.TAP_ONLY, store.videoPlayer.first().videoGestureMode)
        store.setVideoGestureMode(GestureMode.NONE)
        assertEquals(GestureMode.NONE, store.videoPlayer.first().videoGestureMode)
        store.setVideoGestureMode(GestureMode.ALL)
        assertEquals(GestureMode.ALL, store.videoPlayer.first().videoGestureMode)
    }

    @Test
    fun `double tap hold seek defaults on and round-trips`() = runTest {
        assertTrue(store.videoPlayer.first().videoDoubleTapHoldSeekEnabled)
        store.setVideoDoubleTapHoldSeekEnabled(false)
        assertFalse(store.videoPlayer.first().videoDoubleTapHoldSeekEnabled)
        store.setVideoDoubleTapHoldSeekEnabled(true)
        assertTrue(store.videoPlayer.first().videoDoubleTapHoldSeekEnabled)
    }

    @Test
    fun `hide osd on pause defaults off and round-trips`() = runTest {
        // Default OFF — today's show-on-pause behavior is untouched.
        assertFalse(store.videoPlayer.first().videoHideOsdOnPause)
        store.setVideoHideOsdOnPause(true)
        assertTrue(store.videoPlayer.first().videoHideOsdOnPause)
        store.setVideoHideOsdOnPause(false)
        assertFalse(store.videoPlayer.first().videoHideOsdOnPause)
    }

    @Test
    fun `resume on headset plug defaults off and round-trips`() = runTest {
        // Default OFF — the resume is opt-in.
        assertFalse(store.videoPlayer.first().videoResumeOnHeadsetPlug)
        store.setVideoResumeOnHeadsetPlug(true)
        assertTrue(store.videoPlayer.first().videoResumeOnHeadsetPlug)
        store.setVideoResumeOnHeadsetPlug(false)
        assertFalse(store.videoPlayer.first().videoResumeOnHeadsetPlug)
    }

    @Test
    fun `legacy video_gestures_enabled false migrates to NONE`() = runTest {
        // Pre-mode install with gestures disabled: no `video_gesture_mode` key,
        // so readGestureMode falls back to the legacy boolean.
        dataStore.edit {
            it[androidx.datastore.preferences.core.booleanPreferencesKey("video_gestures_enabled")] = false
        }
        assertEquals(GestureMode.NONE, store.videoPlayer.first().videoGestureMode)
    }

    @Test
    fun `legacy video_gestures_enabled true migrates to ALL`() = runTest {
        dataStore.edit {
            it[androidx.datastore.preferences.core.booleanPreferencesKey("video_gestures_enabled")] = true
        }
        assertEquals(GestureMode.ALL, store.videoPlayer.first().videoGestureMode)
    }

    @Test
    fun `video_gesture_mode key wins over legacy boolean`() = runTest {
        dataStore.edit {
            it[androidx.datastore.preferences.core.booleanPreferencesKey("video_gestures_enabled")] = false
            it[androidx.datastore.preferences.core.stringPreferencesKey("video_gesture_mode")] = "TAP_ONLY"
        }
        assertEquals(GestureMode.TAP_ONLY, store.videoPlayer.first().videoGestureMode)
    }

    @Test
    fun `corrupt video_gesture_mode falls back to legacy boolean`() = runTest {
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("video_gesture_mode")] = "BOGUS"
        }
        assertEquals(GestureMode.ALL, store.videoPlayer.first().videoGestureMode)
    }

    @Test
    fun `input bindings default to the legacy-derived mapping and round-trip`() = runTest {
        // Fresh store: the blob is absent, so the default read derives the
        // mapping from the ALL-mode legacy config — the full factory map.
        assertEquals(PlayerInputDefaults.defaultMap(), store.videoPlayer.first().videoInputBindings)
        val custom = PlayerInputDefaults.defaultMap().let { map ->
            map.copy(
                bindings = map.bindings.map { binding ->
                    if (binding.id == PlayerInputDefaults.ID_SWIPE_BRIGHTNESS) {
                        binding.copy(enabled = false)
                    } else {
                        binding
                    }
                },
            )
        }
        store.setVideoInputBindings(custom)
        assertEquals(custom, store.videoPlayer.first().videoInputBindings)
    }

    @Test
    fun `updateVideoInputBindings applies the transform to the stored blob inside the edit`() = runTest {
        // Seed a blob, then flip one row through the transform write — the
        // stored blob is read and rewritten in the SAME edit, so a
        // read-modify-write can never interleave with another writer.
        store.setVideoInputBindings(PlayerInputDefaults.defaultMap())
        store.updateVideoInputBindings { it.withBindingEnabled(PlayerInputDefaults.ID_PINCH, false) }
        val stored = store.videoPlayer.first().videoInputBindings
        assertFalse(stored.bindings.first { it.id == PlayerInputDefaults.ID_PINCH }.enabled)
        // Every other row rode along untouched.
        assertEquals(
            PlayerInputDefaults.defaultMap().withBindingEnabled(PlayerInputDefaults.ID_PINCH, false),
            stored,
        )
        // An unchanged candidate writes nothing (the stored map is unchanged).
        store.updateVideoInputBindings { it.withBindingEnabled("no-such-id", false) }
        assertEquals(stored, store.videoPlayer.first().videoInputBindings)
    }

    @Test
    fun `a locked-out stored map is healed on read and persisted by the next mapping write`() = runTest {
        // The broken-editor fallout (issue #171): every summon surface off —
        // the controls can never be shown. The heal rides the read, so the
        // projection is repaired even though the stored blob is not.
        val locked = PlayerInputDefaults.defaultMap()
            .withBindingEnabled(PlayerInputDefaults.ID_TAP, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_LEFT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_EDGE_SWIPE_RIGHT, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_UP, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_DOWN, enabled = false)
            .withBindingEnabled(PlayerInputDefaults.ID_DPAD_SELECT, enabled = false)
        store.setVideoInputBindings(locked)

        val healed = store.videoPlayer.first().videoInputBindings
        assertNotEquals(locked, healed)
        assertTrue(healed.bindings.first { it.pattern == InputPattern.Tap }.enabled)
        // Deliberate narrow choices ride along untouched.
        assertFalse(healed.bindings.first { it.id == PlayerInputDefaults.ID_EDGE_SWIPE_LEFT }.enabled)

        // The RMW verb transforms the HEALED current, so any editor write
        // persists the repair — the identity transform suffices.
        store.updateVideoInputBindings { it }
        assertEquals(healed, store.videoPlayer.first().videoInputBindings)
    }

    @Test
    fun `input bindings migrate from the legacy gesture config when blob absent`() = runTest {
        // Pre-blob install: gesture mode + the two per-behavior switches feed
        // the derived default — the exact prior behavior, in mapping form.
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("video_gesture_mode")] = "TAP_ONLY"
            it[androidx.datastore.preferences.core.booleanPreferencesKey("video_hold_speed_enabled")] = false
            it[androidx.datastore.preferences.core.booleanPreferencesKey("video_double_tap_hold_seek_enabled")] = false
        }
        val migrated = store.videoPlayer.first().videoInputBindings
        // The migrated map IS the factory map built for this exact legacy
        // config — the pre-mapping behavior, in mapping form.
        assertEquals(
            PlayerInputDefaults.defaultMap(
                gestureMode = GestureMode.TAP_ONLY,
                holdSpeedEnabled = false,
                doubleTapHoldSeekEnabled = false,
            ),
            migrated,
        )
        // Sanity: the swipe-tier rows really are off in the migrated map.
        assertTrue(
            migrated.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_VOLUME }.enabled.not(),
        )
        assertTrue(
            migrated.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_BRIGHTNESS }.enabled.not(),
        )
        assertFalse(
            migrated.bindings.first { it.id == PlayerInputDefaults.ID_LONG_PRESS }.enabled,
            "hold-speed pref off ⇒ hold-speed row disabled",
        )
        assertFalse(
            migrated.bindings.first { it.id == PlayerInputDefaults.ID_DOUBLE_TAP_HOLD_LEFT }.enabled,
            "double-tap-hold pref off ⇒ hold row disabled",
        )
        // The wheel/keyboard/D-pad rows are untouched by the touch-tier config.
        assertTrue(
            migrated.bindings.first { it.id == PlayerInputDefaults.ID_WHEEL_VOLUME }.enabled,
        )
    }

    @Test
    fun `setVideoGestureMode applies the preset to the stored mapping`() = runTest {
        // The demoted preset: picking TAP_ONLY mass-disables the swipe-tier
        // rows of the PERSISTED blob in the same edit.
        store.setVideoGestureMode(GestureMode.TAP_ONLY)
        val stored = store.videoPlayer.first().videoInputBindings
        assertTrue(
            stored.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_VOLUME }.enabled.not(),
        )
        assertTrue(
            stored.bindings.first { it.id == PlayerInputDefaults.ID_DOUBLE_TAP_CENTER }.enabled,
        )
        // And back: ALL re-enables the swipe rows, leaving the tap rows alone.
        store.setVideoGestureMode(GestureMode.ALL)
        val restored = store.videoPlayer.first().videoInputBindings
        assertTrue(
            restored.bindings.first { it.id == PlayerInputDefaults.ID_SWIPE_VOLUME }.enabled,
        )
    }

    @Test
    fun `skip_segments_on_seek defaults off and toggles`() = runTest {
        // Opt-in by default — a seek remap must be asked for.
        assertFalse(store.videoPlayer.first().skipSegmentsOnSeek)
        store.setSkipSegmentsOnSeek(true)
        assertTrue(store.videoPlayer.first().skipSegmentsOnSeek)
        store.setSkipSegmentsOnSeek(false)
        assertFalse(store.videoPlayer.first().skipSegmentsOnSeek)
    }

    @Test
    fun `still watching defaults off with zero threshold`() = runTest {
        val slice = store.videoPlayer.first()
        assertEquals(StillWatchingMode.OFF, slice.stillWatchingMode)
        assertEquals(0, slice.stillWatchingEpisodeThreshold)
    }

    @Test
    fun `still watching setters round-trip and coerce`() = runTest {
        store.setStillWatchingMode(StillWatchingMode.BOTH)
        store.setStillWatchingEpisodeThreshold(3)
        val slice = store.videoPlayer.first()
        assertEquals(StillWatchingMode.BOTH, slice.stillWatchingMode)
        assertEquals(3, slice.stillWatchingEpisodeThreshold)

        // Negative thresholds coerce to 0 (= off), mirroring the pass-out hours.
        store.setStillWatchingEpisodeThreshold(-4)
        assertEquals(0, store.videoPlayer.first().stillWatchingEpisodeThreshold)

        // A corrupt/unknown stored enum name falls back to OFF.
        dataStore.edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("still_watching_mode")] = "BOGUS"
        }
        assertEquals(StillWatchingMode.OFF, store.videoPlayer.first().stillWatchingMode)
    }

    @Test
    fun `still watching fields survive restore`() = runTest {
        val slice = VideoPlayerSlice(
            stillWatchingMode = StillWatchingMode.EPISODES,
            stillWatchingEpisodeThreshold = 5,
        )
        store.restore(slice)
        val restored = store.videoPlayer.first()
        assertEquals(StillWatchingMode.EPISODES, restored.stillWatchingMode)
        assertEquals(5, restored.stillWatchingEpisodeThreshold)
    }

    @Test
    fun `restore(slice) round-trips a fully-populated slice`() = runTest {
        val slice = VideoPlayerSlice(
            videoSeekDurationMs = 15_000L,
            videoControlsTimeoutMs = 10_000L,
            videoDefaultOrientation = OrientationMode.LOCKED_LANDSCAPE,
            videoDefaultAspectRatio = "16:9",
            videoGestureMode = GestureMode.TAP_ONLY,
            videoDoubleTapHoldSeekEnabled = false,
            videoHideOsdOnPause = true,
            videoResumeOnHeadsetPlug = true,
            videoPassOutProtectionHours = 24,
            videoSkipBackOnResumeMs = 10_000L,
            videoHoldSpeedEnabled = false,
            videoHoldSpeedMultiplier = 3.0f,
            videoDefaultSpeed = 1.25f,
            videoAutoplayNext = false,
            trailerAutoplay = false,
            cinemaModeEnabled = true,
            videoSwipeSeekMaxMs = 180_000L,
            videoRememberBrightness = false,
            videoBrightnessLevel = 0.8f,
            videoAutoSkipIntro = true,
            videoAutoSkipOutro = true,
            videoRememberMuted = false,
            videoMuted = true,
            videoGestureIndicatorSide = GestureIndicatorSide.SAME,
            trickplayEnabled = false,
            trickplayOnSeekGesture = false,
            videoEpisodeBrowserEnabled = false,
            videoShowPlaybackMetadata = false,
            videoPreloadBufferSize = PreloadBufferSize.HIGH,
            showClockInPlayer = true,
            showTimeRemaining = true,
            tvZoomModePercent = 110f,
            incognitoModeEnabled = true,
            segmentBehaviors = SegmentBehavior.DEFAULT_BEHAVIORS + (MediaSegmentType.INTRO to SegmentBehavior.IGNORE),
            skipSegmentsOnSeek = true,
        )

        store.restore(slice)

        assertEquals(slice, store.videoPlayer.first())
    }

    private fun stringLegacyKey(name: String) =
        androidx.datastore.preferences.core.stringPreferencesKey(name)
}
