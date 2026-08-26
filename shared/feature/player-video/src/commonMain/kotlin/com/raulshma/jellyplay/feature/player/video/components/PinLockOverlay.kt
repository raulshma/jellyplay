package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_enter_pin
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_incorrect_pin
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_unlock_player



import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.core.ui.components.PinLockScreen
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKey
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

/**
 * Fullscreen overlay that gates playback behind a PIN. Wraps the existing
 * [PinLockScreen] so the keypad reuses the shared Material style, focus
 * indicators, and TV d-pad layout (including the backspace affordance that
 * the bespoke TV keypad was missing).
 *
 * On TV the overlay captures the Back key via [onDismiss]; on mobile it
 * relies on the system back gesture (handled by the host screen).
 */
@Composable
internal fun PinLockOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
    verifyPin: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visible, isTv) {
        if (visible && isTv) {
            focusRequester.tryRequestFocus("tv_pin_lock_overlay")
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(playerScrimColor().copy(alpha = 0.92f))
                .then(
                    if (isTv) {
                        Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .onDpadKey(
                                onBack = {
                                    onDismiss()
                                    true
                                },
                            )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var verifying by remember { mutableStateOf(false) }
            val incorrectPinMessage = stringResource(Res.string.player_video_incorrect_pin)
            PinLockScreen(
                title = stringResource(Res.string.player_video_enter_pin),
                subtitle = stringResource(Res.string.player_video_unlock_player),
                onPinEntered = { pin ->
                    if (verifying) return@PinLockScreen
                    verifying = true
                    scope.launch {
                        val valid = verifyPin(pin)
                        if (valid) {
                            errorMessage = null
                            onUnlock()
                        } else {
                            errorMessage = incorrectPinMessage
                        }
                        verifying = false
                    }
                },
                onErrorClear = { errorMessage = null },
                errorMessage = errorMessage,
                compactMode = true,
                enabled = !verifying,
                verifying = verifying,
            )
        }
    }
}
