package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The TV focus-gated trailer preview's play-target port. The detail
 * hero's muted preview (ThemeVideo clip or YouTube embed, the backdrop
 * autoplay pipeline) mounts only while the Play/Trailer button holds focus
 * — so the button's focus state must travel from `DetailActionButtons`
 * (deep in the body) up to `DetailBackdrop`.
 *
 * A composition-local holder, NOT a [DetailContentState] field: focus flips
 * at D-pad speed, and rebuilding the @Immutable state bag per flip would
 * recompose the whole detail content tree (the [LocalEpisodeDownloadProgress]
 * rationale, inverted — high-frequency data stays out of the bag).
 * `MediaDetailScreen`/`DetailContent` own the instance; the Play button
 * writes it, `DetailBackdrop` reads it.
 */
internal class TrailerFocusPreviewState {
    /** True while the primary Play/Trailer button (or its subtree) holds focus. */
    var playTargetFocused by mutableStateOf(false)
}

/** The port's write/read channel; the default instance is a detached sink so unwired hosts stay inert. */
internal val LocalTrailerFocusPreview = staticCompositionLocalOf { TrailerFocusPreviewState() }

/**
 * The mount decision, in its pure form — the focus-gating state machine
 * the TV preview rides:
 *
 *  - `trailerAutoplayEnabled` off → nothing mounts (today's content; the
 *    whole preview family is dead — the interaction-matrix guarantee).
 *  - Non-TV → mount immediately (unchanged behaviour — the phone/tablet/
 *    desktop autoplay as it has always run).
 *  - TV with the `trailer_preview_on_focus` semantic off → mount immediately
 *    (the row merely toggles the focus-gating, not the family).
 *  - TV with the semantic on → mount only while the play target holds focus
 *    AND the window is visible (a hidden window has no focused button —
 *    lifecycle pause/resume of a mounted embed is separately handled by the
 *    embed's own observer; this leg only gates the mount).
 */
internal fun trailerPreviewShouldMount(
    trailerAutoplayEnabled: Boolean,
    focusPreviewEnabled: Boolean,
    isTv: Boolean,
    playTargetFocused: Boolean,
    windowVisible: Boolean,
): Boolean {
    if (!trailerAutoplayEnabled) return false
    if (!isTv || !focusPreviewEnabled) return true
    return windowVisible && playTargetFocused
}

/**
 * The window's RESUMED state as composition state — the "window hidden" leg
 * of the machine (a backgrounded app holds no focus, so the focus-gated
 * mount drops instead of silently persisting).
 */
@Composable
internal fun rememberWindowVisible(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var visible by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> visible = true
                Lifecycle.Event.ON_PAUSE -> visible = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return visible
}
