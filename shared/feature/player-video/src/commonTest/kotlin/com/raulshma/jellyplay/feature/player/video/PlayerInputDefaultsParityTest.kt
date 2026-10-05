package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.DpadControl
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputKey
import com.raulshma.jellyplay.core.model.SwipeEdge
import com.raulshma.jellyplay.core.model.SwipeSide
import com.raulshma.jellyplay.core.model.TouchZone
import com.raulshma.jellyplay.core.model.WheelAxis
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputPolicy
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_ALT_CTRL_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_CTRL_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_FINE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_HOME_END_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_PAGE_MS
import com.raulshma.jellyplay.feature.player.video.chrome.KEY_SEEK_STEP_SHIFT_CTRL_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Pins the DEFAULT mapping ([PlayerInputDefaults]) — one assertion per input
 * the player handles. The retired decision tables (`mediaKeyAction`, the
 * D-pad branch, `PlayerWheelPolicy`'s shift-seek fold, the swipe arms, the
 * tap zones) became this data (issue #171's generalized input mapping): each
 * row here pins that the retired behavior is exactly what a fresh install
 * resolves, so the mapping can never silently drift from the historical
 * semantics — keyboard AND wheel AND D-pad. Runtime resolution runs through
 * the persisted map (`PlayerInputPolicy`, candidate ladders included); ESC/
 * BACK's controls-visible fold happens at the execution shell (the single
 * BACK_OR_HIDE action), so one row covers both arms.
 *
 * The seek keys' STEP table is [MediaKeySeekTest] below — unchanged, the
 * mapping deliberately does not own the modifier-step semantics.
 */
class PlayerInputDefaultsParityTest {

    private val map = PlayerInputDefaults.defaultMap()

    private fun actionOf(
        key: PlayerInputKey,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
    ): PlayerAction {
        val binding = PlayerInputPolicy.resolve(
            map,
            PlayerInputPolicy.keyCandidates(key, isCtrlPressed = ctrl, isShiftPressed = shift, isAltPressed = alt),
        ) ?: fail("key $key (ctrl=$ctrl shift=$shift alt=$alt) has no default row")
        return binding.action
    }

    @Test
    fun space_andMediaPlayAliases_togglePlayPause() {
        // The media-key aliases (PLAY / PAUSE / PLAY_PAUSE) land on the same
        // action as SPACE.
        for (key in listOf(
            PlayerInputKey.SPACE,
            PlayerInputKey.MEDIA_PLAY,
            PlayerInputKey.MEDIA_PAUSE,
            PlayerInputKey.MEDIA_PLAY_PAUSE,
        )) {
            assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, actionOf(key), "key=$key")
        }
    }

    @Test
    fun k_completesTheJklTrio_togglesPlayPause() {
        assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, actionOf(PlayerInputKey.K))
    }

    @Test
    fun leftBracket_decreasesSubtitleDelay() {
        assertEquals(PlayerAction.SUBTITLE_DELAY_DECREASE, actionOf(PlayerInputKey.BRACKET_LEFT))
    }

    @Test
    fun rightBracket_increasesSubtitleDelay() {
        assertEquals(PlayerAction.SUBTITLE_DELAY_INCREASE, actionOf(PlayerInputKey.BRACKET_RIGHT))
    }

    @Test
    fun ctrlLeftBracket_decreasesAudioDelay() {
        // mpv-style Ctrl+± on the audio delay — the exact-modifier candidate
        // beats the plain bracket row.
        assertEquals(PlayerAction.AUDIO_DELAY_DECREASE, actionOf(PlayerInputKey.BRACKET_LEFT, ctrl = true))
    }

    @Test
    fun ctrlRightBracket_increasesAudioDelay() {
        assertEquals(PlayerAction.AUDIO_DELAY_INCREASE, actionOf(PlayerInputKey.BRACKET_RIGHT, ctrl = true))
    }

    @Test
    fun ctrlBracketCombos_withExtraModifiers_stayAudioDelay() {
        // The retired table folded ANY Ctrl+bracket press — Shift/Alt held
        // alongside made no difference (`if (isCtrlPressed)`). Every combo
        // carries its own row so the exact-modifier candidate hits the
        // audio-delay arm instead of falling through the ladder to the plain
        // subtitle row.
        for (mods in listOf(
            Pair(first = true, second = false),
            Pair(first = false, second = true),
            Pair(first = true, second = true),
        )) {
            assertEquals(
                PlayerAction.AUDIO_DELAY_DECREASE,
                actionOf(PlayerInputKey.BRACKET_LEFT, ctrl = true, shift = mods.first, alt = mods.second),
                "shift=${mods.first} alt=${mods.second}",
            )
            assertEquals(
                PlayerAction.AUDIO_DELAY_INCREASE,
                actionOf(PlayerInputKey.BRACKET_RIGHT, ctrl = true, shift = mods.first, alt = mods.second),
                "shift=${mods.first} alt=${mods.second}",
            )
        }
    }

    @Test
    fun wheelDefaults_shiftSeeks_anyAxis_plainVerticalIsVolume() {
        // The retired PlayerWheelPolicy fold: Shift seeks regardless of axis,
        // a vertically-dominant unshifted scroll notches volume, a
        // horizontally-dominant scroll seeks.
        fun wheelAction(axis: WheelAxis, shift: Boolean): PlayerAction =
            PlayerInputPolicy.resolve(map, PlayerInputPolicy.wheelCandidates(axis, shift))?.action
                ?: fail("wheel axis=$axis shift=$shift has no default row")

        assertEquals(PlayerAction.SWIPE_VOLUME, wheelAction(WheelAxis.VERTICAL, shift = false))
        assertEquals(PlayerAction.SWIPE_SEEK, wheelAction(WheelAxis.VERTICAL, shift = true))
        assertEquals(PlayerAction.SWIPE_SEEK, wheelAction(WheelAxis.HORIZONTAL, shift = false))
        assertEquals(PlayerAction.SWIPE_SEEK, wheelAction(WheelAxis.HORIZONTAL, shift = true))
    }

    @Test
    fun dpadDefaults_reproduceTheRetiredBranch() {
        // One row per remote control; the seek arms' accumulate/commit shape
        // and the SELECT/UP/DOWN controls-visible gate live at the effect
        // site, the mapping carries the action alone.
        val expected = mapOf(
            DpadControl.LEFT to PlayerAction.SEEK_BACK,
            DpadControl.RIGHT to PlayerAction.SEEK_FORWARD,
            DpadControl.UP to PlayerAction.TOGGLE_CONTROLS,
            DpadControl.DOWN to PlayerAction.TOGGLE_CONTROLS,
            DpadControl.SELECT to PlayerAction.TOGGLE_CONTROLS,
            DpadControl.BACK to PlayerAction.BACK_OR_HIDE,
            DpadControl.PLAY_PAUSE to PlayerAction.TOGGLE_PLAY_PAUSE,
            DpadControl.FAST_FORWARD to PlayerAction.SEEK_FORWARD,
            DpadControl.REWIND to PlayerAction.SEEK_BACK,
        )
        for ((control, action) in expected) {
            val binding = PlayerInputPolicy.resolve(map, InputPattern.DPad(control))
                ?: fail("D-pad control $control has no default row")
            assertEquals(action, binding.action, "control=$control")
        }
    }

    @Test
    fun g_decreases_andH_increasesSubtitleDelay() {
        // G/H are jellyfin-web's subtitle-delay aliases of `[`/`]`.
        assertEquals(PlayerAction.SUBTITLE_DELAY_DECREASE, actionOf(PlayerInputKey.G))
        assertEquals(PlayerAction.SUBTITLE_DELAY_INCREASE, actionOf(PlayerInputKey.H))
    }

    @Test
    fun dpadUp_andVolumeUp_areVolumeUp_dpadDown_andVolumeDown_areVolumeDown() {
        assertEquals(PlayerAction.VOLUME_UP, actionOf(PlayerInputKey.DPAD_UP))
        assertEquals(PlayerAction.VOLUME_UP, actionOf(PlayerInputKey.VOLUME_UP))
        assertEquals(PlayerAction.VOLUME_DOWN, actionOf(PlayerInputKey.DPAD_DOWN))
        assertEquals(PlayerAction.VOLUME_DOWN, actionOf(PlayerInputKey.VOLUME_DOWN))
    }

    @Test
    fun f_andFunctionKeys_toggleOrientation() {
        for (key in listOf(
            PlayerInputKey.F,
            PlayerInputKey.F1,
            PlayerInputKey.F2,
            PlayerInputKey.F3,
            PlayerInputKey.F4,
        )) {
            assertEquals(PlayerAction.TOGGLE_ORIENTATION, actionOf(key), "key=$key")
        }
    }

    @Test
    fun m_togglesMute() {
        assertEquals(PlayerAction.TOGGLE_MUTE, actionOf(PlayerInputKey.M))
    }

    @Test
    fun v_togglesSubtitles() {
        assertEquals(PlayerAction.TOGGLE_SUBTITLES, actionOf(PlayerInputKey.V))
    }

    @Test
    fun escape_andBack_resolveBackOrHide() {
        // The controls-visible split is execution-time (the shell reads
        // showControls); the mapping carries ONE action for both arms.
        assertEquals(PlayerAction.BACK_OR_HIDE, actionOf(PlayerInputKey.ESCAPE))
        assertEquals(PlayerAction.BACK_OR_HIDE, actionOf(PlayerInputKey.BACK))
    }

    @Test
    fun seekFamilyKeys_carryTheCanonicalSeekRows() {
        // One modifier-less row per seek key; the direction rides the action.
        assertEquals(PlayerAction.SEEK_FORWARD, actionOf(PlayerInputKey.DPAD_RIGHT))
        assertEquals(PlayerAction.SEEK_FORWARD, actionOf(PlayerInputKey.MEDIA_FAST_FORWARD))
        assertEquals(PlayerAction.SEEK_FORWARD, actionOf(PlayerInputKey.L))
        assertEquals(PlayerAction.SEEK_FORWARD, actionOf(PlayerInputKey.PAGE_UP))
        assertEquals(PlayerAction.SEEK_FORWARD, actionOf(PlayerInputKey.MOVE_END))
        assertEquals(PlayerAction.SEEK_BACK, actionOf(PlayerInputKey.DPAD_LEFT))
        assertEquals(PlayerAction.SEEK_BACK, actionOf(PlayerInputKey.MEDIA_REWIND))
        assertEquals(PlayerAction.SEEK_BACK, actionOf(PlayerInputKey.J))
        assertEquals(PlayerAction.SEEK_BACK, actionOf(PlayerInputKey.PAGE_DOWN))
        assertEquals(PlayerAction.SEEK_BACK, actionOf(PlayerInputKey.MOVE_HOME))
    }

    @Test
    fun touchDefaults_reproduceTheRetiredArms() {
        // One row per touch pattern; the zone split, hold cadence and swipe
        // curves live at the detector sites, the mapping carries the action
        // alone. The retired arms: tap toggles controls, the double-tap zones
        // seek back / play-pause / seek forward, the seek-zone holds run the
        // continuous seek, long-press is hold-speed, left/right vertical
        // swipes are brightness/volume, horizontal swipe seeks, edge swipes
        // back out, pinch zooms.
        val expected = listOf(
            InputPattern.Tap to PlayerAction.TOGGLE_CONTROLS,
            InputPattern.DoubleTap(TouchZone.LEFT) to PlayerAction.SEEK_BACK,
            InputPattern.DoubleTap(TouchZone.CENTER) to PlayerAction.TOGGLE_PLAY_PAUSE,
            InputPattern.DoubleTap(TouchZone.RIGHT) to PlayerAction.SEEK_FORWARD,
            InputPattern.DoubleTapHold(TouchZone.LEFT) to PlayerAction.SEEK_BACK,
            InputPattern.DoubleTapHold(TouchZone.RIGHT) to PlayerAction.SEEK_FORWARD,
            InputPattern.LongPress to PlayerAction.HOLD_SPEED,
            InputPattern.VerticalSwipe(SwipeSide.LEFT) to PlayerAction.SWIPE_BRIGHTNESS,
            InputPattern.VerticalSwipe(SwipeSide.RIGHT) to PlayerAction.SWIPE_VOLUME,
            InputPattern.HorizontalSwipe to PlayerAction.SWIPE_SEEK,
            InputPattern.EdgeSwipe(SwipeEdge.LEFT) to PlayerAction.BACK_OR_HIDE,
            InputPattern.EdgeSwipe(SwipeEdge.RIGHT) to PlayerAction.BACK_OR_HIDE,
            InputPattern.Pinch to PlayerAction.ZOOM,
        )
        for ((pattern, action) in expected) {
            val binding = PlayerInputPolicy.resolve(map, pattern)
                ?: fail("touch pattern $pattern has no default row")
            assertEquals(action, binding.action, "pattern=$pattern")
        }
    }

    @Test
    fun everyCatalogKey_hasExactlyOnePlainDefaultRow() {
        // The catalog is the whole key domain the platform actuals can
        // produce (playerKeyCodeOf is exhaustive over it) — a key without a
        // plain (modifier-less) default row is a hardware key the player
        // silently stopped handling. Modifier rows (the Ctrl-combo bracket
        // audio delays) are extra by design; the plain row is the fallback
        // every unmodified press resolves against.
        for (key in PlayerInputKey.entries) {
            val plainRows = map.bindings.filter { binding ->
                val pattern = binding.pattern
                pattern is InputPattern.Key && pattern.key == key &&
                    !pattern.ctrl && !pattern.shift && !pattern.alt
            }
            assertEquals(1, plainRows.size, "catalog key $key must have exactly one plain default row")
        }
    }

    @Test
    fun platformKeyCodes_areDistinctPerCatalogKey() {
        // The reverse lookup (raw code → catalog key) is only well-defined if
        // the platform actuals give every catalog key its own code.
        val codes = PlayerInputKey.entries.map { playerKeyCodeOf(it) }
        assertEquals(codes.size, codes.distinct().size, "platform key codes must be distinct per catalog key")
    }
}

/**
 * The seek-table rows ([mediaKeySeek]) — one row per key, plus the
 * modifier folds. The step VALUES are the shared player-contract's
 * `keyboardSeekStepMs` table (pinned there in PlayerChromePoliciesTest); the
 * rows here pin WHICH keys carry the fold and in which direction, and that
 * the configured jump only feeds the plain (modifier-less) press.
 */
class MediaKeySeekTest {

    @Test
    fun dpadRight_fastForward_andL_seekForward() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
            PlayerKeyCodes.KEYCODE_MEDIA_FAST_FORWARD,
            PlayerKeyCodes.KEYCODE_L,
        )) {
            assertEquals(
                PlayerKeySeek(direction = 1, stepMs = 10_000L),
                mediaKeySeek(keyCode = code, configuredStepMs = 10_000L),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun dpadLeft_rewind_andJ_seekBack() {
        for (code in listOf(
            PlayerKeyCodes.KEYCODE_DPAD_LEFT,
            PlayerKeyCodes.KEYCODE_MEDIA_REWIND,
            PlayerKeyCodes.KEYCODE_J,
        )) {
            assertEquals(
                PlayerKeySeek(direction = -1, stepMs = 10_000L),
                mediaKeySeek(keyCode = code, configuredStepMs = 10_000L),
                "keyCode=$code",
            )
        }
    }

    @Test
    fun shiftArrows_takeTheFineFiveSecondStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_FINE_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_FINE_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 30_000L,
                isShiftPressed = true,
            ),
        )
    }

    @Test
    fun ctrlArrows_takeTheSixtySecondStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 30_000L,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun shiftCtrlArrows_takeTheThirtySecondStep_beatingTheSingleModifierRows() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_SHIFT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_SHIFT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 10_000L,
                isShiftPressed = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun altCtrlArrows_takeTheFiveMinuteStep_bothDirections() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_ALT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT,
                configuredStepMs = 10_000L,
                isAltPressed = true,
                isCtrlPressed = true,
            ),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_ALT_CTRL_MS),
            mediaKeySeek(
                keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT,
                configuredStepMs = 10_000L,
                isAltPressed = true,
                isCtrlPressed = true,
            ),
        )
    }

    @Test
    fun home_isBack_andEnd_isForward_atTheSmallStep() {
        // jellyfin-media-player semantics: Home/End are the small-step rows,
        // direction-fixed and modifier-independent.
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_HOME_END_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_MOVE_HOME, configuredStepMs = 60_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_HOME_END_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_MOVE_END, configuredStepMs = 60_000L),
        )
    }

    @Test
    fun pageUp_isForward_andPageDown_isBack_atTheBigStep() {
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = KEY_SEEK_STEP_PAGE_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_PAGE_UP, configuredStepMs = 60_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = KEY_SEEK_STEP_PAGE_MS),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_PAGE_DOWN, configuredStepMs = 60_000L),
        )
    }

    @Test
    fun plainArrows_keepTheConfiguredStep() {
        // The configured preference step is the whole point of the plain row —
        // it must flow through untouched, not a hardcoded default.
        assertEquals(
            PlayerKeySeek(direction = 1, stepMs = 33_000L),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_DPAD_RIGHT, configuredStepMs = 33_000L),
        )
        assertEquals(
            PlayerKeySeek(direction = -1, stepMs = 33_000L),
            mediaKeySeek(keyCode = PlayerKeyCodes.KEYCODE_DPAD_LEFT, configuredStepMs = 33_000L),
        )
    }

    @Test
    fun delayAdjust_stepsAndClampsToTheOverlayWindow() {
        // the fold: ±step around the current value, clamped to the same ±30s
        // window SubtitleDelayOverlay and AVSyncSheet enforce.
        assertEquals(150L, delayAdjustMs(currentMs = 100L, sign = 1, stepMs = KEY_SUBTITLE_DELAY_STEP_MS))
        assertEquals(50L, delayAdjustMs(currentMs = 100L, sign = -1, stepMs = KEY_SUBTITLE_DELAY_STEP_MS))
        assertEquals(300L, delayAdjustMs(currentMs = 200L, sign = 1, stepMs = KEY_AUDIO_DELAY_STEP_MS))
        // Clamp ends: the overlays stop at ±30s, so must the shortcuts.
        assertEquals(KEY_DELAY_MAX_MS, delayAdjustMs(currentMs = KEY_DELAY_MAX_MS, sign = 1, stepMs = 5_000L))
        assertEquals(KEY_DELAY_MIN_MS, delayAdjustMs(currentMs = KEY_DELAY_MIN_MS, sign = -1, stepMs = 5_000L))
    }
}
