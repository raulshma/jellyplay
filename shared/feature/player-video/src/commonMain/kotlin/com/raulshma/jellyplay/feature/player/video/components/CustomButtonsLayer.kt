package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.CustomButtonAction
import com.raulshma.jellyplay.feature.player.video.CustomButtonSide
import com.raulshma.jellyplay.feature.player.video.CustomPlayerButton
import com.raulshma.jellyplay.feature.player.video.CustomPlayerButtons
import com.raulshma.jellyplay.feature.player.video.PlayerSheet
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine

/** Reference-loop cap for `customButton` chains (a → b → a …). */
private const val BUTTON_REF_DEPTH_LIMIT = 4

/**
 * The custom on-screen player buttons (issue #27): a vertical pill stack per
 * screen edge, rendered from the user-authored `mpvCustomButtons` JSON
 * ([CustomPlayerButtons.parse]). mpv-only by the caller's [isMpvEngine] gate;
 * hidden whenever the control chrome is (the caller's [visible] — controls
 * hidden, PiP, or a locked screen), so the buttons are part of the chrome and
 * never intercept gestures aimed at the video.
 *
 * Execution routes through the screen's EXISTING effect funnels only — this
 * layer writes NO uiState (the ControllerOwnershipTest ownership ratchet):
 *
 *  - seek → the routed seek funnel (SyncPlay → cast → local), target =
 *    current position ± the action's seconds;
 *  - sheet → the screen's sheet-opener lambda (the same funnel every control
 *    button uses);
 *  - filter / raw command → [MediaEngine.executeUserCommand] (mpv engines
 *    only; the default no-op returns false and stays silent).
 *
 * The engine is read through [getEngine] at EXECUTION time (the reader-lambda
 * rule) so a mid-session engine swap cannot leave the layer commanding a dead
 * handle.
 */
@Composable
internal fun CustomButtonsLayer(
    buttonsJson: String,
    isMpvEngine: Boolean,
    visible: Boolean,
    getEngine: () -> MediaEngine?,
    currentPositionMs: () -> Long,
    onSeekTo: (Long) -> Unit,
    onOpenSheet: (PlayerSheet) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttons = remember(buttonsJson) { CustomPlayerButtons.parse(buttonsJson) }
    if (!isMpvEngine || !visible || buttons.isEmpty()) return

    fun execute(action: CustomButtonAction, depth: Int) {
        val engine = getEngine() ?: return
        when (action) {
            is CustomButtonAction.Seek -> {
                val target = (currentPositionMs() + (action.seconds * 1000).toLong()).coerceAtLeast(0L)
                onSeekTo(target)
            }
            is CustomButtonAction.ToggleFilter ->
                engine.executeUserCommand(CustomPlayerButtons.mpvToggleFilterCommand(action.filterName))
            is CustomButtonAction.Command -> engine.executeUserCommand(action.command)
            is CustomButtonAction.OpenSheet -> onOpenSheet(action.sheet)
            is CustomButtonAction.ButtonRef -> {
                // Chain depth is bounded: a self- or mutually-referencing set
                // of buttons cannot loop forever.
                if (depth >= BUTTON_REF_DEPTH_LIMIT) return
                buttons.getOrNull(action.id)?.tap?.let { execute(it, depth + 1) }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val rightStack = buttons.filter { it.side == CustomButtonSide.RIGHT }
        val leftStack = buttons.filter { it.side == CustomButtonSide.LEFT }
        if (rightStack.isNotEmpty()) {
            ButtonStack(
                buttons = rightStack,
                onExecute = { execute(it, depth = 0) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 12.dp, vertical = 48.dp),
            )
        }
        if (leftStack.isNotEmpty()) {
            ButtonStack(
                buttons = leftStack,
                onExecute = { execute(it, depth = 0) },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 12.dp, vertical = 48.dp),
            )
        }
    }
}

/** One side's ordered pill stack. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ButtonStack(
    buttons: List<CustomPlayerButton>,
    onExecute: (CustomButtonAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        buttons.forEach { button ->
            // Chrome-styled pill: the same dark-scrim / light-text family the
            // control bars use (labels are user-authored — no app strings).
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            ) {
                Text(
                    text = button.label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { button.tap?.let(onExecute) },
                            onLongClick = { button.longPress?.let(onExecute) },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
