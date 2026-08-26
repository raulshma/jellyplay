package com.raulshma.jellyplay.feature.player.video.components

import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Minus
import com.composables.icons.tabler.outline.Plus
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_close
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_decrease
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_increase
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_reset
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_text_seen
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_delay_voice_heard







import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Compact, transparent subtitle-delay setter overlaying the video so the user
 * can watch the live subtitles shift while nudging the offset — VLC-style.
 *
 * The outer box is `fillMaxSize` with **no** `pointerInput`/`clickable`, so taps
 * on empty space fall through to the host gesture layer — only the inner card
 * consumes input (mirrors [MpvSubtitleOverlay]'s pass-through pattern).
 *
 * Controls mirror VLC for Android's subtitle-delay overlay:
 *  - A value row with press-and-hold **+ / −** buttons that step in 50ms
 *    increments (snap-to-grid when off-step), repeating faster the longer they
 *    are held. The whole-millisecond readout ("50 ms", "-200 ms") updates live.
 *  - A two-tap sync row: tap **Voice heard** when speech starts, then **Text
 *    seen** when the subtitle appears — the delta is applied automatically.
 *  - A **Reset** button that zeroes the delay and clears the sync markers.
 *
 * Each nudge writes to a local accumulator (so the readout moves instantly);
 * the final value flushes to [onChange] through a short debounce because some
 * engines (e.g. ExoPlayer) must reload the media item to re-parse cues. The
 * overlay stays open until the user closes it (✕ or back) — there is no
 * auto-hide.
 */
@Composable
fun SubtitleDelayOverlay(
    currentDelayMs: Long,
    onChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    JellyPlayBackHandler(enabled = true) { onDismiss() }

    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    // Local accumulator, re-seeded from the persisted value whenever the engine
    // or another surface mutates it out from under us (e.g. reset from the hub).
    var pendingMs by remember(currentDelayMs) { mutableLongStateOf(currentDelayMs) }
    // A/B sync markers; -1L means "not captured". Stored as wall-clock millis so
    // their delta (voice - text) is already a delay delta in milliseconds.
    var voiceMarkerMs by remember { mutableLongStateOf(-1L) }
    var textMarkerMs by remember { mutableLongStateOf(-1L) }

    val scope = rememberCoroutineScope()
    var flushJob by remember { mutableStateOf<Job?>(null) }

    fun scheduleFlush(newMs: Long) {
        // Clamp to the supported delay range so a runaway two-tap sync (markers
        // captured minutes apart) or a long hold can't push the offset into
        // engine-defined territory (VLC applies the same ±30s cap).
        pendingMs = newMs.coerceIn(MIN_MS, MAX_MS)
        // Cancel any pending flush and start a fresh debounce window; the final
        // value is pushed to the engine once the burst settles.
        flushJob?.cancel()
        flushJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            onChange(pendingMs)
        }
    }

    fun adjust(deltaMs: Long, fromCustom: Boolean = false) {
        // VLC snap-to-grid: if the current value is not on the step grid (e.g.
        // after a two-tap sync produced an off-step value), the next +/- first
        // snaps to the adjacent grid line in that direction instead of adding a
        // full step. Only the partial remainder moves, so e.g. +137ms snaps to
        // 150 on "+" and to 100 on "−".
        val stepped = if (!fromCustom && pendingMs % STEP_MS != 0L) {
            // Non-negative remainder in [0, STEP_MS), independent of pendingMs's
            // sign (Kotlin's % follows the dividend, which would skew the snap).
            val remainder = ((pendingMs % STEP_MS) + STEP_MS) % STEP_MS
            if (deltaMs > 0) STEP_MS - remainder else -remainder
        } else {
            deltaMs
        }
        scheduleFlush(pendingMs + stepped)
    }

    fun applySync() {
        if (voiceMarkerMs != -1L && textMarkerMs != -1L) {
            // VLC: newDelay = currentDelay + (voice - text). A late subtitle
            // (text after voice) therefore decreases the delay.
            adjust(voiceMarkerMs - textMarkerMs, fromCustom = true)
            voiceMarkerMs = -1L
            textMarkerMs = -1L
        }
    }

    fun reset() {
        flushJob?.cancel()
        voiceMarkerMs = -1L
        textMarkerMs = -1L
        pendingMs = 0L
        onChange(0L)
    }

    LaunchedEffect(currentDelayMs) {
        // An external change (reset button elsewhere, engine hydration) wins:
        // re-seed the local accumulator and drop any pending flush.
        if (currentDelayMs != pendingMs) {
            flushJob?.cancel()
            pendingMs = currentDelayMs
        }
    }
    LaunchedEffect(isTv) {
        if (isTv) focusRequester.tryRequestFocus("subtitle-delay-overlay")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = OVERLAY_END_MARGIN_DP.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(16.dp))
                .background(playerScrimColor().copy(alpha = 0.85f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Header: title + close.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.player_video_subtitle_delay),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.X,
                        contentDescription = stringResource(Res.string.player_video_subtitle_delay_close),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Value row: [−]   "N ms"   [+]
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RepeatableButton(
                    icon = Tabler.Outline.Minus,
                    description = stringResource(Res.string.player_video_subtitle_delay_decrease),
                    focusRequester = focusRequester,
                    modifier = Modifier.testTag("subtitle_delay_minus"),
                ) { adjust(-STEP_MS) }

                Text(
                    formatDelayLabelMs(pendingMs),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_delay_value"),
                )

                RepeatableButton(
                    icon = Tabler.Outline.Plus,
                    description = stringResource(Res.string.player_video_subtitle_delay_increase),
                    modifier = Modifier.testTag("subtitle_delay_plus"),
                ) { adjust(STEP_MS) }
            }

            Spacer(Modifier.height(16.dp))

            // Two-tap sync: Voice heard → Text seen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncButton(
                    label = stringResource(Res.string.player_video_subtitle_delay_voice_heard),
                    captured = voiceMarkerMs != -1L,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_delay_voice"),
                ) {
                    voiceMarkerMs = if (voiceMarkerMs == -1L) System.currentTimeMillis() else -1L
                    applySync()
                }
                SyncButton(
                    label = stringResource(Res.string.player_video_subtitle_delay_text_seen),
                    captured = textMarkerMs != -1L,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtitle_delay_text"),
                ) {
                    textMarkerMs = if (textMarkerMs == -1L) System.currentTimeMillis() else -1L
                    applySync()
                }
            }

            Spacer(Modifier.height(12.dp))

            // Reset (always visible, VLC-style).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .clickable { reset() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("subtitle_delay_reset"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.player_video_subtitle_delay_reset),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Circular +/- button with VLC-style press-and-hold auto-repeat: fires once on
 * press, then repeats (500ms initial → 150ms normal), speeding up the longer it
 * is held (÷3 after 2s, ÷9 after 4s).
 *
 * Touch owns the gesture (press-hold repeat); D-pad/keyboard activation fires
 * per key-press (and per system key-repeat while held); TalkBack activates via
 * the semantics [onClick] action. The callback is read through
 * [rememberUpdatedState] so a press started before a recomposition keeps
 * invoking the latest lambda without restarting the gesture detector.
 */
@Composable
private fun RepeatableButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, CircleShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    currentOnClick()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    currentOnClick() // fire immediately, like VLC's ACTION_DOWN
                    val job = scope.launch {
                        val start = System.currentTimeMillis()
                        delay(REPEAT_INITIAL_MS)
                        while (isActive) {
                            currentOnClick()
                            val held = System.currentTimeMillis() - start
                            val interval = when {
                                held < REPEAT_SPEEDUP_MS -> REPEAT_NORMAL_MS
                                held < REPEAT_SPEEDUP_MS * 2 -> REPEAT_NORMAL_MS / 3
                                else -> REPEAT_NORMAL_MS / 9
                            }
                            delay(interval)
                        }
                    }
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val allReleased = event.changes.all { !it.pressed }
                            event.changes.forEach { it.consume() }
                            if (allReleased) break
                        }
                    } finally {
                        job.cancel()
                    }
                }
            }
            .semantics {
                role = Role.Button
                onClick(label = description) { currentOnClick(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

/**
 * Two-tap sync marker button. Tapping toggles the marker: when captured the
 * button is highlighted (VLC tints the checkmark orange); tapping again clears
 * it. When both markers are captured the delta is applied by [applySync].
 */
@Composable
private fun SyncButton(
    label: String,
    captured: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val focus = rememberTvFocusState()
    val highlight = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (captured) highlight.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.10f))
            .then(focus.focusModifier)
            .tvFocusIndicator(focus, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (captured) {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = null,
                    tint = highlight,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (captured) highlight else Color.White,
            )
        }
    }
}

private const val STEP_MS = 50L
private const val MIN_MS = -30000L
private const val MAX_MS = 30000L
private const val FLUSH_DEBOUNCE_MS = 250L

// Press-and-hold repeat timing, matching VLC's OnRepeatListener defaults.
private const val REPEAT_INITIAL_MS = 500L
private const val REPEAT_NORMAL_MS = 150L
private const val REPEAT_SPEEDUP_MS = 2000L

/**
 * Anchors the compact overlay against the right edge.
 */
private const val OVERLAY_END_MARGIN_DP = 16
