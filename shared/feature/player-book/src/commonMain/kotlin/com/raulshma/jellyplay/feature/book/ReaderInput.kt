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
import com.raulshma.jellyplay.feature.book.epub.tapZoneFor

/**
 * What any content input should do after the guards fold. The shared
 * vocabulary of every input path — the native paged tap zones, the
 * JS-reported taps and swipes, the keyboard arrows and the volume keys — so
 * all of them map through [readerNavDecision]'s one direction-aware rule
 * ([IGNORE] exists only for the guarded paths: a sheet holding the screen or
 * a live text selection).
 */
internal enum class ReaderNavDecision { FORWARD, BACKWARD, TOGGLE_CONTROLS, IGNORE }

/**
 * Physical-left input (left arrow / leading tap zone) pages forward under
 * RTL, backward under LTR — the one named direction predicate both the arrow
 * keys ([handleKeyEvent]) and the content-input decision ([readerNavDecision])
 * fold through.
 */
internal fun ReadingDirection.isForwardFromLeft(): Boolean = this == ReadingDirection.RTL

/**
 * THE content-input decision — the one direction-aware "which third → which
 * action" rule. The guards fold in front (sheet-open taps are noise: a modal
 * holds the screen; a live selection must not page), then the leading third
 * pages backward / the trailing third forward under LTR and the reverse
 * under RTL (mirroring the arrow keys via [isForwardFromLeft]), and the
 * center toggles the chrome. Every input path funnels through this one
 * function: the native paged tap zones ([readerTapZones] resolves their
 * gesture geometry with the same [tapZoneFor] the bridge decode uses), the
 * JS-reported taps, and the swipes (synthesized zones — swipe left ≡ tap on
 * the right). Pure — pinned by ReaderInputTest.
 */
internal fun readerNavDecision(
    zone: EpubTapZone,
    direction: ReadingDirection,
    sheetOpen: Boolean = false,
    selectionActive: Boolean = false,
): ReaderNavDecision {
    if (sheetOpen || selectionActive) return ReaderNavDecision.IGNORE
    return when (zone) {
        EpubTapZone.LEFT -> if (direction.isForwardFromLeft()) ReaderNavDecision.FORWARD else ReaderNavDecision.BACKWARD
        EpubTapZone.RIGHT -> if (direction.isForwardFromLeft()) ReaderNavDecision.BACKWARD else ReaderNavDecision.FORWARD
        EpubTapZone.CENTER -> ReaderNavDecision.TOGGLE_CONTROLS
    }
}

/**
 * Volume-key paging mapping (physical, like PageUp/PageDown): VolumeDown
 * pages FORWARD, VolumeUp BACKWARD — FORWARD/BACKWARD are the reader's
 * direction-relative turns, so RTL books still page in their reading order.
 * Any other key maps to null (not a volume-paging key). Pure — pinned by
 * ReaderInputTest; wired only when the volumeKeyPaging preference is on
 * (Android hardware; a harmless no-op where no volume keys exist).
 */
internal fun volumeKeyPagingAction(key: Key): ReaderNavDecision? = when (key) {
    Key.VolumeDown -> ReaderNavDecision.FORWARD
    Key.VolumeUp -> ReaderNavDecision.BACKWARD
    else -> null
}

/**
 * The scroll style a programmatic page turn (keyboard / tap zone / slider /
 * outline jump) uses: animated when the animatedPageTurns preference is on,
 * snapped when off. User SWIPES always animate (the pager owns those). Pure —
 * pinned by ReaderInputTest.
 */
internal enum class PageTurnScroll { ANIMATED, SNAP }

internal fun pageTurnScroll(animatedPageTurns: Boolean): PageTurnScroll =
    if (animatedPageTurns) PageTurnScroll.ANIMATED else PageTurnScroll.SNAP

internal fun handleKeyEvent(
    event: KeyEvent,
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    volumeKeyPaging: Boolean = false,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (volumeKeyPaging) {
        volumeKeyPagingAction(event.key)?.let { action ->
            when (action) {
                ReaderNavDecision.FORWARD -> onForward()
                ReaderNavDecision.BACKWARD -> onBackward()
                ReaderNavDecision.TOGGLE_CONTROLS -> Unit // unreachable for volume keys
                ReaderNavDecision.IGNORE -> Unit // unreachable — keys carry no sheet/selection guards
            }
            return true
        }
    }
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
 * Direction-aware tap zones: the gesture's thirds resolve through
 * [tapZoneFor] (the same geometry rule the JS bridge decode uses) and the
 * action through [readerNavDecision] — the one funnel every input path
 * rides. [direction] keys the detector so a flip re-arms it with the new
 * mapping. An [onDoubleTap] (paged reader zoom toggle) makes single taps
 * wait the double-tap timeout — that is the cost of both gestures living on
 * the same surface; the paged reader is the only caller that passes one.
 */
internal fun Modifier.readerTapZones(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onToggleControls: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
): Modifier = pointerInput(direction) {
    detectTapGestures(
        onTap = { offset ->
            readerNavDecision(tapZoneFor(offset.x.toDouble(), size.width.toDouble()), direction)
                .dispatch(onForward, onBackward, onToggleControls)
        },
        onDoubleTap = onDoubleTap?.let { handler -> { _ -> handler() } },
    )
}

private fun ReaderNavDecision.dispatch(
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onToggleControls: () -> Unit,
) {
    when (this) {
        ReaderNavDecision.FORWARD -> onForward()
        ReaderNavDecision.BACKWARD -> onBackward()
        ReaderNavDecision.TOGGLE_CONTROLS -> onToggleControls()
        ReaderNavDecision.IGNORE -> Unit // unreachable natively — no sheet/selection guards folded
    }
}

/**
 * The paged reader's input surface: keyboard/DPAD events (arrows follow the
 * reading direction, PageUp/PageDown stay physical, VolumeUp/Down page when
 * [volumeKeyPaging] is on) plus the direction-aware NATIVE tap zones and the
 * optional double-tap zoom toggle. Only the pager may use this — a
 * `pointerInput` overlay swallows every touch, which is exactly what the
 * reflowable WebView must NOT do (text selection needs the raw web events).
 */
internal fun Modifier.readerInput(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    volumeKeyPaging: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
): Modifier = focusable()
    .onPreviewKeyEvent { event ->
        handleKeyEvent(
            event = event,
            direction = direction,
            onForward = onForward,
            onBackward = onBackward,
            onBack = onBack,
            volumeKeyPaging = volumeKeyPaging,
        )
    }
    .chromeToggleKey(onToggleControls)
    .readerTapZones(
        direction = direction,
        onForward = onForward,
        onBackward = onBackward,
        onToggleControls = onToggleControls,
        onDoubleTap = onDoubleTap,
    )

/**
 * The reflowable reader's input surface: keyboard ONLY. `focusable` +
 * `onPreviewKeyEvent` never consume pointer events, so web touches (text
 * selection, link taps) reach the WebView untouched — touch navigation rides
 * the JS-reported tap events instead (reader.js computes the thirds inside
 * the content iframe and skips taps that are really selection gestures).
 * TV/desktop keyboards keep the exact paged-reader behavior; volume keys page
 * when [volumeKeyPaging] is on.
 */
internal fun Modifier.readerKeys(
    direction: ReadingDirection,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    volumeKeyPaging: Boolean = false,
): Modifier = focusable()
    .onPreviewKeyEvent { event ->
        handleKeyEvent(
            event = event,
            direction = direction,
            onForward = onForward,
            onBackward = onBackward,
            onBack = onBack,
            volumeKeyPaging = volumeKeyPaging,
        )
    }
    .chromeToggleKey(onToggleControls)
