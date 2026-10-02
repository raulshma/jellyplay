package com.raulshma.jellyplay.core.ui.components
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_backspace

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding

import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.RequestOrRestoreFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import com.raulshma.jellyplay.core.ui.animation.defaultSpatialSpec
import com.raulshma.jellyplay.core.ui.animation.fastEffectsSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import com.raulshma.jellyplay.core.designsystem.theme.LocalJellyPlayColorScheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalJellyPlayShapes
import com.raulshma.jellyplay.core.designsystem.theme.LocalJellyPlayTypography
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.pressed

@OptIn(ExperimentalFoundationStyleApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PinLockScreen(
    title: String = "Enter PIN",
    subtitle: String = "Unlock JellyPlay",
    onPinEntered: (String) -> Unit,
    onErrorClear: () -> Unit = {},
    errorMessage: String? = null,
    compactMode: Boolean = false,
    enabled: Boolean = true,
    verifying: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }
    val maxDigits = 4

    // TV: land focus on the first keypad key so D-pad entry works immediately.
    val firstKeyFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(firstKeyFocusRequester, debugKey = "pin_first_key")

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    val keySize = when {
        compactMode && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> 56.dp
        adaptiveInfo.windowSizeClass == WindowSizeClass.Expanded -> 80.dp
        else -> 72.dp
    }
    val dotSize = when {
        compactMode && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> 12.dp
        else -> 16.dp
    }
    val dotFilledSize = when {
        compactMode && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> 16.dp
        else -> 20.dp
    }
    val keySpacing = when {
        compactMode && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> 8.dp
        else -> 16.dp
    }
    val sectionSpacing = when {
        compactMode && adaptiveInfo.windowSizeClass == WindowSizeClass.Compact -> 8.dp
        else -> 16.dp
    }

    Column(
        modifier = Modifier
            .then(if (compactMode) Modifier else Modifier.fillMaxSize())
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = if (compactMode) 0.dp else contentPad),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (compactMode) Arrangement.Center else Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))

        if (verifying) {
            // PBKDF2 verification is deliberately slow; show a spinner in
            // place of the dots so the user knows the entry is being checked.
            JellyPlayLoadingIndicator()
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(keySpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(maxDigits) { index ->
                    val filled = index < pin.length
                    val interactionSource = remember { MutableInteractionSource() }
                    val styleState = rememberUpdatedStyleState(interactionSource)

                    Box(
                        modifier = Modifier
                            .styleable(
                                styleState,
                                if (filled) PinStyles.pinDotFilledStyle(dotFilledSize) else PinStyles.pinDotStyle(dotSize)
                            )
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(if (compactMode) 32.dp else 48.dp))

        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "backspace"),
        )

        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(keySpacing, androidx.compose.ui.Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(keySize))
                        "backspace" -> {
                            val interactionSource = remember { MutableInteractionSource() }
                            val styleState = rememberUpdatedStyleState(interactionSource)
                            val backspaceFocusState = rememberTvFocusState()

                            Box(
                                modifier = Modifier
                                    .styleable(styleState, PinStyles.pinKeyStyle(keySize))
                                    .then(backspaceFocusState.focusModifier)
                                    .tvFocusIndicator(backspaceFocusState, MaterialTheme.shapes.extraLarge)
                                    .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        enabled = enabled,
                                    ) {
                                        if (pin.isNotEmpty()) {
                                            pin = pin.dropLast(1)
                                            onErrorClear()
                                        }
                                    }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.Backspace,
                                    contentDescription = stringResource(Res.string.core_ui_backspace),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        else -> {
                            PinKeyButton(
                                key = key,
                                isPressed = false,
                                keySize = keySize,
                                enabled = enabled,
                                focusRequester = if (key == "1") firstKeyFocusRequester else null,
                                onClick = {
                                    if (pin.length < maxDigits) {
                                        pin += key
                                        onErrorClear()
                                        if (pin.length == maxDigits) {
                                            onPinEntered(pin)
                                            pin = ""
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(sectionSpacing))
        }
    }
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun PinKeyButton(
    key: String,
    isPressed: Boolean,
    keySize: Dp = 72.dp,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val styleState = rememberUpdatedStyleState(interactionSource)
    val tvFocusState = com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState()

    Box(
        modifier = Modifier
            .styleable(styleState, PinStyles.pinKeyStyle(keySize))
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier,
            )
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, MaterialTheme.shapes.extraLarge)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}


/**
 * The PIN pad's component styles, folded here from designsystem's
 * `ComponentStyles` (deleted): this screen was its only consumer, and the
 * styles are PIN-specific chrome — key tile, empty dot, filled dot — built on
 * designsystem's Style DSL and theme locals. Kept as one private object so
 * the three styles stay adjacent and can't drift apart.
 */
@OptIn(ExperimentalFoundationStyleApi::class)
private object PinStyles {

    private val StyleScope.colors: ColorScheme
        get() = LocalJellyPlayColorScheme.currentValue

    private val StyleScope.typography: Typography
        get() = LocalJellyPlayTypography.currentValue

    private val StyleScope.shapes: Shapes
        get() = LocalJellyPlayShapes.currentValue

    fun pinKeyStyle(keySize: Dp = 72.dp) = Style {
        background(colors.surfaceVariant)
        shape(shapes.extraLarge)
        minWidth(keySize)
        minHeight(keySize)
        textStyle(typography.headlineMedium)

        pressed {
            background(colors.primaryContainer)
            // NOTE: a `scale(0.95f)` pressed-state transform previously lived here
            // but was dropped — Compose Foundation 1.12.0-beta01's Styles API no
            // longer resolves `scale` inside the `pressed { }` lambda (it now
            // routes through `state(StyleStateKey)`). Re-add once the API stabilizes.
        }
    }

    fun pinDotStyle(dotSize: Dp = 16.dp) = Style {
        shape(shapes.extraLarge)
        background(colors.outlineVariant)
        minWidth(dotSize)
        minHeight(dotSize)
    }

    fun pinDotFilledStyle(dotSize: Dp = 20.dp) = Style {
        shape(shapes.extraLarge)
        background(colors.primary)
        minWidth(dotSize)
        minHeight(dotSize)
    }
}
