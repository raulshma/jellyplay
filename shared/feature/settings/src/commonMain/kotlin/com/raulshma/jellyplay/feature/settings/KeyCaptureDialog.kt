package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.isSeekFamilyKey
import com.raulshma.jellyplay.feature.player.video.playerInputKeyOf
import com.raulshma.jellyplay.feature.player.video.playerKeyCode
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_alt
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_ctrl
import com.raulshma.jellyplay.core.ui.generated.resources.core_input_key_shift
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_cancel
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_add_shortcut
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_instruction
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_press
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_pending
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_seek_family
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_unsupported
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_capture_escape_hint
import org.jetbrains.compose.resources.stringResource

/**
 * The "press a key" capture dialog behind the editor's add-shortcut row: it
 * grabs focus, echoes held modifiers live ("Ctrl + Shift + …"), and commits
 * on the first non-modifier key down through the [playerInputKeyOf] bridge.
 * Escape (unmodified) cancels — Escape WITH modifiers is a bindable combo
 * like any other. Codes outside the catalog ([playerInputKeyOf] `null`) and
 * modifier combos over seek-family keys ([isSeekFamilyKey] — their
 * modifiers are event-time step semantics, so such a row could never
 * resolve) show a hint and keep listening; the dialog never closes itself
 * on a commit — the caller does, after [onCaptured].
 */
@Composable
internal fun KeyCaptureDialog(
    onCaptured: (InputPattern.Key) -> Unit,
    onDismiss: () -> Unit,
) {
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }
    var unsupported by remember { mutableStateOf(false) }
    var seekFamily by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Modifier words resolve in composition; the key handler only flips
    // flags. The pending "…" comes from a template so the ellipsis
    // localizes with the text.
    val ctrlLabel = stringResource(CoreUiRes.string.core_input_key_ctrl)
    val shiftLabel = stringResource(CoreUiRes.string.core_input_key_shift)
    val altLabel = stringResource(CoreUiRes.string.core_input_key_alt)
    val heldLabels = listOfNotNull(
        ctrlLabel.takeIf { ctrl },
        shiftLabel.takeIf { shift },
        altLabel.takeIf { alt },
    )
    val preview = when {
        heldLabels.isEmpty() -> stringResource(Res.string.settings_input_bindings_capture_press)
        else -> stringResource(Res.string.settings_input_bindings_capture_pending, heldLabels.joinToString(" + "))
    }

    val handleKey: (KeyEvent) -> Boolean = handler@{ event ->
        // Every key event inside the dialog is consumed — nothing may leak
        // to the screen behind the dialog window. The modifier flags track
        // EVERY event, not just KeyDown, so a released modifier clears the
        // live preview; a modifier's own KeyUp still reports itself pressed
        // on some platforms, so that one is cleared by hand.
        ctrl = event.isCtrlPressed
        shift = event.isShiftPressed
        alt = event.isAltPressed
        if (event.type == KeyEventType.KeyUp && event.key in ModifierOnlyKeys) {
            when (event.key) {
                Key.CtrlLeft, Key.CtrlRight -> ctrl = false
                Key.ShiftLeft, Key.ShiftRight -> shift = false
                Key.AltLeft, Key.AltRight -> alt = false
            }
        }
        if (event.type != KeyEventType.KeyDown) return@handler true
        val isModifierKey = event.key in ModifierOnlyKeys
        unsupported = false
        seekFamily = false
        if (isModifierKey) return@handler true
        if (event.key == Key.Escape && !event.isCtrlPressed && !event.isShiftPressed && !event.isAltPressed) {
            onDismiss()
            return@handler true
        }
        val inputKey = playerInputKeyOf(event.playerKeyCode)
        when {
            inputKey == null -> unsupported = true
            // Seek-family modifiers are the event-time step ladder, not
            // bindings — the resolution candidate list never carries the
            // exact arm for these keys, so such a row would be dead.
            inputKey.isSeekFamilyKey &&
                (event.isCtrlPressed || event.isShiftPressed || event.isAltPressed) ->
                seekFamily = true

            else -> onCaptured(
                InputPattern.Key(inputKey, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed),
            )
        }
        true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = ShapeCache.smooth24,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .onPreviewKeyEvent(handleKey)
                    .focusRequester(focusRequester)
                    .focusable(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Keyboard,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(Res.string.settings_input_bindings_add_shortcut),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(Res.string.settings_input_bindings_capture_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = ShapeCache.smooth16,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (heldLabels.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    AnimatedVisibility(visible = unsupported || seekFamily) {
                        Text(
                            text = stringResource(
                                if (unsupported) Res.string.settings_input_bindings_capture_unsupported
                                else Res.string.settings_input_bindings_capture_seek_family,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_input_bindings_capture_escape_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.padding(4.dp))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(Res.string.settings_cancel))
                        }
                    }
                }
            }
        }
    }
}

/** The key events that only ever adjust the pending preview, never commit. */
private val ModifierOnlyKeys = setOf(
    Key.CtrlLeft, Key.CtrlRight,
    Key.ShiftLeft, Key.ShiftRight,
    Key.AltLeft, Key.AltRight,
)
