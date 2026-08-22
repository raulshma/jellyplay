package com.raulshma.jellyplay.core.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot handed by a card to [MediaPreviewController] when the user long-presses
 * it. Carries everything the press-and-hold "peek" overlay needs to render a rich
 * detail card with **zero network calls** — every field is already present on the
 * [MediaItem] the card was displaying.
 *
 * The callbacks ([onDismiss]) are intentionally minimal: the peek is purely visual
 * (lift-to-dismiss), so there are no in-overlay action buttons. Opening the full
 * detail screen / playing happens via the card's existing tap & play affordances.
 */
@Immutable
data class MediaPreview(
    val item: MediaItem,
    val posterUrl: String?,
    val backdropUrl: String?,
    val blurHash: String?,
    /**
     * The card's on-screen bounds (in window coordinates) at the moment of the
     * long-press. Drives the position-anchored morph: the preview card animates
     * *out of* this rect into its centered resting position, mimicking a
     * shared-element transition without requiring both endpoints to share an
     * [androidx.compose.animation.AnimatedVisibilityScope]. `null` falls back to
     * a simple centered fade/scale.
     */
    val sourceBounds: Rect? = null,
)

/**
 * Ephemeral host for the press-and-hold media preview. Holds the currently
 * previewed [MediaPreview] (or `null` when none is showing) as a [StateFlow].
 *
 * Unlike [com.raulshma.jellyplay.core.ui.feedback.UserMessageBus] (a Hilt
 * `@Singleton` for one-shot messages that must outlive the UI), the preview is
 * purely transient UI state, so it is `remember`-ed once at the app root and
 * provided via [LocalMediaPreviewController] — no DI graph changes required.
 */
@Stable
class MediaPreviewController {
    private val _state = MutableStateFlow<MediaPreview?>(null)

    /** The currently previewed media, or `null` when the peek is hidden. */
    val state: StateFlow<MediaPreview?> = _state.asStateFlow()

    /** Show the peek overlay for [preview]. Replaces any existing preview. */
    fun show(preview: MediaPreview) {
        _state.value = preview
    }

    /** Hide the peek overlay. */
    fun hide() {
        _state.value = null
    }
}

/**
 * CompositionLocal giving any Composable access to the app-wide
 * [MediaPreviewController] without threading it through every parameter list.
 *
 * `null` by default so that previews/tests/unwired hosts simply skip the feature
 * instead of crashing. Provided once at the root
 * ([com.raulshma.jellyplay.navigation.JellyPlayApp]).
 */
val LocalMediaPreviewController = staticCompositionLocalOf<MediaPreviewController?> { null }

/**
 * Whether the press-and-hold "peek" preview is enabled. Bound at the root from
 * `UserPreferences.isExperimentalEnabled(ExperimentalFeature.MEDIA_CARD_PEEK)`
 * (off by default — it is an experimental opt-in). Cards and the overlay both
 * read this so the feature is fully dormant (no gesture wiring, no overlay, no
 * backdrop blur) when the user hasn't turned it on.
 */
val LocalMediaPeekEnabled = staticCompositionLocalOf<Boolean> { false }

/**
 * Bundle returned by [rememberMediaPeek] so a card gets everything the peek
 * feature needs in one call:
 *  - [onLongClick]: a **stable** lambda for `combinedClickable(onLongClick = …)`,
 *    or `null` when the feature is off (no controller / TV).
 *  - [boundsModifier]: tracks the card's on-screen rect so the preview can
 *    morph out of the card's exact position. Apply it to the same element that
 *    receives the `combinedClickable`.
 */
@Immutable
data class MediaPeekHandle(
    val onLongClick: (() -> Unit)?,
    val boundsModifier: Modifier,
)

/**
 * Wires up the press-and-hold "peek" preview for a card. Returns a
 * [MediaPeekHandle] whose [MediaPeekHandle.onLongClick] is a **stable** lambda
 * (so `combinedClickable`'s gesture detector is not restarted mid-press) and
 * whose [MediaPeekHandle.boundsModifier] captures the card's on-screen rect for
 * the position-anchored morph.
 *
 * `onLongClick` is `null` when the feature is unavailable (no controller wired,
 * or TV — where long-press does not exist on a D-pad).
 *
 * [previewFactory] builds the [MediaPreview] at long-press time (not at
 * composition), so cards whose preview content isn't a [MediaItem] — e.g.
 * [SeerrSearchItem.toMediaPreview] — can participate without inlining the
 * bounds/launcher plumbing themselves. The factory is given the captured
 * card bounds so it can populate [MediaPreview.sourceBounds].
 *
 * Pair this with [rememberReleaseDismiss] to get Instagram's lift-to-dismiss
 * behavior: the same [interactionSource] the card already uses for its
 * press-scale animation drives the hide-on-release.
 */
@Composable
fun rememberMediaPeek(
    previewFactory: (sourceBounds: Rect?) -> MediaPreview,
): MediaPeekHandle {
    val controller = LocalMediaPreviewController.current
    val isTv = LocalJellyPlayUi.current.isTv
    // Fully dormant unless the user has opted in (experimental, off by default).
    val enabled = LocalMediaPeekEnabled.current

    // Track the card's bounds in window coordinates. Held in a state object so
    // the launcher lambda (keyed below) always reads the latest rect at the
    // instant of the long-press without needing to re-key on position changes.
    val boundsState = remember { mutableStateOf(Rect.Zero) }
    val boundsModifier = if (controller != null && !isTv && enabled) {
        Modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInWindow()
            boundsState.value = Rect(
                left = pos.x,
                top = pos.y,
                right = pos.x + coords.size.width,
                bottom = pos.y + coords.size.height,
            )
        }
    } else {
        Modifier
    }

    val onLongClick: (() -> Unit)? = if (controller != null && !isTv && enabled) {
        remember(controller, previewFactory) {
            {
                controller.show(previewFactory(boundsState.value.takeIf { it.width > 0 }))
            }
        }
    } else {
        null
    }

    return MediaPeekHandle(onLongClick = onLongClick, boundsModifier = boundsModifier)
}

/**
 * [MediaItem]-typed convenience overload of [rememberMediaPeek]. Builds the
 * [MediaPreview] from the item's fields; callers with a different source model
 * (e.g. Seerr) use the [rememberMediaPeek] factory overload together with
 * [SeerrSearchItem.toMediaPreview].
 */
@Composable
fun rememberMediaPeek(
    item: MediaItem,
    posterUrl: String?,
    backdropUrl: String?,
    blurHash: String?,
): MediaPeekHandle = rememberMediaPeek(
    previewFactory = { sourceBounds ->
        MediaPreview(
            item = item,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            blurHash = blurHash,
            sourceBounds = sourceBounds,
        )
    }
)

/**
 * Dismisses the peek overlay the instant the user lifts their finger, driven by
 * the card's own press state ([isPressed], from `collectIsPressedAsState` on the
 * same `MutableInteractionSource` feeding its press-scale animation). This gives
 * Instagram's signature **release-to-dismiss** without any fragile raw-pointer
 * hand-off: the card keeps owning the gesture, and we simply observe the
 * press-interaction stream it already emits.
 *
 * Crucially, dismissal only fires on a genuine **press → release edge**
 * (`isPressed` going `true → false`). A freshly-composed card initializes with
 * `isPressed == false`, so without the edge guard a card that enters
 * composition *while* another card's peek is open (e.g. a staggered home row
 * loading mid-hold) would wrongly dismiss it. We track [wasPressed] to skip
 * that initial pass.
 *
 * No-op (installs nothing) when there is no controller or on TV.
 *
 * Drop-in usage inside a card, next to its existing `collectIsPressedAsState`:
 * ```
 * val isPressed by interactionSource.collectIsPressedAsState()
 * rememberReleaseDismiss(isPressed)
 * ```
 */
@Composable
fun rememberReleaseDismiss(isPressed: Boolean) {
    val controller = LocalMediaPreviewController.current ?: return
    if (LocalJellyPlayUi.current.isTv) return
    val latestController by rememberUpdatedState(controller)
    var wasPressed by remember { mutableStateOf(false) }

    // Only hide on a real press→release transition, never on initial composition.
    LaunchedEffect(isPressed) {
        if (wasPressed && !isPressed && latestController.state.value != null) {
            latestController.hide()
        }
        wasPressed = isPressed
    }
}

/**
 * Maps a Seerr (TMDB-sourced) [SeerrSearchItem] into a [MediaItem]-shaped
 * [MediaPreview], so [SeerrMediaCard] can participate in the peek feature
 * without the controller having to know about the Seerr model.
 *
 * Lossy by design: Seerr items lack runtime/genres/playback state, so those
 * preview rows simply omit — every field on [MediaItem] is nullable for exactly
 * this reason.
 */
fun SeerrSearchItem.toMediaPreview(
    posterUrl: String?,
    backdropUrl: String?,
    sourceBounds: Rect? = null,
): MediaPreview {
    val mediaType = when (mediaType.lowercase()) {
        "movie" -> MediaType.MOVIE
        "tv" -> MediaType.SERIES
        else -> MediaType.UNKNOWN
    }
    val item = MediaItem(
        id = "seerr_$id",
        name = displayName,
        overview = overview,
        mediaType = mediaType,
        year = year,
        communityRating = voteAverage,
    )
    return MediaPreview(
        item = item,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        blurHash = null,
        sourceBounds = sourceBounds,
    )
}
