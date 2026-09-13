package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.raulshma.jellyplay.core.datastore.reader.ReadingDirection
import com.raulshma.jellyplay.feature.book.epub.EpubTapZone

/**
 * What a content tap should do. Shared vocabulary of the native tap zones
 * (paged reader) and the JS-reported ones (reflowable reader) so both map
 * through the same direction-aware logic.
 */
internal enum class ReaderTapAction { FORWARD, BACKWARD, TOGGLE_CONTROLS }

/**
 * Physical-left input (left arrow / leading tap zone) pages forward under
 * RTL, backward under LTR — the one named place the direction mapping lives
 * for the key handler, the native tap zones and the JS tap events.
 */
internal fun ReadingDirection.isForwardFromLeft(): Boolean = this == ReadingDirection.RTL

/**
 * Maps a JS-reported content tap zone ([EpubTapZone], computed by reader.js
 * from the touch thirds) to the direction-aware action: the left zone pages
 * forward under RTL, backward under LTR (mirroring key handling); the center
 * zone toggles the chrome. Pure — pinned by ReaderInputTest.
 */
internal fun epubTapAction(zone: EpubTapZone, direction: ReadingDirection): ReaderTapAction =
    when (zone) {
        EpubTapZone.LEFT -> if (direction.isForwardFromLeft()) ReaderTapAction.FORWARD else ReaderTapAction.BACKWARD
        EpubTapZone.RIGHT -> if (direction.isForwardFromLeft()) ReaderTapAction.BACKWARD else ReaderTapAction.FORWARD
        EpubTapZone.CENTER -> ReaderTapAction.TOGGLE_CONTROLS
    }

internal fun handleKeyEvent(
    event: KeyEvent,
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        // Arrows follow the reading direction; PageUp/PageDown stay physical.
        Key.DirectionLeft -> {
            if (direction.isForwardFromLeft()) onForward() else onBackward()
            true
        }
        Key.DirectionRight -> {
            if (direction.isForwardFromLeft()) onBackward() else onForward()
            true
        }
        Key.PageUp, Key.MediaRewind -> {
            onBackward()
            true
        }
        Key.PageDown, Key.MediaFastForward -> {
            onForward()
            true
        }
        Key.Escape -> {
            onBack()
            true
        }
        else -> false
    }
}

/**
 * TV remotes have no tap zone: D-pad center / Enter / Menu is the only way
 * back into the (auto-hiding) settings chrome. This lives in the BUBBLE
 * phase, unlike the paging keys: a focused chrome control (the settings
 * gear) must see center/Enter first — a preview handler would swallow the
 * click and just toggle the chrome away beneath it.
 */
internal fun Modifier.chromeToggleKey(onToggleControls: () -> Unit): Modifier =
    onKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown &&
            (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Menu)
        ) {
            onToggleControls()
            true
        } else {
            false
        }
    }

/**
 * Direction-aware tap zones: leading third = backward, trailing third =
 * forward, center = toggle chrome — with "leading" flipping under RTL.
 * [direction] keys the detector so a flip re-arms it with the new mapping.
 */
internal fun Modifier.readerTapZones(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onToggleControls: () -> Unit,
): Modifier = pointerInput(direction) {
    detectTapGestures { offset ->
        val action = when {
            offset.x < size.width / 3f ->
                if (direction.isForwardFromLeft()) ReaderTapAction.FORWARD else ReaderTapAction.BACKWARD
            offset.x > size.width * 2f / 3f ->
                if (direction.isForwardFromLeft()) ReaderTapAction.BACKWARD else ReaderTapAction.FORWARD
            else -> ReaderTapAction.TOGGLE_CONTROLS
        }
        action.dispatch(onForward, onBackward, onToggleControls)
    }
}

private fun ReaderTapAction.dispatch(
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onToggleControls: () -> Unit,
) {
    when (this) {
        ReaderTapAction.FORWARD -> onForward()
        ReaderTapAction.BACKWARD -> onBackward()
        ReaderTapAction.TOGGLE_CONTROLS -> onToggleControls()
    }
}

/**
 * The paged reader's input surface: keyboard/DPAD events (arrows follow the
 * reading direction, PageUp/PageDown stay physical) plus the direction-aware
 * NATIVE tap zones. Only the pager may use this — a `pointerInput` overlay
 * swallows every touch, which is exactly what the reflowable WebView must
 * NOT do (text selection needs the raw web events).
 */
internal fun Modifier.readerInput(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
): Modifier = focusable()
    .onPreviewKeyEvent { event ->
        handleKeyEvent(
            event = event,
            direction = direction,
            onForward = onForward,
            onBackward = onBackward,
            onBack = onBack,
        )
    }
    .chromeToggleKey(onToggleControls)
    .readerTapZones(
        direction = direction,
        onForward = onForward,
        onBackward = onBackward,
        onToggleControls = onToggleControls,
    )

/**
 * The reflowable reader's input surface: keyboard ONLY. `focusable` +
 * `onPreviewKeyEvent` never consume pointer events, so web touches (text
 * selection, link taps) reach the WebView untouched — touch navigation rides
 * the JS-reported tap events instead (reader.js computes the thirds inside
 * the content iframe and skips taps that are really selection gestures).
 * TV/desktop keyboards keep the exact paged-reader behavior.
 */
internal fun Modifier.readerKeys(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
): Modifier = focusable()
    .onPreviewKeyEvent { event ->
        handleKeyEvent(
            event = event,
            direction = direction,
            onForward = onForward,
            onBackward = onBackward,
            onBack = onBack,
        )
    }
    .chromeToggleKey(onToggleControls)
