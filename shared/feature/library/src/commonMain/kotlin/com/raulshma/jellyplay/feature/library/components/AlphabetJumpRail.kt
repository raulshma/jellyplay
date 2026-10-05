package com.raulshma.jellyplay.feature.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.library.AlphabetRailGeometry

/**
 * Right-edge alphabet "jump to letter" rail for large libraries. Renders the set
 * of leading letters present in the loaded items (plus `#` for non A–Z names) as
 * a compact vertical column. Input modes:
 * - Tap anywhere on the rail → jump to the letter at that Y-position via [onJump].
 * - Drag (touch) up and down the rail → fisheye lens: the letter under the finger
 * magnifies most, its neighbors taper smaller via a gaussian falloff, and the
 * whole bell-curve animates continuously with the finger. [onJump] fires as the
 * finger crosses each letter boundary. A magnifier bubble tracks the finger Y.
 * - dpad-focus a letter (TV) → press select to jump.
 * - The active letter (from the host's scroll position) is tinted primary so the
 * rail doubles as a "you are here" indicator.
 *
 * Touch input is handled at the rail level (not per-letter) so the user never has
 * to hit a ~16px label — any point on the rail maps to a letter. Per-letter focus
 * is retained for TV dpad navigation.
 *
 * Local-only — see [com.raulshma.jellyplay.feature.library.LibraryScreen] for why
 * a NameStartsWith server filter isn't used.
 */
@Composable
internal fun AlphabetJumpRail(
    letters: List<Char>,
    onJump: (Char) -> Unit,
    modifier: Modifier = Modifier,
    activeLetter: Char? = null,
) {
    if (letters.isEmpty()) return
    val isLight = LocalIsLightTheme.current
    val railShape = ShapeCache.smooth12
    val railBg = if (isLight) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f)
    val contentColor = MaterialTheme.colorScheme.onBackground
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // Continuous finger position in *letter-index space* (e.g. 3.4 = between D
    // and E). Null at rest. Tracking fractional position (not just the integer
    // letter under the finger) is what lets the fisheye bell-curve glide smoothly
    // — the gaussian weight on each neighbor updates every pointer move.
    // Backing state holders are kept as vals so they can be captured in stable
    // provider lambdas (read in the draw phase) without breaking Compose skipping
    // — a delegated `var` captured directly would force a new lambda every frame.
    val touchIndexState = remember { mutableStateOf<Float?>(null) }
    var touchIndex by touchIndexState
    val bubbleState = remember { mutableStateOf<Char?>(null) }
    var bubbleForJump by bubbleState
    var dragging by remember { mutableStateOf(false) }
    var lastBubbleLetter by remember { mutableStateOf(letters.first()) }
    // Derived over the *integer* letter index, so the rail composition only
    // recomputes when the finger crosses a letter boundary — not on every
    // fractional pointer move. Without this, `touchIndex` (read in composition
    // below) would invalidate the whole rail every drag frame.
    val currentBubble by remember {
        derivedStateOf {
            val ti = touchIndexState.value
            when {
                dragging && ti != null -> letters[ti.toInt().coerceIn(0, letters.lastIndex)]
                else -> bubbleState.value
            }
        }
    }
    currentBubble?.let { lastBubbleLetter = it }
    val bubbleVisible = dragging || bubbleForJump != null
    // A tap (no drag) shows the zoom bubble briefly so the user sees feedback,
    // then fades it out. Drag-driven bubbles clear on drag end.
    LaunchedEffect(bubbleForJump) {
        if (bubbleForJump != null) {
            kotlinx.coroutines.delay(350)
            bubbleForJump = null
        }
    }

    // Fixed per-letter row height — tight enough that the rail reads as a compact
    // column (no big gaps), tall enough to tap. Determined up front (not derived
    // from fillMaxHeight) so the row→index math is exact and stable.
    val rowPx = with(density) { LETTER_ROW_HEIGHT.toPx() }
    // Pure math (index mapping, fisheye, jump targets) lives beside the screen
    // so it stays Compose-free and testable; only dp/px conversion, drawing,
    // and pointer wiring remain here.
    val geometry = remember(letters, rowPx) { AlphabetRailGeometry(letters, rowPx) }

    Box(modifier = modifier) {
        // Rail body — wrap-content height (sum of letter rows), centered in the
        // host. Width-only rail; the Column inside stacks rows at the fixed height.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(28.dp)
                .clip(railShape)
                .background(railBg),
        ) {
            // Letters. Each row delegates to a skippable [LetterItem] that owns its
            // own TV-focus state and fisheye draw-layer. The fisheye scale is read
            // directly inside `graphicsLayer { }` from a snapshot-read lambda so the
            // per-frame finger motion drives the draw phase only — no recomposition
            // of the ~27 letter rows (and their animate*AsState coroutines) on every
            // pointer move. Only the rail shell + 1–2 letters whose `isActive` flag
            // flips recompose.
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Derived to the *integer* letter under the finger so the parent
                // composition only recomposes when the finger crosses a letter
                // boundary (≤ letters.size times per drag) — not on every fractional
                // pointer move. The fractional value is still consumed live in the
                // draw phase via [LetterItem]'s fisheyeScaleProvider.
                val touchedLetter by remember {
                    derivedStateOf {
                        touchIndexState.value
                            ?.toInt()
                            ?.coerceIn(0, letters.lastIndex)
                            ?.let { letters[it] }
                    }
                }
                letters.forEachIndexed { index, letter ->
                    key(letter) {
                        // Stable provider per letter: captures only `index` (a
                        // constant) and the `touchIndexState` ref — reads `.value`
                        // live inside the graphicsLayer draw lambda, so the same
                        // lambda instance survives across drag frames and keeps
                        // [LetterItem] skippable.
                        val fisheyeScaleProvider = remember(index, geometry) {
                            { geometry.fisheyeScaleAt(index, touchIndexState.value) }
                        }
                        // Stable click handler per letter so the parent
                        // recomposing (on boundary crossings) doesn't hand every
                        // item a new lambda and force a full rail re-invoke.
                        val onClick = remember(letter, bubbleState, onJump) {
                            {
                                bubbleState.value = letter
                                onJump(letter)
                            }
                        }
                        LetterItem(
                            letter = letter,
                            fisheyeScaleProvider = fisheyeScaleProvider,
                            isActive = letter == activeLetter ||
                                letter == bubbleForJump ||
                                letter == touchedLetter,
                            railShape = railShape,
                            contentColor = contentColor,
                            activeColor = primaryColor,
                            onClick = onClick,
                        )
                    }
                }
            }

            // Touch overlay — drawn ON TOP (last child) and matchParentSize. This
            // is critical: without it the letters' clickable handlers (which sit
            // in the Column above) intercept every pointer, so the drag never
            // starts and the fisheye never engages. The overlay is transparent
            // and non-focusable, so TV dpad focus still reaches the letters below.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(geometry) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchIndex = geometry.indexAt(offset.y)
                                dragging = true
                                onJump(geometry.letterAt(offset.y))
                            },
                            onDrag = { change, _ ->
                                touchIndex = geometry.indexAt(change.position.y)
                                onJump(geometry.letterAt(change.position.y))
                            },
                            onDragEnd = { dragging = false; touchIndex = null },
                            onDragCancel = { dragging = false; touchIndex = null },
                        )
                    }
                    .pointerInput(geometry) {
                        detectTapGestures { offset ->
                            val l = geometry.letterAt(offset.y)
                            bubbleForJump = l
                            onJump(l)
                        }
                    },
            )

            // Magnifier bubble — zoom the active/dragged letter next to the rail.
            // Tracks the finger Y while dragging (touchIndex × rowPx); centers at
            // rest. Uses lastBubbleLetter (not the live source!!) because content
            // composes during the exit fade when the source is null.
            AnimatedVisibility(
                visible = bubbleVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
                val centerX = with(density) { -(BUBBLE_SIZE.toPx() + 12.dp.toPx()) }
                // Read touchIndex in the draw phase (offset lambda) so the rail
                // composition doesn't re-subscribe to every fractional pointer move
                // — the bubble tracks the finger smoothly with no recomposition.
                Box(
                    modifier = Modifier
                        .size(BUBBLE_SIZE)
                        .offset {
                            val ti = touchIndexState.value
                            val centerY = if (ti != null) {
                                ti * rowPx - bubbleSizePx / 2f
                            } else {
                                0f
                            }
                            androidx.compose.ui.unit.IntOffset(centerX.toInt(), centerY.toInt())
                        }
                        .clip(CircleShape)
                        .background(primaryColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = lastBubbleLetter.uppercaseChar().toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** Fixed per-letter row height. Tight enough to avoid gaps, tall enough to tap. */
private val LETTER_ROW_HEIGHT = 18.dp
/** Magnifier bubble diameter. */
private val BUBBLE_SIZE = 44.dp

/**
 * Single letter row in the [AlphabetJumpRail]. Skippable: all parameters are
 * stable across drag frames except [isActive] (toggles at most a few times per
 * drag) and [fisheyeScaleProvider], whose identity is stable — it reads live
 * state from inside the draw phase. This keeps the per-letter [rememberTvFocusState]
 * (which on TV spins an infinite breathing transition) and its border/glow
 * animations alive exactly once per letter, instead of being recreated every
 * pointer frame as the old inline `forEachIndexed` did.
 */
@Composable
private fun LetterItem(
    letter: Char,
    isActive: Boolean,
    fisheyeScaleProvider: () -> Float,
    railShape: androidx.compose.ui.graphics.Shape,
    contentColor: Color,
    activeColor: Color,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.15f)
    val interactionSource = remember { MutableInteractionSource() }
    // Soft handoff when the active letter moves (scroll) or the drag bubble
    // passes through — the fisheye already glides, the color shouldn't snap.
    val letterColor by animateColorAsState(
        targetValue = if (isActive) activeColor else contentColor,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "letterActiveColor",
    )
    Box(
        modifier = Modifier
            .height(LETTER_ROW_HEIGHT)
            .width(LETTER_ROW_HEIGHT)
            .graphicsLayer {
                // Combined fisheye + TV focus scale, read in the draw phase so
                // finger motion animates the bell-curve with zero recomposition.
                val s = fisheyeScaleProvider() * focusState.scale
                scaleX = s
                scaleY = s
            }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, railShape)
            .focusable()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter.uppercaseChar().toString(),
            style = MaterialTheme.typography.labelSmall,
            color = letterColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
