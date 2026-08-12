package com.raulshma.jellyplay.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.animation.defaultEffectsSpec
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import kotlinx.coroutines.flow.first

/**
 * Visual register for a [ConfirmDialog]. Drives the leading icon-badge tint and the
 * confirm-action color so call sites describe intent rather than hand-pick colors.
 *
 * - [DESTRUCTIVE]: irreversible removal (error red).
 * - [WARNING]: consequential but recoverable / cautionary (error red action, warm badge).
 * - [NEUTRAL]: plain confirmation, no special emphasis.
 * - [PRIMARY]: an affirmative forward action (primary brand).
 */
enum class ConfirmTone { DESTRUCTIVE, WARNING, NEUTRAL, PRIMARY }

/**
 * An optional extra action button rendered left of the dismiss button, for flows
 * that need three choices (e.g. the metadata editor's Discard / Save / Keep editing).
 */
class ConfirmAction(
    val text: String,
    val tone: ConfirmTone = ConfirmTone.PRIMARY,
    val onClick: () -> Unit,
)

/**
 * Reusable confirmation dialog for destructive / consequential actions.
 *
 * Drawn as a custom centered [Dialog] (no stock `AlertDialog`) so it carries the
 * app's visual language: a [ShapeCache.smooth28] floating surface, a tone-tinted
 * squircle icon badge ([ShapeCache.smooth16]), [MaterialTheme.motionScheme] enter/exit,
 * MD3 Expressive Floating Navigation inspired pill action buttons ([ShapeCache.smoothPill]),
 * and TV D-pad focus — the same "draw your own chrome" approach used by the Library and
 * Media Detail screens.
 *
 * ## Two usage styles
 *
 * **1. Deferred action (preferred for one-off flows).** Use [rememberConfirmState]
 * so the confirm lambda is supplied at request time and reaped after the dialog
 * dismisses — no hand-rolled `var show by remember` per call site:
 *
 * ```
 * val confirm = rememberConfirmState()
 * IconButton(onClick = { confirm.request { viewModel.removeServer(id) } }) { ... }
 * confirm.ConfirmDialog(
 *     title = "Remove server?",
 *     message = "This removes the server and all saved users on it.",
 *     confirmText = "Remove",
 *     icon = Tabler.Outline.Trash,
 * )
 * ```
 *
 * **2. Explicit gate.** Callers that already own a `Boolean` gate can render the
 * dialog directly:
 *
 * ```
 * if (showConfirm) {
 *     ConfirmDialog(
 *         title = "Remove server?",
 *         message = "This removes the server and all saved users on it.",
 *         confirmText = "Remove",
 *         dismissText = "Cancel",
 *         icon = Tabler.Outline.Trash,
 *         onConfirm = { viewModel.removeServer(id) },
 *         onDismiss = { showConfirm = false },
 *     )
 * }
 * ```
 */
@Composable
fun ConfirmDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    // Null confirmText -> no primary confirm button (content-only / choice dialogs
    // rendered entirely via [content], e.g. multi-option pickers).
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    tone: ConfirmTone = ConfirmTone.DESTRUCTIVE,
    icon: ImageVector? = null,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    secondaryAction: ConfirmAction? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val isTv = LocalJellyPlayUi.current.isTv

    // Captures the real dismiss/confirm intent; the dialog plays its exit
    // transition before the caller removes it from the composition.
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Starts invisible and targets visible on first composition, which plays the
    // enter transition. Flipping targetState back to false plays the exit.
    val transitionState = remember { MutableTransitionState(false).apply { targetState = true } }

    LaunchedEffect(pendingExit) {
        val exit = pendingExit ?: return@LaunchedEffect
        transitionState.targetState = false
        snapshotFlow { transitionState.isIdle }.first { it }
        exit.invoke()
        pendingExit = null
    }

    val requestExit: (() -> Unit) -> Unit = { action ->
        if (pendingExit == null) pendingExit = action
    }

    Dialog(
        onDismissRequest = { requestExit(onDismiss) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(defaultEffectsSpec()) + scaleIn(initialScale = 0.92f),
                exit = fadeOut(fastEffectsSpec()) + scaleOut(targetScale = 0.96f),
            ) {
                ConfirmPanel(
                    title = title,
                    message = message,
                    confirmText = confirmText,
                    dismissText = dismissText,
                    tone = tone,
                    icon = icon,
                    confirmEnabled = confirmEnabled,
                    confirmLoading = confirmLoading,
                    secondaryAction = secondaryAction,
                    content = content,
                    isTv = isTv,
                    onConfirm = { requestExit { onConfirm?.invoke(); onDismiss() } },
                    onDismiss = { requestExit(onDismiss) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
internal fun ConfirmPanel(
    title: String,
    message: String?,
    confirmText: String?,
    dismissText: String?,
    tone: ConfirmTone,
    icon: ImageVector?,
    confirmEnabled: Boolean,
    confirmLoading: Boolean,
    secondaryAction: ConfirmAction?,
    content: (@Composable ColumnScope.() -> Unit)?,
    isTv: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val (badgeColor, badgeContentColor) = badgeColors(tone)

    // On TV, land initial focus on the safe action (dismiss) for destructive /
    // warning tones so a stray "OK" D-pad press cannot trigger harm. Content-only
    // dialogs (no confirm button) also focus dismiss; otherwise focus confirm.
    val hasConfirm = confirmText != null
    val focusOnDismiss = isTv && (!hasConfirm || tone == ConfirmTone.DESTRUCTIVE || tone == ConfirmTone.WARNING)
    val dismissFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (focusOnDismiss) dismissFocus.tryRequestFocus("confirm_dismiss")
        else confirmFocus.tryRequestFocus("confirm_action")
    }

    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        // surface (not surfaceContainer) to match the pure-black OLED backgrounds
        // of Library / MediaDetail in every theme mode, plus a subtle MD3 nav floating border.
        color = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        shape = ShapeCache.smooth28,
        border = BorderStroke(
            width = 1.dp,
            color = colorScheme.onSurface.copy(alpha = 0.08f),
        ),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Surface(
                    shape = ShapeCache.smooth16,
                    color = badgeColor,
                    contentColor = badgeContentColor,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (content != null) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCache.smooth16,
                    color = colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = colorScheme.onSurface.copy(alpha = 0.05f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = content,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // MD3 Expressive Floating Nav inspired Pill Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (secondaryAction != null) {
                    val secColors = confirmButtonColors(secondaryAction.tone)
                    Button(
                        onClick = { secondaryAction.onClick(); onDismiss() },
                        shape = ShapeCache.smoothPill,
                        colors = secColors,
                    ) {
                        Text(secondaryAction.text, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (dismissText != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = ShapeCache.smoothPill,
                        border = BorderStroke(
                            width = 1.dp,
                            color = colorScheme.onSurface.copy(alpha = 0.12f),
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colorScheme.onSurfaceVariant,
                        ),
                        modifier = Modifier.focusRequester(dismissFocus),
                    ) {
                        Text(dismissText, fontWeight = FontWeight.Medium)
                    }
                }
                if (confirmText != null) {
                    val btnColors = confirmButtonColors(tone)
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled && !confirmLoading,
                        shape = ShapeCache.smoothPill,
                        colors = btnColors,
                        modifier = Modifier.focusRequester(confirmFocus),
                    ) {
                        if (confirmLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = btnColors.disabledContentColor,
                            )
                        } else {
                            Text(confirmText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** Tone → leading icon-badge container/content color. */
@Composable
private fun badgeColors(tone: ConfirmTone): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (tone) {
        ConfirmTone.DESTRUCTIVE -> c.errorContainer to c.onErrorContainer
        ConfirmTone.WARNING -> c.tertiaryContainer to c.onTertiaryContainer
        ConfirmTone.NEUTRAL -> c.surfaceContainerHighest to c.onSurfaceVariant
        ConfirmTone.PRIMARY -> c.primaryContainer to c.onPrimaryContainer
    }
}

/** Tone → confirm button container & content colors. */
@Composable
private fun confirmButtonColors(tone: ConfirmTone): ButtonColors {
    val c = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ConfirmTone.DESTRUCTIVE -> c.errorContainer to c.onErrorContainer
        ConfirmTone.WARNING -> c.tertiaryContainer to c.onTertiaryContainer
        ConfirmTone.NEUTRAL -> c.primaryContainer to c.onPrimaryContainer
        ConfirmTone.PRIMARY -> c.primary to c.onPrimary
    }
    return ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = content,
        disabledContainerColor = container.copy(alpha = 0.5f),
        disabledContentColor = content.copy(alpha = 0.7f),
    )
}

/**
 * Holds a single pending confirmation so a screen can defer the destructive
 * lambda until the user actually confirms.
 *
 * Why a holder instead of a plain `Boolean`: a `Boolean` gate forces every call
 * site to also stash the target id/lambda somewhere reachable from the dialog's
 * `onConfirm`. [request] captures the lambda inline, and [dismiss] clears it,
 * so there is nothing extra to track.
 *
 * Render via [ConfirmDialog]; the convenience extension renders it only while
 * a request is pending.
 */
@Stable
class ConfirmState internal constructor() {
    internal var pending: (() -> Unit)? by mutableStateOf(null)

    /** Whether a confirmation is currently awaiting the user. */
    val isVisible: Boolean get() = pending != null

    /**
     * Show the dialog; [onConfirm] runs only if the user confirms, then is
     * cleared regardless of the outcome.
     */
    fun request(onConfirm: () -> Unit) {
        pending = onConfirm
    }

    /** Dismiss the pending confirmation without running its action. */
    fun dismiss() {
        pending = null
    }
}

/**
 * Remember a [ConfirmState] scoped to the composition.
 */
@Composable
fun rememberConfirmState(): ConfirmState = remember { ConfirmState() }

/**
 * Render the dialog backing [state] while a request is pending.
 *
 * The confirm text should describe the action ("Remove", "Delete"); the dismiss
 * text defaults to `Cancel` when omitted.
 */
@Composable
fun ConfirmState.ConfirmDialog(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    confirmText: String? = null,
    dismissText: String? = null,
    tone: ConfirmTone = ConfirmTone.DESTRUCTIVE,
    icon: ImageVector? = null,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    secondaryAction: ConfirmAction? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val onConfirm = pending ?: return
    ConfirmDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = { dismiss() },
        modifier = modifier,
        message = message,
        dismissText = dismissText,
        tone = tone,
        icon = icon,
        confirmEnabled = confirmEnabled,
        confirmLoading = confirmLoading,
        secondaryAction = secondaryAction,
        content = content,
    )
}
